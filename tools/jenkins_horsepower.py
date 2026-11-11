#!/usr/bin/env python3
"""Portable, credential-free Horse Power CE acceptance invoked by central Jenkins.

Only reviewed main runs on private workers. The host-only publishing broker
verifies the finished release-output bundle; this program cannot upload it.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ("forge-1.20.1", "neoforge-1.21.1")
PINNED_BIONICS_FORGE = "d597264a86c0e6d655e4bc9ffec23be292382882"
BIONICS_FORGE_SOURCE = "https://github.com/Dshbwlto/Create-Bionics-1.20.1.git"


def run(*args: str, cwd: Path = ROOT, env: dict[str, str] | None = None) -> None:
    print("RUN", cwd, " ".join(args), flush=True)
    subprocess.run(args, cwd=cwd, env=env, check=True)


def gradle(*args: str, cwd: Path = ROOT, env: dict[str, str] | None = None) -> None:
    wrapper = cwd / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise RuntimeError(f"Missing checked-in Gradle wrapper: {wrapper}")
    command = ([os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c"]
               if os.name == "nt" else [])
    run(*command, str(wrapper), *args, "--stacktrace", "--no-daemon",
        "--max-workers=2", cwd=cwd, env=env)


def script(name: str, *args: str) -> None:
    run(sys.executable, str(ROOT / "scripts" / name), *args)


def jar_for(target: str, version: str) -> Path:
    return ROOT / target / "build" / "libs" / f"createhorsepower-ce-{target.split('-', 1)[1]}-{version}.jar"


def version() -> str:
    match = re.search(r"^mod_version\s*=\s*(\S+)\s*$",
                      (ROOT / "gradle.properties").read_text(), re.M)
    if not match or not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", match.group(1)):
        raise ValueError("Invalid mod_version")
    return match.group(1)


def verify() -> None:
    gradle("verifySharedSources", "verifySharedResources", "verifyChangelog",
           "--configure-on-demand")
    run(sys.executable, "-m", "unittest", "discover",
        "-s", "tools", "-p", "test_*.py", "-v")


def build(target: str) -> None:
    gradle(f":{target}:runData")
    changed = subprocess.run(
        ["git", "status", "--porcelain", "--untracked-files=all", "--",
         f"{target}/src/generated/resources"],
        cwd=ROOT, check=True, capture_output=True, text=True).stdout
    if changed.strip():
        raise RuntimeError(f"{target}: runData modified tracked/generated resources: {changed}")
    gradle(f":{target}:build", f":{target}:runGameTestServer")


def icy(target: str) -> None:
    gradle(f":{target}:build", "-PicyCompatibilityTest=true")
    flag = "--forge" if target.startswith("forge-") else "--neoforge"
    script("verify_compat_jar.py", flag, str(jar_for(target, version())))
    gradle(f":{target}:runGameTestServer", "-PicyCompatibilityTest=true")
    script("assert_icy_gametests.py", "--project", target)
    for enabled in ("true", "false"):
        script("run_restart_probe.py", "--project", target, "--icy", enabled)


def java17_home() -> Path:
    """Select an actual JDK 17, not an assumed PATH/java or arbitrary JDK."""
    candidates = []
    for name in ("JAVA17_HOME", "JDK17_HOME"):
        if os.environ.get(name):
            candidates.append(Path(os.environ[name]))
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    if os.name == "nt":
        for root in (os.environ.get("ProgramFiles", r"C:\Program Files"),
                     os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)")):
            for prefix in ("Java/jdk-17*", "Eclipse Adoptium/jdk-17*",
                           "Microsoft/jdk-17*"):
                candidates.extend(Path(root).glob(prefix))
    else:
        candidates.extend(Path("/usr/lib/jvm").glob("*17*"))
        candidates.extend(Path("/opt/java").glob("*17*"))
    for path in candidates:
        java = path / "bin" / ("java.exe" if os.name == "nt" else "java")
        if not java.is_file():
            continue
        result = subprocess.run([str(java), "-version"], capture_output=True,
                                text=True, timeout=15, check=False)
        if result.returncode == 0 and re.search(r'\bversion "17(?:\.|")',
                                                 result.stdout + result.stderr):
            return path
    raise RuntimeError("JDK 17 not found for pinned Forge Bionics acceptance")


def prepared_bionics_jar() -> None:
    destination = ROOT / TARGETS[0] / "build" / "bionics-runtime" / "createbionics-2.0.0.jar"
    destination.parent.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, JAVA_HOME=str(java17_home()))
    with tempfile.TemporaryDirectory(prefix="chp-bionics-source-") as scratch:
        upstream = Path(scratch) / "source"
        run("git", "clone", "--no-checkout", BIONICS_FORGE_SOURCE, str(upstream))
        run("git", "checkout", "--detach", PINNED_BIONICS_FORGE, cwd=upstream)
        actual = subprocess.run(["git", "rev-parse", "HEAD"], cwd=upstream,
                                capture_output=True, text=True, check=True).stdout.strip()
        if actual != PINNED_BIONICS_FORGE:
            raise RuntimeError("Unexpected Forge Bionics upstream commit")
        gradle("build", cwd=upstream, env=env)
        source = upstream / "build" / "libs" / "createbionics-2.0.0.jar"
        if not source.is_file():
            raise RuntimeError("Pinned upstream Bionics build produced no expected JAR")
        shutil.copyfile(source, destination)


def bionics(target: str) -> None:
    if target == TARGETS[0]:
        prepared_bionics_jar()
    gradle(f":{target}:build", "-PbionicsCompatibilityTest=true")
    gradle(f":{target}:runGameTestServer", "-PbionicsCompatibilityTest=true")
    script("assert_bionics_gametests.py", "--project", target)
    script("run_bionics_restart_probe.py", "--project", target)


def package() -> None:
    # Rebuild with no optional-compat flags; the compatibility tasks have
    # already passed, but only default-runtime release JARs may be published.
    gradle("buildAll")
    ver = version()
    source_sha = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT,
                                check=True, capture_output=True, text=True).stdout.strip()
    if not re.fullmatch(r"[a-f0-9]{40}", source_sha):
        raise RuntimeError("Invalid exact source SHA")
    original = [jar_for(target, ver) for target in TARGETS]
    for path in original:
        if not path.is_file() or path.stat().st_size < 1024:
            raise RuntimeError(f"Missing production JAR: {path}")
        with zipfile.ZipFile(path) as archive:
            if "net/steampn/createhorsepower/CreateHorsePower.class" not in archive.namelist():
                raise RuntimeError(f"Wrong entrypoint in production JAR: {path}")
    script("verify_compat_jar.py", "--forge", str(original[0]),
           "--neoforge", str(original[1]))
    output = ROOT / "release-output"
    if output.exists():
        raise RuntimeError("Release output already exists; refuse stale artifacts")
    output.mkdir()
    checksums: list[tuple[str, str]] = []
    for target, path in zip(TARGETS, original):
        name = f"createhorsepower-{target}-{ver}.jar"
        dest = output / name
        shutil.copyfile(path, dest)
        checksums.append((hashlib.sha256(dest.read_bytes()).hexdigest(), name))
    provenance = {"source_sha": source_sha, "version": ver, "targets": sorted(TARGETS)}
    meta = output / "provenance.json"
    meta.write_text(json.dumps(provenance, sort_keys=True) + "\n", encoding="utf-8")
    checksums.append((hashlib.sha256(meta.read_bytes()).hexdigest(), meta.name))
    (output / "SHA256SUMS").write_text(
        "".join(f"{digest}  {name}\n" for digest, name in checksums), encoding="utf-8")


def main() -> None:
    actions = ("verify", "vanilla-forge", "vanilla-neoforge",
               "icy-forge", "icy-neoforge", "bionics-forge",
               "bionics-neoforge", "package")
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=actions)
    action = parser.parse_args().action
    if action == "verify":
        verify()
    elif action.startswith("vanilla-"):
        build(TARGETS[0] if action.endswith("forge") and not action.endswith("neoforge") else TARGETS[1])
    elif action.startswith("icy-"):
        icy(TARGETS[0] if action == "icy-forge" else TARGETS[1])
    elif action.startswith("bionics-"):
        bionics(TARGETS[0] if action == "bionics-forge" else TARGETS[1])
    else:
        package()


if __name__ == "__main__":
    main()

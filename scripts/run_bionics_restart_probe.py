#!/usr/bin/env python3
"""Two separate Minecraft server JVMs against an isolated real-Bionics world."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile


def properties(path: Path) -> dict[str, str]:
    result = {}
    for line in path.read_text(encoding="iso-8859-1").splitlines():
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            result[key] = value
    return result


def run(command: list[str], root: Path, log: Path, timeout: int) -> None:
    opts = {"start_new_session": True} if os.name != "nt" else {
        "creationflags": subprocess.CREATE_NEW_PROCESS_GROUP
    }
    if os.name == "nt":
        command = ["cmd.exe", "/c", *command]
    with log.open("w", encoding="utf-8") as out:
        process = subprocess.Popen(command, cwd=root, stdout=out, stderr=subprocess.STDOUT, **opts)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            if os.name != "nt":
                os.killpg(process.pid, signal.SIGTERM)
            else:
                subprocess.run(["taskkill.exe", "/PID", str(process.pid), "/T", "/F"],
                               check=False, stdout=out, stderr=subprocess.STDOUT)
            raise RuntimeError(f"Server timed out during {log.name}; see {log}")
    if code:
        raise RuntimeError(f"Server exited {code} during {log.name}; see {log}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", choices=["forge-1.20.1", "neoforge-1.21.1"], required=True)
    parser.add_argument("--timeout", type=int, default=1200)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    evidence_root = root / args.project / "build" / "bionics-restart-evidence"
    evidence_root.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="real-bionics-", dir=evidence_root)).resolve()
    (work / ".chp-isolated-bionics-probe").write_text("isolated ci probe\n")
    (work / "eula.txt").write_text("eula=true\n")
    generator = {"layers": [
        {"block": "minecraft:bedrock", "height": 1},
        {"block": "minecraft:dirt", "height": 2},
        {"block": "minecraft:grass_block", "height": 1}],
        "biome": "minecraft:plains", "structures": {}}
    settings = {
        "level-name": "probe-world", "level-type": "minecraft:flat",
        "generator-settings": json.dumps(generator, separators=(",", ":")),
        "online-mode": "false", "server-ip": "127.0.0.1", "server-port": "0",
        "enable-rcon": "false", "enable-query": "false",
        "spawn-protection": "0", "max-tick-time": "180000",
        "view-distance": "2", "simulation-distance": "2", "allow-nether": "false",
        "spawn-monsters": "false", "spawn-animals": "true", "spawn-npcs": "false",
        "sync-chunk-writes": "true"}
    (work / "server.properties").write_text(
        "".join(f"{key}={value}\n" for key, value in settings.items()))
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    phases = []
    for phase in ("write", "read"):
        command = [str(wrapper), f":{args.project}:runServer",
                   "-PbionicsCompatibilityTest=true",
                   f"-PchpRestartPhase={phase}", f"-PchpRestartDirectory={work}",
                   "--no-daemon", "--stacktrace", "--max-workers=2"]
        run(command, root, work / f"{phase}.log", args.timeout)
        evidence = work / f"bionics-{phase}.properties"
        if not evidence.is_file():
            raise RuntimeError(f"Missing {phase} evidence (server never executed): {work}")
        result = properties(evidence)
        if result.get("status") != "PASS" or result.get("phase") != phase:
            raise RuntimeError(f"Real-mod {phase} probe failed: {result}; see {work}")
        if result.get("assembly") != "3":
            raise RuntimeError(f"Bionics assembly was not persisted: {result}")
        if int(result.get("tickDelta", "0")) < 40:
            raise RuntimeError(f"Real worker did not tick: {result}")
        if float(result.get("displacement", "0")) <= 0.03:
            raise RuntimeError(f"Real worker did not move: {result}")
        if abs(float(result.get("rpm", "0"))) <= 0.01:
            raise RuntimeError(f"Real crank did not generate power: {result}")
        if int(result.get("initialFuel", "0")) <= int(result.get("fuel", "0")):
            raise RuntimeError(f"Finite fuel was not actually consumed: {result}")
        phases.append(result)
        print(f"CHP_BIONICS_RESTART {args.project} {phase}: PASS", flush=True)
    if phases[0]["pid"] == phases[1]["pid"]:
        raise RuntimeError("Restart used the same JVM PID")
    for field in ("uuid", "crank", "assembly"):
        if phases[0][field] != phases[1][field]:
            raise RuntimeError(f"Persisted identity changed across JVM restart: {field}")
    if int(phases[1]["initialFuel"]) > int(phases[0]["fuel"]):
        raise RuntimeError("Restored Bionics fuel increased across restart")
    (work / "bionics-summary.json").write_text(json.dumps({
        "status": "PASS", "project": args.project, "phases": phases}, indent=2))
    print(f"CHP_BIONICS_JVM_RESTART PASS {args.project}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("CHP_BIONICS_JVM_RESTART FAIL:", error, file=sys.stderr)
        raise SystemExit(1)

#!/usr/bin/env python3
"""Verify optional integration packaging in actual built CHP release jars."""

import argparse
import hashlib
import json
import sys
import tomllib
import zipfile
from pathlib import Path

CONFIG = "createhorsepower.compat.mixins.json"
PACKAGE = "net/steampn/createhorsepower/compat/mixin/"
ICY_MIXINS = {"IcyHorsePlacementMixin", "IcyHorseManagementMixin", "IcyWagonHandoffMixin"}
BIONICS_MIXINS = {"BionicsOxhaulerSoundscapeMixin"}
MIXINS = ICY_MIXINS | BIONICS_MIXINS


def verify(path: Path, platform: str) -> dict:
    with zipfile.ZipFile(path) as jar:
        files = set(jar.namelist())
        assert CONFIG in files, f"{path}: missing optional mixin config"
        config = json.loads(jar.read(CONFIG))
        assert set(config["mixins"]) == MIXINS, (\n            f"{path}: optional hooks differ from required Icy and Bionics mixins: "\n            f"missing={sorted(MIXINS - set(config[\'mixins\']))}, "\n            f"unexpected={sorted(set(config[\'mixins\']) - MIXINS)}"\n        )
        assert config["plugin"] == "net.steampn.createhorsepower.compat.mixin.IcyMixinPlugin", (
            f"{path}: missing bytecode-only optional bootstrap guard"
        )
        for name in MIXINS | {"IcyMixinPlugin"}:
            assert PACKAGE + name + ".class" in files, f"{path}: missing {name}"
        # Optional integration selectors target upstream method names with remap=false.
        # If a refmap is configured, the release jar must contain it.
        if config.get("refmap"):
            assert config["refmap"] in files, f"{path}: configured refmap is missing"
        if platform == "forge":
            metadata = tomllib.loads(jar.read("META-INF/mods.toml").decode())
            manifest = jar.read("META-INF/MANIFEST.MF").decode().replace("\r\n ", "")
            entry = next((line for line in manifest.splitlines() if line.startswith("MixinConfigs:")), "")
            assert CONFIG in entry.split(":", 1)[-1].strip().split(","), f"{path}: Forge manifest does not register config"
        else:
            metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
            assert any(item.get("config") == CONFIG for item in metadata.get("mixins", [])), (
                f"{path}: NeoForge metadata does not register config"
            )
        deps = metadata["dependencies"]["createhorsepower"]
        icy = [item for item in deps if item.get("modId") == "icys_better_horses"]
        assert len(icy) == 1, f"{path}: expected one explicit optional Icy dependency contract"
        assert icy[0]["versionRange"] == "[2.1.7]", f"{path}: unsupported Icy version range"
        if platform == "forge":
            assert icy[0].get("mandatory") is False, f"{path}: Icy accidentally required"
        else:
            assert icy[0].get("type") == "optional", f"{path}: Icy accidentally required"
        assert not any(name.startswith(("icy/betterhorses/", "assets/icys-better-horses/")) for name in files), (
            f"{path}: upstream implementation or assets must not be bundled"
        )
    return {
        "jar": str(path), "platform": platform, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "optional_icy_version": "2.1.7", "icy_hooks_packaged": sorted(ICY_MIXINS),
        "bionics_hooks_packaged": sorted(BIONICS_MIXINS), "hooks_packaged": sorted(MIXINS),
        "result": "passed",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--forge", type=Path)
    parser.add_argument("--neoforge", type=Path)
    args = parser.parse_args()
    if args.forge is None and args.neoforge is None:
        parser.error("provide at least one actual built jar")
    for platform in ("forge", "neoforge"):
        path = getattr(args, platform)
        if path is not None:
            print(json.dumps(verify(path, platform), sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())

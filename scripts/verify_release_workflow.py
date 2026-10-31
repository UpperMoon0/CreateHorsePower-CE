#!/usr/bin/env python3
"""Fail CI if release publishing can bypass packaged-jar validation."""

import re
from pathlib import Path

workflow = Path(__file__).resolve().parents[1] / ".github/workflows/release.yml"
source = workflow.read_text(encoding="utf-8")
matches = list(re.finditer(r"(?m)^      - name: (.+)$", source))
steps = {}
for index, match in enumerate(matches):
    name = match.group(1).strip()
    if name in steps:
        raise AssertionError(f"Duplicate release step: {name}")
    end = matches[index + 1].start() if index + 1 < len(matches) else len(source)
    steps[name] = (match.start(), source[match.start():end])

sequence = (
    "Verify and build both platform artifacts",
    "Read mod versions and locate artifacts",
    "Verify packaged dependency ranges and optional integrations",
    "Ensure release tag points at this commit",
    "Upload workflow artifact",
    "Publish GitHub Release",
    "Publish to CurseForge",
)
positions = [steps[name][0] for name in sequence]
assert positions == sorted(positions), "Packaged-jar verification must precede every release mutation"

gate = steps["Verify packaged dependency ranges and optional integrations"][1]
required = (
    "if: steps.guard.outputs.release == 'true'",
    "FORGE_JAR: ${{ steps.release_file.outputs.forge_jar }}",
    "NEOFORGE_JAR: ${{ steps.release_file.outputs.nf_jar }}",
    "set -euo pipefail",
    "python3 scripts/verify_compat_jar.py",
    '--forge "$FORGE_JAR"',
    '--neoforge "$NEOFORGE_JAR"',
)
for fragment in required:
    assert fragment in gate, f"Release packaged-jar gate missing: {fragment}"
print("Release workflow verifies both packaged jars before tagging or publishing.")

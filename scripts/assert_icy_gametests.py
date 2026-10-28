#!/usr/bin/env python3
"""Fail CI when the opt-in namespace silently selects zero or fewer tests."""
import argparse
from pathlib import Path
import re

parser = argparse.ArgumentParser()
parser.add_argument("--project", choices=["forge-1.20.1", "neoforge-1.21.1"], required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
wrapper = root / args.project / "src/main/java/net/steampn/createhorsepower/gametest/IcyCompatibilityGameTests.java"
expected = wrapper.read_text(encoding="utf-8").count("@GameTest(")
log = (root / args.project / "run/logs/latest.log").read_text(encoding="utf-8", errors="replace")
counts = [int(n) for n in re.findall(r"(\d+) tests are now running", log)]
passed = [int(n) for n in re.findall(r"All (\d+) required tests passed", log)]
if expected <= 0 or expected not in counts or expected not in passed:
    raise SystemExit(f"Compatibility coverage did not execute fully: expected={expected}, started={counts}, passed={passed}")
print(f"CHP_ICY_GAMETEST_COVERAGE PASS: {expected} required real-mod scenarios executed")

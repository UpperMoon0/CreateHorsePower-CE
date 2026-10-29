#!/usr/bin/env python3
"""Require every real Create: Bionics scenario and refuse startup-only 'passes'."""
import argparse
from pathlib import Path
import re

p = argparse.ArgumentParser()
p.add_argument("--project", choices=["forge-1.20.1", "neoforge-1.21.1"], required=True)
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
fixture = root / a.project / "src/main/java/net/steampn/createhorsepower/gametest/BionicsCompatibilityGameTests.java"
expected = fixture.read_text(encoding="utf-8").count("@GameTest(")
if a.project == "neoforge-1.21.1":
    actual_parts = fixture.parent / "BionicsPartGameTests.java"
    expected += actual_parts.read_text(encoding="utf-8").count("@GameTest(")
log_file = root / a.project / "run/logs/latest.log"
if not log_file.is_file():
    raise SystemExit("No real GameTest server log: " + str(log_file))
log = log_file.read_text(encoding="utf-8", errors="replace")
started = [int(n) for n in re.findall(r"(\d+) tests are now running", log)]
passed = [int(n) for n in re.findall(r"All (\d+) required tests passed", log)]
# The active namespace must select EXACTLY this suite: no false positive from
# vanilla or unrelated GameTests and no '0 tests' accidental success.
if expected < 3 or started != [expected] or passed != [expected]:
    raise SystemExit(
        f"CHP_BIONICS_ACCEPTANCE FAIL expected={expected} started={started} passed={passed}; "
        f"see {log_file}"
    )
if "createbionics" not in log.lower():
    raise SystemExit("Bionics mod name absent from runtime log")
print(f"CHP_BIONICS_ACCEPTANCE PASS {a.project}: {expected} real-mod GameTests")

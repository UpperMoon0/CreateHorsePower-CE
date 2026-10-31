#!/usr/bin/env python3
"""Fail CI if release publishing can bypass packaged-jar validation.

Parse GitHub Actions YAML rather than searching raw source text. The accepted
verification command is intentionally narrow: changes to its execution
semantics must be reviewed, not silently accepted as equivalent.
"""

from pathlib import Path

import yaml

RELEASE_WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/release.yml"

SEQUENCE = (
    "Verify and build both platform artifacts",
    "Read mod versions and locate artifacts",
    "Verify packaged dependency ranges and optional integrations",
    "Ensure release tag points at this commit",
    "Upload workflow artifact",
    "Publish GitHub Release",
    "Publish to CurseForge",
)
GATE_NAME = "Verify packaged dependency ranges and optional integrations"
RELEASE_CONDITION = "steps.guard.outputs.release == 'true'"
EXPECTED_ENV = {
    "FORGE_JAR": "$" + "{{ steps.release_file.outputs.forge_jar }}",
    "NEOFORGE_JAR": "$" + "{{ steps.release_file.outputs.nf_jar }}",
}
EXPECTED_RUN = "\n".join((
    "set -euo pipefail",
    "python3 scripts/verify_compat_jar.py \\",
    '  --forge "$FORGE_JAR" \\',
    '  --neoforge "$NEOFORGE_JAR"',
))


def verify_workflow(source: str) -> None:
    document = yaml.safe_load(source)
    assert isinstance(document, dict), "Release YAML must be a mapping"
    jobs = document.get("jobs")
    assert isinstance(jobs, dict), "Missing GitHub Actions jobs"
    release = jobs.get("release")
    assert isinstance(release, dict), "Missing release job"
    assert release.get("continue-on-error", False) is False, (
        "Release job must fail when any validation step fails"
    )
    steps = release.get("steps")
    assert isinstance(steps, list), "Release steps must be a list"

    by_name = {}
    for position, step in enumerate(steps):
        assert isinstance(step, dict), f"Malformed release step at index {position}"
        name = step.get("name")
        assert isinstance(name, str) and name, "Every release step needs a name"
        assert name not in by_name, f"Duplicate release step: {name}"
        # Reject both literal true and expression-valued flags. Fail closed.
        assert step.get("continue-on-error", False) is False, (
            f"{name}: continue-on-error must not suppress release failures"
        )
        by_name[name] = (position, step)

    positions = [by_name[name][0] for name in SEQUENCE]
    assert positions == sorted(positions), (
        "Packaged-jar verification must precede tags and every publication"
    )

    gate = by_name[GATE_NAME][1]
    assert gate.get("if") == RELEASE_CONDITION, "Release verification must not be skipped"
    assert gate.get("shell") == "bash" and "uses" not in gate, (
        "Release verification must run as a Bash shell step"
    )
    assert gate.get("env") == EXPECTED_ENV, (
        "Release verification must inspect both located release artifacts"
    )
    run = gate.get("run")
    assert isinstance(run, str) and run.strip() == EXPECTED_RUN, (
        "Release verification must execute the exact fail-closed dual-jar command"
    )

    # A later publish step using always()/failure() would bypass the default
    # success() condition and could run after a failed verification.
    for name in SEQUENCE[3:]:
        assert by_name[name][1].get("if") == RELEASE_CONDITION, (
            f"{name}: publication must require successful release validation"
        )


def main() -> None:
    verify_workflow(RELEASE_WORKFLOW.read_text(encoding="utf-8"))
    print("Release workflow parses correctly and cannot skip dual-jar verification.")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Regression tests for fail-closed GitHub Release workflow wiring."""

import unittest
from pathlib import Path

from verify_release_workflow import verify_workflow

WORKFLOW = (
    Path(__file__).resolve().parents[1] / ".github/workflows/release.yml"
)
GATE = "Verify packaged dependency ranges and optional integrations"


class ReleaseWorkflowGuardTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = WORKFLOW.read_text(encoding="utf-8")

    def replace_once(self, old, new):
        self.assertEqual(self.source.count(old), 1, f"Fixture anchor not unique: {old}")
        return self.source.replace(old, new, 1)

    def assert_rejected(self, modified):
        with self.assertRaises(AssertionError):
            verify_workflow(modified)

    def test_valid_release_workflow(self):
        verify_workflow(self.source)

    def test_commented_out_verifier_command(self):
        changed = self.replace_once(
            "          python3 scripts/verify_compat_jar.py \\",
            "          # python3 scripts/verify_compat_jar.py \\",
        )
        self.assert_rejected(changed)

    def test_verifier_left_in_yaml_comment_only(self):
        changed = self.replace_once(
            "          python3 scripts/verify_compat_jar.py \\",
            "          echo verification-skipped\n"
            "          # python3 scripts/verify_compat_jar.py \\",
        )
        self.assert_rejected(changed)

    def test_continue_on_error_in_verification_step(self):
        prefix = f"      - name: {GATE}\n"
        changed = self.replace_once(
            prefix,
            prefix + "        continue-on-error: true\n",
        )
        self.assert_rejected(changed)

    def test_expression_continue_on_error(self):
        prefix = f"      - name: {GATE}\n"
        changed = self.replace_once(
            prefix,
            prefix + "        continue-on-error: " + "$" + "{{ true }}\n",
        )
        self.assert_rejected(changed)

    def test_job_continue_on_error(self):
        changed = self.replace_once(
            "jobs:\n  release:\n",
            "jobs:\n  release:\n    continue-on-error: true\n",
        )
        self.assert_rejected(changed)

    def test_swallowing_verifier_failure(self):
        changed = self.replace_once(
            '            --neoforge "$NEOFORGE_JAR"',
            '            --neoforge "$NEOFORGE_JAR" || true',
        )
        self.assert_rejected(changed)

    def test_gate_skipped_but_publishing_allowed(self):
        prefix = f"      - name: {GATE}\n"
        changed = self.replace_once(
            prefix + "        if: steps.guard.outputs.release == 'true'\n",
            prefix + "        if: false\n",
        )
        self.assert_rejected(changed)

    def test_publisher_runs_after_failure(self):
        changed = self.replace_once(
            "      - name: Publish GitHub Release\n"
            "        if: steps.guard.outputs.release == 'true'",
            "      - name: Publish GitHub Release\n"
            "        if: always()",
        )
        self.assert_rejected(changed)

    def test_verifier_after_tag(self):
        gate_index = self.source.index(f"      - name: {GATE}\n")
        tag_index = self.source.index("      - name: Ensure release tag points at this commit\n")
        self.assertLess(gate_index, tag_index)
        block = self.source[gate_index:tag_index]
        changed = (
            self.source[:gate_index]
            + self.source[tag_index:]
            + ""  # avoid changing order elsewhere
        )
        # Insert verifier after the tag step but before artifact upload.
        upload_index = changed.index("      - name: Upload workflow artifact\n")
        changed = changed[:upload_index] + block + changed[upload_index:]
        self.assert_rejected(changed)


if __name__ == "__main__":
    unittest.main()

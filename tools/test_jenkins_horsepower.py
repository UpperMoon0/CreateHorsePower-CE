"""Contracts for portable CI packaging, no network/Gradle invocation required."""
import hashlib
import json
from types import SimpleNamespace
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import jenkins_horsepower as ci


class HorsePowerCITests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        (self.root / "gradle.properties").write_text("mod_version=1.2.12\n")
        self.root_patch = patch.object(ci, "ROOT", self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def make_jars(self):
        for target in ci.TARGETS:
            jar = ci.jar_for(target, "1.2.12")
            jar.parent.mkdir(parents=True)
            with zipfile.ZipFile(jar, "w") as output:
                output.writestr("net/steampn/createhorsepower/CreateHorsePower.class", b"valid class placeholder" * 128)

    def test_target_names_and_main_only_delegate(self):
        self.assertEqual(ci.TARGETS, ("forge-1.20.1", "neoforge-1.21.1"))
        delegate = Path(__file__).resolve().parents[1] / "Jenkinsfile"
        self.assertEqual(delegate.read_text().strip().splitlines()[-1],
                         "nstutHorsePowerCE()")

    def test_package_emits_exact_hash_provenance_and_both_targets(self):
        self.make_jars()
        with patch.object(ci, "gradle"), patch.object(ci, "script"), \
             patch.object(ci.subprocess, "run") as git:
            git.side_effect = [SimpleNamespace(returncode=2, stdout=""),
                               SimpleNamespace(returncode=0, stdout="a" * 40 + "\n")]
            ci.package()
            self.assertEqual(git.call_count, 2)
        output = self.root / "release-output"
        manifest = (output / "SHA256SUMS").read_text().splitlines()
        self.assertEqual(len(manifest), 3)
        for line in manifest:
            digest, name = line.split()
            self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), digest)
        provenance = json.loads((output / "provenance.json").read_text())
        self.assertEqual(provenance, {"source_sha": "a" * 40, "version": "1.2.12",
                                      "targets": sorted(ci.TARGETS)})

    def test_generated_files_require_real_content_parity_and_no_untracked_files(self):
        target = "forge-1.20.1"
        generated = self.root / target / "src" / "generated" / "resources"
        generated.mkdir(parents=True)
        tracked = generated / "recipe.json"
        tracked.write_text('{"id":"original"}\n', encoding="utf-8")
        (self.root / ".gitattributes").write_text(
            "**/src/generated/resources/**/*.json text eol=lf\n",
            encoding="utf-8")
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.email", "ci-fixture@example.invalid"],
                       cwd=self.root, check=True)
        subprocess.run(["git", "config", "user.name", "CI Fixture"],
                       cwd=self.root, check=True)
        subprocess.run(["git", "config", "core.autocrlf", "true"],
                       cwd=self.root, check=True)
        subprocess.run(["git", "add", "-A"], cwd=self.root, check=True)
        subprocess.run(["git", "commit", "-q", "-m", "fixture"], cwd=self.root, check=True)
        # RunData may rewrite tracked JSON with identical LF bytes on Windows.
        # Stat/CRLF warnings must not produce false failures.
        tracked.write_text('{"id":"original"}\n', encoding="utf-8")
        with patch.object(ci, "gradle") as gradle:
            ci.build(target)
            self.assertEqual(gradle.call_count, 2)
        tracked.write_text('{"id":"changed"}\n', encoding="utf-8")
        with patch.object(ci, "gradle") as gradle:
            with self.assertRaisesRegex(RuntimeError, "changed tracked"):
                ci.build(target)
            gradle.assert_called_once()
        tracked.write_text('{"id":"original"}\n', encoding="utf-8")
        (generated / "extra.json").write_text('{"unexpected":true}\n')
        with patch.object(ci, "gradle") as gradle:
            with self.assertRaisesRegex(RuntimeError, "untracked"):
                ci.build(target)
            gradle.assert_called_once()

    def test_tag_guard_rejects_collision_and_network_failure(self):
        with patch.object(ci.subprocess, 'run') as git:
            git.return_value.returncode = 0
            with self.assertRaisesRegex(RuntimeError, 'already exists'):
                ci.ensure_new_release_version('1.2.12')
            git.return_value.returncode = 128
            with self.assertRaisesRegex(RuntimeError, 'Cannot verify'):
                ci.ensure_new_release_version('1.2.12')
            git.return_value.returncode = 2
            ci.ensure_new_release_version('1.2.12')

    def test_stale_release_output_refused_without_overwrite(self):
        self.make_jars()
        (self.root / "release-output").mkdir()
        with patch.object(ci, "gradle"), patch.object(ci, "script"), \
             patch.object(ci.subprocess, "run") as git:
            git.side_effect = [SimpleNamespace(returncode=2, stdout=""),
                               SimpleNamespace(returncode=0, stdout="a" * 40 + "\n")]
            with self.assertRaisesRegex(RuntimeError, "stale artifacts"):
                ci.package()


if __name__ == "__main__":
    unittest.main()

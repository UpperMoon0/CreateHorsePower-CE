"""Contracts for portable CI packaging, no network/Gradle invocation required."""
import hashlib
import json
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
            git.return_value.stdout = "a" * 40 + "\n"
            ci.package()
            git.assert_called_once()
        output = self.root / "release-output"
        manifest = (output / "SHA256SUMS").read_text().splitlines()
        self.assertEqual(len(manifest), 3)
        for line in manifest:
            digest, name = line.split()
            self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), digest)
        provenance = json.loads((output / "provenance.json").read_text())
        self.assertEqual(provenance, {"source_sha": "a" * 40, "version": "1.2.12",
                                      "targets": sorted(ci.TARGETS)})

    def test_stale_release_output_refused_without_overwrite(self):
        self.make_jars()
        (self.root / "release-output").mkdir()
        with patch.object(ci, "gradle"), patch.object(ci, "script"), \
             patch.object(ci.subprocess, "run") as git:
            git.return_value.stdout = "a" * 40 + "\n"
            with self.assertRaisesRegex(RuntimeError, "stale artifacts"):
                ci.package()


if __name__ == "__main__":
    unittest.main()

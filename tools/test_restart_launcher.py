"""Regression: real Linux restart probes must not require gradlew's +x bit."""
import os
from pathlib import Path
import tempfile
import unittest

from scripts import run_restart_probe as icy
from scripts import run_bionics_restart_probe as bionics


class RestartLauncherTests(unittest.TestCase):
    @unittest.skipUnless(os.name == "posix", "Requires native POSIX exec permission semantics")
    def test_both_restart_probes_use_bash_for_nonexecutable_gradlew(self):
        with tempfile.TemporaryDirectory(prefix="chp-restart-launch-") as tmp:
            root = Path(tmp)
            wrapper = root / "gradlew"
            wrapper.write_text("#!/bin/sh\nprintf 'CHP_WRAPPER_OK\\n'\n", encoding="utf-8")
            wrapper.chmod(0o600)  # Deliberately lacks executable permission.
            self.assertFalse(os.access(wrapper, os.X_OK))
            for label, runner in (("icy", icy), ("bionics", bionics)):
                with self.subTest(runner=label):
                    command = runner.gradle_launcher(root)
                    self.assertEqual(command, ["bash", str(wrapper)])
                    output = root / f"{label}.log"
                    runner.run(command, root, output, 10)
                    self.assertIn("CHP_WRAPPER_OK", output.read_text(encoding="utf-8"))

    def test_missing_wrapper_rejected_before_launch(self):
        with tempfile.TemporaryDirectory(prefix="chp-restart-missing-") as tmp:
            for runner in (icy, bionics):
                with self.subTest(runner=runner.__name__):
                    with self.assertRaises(FileNotFoundError):
                        runner.gradle_launcher(Path(tmp))


if __name__ == "__main__":
    unittest.main()

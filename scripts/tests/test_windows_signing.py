"""Offline regression: fake signtool, dummy PFX, no certificate-store changes."""
import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


@unittest.skipUnless(sys.platform == "win32" and shutil.which("pwsh"), "Windows PowerShell required")
class WindowsSigningTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="chatlite-sign-test-")
        self.root = Path(self.temp.name)
        self.script = Path(__file__).resolve().parents[1] / "sign-windows.ps1"
        self.env = dict(os.environ, WINDOWS_PFX=base64.b64encode(b"dummy fixture").decode(),
                        WINDOWS_PFX_PASSWORD="fixture-password", SIGN_LOG=str(self.root / "calls.jsonl"))
        self.installer = self.root / "installer with spaces.msi"
        self.installer.write_bytes(b"fixture installer")
        self.tool = self.root / "fake tool.cmd"
        self.tool.write_text(f'@echo off\n"{sys.executable}" "{self.root / "tool.py"}" %*\nexit /b %errorlevel%\n')
        (self.root / "tool.py").write_text('''import json, os, pathlib, sys
args = sys.argv[1:]
with open(os.environ["SIGN_LOG"], "a") as log:
    log.write(json.dumps(args) + "\\n")
if args[0] == "sign":
    assert pathlib.Path(args[args.index("/f") + 1]).read_bytes() == b"dummy fixture"
sys.exit(int(os.environ.get("FAIL_" + args[0].upper(), "0")))
''')
        self.harness = self.root / "invoke.ps1"
        self.harness.write_text('''param($Script, $Tool, $Patterns)
$files = @(Get-Content -LiteralPath $Patterns -Raw -Encoding UTF8 | ConvertFrom-Json)
& $Script -SignToolPath $Tool -Files $files
''')

    def tearDown(self):
        self.temp.cleanup()

    def run_sign(self, files=None, **env):
        patterns = self.root / "patterns.json"
        patterns.write_text(json.dumps(files or [str(self.installer)]), encoding="utf-8")
        return subprocess.run([shutil.which("pwsh"), "-NoProfile", "-File", str(self.harness),
                               "-Script", str(self.script), "-Tool", str(self.tool), "-Patterns", str(patterns)],
                              env=dict(self.env, **env), capture_output=True, text=True,
                              encoding="utf-8", errors="replace", timeout=30)

    def calls(self):
        return [json.loads(line) for line in (self.root / "calls.jsonl").read_text().splitlines()]

    def assert_certificate_removed(self, calls):
        certificates = [Path(args[args.index("/f") + 1]) for args in calls if args[0] == "sign"]
        self.assertTrue(certificates)
        self.assertTrue(all(not path.exists() for path in certificates))

    def test_success_signs_and_verifies_then_removes_temporary_certificate(self):
        result = self.run_sign()
        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls()
        self.assertEqual(["sign", "verify"], [args[0] for args in calls])
        self.assertTrue(all(Path(args[-1]).samefile(self.installer) for args in calls))
        self.assert_certificate_removed(calls)

    def test_sign_failure_is_not_reported_as_success(self):
        result = self.run_sign(FAIL_SIGN="7")
        self.assertNotEqual(0, result.returncode)
        calls = self.calls()
        self.assertEqual(["sign"], [args[0] for args in calls])
        self.assert_certificate_removed(calls)

    def test_verify_failure_is_not_reported_as_success(self):
        result = self.run_sign(FAIL_VERIFY="9")
        self.assertNotEqual(0, result.returncode)
        self.assert_certificate_removed(self.calls())

    def test_missing_installer_does_not_call_signer(self):
        result = self.run_sign([str(self.root / "missing*.msi")])
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / "calls.jsonl").exists())

    def test_partial_credentials_fail_before_calling_signer(self):
        result = self.run_sign(WINDOWS_PFX_PASSWORD="")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / "calls.jsonl").exists())

    def test_overlapping_globs_sign_each_installer_only_once(self):
        second = self.root / "second.msi"
        second.write_bytes(b"second installer")
        result = self.run_sign([str(self.root / "*.msi"), str(self.installer)])
        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls()
        self.assertEqual(["sign", "verify", "sign", "verify"], [args[0] for args in calls])
        self.assertEqual({self.installer.name, second.name}, {Path(args[-1]).name for args in calls})
        self.assert_certificate_removed(calls)


if __name__ == "__main__":
    unittest.main()

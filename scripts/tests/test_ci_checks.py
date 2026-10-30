import importlib.util
from pathlib import Path
import tempfile
import unittest


def load(name):
    script = Path(__file__).resolve().parents[1] / f"{name}.py"
    spec = importlib.util.spec_from_file_location(name, script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


protocol = load("check-protocol-copies")
reports = load("check-test-results")


class CiCheckTest(unittest.TestCase):
    def test_no_reports_all_skipped_or_failing_tests_cannot_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            self.assertEqual(1, reports.check(directory))
            report = directory / "TEST-fixture.xml"
            for attributes in ('tests="0"', 'tests="2" skipped="2"',
                               'tests="2" failures="1"', 'tests="2" errors="1"'):
                report.write_text(f"<testsuite {attributes}/>", encoding="utf-8")
                self.assertEqual(1, reports.check(directory))
            report.write_text('<testsuite tests="2" skipped="1"/>', encoding="utf-8")
            self.assertEqual(0, reports.check(directory))

    def test_both_protocols_and_every_copy_are_checked_without_rewriting(self):
        with tempfile.TemporaryDirectory() as folder:
            client, backend = Path(folder) / "client", Path(folder) / "backend"
            directories = ("src/main/proto", "proto", "web/src/proto", "web/public/proto")
            for name in ("message.proto", "agent_stream.proto"):
                for root, locations in ((backend, (directories[0],)), (client, directories)):
                    for location in locations:
                        target = root / location / name
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_text("fixture\n", encoding="utf-8")
            self.assertEqual(0, protocol.check(client, backend))
            for directory in directories:
                target = client / directory / "agent_stream.proto"
                target.write_text("changed\n", encoding="utf-8")
                self.assertEqual(1, protocol.check(client, backend))
                self.assertEqual("changed\n", target.read_text(encoding="utf-8"))
                target.write_text("fixture\n", encoding="utf-8")
            (backend / directories[0] / "message.proto").unlink()
            self.assertEqual(1, protocol.check(client, backend))


if __name__ == "__main__":
    unittest.main()

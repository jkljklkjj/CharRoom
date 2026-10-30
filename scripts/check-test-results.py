"""Reject missing/empty JUnit reports as well as failing or entirely skipped suites."""
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


def check(directory: Path) -> int:
    tests = failures = skipped = 0
    for report in directory.glob("TEST-*.xml"):
        suite = ET.parse(report).getroot()
        tests += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
    print(f"{directory}: tests={tests}, failures={failures}, skipped={skipped}")
    return int(tests <= skipped or failures > 0)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("Usage: check-test-results.py <JUnit directory> [...]")
    results = [check(Path(directory)) for directory in sys.argv[1:]]
    raise SystemExit(int(any(results)))

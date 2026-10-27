#!/usr/bin/env python3
"""Report actual JUnit counts and fail if Gradle produced no test results."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

results = sorted(Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
if not results:
    sys.exit("No JUnit XML results were produced.")
counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
for path in results:
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.get(key, "0"))
print("Unit test summary:", ", ".join(f"{key}={value}" for key, value in counts.items()))
if not counts["tests"] or counts["failures"] or counts["errors"]:
    sys.exit(1)

#!/usr/bin/env python3
"""
Test suite for BMKG danger classification rules.

Validates the classifier against saved fixtures in tools/test_fixtures/.
Prints PASS/FAIL for each event and exits with status 0 if all pass, 1 otherwise.

Usage (from repo root):
  python tools/test_classifier.py                     # run all tests
  python tools/test_classifier.py --verbose           # show parsed values too
  python tools/test_classifier.py --list-tests        # just list what would be tested
"""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

# Add parent dir to path so we can import from fetch_bmkg_autogempa
SCRIPT_DIR = Path(__file__).parent
FIXTURES_DIR = SCRIPT_DIR / "test_fixtures"

# Import classification logic from the main module
sys.path.insert(0, str(SCRIPT_DIR))
from fetch_bmkg_autogempa import (  # noqa: E402
    classify_event,
    is_dangerous,
    parse_magnitude,
    parse_depth,
    max_mmi,
    has_tsunami_potential,
    pick,
)


# ---------------------------------------------------------------------------
# Test cases
# Each test case specifies:
#   - fixture: filename in test_fixtures/
#   - event_index: which event in the array (0-based), or None if single-object feed
#   - expected_dangerous: True/False
#   - expected_rules: list of rule IDs that should match (optional)
#   - description: human-readable description
# ---------------------------------------------------------------------------

TestCases = list[dict]

def get_test_cases() -> TestCases:
    return [
        # ── autogempa (live feed, typically M2.9, non-dangerous) ──
        {
            "fixture": "autogempa.json",
            "event_index": None,
            "expected_dangerous": False,
            "expected_rules": [],
            "description": "autogempa live — small event (M2.9, depth 10km, MMI III, no tsunami)",
        },
        # ── gempaterkini events ──
        {
            "fixture": "gempaterkini.json",
            "event_index": 0,
            "expected_dangerous": True,
            "expected_rules": ["high_magnitude", "moderate_magnitude_shallow"],
            "description": "gempaterkini event 1 — M6.0 depth 10km, dangerous (high mag + shallow)",
        },
        {
            "fixture": "gempaterkini.json",
            "event_index": 1,
            "expected_dangerous": False,
            "expected_rules": ["moderate_magnitude_deep"],
            "description": "gempaterkini event 2 — M5.3 depth 188km, NOT dangerous (deep)",
        },
        {
            "fixture": "gempaterkini.json",
            "event_index": 4,
            "expected_dangerous": True,
            "expected_rules": ["moderate_magnitude_shallow"],
            "description": "gempaterkini event 5 — M5.1 depth 12km, dangerous (shallow)",
        },
        {
            "fixture": "gempaterkini.json",
            "event_index": 6,
            "expected_dangerous": True,
            "expected_rules": ["moderate_magnitude_shallow"],
            "description": "gempaterkini event 7 — M5.0 depth 12km, dangerous (shallow at threshold)",
        },
        {
            "fixture": "gempaterkini.json",
            "event_index": 8,
            "expected_dangerous": True,
            "expected_rules": ["high_magnitude", "moderate_magnitude_shallow"],
            "description": "gempaterkini event 9 — M6.0 depth 31km, dangerous",
        },
    ]


# ---------------------------------------------------------------------------
# Fixture loader
# ---------------------------------------------------------------------------

def load_fixture(filename: str) -> list[dict]:
    """Load a fixture file and return list of event dicts."""
    path = FIXTURES_DIR / filename
    if not path.exists():
        print(f"  ⚠  Fixture not found: {path}")
        return []
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    inf = data.get("Infogempa") or data.get("infogempa")
    if not isinstance(inf, dict):
        return []
    g = inf.get("gempa")
    if isinstance(g, dict):
        return [g]
    if isinstance(g, list):
        return g
    return []


# ---------------------------------------------------------------------------
# Test runner
# ---------------------------------------------------------------------------

def run_tests(verbose: bool = False) -> int:
    cases = get_test_cases()
    passed = 0
    failed = 0
    total = len(cases)

    print(f"BMKG Danger Classifier — Test Suite\n")
    print(f"Fixtures dir: {FIXTURES_DIR}\n")
    print(f"{'='*70}")
    print(f"{'#':>3}  {'Result':>7}  {'Description':<50}  {'Rules':<20}")
    print(f"{'='*70}")

    for i, case in enumerate(cases, 1):
        events = load_fixture(case["fixture"])
        if not events:
            print(f"{i:>3}  {'⚠ MISSING':>7}  {case['description']:<50}")
            failed += 1
            continue

        idx = case["event_index"]
        if idx is not None:
            if idx >= len(events):
                print(f"{i:>3}  {'⚠ NOEVENT':>7}  {case['description']:<50}  (event #{idx} not found)")
                failed += 1
                continue
            event = events[idx]
        else:
            event = events[0]

        matches = classify_event(event)
        dangerous = is_dangerous(matches)
        matched_ids = sorted(m["id"] for m in matches)

        # Check expectations
        danger_ok = dangerous == case["expected_dangerous"]
        rules_ok = set(matched_ids) == set(case["expected_rules"])

        if danger_ok and rules_ok:
            result_str = "✅ PASS"
            passed += 1
        else:
            result_str = "❌ FAIL"
            failed += 1

        print(f"{i:>3}  {result_str:>7}  {case['description']:<50}  {','.join(matched_ids):<20}")

        if verbose and (not danger_ok or not rules_ok or verbose):
            print(f"      Fixture: {case['fixture']}, event #{idx if idx is not None else 0}")
            print(f"      Magnitude: {parse_magnitude(event.get('Magnitude'))}, "
                  f"Depth: {parse_depth(event.get('Kedalaman'))}km, "
                  f"MMI: {max_mmi(event.get('Dirasakan'))}, "
                  f"Tsunami: {has_tsunami_potential(event.get('Potensi'))}")
            print(f"      Expected dangerous={case['expected_dangerous']}, got={dangerous}")
            print(f"      Expected rules={set(case['expected_rules'])}, got={set(matched_ids)}")
            print()

    # Summary
    print(f"{'='*70}")
    print(f"\nResults: {passed}/{total} passed, {failed}/{total} failed\n")

    if failed == 0:
        print("✅ All tests passed!")
        return 0
    else:
        print("❌ Some tests failed — review details above.")
        return 1


# ---------------------------------------------------------------------------
# List tests (dry-run)
# ---------------------------------------------------------------------------

def list_tests() -> None:
    cases = get_test_cases()
    print("Available tests:\n")
    for i, case in enumerate(cases, 1):
        idx_info = f"event[{case['event_index']}]" if case["event_index"] is not None else "single"
        expected = "DANGEROUS" if case["expected_dangerous"] else "safe"
        rules = ", ".join(case["expected_rules"]) if case["expected_rules"] else "(none)"
        print(f"  {i:>2}. [{case['fixture']}:{idx_info}] → {expected}")
        print(f"       {case['description']}")
        print(f"       Expected rules: {rules}")
    print(f"\nTotal: {len(cases)} tests")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    args = [a.lower() for a in sys.argv[1:]]

    if "--list-tests" in args or "-l" in args:
        list_tests()
        return 0

    verbose = "--verbose" in args or "-v" in args
    return run_tests(verbose=verbose)


if __name__ == "__main__":
    raise SystemExit(main())
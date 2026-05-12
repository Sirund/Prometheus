#!/usr/bin/env python3
"""
Fetch BMKG earthquake feeds and classify danger.

Usage (from repo root):
  python tools/fetch_bmkg_autogempa.py                          # print summary (default autogempa)
  python tools/fetch_bmkg_autogempa.py --feed gempaterkini      # specify feed
  python tools/fetch_bmkg_autogempa.py --classify                # print summary + danger classification
  python tools/fetch_bmkg_autogempa.py --save-fixture            # save raw JSON to tools/test_fixtures/
  python tools/fetch_bmkg_autogempa.py --classify --save-fixture # both
"""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request

BASE_URL = "https://data.bmkg.go.id/DataMKG/TEWS"
FEEDS: dict[str, str] = {
    "autogempa": f"{BASE_URL}/autogempa.json",
    "gempaterkini": f"{BASE_URL}/gempaterkini.json",
    "gempadirasakan": f"{BASE_URL}/gempadirasakan.json",
}
FIXTURE_DIR = os.path.join(os.path.dirname(__file__), "test_fixtures")
USER_AGENT = "PrometheusHackathon/1.0 (BMKG data consumer; educational)"

# Roman numeral → integer mapping for MMI parsing
ROMAN_MAP: dict[str, int] = {
    "I": 1, "II": 2, "III": 3, "IV": 4, "V": 5,
    "VI": 6, "VII": 7, "VIII": 8, "IX": 9, "X": 10,
    "XI": 11, "XII": 12,
}


# ---------------------------------------------------------------------------
# Parsing helpers
# ---------------------------------------------------------------------------

def parse_magnitude(mag_str: str | None) -> float | None:
    """Parse magnitude string like '5.2' → float."""
    if not mag_str:
        return None
    try:
        return float(mag_str.strip())
    except (ValueError, TypeError):
        return None


def parse_depth(depth_str: str | None) -> int | None:
    """Parse depth string like '10 km' → integer km."""
    if not depth_str:
        return None
    try:
        return int(depth_str.strip().split()[0])
    except (ValueError, IndexError, TypeError):
        return None


def parse_coordinates(coord_str: str | None) -> tuple[float, float] | None:
    """Parse coordinates string like '-4.08,121.79' → (lat, lon)."""
    if not coord_str:
        return None
    parts = coord_str.strip().split(",")
    if len(parts) != 2:
        return None
    try:
        return (float(parts[0]), float(parts[1]))
    except ValueError:
        return None


def has_tsunami_potential(potensi: str | None) -> bool:
    """Check if Potensi field indicates active tsunami potential."""
    if not potensi:
        return False
    lower = potensi.lower()
    has_positive = "tsunami" in lower and "berpotensi" in lower
    is_negated = "tidak" in lower
    return has_positive and not is_negated


def max_mmi(dirasakan: str | None) -> int:
    """Return the maximum MMI integer from a Dirasakan string (e.g. 'III Kolaka, II-III' → 3)."""
    if not dirasakan:
        return 0
    numerals = re.findall(r"\b[IVXLCDM]+\b", dirasakan)
    values = [ROMAN_MAP.get(n, 0) for n in numerals]
    return max(values) if values else 0


# ---------------------------------------------------------------------------
# Danger classification (matches config/bmkg_endpoints.json rules)
# ---------------------------------------------------------------------------

DANGER_RULES: list[dict] = [
    {
        "id": "tsunami_potential",
        "severity": "CRITICAL",
        "check": lambda g: has_tsunami_potential(g.get("Potensi")),
    },
    {
        "id": "high_magnitude",
        "severity": "HIGH",
        "check": lambda g: (parse_magnitude(g.get("Magnitude")) or 0) >= 6.0,
    },
    {
        "id": "moderate_magnitude_shallow",
        "severity": "HIGH",
        "check": lambda g: (parse_magnitude(g.get("Magnitude")) or 0) >= 5.0
                           and (parse_depth(g.get("Kedalaman")) or 999) < 70,
    },
    {
        "id": "felt_intensity_damage",
        "severity": "HIGH",
        "check": lambda g: max_mmi(g.get("Dirasakan")) >= 5,  # MMI V = 5
    },
    {
        "id": "moderate_magnitude_deep",
        "severity": "LOW",
        "check": lambda g: (parse_magnitude(g.get("Magnitude")) or 0) >= 5.0
                           and (parse_depth(g.get("Kedalaman")) or 0) >= 70,
    },
]


def classify_event(g: dict) -> list[dict]:
    """Run danger rules on a single event dict. Returns list of matched rules."""
    matches = []
    for rule in DANGER_RULES:
        try:
            if rule["check"](g):
                matches.append({"id": rule["id"], "severity": rule["severity"]})
        except Exception:
            continue
    return matches


def is_dangerous(matches: list[dict]) -> bool:
    """Return True if any matched rule is CRITICAL or HIGH."""
    return any(m["severity"] in ("CRITICAL", "HIGH") for m in matches)


# ---------------------------------------------------------------------------
# Fetching
# ---------------------------------------------------------------------------

def fetch_json(url: str) -> dict:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=20) as resp:
        raw = resp.read().decode("utf-8", errors="replace")
    return json.loads(raw)


def extract_events(data: dict) -> list[dict]:
    """Extract event dict(s) from a parsed BMKG response. Always returns a list."""
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
# Display
# ---------------------------------------------------------------------------

def pick(g: dict, *keys: str) -> str:
    for k in keys:
        v = g.get(k)
        if v is not None and str(v).strip():
            return str(v).strip()
    return "(n/a)"


def print_summary(g: dict, indent: str = "") -> None:
    print(f"{indent}Tanggal:    {pick(g, 'Tanggal', 'tanggal')}")
    print(f"{indent}Jam:        {pick(g, 'Jam', 'jam')}")
    print(f"{indent}Magnitude:  {pick(g, 'Magnitude', 'magnitude')}")
    print(f"{indent}Kedalaman:  {pick(g, 'Kedalaman', 'kedalaman')}")
    print(f"{indent}Koordinat:  {pick(g, 'Coordinates', 'coordinates')}")
    print(f"{indent}Wilayah:    {pick(g, 'Wilayah', 'wilayah')}")
    print(f"{indent}Potensi:    {pick(g, 'Potensi', 'potensi')}")
    print(f"{indent}Dirasakan:  {pick(g, 'Dirasakan', 'dirasakan')[:200]}")


def print_classification(g: dict, matches: list[dict]) -> None:
    print(f"\n  ── Danger classification ──")
    if not matches:
        print("  No rules matched.")
        return
    for m in matches:
        icon = {"CRITICAL": "🔴", "HIGH": "🟠", "LOW": "🟡", "INFO": "⚪"}
        print(f"  {icon.get(m['severity'], '⚪')} {m['severity']:>8}  {m['id']}")
    if is_dangerous(matches):
        print(f"\n  🚨 ** DANGEROUS — trigger alarm **")
    else:
        print(f"\n  ✅ NOT dangerous — log only")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    feed_id = "autogempa"
    do_classify = False
    do_save = False

    # Parse args
    args = [a.lower() for a in sys.argv[1:]]
    if "--feed" in args:
        idx = args.index("--feed")
        if idx + 1 < len(args):
            feed_id = args[idx + 1]
    if "--classify" in args:
        do_classify = True
    if "--save-fixture" in args:
        do_save = True
    # Also check short forms
    if "-f" in args and not feed_id != "autogempa":
        pass  # already default
    if "-c" in args:
        do_classify = True
    if "-s" in args:
        do_save = True

    if feed_id not in FEEDS:
        print(f"Unknown feed '{feed_id}'. Options: {', '.join(FEEDS.keys())}", file=sys.stderr)
        return 1

    url = FEEDS[feed_id]
    print(f"BMKG feed: {feed_id}")
    print(f"Source:    {url}\n")

    try:
        data = fetch_json(url)
    except urllib.error.URLError as e:
        print(f"Request failed: {e}", file=sys.stderr)
        return 1
    except json.JSONDecodeError as e:
        print(f"Invalid JSON: {e}", file=sys.stderr)
        return 1

    events = extract_events(data)
    if not events:
        print("No events found in response.", file=sys.stderr)
        return 2

    # Save fixture if requested
    if do_save:
        os.makedirs(FIXTURE_DIR, exist_ok=True)
        out_path = os.path.join(FIXTURE_DIR, f"{feed_id}.json")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"Fixture saved: {out_path}\n")

    # Print each event
    for i, event in enumerate(events):
        label = f"Event {i+1} of {len(events)}" if len(events) > 1 else "Latest event"
        print(f"── {label} ──")
        print_summary(event)

        if do_classify:
            matches = classify_event(event)
            print_classification(event, matches)

        print()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
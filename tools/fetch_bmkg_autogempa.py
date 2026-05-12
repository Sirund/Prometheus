#!/usr/bin/env python3
"""
Fetch BMKG autogempa.json and print a short human-readable summary.
For local integration testing; does not modify the iOS app.

Usage (from repo root):
  python tools/fetch_bmkg_autogempa.py
"""

from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request

URL = "https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json"


def main() -> int:
    req = urllib.request.Request(
        URL,
        headers={"User-Agent": "PrometheusHackathon/1.0 (BMKG data consumer; educational)"},
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as e:
        print(f"Request failed: {e}", file=sys.stderr)
        return 1

    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        print(f"Invalid JSON: {e}", file=sys.stderr)
        return 1

    # BMKG shape: { "Infogempa": { "gempa": { ... } } }
    inf = data.get("Infogempa") or data.get("infogempa")
    if not isinstance(inf, dict):
        print("Unexpected root shape; keys:", list(data.keys())[:10])
        return 2

    g = inf.get("gempa")
    if not isinstance(g, dict):
        print("Missing Infogempa.gempa; Infogempa keys:", list(inf.keys()))
        return 2

    def pick(*keys: str) -> str:
        for k in keys:
            v = g.get(k)
            if v is not None and str(v).strip():
                return str(v).strip()
        return "(n/a)"

    print("BMKG autogempa - summary")
    print("  Source:", URL)
    print("  Tanggal:", pick("Tanggal", "tanggal"))
    print("  Jam:", pick("Jam", "jam"))
    print("  Magnitude:", pick("Magnitude", "magnitude"))
    print("  Kedalaman:", pick("Kedalaman", "kedalaman"))
    print("  Koordinat:", pick("Coordinates", "coordinates"))
    print("  Wilayah:", pick("Wilayah", "wilayah"))
    print("  Potensi:", pick("Potensi", "potensi"))
    print("  Dirasakan:", pick("Dirasakan", "dirasakan")[:200])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

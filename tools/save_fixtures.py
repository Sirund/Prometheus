#!/usr/bin/env python3
"""Save live BMKG feed data as test fixtures."""
import json
import urllib.request
import os

BASE = "https://data.bmkg.go.id/DataMKG/TEWS"
FEEDS = {
    "autogempa": f"{BASE}/autogempa.json",
    "gempaterkini": f"{BASE}/gempaterkini.json",
    "gempadirasakan": f"{BASE}/gempadirasakan.json",
}
OUT_DIR = os.path.join(os.path.dirname(__file__), "test_fixtures")
os.makedirs(OUT_DIR, exist_ok=True)

HEADERS = {"User-Agent": "PrometheusHackathon/1.0 (fixture save)"}

for name, url in FEEDS.items():
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=20) as resp:
        data = json.loads(resp.read().decode("utf-8", errors="replace"))
    out_path = os.path.join(OUT_DIR, f"{name}.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"Saved {out_path} ({os.path.getsize(out_path)} bytes)")
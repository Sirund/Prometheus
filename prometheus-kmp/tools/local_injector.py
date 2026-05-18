#!/usr/bin/env python3
"""
Prometheus Local Earthquake Data Injector

Runs an HTTP server on your local network that serves BMKG-format JSON,
so the Prometheus phone app can use it instead of the real BMKG API.
Useful for testing evacuation routes with simulated earthquake data.

Usage:
    python3 local_injector.py

Edit the CONFIG dict below to change the simulated earthquake parameters.
"""

import json
import socket
import sys
from datetime import datetime, timedelta, timezone
from http.server import HTTPServer, BaseHTTPRequestHandler

# ─── Configuration — edit these values to change the simulated earthquake ───

CONFIG = {
    "port": 8080,
    "mag": 6.8,
    "lat": -7.5,
    "lon": 115.5,
    "depth": 10,
    "tsunami": True,
    "wilayah": "Laut Bali 100 km Timur Laut Denpasar",
    "dirasakan": "MMI V-VI",
}

# ───────────────────────────────────────────────────────────────────────────

WIB = timezone(timedelta(hours=7))


def get_local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip


def build_gempa_json(
    mag: float,
    lat_deg: float,
    lon_deg: float,
    depth_km: int,
    tsunami: bool,
    wilayah: str,
    dirasakan: str,
) -> dict:
    now = datetime.now(WIB)
    date_str = now.strftime("%d-%b-%y %H:%M:%S WIB")
    time_str = now.strftime("%H:%M:%S")

    lat_dir = "LS" if lat_deg < 0 else "LU"
    lon_dir = "BT" if lon_deg >= 0 else "BB"

    gempa = {
        "Tanggal": date_str,
        "Jam": time_str,
        "DateTime": now.strftime("%Y-%m-%d %H:%M:%S"),
        "Magnitude": str(mag),
        "Kedalaman": f"{depth_km} km",
        "Coordinates": f"{lat_deg}, {lon_deg}",
        "Lintang": f"{abs(lat_deg):.2f} {lat_dir}",
        "Bujur": f"{abs(lon_deg):.2f} {lon_dir}",
        "Wilayah": wilayah,
        "Potensi": "Berpotensi tsunami" if tsunami else "Tidak berpotensi tsunami",
        "Dirasakan": dirasakan,
    }
    return gempa


def build_response(gempa: dict) -> str:
    return json.dumps(
        {
            "Infogempa": {
                "gempa": gempa,
                "metadata": {
                    "note": "INJECTED DATA — not from BMKG",
                },
            }
        },
        indent=2,
    )


class InjectorHandler(BaseHTTPRequestHandler):
    gempa_data: dict = {}

    def do_GET(self):
        path = self.path.rstrip("/")
        if path in (
            "/DataMKG/TEWS/autogempa.json",
            "/DataMKG/TEWS/gempaterkini.json",
            "/DataMKG/TEWS/gempadirasakan.json",
        ):
            body = build_response(self.gempa_data).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'{"error": "not found"}')

    def log_message(self, fmt, *args):
        sys.stderr.write("[INJECTOR] %s - %s\n" % (self.client_address[0], fmt % args))


def main():
    cfg = CONFIG
    gempa = build_gempa_json(
        mag=cfg["mag"],
        lat_deg=cfg["lat"],
        lon_deg=cfg["lon"],
        depth_km=cfg["depth"],
        tsunami=cfg["tsunami"],
        wilayah=cfg["wilayah"],
        dirasakan=cfg["dirasakan"],
    )
    InjectorHandler.gempa_data = gempa

    local_ip = get_local_ip()
    server = HTTPServer(("0.0.0.0", cfg["port"]), InjectorHandler)

    print("=" * 60)
    print("  Prometheus — Local Earthquake Data Injector")
    print("=" * 60)
    print()
    print(f"  Server: http://{local_ip}:{cfg['port']}")
    print()
    print("  Earthquake data being served:")
    print(f"    Magnitude : M {cfg['mag']}")
    print(f"    Location  : {abs(cfg['lat']):.2f}°{'S' if cfg['lat'] < 0 else 'N'}, {abs(cfg['lon']):.2f}°{'E' if cfg['lon'] >= 0 else 'W'}")
    print(f"    Depth     : {cfg['depth']} km")
    print(f"    Tsunami   : {'YES' if cfg['tsunami'] else 'no'}")
    print(f"    Wilayah   : {cfg['wilayah']}")
    print()
    print("  On your phone, set Injection IP to:")
    print(f"    {local_ip}")
    print(f"    Port: {cfg['port']}")
    print()
    print("  Edit CONFIG in local_injector.py to change parameters.")
    print("  Press Ctrl+C to stop.")
    print("=" * 60)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()


if __name__ == "__main__":
    main()

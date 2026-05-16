#!/usr/bin/env python3
"""
Prometheus Local Earthquake Data Injector

Runs an HTTP server on your local network that serves BMKG-format JSON,
so the Prometheus phone app can use it instead of the real BMKG API.
Useful for testing evacuation routes with simulated earthquake data.

Usage:
    python3 local_injector.py
    python3 local_injector.py --port 9090
    python3 local_injector.py --mag 7.2 --lat -6.2 --lon 106.8 --depth 10 --tsunami
"""

import argparse
import json
import socket
import sys
from datetime import datetime, timedelta, timezone
from http.server import HTTPServer, BaseHTTPRequestHandler

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
            self.wfile.write(b"{\"error\": \"not found\"}")

    def log_message(self, fmt, *args):
        sys.stderr.write("[INJECTOR] %s - %s\n" % (self.client_address[0], fmt % args))


def main():
    parser = argparse.ArgumentParser(description="Prometheus local earthquake data injector")
    parser.add_argument("--port", type=int, default=8080, help="Port to listen on (default: 8080)")
    parser.add_argument("--mag", type=float, default=6.5, help="Earthquake magnitude (default: 6.5)")
    parser.add_argument("--lat", type=float, default=-8.5, help="Epicenter latitude (default: -8.5)")
    parser.add_argument("--lon", type=float, default=115.5, help="Epicenter longitude (default: 115.5)")
    parser.add_argument("--depth", type=int, default=10, help="Depth in km (default: 10)")
    parser.add_argument("--tsunami", action="store_true", help="Mark as tsunami potential")
    parser.add_argument("--no-tsunami", action="store_true", help="Mark as no tsunami potential")
    parser.add_argument("--wilayah", type=str, default="Laut Bali 100 km Timur Laut Denpasar", help="Location description")
    parser.add_argument("--dirasakan", type=str, default="MMI V-VI", help="Felt intensity description")
    args = parser.parse_args()

    gempa = build_gempa_json(
        mag=args.mag,
        lat_deg=args.lat,
        lon_deg=args.lon,
        depth_km=args.depth,
        tsunami=args.tsunami,
        wilayah=args.wilayah,
        dirasakan=args.dirasakan,
    )
    InjectorHandler.gempa_data = gempa

    local_ip = get_local_ip()
    server = HTTPServer(("0.0.0.0", args.port), InjectorHandler)

    print("=" * 60)
    print("  Prometheus — Local Earthquake Data Injector")
    print("=" * 60)
    print()
    print(f"  Server: http://{local_ip}:{args.port}")
    print()
    print("  Earthquake data being served:")
    print(f"    Magnitude : M {args.mag}")
    print(f"    Location  : {abs(args.lat):.2f}°{'S' if args.lat < 0 else 'N'}, {abs(args.lon):.2f}°{'E' if args.lon >= 0 else 'W'}")
    print(f"    Depth     : {args.depth} km")
    print(f"    Tsunami   : {'YES' if args.tsunami else 'no'}")
    print(f"    Wilayah   : {args.wilayah}")
    print()
    print("  On your phone, set Injection IP to:")
    print(f"    {local_ip}")
    print(f"    Port: {args.port}")
    print()
    print("  Press Ctrl+C to stop.")
    print("=" * 60)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()


if __name__ == "__main__":
    main()

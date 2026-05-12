# BMKG Earthquake Data — Field Reference

**Source:** Badan Meteorologi, Klimatologi, dan Geofisika (BMKG) Indonesia  
**Purpose:** Reference for parsing BMKG earthquake JSON feeds in the Prometheus app  
**Maintainer:** Andi  
**Updated:** 2026-05-12

---

## JSON Root Structure

All BMKG earthquake feeds share the same top-level wrapper:

```json
{
  "Infogempa": {
    "gempa": { ... }       // autogempa — single object
    "gempa": [ ... ]       // gempaterkini / gempadirasakan — array
  }
}
```

**Important:** The root key is always `"Infogempa"` (capital I). The `data` key is always lowercase `"gempa"`.

---

## Field Dictionary

| # | Field | BMKG Key | Type | Example | Feed(s) | Notes |
|---|-------|----------|------|---------|---------|-------|
| 1 | Date | `Tanggal` | string | `"12 Mei 2026"` | all | Format: `DD Month YYYY` in Indonesian. Month names: Januari, Februari, Maret, April, Mei, Juni, Juli, Agustus, September, Oktober, November, Desember |
| 2 | Time (local) | `Jam` | string | `"04:06:20 WIB"` | all | 24-hour format with timezone suffix (WIB = UTC+7, WITA = UTC+8, WIT = UTC+9) |
| 3 | Timestamp (UTC) | `DateTime` | string (ISO 8601) | `"2026-05-11T21:06:20+00:00"` | all | **Use this as the unique event identifier.** ISO 8601 format with UTC offset. |
| 4 | Coordinates | `Coordinates` | string | `"-4.08,121.79"` | all | `latitude,longitude`. Negative lat = South, negative lon = West. For Indonesia, lat is always South (negative) or slightly North, lon is always East (positive). |
| 5 | Latitude (text) | `Lintang` | string | `"4.08 LS"` | all | Human-readable: `LS` = Lintang Selatan (South), `LU` = Lintang Utara (North) |
| 6 | Longitude (text) | `Bujur` | string | `"121.79 BT"` | all | Human-readable: `BT` = Bujur Timur (East) |
| 7 | Magnitude | `Magnitude` | string | `"2.9"` | all | **Parse as Float.** BMKG uses Richter scale / unified magnitude. Always positive. |
| 8 | Depth | `Kedalaman` | string | `"10 km"` | all | **Parse integer:** extract digits before ` km`. In km. |
| 9 | Location | `Wilayah` | string | `"Pusat gempa berada di darat 12 km barat daya Kolaka Timur"` | all | Free-text location description in Indonesian. May include: distance + direction + nearest city + province. |
| 10 | Tsunami Potential | `Potensi` | string | `"Tidak berpotensi tsunami"` | all | **Key field for danger classification.** See notes below for parsing rules. |
| 11 | Felt Reports | `Dirasakan` | string | `"III Kolaka Timur, II-III Kolaka"` | autogempa, gempadirasakan | MMI (Modified Mercalli Intensity) scale reports from different locations. Format: `MMI Location, MMI Location, ...`. **Not present in gempaterkini.** |
| 12 | Shakemap | `Shakemap` | string (filename) | `"20260512040620.mmi.jpg"` | autogempa only | Image filename for the shakemap. Full URL is: `https://data.bmkg.go.id/DataMKG/TEWS/{shakemap}`. **Only on autogempa feed.** |

---

## Feed-Specific Differences

| Feature | autogempa | gempaterkini | gempadirasakan |
|---------|-----------|--------------|----------------|
| Root → data type | single object | array of objects | array of objects |
| Has `Dirasakan`? | ✅ Yes | ❌ No | ✅ Yes |
| Has `Shakemap`? | ✅ Yes | ❌ No | ❌ No |
| Has `Potensi`? | ✅ Yes | ✅ Yes | ✅ Yes |
| Has `DateTime`? | ✅ Yes | ✅ Yes | ✅ Yes |
| Recommended use | Primary live monitor | Historical context | Felt intensity cross-check |

---

## Parsing Recipes

### 1. Parse Magnitude (to Float)

```python
mag_str = "5.2"
magnitude = float(mag_str)  # → 5.2
```

Edge cases: always a valid float string. No special suffices.

### 2. Parse Depth (to Int)

```python
depth_str = "10 km"
depth_km = int(depth_str.split()[0])  # → 10

# handles edge cases:
depth_str = "1 km"
depth_km = int(depth_str.split()[0])  # → 1
```

### 3. Parse Coordinates (to Lat/Lon Floats)

```python
coord_str = "-4.08,121.79"
lat, lon = [float(x) for x in coord_str.split(",")]  # lat = -4.08, lon = 121.79
```

### 4. Parse DateTime (unique ID)

```python
from datetime import datetime
dt_str = "2026-05-11T21:06:20+00:00"
event_time = datetime.fromisoformat(dt_str)
```

### 5. Check Tsunami Potential

```python
potensi = "Tidak berpotensi tsunami"  # or "Berpotensi tsunami"

def has_tsunami_potential(potensi: str) -> bool:
    lower = potensi.lower()
    has_positive = "tsunami" in lower and "berpotensi" in lower
    is_negated = "tidak" in lower
    return has_positive and not is_negated
```

### 6. Extract Max MMI from Dirasakan

```python
import re

dirasakan = "III Kolaka Timur, II-III Kolaka"

# MMI is always a Roman numeral: I, II, III, IV, V, VI, VII, VIII, IX, X, XI, XII
# May include ranges like "II-III" (meaning II to III)
ROMAN_MAP = {
    "I": 1, "II": 2, "III": 3, "IV": 4, "V": 5,
    "VI": 6, "VII": 7, "VIII": 8, "IX": 9, "X": 10,
    "XI": 11, "XII": 12
}

def max_mmi(dirasakan: str) -> int:
    """Return the maximum MMI integer value from a Dirasakan string."""
    # Find all Roman numerals (with or without ranges)
    numerals = re.findall(r'\b[IVXLCDM]+\b', dirasakan)
    values = [ROMAN_MAP.get(n, 0) for n in numerals]
    return max(values) if values else 0

# Example:
# "III Kolaka Timur, II-III Kolaka" → max of [3, 2, 3] → 3
# "V Kolaka, VI Makassar" → max of [5, 6] → 6
```

---

## Danger Classification Quick Reference

| Rule | Condition | Severity |
|------|-----------|----------|
| Tsunami potential | `Potensi` contains "tsunami" AND NOT "tidak" | CRITICAL |
| High magnitude | Magnitude >= 6.0 | HIGH |
| Moderate + shallow | Mag >= 5.0 AND depth < 70 km | HIGH |
| Strong shaking | Max MMI >= V (5) | HIGH |
| Moderate + deep | Mag >= 5.0 AND depth >= 70 km | LOW (log only) |
| Small event | Mag < 5.0 | INFO (ignore) |

**Alarm triggers when ANY CRITICAL or HIGH rule matches.**

---

## Full Example: autogempa.json

```json
{
  "Infogempa": {
    "gempa": {
      "Tanggal": "12 Mei 2026",
      "Jam": "04:06:20 WIB",
      "DateTime": "2026-05-11T21:06:20+00:00",
      "Coordinates": "-4.08,121.79",
      "Lintang": "4.08 LS",
      "Bujur": "121.79 BT",
      "Magnitude": "2.9",
      "Kedalaman": "10 km",
      "Wilayah": "Pusat gempa berada di darat 12 km barat daya Kolaka Timur",
      "Potensi": "Gempa ini dirasakan untuk diteruskan pada masyarakat",
      "Dirasakan": "III Kolaka Timur, II-III Kolaka",
      "Shakemap": "20260512040620.mmi.jpg"
    }
  }
}
```

Analysis for this event:
- Magnitude: 2.9 (< 5.0 → INFO)
- Depth: 10 km
- Potensi: "Gempa ini dirasakan..." (no tsunami → safe)
- Dirasakan max MMI: III (< V → safe)
- **Result: NOT dangerous. No alarm.**

---

## Full Example: gempaterkini.json

```json
{
  "Infogempa": {
    "gempa": [
      {
        "Tanggal": "05 Mei 2026",
        "Jam": "13:44:50 WIB",
        "DateTime": "2026-05-05T06:44:50+00:00",
        "Coordinates": "-10.11,119.30",
        "Lintang": "10.11 LS",
        "Bujur": "119.30 BT",
        "Magnitude": "6.0",
        "Kedalaman": "10 km",
        "Wilayah": "37 km BaratDaya WANOKAKA-NTT",
        "Potensi": "Tidak berpotensi tsunami"
      }
    ]
  }
}
```

Analysis:
- Magnitude: 6.0 (>= 6.0 → HIGH)
- Depth: 10 km (< 70)
- Potensi: "Tidak berpotensi tsunami" (safe)
- **Result: DANGEROUS (M 6.0+). Trigger alarm.**

---

## Attribution

Data sourced from BMKG (Badan Meteorologi, Klimatologi, dan Geofisika) Indonesia.  
Always credit BMKG in the app UI and any published materials.
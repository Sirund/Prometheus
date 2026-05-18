# Prometheus

**Indonesia has 277 million people living on the Ring of Fire — the most seismically active region on Earth. Yet when a major earthquake strikes, the first thing that fails is the network, and that is exactly when most early warning systems go silent.**

Prometheus is a dual-platform mobile app (iOS + Android) that puts the entire early warning and survival pipeline directly on the device. It combines **BMKG open seismic and weather data** with **Gemma 4 running fully offline on the phone** — so the alarm fires, the spoken briefing plays, and the survival assistant answers questions even when every cell tower in the region is down.

Built by **Team Gravity Falls** for the Google Gemma Hackathon — May 2026.

---

## The Problem

Indonesia's earthquake early warning is fundamentally broken for the realities of a large, geographically dispersed population in a developing country:

- **Network-dependent alerts** — standard push notifications require working mobile data. Cell towers go down within minutes of a major seismic event. By the time an alert reaches a phone, the window for protective action may already have closed.
- **Generic guidance** — existing apps send template messages like "earthquake detected." They do not use the structured data BMKG publishes (magnitude, depth, felt intensity, tsunami potential) to give people specific, actionable instructions for *the event that just happened*.
- **No offline AI reasoning** — every LLM-powered assistant in existence requires a server call. Post-disaster connectivity is unreliable for hours or days. Cloud AI is useless exactly when it is needed most.
- **Accessibility gap** — in a disaster, reading a screen is hard. Visually impaired users and anyone in a dark, smoky, or high-stress environment have no voice-first option.

This is not only an Indonesia problem. The BMKG integration pattern is directly applicable to any country with an open seismic or meteorological data API — Philippines PHIVOLCS, Japan JMA, Nepal NSMC, Turkey AFAD. **Prometheus is a template for what developing-country disaster AI should look like.**

---

## What It Does

### MONITOR tab
- Polls BMKG open JSON feeds (`autogempa.json`, `gempaterkini.json`) on a configurable interval
- Classifies events by magnitude, depth, felt intensity, and tsunami potential against on-device danger rules
- When a dangerous event is detected: audible alarm fires + Gemma 4 generates a **spoken emergency briefing** grounded in the actual BMKG payload — not a generic template
- Shows live **weather forecast** (temperature, humidity, wind) from BMKG's forecast API, keyed by province
- Shows **nowcast weather warnings** from BMKG's RSS feed — severe weather alerts displayed and spoken

### EVACUATE tab
- Plots the BMKG epicentre on a map (Google Maps SDK)
- Computes and displays the **bearing and cardinal direction** from the epicentre to the user's location — "Evacuate North-East (42°)"
- Overlays the hazard zone and computes the shortest exit route

### ASSISTANT tab — CHAT panel
- Streaming conversation with **Gemma 4 on-device** using a survival-focused system prompt
- Covers first aid, shelter, water sourcing, evacuation planning, and Indonesia-specific hazard awareness
- Works completely offline after the one-time model download (~2.4 GB)
- Supports **image attachment** (camera or photo library) — the image is analyzed and its description is injected into the query
- Persists conversation history across sessions; supports multiple named conversations
- BMKG earthquake context is automatically appended to the system prompt when a dangerous event is active

### ASSISTANT tab — TALK panel
- **Hold-to-speak voice interface** — press and hold the mic, speak, release
- Uses `SFSpeechRecognizer` for on-device speech-to-text
- Simultaneously captures a camera frame and runs scene description via Apple Vision framework
- Sends the combined voice query + camera context to Gemma 4 via a **one-shot conversation** (does not pollute the chat history)
- Gemma's response is spoken back immediately via `AVSpeechSynthesizer`
- Designed for eyes-free, hands-free, voice-first use in disaster situations

---

## Why Gemma Changes Everything

Most disaster apps are databases with push notifications. Prometheus is different because **Gemma 4 on-device is a reasoning engine, not a lookup table**.

When a M 7.8 earthquake strikes 12 km below the Banda Sea, Prometheus does not show a template card. It injects the actual BMKG payload — coordinates, magnitude, depth, felt intensity, tsunami flag — into an emergency system prompt and lets Gemma reason about *this specific event*. The output is a spoken briefing tailored to what just happened.

In the post-disaster period — hours or days when networks are down and people are dealing with injuries, displacement, and fear — Gemma serves as a **universal survival assistant**. It answers first aid questions, helps plan evacuation routes, describes what the camera sees, and responds to voice queries when typing is impossible. This capability is not Indonesia-specific. The same model, the same prompts, and the same pipeline work anywhere on Earth.

**The combination of BMKG-grounded alerts (local, developing-country data) and Gemma's general reasoning (universal, offline) is the core of what makes Prometheus replicable across the developing world.**

---

## Team Gravity Falls

| Person | Responsibility |
|--------|----------------|
| **Pelangi** | iOS app: SwiftUI architecture, BMKG polling + danger classification, alarm pipeline, TTS/TTS-stop, Gemma conversation wiring, Google Maps SDK, evacuation routing UI, weather/nowcast integration, camera input, image attachment, voice (Talk) panel, permissions |
| **Andi** | BMKG integration: endpoint selection, field parsing, danger classification rules (magnitude thresholds, depth bands, tsunami flags), weather and nowcast feeds, test fixtures against live payloads |
| **Arund** | Gemma 4 behaviour: survival chat prompt, emergency briefing prompt, vision-accessibility prompt, voice prompt (Talk panel), prompt safety, response length constraints for TTS, evaluation against real crisis scenarios |
| **Adfi** | Video production: shooting, directing, and editing the demo and pitch video |
| **Alifa** | Cinematography: visual storytelling, app walkthrough footage, post-production |

---

## Tech Stack

| Layer | Android (KMP) | iOS |
|-------|---------------|-----|
| App | Jetpack Compose | Swift + SwiftUI |
| Shared logic | KMP shared module (`shared/`) | KMP shared module (`shared/`) |
| Hazard data | BMKG open JSON + RSS | BMKG open JSON + RSS |
| On-device LLM | Gemma 4 E2B · LiteRT LM (Android SDK) | Gemma 4 E2B · LiteRT LM (Swift SDK) |
| Evacuation map | Google Maps SDK for Android | Google Maps SDK for iOS |
| Voice output | Android TextToSpeech | AVSpeechSynthesizer |
| Voice input | Android SpeechRecognizer | SFSpeechRecognizer + AVAudioEngine |
| Camera / vision | CameraX → Apple Vision framework | AVCaptureSession → VNRecognizeTextRequest |
| Alerts | Notifications + alarm audio | Local notifications + alarm audio |

---

## Repository Layout

| Path | Purpose |
|------|---------|
| `prometheus-kmp/` | KMP project — shared Kotlin module + Android app (Jetpack Compose) |
| `prometheus-kmp/androidApp/` | Android app: Compose UI, navigation, inference, BMKG services |
| `prometheus-kmp/shared/` | KMP shared module: BMKG models, danger classifier, system prompts |
| `prometheus-app/` | iOS app — SwiftUI, LiteRT LM, BMKG polling, Google Maps |
| `landing/` | Landing page (`index.html`) for hackathon submission |
| `config/` | Shared BMKG endpoint references and prompt drafts |
| `tools/` | Small scripts for BMKG integration testing |

---

## Quick Start

### Android
```bash
# Prerequisites: JDK 17+, Android SDK API 36
cd prometheus-kmp
./gradlew :androidApp:assembleDebug
adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### iOS
Open `prometheus-app/prometheus-app.xcodeproj` in Xcode 16+. Build and run on a physical iPhone (on-device inference does not work in the simulator).

### BMKG smoke check
```bash
python tools/fetch_bmkg_autogempa.py
```

---

## BMKG Open Data

Indonesia's Meteorology, Climatology, and Geophysics Agency publishes open feeds used by Prometheus:

**Seismic:**
- `https://data.bmkg.go.id/DataMKG/TEWS/autogempa.json` — latest event
- `https://data.bmkg.go.id/DataMKG/TEWS/gempaterkini.json` — recent list (M 5.0+)
- `https://data.bmkg.go.id/DataMKG/TEWS/gempadirasakan.json` — felt events

**Weather:**
- `https://api.bmkg.go.id/publik/prakiraan-cuaca?adm4=<code>` — forecast by station code
- `https://www.bmkg.go.id/alerts/nowcast/id` — nowcast RSS feed

Always credit BMKG in any derivative work and respect their terms of use.

---

## The Broader Vision

Prometheus is built for Indonesia first — because Indonesia needs it most urgently. But the architecture is intentionally generic:

- Any country with an open seismic API can replace `BMKGPollingService` with a local equivalent
- The danger classification rules are configurable thresholds, not hardcoded to Indonesian event patterns
- Gemma 4 on-device requires no localization — it reasons in any language and about any disaster type
- The TALK panel's voice-first interface is particularly valuable in low-literacy contexts

The real opportunity is a world where every country on an active fault line has access to a free, open-source, offline-capable disaster AI. Prometheus is a working proof that this is technically achievable today.

---

*Hackathon project — not a substitute for official emergency instructions or BMKG warnings.*

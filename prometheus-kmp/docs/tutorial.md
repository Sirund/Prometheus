# Prometheus Android — Tutorial

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.2.20 | Language |
| Jetpack Compose | BOM 2024.12 | UI framework |
| Ktor | 3.1.0 | BMKG HTTP client |
| kotlinx.serialization | 1.7.3 | BMKG JSON parsing |
| kotlinx.coroutines | 1.9.0 | Async polling |
| CameraX | 1.4.1 | Camera preview + capture |
| LiteRT-LM | 0.11.0 | On-device Gemma 4 inference |
| Google Maps Compose | 4.3.3 | Evacuation map |
| Material Icons Extended | — | Icon set |

Full catalog: `gradle/libs.versions.toml`

## Building

```bash
cd prometheus-kmp
./gradlew :androidApp:assembleDebug
```

## App features

### Monitor tab
- Auto-polls BMKG every 60s for latest earthquake
- Shows magnitude, location, depth, felt reports, tsunami potential
- Threat level banner (green/yellow/orange/red)
- System status card (alarm + TTS readiness)
- Tap **REFRESH BMKG** to force a poll

### Evacuation tab
- Google Maps view with epicentre marker + danger radius circle
- User location marker (blue)
- Evacuation route from user position to outside danger radius
- Routing details panel with travel times (walk/run/cycle/motor/car)

### Chat tab
- Offline survival assistant powered by Gemma 4
- Mode toggle: SURVIVAL CHAT / EMERGENCY BRIEF
- Conversation history saved to device
- Image attachment support (camera/gallery)
- Markdown rendering in chat bubbles

### Vision tab
- Camera preview with gesture controls
- Hold to record voice, tap to capture image
- Double-tap to send to Gemma 4 for analysis
- TTS spoken response (accessibility mode)

## Theme

- Dark/light mode toggle in bottom navigation bar
- Default follows system setting
- Preference saved across app restarts
- System bars adapt to current theme

## Permissions

The app requests on first launch:
- **Notifications** (Android 13+) — for alarm alerts
- **Location** (fine) — for evacuation routing
- **Camera** — for vision mode
- **Microphone** — for voice input

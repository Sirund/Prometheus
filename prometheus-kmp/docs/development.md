# Prometheus KMP — Development Guide

## Prerequisites

| Tool | Version | Required for |
|------|---------|-------------|
| JDK | 17+ | Gradle + Kotlin compiler |
| Android SDK | 36 | Android app build |
| Xcode | 16+ | iOS app build (macOS only) |
| LiteRT-LM | — | On-device Gemma 4 inference |

## Project structure

```
prometheus-kmp/
├── shared/                        # KMP shared module (Android + iOS)
│   └── src/
│       ├── commonMain/            # Kotlin common code
│       │   ├── model/             # BMKG models, EarthquakeEvent, DangerClassifier
│       │   ├── network/           # BMKGClient (Ktor HTTP)
│       │   ├── monitor/           # BMKGPollingManager, EmergencyBriefingFormatter
│       │   └── prompt/            # System prompts (emergency + survival)
│       ├── commonTest/            # Kotlin tests (24 tests)
│       ├── androidMain/           # Android-specific expect/actual
│       └── iosMain/               # iOS-specific expect/actual
├── androidApp/                    # Android app (Jetpack Compose)
│   └── src/main/java/com/prometheus/android/
│       ├── navigation/            # Bottom navigation (4 tabs)
│       ├── service/               # BMKGPollingController, PrometheusAlarmManager
│       ├── inference/             # InferenceManager (LiteRT-LM Android stub)
│       └── ui/                    # Compose screens (dashboard, assistant, vision, library)
├── iosApp/                        # iOS app (SwiftUI)
│   └── iosApp/
│       ├── *.swift                # SwiftUI views + inference + polling services
│       └── iosApp.xcodeproj/      # Xcode project (imports shared framework)
├── build.gradle.kts               # Root Gradle configuration
├── settings.gradle.kts            # Includes :shared and :androidApp
├── gradle.properties              # JDK path, Android SDK config
└── gradle/libs.versions.toml      # Version catalog (Kotlin 2.1, Ktor 3.1, AGP 8.8)
```

## Architecture

```
BMKG autogempa.json
    │  (60s poll via BMKGPollingManager in shared module)
    ▼
EarthquakeEvent + DangerClassifier  (shared/commonMain)
    │
    ├── if dangerous ──► EmergencyBriefingFormatter (shared)
    │                       │
    │                       ├─ Android: Notification + TextToSpeech + alarm
    │                       └─ iOS: UNNotification + AVSpeechSynthesizer
    │
    └── always ──► onNewEvent → dashboard update
```

## Building

### Android

```bash
cd prometheus-kmp
./gradlew :androidApp:assembleDebug
```

APK at: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

Install on device:
```bash
adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

The app requests notification permission on first launch, then starts polling BMKG autogempa every 60 seconds. Dangerous events trigger a notification + TTS briefing (Indonesian) + alarm sound.

### iOS

Requires macOS with Xcode 16+.

```bash
# 1. Build the shared Kotlin framework
cd prometheus-kmp
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# 2. Open Xcode project
open iosApp/iosApp.xcodeproj

# 3. Build & run from Xcode (Product → Run)
```

The Xcode project includes a build phase script that runs the Gradle task automatically.

## Testing

```bash
# 24 unit tests (BMKG models + danger classifier + edge cases)
./gradlew :shared:jvmTest

# Run all checks (tests + lint)
./gradlew :shared:check

# Android instrumented tests (requires emulator/device)
./gradlew :androidApp:connectedCheck
```

Tests use the same BMKG fixture files as the Python test suite (`tools/test_fixtures/`).

## Key components

### shared module
- **EarthquakeEvent** — BMKG JSON model with computed properties (magnitudeValue, depthKm, maxMMI, coordinatePair, hasTsunamiPotential)
- **DangerClassifier** — 5 rules: tsunami (CRITICAL), high magnitude ≥6.0 (HIGH), moderate+shallow ≥5.0 <70km (HIGH), felt intensity ≥MMI V (HIGH), moderate+deep (LOW)
- **BMKGClient** — Ktor HTTP client for BMKG feeds
- **BMKGPollingManager** — Coroutine-based polling loop, deduplication by DateTime
- **EmergencyBriefingFormatter** — Formats event data for display or Gemma prompt

### Android
- **PrometheusAlarmManager** — Notification channels, TextToSpeech (id-ID), alarm media player
- **BMKGPollingController** — Wires shared polling manager to Android alarm system
- **InferenceManager** — LiteRT-LM Kotlin integration point for Gemma 4

### iOS
- **BMKGPollingService** — Timer-based polling calling shared BMKGClient
- **PrometheusAppDelegate** — UNUserNotificationCenter setup, auto-starts polling
- **InferenceManager** — LiteRT-LM Swift integration (already functional)

## Dependencies

| Library | Version | Used in |
|---------|---------|---------|
| Kotlin | 2.1.20 | All modules |
| Ktor | 3.1.0 | shared (BMKG networking) |
| kotlinx.serialization | 1.7.3 | shared (JSON decoding) |
| kotlinx.coroutines | 1.9.0 | shared (async polling) |
| Jetpack Compose | BOM 2024.12 | Android UI |
| LiteRT-LM | latest | iOS inference (Swift), Android inference (Kotlin) |

## Migrating from original iOS-only project

The original iOS project is at `prometheus-app/`. The KMP project at `prometheus-kmp/` replaces it:

- **iOS** — Code moved to `prometheus-kmp/iosApp/`. Same SwiftUI views + LiteRT-LM inference, now imports the shared framework for models and networking.
- **Android** — New Compose app that mirrors the iOS UI, shares business logic via the KMP module.
- **Shared** — BMKG models, danger classifier, Ktor HTTP client, polling manager, system prompts — all in common Kotlin code.

## Credits

BMKG earthquake data: https://data.bmkg.go.id
LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM

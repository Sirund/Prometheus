# LiteRT-LM Integration (Gemma 4 On-Device)

## Activating real Gemma 4 inference

### 1. Dependency

Already added in `androidApp/build.gradle.kts`:
```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")
```

### 2. Download a Gemma 4 model

LiteRT-LM models are available on HuggingFace:
https://huggingface.co/litert-community

Download a `.litertlm` model file (e.g., Gemma 4 E2B ~2.4GB) and push to your device:

```bash
# Push model to device
adb push gemma4.litertlm /data/local/tmp/gemma4.litertlm
# Or copy to app's files dir
adb push gemma4.litertlm /sdcard/gemma4.litertlm
```

### 3. Replace stub files

Copy the real LiteRT-LM files up one directory:

```bash
cd androidApp/src/main/java/com/prometheus/android/inference
cp litertlm/InferenceManager.kt .
cp litertlm/EmergencyInferenceManager.kt .
```

### 4. Rebuild

```bash
./gradlew :androidApp:assembleDebug
adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Model locations (searched in order)

1. `context.getExternalFilesDir(null)/gemma4.litertlm`
2. `context.filesDir/gemma4.litertlm`
3. `/data/local/tmp/gemma4.litertlm`
4. `/sdcard/gemma4.litertlm`

## Real vs stub

| Feature | Stub (active) | Real LiteRT-LM |
|---------|---------------|----------------|
| Inference | Hardcoded responses | Gemma 4 on-device |
| Streaming | Single response | Flow<Message> streaming |
| Emergency briefing | Static text | Gemma-generated with full BMKG context |
| Model download | None | Manual from HuggingFace |
| GPU accel | None | GPU via LiteRT |

## API reference

- `Engine(config).initialize()` → load model
- `engine.createConversation(config?)` → Conversation
- `conversation.sendMessageAsync(text).collect { }` → stream response
- `conversation.sendMessage(text)` → synchronous response
- `ConversationConfig(systemInstruction, samplerConfig, ...)`

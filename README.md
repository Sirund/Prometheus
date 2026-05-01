# Prometheus

Offline-first disaster response intelligence. Runs entirely on your phone.
No internet. No servers. No cost per query.

Built by **Team Gravity Falls** for Google Gemma hackathon — May 2026.

## What it does

- Ask survival questions by voice, text, or photo
- Get answers from a locally-stored knowledge base
- Works with zero signal — airplane mode from start to finish

## Team Gravity Falls

| Person  | In Charge of                          |
|---------|-------------------------------|
| Pelangi | iOS app, vision, speech       |
| Andi    | Knowledge ingestion pipeline  |
| Arund   | RAG (retrieval + generation)  |

## Tech stack

| Layer      | Technology                          |
|------------|-------------------------------------|
| App        | Swift + SwiftUI                     |
| Inference  | Gemma 4 E2B via LiteRT              |
| Vision     | MediaPipe iOS SDK                   |
| Speech     | Apple SFSpeechRecognizer / AVSpeechSynthesizer |
| Embeddings | Gemma embedding model               |
| Search     | Faiss (C++ iOS bridge)              |
| Indexing   | Google Colab + Document AI          |

## Quick start

1. Clone the repo
2. Open `ios/Prometheus.xcodeproj` in Xcode
3. Download model weights — see `ios/README.md`
4. Download index files from Drive — see `knowledge/README.md`
5. Build and run on device (simulator won't work — LiteRT needs real hardware)

## Architecture

See `docs/architecture.md` for the full system diagram.
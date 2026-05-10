# Knowledge Ingestion Pipeline

Pipeline ini menyiapkan output ekstraksi PDF mentah menjadi dataset siap retrieval.

## Input

File JSON hasil ekstraksi (`parents` + `children`), contoh:
- `book/Essential-Survival-Skills_database.json`
- `book/The-Ultimate-Guide-to-Rebuilding_database.json`

## Jalankan pipeline

```bash
python src/chunking/ingestion_pipeline.py \
  --inputs book/Essential-Survival-Skills_database.json book/The-Ultimate-Guide-to-Rebuilding_database.json \
  --queries src/chunking/smoke_queries.json \
  --out_dir src/chunking/artifacts \
  --ingestion_version 2026-05-05
```

## Output

- `src/chunking/artifacts/dataset_clean.json`
- `src/chunking/artifacts/dataset_manifest.json`
- `src/chunking/artifacts/smoke_test_results.json`
- `src/chunking/artifacts/ingestion_report.md`

## Contract

Skema final dijelaskan di `src/chunking/SCHEMA_CONTRACT.md`.

# Knowledge Ingestion Pipeline

This pipeline transforms raw PDF extraction output into a retrieval-ready dataset used by the RAG team and iOS app.

---

## Pipeline Architecture (2 Stages)

```
Raw PDF
    │
    ▼
┌─────────────────────────────────────┐
│  Stage 1: llm_extract_pdf.py        │
│  Text extraction via LLM (LM Studio)│
│  Output: JSON {parents, children}   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  Stage 2: ingestion_pipeline.py     │
│  Chunking → Quality Gate → Smoke    │
│  Output: dataset_clean.json + assets│
└─────────────────────────────────────┘
    │
    ▼
RAG Team (Arund) → Faiss Indexing
App Team (Pelangi) → iOS Retrieval
```

---

## Stage 1: `llm_extract_pdf.py` — PDF Extraction with LLM

This script runs **separately** (manual, requires LM Studio running on localhost:1234).

### Workflow

1. **Read PDF** — Uses PyMuPDF (`fitz`) to extract text per page
2. **Clean with LLM** — Sends raw text per page to LM Studio (Gemma via LiteRT):
   - Prompt instructs LLM to discard junk (copyright, TOC, etc.)
   - Fixes broken sentences into coherent paragraphs
   - Returns `EMPTY` if the page has no useful information
3. **Split into sentences** — Each sentence becomes a `children` entry with a unique ID
4. **Atomic save** — Writes to `.tmp` file first, then renames to final file. Survives sudden power loss.
5. **Auto-resume** — If interrupted (Ctrl+C or crash), next run continues from the last saved page

### Stage 1 Output

JSON file with structure:
```json
{
  "parents": {
    "page_1": "Cleaned text page 1...",
    "page_2": "Cleaned text page 2..."
  },
  "children": [
    {
      "id": "page_1_chunk_0",
      "parent_id": "page_1",
      "page_number": 1,
      "search_text": "First sentence."
    }
  ]
}
```

### How to Run

```bash
# Make sure LM Studio is running at http://127.0.0.1:1234
python src/chunking/llm_extract_pdf.py
```

> **Note:** The PDF path is currently hardcoded at line 119. Edit the file directly to change the PDF.

---

## Stage 2: `ingestion_pipeline.py` — Normalization, Quality Gate & Smoke Test

This is the **core ingestion pipeline**. It takes JSON from Stage 1 and:
- Splits text into retrieval-optimized chunks
- Applies quality gates to discard noise
- Runs a smoke test to verify retrieval quality
- Outputs a production-ready dataset + reports

### How to Run

```bash
python src/chunking/ingestion_pipeline.py \
  --inputs book/Essential-Survival-Skills_database.json book/The-Ultimate-Guide-to-Rebuilding_database.json \
  --queries src/chunking/smoke_queries.json \
  --out_dir src/chunking/artifacts \
  --ingestion_version 2026-05-05
```

### CLI Arguments

| Argument | Default | Description |
|---------|---------|-------------|
| `--inputs` | (required) | One or more extraction JSON files |
| `--queries` | 10 default queries | JSON file with smoke test queries |
| `--out_dir` | `src/chunking/artifacts` | Output directory |
| `--ingestion_version` | `2026-05-05` | Process version label |
| `--language` | `en` | Language code |
| `--min_chars` | `80` | Minimum chunk length |
| `--max_chars` | `1200` | Maximum chunk length |
| `--top_k` | `3` | Top-K retrieval for smoke test |

---

## Detailed Code Explanation

### 1️⃣ **Data Structure: `ChunkRecord`** (lines 40-54)

Each chunk record is a dataclass with 14 fields storing:
- **Identity:** `id`, `book_id`, `book_title`, `source_file`, `language`
- **Source location:** `parent_id` (e.g. `page_22`), `page_number` (parsed from parent_id), `chunk_index` (order within page)
- **Statistics:** `char_count`, `token_count` (for filtering)
- **Content:** `text` — chunk text ready for embedding
- **Process metadata:** `created_at`, `ingestion_version`

### 2️⃣ **Text Processing Functions**

| Function | Lines | Purpose |
|----------|:-----:|---------|
| `normalize_text(text)` | 57-60 | Replace newlines with spaces, collapse multiple whitespace |
| `tokenize(text)` | 63-64 | Extract alphanumeric tokens (letters + numbers + apostrophes + hyphens) |
| `stable_chunk_id(...)` | 67-70 | Generate deterministic ID from SHA1 hash of `{book_id\|page\|chunk_index\|text}` |

**Why deterministic IDs matter:**
- Re-running with the same input produces the same IDs (idempotent)
- Traceability: each chunk can be traced back to its source page
- Cache/retrieval metadata remains stable across pipeline re-runs

### 3️⃣ **📐 Chunking Strategy: `split_parent_into_chunks()`** (lines 87-114)

Splits one page's text (`parent`) into multiple chunks with these rules:

```
default: max_tokens=110, overlap_tokens=20
```
- Split by sentence (`re.split(r"(?<=[.!?])\s+", text)`)
- Each chunk has a maximum of 110 tokens
- **Sliding window overlap:** The last 20 tokens of the previous chunk are prepended to the next chunk
- This preserves context continuity across chunks for retrieval

**Chunking example:**
```
Page 7: [sentence A][sentence B][sentence C][sentence D][sentence E]
         ↓
Chunk 0: [sentence A][sentence B]                     (100 tokens)
Chunk 1: [last_20_tokens from Chunk 0][sentence C][sentence D]  (105 tokens)
Chunk 2: [last_20_tokens from Chunk 1][sentence E]             (50 tokens)
```

### 4️⃣ **🛡️ Quality Gate: `quality_gate()`** (lines 187-218)

Six layered filters ensure only high-quality chunks pass through:

| Filter | Criteria | Impact |
|--------|----------|--------|
| **too_short** | `char_count < 80` | Discard chunks that are too short (uninformative) |
| **too_long** | `char_count > 1200` | Discard chunks that are too long (exceed embedding context) |
| **noise_pattern** | Regex: copyright, TOC, index, ISBN, publisher, etc. | Discard noise from book headers/footers |
| **non_english** | Check: at least 1 hint word + alphabetic ratio ≥ 60% | Discard non-English or corrupted text |
| **duplicate** | MD5 case-insensitive hash | Discard duplicate text |

**English hint words** checked: `the, and, to, of, for, with, you, your, water, shelter, survival`

**Noise patterns** detected: `copyright, all rights reserved, table of contents, contents, index, isbn, publisher, page N`

### 5️⃣ **📊 Smoke Test Retrieval: `run_smoke_test()`** (lines 276-313)

Tests whether the dataset can retrieve relevant chunks for given queries:

- **Scoring:** Simple token overlap — counts intersecting tokens between query and chunk
- **Formula:** `overlap / max(1, len(unique_query_tokens))`
- **Top-K:** Take the K chunks with the highest score per query
- **Hit rate:** Percentage of queries that have a score > 0

```python
def score_query(query, text):
    overlap = sum(min(count_query[t], count_doc[t]) for t in query_tokens)
    return overlap / len(unique_query_tokens)
```

### 6️⃣ **Reports & Artifacts**

#### `build_manifest()` (lines 226-239)
Merges:
- Per-book statistics (pages_processed, chunks_produced)
- Quality gate statistics (input, kept, dropped, drop_reasons)
- Post-quality per-book stats
- Dataset schema version

#### `write_markdown_report()` (lines 322-358)
Writes `ingestion_report.md` with:
- Quality summary
- Smoke test results (hit rate)
- Top 5 sample query results

---

## Output Artifacts

All outputs are written to `--out_dir` (default: `src/chunking/artifacts/`).

| File | Format | Contents |
|------|--------|----------|
| `dataset_clean.json` | JSON array | 1,169 chunks that passed quality gate, each with 14 fields per `SCHEMA_CONTRACT.md` |
| `dataset_manifest.json` | JSON object | Process metadata + quality statistics + schema version |
| `smoke_test_results.json` | JSON object | Details of 15 smoke test queries, top 3 results per query |
| `ingestion_report.md` | Markdown | Human-readable summary for review |

---

## Latest Results

| Metric | Value |
|--------|:-----:|
| **Books processed** | Essential Survival Skills + The Ultimate Guide to Rebuilding |
| **Total pages** | 328 |
| **Input chunks** | 1,179 |
| **Passed quality gate** | **1,169** (99.2%) |
| **Discarded** | 10 (all due to noise pattern) |
| **Smoke test queries** | 15 |
| **Hit rate** | **1.0** (100%) |
| **Schema** | `1.0.0` (backward-compatible) |

---

## Team Contract

The final `dataset_clean.json` schema is documented in `SCHEMA_CONTRACT.md`. This document serves as the contract between:
- **Andi** (Ingestion) — guarantees stable output format
- **Arund** (RAG) — parsing and indexing into Faiss
- **Pelangi** (iOS App) — retrieval and display

Any changes to required fields must be communicated and the `dataset_schema_version` must be updated.
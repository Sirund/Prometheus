import argparse
import hashlib
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Tuple


NOISE_PATTERNS = [
    r"\bcopyright\b",
    r"\ball rights reserved\b",
    r"\btable of contents\b",
    r"\bcontents\b",
    r"\bindex\b",
    r"\bisbn\b",
    r"\bpublisher\b",
    r"\bpage\s+\d+\b",
]

ENGLISH_HINT_WORDS = {
    "the",
    "and",
    "to",
    "of",
    "for",
    "with",
    "you",
    "your",
    "water",
    "shelter",
    "survival",
}

TOKEN_RE = re.compile(r"[a-zA-Z][a-zA-Z0-9'-]+")


@dataclass
class ChunkRecord:
    id: str
    book_id: str
    book_title: str
    source_file: str
    language: str
    parent_id: str
    page_number: int
    chunk_index: int
    char_count: int
    token_count: int
    text: str
    created_at: str
    ingestion_version: str


def normalize_text(text: str) -> str:
    text = text.replace("\n", " ").strip()
    text = re.sub(r"\s+", " ", text)
    return text


def tokenize(text: str) -> List[str]:
    return [m.group(0).lower() for m in TOKEN_RE.finditer(text)]


def stable_chunk_id(book_id: str, page_number: int, chunk_index: int, text: str) -> str:
    payload = f"{book_id}|{page_number}|{chunk_index}|{text}".encode("utf-8")
    digest = hashlib.sha1(payload).hexdigest()[:12]
    return f"{book_id}_p{page_number}_c{chunk_index}_{digest}"


def is_noise(text: str) -> bool:
    lowered = text.lower()
    return any(re.search(pattern, lowered) for pattern in NOISE_PATTERNS)


def looks_english(text: str) -> bool:
    tokens = tokenize(text)
    if not tokens:
        return False
    hint_hits = sum(1 for tok in tokens if tok in ENGLISH_HINT_WORDS)
    alpha_ratio = sum(1 for ch in text if ch.isalpha()) / max(1, len(text))
    return hint_hits >= 1 and alpha_ratio >= 0.6


def split_parent_into_chunks(text: str, max_tokens: int = 110, overlap_tokens: int = 20) -> List[str]:
    sentences = re.split(r"(?<=[.!?])\s+", text)
    sentences = [s.strip() for s in sentences if s.strip()]
    chunks: List[str] = []
    current: List[str] = []
    current_tokens = 0
    overlap_buffer: List[str] = []

    for sentence in sentences:
        sent_tokens = tokenize(sentence)
        if not sent_tokens:
            continue
        if current and current_tokens + len(sent_tokens) > max_tokens:
            chunks.append(" ".join(current))
            overlap_buffer = tokenize(" ".join(current))[-overlap_tokens:]
            current = []
            current_tokens = 0
            if overlap_buffer:
                current = [" ".join(overlap_buffer)]
                current_tokens = len(overlap_buffer)

        current.append(sentence)
        current_tokens += len(sent_tokens)

    if current:
        chunks.append(" ".join(current))

    return [normalize_text(c) for c in chunks if c.strip()]


def parse_page_number(parent_id: str, fallback: int) -> int:
    m = re.search(r"(\d+)$", parent_id)
    if not m:
        return fallback
    return int(m.group(1))


def load_book_raw(path: Path) -> Dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def book_id_from_filename(path: Path) -> str:
    stem = path.stem.lower()
    stem = re.sub(r"[^a-z0-9]+", "_", stem).strip("_")
    return stem or "book"


def build_records_from_book(
    book_path: Path,
    ingestion_version: str,
    language: str,
) -> Tuple[List[ChunkRecord], Dict]:
    raw = load_book_raw(book_path)
    parents = raw.get("parents", {})
    created_at = datetime.now(timezone.utc).isoformat()

    book_id = book_id_from_filename(book_path)
    book_title = book_path.stem.replace("_database", "").replace("-", " ").strip()

    records: List[ChunkRecord] = []
    total_pages = 0
    for page_idx, (parent_id, parent_text) in enumerate(parents.items(), start=1):
        cleaned_parent = normalize_text(parent_text)
        if len(cleaned_parent) < 40:
            continue
        total_pages += 1
        page_number = parse_page_number(parent_id, page_idx)
        chunks = split_parent_into_chunks(cleaned_parent)
        for chunk_index, chunk_text in enumerate(chunks):
            tokens = tokenize(chunk_text)
            if not tokens:
                continue
            rec = ChunkRecord(
                id=stable_chunk_id(book_id, page_number, chunk_index, chunk_text),
                book_id=book_id,
                book_title=book_title,
                source_file=str(book_path.as_posix()),
                language=language,
                parent_id=parent_id,
                page_number=page_number,
                chunk_index=chunk_index,
                char_count=len(chunk_text),
                token_count=len(tokens),
                text=chunk_text,
                created_at=created_at,
                ingestion_version=ingestion_version,
            )
            records.append(rec)

    stats = {
        "book_id": book_id,
        "book_title": book_title,
        "source_file": str(book_path.as_posix()),
        "pages_processed": total_pages,
        "chunks_produced": len(records),
    }
    return records, stats


def quality_gate(records: List[ChunkRecord], min_chars: int, max_chars: int) -> Tuple[List[ChunkRecord], Dict]:
    kept: List[ChunkRecord] = []
    reject_reasons: Counter = Counter()
    seen_text_hashes = set()

    for rec in records:
        if rec.char_count < min_chars:
            reject_reasons["too_short"] += 1
            continue
        if rec.char_count > max_chars:
            reject_reasons["too_long"] += 1
            continue
        if is_noise(rec.text):
            reject_reasons["noise_pattern"] += 1
            continue
        if not looks_english(rec.text):
            reject_reasons["non_english_or_low_signal"] += 1
            continue
        text_hash = hashlib.md5(rec.text.lower().encode("utf-8")).hexdigest()
        if text_hash in seen_text_hashes:
            reject_reasons["duplicate"] += 1
            continue
        seen_text_hashes.add(text_hash)
        kept.append(rec)

    quality = {
        "input_chunks": len(records),
        "kept_chunks": len(kept),
        "dropped_chunks": len(records) - len(kept),
        "drop_reasons": dict(reject_reasons),
    }
    return kept, quality


def build_manifest(
    clean_records: List[ChunkRecord],
    source_stats: List[Dict],
    quality_stats: Dict,
    ingestion_version: str,
) -> Dict:
    per_book = defaultdict(lambda: {"chunks": 0, "pages": 0})
    for rec in clean_records:
        per_book[rec.book_id]["chunks"] += 1
        per_book[rec.book_id]["pages"] = max(per_book[rec.book_id]["pages"], rec.page_number)

    return {
        "ingestion_version": ingestion_version,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "books": source_stats,
        "quality": quality_stats,
        "post_quality_book_stats": dict(per_book),
        "dataset_schema_version": "1.0.0",
    }


def load_queries(path: Path | None) -> List[str]:
    if path is None:
        return [
            "How can I prevent hypothermia in cold weather?",
            "What should I do first in a survival situation?",
            "How do I purify water in the wild?",
            "What are safe techniques for crossing a river?",
            "How can I navigate if I get lost?",
            "What should be in a basic survival kit?",
            "How to avoid panic during a disaster?",
            "What is the best clothing strategy outdoors?",
            "How do I signal for rescue effectively?",
            "When should I stay put versus move?",
        ]
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    if isinstance(data, dict) and "queries" in data:
        return [str(q) for q in data["queries"]]
    if isinstance(data, list):
        return [str(q) for q in data]
    raise ValueError("Query file must be a JSON list or {'queries': [...]} format.")


def score_query(query: str, text: str) -> float:
    q_tokens = tokenize(query)
    d_tokens = tokenize(text)
    if not q_tokens or not d_tokens:
        return 0.0
    q_count = Counter(q_tokens)
    d_count = Counter(d_tokens)
    overlap = sum(min(q_count[t], d_count[t]) for t in q_count)
    return overlap / max(1, len(set(q_tokens)))


def run_smoke_test(clean_records: List[ChunkRecord], queries: List[str], top_k: int) -> Dict:
    if not clean_records:
        return {"queries_total": len(queries), "queries_with_hit": 0, "hit_rate": 0.0, "samples": []}

    samples = []
    hits = 0
    for query in queries:
        scored = sorted(
            ((score_query(query, rec.text), rec) for rec in clean_records),
            key=lambda x: x[0],
            reverse=True,
        )[:top_k]
        best_score = scored[0][0] if scored else 0.0
        has_hit = best_score > 0
        if has_hit:
            hits += 1
        samples.append(
            {
                "query": query,
                "top_score": best_score,
                "top_results": [
                    {
                        "score": score,
                        "chunk_id": rec.id,
                        "book_id": rec.book_id,
                        "page_number": rec.page_number,
                        "text_preview": rec.text[:220],
                    }
                    for score, rec in scored
                ],
            }
        )
    return {
        "queries_total": len(queries),
        "queries_with_hit": hits,
        "hit_rate": round(hits / max(1, len(queries)), 3),
        "samples": samples,
    }


def write_json(path: Path, payload: Dict | List) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2, ensure_ascii=False)


def write_markdown_report(
    report_path: Path,
    manifest: Dict,
    smoke_result: Dict,
) -> None:
    quality = manifest["quality"]
    lines = [
        "# Ingestion Report",
        "",
        f"- Generated at: `{manifest['generated_at']}`",
        f"- Ingestion version: `{manifest['ingestion_version']}`",
        f"- Dataset schema version: `{manifest['dataset_schema_version']}`",
        "",
        "## Quality Summary",
        f"- Input chunks: **{quality['input_chunks']}**",
        f"- Kept chunks: **{quality['kept_chunks']}**",
        f"- Dropped chunks: **{quality['dropped_chunks']}**",
        f"- Drop reasons: `{json.dumps(quality['drop_reasons'], ensure_ascii=False)}`",
        "",
        "## Retrieval Smoke Test",
        f"- Queries total: **{smoke_result['queries_total']}**",
        f"- Queries with non-zero hit: **{smoke_result['queries_with_hit']}**",
        f"- Hit rate: **{smoke_result['hit_rate']}**",
        "",
        "## Sample Query Results",
    ]

    for sample in smoke_result.get("samples", [])[:5]:
        top = sample["top_results"][0] if sample["top_results"] else None
        lines.append(f"- Query: `{sample['query']}`")
        if top:
            lines.append(
                f"  - Top result: `{top['chunk_id']}` (book `{top['book_id']}`, page `{top['page_number']}`), score `{top['score']:.3f}`"
            )

    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def records_to_dict(records: List[ChunkRecord]) -> List[Dict]:
    return [rec.__dict__ for rec in records]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Normalize, validate, and package ingestion dataset.")
    parser.add_argument(
        "--inputs",
        nargs="+",
        required=True,
        help="Input raw extraction JSON files.",
    )
    parser.add_argument("--out_dir", default="src/chunking/artifacts", help="Output directory.")
    parser.add_argument("--ingestion_version", default="2026-05-05", help="Ingestion version string.")
    parser.add_argument("--language", default="en", help="Language code.")
    parser.add_argument("--min_chars", type=int, default=80, help="Minimum chunk length.")
    parser.add_argument("--max_chars", type=int, default=1200, help="Maximum chunk length.")
    parser.add_argument("--queries", default=None, help="Optional JSON file of smoke test queries.")
    parser.add_argument("--top_k", type=int, default=3, help="Top-k retrieval for smoke test.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_paths = [Path(p) for p in args.inputs]
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    all_records: List[ChunkRecord] = []
    source_stats: List[Dict] = []
    for in_path in input_paths:
        records, stats = build_records_from_book(in_path, args.ingestion_version, args.language)
        all_records.extend(records)
        source_stats.append(stats)

    clean_records, quality_stats = quality_gate(all_records, args.min_chars, args.max_chars)
    manifest = build_manifest(clean_records, source_stats, quality_stats, args.ingestion_version)

    queries = load_queries(Path(args.queries) if args.queries else None)
    smoke_result = run_smoke_test(clean_records, queries, args.top_k)

    write_json(out_dir / "dataset_clean.json", records_to_dict(clean_records))
    write_json(out_dir / "dataset_manifest.json", manifest)
    write_json(out_dir / "smoke_test_results.json", smoke_result)
    write_markdown_report(out_dir / "ingestion_report.md", manifest, smoke_result)

    print(f"Processed records: {len(all_records)}")
    print(f"Clean records: {len(clean_records)}")
    print(f"Artifacts written to: {out_dir.as_posix()}")


if __name__ == "__main__":
    main()

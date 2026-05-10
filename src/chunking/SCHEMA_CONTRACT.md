# Dataset Schema Contract

This document serves as the contract between the ingestion team and the RAG/app team for the `dataset_clean.json` file.

## Version

- `dataset_schema_version`: `1.0.0`

## Top-level artifacts

- `dataset_clean.json`: array of chunk records.
- `dataset_manifest.json`: ingestion process metadata + quality statistics.
- `ingestion_report.md`: quality summary and retrieval smoke test results.
- `smoke_test_results.json`: detailed smoke test query results.

## Chunk record fields (`dataset_clean.json`)

Each element is an object with the following fields:

- `id` (`string`, required): Stable/deterministic chunk ID.
- `book_id` (`string`, required): Source book ID (slug derived from filename).
- `book_title` (`string`, required): Human-readable book title.
- `source_file` (`string`, required): Path to the raw source JSON file.
- `language` (`string`, required): Language code (default `en`).
- `parent_id` (`string`, required): Reference to the parent source node (e.g. `page_22`).
- `page_number` (`integer`, required): Page number parsed from `parent_id`.
- `chunk_index` (`integer`, required): Chunk order within the parent/page.
- `char_count` (`integer`, required): Character length of the chunk.
- `token_count` (`integer`, required): Simple alphanumeric token count.
- `text` (`string`, required): Chunk content ready for embedding/retrieval.
- `created_at` (`string`, required): UTC ISO8601 timestamp of ingestion.
- `ingestion_version` (`string`, required): Ingestion process version.

## Deterministic ID rule

`id = "{book_id}_p{page_number}_c{chunk_index}_{sha1_12}"`

where the hash is computed from the payload:

`"{book_id}|{page_number}|{chunk_index}|{text}"`

This rule ensures:
- idempotent re-run (same input produces same IDs),
- traceability across indexing stages,
- stable references for cache/retrieval metadata.

## Quality gate minimum rules

A chunk is discarded if:
- `char_count < min_chars` or `char_count > max_chars`,
- noise patterns detected (copyright, TOC, index, ISBN, etc.),
- language-signal check fails,
- duplicate text (case-insensitive hash).

## Compatibility note

This contract is backward-compatible as long as required fields are not removed or have their types changed.
Adding new fields is allowed as optional fields.
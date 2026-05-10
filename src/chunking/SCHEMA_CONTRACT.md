# Dataset Schema Contract

Dokumen ini menjadi kontrak antara tim ingestion dan tim RAG/app untuk file `dataset_clean.json`.

## Version

- `dataset_schema_version`: `1.0.0`

## Top-level artifact

- `dataset_clean.json`: array of chunk records.
- `dataset_manifest.json`: metadata proses ingestion + statistik kualitas.
- `ingestion_report.md`: ringkasan kualitas dan smoke test retrieval.
- `smoke_test_results.json`: hasil detail query smoke test.

## Chunk record fields (`dataset_clean.json`)

Setiap elemen adalah object dengan field berikut:

- `id` (`string`, required): ID chunk stabil/deterministik.
- `book_id` (`string`, required): ID source book (slug dari nama file).
- `book_title` (`string`, required): Judul buku human-readable.
- `source_file` (`string`, required): path file sumber JSON raw.
- `language` (`string`, required): kode bahasa (default `en`).
- `parent_id` (`string`, required): referensi node parent sumber (mis. `page_22`).
- `page_number` (`integer`, required): nomor halaman hasil parse dari `parent_id`.
- `chunk_index` (`integer`, required): urutan chunk dalam parent/page.
- `char_count` (`integer`, required): panjang karakter chunk.
- `token_count` (`integer`, required): jumlah token alfanumerik sederhana.
- `text` (`string`, required): isi chunk siap embedding/retrieval.
- `created_at` (`string`, required): waktu UTC ISO8601 saat ingestion.
- `ingestion_version` (`string`, required): versi proses ingestion.

## Deterministic ID rule

`id = "{book_id}_p{page_number}_c{chunk_index}_{sha1_12}"`

dengan hash dihitung dari payload:

`"{book_id}|{page_number}|{chunk_index}|{text}"`

Aturan ini menjaga:
- idempotent re-run (input sama menghasilkan ID sama),
- traceability lintas tahap indexing,
- stabilitas referensi untuk cache/metadata retrieval.

## Quality gate minimum rules

Chunk dibuang bila:
- `char_count < min_chars` atau `char_count > max_chars`,
- terdeteksi pola noise (copyright, TOC, index, ISBN, dll),
- gagal language-signal check,
- duplikat teks (case-insensitive hash).

## Compatibility note

Kontrak ini backward-compatible selama field required tidak dihapus/diubah tipe.
Penambahan field baru diperbolehkan sebagai field optional.

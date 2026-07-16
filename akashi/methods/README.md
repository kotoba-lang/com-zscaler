# akashi Method Notes

Every adapter, parser, normalizer and linker must publish a `methodNote` before
its output can be treated as canonical akashi evidence.

R0 reserves method note families:

- `source-policy` — source URL/page, access mode, robots/ToS posture, cadence.
- `snapshot-parser` — raw source payload to `adDisclosureSnapshot`.
- `creative-normalizer` — text/media/category extraction.
- `landing-evidence` — URL normalization, redirect summary, content hash.
- `cross-platform-link` — non-adjudicating factual links.
- `malak-candidate` — evidence-candidate criteria for reviewed malak handoff.

Closed scoring is prohibited. A method note must state limits and known false
positive cases.

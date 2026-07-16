# closure fixture

Local fixture for records that close the akashi graph without live collection:

- `adDisclosureLink`
- `adTransparencyReport`
- `malakEvidenceCandidate`

These records are deliberately non-adjudicating. The malak fixture is
`candidate-only` and does not import into malak.

`negative_malak_imported.json` is intentionally invalid for akashi R0: it proves
tests reject a malak-imported closure fixture.

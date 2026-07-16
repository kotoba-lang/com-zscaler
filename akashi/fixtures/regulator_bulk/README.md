# regulator_bulk fixture

Local fixture for the first akashi R1 parser. It models a regulator-style
public bulk export and is intentionally not fetched by any cell.

- Source family: `regulator-repository`
- Parser: `/20-actors/akashi/adapters/regulator_bulk_fixture_parser.cljc`
- Output Lexicons: `adDisclosureSnapshot`, `advertiserIdentity`,
  `creativeDisclosure`, `deliveryDisclosure`, `landingEvidence`,
  `methodNote`, `sourcePolicySnapshot`
- Boundary: fixture-only; no platform endpoint or page collection

Files:

- `sample.json` covers a fuller source-disclosed record.
- `missing_optional_fields.json` proves omitted optional delivery/advertiser
  fields remain omitted instead of inferred.
- `negative_missing_landing_url.json` proves malformed source records are
  rejected before lexicon validation.

# etzhayyim-esign

DID-bound, MST-recorded, L2-anchored document signing for the religious-corp substrate.

Per [ADR-2605231230](../../90-docs/adr/2605231230-etzhayyim-esign-actor-did-bound-mst-anchored.md).

## Status

Phase 0 scaffold — lexicons in place, procedure handlers return `501 NotYetImplemented`. Phase 1 (MST + IPFS, no anchor yet) targets the Bootstrap Council Seats 2–5 RFP minutes deadline of 2026-06-19.

## DID

`did:web:esign.etzhayyim.com` (resolved by `50-infra/etzhayyim-esign-did-web/`).

## Lexicons

Defined in `00-contracts/lexicons/com/etzhayyim/esign/`:

| NSID | Type |
|---|---|
| `com.etzhayyim.esign.envelope` | record |
| `com.etzhayyim.esign.signature` | record |
| `com.etzhayyim.esign.completedEvent` | record |
| `com.etzhayyim.esign.anchoredEvent` | record |
| `com.etzhayyim.esign.requestEnvelope` | procedure |
| `com.etzhayyim.esign.signEnvelope` | procedure |
| `com.etzhayyim.esign.declineEnvelope` | procedure |

## Substrate boundary

This Worker MUST NOT import `@atproto/api` or `viem` directly. All AT Protocol writes flow through `@etzhayyim/sdk`. All on-chain anchoring is delegated to `anchor-cron` via the `completedEvent` record — this Worker never holds a wallet key.

DocuSign / Adobe Sign / RazorpaySign passthroughs (`com.etzhayyim.apps.lawfirm.eSign*`) are reserved for fiat receipts and external counsel intake per ADR-2605192115 §4. Religious-corp documents (Council resolutions, Land donations, Force R&D consent, Public Fund disbursements, membership affirmations) MUST use this actor.

## License

Apache-2.0 with the etzhayyim Charter Compliance Rider v2.0 — see `NOTICE` and `/CHARTER-RIDER.md` at the repo root.

## Development

```bash
npm install        # installs wrangler + @cloudflare/workers-types
npm run dev        # local wrangler dev (port 8787 by default)
npm run deploy     # wrangler deploy — requires wrangler auth + DNS for esign.etzhayyim.com
```

DNS provisioning (`esign.etzhayyim.com` AAAA → `100::` proxied) is a manual Cloudflare dashboard step until the Phase 1 deploy runbook is written.

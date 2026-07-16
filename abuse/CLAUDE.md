# abuse.etzhayyim.com — Phishing Takedown / Abuse Reporting Actor

Phishing / malware / brand-impersonation takedown coordination. Gathers intel (whois, dns, reverse-ip, archive), drafts per-target abuse reports, dispatches via `mailer.etzhayyim.com` (Resend), tracks status.

## Runtime

| key | value |
|---|---|
| nanoid | `abs3r3p0` |
| domain | `abuse.etzhayyim.com` |
| DID | `did:web:abuse.etzhayyim.com` |
| performerType | `service` |
| executionTier | **T1** (MCP-Compose pipeline via PDS Shared Executor — no standalone Worker) |
| UI | `appview` (iframe card UI, `/reports` list) |
| Transport | XRPC (external) + MCP tools/list (agent discovery) |
| Dispatch | `mailer.etzhayyim.com` XRPC `com.etzhayyim.apps.mailer.sendEmail` (Resend-backed) |

## Lexicon Namespace: `com.etzhayyim.apps.abuse.*`

| NSID | Type | Purpose |
|---|---|---|
| `com.etzhayyim.apps.abuse.gatherIntel` | procedure | Run dns + whois + reverse-ip + wayback + asn on a target (domain or IP). Writes `vertex_yabai_entity` + `vertex_yabai_evidence`. |
| `com.etzhayyim.apps.abuse.submitReport` | procedure | Draft abuse report(s) for a domain, routing to registrar + hosting + brand owner + CERT. Returns list of draft IDs. |
| `com.etzhayyim.apps.abuse.sendReport` | procedure | Dispatch a queued draft via mailer.etzhayyim.com (Resend). Updates status `draft → sent`. |
| `com.etzhayyim.apps.abuse.markSent` | procedure | Manual override: mark draft as `sent` (if dispatched out-of-band). |
| `com.etzhayyim.apps.abuse.listReports` | query | List reports filtered by domain / status / target_type. |
| `com.etzhayyim.apps.abuse.getReport` | query | Fetch single report by rkey (full EML body). |
| `com.etzhayyim.apps.abuse.listTargets` | query | List known abuse contacts (registrar / hosting / brand / cert). |
| `com.etzhayyim.apps.abuse.registerTarget` | procedure | Add new abuse contact. |

## Data Model (reuses yabai graph — no new tables)

Abuse reports use `vertex_yabai_entity` with `entity_type='abuse_report'`:

```
vertex_id       = at://did:web:yabai.etzhayyim.com/com.etzhayyim.apps.yabai.entity/abuse-{domain}-{target_type}-{target_sanitized}
entity_type     = 'abuse_report'
canonical_name  = target email (abuse@...)
aliases         = target_type (registrar | hosting | brand | cert)
value           = EML body (RFC 5322 text)
source          = 'takedown-draft' (initial) → 'takedown-sent' (after dispatch)
```

Status transitions encoded in `source` field:
- `takedown-draft` — prepared, not sent
- `takedown-sent` — dispatched via mailer
- `takedown-acked` — target acknowledged (manual mark)
- `takedown-resolved` — domain suspended / action confirmed

## Abuse Target Catalog (initial)

| target_type | email | jurisdiction | org |
|---|---|---|---|
| registrar | `abuse@dynadot.com` | US | Dynadot LLC |
| registrar | `complaint@gname.com` | SG | Gname.com Pte. Ltd. |
| registrar | `abuse@namesilo.com` | US | NameSilo, LLC |
| hosting | `abuse@ucloud.cn` | HK/CN | UCLOUD HK (AS135377) |
| hosting | `abuse@ctgserver.com` | HK | CTG Server (AS152194) |
| hosting | `aliyun-security@list.alibaba-inc.com` | SG/CN | Alibaba US Technology (AS45102) |
| brand | `phish@meta.com` | US | Meta Platforms (WhatsApp) |
| brand | `stopit@mastercard.com` | US | Mastercard Brand Protection |
| brand | `dl_line_corp_legal_notice@linecorp.com` | JP | LINE Corp |
| brand | `phishing@apple.com` | US | Apple Brand Protection |
| brand | `spoof@smbc.co.jp` | JP | SMBC Phishing Reports |
| cert | `office@jpcert.or.jp` | JP | JPCERT/CC |
| cert | `info@jc3.or.jp` | JP | JC3 Japan Cybercrime |
| cert | `reports@apwg.org` | International | APWG |

Each target also has a corresponding yabai sub-DID:
`did:web:yabai.etzhayyim.com:{registrar|hosting}:{cid}` + `vertex_profile` (registered 2026-04-19).

## Pipeline (submitReport flow)

```
XRPC com.etzhayyim.apps.abuse.submitReport {domain, brandHint?}
  1. graph.query      — fetch yabai entity for phishing-url DID (validation)
  2. graph.query      — look up registrar + hosting from evidence record
  3. graph.query      — look up brand owner by typosquat pattern match
  4. agent.chat       — Murakumo/Ollama: compose report body (jurisdiction-appropriate lang)
  5. graph.write ×N   — insert N draft rows (1 per target: registrar, hosting, brand, cert ×3)
  6. derive:social    — yabai DID announces: "Takedown draft: {domain} routed to N abuse contacts"
  Output              — {reportIds: [...], targets: [...]}
```

## Dispatch (sendReport flow)

```
XRPC com.etzhayyim.apps.abuse.sendReport {reportId}
  1. graph.query      — fetch draft (vertex_yabai_entity.abuse_report)
  2. mailer.sendEmail — POST /xrpc/com.etzhayyim.apps.mailer.sendEmail {to, subject, text, from: 'abuse-report@etzhayyim.com'}
                         mailer internally uses Resend API
  3. graph.write      — UPDATE source='takedown-sent', append sent_at to props
  4. derive:social    — "Takedown dispatched: {domain} → {target_email} (Resend message-id: {id})"
  Output              — {status, messageId, sentAt}
```

## Required Mailer Enhancement

mailer.etzhayyim.com must expose `com.etzhayyim.apps.mailer.sendEmail`:

```json
{
  "lexicon": 1,
  "id": "com.etzhayyim.apps.mailer.sendEmail",
  "defs": {"main": {"type": "procedure",
    "input": {"encoding": "application/json", "schema": {"type":"object",
      "properties": {"to":{"type":"string"},"subject":{"type":"string"},"text":{"type":"string"},"html":{"type":"string"},"from":{"type":"string"}},
      "required": ["to","subject","text"]}},
    "output": {"encoding": "application/json", "schema": {"type":"object",
      "properties": {"messageId":{"type":"string"},"provider":{"type":"string"}}}}}}}
```

Backed by `fetch("https://api.resend.com/emails", { headers: { Authorization: "Bearer $RESEND_API_KEY" }, ... })`. Secret: `RESEND_API_KEY` wrangler secret on mailer Worker.

## MCP Tools Registration

All 8 abuse XRPC methods auto-register as MCP tools via `vertex_capability` insert on deploy:
- `abuse.gather-intel` / `abuse.submit-report` / `abuse.send-report` / `abuse.mark-sent`
- `abuse.list-reports` / `abuse.get-report` / `abuse.list-targets` / `abuse.register-target`

## Governance

| Aspect | Policy |
|---|---|
| `classification` | `public-intel` (Tier 2 — takedown drafts are public evidence) |
| Consent | Not required (abuse reports are adversarial signals, not consent-scoped) |
| Approval | `sendReport` requires `sub=did:web:jun.etzhayyim.com` OR API key with scope `abuse:send` |
| Rate limit | 10 `sendReport` / hour per caller DID |
| Audit | Every dispatch logged to `vertex_yabai_audit_log` |

## Follow-based Input (reactive)

Follows `yabai.etzhayyim.com`. On new `com.etzhayyim.apps.yabai.flag` commit with `flag_type LIKE 'phishing-%'`:
- auto-run `gatherIntel` if not yet enriched
- enqueue `submitReport` as draft (no auto-send)

## Files

| File | Purpose |
|---|---|
| `20-actors/abuse/actor-manifest.jsonld` | T1 MCP-Compose pipeline definition |
| `00-contracts/lexicons/com/etzhayyim/apps/abuse/*.json` | 8 XRPC lexicons |
| `20-actors/abuse/CLAUDE.md` | This doc |

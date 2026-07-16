# toritsugi procedure-registry — Verification Workflow (G14)

Per ADR-2605312030 §2 + §4 (G14 verified-procedure-only submission). Every
`com.etzhayyim.toritsugi.procedure` record ships `verificationStatus =
unverified-seed` and **no live submission (`toritsugi_submit`) may run against
an unverified-seed or stale entry**. This file documents how an entry is moved
through the three tiers — the human/Council checks that gate `toritsugi_submit`.

> **R0 status**: this is the *process spec*. No entry is verified yet; all 6
> seed entries remain `unverified-seed`. Verification execution begins at R1
> (Council ratification + procedure-verification maintainer DID registered —
> see `toritsugi_procedure_registry/cell.py`).

## Tiers (`verificationStatus`)

| Tier | Meaning | Who flips it | Unlocks |
|---|---|---|---|
| `unverified-seed` | routing/guide scaffold only; best-effort public refs | (initial) | 案内/guide design only — **no live submission** |
| `maintainer-verified` | a maintainer has re-checked all fields against the official source within the freshness window | procedure-verification maintainer DID | **member self-submission** guidance (R2) |
| `council-verified` | Council-reviewed; eligible for the 代行 path | Council Lv6+ (per G15 the 代行 path additionally needs Council Lv7+ + 行政書士法 clearance) | **agent-on-behalf (代行)** eligibility (R3) |

`freshnessWindowDays` (currently **180**) bounds staleness: an entry whose
`lastVerified` is older than the window is treated as unverified for dispatch
even if its status is `maintainer-verified`.

## Per-field verification checklist (unverified-seed → maintainer-verified)

For each procedure entry, a maintainer confirms against the **official
authority source** (the `provenance` URL, which MUST be a `.go.jp` / official
domain — never a third-party blog or aggregator):

1. **`title`** — matches the official procedure name (申請/届出 の正式名称).
2. **`authority` (所管)** — the correct 省庁/自治体 owns this procedure; for
   per-自治体 procedures, note that the concrete 窓口/住所 resolves at guide
   time (not pinned in the seed).
3. **`legalBasis` (根拠法令)** — the cited statute + article is current and
   actually establishes the procedure (G8 non-fabrication). Re-check on every
   verification: statutes are amended.
4. **`channelType` + `onlineUrl`** — the filing channel is correct and, if
   online, the portal URL resolves to the actual application entry point (not a
   landing page).
5. **`requiredDocuments` (必要書類)** — complete and current; flag any item
   that varies by 自治体.
6. **`feeJpy` (手数料)** — matches the official fee (note 自治体 variance;
   the seed value is a 目安).
7. **`statutoryProcessingDays` (法定処理期間)** — matches the statute / official
   SLA; `0` = same-day/immediate.
8. **`provenance`** — resolves, is an official source, and actually supports the
   above fields. **If provenance cannot be confirmed official, the entry stays
   `unverified-seed`** (fail-closed).
9. **`lastVerified`** — set to the verification datetime (UTC).
10. **行政書士法/UPL re-check (G5)** — confirm the guide for this procedure is
    案内 + 入力補助 only; if the procedure inherently requires 作成代理 or
    professional judgment (e.g. 税務 characterization), the entry's guide MUST
    route to chigiri + licensed counsel / toritate, not toritsugi.

Only when **all 10** pass may a maintainer set
`verificationStatus = maintainer-verified` + refresh `lastVerified`.

## maintainer-verified → council-verified (代行 eligibility)

Additional to the above, for an entry to be eligible for the 代行
(`agent-on-behalf`) submission path:

- Council Lv6+ review of the procedure + its 行政書士法/税理士法 reserved-practice
  exposure (does filing it on a member's behalf cross a reserved-practice line?);
- a recorded `councilGateRef` (per `submissionRecord` schema, G15) — and note
  that **executing** 代行 still requires the R3 gate (Council Lv7+ unanimity +
  行政書士法 clearance, `DAIKOU_R3_GATE_TX`), independent of this registry tier.

## Current seed status (2026-05-31)

All 6 entries `unverified-seed`, all `legalBasis` + `provenance` present, all
`provenance` on `.go.jp` official sources. Legal citations (住民基本台帳法 §12 /
§22 · 戸籍法 §49 · 番号法 §17 · 児童手当法 §7 · 所得税法 §120) authored from
the official sources but **not yet maintainer-verified** — they are routing
scaffolds, not authoritative contacts (drift expected, esp. per-自治体).

## Machine-enforced floor

`70-tools/scripts/audit/test_toritsugi_invariants.py::test_seed_all_unverified_and_cited`
pins: every seed entry is `unverified-seed` AND cites `legalBasis` + `provenance`.
A seed shipped pre-verified, or missing a citation, fails CI. The G14 dispatch
refusal itself lives in `toritsugi_procedure_registry/cell.py` (R0: import-raise).

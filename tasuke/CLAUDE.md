# tasuke (助) — free cybercrime-victim-support membrane

**DID**: `did:web:etzhayyim.com:actor:tasuke` · **Tier**: B · **Status**: R0 · **ADR**: 2606060900

**Read the root `/CLAUDE.md` Charter + substrate rules first.** tasuke-specific invariants below
OVERRIDE nothing in the Charter; they make it concrete for this actor.

## The one-sentence identity

助 (たすけ) is where a consenting member hit by online crime gets walked, **for free**, from
被害相談 to recovery — and where the documents they themselves submit (被害届・被害状況報告書・
証拠目録・被害額算定書・銀行組戻し依頼・プラットフォーム依頼・アカウント復旧手順) are **generated
ready-to-use**. It connects to **no paid counsel**; it produces **the victim's own filings**, never
police-authored 公文書; it **drafts but never submits as an agent**.

## The pipeline

```
intake_triage ─▶ evidence_preservation ─▶ ┌ police_report      被害届 / 被害状況報告書 / 証拠目録 / 被害額算定書
(同意・無料・分類)  (暗号化参照 + hash)        ├ platform_abuse     銀行 組戻し(振り込め詐欺救済法) / プラットフォーム依頼
                                            └ account_recovery   アカウント復旧手順(本人実行)
                                                    │
                                                    ▼
                                         FREE public windows (#9110 / 188 / NCCC / …)
```

## The 10 gates — do NOT weaken

The three load-bearing ones live in **three places each** (schema `:db/allowed`/enum + lexicon
`const`/`enum` + Python `ValueError`). Touch one, touch all three.

- **G1 全て無料 / cash≡0** — `:support/cost-jpy` is `:db/allowed [0]`, `supportCostJpy` is
  `const 0`, `triage.SUPPORT_COST_JPY = 0`. A fee / charge / subscription is **unrepresentable**.
  `analyze.run()` asserts the total victim cost across all cases is ¥0.
- **G2 本人作成・本人提出** — `:support/role` allows only `{:guide :draft-assist :self-submit}`;
  `:represent`/`:proxy-submit`/`:agent-file` are **unrepresentable**. Every generated doc carries
  `needsMemberSignature const true`. 助 drafts; **the member authors, signs, and submits**
  (行政書士法/弁護士法 独占業務不踏). Never add a "submit-for-member" path.
- **G3 警察authored不可** — `:doc/authored-by` is `:db/allowed [:member]`, `authoredBy` is
  `const "member"`, `report_gen._doc` hard-wires `:member`. `:police`/`:official`/`:server` are
  **unrepresentable** — a generated filing is the **victim's own 申告書類**, addressed to the
  authority, never a police-authored 公文書 (公文書偽造を構造的に排除). "そのまま使える" means an
  officer can *work from* it, not that 助 forged an official record.
- **G4 non-adjudicating** — a scam KIND is a routing label, never a finding that a crime occurred.
  `:case/verdict` does not exist; the triage output has no guilt/crime field (danjo/chigiri
  boundary). `classify()` routes; it never decides fault.
- **G5 no paid counsel** — `:referral/paid` + `:support/paid-referral` are `:db/allowed [false]`;
  `paidReferral const false`. Only **FREE** public windows. **弁護士へつながない.**
- **G6 PII-by-reference** — evidence lives in `com.etzhayyim.encrypted.*` envelopes; only an
  `envelope-ref` + a chain-of-custody `sha256` are in the clear. `evidence.preserve()` **raises**
  on any plaintext-PII field (ADR-2605181100).
- **G7 no-server-key** — `:case/server-held-key` is `:db/allowed [false]`; `serverHeldKey
  const false`; the member signs every submission (ADR-2605231525).
- **G8 Murakumo-only** — any LLM refinement of classification/wording runs on the Murakumo fleet
  (LiteLLM `127.0.0.1:4000`), never a commercial GPU (ADR-2605215000).
- **G9 outward-gated** — live filing / sending / submission = Council Lv6+ + operator;
  `:doc/published` is `const false` at R0; every cell's `.solve()` raises.
- **G10 sourcing-honesty** — the procedure/window registry is `:representative`; 法定期限 / 窓口 /
  根拠法令 must be verified against the primary source before any live use.

## When editing

- The three structural invariants (G1/G2/G3) are enforced in **schema + lexicon + code**.
  `methods/test_charter_invariants.py` guards all three. A new doc kind or role must keep them.
- `triage.py` is the heart (G1/G4/G7 anchor); `report_gen.py` is the generator (G2/G3 anchor);
  `evidence.py` is the custody/PII engine (G6 anchor). `report_gen.assert_member_authored()` is the
  one-call guard every generated doc passes through (G1/G2/G3/G9) — call it on anything new.
- Tests are standalone-runnable (`python3 test_*.py`). Run everything with `./run_tests.sh`.
- `.solve()` raises `RuntimeError` on every cell at R0 — live execution is G9-gated. Do not wire a
  cell to a live filing, a live bank/platform send, or a live account login; that needs Council
  Lv6+ + operator.
- The cell directory `intake_triage` (NOT `triage`) avoids shadowing the methods module
  `methods/triage.py`. Keep that separation.

## Siblings / boundaries

- **tadori 辿** — for crypto-theft cases, the on-chain trace; 助 routes the victim, tadori traces.
- **kurashimori 暮らし守** — 事業者-contract consumer disputes (cooling-off, 特商法); 助 is the
  cybercrime counterpart, not the merchant-dispute one.
- **himotoki 繙き** — DSAR/disclosure (own-data); useful when a case needs a platform disclosure.
- **toritsugi 取次** — government-procedure concierge; the surface that can present 助 to a citizen.
- **chigiri 契 / danjo 弾正** — the boundary: 助 prepares *records*; it does **not** adjudicate
  fraud (danjo observes, chigiri routes legal characterization, the courts decide). 助 never crosses
  into 法律事務 代理 or a verdict.
- **kokoro 心** — psychosocial support referral for victim trauma.
- **no-server-key (ADR-2605231525) · kotoba-canonical (ADR-2605312345) · encrypted-records
  (ADR-2605181100) · Charter Rider (ADR-2605192200)** — invariants 助 *strengthens*; it amends none.

## Build / test

```
./run_tests.sh                                  # all 8 suites
cd methods && python3 triage.py                 # triage the :representative victim cases
cd methods && python3 analyze.py                # end-to-end dry-run → methods/out/support-dryrun.md (asserts ¥0)
```

## Do not

- Do not add any fee / cost / price / subscription field, or a non-zero `:support/cost-jpy` — G1.
- Do not add `:represent`/`:proxy-submit`/`:agent-file` to `:support/role`, or a submit-for-member
  path — G2 (本人作成・本人提出).
- Do not add `:police`/`:official`/`:server` to `:doc/authored-by`, or generate an official
  police-authored document — G3 (公文書偽造を排除).
- Do not add a `:case/verdict` / guilt / crime-confirmed field — G4 (non-adjudicating).
- Do not route to paid counsel / 弁護士 — G5 (free public windows only).
- Do not store plaintext PII on an evidence record — G6 (encrypted-by-reference).
- Do not call any cell's `.solve()`, or wire a live filing/send/login — R0 scaffolds raise (G9).
- Do not route design/inference through a commercial GPU — G8 (Murakumo-only).

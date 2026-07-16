# seigyo (制御) — Industrial Control Layer Tier-B Actor

**DID**: `did:web:etzhayyim.com:seigyo`
**Namespace**: `com.etzhayyim.seigyo.*`
**ADR**: ADR-2606111000 (R0 master), ADR-2606111100 (R1 benchtop loop), R2/R3 reserved
**Status**: R0 scaffold (2026-06-11) — all 5 cells import-time RuntimeError

## Overview

religious-corp 全産業アクター(igata / silicon=tsukuru / yakushi / futawa / suki / pillow / tsutae / hikari-denki / mizuho / district-heat)の **制御層 (ISA-95 L0–L2)** を一手に持つ substrate actor。既存の製造セル群は L3(レシピ/オーケストレーション)— そのレシピが最終的に実行される PLC ロジック・SCADA 監視・フィールドバスの層が seigyo。

**モノリシックな DCS 製品は置かない**: DCS 相当 = OpenPLC (L1) + FUXA/Rapid SCADA (L2) + OPC UA open62541 (L2.5) の合成を単一の attestation 体制下に置いたもの。商用 DCS/PLC/SCADA ランタイム(Siemens / Honeywell / Yokogawa / Emerson / Rockwell / ABB / Schneider / Mitsubishi / GE / AVEVA / Ignition)は **禁止**(ADR-2605265000 §1.9 の全アクター一般化)。

## Why "seigyo" (制御)

制御 = control。あらゆるレシピの下にある「弁にかかる手」。igata(鋳型)/ fuigo(鞴)/ tatara(踏鞴)が工程・道具を名付けたのに対し、seigyo は工程横断の**層**を名付ける — どのアクターの炉でも弁でも、その制御ロジックは同じ憲章の下にある。

## Safety Invariant — ABSOLUTE (ADR-2606111000 §3)

- 安全インターロック(E-stop / 過圧 / 過温 / ガス検知 / ライトカーテン / LOTO)は **hardwired / 安全リレー (L1S) のみ**
- **LLM・Murakumo 推論・kotoba セル・ネットワーク往復は安全パスに決して入らない** — 安全機能はサイトのネットワークケーブルを切っても完遂する
- L1S より上のソフトウェアは安全に関して advisory-only。インターロックのリセットは物理・現地・人間
- Murakumo 最適化提案は attested PLC プログラムにコンパイルされた **setpoint envelope**(min/max + 変化率上限)経由のみ。envelope 拡大 = 新プログラム版 = 再 attestation

## Pregel Cells (5, all R0 import-time RuntimeError)

| Cell | Murakumo node | ISA-95 seam | §2(a)(c) risk |
|---|---|---|---|
| `seigyo_plc_program_lifecycle` | judah | L4 → L1 | HIGH |
| `seigyo_interlock_attestation` | benjamin | L4 → L1S (attest only) | HIGH |
| `seigyo_scada_gateway` | judah | L2 ↔ L3 | MEDIUM |
| `seigyo_opcua_bridge` | judah | L2.5 ↔ L3 | MEDIUM |
| `seigyo_historian_aggregate` | simeon | L2 → L3 | LOW |

Cells live in `kotoba/crates/kotoba-kotodama/cells/seigyo_*/`.

## PLC Program Lifecycle (ADR §4)

```
author (IEC 61131-3 ST) → static check + OpenPLC simulation → engineer review
  → attestation (program CID + setpoint-envelope table)
  → deploy to OpenPLC runtime
  → runtime hash watch (mismatch ⇒ L3 recipe dispatch freeze)
```

## Lexicons (9, R0 stub deferred to R1+)

```
com.etzhayyim.seigyo.{
  ioPointRegistry, plcProgramAttestation, setpointEnvelope,
  runtimeAttestation, interlockVerificationRecord, scadaProjectAttestation,
  alarmEventRecord, telemetryAggregateRecord, silenSeigyoReview
}
```

## Roadmap

| Phase | Scope | Gate |
|---|---|---|
| **R0** | Scaffold. 5 cells RuntimeError. No hardware. | ADR-2606111000 (proposed) |
| **R1** | ONE benchtop loop (≤2 kW mock-furnace temperature PID; igata R1 die-preheat analog) on OpenPLC + FUXA + open62541; full lifecycle end-to-end; physical E-stop verified + recorded | ADR-2606111100 + Council Lv6+ ≥3 + controls-engineer SME DID |
| **R2** | One full production cell (igata die-prep OR pillow foam line); historian + N7 aggregation live | post-R1 + 90-day clean runtime-attestation history |
| **R3** | Multi-site rollout (silicon / pharma / vehicle actors per their own R-gates) | annual silenSeigyoReview pass + controls-engineer on Council attestation path |

## Cross-Actor Position

seigyo は供給者ではなく**層**: 各製造アクターの commissioning が seigyo の監査済みスタックを継承する。最初のペアリングは **igata R1 benchtop commissioning**(ADR-2605261215)— igata の firmware retrofit 要件(open-source 制御、@1 kHz logging、interlock 文書化)は seigyo の §4 lifecycle + §3 invariant のインスタンス。

## References

- `/90-docs/adr/2606111000-seigyo-industrial-control-layer-tier-b-actor-r0.md` — R0 master
- `/90-docs/adr/2606111100-seigyo-r1-benchtop-loop-commissioning.md` — R1
- `/90-docs/adr/2605265000-district-heating-cooling-d-gate-evaluation-r0.md` §1.9 — vendor 禁止の出自
- `/90-docs/adr/2605215000-etzhayyim-inference-murakumo-only-no-runpod.md` — Murakumo-only inference
- `/20-actors/igata/README.md` — R1 pairing target
- `/CHARTER-RIDER.md` — §2 audit basis

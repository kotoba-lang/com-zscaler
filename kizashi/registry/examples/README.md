# kizashi — worked example records (R1 fixtures)

Per ADR-2605312700. These illustrate the most important and most abstract part
of the actor — the **non-diagnostic contract (G3 + G7)** — with concrete records,
and double as R1 schema-validation fixtures.

> Illustration + fixtures only — NOT real member data. In production every
> PII-bearing field rides the encrypted payload (G2); these carry only the
> aggregate, non-PII shape. The CIDs are `*-EXAMPLE` placeholders.

| File | Lexicon | Demonstrates |
|---|---|---|
| `attributionReport.lower-back.example.json` | `attributionReport` | G3 — only candidate contributing FACTORS, no disease/diagnosis field; G7 — confidence + const disclaimer + consult; weightPermille sums to 1000 incl. an explicit `uncertain-residual` |
| `triageReferral.lower-back.example.json` | `triageReferral` | G3 — refer-only (`targetActor=mitate`); G5 — `emergencyFlag` present (false = routine) |

The 腰痛 example deliberately encodes the feasibility-analysis verdict: posture
is the dominant *contribution* (functional sign, the pod's strength), structural
cause is **not assessed** (would need licensed imaging — referral), and a real
`uncertain-residual` is carried rather than hidden (G7, no false precision).

## Conformance check (runnable now)

Validates each example against its kizashi lexicon AND the structural gates.

```bash
python3 - <<'PY'
import json, glob, os
LEX='00-contracts/lexicons/com/etzhayyim/kizashi'
EX='20-actors/kizashi/registry/examples'
def rec(n): return json.load(open(f'{LEX}/{n}.json'))['defs']['main']['record']
ok=True
def chk(c,m):
    global ok; print(('ok  ' if c else 'FAIL')+m); ok=ok and c

# attributionReport
ar=json.load(open(f'{EX}/attributionReport.lower-back.example.json'))
arr=rec('attributionReport')
chk(set(arr['required'])<=set(ar), 'attributionReport: all required fields present')
FORB={'diagnosis','diagnoses','prescription','prescriptions','treatmentPlan','treatment','medication','dosage','icdCode','icd10','dxCode'}
chk(not (FORB & set(ar)), 'G3: example carries no diagnosis-class field')
chk(ar['disclaimer']==arr['properties']['disclaimer']['const'], 'G7: disclaimer == lexicon const')
chk(ar['confidence'] in arr['properties']['confidence']['knownValues'], 'G7: confidence in enum')
chk(ar['consultRecommendation'] in arr['properties']['consultRecommendation']['knownValues'], 'consultRecommendation in enum')
chk(ar['symptomDomain'] in arr['properties']['symptomDomain']['knownValues'], 'symptomDomain in enum')
s=sum(c['weightPermille'] for c in ar['contributions'])
chk(s==1000, f'contributions sum to 1000 permille (got {s})')
chk(all(set(c)>= {'factor','weightPermille'} for c in ar['contributions']), 'each contribution has factor+weightPermille')

# triageReferral
tr=json.load(open(f'{EX}/triageReferral.lower-back.example.json'))
trr=rec('triageReferral')
chk(set(trr['required'])<=set(tr), 'triageReferral: all required fields present')
chk(tr['targetActor'] in {'mitate','iyashi','kokoro'}, 'G3/G5: targetActor in {mitate,iyashi,kokoro}')
chk(isinstance(tr['emergencyFlag'],bool), 'G5: emergencyFlag is boolean')
chk(tr['referralReason'] in trr['properties']['referralReason']['knownValues'], 'referralReason in enum')

print('\nEXAMPLE CONFORMANCE', 'PASS' if ok else 'FAIL'); raise SystemExit(0 if ok else 1)
PY
```

**Last verified**: 2026-06-01 (R0) — EXAMPLE CONFORMANCE PASS.

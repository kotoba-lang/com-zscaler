# atsurae 誂え — Product Line Engineering (PLE) feature model

The product-FAMILY layer beneath **uchiwake 内訳** (per-product BOM) + **open-kyber 開** (ERP).
A feature model (mandatory / optional / xor / or + requires/excludes), from which valid
**variants** are derived (誂え = bespoke, configured-to-order), each variant's **BOM** composed
from the parts its selected features bind.

A **commons spec, never a license key**. **Spec + derivation only** — atsurae never manufactures
(the manufacturing actors build, under Council gate). Validity is **structural**, not a verdict.

```bash
./20-actors/atsurae/run_tests.sh                                       # 11 tests / 41 assertions
bb --classpath 20-actors 20-actors/atsurae/methods/feature_model.cljc  # product-line report
```

- ADR-2606212010 · clj-native R0 · `com.etzhayyim.atsurae.*`
- 15-feature synthetic OSS-robotics seed → **176** valid variants; platform = {robot-base, locomotion, power}.
- See `CLAUDE.md` for the model, invariants, and composition diagram.

# okaimono 御買物

Global product-discovery + **provisioning-commons** actor at `okaimono.etzhayyim.com`.
The charter-aligned answer to "an Amazon for etzhayyim" — **not** a marketplace clone but
its inversion, organized as three concentric rings:

- **Ring 0 — commons-first** (borrow / repair / secondhand / surplus): the best purchase is no purchase.
- **Ring 1 — internal economy** (SBT↔SBT over etzhayyim's own producing actors): a real, shippable storefront; USDC + warifu + 10% tithe.
- **Ring 2 — external world catalog** (discovery + compare now; self-checkout handoff at R0; 代理-purchase R3-gated).

No ads, no affiliate, no dark-patterns; Murakumo-only inference; kotoba-EAVT-native.

See `CLAUDE.md` for the full design and ADR-2606012100 for the decision record.

```
deploy:  KOTOBA_URL=http://127.0.0.1:8077 KOTOBA_TOKEN=<jwt> kotoba/deploy.sh
test:    cd py && python3 test_agent.py
```

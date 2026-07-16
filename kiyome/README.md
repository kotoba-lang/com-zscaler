# kiyome 清め — domestic / janitorial cleaning robotics

> *清め* = purification / cleansing. The actor that frees the most invisible labour on Earth.

**LPS #3** (ADR-2606032100). ISIC **T/N81** · ISCO **9111/9112** · UNSPSC **76**. DID
`did:web:etzhayyim.com:actor:kiyome`.

## Why cleaning work

~75 M domestic workers + tens of millions of janitorial/sanitation workers — invisible, gendered,
dignity-poor, and entirely un-automated by any actor. Freeing it is among the highest-Wellbecoming
acts available (gate **G6**).

## Privacy is the defining constraint

Cleaning means entering homes. Gate **G9** makes kiyome the opposite of a surveillance product:
on-device only, **no cloud imagery, no sensor feed, no biometric/facial recognition** — enforced as
hard `const` invariants in the lexicons (`onDeviceOnly: true`, `imageryRetained: false`,
`biometricCapture: false`). Displaced cleaners are registered for the tenure-weighted Displacement
Dividend (ADR-2606032130, gate **G2**).

## Honest

Dexterous manipulation in unstructured homes (dishes, clutter) is `:research` maturity. R0 = design only.

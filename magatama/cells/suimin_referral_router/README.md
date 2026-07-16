# suimin_referral_router — Pregel cell: referral routing to local sleep-medicine care

Per **ADR-2606072800 §Decision 3 G4** (referral-not-treatment) + §Decision 5.

Paired actor: [suimin](../../suimin/). Murakumo node (proposed): **levi**.

Surfaces what KIND of facility to consult (sleep-medicine outpatient / accredited sleep-testing
center / otolaryngology / respiratory / cardiology comorbidity / pediatric sleep / dental oral
appliance / emergency) and nearby facilities from a Council-ratified directory. **Presentation
only** — no appointment booking, no telehealth scheduling, no device sales (N6/N7).

## I/O

- **In**: gated output from `suimin_disclaimer_gate`
- **Out**: `com.etzhayyim.suimin.referralPathway` (terminal — patient is pointed to a local clinician)

## Gate enforcement

- **G4**: referral routing only — never a diagnosis, treatment, booking, or device sale.
- Coarse area only (no precise patient geolocation stored).

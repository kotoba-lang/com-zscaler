# yadori (宿り) — DNS-availability + domain-acquisition actor

**DID**: `did:web:etzhayyim.com:actor:yadori` · **Tier**: B · **Status**: R0 · **ADR**: 2606038400 (+ 2606012100 acquisition leg)

## What this is

The actor that **checks whether a domain name is free and shepherds its acquisition** — the piece
that was missing between "we minted an actor DID" and "the name actually exists" (until now
`etzhayyim.com` was registered by hand, ADR-2605222330). 宿り = taking up dwelling: a domain is where
an actor lodges.

It is the **charter-clean inverse of a retail registrar** (GoDaddy/Namecheap), the way okaimono
inverts Amazon: **no fiat inflow, no markup/affiliate/parking, no speculation**. Availability uses
**RDAP** (the structured public successor to WHOIS port-43); acquisition uses **okaimono
member-principal assisted-checkout** (ADR-2606012100) over the **Cloudflare Registrar at-cost path**,
so yadori is never the buyer and §1.3 (no external purchase inflow) holds without amendment.

ISIC J6311 · ISCO 2521/3513 · UNSPSC 81 (data infrastructure / domain services).

## Cells (langgraph→WASM; Murakumo-only; `.solve()` raises at R0)

**availability_check** (dan — coded; wraps `methods/availability.py`, live RDAP behind the G7
operator gate) · name_suggest (naphtali) · registrar_quote (gad) ·
**reservation** (asher — coded reference cell) · dns_provision (issachar).

## Gates (immutable R0→R3)

G1 read-only-availability (RDAP/EPP `<check>`/public WHOIS only; **no third-party zone enumeration**) ·
**G2 no-fiat-inflow / member-principal** (registrar fees never from religious-corp funds; okaimono
assisted-checkout; yadori never the buyer-of-record) · **G3 cloudflare-registrar-default**
(at-cost, no markup; GoDaddy/external fiat-markup registrars not recommended, Council-gated) ·
G4 Murakumo-only · **G5 no-server-key** (registrar/EPP creds member-held; authorization = member
signature, ADR-2605231525) · **G6 no-squatting** (no cybersquatting/typosquatting/trademark-
infringement/speculation/parking/drop-catch; held-trademark + confusable screen + Charter-Rider scan) ·
G7 outward-gated (live registrar mutate + live RDAP fetch Council Lv6+ + operator; R0 = offline +
intent only) · G8 sourcing-honesty (`:representative` RDAP bootstrap + fixtures) ·
G9 PII-consent (registrant WHOIS data encrypted + privacy-proxy default).

## Non-goals

N1 no domain speculation/resale/parking-for-revenue · N2 no cybersquatting/typosquatting/trademark
infringement/impersonation · N3 no bulk drop-catching/automated mass registration · N4 no third-party
zone surveillance product · N5 no DNS provisioning for prohibited content or detection-evasion
(fast-flux / DGA / phishing infra).

## Build / test

```
cd methods && python3 -m pytest test_availability.py      # RDAP classifier + IDN + alternatives (11)
cd cells   && python3 -m pytest test_state_machines.py     # availability_check + reservation (20)
```

(The repo's pytest plugin env is currently broken — `pydantic`/`langsmith`; prefix with
`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1` to run the suites in isolation.)

R0 = design + wired RDAP classifier (availability_check) + reservation state-machine +
`:representative` RDAP bootstrap. The **live RDAP fetch is wired but G7-gated**: it fires only when
an operator passes `operator_gate=True` in the cell state AND the process env `YADORI_ALLOW_LIVE_RDAP=1`
is set (otherwise offline → fixture or `:unknown`, never a socket). No live registration; that and
any registrar mutate are Council Lv6+ + operator gated (G7).

## Do not

- Do not integrate a fiat registrar (GoDaddy/Namecheap card checkout) or make yadori the
  buyer-of-record — G2 / §1.3. Acquisition is member-principal via okaimono (ADR-2606012100).
- Do not hold registrar/EPP credentials or a registration signing key server-side — G5 /
  ADR-2605231525. The member signs and pays.
- Do not surface or register a name that fails the held-trademark/confusable screen or the
  Charter-Rider scan, and do not register for speculation/resale/parking — G6 / N1 / N2
  (`reservation/state_machine.py` raises `ValueError`).
- Do not call any cell's `.solve()` — R0 scaffolds raise `RuntimeError` by design.
- Do not enable live RDAP fetch or any registrar mutate without operator + Council (G7).

# yadori 宿り — DNS-availability + domain acquisition

yadori checks whether a domain name is free and shepherds its acquisition for the etzhayyim
substrate — the missing link between minting an actor DID (`did:web:etzhayyim.com:actor:<handle>`)
and the underlying name actually existing.

It is deliberately **not** a retail registrar clone. Where GoDaddy/Namecheap monetize markup,
privacy upsell, parking ads, affiliate links, and speculation, yadori does the opposite:

- **Availability** is read-only via **RDAP** (the structured, rate-limit-friendly successor to WHOIS
  port-43), with public WHOIS as fallback. No third-party zone enumeration.
- **Naming** suggestions are generated Murakumo-only and pass a no-squatting eligibility screen plus
  the Charter-Rider §2(a)–(h) scanner before they are ever shown.
- **Acquisition** is **member-principal**: the member is the registrant-of-record and the payer,
  using okaimono assisted-checkout (ADR-2606012100) over the **Cloudflare Registrar at-cost path**.
  yadori never buys, never holds the registrar credential, never holds the signing key — so the
  no-external-purchase (§1.3) and no-server-key (ADR-2605231525) invariants hold without amendment.

## Status

R0 (design + working availability classifier + reservation state machine + `:representative` RDAP
bootstrap). Live RDAP fetch and any registrar mutation are Council Lv6+ + operator gated. See
`90-docs/adr/2606038400-*` and `CLAUDE.md` for gates G1–G9 and non-goals N1–N5.

## Try the availability classifier

```
python3 methods/availability.py example.com newproject.dev xn--example.jp
```

(Offline by default — answers come from the `:representative` bootstrap + fixtures; live RDAP is
gated.)

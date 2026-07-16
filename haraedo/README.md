# haraedo 祓戸

Global **bulky-waste (粗大ゴミ) disposal** actor — citizen intake + operator
logistics (受付 / 配車 / ルート / 担当者) + worldwide processing-facility registry.
See `CLAUDE.md` for role, gates, and boundary; ADR-2606010200 for the full
decision record.

## Quickstart

```bash
# deploy schema + seed + langgraph wasm actor to a running kotoba server (:8077)
./kotoba/deploy.sh
```

## See also

- toritsugi (procedure concierge — may surface haraedo) · hodoki (ELV dismantle) ·
  kanayama (metals recovery) · chigiri (legal/permit boundary)
- kotoba EAVT substrate (ADR-2605262130 / 2605312345)
- Murakumo-only inference (ADR-2605215000)

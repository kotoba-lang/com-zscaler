#!/usr/bin/env python3
"""haraedo 祓戸 — authoritative waste-facility ingestion (R2).

ADR-2606010200 §R2. Replaces the R0/R1 `:sourcing :representative` seed with
AUTHORITATIVE open-data facility registries. Carries a coded SOURCES registry
(per jurisdiction: the official dataset + license + provenance URL) and a
CSV→kotoba-EDN transform that stamps every emitted facility with
`:facility/sourcing :authoritative` + `:facility/source-url` + dataset id
(G8 non-fabrication: every authoritative record cites its provenance).

Usage:
    python3 fetch_facilities.py --list-sources
    python3 fetch_facilities.py --from <facilities.csv> --dataset jp.moe.ippan --out facilities.authoritative.edn

CSV columns (header row required):
    id,jurisdiction,name,kind,lat,lon,capacity_tonnes_day,accepted_categories,operating_hours,gate_fee_per_tonne
    (accepted_categories = ';'-separated item-category codes)

Network crawling of each portal is deferred (R2.1): this transform is the stable
seam; point it at a downloaded official extract. `--list-sources` documents where
to get each.
"""
from __future__ import annotations

import argparse
import csv
import os
import sys

# Coded authoritative-source registry (open / re-usable licenses only, per
# Charter Rider §2(e); no proprietary aggregators).
SOURCES = {
    "jp.moe.ippan": {
        "name": "一般廃棄物処理実態調査 (MOE Japan, municipal solid-waste facilities)",
        "jurisdiction_scope": "JP (all 市区町村)",
        "license": "政府標準利用規約 (CC-BY 4.0 compatible)",
        "url": "https://www.env.go.jp/recycle/waste_tech/ippan/",
    },
    "us.epa.frs": {
        "name": "EPA Facility Registry Service (FRS) — waste/recycling facilities",
        "jurisdiction_scope": "US (all states)",
        "license": "US public domain",
        "url": "https://www.epa.gov/frs",
    },
    "eu.eea.eprtr": {
        "name": "European Pollutant Release and Transfer Register (E-PRTR) waste facilities",
        "jurisdiction_scope": "EU/EEA",
        "license": "EEA open re-use (Decision 2011/833/EU)",
        "url": "https://industry.eea.europa.eu/",
    },
    "gb.defra.wdf": {
        "name": "WasteDataFlow / Environment Agency permitted waste sites",
        "jurisdiction_scope": "GB",
        "license": "Open Government Licence v3.0",
        "url": "https://environment.data.gov.uk/",
    },
}

VALID_KINDS = {"incinerator", "recycling-center", "landfill", "transfer-station",
               "bulky-dismantle", "compost", "mrf"}


def _kw(s):
    return ":" + s.strip()


def _edn_str(s):
    return '"' + str(s).replace("\\", "\\\\").replace('"', '\\"') + '"'


def transform(rows, dataset, source_url):
    """Yield kotoba EDN facility maps from CSV rows, flagged :authoritative."""
    out = []
    for r in rows:
        cats = [c for c in (r.get("accepted_categories", "") or "").split(";") if c.strip()]
        cats_edn = "#{" + " ".join(_kw(c) for c in cats) + "}" if cats else "#{}"
        kind = (r.get("kind", "") or "").strip()
        kind_edn = _kw(kind) if kind in VALID_KINDS else ":transfer-station"
        out.append(
            "{"
            f":facility/id {_edn_str(r['id'])} "
            f":facility/jurisdiction {_edn_str(r['jurisdiction'])} "
            f":facility/name {_edn_str(r['name'])} "
            f":facility/kind {kind_edn} "
            f":facility/lat {float(r['lat'])} "
            f":facility/lon {float(r['lon'])} "
            f":facility/capacity-tonnes-day {float(r.get('capacity_tonnes_day') or 0)} "
            f":facility/load-tonnes-today 0.0 "
            f":facility/accepted-categories {cats_edn} "
            f":facility/operating-hours {_edn_str(r.get('operating_hours', '') or '')} "
            f":facility/gate-fee-per-tonne {int(float(r.get('gate_fee_per_tonne') or 0))} "
            f":facility/sourcing :authoritative "
            f":facility/source-dataset {_edn_str(dataset)} "
            f":facility/source-url {_edn_str(source_url)}"
            "}"
        )
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--list-sources", action="store_true")
    ap.add_argument("--from", dest="csv_path")
    ap.add_argument("--dataset", choices=sorted(SOURCES))
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "facilities.authoritative.edn"))
    args = ap.parse_args()

    if args.list_sources or not args.csv_path:
        print("haraedo authoritative facility sources (open-license only):\n")
        for k, v in SOURCES.items():
            print(f"  {k}")
            print(f"    {v['name']}")
            print(f"    scope:   {v['jurisdiction_scope']}")
            print(f"    license: {v['license']}")
            print(f"    url:     {v['url']}\n")
        if not args.csv_path:
            print("Provide --from <csv> --dataset <id> to emit authoritative EDN.")
        return 0

    if not args.dataset:
        print("error: --dataset required with --from", file=sys.stderr)
        return 2
    src = SOURCES[args.dataset]
    with open(args.csv_path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    facilities = transform(rows, args.dataset, src["url"])
    header = (f";; haraedo 祓戸 — authoritative facilities (R2, ADR-2606010200)\n"
              f";; dataset: {args.dataset} — {src['name']}\n"
              f";; license: {src['license']}  source: {src['url']}\n"
              f";; :sourcing :authoritative — generated by fetch_facilities.py\n\n")
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(header + "[" + "\n ".join(facilities) + "]\n")
    print(f"wrote {len(facilities)} authoritative facilities → {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

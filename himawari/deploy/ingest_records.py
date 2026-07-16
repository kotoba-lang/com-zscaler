#!/usr/bin/env python3
"""himawari 向日葵 — ingest lexicon records into a live kotoba-server (PDS write path).

ADR-2606021200 + ADR-2606015000 (PDS refactor onto kotoba-server).

Reads the seven com.etzhayyim.himawari.* record types from seed.edn, projects each
entity map into a kotoba KG-ingest entity (id + claims + relations), and writes
them into the canonical kotoba Datom log via the kotoba-server PDS XRPC write path:

    1.  com.etzhayyim.pds.session.verify   (ADR-2606015000 D1) — verify the operator's
        session Proof-of-Possession (compact EdDSA JWS). kotoba-server resolves the
        signer DID (did:key trustless / did:web via ERC725-mirror doc) and verifies
        the signature zero-access (server holds no key). A write is GATED on a valid
        session PoP; this is the no-server-key substrate boundary.
    2.  com.etzhayyim.apps.kotobase.kg.ingest  — assert the entity's datoms into the named
        graph (canonical EAVT state; G6/G8). Each lexicon field becomes a `claim`
        (literal) or `relation` (ref). `kotoba commit` seals the hot arrangement.

Writes require an operator AT-session PoP token (KOTOBA_SESSION_POP). Without it the
run is a DRY RUN: it parses + projects + counts datoms and prints the would-be
requests, but performs NO writes (G11 — outward writes are operator/Council-gated).
NO non-kotoba store is ever used (substrate boundary).

Usage:
    # dry run (no token): parse + project + count only
    python3 ingest_records.py [--url http://127.0.0.1:8077] [--graph com.etzhayyim.himawari]

    # live ingest (operator session PoP present)
    KOTOBA_SESSION_POP=<compact-eddsa-jws> \\
      python3 ingest_records.py --url http://127.0.0.1:8077 --graph com.etzhayyim.himawari
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request

SEED = os.path.join(os.path.dirname(__file__), "seed.edn")

NSID_SESSION_VERIFY = "com.etzhayyim.pds.session.verify"
NSID_KG_INGEST = "com.etzhayyim.apps.kotobase.kg.ingest"

# The unique-identity attribute per himawari record namespace — the entity's `id`
# in the kg.ingest projection is taken from this attribute (mirrors schema.edn
# :db.unique/identity). A relation predicate (ref → another entity) is detected by
# membership in _REF_PREDS so it is emitted as a kg `relation`, not a `claim`.
_IDENTITY_ATTR = {
    "himawari.provenance": "lot-id",
    "himawari.wafer": "batch-id",
    "himawari.cell": "batch-id",
    "himawari.module": "serial",
    "himawari.loading": "cycle-id",
    "himawari.outbound": "manifest-id",
    "himawari.review": "id",
}
# ref-typed predicates (schema.edn :db.type/ref) — emitted as kg relations.
_REF_PREDS = {
    "himawari.wafer/feedstock-lot",
    "himawari.cell/wafer-batch",
    "himawari.module/cell-batch",
    "himawari.module/feedstock-lot",
    "himawari.outbound/loading",
}
# NB: :himawari.loading/module-serials is cardinality/many string (NOT a ref) and is
# emitted as one claim per element — see the list branch in project_entity below.


# --------------------------------------------------------------------------- #
# Minimal EDN reader (entity maps only) — same shape as okaimono/ingest_mcp.py.
# We only need to walk top-level {...} maps and read their `:ns/attr value` pairs.
# --------------------------------------------------------------------------- #
def _strip_comments(s: str) -> str:
    out = []
    in_str = False
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if in_str:
            out.append(c)
            if c == '"' and s[i - 1] != "\\":
                in_str = False
            i += 1
            continue
        if c == '"':
            in_str = True
            out.append(c)
            i += 1
            continue
        if c == ";":
            while i < n and s[i] != "\n":
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def _top_level_entities(s: str):
    """Yield each top-level {...} map literal inside the outer [ ... ] vector."""
    s = _strip_comments(s)
    start = s.find("[")
    if start < 0:
        return
    depth = 0
    buf = []
    in_str = False
    for c in s[start + 1:]:
        if in_str:
            buf.append(c)
            if c == '"':
                in_str = False
            continue
        if c == '"':
            in_str = True
            buf.append(c)
            continue
        if c == "{":
            depth += 1
            buf.append(c)
        elif c == "}":
            depth -= 1
            buf.append(c)
            if depth == 0:
                yield "".join(buf).strip()
                buf = []
        elif depth > 0:
            buf.append(c)


# token: a quoted string, a [vector], a :keyword, or a bare atom (number/symbol).
_TOKEN_RE = re.compile(
    r'"(?:[^"\\]|\\.)*"'      # string
    r"|\[[^\]]*\]"            # vector (no nested vectors in this seed)
    r"|:[^\s\[\]{}]+"         # keyword
    r"|[^\s\[\]{}]+"          # bare atom
)


def _read_value(tok: str):
    tok = tok.strip()
    if tok.startswith('"') and tok.endswith('"'):
        return json.loads(tok)  # JSON string decode handles escapes
    if tok.startswith("["):
        inner = tok[1:-1]
        return [_read_value(t.group(0)) for t in _TOKEN_RE.finditer(inner)]
    if tok.startswith(":"):
        return tok[1:]  # keyword → bare name (stored as a string claim)
    # numeric?
    try:
        return int(tok)
    except ValueError:
        try:
            return float(tok)
        except ValueError:
            return tok


def _parse_entity(block: str) -> dict:
    """Parse one `{:ns/attr value ...}` map into {attr-keyword: python-value}."""
    inner = block.strip()
    if inner.startswith("{"):
        inner = inner[1:]
    if inner.endswith("}"):
        inner = inner[:-1]
    toks = [m.group(0) for m in _TOKEN_RE.finditer(inner)]
    out: dict = {}
    i = 0
    while i < len(toks):
        key = toks[i]
        if not key.startswith(":"):
            i += 1
            continue
        if i + 1 >= len(toks):
            break
        out[key[1:]] = _read_value(toks[i + 1])
        i += 2
    return out


# --------------------------------------------------------------------------- #
# Project an EDN entity → kg.ingest request body (id + claims + relations).
# --------------------------------------------------------------------------- #
def _namespace(attr: str) -> str:
    """':himawari.module/serial' attr-name 'himawari.module/serial' → 'himawari.module'."""
    return attr.split("/", 1)[0]


def project_entity(ent: dict) -> dict | None:
    """Map one parsed himawari entity to a KgIngestReq-shaped dict.

    The entity id = the value of the record's :db.unique/identity attribute. Each
    other attribute becomes a `claim` (literal) or, for ref-typed predicates, a
    `relation` to the referenced entity id."""
    if not ent:
        return None
    ns = next((_namespace(a) for a in ent), None)
    if ns not in _IDENTITY_ATTR:
        return None
    id_attr = f"{ns}/{_IDENTITY_ATTR[ns]}"
    ent_id = ent.get(id_attr)
    if ent_id is None:
        return None

    claims = []
    relations = []
    for attr, val in ent.items():
        full = attr if "/" in attr else f"{ns}/{attr}"
        if full == id_attr:
            continue
        if full in _REF_PREDS and not isinstance(val, list):
            relations.append({"pred": full, "dstId": str(val)})
        elif isinstance(val, list):
            # cardinality/many — one claim per element (e.g. module-serials)
            for v in val:
                claims.append({"pred": full, "value": str(v)})
        else:
            claims.append({"pred": full, "value": str(val)})
    return {
        "id": str(ent_id),
        "type": ns,
        "labelEn": str(ent_id),
        "extractor": "himawari/deploy/ingest_records.py",
        "license": "Apache-2.0 + etzhayyim Charter Rider v2.0",
        "claims": claims,
        "relations": relations,
    }


# --------------------------------------------------------------------------- #
# kotoba-server PDS XRPC client (session verify → kg.ingest).
# --------------------------------------------------------------------------- #
def _post(url: str, body: dict, token: str | None = None) -> tuple[int, dict]:
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        try:
            payload = json.loads(e.read().decode("utf-8") or "{}")
        except Exception:
            payload = {"error": str(e)}
        return e.code, payload


def verify_session(base_url: str, pop_token: str) -> tuple[bool, dict]:
    """ADR-2606015000 D1: verify the operator session PoP before any write. The
    server resolves the signer DID and verifies the JWS zero-access. Returns
    (valid, claims-or-reason)."""
    status, body = _post(
        f"{base_url}/xrpc/{NSID_SESSION_VERIFY}",
        {"token": pop_token},
    )
    return bool(body.get("valid")) and status == 200, body


def ingest_entity(base_url: str, graph: str, entity: dict, pop_token: str) -> tuple[int, dict]:
    """Write one projected entity into the kotoba Datom log via kg.ingest. The
    operator session PoP is forwarded as the bearer credential; the server applies
    its own write authorization over the canonical state (no server-held key)."""
    body = {**entity, "graph": graph}
    return _post(f"{base_url}/xrpc/{NSID_KG_INGEST}", body, token=pop_token)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default=os.environ.get("KOTOBA_URL", "http://127.0.0.1:8077"))
    ap.add_argument("--graph", default=os.environ.get("HIMAWARI_GRAPH", "com.etzhayyim.himawari"))
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    with open(SEED, encoding="utf-8") as f:
        raw = f.read()

    entities = []
    for block in _top_level_entities(raw):
        proj = project_entity(_parse_entity(block))
        if proj:
            entities.append(proj)

    total_datoms = sum(len(e["claims"]) + len(e["relations"]) + 1 for e in entities)
    print(f"   parsed {len(entities)} himawari records (~{total_datoms} datoms) from seed.edn")

    pop = os.environ.get("KOTOBA_SESSION_POP")
    if args.dry_run or not pop:
        print("   DRY RUN — no writes. Set KOTOBA_SESSION_POP (operator session PoP) to ingest.")
        for e in entities:
            print(f"     - {e['type']:<20} id={e['id']:<24} "
                  f"claims={len(e['claims'])} relations={len(e['relations'])}")
        return 0

    # Live ingest — gate on a valid operator session PoP (ADR-2606015000 D1).
    print(f"--> verifying operator session PoP via {NSID_SESSION_VERIFY}")
    ok, vinfo = verify_session(args.url, pop)
    if not ok:
        print(f"!! session PoP rejected: {vinfo.get('reason', vinfo)} — refusing to write (no-server-key boundary)",
              file=sys.stderr)
        return 1
    print(f"    session valid for {vinfo.get('did', '?')}")

    written = 0
    for e in entities:
        status, body = ingest_entity(args.url, args.graph, e, pop)
        if status == 200 and body.get("ok"):
            written += 1
            print(f"    wrote {e['type']} id={e['id']} "
                  f"(subjectCid={body.get('subjectCid', '?')[:16]}…, quads={body.get('quadCount')})")
        else:
            print(f"!! ingest failed for {e['id']}: {status} {body}", file=sys.stderr)
    print(f"==> wrote {written}/{len(entities)} records. Run `kotoba commit` to seal the hot arrangement.")
    return 0 if written == len(entities) else 1


if __name__ == "__main__":
    sys.exit(main())

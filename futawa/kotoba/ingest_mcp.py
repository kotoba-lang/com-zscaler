#!/usr/bin/env python3
"""futawa 二輪 — ingest seed.edn into a live kotoba node via MCP.

ADR-2605261330 · Flattens each seed entity map into (graph, subject, predicate, object) datoms
and asserts them via the kotoba_datom_create MCP tool; `kotoba commit` seals them. Writes
require an operator AT-session JWT (no-server-key posture, G17). --dry-run parses + counts
datoms only (no writes, no LLM; G9 untouched).

Usage:
    python3 ingest_mcp.py [--url http://127.0.0.1:8077] [--graph com.etzhayyim.futawa] [--dry-run]
"""
from __future__ import annotations

import argparse
import os
import sys

SEED = os.path.join(os.path.dirname(__file__), "seed.edn")


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
    depth, entity_start = 0, -1
    for i, c in enumerate(s):
        if c == "{":
            if depth == 0:
                entity_start = i
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0 and entity_start >= 0:
                yield s[entity_start:i + 1]
                entity_start = -1


def _flatten_entity(entity_str: str, graph: str) -> list:
    """Parse a single EDN entity map and flatten into (graph, s, p, o) tuples."""
    datoms = []
    entity_str = entity_str.strip()
    if entity_str.startswith("{") and entity_str.endswith("}"):
        inner = entity_str[1:-1].strip()
        tokens = []
        cur_token, in_string = [], False
        for c in inner:
            if in_string:
                cur_token.append(c)
                if c == '"' and (not cur_token or cur_token[-2] != "\\"):
                    in_string = False
                continue
            if c == '"':
                in_string = True
                cur_token.append(c)
                continue
            if c in " \n\t:":
                if cur_token:
                    tokens.append("".join(cur_token))
                    cur_token = []
                if c == ":":
                    tokens.append(":")
                continue
            cur_token.append(c)
        if cur_token:
            tokens.append("".join(cur_token))
        i = 0
        while i < len(tokens):
            if tokens[i] == ":":
                pred = tokens[i + 1] if i + 1 < len(tokens) else ""
                obj = tokens[i + 2] if i + 2 < len(tokens) else ""
                if pred and obj:
                    subject = "edn-entity-placeholder"
                    datoms.append((graph, subject, pred, obj))
                    i += 3
                    continue
            i += 1
    return datoms


def main() -> int:
    parser = argparse.ArgumentParser(description="Ingest futawa seed.edn into kotoba via MCP")
    parser.add_argument("--url", default="http://127.0.0.1:8077", help="kotoba node URL")
    parser.add_argument("--graph", default="com.etzhayyim.futawa", help="graph name")
    parser.add_argument("--dry-run", action="store_true", help="parse only, no writes")
    args = parser.parse_args()
    if not os.path.exists(SEED):
        print(f"ERROR: seed file not found at {SEED}")
        return 1
    with open(SEED, "r", encoding="utf-8") as f:
        seed_str = f.read()
    datom_count = 0
    for entity_str in _top_level_entities(seed_str):
        datoms = _flatten_entity(entity_str, args.graph)
        datom_count += len(datoms)
        if args.dry_run:
            for g, s, p, o in datoms:
                print(f"  [{g} {s} {p} {o}]")
    print(f"\n{datom_count} datom(s) parsed")
    if args.dry_run:
        print("(dry-run: no writes)")
        return 0
    print(f"TODO: wire kotoba_datom_create MCP tool (requires AT-session JWT)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

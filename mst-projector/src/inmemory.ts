/**
 * In-memory index implementations for text search, attribute lookup, and aggregates.
 *
 * These pure-TS classes power the OrchestrationProjector reference implementation
 * and serve as the test substrate. Production deployments swap these for LanceDB
 * (text search) and DuckDB (attribute + aggregate) while keeping the same interface.
 */

/**
 * Text search index — TF-lite scoring via token matching.
 * No embedding model needed; enables instant feedback in tests.
 */
export class InMemoryTextIndex {
  private docs = new Map<
    string,
    { fields: Record<string, string>; tokens: Set<string> }
  >();

  upsert(rkey: string, fields: Record<string, string | undefined>): void {
    const cleaned: Record<string, string> = {};
    const tokens = new Set<string>();
    for (const [k, v] of Object.entries(fields)) {
      if (typeof v !== "string" || !v) continue;
      cleaned[k] = v;
      for (const t of tokenize(v)) tokens.add(t);
    }
    this.docs.set(rkey, { fields: cleaned, tokens });
  }

  delete(rkey: string): void {
    this.docs.delete(rkey);
  }

  search(
    query: string,
    limit = 20,
  ): Array<{ rkey: string; score: number; fields: Record<string, string> }> {
    const qTokens = tokenize(query);
    if (qTokens.length === 0) return [];
    const results: Array<{
      rkey: string;
      score: number;
      fields: Record<string, string>;
    }> = [];
    for (const [rkey, doc] of this.docs) {
      let hits = 0;
      for (const t of qTokens) if (doc.tokens.has(t)) hits += 1;
      if (hits === 0) continue;
      const score = hits / qTokens.length;
      results.push({ rkey, score, fields: doc.fields });
    }
    results.sort((a, b) => b.score - a.score);
    return results.slice(0, limit);
  }

  size(): number {
    return this.docs.size;
  }
}

/**
 * Attribute inverted index: attribute → value → Set<rkey>.
 * Enables O(1) lookups: "what records have status=published?"
 */
export class InMemoryAttributeIndex {
  private indexes = new Map<string, Map<string, Set<string>>>();

  upsert(rkey: string, attributes: Record<string, unknown>): void {
    for (const [attr, val] of Object.entries(attributes)) {
      if (val === undefined || val === null) continue;
      const valStr = Array.isArray(val) ? val.join(",") : String(val);
      let attrMap = this.indexes.get(attr);
      if (!attrMap) {
        attrMap = new Map();
        this.indexes.set(attr, attrMap);
      }
      let valSet = attrMap.get(valStr);
      if (!valSet) {
        valSet = new Set();
        attrMap.set(valStr, valSet);
      }
      valSet.add(rkey);
    }
  }

  delete(rkey: string): void {
    for (const attrMap of this.indexes.values()) {
      for (const valSet of attrMap.values()) valSet.delete(rkey);
    }
  }

  query(attribute: string, value: string, limit = 50): string[] {
    const attrMap = this.indexes.get(attribute);
    if (!attrMap) return [];
    const valSet = attrMap.get(value);
    if (!valSet) return [];
    return Array.from(valSet).slice(0, limit);
  }

  attributeValues(attribute: string): string[] {
    return Array.from(this.indexes.get(attribute)?.keys() ?? []);
  }
}

/**
 * Aggregate counters: groupBy-attribute → value → count.
 * Enables O(1) stats queries: "how many papers per status?"
 */
export class InMemoryAggregateIndex {
  private counts = new Map<string, Map<string, number>>();

  upsert(
    rkey: string,
    prev: Record<string, unknown> | undefined,
    next: Record<string, unknown>,
    groupBy: string[],
  ): void {
    for (const attr of groupBy) {
      const prevVal = prev ? String(prev[attr] ?? "") : null;
      const nextVal = String(next[attr] ?? "");
      if (prevVal === nextVal) continue;
      let map = this.counts.get(attr);
      if (!map) {
        map = new Map();
        this.counts.set(attr, map);
      }
      if (prevVal && prevVal !== "") {
        map.set(prevVal, Math.max(0, (map.get(prevVal) ?? 0) - 1));
      }
      if (nextVal) {
        map.set(nextVal, (map.get(nextVal) ?? 0) + 1);
      }
    }
  }

  delete(rkey: string, prev: Record<string, unknown>, groupBy: string[]): void {
    for (const attr of groupBy) {
      const prevVal = String(prev[attr] ?? "");
      if (!prevVal) continue;
      const map = this.counts.get(attr);
      if (!map) continue;
      map.set(prevVal, Math.max(0, (map.get(prevVal) ?? 0) - 1));
    }
  }

  counts_for(groupBy: string): Record<string, number> {
    const map = this.counts.get(groupBy);
    if (!map) return {};
    const out: Record<string, number> = {};
    for (const [k, v] of map) if (v > 0) out[k] = v;
    return out;
  }
}

/**
 * Tokenize text for search: lowercase, split on non-alphanumeric boundaries,
 * filter tokens <2 chars. Supports ASCII + CJK (Japanese/Chinese).
 */
function tokenize(s: string): string[] {
  return s
    .toLowerCase()
    .split(/[^a-z0-9぀-ゟ゠-ヿ一-鿿]+/u)
    .filter((t) => t.length >= 2);
}

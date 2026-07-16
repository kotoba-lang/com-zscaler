/**
 * @etzhayyim/ameno/rag-lora — RAG search + adapter selection facade.
 *
 * Scaffold v0.1.0. Returns empty results so the appview's history / adapter
 * panel renders cleanly without a graph backend. Real impl hits the
 * vertex_lora_adapter / vertex_embedding tables via XRPC; we'll wire that
 * in once the substrate-side index is migrated to the MST + IPFS pipeline
 * (ADR-2605171800).
 */

export interface RagResult {
  text: string;
  score: number;
  /** at:// URI of the source record. */
  uri: string;
}

export interface AdapterCandidate {
  adapterId: string;
  domain: string;
  adapterRank: number;
  baseModel: string;
  /** Selection score from the ranker (0..1). */
  score: number;
}

export interface RagLoraContext {
  /** Pre-rendered context block to prepend to the user prompt. */
  contextPrompt: string;
  /** Adapters the ranker recommends activating for this query. */
  recommendedAdapters: AdapterCandidate[];
}

export async function ragSearch(_query: string, _opts?: { limit?: number }): Promise<RagResult[]> {
  return [];
}

export async function listActorAdapters(_actorDid: string): Promise<AdapterCandidate[]> {
  return [];
}

export async function selectAdapters(
  _query: string,
  _candidates: AdapterCandidate[],
): Promise<AdapterCandidate[]> {
  return [];
}

export async function ragLoraSelect(
  _query: string,
  _actorDid: string | null,
): Promise<RagLoraContext> {
  return { contextPrompt: "", recommendedAdapters: [] };
}

export function buildRagContextPrompt(_results: RagResult[]): string {
  return "";
}

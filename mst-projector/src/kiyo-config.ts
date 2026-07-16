/**
 * kiyo reference implementation projector configuration.
 *
 * kiyo is the Phase 3 reference impl:
 * - Small data (10k+ papers, searchable via title/abstract)
 * - IVF text search most demonstrable
 * - Aggregates (by status, language, field) for getStats
 * - Attribute inverted index (status → papers) for filtered lists
 *
 * Post-Phase3 deployment: kiyo.searchPapers goes from O(N) >500ms
 * to O(log N) <50ms via IVF embeddings.
 */

import type { ProjectorConfig } from "./types.js";

/**
 * kiyo projector configuration for Phase 3 reference implementation.
 *
 * Collections:
 * - paper: searchable via title+abstract; aggregates by status/language/field
 * - review: searchable via conclusion; aggregates by status
 * - endorsement: no text search; aggregates by endorserDid
 */
export const kiyoProjector: ProjectorConfig = {
  actorDid: "did:web:kiyo.etzhayyim.com",
  collections: {
    paper: {
      collection: "com.etzhayyim.kiyo.paper",
      textIndex: {
        fields: ["title", "titleLocal", "abstract", "abstractLocal"],
        model: "all-MiniLM-L6-v2",
      },
      attributes: ["status", "field", "language"],
      aggregates: ["status", "language", "field"],
    },
    review: {
      collection: "com.etzhayyim.kiyo.review",
      textIndex: {
        fields: ["conclusion"],
        model: "all-MiniLM-L6-v2",
      },
      attributes: ["status"],
      aggregates: ["status"],
    },
    endorsement: {
      collection: "com.etzhayyim.kiyo.endorsement",
      // No text search — endorsements are small structured records
      attributes: ["endorserDid"],
      aggregates: ["endorserDid"],
    },
  },
};

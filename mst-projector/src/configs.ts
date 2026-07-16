/**
 * Per-actor ProjectorConfig registry for the 26 non-kiyo kotoba actors
 * (kiyo lives in `src/kiyo-config.ts` as the reference impl).
 *
 * Defaults follow the same shape as `kiyoProjector`:
 *   - textIndex.fields: human-readable headline fields (title / name / abstract / etc.)
 *   - textIndex.model:  "all-MiniLM-L6-v2" (Phase 3c default; promote to bge-large-en-v1.5
 *                       for the larger English-heavy actors before deploy)
 *   - attributes:       facet-friendly fields used in dashboards
 *   - aggregates:       same as attributes by default — pre-computed counts
 *
 * Per ADR-2605212000 §"Per-actor `ProjectorConfig` files for all 25 kotoba actors".
 *
 * Refinement loop: after each actor is benchmarked at production scale, edit
 * its entry in this file (or split into `src/configs/<actor>.ts` once the
 * registry grows). All entries are NEW data — they do not modify scaffold
 * orchestrator / inmemory / adapters modules.
 */

import type { ProjectorConfig } from "./types.js";

const MODEL = "all-MiniLM-L6-v2" as const;

/** anime — anime titles, status / genre facets. */
export const animeProjector: ProjectorConfig = {
  actorDid: "did:web:anime.etzhayyim.com",
  collections: {
    title: {
      collection: "com.etzhayyim.anime.title",
      textIndex: { fields: ["title", "titleLocal", "synopsis"], model: MODEL },
      attributes: ["status", "genre", "studio"],
      aggregates: ["status", "genre"],
    },
  },
};

/** bpmn — BPMN process catalog, namespace facets. */
export const bpmnProjector: ProjectorConfig = {
  actorDid: "did:web:bpmn.etzhayyim.com",
  collections: {
    process: {
      collection: "com.etzhayyim.bpmn.process",
      textIndex: { fields: ["name", "description"], model: MODEL },
      attributes: ["status", "namespace"],
      aggregates: ["status", "namespace"],
    },
  },
};

/** dns — DNS zones + records, recordType facet. */
export const dnsProjector: ProjectorConfig = {
  actorDid: "did:web:dns.etzhayyim.com",
  collections: {
    zone: {
      collection: "com.etzhayyim.dns.zone",
      textIndex: { fields: ["zoneName"], model: MODEL },
      attributes: ["status", "tld"],
      aggregates: ["status", "tld"],
    },
    record: {
      collection: "com.etzhayyim.dns.record",
      attributes: ["recordType", "zoneDid"],
      aggregates: ["recordType"],
    },
  },
};

/** gameka — game / interactive media catalog. */
export const gamekaProjector: ProjectorConfig = {
  actorDid: "did:web:gameka.etzhayyim.com",
  collections: {
    title: {
      collection: "com.etzhayyim.gameka.title",
      textIndex: { fields: ["title", "summary"], model: MODEL },
      attributes: ["status", "platform", "genre"],
      aggregates: ["status", "platform"],
    },
  },
};

/** gtin — GTIN / barcode registry, GS1 prefix facet. */
export const gtinProjector: ProjectorConfig = {
  actorDid: "did:web:gtin.etzhayyim.com",
  collections: {
    product: {
      collection: "com.etzhayyim.gtin.product",
      textIndex: { fields: ["name", "brand"], model: MODEL },
      attributes: ["status", "category", "gs1Prefix"],
      aggregates: ["status", "category"],
    },
  },
};

/** hakkou — fermentation / brewing catalog. */
export const hakkouProjector: ProjectorConfig = {
  actorDid: "did:web:hakkou.etzhayyim.com",
  collections: {
    item: {
      collection: "com.etzhayyim.hakkou.item",
      textIndex: { fields: ["name", "description"], model: MODEL },
      attributes: ["status", "category", "starter"],
      aggregates: ["status", "category"],
    },
  },
};

/** hanrei — Japanese case law / statutes / gazette. Three collections. */
export const hanreiProjector: ProjectorConfig = {
  actorDid: "did:web:hanrei.etzhayyim.com",
  collections: {
    case: {
      collection: "com.etzhayyim.hanrei.case",
      textIndex: { fields: ["title", "summary", "tags"], model: MODEL },
      attributes: ["jurisdiction", "court"],
      aggregates: ["jurisdiction", "court"],
    },
    law: {
      collection: "com.etzhayyim.hanrei.law",
      attributes: ["jurisdiction", "type"],
      aggregates: ["jurisdiction"],
    },
    gazetteEntry: {
      collection: "com.etzhayyim.hanrei.gazetteEntry",
      attributes: ["jurisdiction"],
      aggregates: ["jurisdiction"],
    },
  },
};

/** houbun — statutory text corpus. */
export const houbunProjector: ProjectorConfig = {
  actorDid: "did:web:houbun.etzhayyim.com",
  collections: {
    statute: {
      collection: "com.etzhayyim.houbun.statute",
      textIndex: { fields: ["title", "preamble"], model: MODEL },
      attributes: ["status", "language", "jurisdiction"],
      aggregates: ["status", "language"],
    },
  },
};

/** houki — regulatory rules registry. */
export const houkiProjector: ProjectorConfig = {
  actorDid: "did:web:houki.etzhayyim.com",
  collections: {
    regulation: {
      collection: "com.etzhayyim.houki.regulation",
      textIndex: { fields: ["title", "summary"], model: MODEL },
      attributes: ["status", "sourceJurisdiction"],
      aggregates: ["status", "sourceJurisdiction"],
    },
  },
};

/** houshi — religious / charitable organisation registry. */
export const houshiProjector: ProjectorConfig = {
  actorDid: "did:web:houshi.etzhayyim.com",
  collections: {
    organization: {
      collection: "com.etzhayyim.houshi.organization",
      textIndex: { fields: ["name", "description"], model: MODEL },
      attributes: ["status", "sector", "jurisdiction"],
      aggregates: ["status", "sector"],
    },
  },
};

/** ipaddress — ASN / provider / scan registry. */
export const ipaddressProjector: ProjectorConfig = {
  actorDid: "did:web:ipaddress.etzhayyim.com",
  collections: {
    provider: {
      collection: "com.etzhayyim.ipaddress.provider",
      textIndex: { fields: ["name", "slug"], model: MODEL },
      attributes: ["countryIso3", "abuseType"],
      aggregates: ["countryIso3"],
    },
    scan: {
      collection: "com.etzhayyim.ipaddress.scan",
      attributes: ["providerDid", "scanType"],
      aggregates: ["providerDid", "scanType"],
    },
  },
};

/** isbn — book registry, language facet. */
export const isbnProjector: ProjectorConfig = {
  actorDid: "did:web:isbn.etzhayyim.com",
  collections: {
    book: {
      collection: "com.etzhayyim.isbn.book",
      textIndex: { fields: ["title", "subtitle", "authors"], model: MODEL },
      attributes: ["status", "language", "publisher"],
      aggregates: ["status", "language"],
    },
  },
};

/** isin — securities registry, market / currency facets. */
export const isinProjector: ProjectorConfig = {
  actorDid: "did:web:isin.etzhayyim.com",
  collections: {
    security: {
      collection: "com.etzhayyim.isin.security",
      textIndex: { fields: ["name", "ticker", "issuer"], model: MODEL },
      attributes: ["status", "market", "currency"],
      aggregates: ["status", "market"],
    },
  },
};

/** ki — biota / organism registry. */
export const kiProjector: ProjectorConfig = {
  actorDid: "did:web:ki.etzhayyim.com",
  collections: {
    organism: {
      collection: "com.etzhayyim.ki.organism",
      textIndex: {
        fields: ["scientificName", "commonName", "vernacular"],
        model: MODEL,
      },
      attributes: ["status", "kingdom", "iucnStatus"],
      aggregates: ["kingdom", "iucnStatus"],
    },
  },
};

/** koke — moss / lichen specimen registry. */
export const kokeProjector: ProjectorConfig = {
  actorDid: "did:web:koke.etzhayyim.com",
  collections: {
    specimen: {
      collection: "com.etzhayyim.koke.specimen",
      textIndex: { fields: ["scientificName", "locality"], model: MODEL },
      attributes: ["status", "locality", "family"],
      aggregates: ["family"],
    },
  },
};

/** manga — manga title catalog. */
export const mangaProjector: ProjectorConfig = {
  actorDid: "did:web:manga.etzhayyim.com",
  collections: {
    title: {
      collection: "com.etzhayyim.manga.title",
      textIndex: { fields: ["title", "titleLocal", "synopsis"], model: MODEL },
      attributes: ["status", "genre", "publicationStatus"],
      aggregates: ["status", "genre"],
    },
  },
};

/** narou — web novel platform mirror. */
export const narouProjector: ProjectorConfig = {
  actorDid: "did:web:narou.etzhayyim.com",
  collections: {
    novel: {
      collection: "com.etzhayyim.narou.novel",
      textIndex: { fields: ["title", "synopsis"], model: MODEL },
      attributes: ["status", "genre", "completionStatus"],
      aggregates: ["status", "completionStatus"],
    },
  },
};

/** ndc — Nippon Decimal Classification mirror. */
export const ndcProjector: ProjectorConfig = {
  actorDid: "did:web:ndc.etzhayyim.com",
  collections: {
    classification: {
      collection: "com.etzhayyim.ndc.classification",
      textIndex: { fields: ["label", "description"], model: MODEL },
      attributes: ["status", "level", "parent"],
      aggregates: ["status", "level"],
    },
  },
};

/** ocel — OCEL process model registry. */
export const ocelProjector: ProjectorConfig = {
  actorDid: "did:web:ocel.etzhayyim.com",
  collections: {
    process: {
      collection: "com.etzhayyim.ocel.process",
      textIndex: { fields: ["name", "description"], model: MODEL },
      attributes: ["status", "namespace"],
      aggregates: ["status"],
    },
  },
};

/** open-banking — financial institution registry. */
export const openBankingProjector: ProjectorConfig = {
  actorDid: "did:web:open-banking.etzhayyim.com",
  collections: {
    institution: {
      collection: "com.etzhayyim.openBanking.institution",
      textIndex: { fields: ["name", "country"], model: MODEL },
      attributes: ["status", "country", "type"],
      aggregates: ["country", "type"],
    },
  },
};

/** open-denki — electricity utility registry. */
export const openDenkiProjector: ProjectorConfig = {
  actorDid: "did:web:open-denki.etzhayyim.com",
  collections: {
    utility: {
      collection: "com.etzhayyim.openDenki.utility",
      textIndex: { fields: ["name", "region"], model: MODEL },
      attributes: ["status", "region", "voltageClass"],
      aggregates: ["region", "voltageClass"],
    },
  },
};

/** open-isco — ISCO occupation classifier. */
export const openIscoProjector: ProjectorConfig = {
  actorDid: "did:web:open-isco.etzhayyim.com",
  collections: {
    occupation: {
      collection: "com.etzhayyim.openIsco.occupation",
      textIndex: { fields: ["title", "definition"], model: MODEL },
      attributes: ["status", "majorGroup", "language"],
      aggregates: ["majorGroup", "language"],
    },
  },
};

/** otakiage — disposal-rite / decommissioning event registry. */
export const otakiageProjector: ProjectorConfig = {
  actorDid: "did:web:otakiage.etzhayyim.com",
  collections: {
    event: {
      collection: "com.etzhayyim.otakiage.event",
      textIndex: { fields: ["title", "location"], model: MODEL },
      attributes: ["status", "kind"],
      aggregates: ["status", "kind"],
    },
  },
};

/** sbom — Software Bill of Materials registry. */
export const sbomProjector: ProjectorConfig = {
  actorDid: "did:web:sbom.etzhayyim.com",
  collections: {
    component: {
      collection: "com.etzhayyim.sbom.component",
      textIndex: { fields: ["name", "supplier"], model: MODEL },
      attributes: ["status", "ecosystem", "license"],
      aggregates: ["ecosystem", "license"],
    },
  },
};

/** tsukuru — manufacturing item / lifecycle registry. */
export const tsukuruProjector: ProjectorConfig = {
  actorDid: "did:web:tsukuru.etzhayyim.com",
  collections: {
    product: {
      collection: "com.etzhayyim.tsukuru.product",
      textIndex: { fields: ["name", "description"], model: MODEL },
      attributes: ["status", "lifecycleStage", "category"],
      aggregates: ["lifecycleStage", "category"],
    },
  },
};

/** yoro — circular-economy items / repair registry. */
export const yoroProjector: ProjectorConfig = {
  actorDid: "did:web:yoro.etzhayyim.com",
  collections: {
    item: {
      collection: "com.etzhayyim.yoro.item",
      textIndex: { fields: ["name", "condition"], model: MODEL },
      attributes: ["status", "category", "repairability"],
      aggregates: ["status", "category"],
    },
  },
};

/** Registry for all 26 non-kiyo kotoba actors. */
export const ALL_PROJECTORS: Record<string, ProjectorConfig> = {
  anime: animeProjector,
  bpmn: bpmnProjector,
  dns: dnsProjector,
  gameka: gamekaProjector,
  gtin: gtinProjector,
  hakkou: hakkouProjector,
  hanrei: hanreiProjector,
  houbun: houbunProjector,
  houki: houkiProjector,
  houshi: houshiProjector,
  ipaddress: ipaddressProjector,
  isbn: isbnProjector,
  isin: isinProjector,
  ki: kiProjector,
  koke: kokeProjector,
  manga: mangaProjector,
  narou: narouProjector,
  ndc: ndcProjector,
  ocel: ocelProjector,
  "open-banking": openBankingProjector,
  "open-denki": openDenkiProjector,
  "open-isco": openIscoProjector,
  otakiage: otakiageProjector,
  sbom: sbomProjector,
  tsukuru: tsukuruProjector,
  yoro: yoroProjector,
};

/** Quick lookup by actor slug → full ProjectorConfig. */
export function getProjectorConfig(actorSlug: string): ProjectorConfig | undefined {
  return ALL_PROJECTORS[actorSlug];
}

//! kanae-fiscal-ingest — kanae (鼎) global fiscal-flow ingest VERIFICATION actor
//!
//! A kotoba `kotoba-node` WIT world guest. Demonstrates the kanae R0 pipeline
//! end-to-end against the live kotoba runtime + Murakumo (ADR-2605302300 +
//! ADR-2605301625):
//!
//!   1. ingest a fiscal-flow slice (domestic appropriation→outlay→recipient
//!      chain + an inter-governmental transfer)
//!   2. assert `kanae.fundFlowEdge` datoms into kotoba EAVT (the datomic
//!      kotoba api the user asked to persist into)
//!   3. call Murakumo `llm.infer` (gemma-4-26B-A4B-it) for a FACTUAL,
//!      NON-adjudicating narrative of the flow subgraph
//!   4. assert a `kanae.flowNarrative` datom (with murakumo attestation)
//!
//! CONSTITUTIONAL DISCIPLINE (mirrors ADR-2605302300 §4):
//!   - G4 NON-adjudicating: the narrative prompt forbids legal-conclusion
//!     language; the flowNarrative datom carries nonAdjudicatingNotice=true.
//!   - G7 Murakumo-only: inference goes through the host `llm.infer` bridge,
//!     which the kotoba server wires to the Murakumo fleet
//!     (KOTOBA_INFERENCE_URL + KOTOBA_INFERENCE_MODEL=gemma-4-26B-A4B-it).
//!     No vendor-LLM path exists in this guest.
//!   - G2 kotoba-native: every datom is written via `kqe.assert-quad`
//!     (kotoba EAVT); no RisingWave / external store.
//!   - G5 provenance: each edge carries its source-record id.
//!
//! This is a VERIFICATION HARNESS, NOT one of the 5 gated production kanae
//! cells (those remain path-reserved until Council Lv6+ ratify).

wit_bindgen::generate!({
    path: "../../../../40-engine/kotoba/crates/kotoba-runtime/wit/world.wit",
    world: "kotoba-node",
});

use kotoba::kais::{auth, chain, kqe, llm};

/// Murakumo model the kanae narrative leg targets. NOTE: the kotoba host
/// `llm.infer` currently selects the served model via the server env
/// `KOTOBA_INFERENCE_MODEL`; this tag is passed as the WIT `model-cid` for
/// forward-compatibility + provenance and documents kanae's intended model.
const MODEL_TAG: &str = "gemma-4-26B-A4B-it";

/// The kotoba graph kanae writes its fiscal-flow datoms into.
const GRAPH: &str = "etzhayyim/kanae/fiscal-flow";

/// One fiscal-flow edge in the verification sample.
struct FlowEdge {
    edge_id: &'static str,
    flow_class: &'static str,
    from_label: &'static str,
    to_label: &'static str,
    amount: &'static str,
    currency: &'static str,
    period: &'static str,
    jurisdiction: &'static str,
    source_record: &'static str,
}

/// A small, representative slice of global government fiscal flows.
/// Domestic full chain (USAspending-shaped) + inter-governmental (IMF-shaped).
/// These are illustrative VERIFICATION values, not pinned corpus records.
const SAMPLE_FLOWS: &[FlowEdge] = &[
    FlowEdge {
        edge_id: "us-fy2024-appropriation-050-0001",
        flow_class: "appropriation",
        from_label: "USA/Congress/appropriation:050-0001",
        to_label: "USA/agency/dept-of-x/program:P-12",
        amount: "1200000000",
        currency: "USD",
        period: "FY2024",
        jurisdiction: "USA",
        source_record: "gov.dataset.budgetRecord:usaspending:fy2024:050-0001",
    },
    FlowEdge {
        edge_id: "us-fy2024-outlay-050-0001-q3",
        flow_class: "outlay",
        from_label: "USA/agency/dept-of-x/program:P-12",
        to_label: "recipient-class:research-universities",
        amount: "318000000",
        currency: "USD",
        period: "FY2024-Q3",
        jurisdiction: "USA",
        source_record: "gov.dataset.budgetRecord:usaspending:fy2024:outlay:050-0001-q3",
    },
    FlowEdge {
        edge_id: "imf-2024-sdr-alloc-jpn",
        flow_class: "intergovernmental-transfer",
        from_label: "IMF/SDR-department",
        to_label: "JPN/sovereign-account",
        amount: "4200000000",
        currency: "XDR",
        period: "2024",
        jurisdiction: "IMF",
        source_record: "gov.dataset.statisticsObservation:imf-sdmx:sdr-alloc:2024:jpn",
    },
];

struct KanaeFiscalIngest;

impl Guest for KanaeFiscalIngest {
    fn run(ctx_cbor: Vec<u8>) -> Result<Vec<u8>, String> {
        let did = auth::current_did();
        let mut edge_count = 0usize;

        // ── 1+2. Ingest the fiscal slice → kotoba EAVT fundFlowEdge datoms ──
        // Each edge is asserted as a set of datoms keyed by the edge id, with
        // predicates mirroring the com.etzhayyim.kanae.fundFlowEdge lexicon.
        let mut flow_summary = String::new();
        for e in SAMPLE_FLOWS {
            assert_str(GRAPH, e.edge_id, "kanae/fundFlowEdge/flowClass", e.flow_class)?;
            assert_str(GRAPH, e.edge_id, "kanae/fundFlowEdge/fromEndpoint", e.from_label)?;
            assert_str(GRAPH, e.edge_id, "kanae/fundFlowEdge/toEndpoint", e.to_label)?;
            assert_str(GRAPH, e.edge_id, "kanae/fundFlowEdge/amount", e.amount)?;
            assert_str(GRAPH, e.edge_id, "kanae/fundFlowEdge/currency", e.currency)?;
            assert_str(GRAPH, e.edge_id, "kanae/fundFlowEdge/period", e.period)?;
            assert_str(GRAPH, e.edge_id, "kanae/fundFlowEdge/jurisdiction", e.jurisdiction)?;
            // G5 provenance: source record id on every edge.
            assert_str(GRAPH, e.edge_id, "kanae/fundFlowEdge/sourceRecord", e.source_record)?;
            edge_count += 1;
            flow_summary.push_str(&format!(
                "- {flow}: {from} -> {to}, {amt} {cur} ({period}, {juris})\n",
                flow = e.flow_class,
                from = e.from_label,
                to = e.to_label,
                amt = e.amount,
                cur = e.currency,
                period = e.period,
                juris = e.jurisdiction,
            ));
        }

        // ── 3. Murakumo narrative (gemma-4-26B-A4B-it), G4 non-adjudicating ──
        // The prompt structurally forbids legal-conclusion language.
        let prompt = format!(
            "You are kanae, a non-adjudicating fiscal-flow describer. Describe the \
             following government fund-flow records FACTUALLY: what flowed, from which \
             authority/jurisdiction, to which recipient class/jurisdiction, in what \
             amount and period. Do NOT assert that any crime, violation, fraud, or \
             wrongdoing occurred — describe magnitudes and routes only.\n\n{flow_summary}"
        );

        let narrative_id = "kanae-narrative-verify-0001";
        let (narrative_text, inference_status) =
            match llm::infer(MODEL_TAG, prompt.as_bytes()) {
                Ok(bytes) => (
                    String::from_utf8_lossy(&bytes).to_string(),
                    "murakumo-inferred",
                ),
                // Robust: ingest + persistence still complete if the Murakumo
                // node serving the 26B model is unreachable. The narrative is
                // marked pending so the EAVT record is honest about it.
                Err(e) => (
                    format!("[pending-inference] Murakumo llm.infer unavailable: {e}"),
                    "pending-inference",
                ),
            };

        // ── 4. Persist flowNarrative datom → kotoba EAVT (G4 + G7 markers) ──
        assert_str(GRAPH, narrative_id, "kanae/flowNarrative/narrative", &narrative_text)?;
        assert_str(GRAPH, narrative_id, "kanae/flowNarrative/nonAdjudicatingNotice", "true")?;
        assert_str(GRAPH, narrative_id, "kanae/flowNarrative/inferenceStatus", inference_status)?;
        // G7 murakumo attestation (inferenceSubstrate pinned to murakumo).
        assert_str(GRAPH, narrative_id, "kanae/flowNarrative/murakumo/inferenceSubstrate", "murakumo")?;
        assert_str(GRAPH, narrative_id, "kanae/flowNarrative/murakumo/model", MODEL_TAG)?;
        // Link the narrative to every edge it describes (G5).
        for e in SAMPLE_FLOWS {
            assert_str(GRAPH, narrative_id, "kanae/flowNarrative/coversEdge", e.edge_id)?;
        }

        // ── Provenance: append an Infer ChainEntry when inference ran ───────
        if inference_status == "murakumo-inferred" {
            let _ = chain::append_infer(MODEL_TAG, "prompt:kanae-verify-0001", narrative_id);
        }

        let output = format!(
            "kanae-fiscal-ingest ok | did={did} | ctx_len={ctx} | edges_asserted={edges} \
             | narrative={nid} | inference={status} | model={model} | graph={graph}",
            ctx = ctx_cbor.len(),
            edges = edge_count,
            nid = narrative_id,
            status = inference_status,
            model = MODEL_TAG,
            graph = GRAPH,
        );
        Ok(output.into_bytes())
    }
}

/// Assert a single (graph, subject, predicate, text-value) datom into kotoba
/// EAVT. Value bytes are raw UTF-8 (host wraps as KqeValue::Bytes), matching
/// the examples/kotoba-hello convention.
fn assert_str(graph: &str, subject: &str, predicate: &str, value: &str) -> Result<(), String> {
    kqe::assert_quad(&kqe::Quad {
        graph: graph.into(),
        subject: subject.into(),
        predicate: predicate.into(),
        object_cbor: value.as_bytes().to_vec(),
    })
    .map_err(|e| format!("assert {predicate} failed: {e}"))
}

export!(KanaeFiscalIngest);

//! tsumugi 紡ぎ core — edge-karma aggregation as a WASM actor.
//!
//! Per ADR-2606014500. This is the *executable* face of the tsumugi actor: a
//! content-addressed `wasm32-unknown-unknown` module published to IPFS and run
//! locally by the browser (ameno) or a donated mesh node — there is NO per-actor
//! server. State (the full power-graph) lives in the kotoba Datom log; this PoC
//! embeds a bounded `:representative` seed so the module is self-contained and
//! verifiable offline.
//!
//! Constitutional alignment with ADR-2606011800:
//!   - G1 power-only: every node is a public power-entity (法人/institution).
//!   - G2 edge-primary karma: a node's score is Σ of the grasping-load of its
//!     INCIDENT 縁 — an aggregate, NEVER a per-soul score (N1). Powerless absent
//!     by construction. This is an accountability map, not a target-list.
//!
//! ABI (host-agnostic; identical in Node and the browser):
//!   compute()    -> i32   run aggregation, write JSON into linear memory,
//!                         return its byte length
//!   result_ptr() -> i32   pointer to the JSON bytes in linear memory
//! The host reads `memory[result_ptr .. result_ptr + compute()]` as UTF-8 JSON.

// ── bounded :representative seed (power-graph) ───────────────────────────────
// Mirrors the tsumugi finding that TSMC carries the highest 取-concentration.
// Real deployments read the full graph from the kotoba `actors-v1`/engi graph.
const ORGS: &[&str] = &[
    "TSMC", "Toyota", "Arm", "NVIDIA", "Samsung", // 0..4
    "Apple", "ASML", "Sony", "Intel", "Qualcomm", // 5..9
];

// (from, to, grasping_load_milli) — directed 縁; karma is incident (undirected
// incidence sum), per G2.
const EDGES: &[(usize, usize, u32)] = &[
    (5, 0, 900), // Apple   → TSMC
    (3, 0, 850), // NVIDIA  → TSMC
    (9, 0, 700), // Qualcomm→ TSMC
    (0, 6, 600), // TSMC    → ASML
    (0, 2, 300), // TSMC    → Arm
    (3, 2, 500), // NVIDIA  → Arm
    (5, 2, 550), // Apple   → Arm
    (9, 2, 500), // Qualcomm→ Arm
    (1, 7, 400), // Toyota  → Sony
    (1, 4, 500), // Toyota  → Samsung
    (5, 4, 450), // Apple   → Samsung
    (3, 4, 300), // NVIDIA  → Samsung
    (1, 0, 400), // Toyota  → TSMC
    (8, 6, 500), // Intel   → ASML
    (7, 0, 350), // Sony    → TSMC
];

fn run() -> String {
    // edge-primary incident karma per node (Σ grasping-load over incident 縁).
    let mut karma = [0u32; 10];
    for &(f, t, gl) in EDGES {
        karma[f] += gl;
        karma[t] += gl;
    }
    // rank desc by karma (stable by index on ties).
    let mut order: Vec<usize> = (0..ORGS.len()).collect();
    order.sort_by(|&a, &b| karma[b].cmp(&karma[a]).then(a.cmp(&b)));

    let mut top = String::new();
    for (rank, &i) in order.iter().take(5).enumerate() {
        if rank > 0 {
            top.push(',');
        }
        // karma is milli-units → render as a decimal with 3 places.
        top.push_str(&format!(
            "{{\"rank\":{},\"id\":{},\"label\":\"{}\",\"karma\":{}.{:03}}}",
            rank + 1,
            i,
            ORGS[i],
            karma[i] / 1000,
            karma[i] % 1000
        ));
    }
    format!(
        "{{\"actor\":\"tsumugi\",\"metric\":\"edge-primary-incident-karma\",\
         \"sourcing\":\"representative\",\"nodes\":{},\"edges\":{},\"top\":[{}]}}",
        ORGS.len(),
        EDGES.len(),
        top
    )
}

// ── ABI ──────────────────────────────────────────────────────────────────────
static mut RESULT: Vec<u8> = Vec::new();

/// Run the aggregation; return the byte length of the JSON result.
#[no_mangle]
pub extern "C" fn compute() -> i32 {
    let bytes = run().into_bytes();
    let len = bytes.len() as i32;
    unsafe {
        RESULT = bytes;
    }
    len
}

/// Pointer to the JSON result bytes in linear memory (valid after compute()).
#[no_mangle]
pub extern "C" fn result_ptr() -> i32 {
    unsafe { RESULT.as_ptr() as i32 }
}

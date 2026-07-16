//! kanae 鼎 core — government fiscal-flow aggregation as a WASM actor.
//!
//! Per ADR-2606015200. The compact (raw-CID, T1 browser-local) executable face of
//! the kanae actor (fiscal-flow VISUALIZATION, ADR-2605302300): given public
//! `fundFlowEdge`s (appropriation → outlay → recipient, and inter-governmental
//! transfers), it aggregates **inflow per recipient** — a NON-adjudicating,
//! aggregate-first summary (kanae renders, danjo finds; no per-entity verdict).
//! Embeds a bounded `:representative` seed so the module is self-contained.
//!
//! Same ABI as tsumugi-core: `compute() -> i32` (writes JSON, returns len) +
//! `result_ptr() -> i32`.

const NODES: &[&str] = &[
    "Treasury", "MoD", "MHLW", "MEXT", "MLIT", // 0..4 organs
    "IMF", "WorldBank", "UN", "Prefectures", "Contractors", // 5..9 recipients/bodies
];

// (from, to, amount_oku_jpy) — public fund-flow edges (representative).
const EDGES: &[(usize, usize, u32)] = &[
    (0, 1, 5400), // Treasury → MoD
    (0, 2, 33800), // Treasury → MHLW
    (0, 3, 5300), // Treasury → MEXT
    (0, 4, 6100), // Treasury → MLIT
    (0, 8, 15800), // Treasury → Prefectures (local allocation tax)
    (1, 9, 4200), // MoD → Contractors
    (2, 8, 21000), // MHLW → Prefectures (social security transfers)
    (2, 9, 3100), // MHLW → Contractors
    (3, 8, 2600), // MEXT → Prefectures
    (4, 9, 5200), // MLIT → Contractors (public works)
    (0, 5, 900), // Treasury → IMF (quota/contribution)
    (0, 6, 600), // Treasury → WorldBank
    (0, 7, 700), // Treasury → UN
    (8, 9, 9800), // Prefectures → Contractors
];

fn run() -> String {
    // inflow per node = Σ amount of edges arriving at it (aggregate-first, N-non-adjudicating).
    let mut inflow = [0u32; 10];
    for &(_f, t, amt) in EDGES {
        inflow[t] += amt;
    }
    let mut order: Vec<usize> = (0..NODES.len()).collect();
    order.sort_by(|&a, &b| inflow[b].cmp(&inflow[a]).then(a.cmp(&b)));

    let mut top = String::new();
    let mut rank = 0;
    for &i in order.iter() {
        if inflow[i] == 0 {
            continue;
        }
        if rank > 0 {
            top.push(',');
        }
        rank += 1;
        top.push_str(&format!(
            "{{\"rank\":{},\"id\":{},\"node\":\"{}\",\"inflow_oku_jpy\":{}}}",
            rank, i, NODES[i], inflow[i]
        ));
        if rank >= 5 {
            break;
        }
    }
    let total: u32 = EDGES.iter().map(|e| e.2).sum();
    format!(
        "{{\"actor\":\"kanae\",\"metric\":\"recipient-inflow-oku-jpy\",\
         \"sourcing\":\"representative\",\"nodes\":{},\"edges\":{},\"total_oku_jpy\":{},\
         \"adjudication\":\"none\",\"top\":[{}]}}",
        NODES.len(),
        EDGES.len(),
        total,
        top
    )
}

static mut RESULT: Vec<u8> = Vec::new();

#[no_mangle]
pub extern "C" fn compute() -> i32 {
    let bytes = run().into_bytes();
    let len = bytes.len() as i32;
    unsafe {
        RESULT = bytes;
    }
    len
}

#[no_mangle]
pub extern "C" fn result_ptr() -> i32 {
    unsafe { RESULT.as_ptr() as i32 }
}

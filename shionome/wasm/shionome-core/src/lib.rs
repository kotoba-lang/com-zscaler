//! shionome 潮目 core — cross-asset capital-flow observation as a WASM actor.
//!
//! Per ADR-2606015200 (T1 raw-CID browser-local pattern; same ABI as tsumugi-core /
//! kanae-core: `compute() -> i32` writes JSON + returns len, `result_ptr() -> i32`).
//! The compact executable face of shionome (ADR-2606072200): embeds the bounded
//! `:representative` seed and computes the actor's two read-side views —
//!   1. the FACTUAL cross-asset regime (risk-on/off/mixed) from net capital flows, and
//!   2. the STOCK PYRAMID (`:outstanding-usd`) — the money-and-markets sizing of each
//!      asset class against the grand total (the Visual-Capitalist "how big is everything").
//!
//! THE DEFINING INVARIANT — トレードはしない (G2): every output is an observation
//! carrying `no_trade:true`; a buy/sell signal, price target, or per-asset rating is
//! unrepresentable here exactly as in the Python methods (weave.py).

// capital buckets (G1 public categories, never persons/accounts): (id, risk_tag)
// risk_tag: 1 = :risk, -1 = :safe, 0 = :neutral
const BUCKETS: &[(&str, i8)] = &[
    ("us-equities", 1),   // 0
    ("jp-equities", 1),   // 1
    ("em-equities", 1),   // 2
    ("us-govt-bonds", -1),// 3
    ("cash-usd", -1),     // 4
    ("gold", -1),         // 5
    ("crypto-btc", 1),    // 6
    ("theme-ai", 1),      // 7
];

// observed CAPITAL-MOVEMENT flows (rotation / fund-in / fund-out), usd-bn ×10
// (from, to, magnitude_dbn); usize::MAX = external. Mirrors the committed seed.
const EXT: usize = usize::MAX;
const FLOWS: &[(usize, usize, u32)] = &[
    (3, 0, 124), // Treasuries → US equities (rotation 12.4bn)
    (4, 0, 81),  // cash → US equities 8.1
    (EXT, 6, 56),// inflow → crypto 5.6
    (0, 7, 69),  // US equities → AI theme 6.9
    (3, EXT, 93),// Treasuries outflow 9.3
    (0, 5, 32),  // US equities → gold (hedge) 3.2
    (EXT, 1, 44),// inflow → JP equities 4.4
    (2, 0, 27),  // EM → US equities 2.7
];

// STOCK PYRAMID layers — :outstanding-usd, usd-tn ×10 (`:representative` rounded
// public figures; derivatives = OTC GROSS NOTIONAL, deliberately the largest layer).
const STOCK: &[(&str, u32)] = &[
    ("derivatives", 6000),
    ("real-estate", 3800),
    ("debt", 1400),
    ("broad-money", 1210),
    ("equities", 1150),
    ("gold", 160),
    ("cash", 80),
    ("crypto", 30),
];

fn run() -> String {
    // net flow per bucket (inflow − outflow), then regime from risk vs safe lean.
    let mut net = [0i64; 8];
    for &(f, t, m) in FLOWS {
        if t != EXT {
            net[t] += m as i64;
        }
        if f != EXT {
            net[f] -= m as i64;
        }
    }
    let (mut risk_net, mut safe_net) = (0i64, 0i64);
    for (i, &(_, tag)) in BUCKETS.iter().enumerate() {
        match tag {
            1 => risk_net += net[i],
            -1 => safe_net += net[i],
            _ => {}
        }
    }
    let regime = if risk_net == 0 && safe_net == 0 {
        "indeterminate"
    } else if risk_net > 0 && safe_net <= 0 {
        "risk-on"
    } else if risk_net < 0 && safe_net >= 0 {
        "risk-off"
    } else {
        "mixed"
    };

    // stock pyramid: share of grand total per layer (integer permille — deterministic).
    let grand: u64 = STOCK.iter().map(|s| s.1 as u64).sum();
    let mut layers = String::new();
    for (i, &(name, dtn)) in STOCK.iter().enumerate() {
        if i > 0 {
            layers.push(',');
        }
        let permille = (dtn as u64 * 1000) / grand;
        layers.push_str(&format!(
            "{{\"asset_class\":\"{}\",\"usd_tn\":{}.{},\"share_permille\":{}}}",
            name,
            dtn / 10,
            dtn % 10,
            permille
        ));
    }

    format!(
        "{{\"actor\":\"shionome\",\"sourcing\":\"representative\",\
         \"regime\":\"{}\",\"risk_net_dbn\":{},\"safe_net_dbn\":{},\
         \"stock_pyramid\":{{\"grand_total_usd_tn\":{}.{},\"unit\":\"usd-tn\",\"layers\":[{}]}},\
         \"no_trade\":true,\"adjudication\":\"none\"}}",
        regime,
        risk_net,
        safe_net,
        grand / 10,
        grand % 10,
        layers
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

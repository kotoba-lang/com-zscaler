//! maps 地図 core — onsen (温泉) discovery + おすすめ ranking as a WASM actor.
//!
//! Per ADR-2606015200 (T1 raw-CID browser-local pattern; same ABI as
//! shionome-core / tsumugi-core / kanae-core: `compute() -> i32` writes JSON +
//! returns len, `result_ptr() -> i32`). The compact executable face of the maps
//! onsen recommender (20-actors/maps/methods/onsen.cljc, ADR-2606064500): embeds
//! a bounded `:representative` seed of famous onsen and computes the TRANSPARENT
//! place-quality ranking the `recommend` read-path serves.
//!
//! THE DEFINING INVARIANT — a feature is a PLACED THING, never a person (G9):
//! every signal is a public PLACE-fact (spring authenticity / notability /
//! amenities); a per-person affect/profile/engagement metric is unrepresentable
//! here exactly as in the cljc methods. Every output carries `place_not_person:true`.
//! The score is fully recomputable from the embedded tags; `why` makes it auditable.

// A bounded :representative seed of famous onsen (NOT full coverage — absence ≠
// "no onsen"; G3). Fields mirror onsen.cljc score-onsen signals:
//   (id, name, lat_e4, lon_e4, authentic, notable, named, open_air, sauna, amenity_info 0..4)
// authentic = natural=hot_spring | onsen=yes | bath:type=onsen   notable = wikidata|wikipedia
struct Onsen {
    id: &'static str,
    name: &'static str,
    lat_e4: i32,
    lon_e4: i32,
    authentic: bool,
    notable: bool,
    named: bool,
    open_air: bool,
    sauna: bool,
    amenity_info: u32,
}

const SEED: &[Onsen] = &[
    Onsen { id: "kusatsu",     name: "草津温泉",   lat_e4: 366225, lon_e4: 1385966, authentic: true, notable: true, named: true, open_air: true,  sauna: false, amenity_info: 2 },
    Onsen { id: "beppu",       name: "別府温泉",   lat_e4: 332790, lon_e4: 1315006, authentic: true, notable: true, named: true, open_air: true,  sauna: false, amenity_info: 1 },
    Onsen { id: "hakone",      name: "箱根温泉",   lat_e4: 352329, lon_e4: 1391069, authentic: true, notable: true, named: true, open_air: true,  sauna: true,  amenity_info: 2 },
    Onsen { id: "noboribetsu", name: "登別温泉",   lat_e4: 424150, lon_e4: 1410850, authentic: true, notable: true, named: true, open_air: true,  sauna: false, amenity_info: 1 },
    Onsen { id: "arima",       name: "有馬温泉",   lat_e4: 347986, lon_e4: 1352486, authentic: true, notable: true, named: true, open_air: false, sauna: false, amenity_info: 1 },
    Onsen { id: "dogo",        name: "道後温泉",   lat_e4: 338510, lon_e4: 1327870, authentic: true, notable: true, named: true, open_air: false, sauna: false, amenity_info: 2 },
    Onsen { id: "yufuin",      name: "由布院温泉", lat_e4: 332620, lon_e4: 1313560, authentic: true, notable: true, named: true, open_air: true,  sauna: false, amenity_info: 1 },
    Onsen { id: "gero",        name: "下呂温泉",   lat_e4: 358050, lon_e4: 1372436, authentic: true, notable: false, named: true, open_air: false, sauna: false, amenity_info: 0 },
];

// transparent place-quality score ×10 (integer-deterministic mirror of score-onsen):
//   authentic +3.0 · notable +2.0 · named +1.0 · open-air +1.0 · sauna +0.5 · amenity_info ×0.5
fn score_x10(o: &Onsen) -> i32 {
    let mut s = 0i32;
    if o.authentic { s += 30; }
    if o.notable   { s += 20; }
    if o.named     { s += 10; }
    if o.open_air  { s += 10; }
    if o.sauna     { s += 5; }
    s += 5 * o.amenity_info as i32;
    s
}

fn why(o: &Onsen) -> String {
    let mut w: Vec<&str> = Vec::new();
    if o.authentic { w.push("authentic-hot-spring"); }
    if o.notable   { w.push("notable"); }
    if o.named     { w.push("named"); }
    if o.open_air  { w.push("open-air-rotenburo"); }
    if o.sauna     { w.push("has-sauna"); }
    if o.amenity_info > 0 { w.push("amenity-info"); }
    w.join("+")
}

fn run() -> String {
    // rank by score desc, stable tiebreak by id (deterministic — no Math.random / clock)
    let mut idx: Vec<usize> = (0..SEED.len()).collect();
    idx.sort_by(|&a, &b| {
        score_x10(&SEED[b])
            .cmp(&score_x10(&SEED[a]))
            .then(SEED[a].id.cmp(SEED[b].id))
    });

    let mut items = String::new();
    for (rank, &i) in idx.iter().enumerate() {
        let o = &SEED[i];
        let sc = score_x10(o);
        if rank > 0 {
            items.push(',');
        }
        items.push_str(&format!(
            "{{\"rank\":{},\"id\":\"{}\",\"name\":\"{}\",\"lat\":{}.{:04},\"lon\":{}.{:04},\
             \"score\":{}.{},\"why\":\"{}\"}}",
            rank + 1,
            o.id,
            o.name,
            o.lat_e4 / 10000,
            (o.lat_e4 % 10000).abs(),
            o.lon_e4 / 10000,
            (o.lon_e4 % 10000).abs(),
            sc / 10,
            sc % 10,
            why(o)
        ));
    }

    format!(
        "{{\"actor\":\"maps\",\"view\":\"onsen-osusume\",\"sourcing\":\"representative\",\
         \"count\":{},\"osusume\":[{}],\
         \"place_not_person\":true,\"adjudication\":\"none\"}}",
        SEED.len(),
        items
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

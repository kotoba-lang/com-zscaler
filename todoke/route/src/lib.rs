//! todoke 届け — last-mile ("one-mile") route sequencing + SAE-L4 sidewalk ODD safety envelope.
//!
//! This is the **Rust "onemile" core** of the todoke Tier-B actor (ADR-2606042300). It is
//! deliberately self-contained (zero dependencies, no workspace membership, deterministic)
//! so it compiles and tests in isolation with `cargo test`. The full autonomous run
//! delegates perception/planning/control to `kami-autodrive` (ADR-2606010600); this crate
//! owns only the two pieces that are *specific to the last mile*:
//!
//!   1. **stop sequencing** — order a small set of curb/door stops to minimise travel
//!      (nearest-neighbour seed + 2-opt local search, both O(n^2) and stable).
//!   2. **safety envelope** — a constitutional gate (G7) that REFUSES any plan that would
//!      exceed the sidewalk-speed cap, enter a disallowed zone (e.g. a vehicular road,
//!      N2), or assume an SAE autonomy level above 4 (the wadachi/todoke ceiling, N2).
//!
//! The envelope is enforced *by construction*: `plan_last_mile` returns `Err` rather than a
//! route whenever an invariant is violated, so no caller can obtain a route that breaks the
//! charter. A pure-Python mirror of the sequencer lives in `methods/last_mile.py` — one
//! model, two runtimes (the sumitsubo pattern, ADR-2606033600).

#![forbid(unsafe_code)]

pub mod sim;

/// SAE J3016 autonomy ceiling for todoke/wadachi ground autonomy. Level 5 is a non-goal (N2).
pub const SAE_LEVEL_CEILING: u8 = 4;

/// The kind of right-of-way a stop or leg traverses. Roads are disallowed at R0 (N2).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Zone {
    /// Pedestrian sidewalk — the default last-mile surface.
    Sidewalk,
    /// Shared bike lane — permitted at a higher (but still bounded) speed cap.
    BikeLane,
    /// Marked pedestrian crossing.
    Crosswalk,
    /// The private path from curb to the recipient's door.
    DoorPath,
    /// A vehicular road. Disallowed for todoke at R0 (that is wadachi's R2+ ODD, N2).
    Road,
}

impl Zone {
    /// Per-zone speed cap in metres/second. `Road` returns `None` (not in the todoke ODD).
    ///
    /// Sidewalk ≈ brisk-walk (1.8 m/s ≈ 6.5 km/h); bike-lane ≈ 4.2 m/s ≈ 15 km/h.
    pub fn speed_cap_mps(self) -> Option<f64> {
        match self {
            Zone::Sidewalk => Some(1.8),
            Zone::Crosswalk => Some(1.4),
            Zone::DoorPath => Some(1.0),
            Zone::BikeLane => Some(4.2),
            Zone::Road => None,
        }
    }
}

/// A single last-mile stop (a curb hand-off point or a recipient door), in a local
/// metric frame (metres, east/north).
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Stop {
    pub id: u32,
    pub x: f64,
    pub y: f64,
    pub zone: Zone,
}

impl Stop {
    pub fn new(id: u32, x: f64, y: f64, zone: Zone) -> Self {
        Stop { id, x, y, zone }
    }
    fn dist(&self, other: &Stop) -> f64 {
        let dx = self.x - other.x;
        let dy = self.y - other.y;
        (dx * dx + dy * dy).sqrt()
    }
}

/// The safety envelope the planned run must satisfy (G7, the constitutional gate).
#[derive(Clone, Copy, Debug)]
pub struct SafetyEnvelope {
    /// The autonomy level the run will operate at. Must be `<= SAE_LEVEL_CEILING`.
    pub sae_level: u8,
    /// The platform's commanded cruise speed (m/s). Checked against every leg's zone cap.
    pub commanded_speed_mps: f64,
}

impl Default for SafetyEnvelope {
    fn default() -> Self {
        // The safe default: brisk-walk pace, SAE L4.
        SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.5 }
    }
}

/// A constitutional refusal — the envelope or ODD was violated. Carrying the reason makes
/// the refusal auditable (it becomes a `safetyAlert` Datom).
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum EnvelopeViolation {
    /// `sae_level` exceeds the SAE L4 ceiling (N2).
    SaeLevelTooHigh { level: u8 },
    /// A stop sits in a zone outside the todoke ODD (e.g. a vehicular road, N2).
    ZoneOutsideOdd { stop: u32, zone: Zone },
    /// The commanded speed exceeds a leg's per-zone cap.
    SpeedExceedsZoneCap { stop: u32, commanded: u32 /* mm/s */, cap: u32 /* mm/s */ },
    /// Nothing to route.
    NoStops,
}

/// A validated last-mile route: the visiting order plus its total path length.
#[derive(Clone, Debug, PartialEq)]
pub struct Route {
    /// Stop ids in visiting order, starting from the depot/curb (the first input stop).
    pub order: Vec<u32>,
    /// Total euclidean path length in metres.
    pub length_m: f64,
}

/// Plan + validate a last-mile route over `stops`, refusing any plan that breaks `env` (G7).
///
/// The first element of `stops` is treated as the start (depot / drop curb). The route does
/// not return to the depot (last-mile delivery is open-path, not a closed tour).
pub fn plan_last_mile(stops: &[Stop], env: &SafetyEnvelope) -> Result<Route, EnvelopeViolation> {
    if stops.is_empty() {
        return Err(EnvelopeViolation::NoStops);
    }
    // --- Envelope gate, enforced BEFORE any route is returned (G7). ---
    if env.sae_level > SAE_LEVEL_CEILING {
        return Err(EnvelopeViolation::SaeLevelTooHigh { level: env.sae_level });
    }
    for s in stops {
        match s.zone.speed_cap_mps() {
            None => return Err(EnvelopeViolation::ZoneOutsideOdd { stop: s.id, zone: s.zone }),
            Some(cap) if env.commanded_speed_mps > cap => {
                return Err(EnvelopeViolation::SpeedExceedsZoneCap {
                    stop: s.id,
                    commanded: (env.commanded_speed_mps * 1000.0).round() as u32,
                    cap: (cap * 1000.0).round() as u32,
                });
            }
            Some(_) => {}
        }
    }

    let seq = two_opt(&nearest_neighbour(stops), stops);
    let length_m = path_length(&seq, stops);
    Ok(Route { order: seq.iter().map(|&i| stops[i].id).collect(), length_m })
}

/// Nearest-neighbour seed tour over stop indices, starting at index 0 (the depot).
fn nearest_neighbour(stops: &[Stop]) -> Vec<usize> {
    let n = stops.len();
    let mut visited = vec![false; n];
    let mut tour = Vec::with_capacity(n);
    let mut cur = 0usize;
    visited[0] = true;
    tour.push(0);
    for _ in 1..n {
        let mut best: Option<usize> = None;
        let mut best_d = f64::INFINITY;
        for (j, s) in stops.iter().enumerate() {
            if !visited[j] {
                let d = stops[cur].dist(s);
                // tie-break on index for determinism
                if d < best_d - 1e-12 || (d <= best_d + 1e-12 && best.is_none_or(|b| j < b)) {
                    best_d = d;
                    best = Some(j);
                }
            }
        }
        let nxt = best.expect("unvisited stop exists");
        visited[nxt] = true;
        tour.push(nxt);
        cur = nxt;
    }
    tour
}

/// 2-opt local search improving an open path (depot fixed at position 0).
fn two_opt(seed: &[usize], stops: &[Stop]) -> Vec<usize> {
    let mut tour = seed.to_vec();
    let n = tour.len();
    if n < 4 {
        return tour;
    }
    let mut improved = true;
    while improved {
        improved = false;
        // i starts at 1 to keep the depot (position 0) pinned.
        for i in 1..n - 1 {
            for k in i + 1..n {
                let a = stops[tour[i - 1]];
                let b = stops[tour[i]];
                let c = stops[tour[k]];
                let d_next = if k + 1 < n { Some(stops[tour[k + 1]]) } else { None };
                let before = a.dist(&b) + d_next.map_or(0.0, |d| c.dist(&d));
                let after = a.dist(&c) + d_next.map_or(0.0, |d| b.dist(&d));
                if after + 1e-9 < before {
                    tour[i..=k].reverse();
                    improved = true;
                }
            }
        }
    }
    tour
}

fn path_length(order: &[usize], stops: &[Stop]) -> f64 {
    order
        .windows(2)
        .map(|w| stops[w[0]].dist(&stops[w[1]]))
        .sum()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn grid_stops() -> Vec<Stop> {
        // depot at origin, then a deliberately scrambled set of door stops.
        vec![
            Stop::new(0, 0.0, 0.0, Zone::Sidewalk),
            Stop::new(1, 30.0, 0.0, Zone::DoorPath),
            Stop::new(2, 10.0, 0.0, Zone::Sidewalk),
            Stop::new(3, 20.0, 0.0, Zone::DoorPath),
            Stop::new(4, 5.0, 0.0, Zone::Crosswalk),
        ]
    }

    #[test]
    fn sequences_collinear_stops_in_order() {
        let stops = grid_stops();
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.0 };
        let route = plan_last_mile(&stops, &env).unwrap();
        // optimal visiting order along a line from the depot is by ascending x.
        assert_eq!(route.order, vec![0, 4, 2, 3, 1]);
        // total length is just the span (0 -> 30) since all are collinear.
        assert!((route.length_m - 30.0).abs() < 1e-6, "len={}", route.length_m);
    }

    #[test]
    fn two_opt_beats_naive_nn_on_a_loop() {
        // a square: NN can take a crossing path; 2-opt must remove the crossing.
        let stops = vec![
            Stop::new(0, 0.0, 0.0, Zone::Sidewalk),
            Stop::new(1, 0.0, 10.0, Zone::Sidewalk),
            Stop::new(2, 10.0, 10.0, Zone::Sidewalk),
            Stop::new(3, 10.0, 0.0, Zone::Sidewalk),
        ];
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.5 };
        let route = plan_last_mile(&stops, &env).unwrap();
        // open path over the square perimeter (no return) = 30 m, never the crossing 10+~14+10.
        assert!(route.length_m <= 30.0 + 1e-6, "len={}", route.length_m);
    }

    #[test]
    fn g7_refuses_sae_level_5() {
        let stops = grid_stops();
        let env = SafetyEnvelope { sae_level: 5, commanded_speed_mps: 1.0 };
        assert_eq!(
            plan_last_mile(&stops, &env),
            Err(EnvelopeViolation::SaeLevelTooHigh { level: 5 })
        );
    }

    #[test]
    fn g7_refuses_a_road_stop_outside_the_odd() {
        let mut stops = grid_stops();
        stops.push(Stop::new(9, 40.0, 0.0, Zone::Road)); // N2: vehicular road not in ODD
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.0 };
        assert_eq!(
            plan_last_mile(&stops, &env),
            Err(EnvelopeViolation::ZoneOutsideOdd { stop: 9, zone: Zone::Road })
        );
    }

    #[test]
    fn g7_refuses_speed_above_sidewalk_cap() {
        let stops = grid_stops();
        // 3.0 m/s (~11 km/h) exceeds the 1.8 m/s sidewalk cap.
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 3.0 };
        match plan_last_mile(&stops, &env) {
            Err(EnvelopeViolation::SpeedExceedsZoneCap { cap, commanded, .. }) => {
                assert_eq!(cap, 1800);
                assert_eq!(commanded, 3000);
            }
            other => panic!("expected speed refusal, got {other:?}"),
        }
    }

    #[test]
    fn bike_lane_permits_higher_speed() {
        let stops = vec![
            Stop::new(0, 0.0, 0.0, Zone::BikeLane),
            Stop::new(1, 10.0, 0.0, Zone::BikeLane),
        ];
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 4.0 };
        assert!(plan_last_mile(&stops, &env).is_ok());
    }

    #[test]
    fn empty_is_refused() {
        assert_eq!(
            plan_last_mile(&[], &SafetyEnvelope::default()),
            Err(EnvelopeViolation::NoStops)
        );
    }
}

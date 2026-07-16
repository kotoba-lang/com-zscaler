//! todoke R1 — curb-to-door rover simulation (kinematic stand-in for kami-autodrive).
//!
//! R1 drives a single delivery rover along the safety-validated route produced by
//! [`crate::plan_last_mile`], leg by leg, from the drop curb to the recipient's door. It is a
//! **longitudinal kinematic stand-in** for the real GNC: full perception → planning → control
//! lives in the `kami-autodrive` crate (ADR-2606010600, `VehicleClass::Car` / sidewalk variant),
//! which is a member of the `40-engine/kami-engine` submodule. When that submodule is populated
//! this module's `drive_leg` integrator is replaced by a `kami-autodrive` `Autopilot` step; the
//! public `simulate` surface (route in → `MissionOutcome` out) stays the same.
//!
//! What R1 *does* faithfully model, self-contained and tested:
//!   * the route core is the SAME `plan_last_mile` (sequence + G7 envelope refusal);
//!   * per-leg speed is capped at `min(commanded, zone cap of the destination stop)` (G7);
//!   * accelerate/cruise/brake-to-stop at each waypoint (trapezoidal longitudinal profile);
//!   * obstacle → emergency-stop + hold, resume when the obstacle clears (safety-first: a
//!     never-clearing obstacle means the mission does NOT arrive);
//!   * a rolling-resistance energy model (honest: rolling-only, no regen/aero).

use crate::{plan_last_mile, EnvelopeViolation, SafetyEnvelope, Stop};

const G: f64 = 9.81;

/// Physical parameters of the delivery rover (small sidewalk class).
#[derive(Clone, Copy, Debug)]
pub struct RoverParams {
    pub mass_kg: f64,
    pub rolling_coeff: f64, // Crr
    pub accel_mps2: f64,    // symmetric accel / brake limit
    pub dt_s: f64,          // integration step
}

impl Default for RoverParams {
    fn default() -> Self {
        RoverParams { mass_kg: 40.0, rolling_coeff: 0.02, accel_mps2: 0.5, dt_s: 0.1 }
    }
}

/// A point obstacle on the path, active over a time window. Models a transient blockage
/// (a pedestrian, a parked cart) that the rover must stop for and wait out.
#[derive(Clone, Copy, Debug)]
pub struct Obstacle {
    pub x: f64,
    pub y: f64,
    pub trigger_radius_m: f64,
    pub active_from_s: f64,
    pub active_to_s: f64, // f64::INFINITY for a never-clearing blockage
}

impl Obstacle {
    fn active_at(&self, t: f64) -> bool {
        t >= self.active_from_s && t < self.active_to_s
    }
}

/// One sampled point of the driven trajectory.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct TrajectorySample {
    pub t_s: f64,
    pub x: f64,
    pub y: f64,
    pub speed_mps: f64,
    pub leg: usize, // index of the destination stop in the route order
}

/// The result of a simulated mission.
#[derive(Clone, Debug)]
pub struct MissionOutcome {
    pub arrived: bool,
    pub total_time_s: f64,
    pub total_distance_m: f64,
    pub energy_wh: f64,
    pub max_speed_mps: f64,
    pub emergency_stops: u32,
    pub samples: Vec<TrajectorySample>,
}

/// Drive the route for `stops` under `env`, with `params` and any `obstacles`.
///
/// Returns the same `EnvelopeViolation` as `plan_last_mile` if the job is unsafe (G7) — the
/// rover never moves on a refused route.
pub fn simulate(
    stops: &[Stop],
    env: &SafetyEnvelope,
    params: &RoverParams,
    obstacles: &[Obstacle],
) -> Result<MissionOutcome, EnvelopeViolation> {
    let route = plan_last_mile(stops, env)?;
    // Re-resolve the visiting order back to Stop structs (route.order is by stop id).
    let by_id = |id: u32| -> Stop { *stops.iter().find(|s| s.id == id).expect("id in stops") };
    let ordered: Vec<Stop> = route.order.iter().map(|&id| by_id(id)).collect();

    let f_roll = params.mass_kg * G * params.rolling_coeff; // constant rolling resistance (N)
    let mut t = 0.0;
    let mut energy_j = 0.0;
    let mut total_dist: f64 = 0.0;
    let mut max_speed: f64 = 0.0;
    let mut emergency_stops = 0u32;
    let mut samples = vec![TrajectorySample {
        t_s: 0.0,
        x: ordered[0].x,
        y: ordered[0].y,
        speed_mps: 0.0,
        leg: 0,
    }];

    // safety budget: refuse to loop forever (a never-clearing obstacle ends the mission).
    let max_t = 24.0 * 3600.0;

    let mut arrived = true;
    'legs: for leg in 1..ordered.len() {
        let a = ordered[leg - 1];
        let b = ordered[leg];
        let leg_len = a.dist(&b);
        if leg_len < 1e-9 {
            continue;
        }
        let (ux, uy) = ((b.x - a.x) / leg_len, (b.y - a.y) / leg_len);
        // G7: per-leg cap = min(commanded, destination zone cap). Road would already be refused.
        let zone_cap = b.zone.speed_cap_mps().unwrap_or(0.0);
        let v_target = env.commanded_speed_mps.min(zone_cap);

        let mut s = 0.0; // distance along the leg
        let mut v = 0.0;
        let mut obstacle_engaged: Option<usize> = None;

        while s < leg_len - 1e-6 {
            if t > max_t {
                arrived = false;
                break 'legs;
            }
            let (px, py) = (a.x + ux * s, a.y + uy * s);

            // obstacle check at the current world position + time
            let blocking = obstacles.iter().enumerate().find(|(_, o)| {
                o.active_at(t) && (px - o.x).hypot(py - o.y) <= o.trigger_radius_m
            });

            let a_cmd = if let Some((idx, _)) = blocking {
                if obstacle_engaged != Some(idx) {
                    emergency_stops += 1;
                    obstacle_engaged = Some(idx);
                }
                -params.accel_mps2 // brake hard while blocked
            } else {
                obstacle_engaged = None;
                // brake-to-stop distance check, else accelerate toward target
                let brake_dist = v * v / (2.0 * params.accel_mps2);
                if leg_len - s <= brake_dist {
                    -params.accel_mps2
                } else {
                    params.accel_mps2
                }
            };

            v = (v + a_cmd * params.dt_s).clamp(0.0, v_target);
            let ds = v * params.dt_s;
            s += ds;
            total_dist += ds;
            energy_j += f_roll * ds; // rolling-resistance-only model
            t += params.dt_s;
            max_speed = max_speed.max(v);

            // a never-clearing obstacle right on the path => stalled forever => not arrived
            if v < 1e-9 && blocking.is_some() {
                if let Some((_, o)) = blocking {
                    if o.active_to_s.is_infinite() {
                        arrived = false;
                        samples.push(TrajectorySample { t_s: t, x: px, y: py, speed_mps: v, leg });
                        break 'legs;
                    }
                }
            }

            samples.push(TrajectorySample {
                t_s: t,
                x: a.x + ux * s.min(leg_len),
                y: a.y + uy * s.min(leg_len),
                speed_mps: v,
                leg,
            });
        }
    }

    Ok(MissionOutcome {
        arrived,
        total_time_s: t,
        total_distance_m: total_dist,
        energy_wh: energy_j / 3600.0,
        max_speed_mps: max_speed,
        emergency_stops,
        samples,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{SafetyEnvelope, Stop, Zone};

    fn line_stops() -> Vec<Stop> {
        vec![
            Stop::new(0, 0.0, 0.0, Zone::Sidewalk),
            Stop::new(1, 30.0, 0.0, Zone::DoorPath),
            Stop::new(2, 10.0, 0.0, Zone::Sidewalk),
            Stop::new(3, 20.0, 0.0, Zone::DoorPath),
            Stop::new(4, 5.0, 0.0, Zone::Crosswalk),
        ]
    }

    #[test]
    fn arrives_and_covers_the_route_length() {
        let stops = line_stops();
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.0 };
        let out = simulate(&stops, &env, &RoverParams::default(), &[]).unwrap();
        assert!(out.arrived);
        // route is 30 m; driven distance is within a few percent (accel/brake ramps).
        assert!((out.total_distance_m - 30.0).abs() < 1.0, "dist={}", out.total_distance_m);
        assert!(out.energy_wh > 0.0);
        assert_eq!(out.emergency_stops, 0);
    }

    #[test]
    fn respects_the_commanded_speed_cap() {
        // an all-sidewalk route may be commanded up to the sidewalk cap (1.8) and cruises there.
        let sidewalk = vec![
            Stop::new(0, 0.0, 0.0, Zone::Sidewalk),
            Stop::new(1, 40.0, 0.0, Zone::Sidewalk),
        ];
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.8 };
        let out = simulate(&sidewalk, &env, &RoverParams::default(), &[]).unwrap();
        assert!(out.max_speed_mps <= 1.8 + 1e-9, "max={}", out.max_speed_mps);
        assert!(out.max_speed_mps > 1.0, "should reach cruise above door-pace, max={}", out.max_speed_mps);

        // a route that touches a door path is (per the G7 envelope) commanded <= 1.0; capped there.
        let with_door = line_stops();
        let env2 = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.0 };
        let out2 = simulate(&with_door, &env2, &RoverParams::default(), &[]).unwrap();
        assert!(out2.max_speed_mps <= 1.0 + 1e-9, "door max={}", out2.max_speed_mps);
    }

    #[test]
    fn obstacle_that_clears_causes_one_emergency_stop_then_arrival() {
        let stops = vec![
            Stop::new(0, 0.0, 0.0, Zone::Sidewalk),
            Stop::new(1, 20.0, 0.0, Zone::Sidewalk),
        ];
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.5 };
        // blocks around x=10 for the first 30 s, then clears.
        let obs = [Obstacle {
            x: 10.0,
            y: 0.0,
            trigger_radius_m: 1.5,
            active_from_s: 0.0,
            active_to_s: 30.0,
        }];
        let out = simulate(&stops, &env, &RoverParams::default(), &obs).unwrap();
        assert!(out.arrived, "should resume and arrive after the obstacle clears");
        assert_eq!(out.emergency_stops, 1);
    }

    #[test]
    fn never_clearing_obstacle_means_no_arrival() {
        let stops = vec![
            Stop::new(0, 0.0, 0.0, Zone::Sidewalk),
            Stop::new(1, 20.0, 0.0, Zone::Sidewalk),
        ];
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.5 };
        let obs = [Obstacle {
            x: 10.0,
            y: 0.0,
            trigger_radius_m: 1.5,
            active_from_s: 0.0,
            active_to_s: f64::INFINITY,
        }];
        let out = simulate(&stops, &env, &RoverParams::default(), &obs).unwrap();
        assert!(!out.arrived, "a permanent blockage must NOT report arrival (safety-first)");
        assert!(out.emergency_stops >= 1);
    }

    #[test]
    fn energy_grows_with_distance() {
        let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.5 };
        let short = vec![
            Stop::new(0, 0.0, 0.0, Zone::Sidewalk),
            Stop::new(1, 10.0, 0.0, Zone::Sidewalk),
        ];
        let long = vec![
            Stop::new(0, 0.0, 0.0, Zone::Sidewalk),
            Stop::new(1, 40.0, 0.0, Zone::Sidewalk),
        ];
        let e_short = simulate(&short, &env, &RoverParams::default(), &[]).unwrap().energy_wh;
        let e_long = simulate(&long, &env, &RoverParams::default(), &[]).unwrap().energy_wh;
        assert!(e_long > e_short);
    }

    #[test]
    fn refused_route_never_drives() {
        let stops = line_stops();
        let env = SafetyEnvelope { sae_level: 5, commanded_speed_mps: 1.0 }; // N2
        assert!(simulate(&stops, &env, &RoverParams::default(), &[]).is_err());
    }
}

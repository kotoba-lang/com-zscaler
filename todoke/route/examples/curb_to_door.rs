//! todoke R1 demo — sequence + safety-validate + drive a curb-to-door delivery.
//!
//!   cargo run --example curb_to_door
//!
//! Prints the planned visiting order, then the simulated mission outcome (a kinematic
//! stand-in for the kami-autodrive GNC, ADR-2606010600). An obstacle is placed mid-route to
//! show the emergency-stop-and-resume behaviour.

use todoke_route::sim::{simulate, Obstacle, RoverParams};
use todoke_route::{plan_last_mile, SafetyEnvelope, Stop, Zone};

fn main() {
    // depot/curb -> three doors along a sidewalk, with a crosswalk in between.
    let stops = vec![
        Stop::new(0, 0.0, 0.0, Zone::Sidewalk),  // drop curb (depot)
        Stop::new(1, 30.0, 4.0, Zone::DoorPath), // door C
        Stop::new(2, 10.0, 0.0, Zone::Sidewalk), // sidewalk node
        Stop::new(3, 20.0, 3.0, Zone::DoorPath), // door B
        Stop::new(4, 5.0, 1.0, Zone::Crosswalk), // crossing
    ];
    // the route touches door paths (cap 1.0), so the G7 envelope requires commanded <= 1.0.
    let env = SafetyEnvelope { sae_level: 4, commanded_speed_mps: 1.0 };

    let route = plan_last_mile(&stops, &env).expect("safe route");
    println!("planned visiting order (stop ids): {:?}", route.order);
    println!("route length: {:.2} m", route.length_m);

    let obstacle = Obstacle {
        x: 10.0,
        y: 0.0,
        trigger_radius_m: 1.5,
        active_from_s: 0.0,
        active_to_s: 12.0, // a pedestrian clears after 12 s
    };

    let out = simulate(&stops, &env, &RoverParams::default(), &[obstacle]).expect("drives");
    println!("--- mission outcome (kinematic stand-in for kami-autodrive) ---");
    println!("arrived:          {}", out.arrived);
    println!("total time:       {:.1} s", out.total_time_s);
    println!("total distance:   {:.2} m", out.total_distance_m);
    println!("energy:           {:.3} Wh", out.energy_wh);
    println!("max speed:        {:.2} m/s (cap respected per zone)", out.max_speed_mps);
    println!("emergency stops:  {}", out.emergency_stops);
    println!("trajectory samples: {}", out.samples.len());
}

//! kami-bridge — golden-trace generator for the Python↔kami-genesis parity tests.
//!
//! Emits (to stdout) a JSON trace computed by the REAL kami-genesis Featherstone
//! solver (`Articulation3dConfig` from `kami_articulated::parse_urdf`):
//!
//!   1. `parity_2link` — a 2-link planar arm with the EXACT geometry of the
//!      Python `:representative` `PlanarArm((1.2, 1.0))` the domain actors use
//!      (revolute about +z, links along +x ⇒ pure x–y planar motion, CCW
//!      positive — the same convention as kinematics.py). FK tip positions over
//!      a joint-grid + damped-least-squares position-IK solutions for sample
//!      targets. The Python test asserts its FK/IK agrees with these numbers.
//!
//!   2. `giemon_arm6` — the real 6-DOF giemon arm fixture: DOF count, joint
//!      limits, FK tip positions for sample configurations, and position-IK
//!      results for reachable targets. This is the trace the future full
//!      6-DOF swap (ADR-2606091800 "mechanical swap") validates against.
//!
//! Deterministic: fixed sample grids, no RNG, no time. Regenerate with:
//!
//!   cargo run --manifest-path 20-actors/kuni-umi/robotics/kami-bridge/Cargo.toml \
//!     --release > 20-actors/kuni-umi/robotics/golden/kami_fk_ik_trace.json

use glam::Vec3;
use kami_articulated::JointKind;
use kami_genesis::Articulation3dConfig;
use serde::Serialize;

/// 2-link planar parity URDF — geometry of the Python PlanarArm((1.2, 1.0)).
/// Joints revolute about +z; links extend along +x; tip = (1.0, 0, 0) on link2.
const PARITY_2LINK_URDF: &str = r#"<?xml version="1.0"?>
<robot name="parity_2link">
  <link name="base_link">
    <inertial><mass value="1.0"/><origin xyz="0 0 0"/>
      <inertia ixx="0.01" iyy="0.01" izz="0.01" ixy="0" ixz="0" iyz="0"/></inertial>
  </link>
  <joint name="j1" type="revolute">
    <parent link="base_link"/><child link="link1"/>
    <origin xyz="0 0 0" rpy="0 0 0"/><axis xyz="0 0 1"/>
    <limit lower="-3.1416" upper="3.1416" effort="40" velocity="3"/>
  </joint>
  <link name="link1">
    <inertial><mass value="1.0"/><origin xyz="0.6 0 0"/>
      <inertia ixx="0.01" iyy="0.05" izz="0.05" ixy="0" ixz="0" iyz="0"/></inertial>
  </link>
  <joint name="j2" type="revolute">
    <parent link="link1"/><child link="link2"/>
    <origin xyz="1.2 0 0" rpy="0 0 0"/><axis xyz="0 0 1"/>
    <limit lower="-3.1416" upper="3.1416" effort="40" velocity="3"/>
  </joint>
  <link name="link2">
    <inertial><mass value="0.8"/><origin xyz="0.5 0 0"/>
      <inertia ixx="0.01" iyy="0.04" izz="0.04" ixy="0" ixz="0" iyz="0"/></inertial>
  </link>
</robot>
"#;

const GIEMON_ARM6_URDF: &str =
    include_str!("../../../../../40-engine/kami-engine/fixtures/giemon_arm6/giemon_arm6.urdf");

#[derive(Serialize)]
struct FkSample {
    q: Vec<f32>,
    tip: Vec<f32>,
}

#[derive(Serialize)]
struct IkSample {
    target: Vec<f32>,
    q: Vec<f32>,
    /// |FK(q) − target| as computed by the Rust solver itself.
    tip_err: f32,
}

#[derive(Serialize)]
struct Parity2Link {
    convention: String,
    links: [f32; 2],
    tip_local: [f32; 3],
    fk: Vec<FkSample>,
    ik: Vec<IkSample>,
}

#[derive(Serialize)]
struct GiemonArm6 {
    ndof: usize,
    joint_limits: Vec<[f32; 2]>,
    /// Conservative workspace radius = Σ joint-origin offsets + tip offset.
    max_reach_upper_bound: f32,
    fk: Vec<FkSample>,
    ik: Vec<IkSample>,
}

#[derive(Serialize)]
struct Trace {
    generator: String,
    solver: String,
    parity_2link: Parity2Link,
    giemon_arm6: GiemonArm6,
}

/// Build an Articulation3dConfig from a URDF string (kinematics only — gravity
/// and dt are irrelevant to FK/IK but required by the constructor).
fn arts(urdf: &str) -> (Articulation3dConfig, usize) {
    let sys = kami_articulated::parse_urdf(urdf).expect("urdf parses");
    let cfg = Articulation3dConfig::from_articulated_system(
        &sys,
        Vec3::new(0.0, 0.0, -9.81),
        1.0 / 240.0,
    );
    let last_body = cfg.n_bodies() - 1;
    (cfg, last_body)
}

fn tip_world(cfg: &Articulation3dConfig, link: usize, p_local: Vec3, q: &[f32]) -> Vec3 {
    let (r, t) = cfg.link_world(q)[link];
    t + r * p_local
}

fn fk_grid(
    cfg: &Articulation3dConfig,
    link: usize,
    p_local: Vec3,
    grid: &[Vec<f32>],
) -> Vec<FkSample> {
    grid.iter()
        .map(|q| {
            let p = tip_world(cfg, link, p_local, q);
            FkSample { q: q.clone(), tip: vec![p.x, p.y, p.z] }
        })
        .collect()
}

fn ik_samples(
    cfg: &Articulation3dConfig,
    link: usize,
    p_local: Vec3,
    targets: &[Vec3],
    ndof: usize,
) -> Vec<IkSample> {
    targets
        .iter()
        .map(|target| {
            // Neutral-ish seed away from the straight-arm singularity.
            let q_init: Vec<f32> = (0..ndof).map(|i| 0.3 + 0.1 * i as f32).collect();
            let q = cfg.solve_position_ik(link, p_local, *target, &q_init, 400, 0.05);
            let p = tip_world(cfg, link, p_local, &q);
            IkSample {
                target: vec![target.x, target.y, target.z],
                q,
                tip_err: (p - *target).length(),
            }
        })
        .collect()
}

fn main() {
    // ── 1. 2-link planar parity (the Python PlanarArm contract) ──────────
    let (cfg2, tip_link2) = arts(PARITY_2LINK_URDF);
    let tip_local2 = Vec3::new(1.0, 0.0, 0.0); // end of the 1.0 m second link

    let mut grid2: Vec<Vec<f32>> = Vec::new();
    for q0 in [-2.0_f32, -1.2, -0.5, 0.0, 0.4, 0.9, 1.7, 2.6] {
        for q1 in [-2.4_f32, -1.3, -0.6, 0.0, 0.7, 1.5, 2.2] {
            grid2.push(vec![q0, q1]);
        }
    }
    let ik_targets2 = vec![
        Vec3::new(1.5, 0.4, 0.0),
        Vec3::new(1.2, 0.5, 0.0),
        Vec3::new(0.9, -0.8, 0.0),
        Vec3::new(-1.1, 1.0, 0.0),
        Vec3::new(0.5, 1.8, 0.0),
        Vec3::new(2.0, 0.3, 0.0),
    ];
    let parity = Parity2Link {
        convention: "revolute +z, links along +x; planar x-y, CCW positive; \
                     Python PlanarArm((1.2,1.0)).fk == Rust (tip.x, tip.y)"
            .into(),
        links: [1.2, 1.0],
        tip_local: [1.0, 0.0, 0.0],
        fk: fk_grid(&cfg2, tip_link2, tip_local2, &grid2),
        ik: ik_samples(&cfg2, tip_link2, tip_local2, &ik_targets2, 2),
    };

    // ── 2. giemon_arm6 (the 6-DOF swap target) ───────────────────────────
    let sys6 = kami_articulated::parse_urdf(GIEMON_ARM6_URDF).expect("giemon urdf");
    let ndof6 = sys6.joints.iter().filter(|j| j.kind != JointKind::Fixed).count();
    let limits: Vec<[f32; 2]> = sys6
        .joints
        .iter()
        .filter(|j| j.kind != JointKind::Fixed)
        .map(|j| [j.lower, j.upper])
        .collect();
    let (cfg6, tip_link6) = arts(GIEMON_ARM6_URDF);
    let tip_local6 = Vec3::new(0.0, 0.0, 0.08); // beyond link6 origin (wrist flange)

    // Upper bound on reach: sum of |joint origin| offsets + tip offset.
    let max_reach: f32 =
        sys6.joints.iter().map(|j| j.origin.xyz.length()).sum::<f32>() + tip_local6.length();

    let grid6: Vec<Vec<f32>> = vec![
        vec![0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
        vec![0.5, -0.4, 0.6, 0.2, -0.3, 0.1],
        vec![-1.0, 0.8, -0.9, 1.2, 0.5, -0.6],
        vec![2.0, -1.5, 1.8, -2.0, 1.4, 2.5],
        vec![0.3, 0.3, 0.3, 0.3, 0.3, 0.3],
        vec![-0.7, -0.7, 0.7, 0.7, -0.7, 0.7],
    ];
    // Reachable targets chosen from FK of in-limit configurations.
    let ik_targets6: Vec<Vec3> = grid6
        .iter()
        .skip(1)
        .take(4)
        .map(|q| tip_world(&cfg6, tip_link6, tip_local6, q))
        .collect();

    let giemon = GiemonArm6 {
        ndof: ndof6,
        joint_limits: limits,
        max_reach_upper_bound: max_reach,
        fk: fk_grid(&cfg6, tip_link6, tip_local6, &grid6),
        ik: ik_samples(&cfg6, tip_link6, tip_local6, &ik_targets6, ndof6),
    };

    let trace = Trace {
        generator: "kami-bridge 0.1.0".into(),
        solver: "kami-genesis Articulation3d (Featherstone RNEA+CRBA), kami-articulated parse_urdf"
            .into(),
        parity_2link: parity,
        giemon_arm6: giemon,
    };
    println!("{}", serde_json::to_string_pretty(&trace).unwrap());
}

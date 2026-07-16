# kami-bridge — Python↔kami-genesis kinematics cross-validation

Generates `../golden/kami_fk_ik_trace.json`: FK/IK traces computed by the
**real kami-genesis Featherstone solver** (`Articulation3dConfig`, URDF-loaded
via `kami_articulated::parse_urdf`) for

1. a 2-link planar arm with the exact geometry of the Python
   `:representative` `PlanarArm((1.2, 1.0))` every infra vertical uses
   (revolute +z, links along +x ⇒ planar x–y, CCW positive), and
2. the real **giemon_arm6** 6-DOF fixture (the ADR-2606091800 swap target).

`../test_kami_parity.py` consumes the committed golden trace, so the parity
suite runs **without** Rust or the submodule. This crate is only needed to
regenerate the trace after a kami-engine bump:

```bash
# one-time: populate the engine submodule
git submodule update --init 40-engine/kami-engine

cd 20-actors/kuni-umi/robotics
cargo run --manifest-path kami-bridge/Cargo.toml --release \
  > golden/kami_fk_ik_trace.json
PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest test_kami_parity.py
```

Deterministic: fixed sample grids, no RNG, no clock. Known, recorded asymmetry:
the Rust damped-least-squares IK can stall on far targets where the Python
analytic 2-link IK is closed-form exact — the parity test asserts agreement on
converged samples and analytic coverage of all reachable targets.

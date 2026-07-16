# funamori 舫 — 淡水化発電 (salinity-gradient power)

Marine-renewable **salinity-gradient power**: electricity from the Gibbs free energy released
when fresh river water mixes with seawater. Tier-B actor, ADR-2605265600 (sub-ADR of 2605264100
§4), R0. The previously path-reserved `salinity_gradient_pro_red/` cell, now a runnable method.

## Two methods (`methods/salinity_gradient.cljc`)

| | Principle | Power density (ADR) | Membrane |
|---|---|---|---|
| **PRO** | osmotic pull lifts brine pressure → turbine; `W_max = A·Δπ²/4` at `ΔP=Δπ/2` | 1–3 W/m² | TFC polyamide on polysulfone |
| **RED** | salinity-driven ion flux across CEM/AEM stack → current; `W = E_cell²/(8·R_area)` | 0.5–2 W/m² | SPEEK sulfonated (open-design) |

## The point: ADR constraints are executable

The ADR-2605265600 constitutional gates are **throwing assertions**, not prose:

```clojure
(require '[funamori.methods.salinity-gradient :as sg])

;; 木曽川河口 reference site, in-house PRO membrane, 30 kW
(sg/evaluate-site
  {:pair (sg/make-source-pair :draw-g-l 36.5 :feed-g-l 0.5)
   :membrane (sg/make-pro-membrane :water-permeability 1.0e-12)
   :power-density-w-m2 1.5
   :total-membrane-area-m2 20000.0})
;; => {:permitted true :technology :pro :rated-kw 30.0 :delta-pi-bar 30.0 ...}

;; commercial Toray membrane → rejected at the gate
(sg/evaluate-site {:pair ... :membrane (sg/make-pro-membrane :vendor "Toray") ...})
;; => {:permitted false :violation :commercial-membrane :message "..."}
```

Gates: open-membrane mandatory (§1.1) · no Toray/Hydranautics/GE-Power/Statkraft (§2) ·
no PFAS/Nafion (§1.2) · Δsalinity ≥30 g/L (§1.4) · power-density ≥1 W/m² (§1.6) ·
≤50 kW & ≤1 site through R3 (§1.9).

## Robotics design (`methods/stack_robotics.cljc`)

The deploy/maintain layer over the shared **kuni-umi** infra-robotics substrate (ADR-2606091800):

- **Anti-fouling coverage sweep** — boustrophedon raster over each membrane face (ADR §5),
  turned into a planar-arm IK trajectory + motion-safety-envelope check.
- **EOL membrane-module swap** — pick-and-place that **reuses the §1/§2 membrane gates**, so a
  commercial or PFAS replacement membrane is refused at the robotics layer too.

All robotics is R0 dry-run (`server-held-key=false`, `dry-run=true`); civilian-use allowlist +
member-signature (no-server-key) + ≥2-robot witness quorum enforced.

```clojure
(require '[funamori.methods.stack-robotics :as r])
(r/plan-clean-pass {:stack (r/make-stack :rows 4 :cols 4)
                    :row 0 :col 0 :member-sig "did:key:zMember"
                    :witness-sigs ["did:key:zRobotA" "did:key:zRobotB"]})
;; => {:reachable true :coverage 1.0 :witness-ok true :datom {... :dry-run true}}
```

## Plant + grid-tie (`methods/plant.cljc`)

Completes the kuni-umi **3-layer infra-robotics pattern** (plant / control / kinematics). The
river-mouth intake's draw-salinity oscillates with the **tide** (M2 semidiurnal) → tidal power
swing → battery-buffered **grid-tie** smooths it into a steady feed to **hikari**'s microgrid
(ADR §4). The tidal PEAK is capped at 50 kW (reuses the physics-layer site cap), and the smoother
reports an **honest shortfall** when the battery is too small.

```clojure
(require '[funamori.methods.plant :as p])
(p/couple-to-microgrid (p/make-plant :membrane-area-m2 20000.0 :power-density-w-m2 1.5)
                       :battery-kwh 1000.0)
;; => {:funamori.plant/microgrid "hikari" :funamori.plant/capacity-factor 0.97
;;     :funamori.plant/fully-smoothed true :funamori.plant/model-only true ...}
```

## Test

```sh
./run_tests.sh    # 46 tests / 115 assertions, babashka
```

## Status

R0 = design + runnable methods (physics + robotics + plant/grid-tie) + gates + kotoba EAVT
schema/seed + 3 lexicons. No hardware; bench pilot is R1 (Council + membrane-chemist + mizuho R2
attested site gated). See `MATURITY.md`.

Apache-2.0 + etzhayyim Charter Compliance Rider v3.1.

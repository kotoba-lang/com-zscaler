(ns watatsumi.methods.test-charter-gates
  "watatsumi 綿津見 — constitutional-gate conformance tests (manifest + local lexicons).

  Substrate-native Clojure (clj + datomic first tier). watatsumi builds CIVILIAN submersibles
  (≤6500 m) — its constitutional core is the no-weaponization / KPI-cap discipline (Transparent
  Force §1.12, Charter Rider §2(a)). Its 14 gates + 12 non-goals are declared in the manifest,
  and the per-step evidence gates are encoded as required-fields across the 8 first-tier
  `lex/*.edn` lexicons. This suite pins them so a future R-phase cell wave cannot silently
  drift them:

    N1   naval weapons HARD-prohibited (torpedoes/missiles/mines/mounts) — the headline gate
    G12  KPI caps NOT weakened — max civilian depth 6500 m / max crew 3 / max submerged 72 h
    G3   every weld + test step IPFS-pinned (weldInspectionRecord.ipfsCid)
    G8   active-sonar acoustic log on the pressure test (≤180 dB cetacean threshold)
    G11  hot-work / pressure-test / dive ops are Council-gated (silenSubmersibleReview)
    G13  propulsion is recorded (LFP/H₂/NH₃/methanol fuel-cell only; nuclear = N2)

  Reads manifest via cheshire + local lexicons via clojure.edn. It weakens no gate; it asserts
  them — the cap-value checks are a doc-drift guard so nobody silently raises depth/crew/duration.
  Touches neither the no-server-key nor Murakumo-only (manifest G10) invariants."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; watatsumi/
     (def ^:private lexdir (java.io.File. actor-dir "lex"))
     (defn- lex [name]
       (edn/read-string (slurp (java.io.File. lexdir (str name ".edn")))))
     (defn- manifest []
       (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))))

(defn- record-node [doc] (get-in doc [:defs :main :record]))
(defn- required-of [doc] (set (:required (record-node doc))))

(defn- gate-map []
  (let [cg (get (manifest) "constitutionalGates")]
    (or (get cg "gates") cg)))
(defn- nongoal-map []
  (let [ng (get (manifest) "nonGoals")]
    (or (get ng "goals") ng)))

;; ── 14 gates + 12 non-goals declared ──
(deftest gates-and-nongoals-declared
  (let [gn (->> (keys (gate-map)) (keep #(second (re-matches #"G(\d+).*" %)))
                (map #(Integer/parseInt %)) set)
        nn (->> (keys (nongoal-map)) (keep #(second (re-matches #"N(\d+).*" %)))
                (map #(Integer/parseInt %)) set)]
    (is (= (set (range 1 15)) gn) "manifest must declare G1–G14")
    (is (= (set (range 1 13)) nn) "manifest must declare N1–N12")))

;; ── N1 — naval weapons HARD-prohibited (the headline constitutional gate) ──
(deftest n1-no-naval-weapons
  (let [n1 (str/lower-case (str (get (nongoal-map) "N1")))]
    (is (str/includes? n1 "weapon")
        "N1: the first non-goal must prohibit naval weapons")))

;; ── G12 — KPI caps NOT weakened (doc-drift guard on the cap VALUES) ──
(deftest g12-kpi-caps-not-weakened
  (let [g12 (str (get (gate-map) "G12"))]
    (doseq [needle ["6500" "crew 3" "72 h"]]
      (is (str/includes? g12 needle)
          (str "G12: KPI-cap text must still pin '" needle "' (no silent cap raise)")))))

;; ── G3 — every weld pass + test step is IPFS-pinned evidence ──
(deftest g3-ipfs-pinned-evidence
  (is (contains? (required-of (lex "weldInspectionRecord")) "ipfsCid")
      "G3: weldInspectionRecord must require an IPFS-pinned CID"))

;; ── G8 + G12 — the pressure test logs acoustics + the tested depth ──
(deftest g8-g12-pressure-test
  (let [r (required-of (lex "pressureTestRecord"))]
    (is (contains? r "acousticLogCid") "G8: pressure test must log acoustics (cetacean threshold)")
    (is (contains? r "depthTestedM") "G12: pressure test must record the tested depth (auditable cap)")
    (is (contains? r "durationH") "G12: pressure test must record the submerged duration")))

;; ── G11 — dive/test ops are Council-gated ──
(deftest g11-council-gated
  (is (contains? (required-of (lex "silenSubmersibleReview")) "councilApproval")
      "G11: submersible review must require Council approval"))

;; ── G13 — propulsion type is recorded (clean fuels only; nuclear = N2) ──
(deftest g13-propulsion-recorded
  (is (contains? (required-of (lex "systemIntegrationAttestation")) "propulsionType")
      "G13: system integration must record the propulsion type"))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'watatsumi.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

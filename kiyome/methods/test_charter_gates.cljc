(ns kiyome.methods.test-charter-gates
  "kiyome 清め — constitutional-gate conformance tests (local lexicons).

  Substrate-native Clojure (clj + datomic first tier). kiyome is domestic/janitorial cleaning
  robotics. Its CLAUDE.md states the privacy gate G9 is a HARD INVARIANT in the lexicons:
  'on-device only, no cloud imagery, no surveillance feed, no biometric capture'. This suite pins
  the G9 privacy-by-construction gate (and the bounded cleaning vocabularies) across the
  first-tier `lex/*.edn` lexicons (read via clojure.edn) so a future R-phase cell wave cannot
  silently drift them:

    G9  privacy-by-construction — onDeviceOnly const true + imageryRetained const false (cleaning
        pass) + biometricCapture const false (site assessment); the cleaner is a vacuum, NOT a
        surveillance platform (§2(c) anti-surveillance, applied inside the home)
    bounded vocabularies — method / surfaceClass / siteClass are closed sets (no record/scan verb)
    circular hand-off — waste is segregated and handed to another actor (handoffActor), not dumped

  It weakens no gate; it asserts them. The dividend-coupling (G2), cash≡0 (G5), outward-gating
  (G7) and Murakumo-only (G4) gates live in cells/manifest and are untouched here."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.edn :as edn]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; kiyome/
     (def ^:private lexdir (java.io.File. actor-dir "lex"))
     (defn- lex [name]
       (edn/read-string (slurp (java.io.File. lexdir (str name ".edn")))))))

(defn- record-node [doc] (get-in doc [:defs :main :record]))
(defn- required-of [doc] (set (:required (record-node doc))))
(defn- const-of [doc field] (get-in (record-node doc) [:properties field :const]))
(defn- enum-of [doc field]
  (let [p (get-in (record-node doc) [:properties field])]
    (set (or (:enum p) (get-in p [:items :enum])))))

;; ── G9 — privacy-by-construction: on-device only, no cloud imagery, no biometric ──
(deftest g9-privacy-on-device-no-imagery
  (let [c (lex "cleaningPassAttestation")]
    (is (contains? (required-of c) "onDeviceOnly") "G9: cleaning pass must require onDeviceOnly")
    (is (= true (const-of c :onDeviceOnly)) "G9: cleaningPass.onDeviceOnly const true")
    (is (= false (const-of c :imageryRetained)) "G9: cleaningPass.imageryRetained const false"))
  (let [s (lex "siteAssessmentRecord")]
    (is (contains? (required-of s) "onDeviceOnly") "G9: site assessment must require onDeviceOnly")
    (is (= true (const-of s :onDeviceOnly)) "G9: siteAssessment.onDeviceOnly const true")
    (is (= false (const-of s :biometricCapture)) "G9: siteAssessment.biometricCapture const false")))

;; ── bounded cleaning vocabularies — the robot sweeps/mops; it does not record/scan ──
(deftest bounded-cleaning-vocabularies
  (let [m (enum-of (lex "cleaningPassAttestation") :method)]
    (is (= #{"sweep" "vacuum" "mop" "wipe"} m)
        (str "method must be the bounded cleaning set, got " m))
    (is (empty? (set/intersection m #{"record" "scan" "surveil" "monitor" "stream"}))
        "no recording/surveillance verb representable as a cleaning method"))
  (is (= #{"high-touch" "floor" "kitchen" "bathroom"} (enum-of (lex "sanitizationRecord") :surfaceClass))
      "surfaceClass is a bounded set")
  (is (= #{"home" "facility" "office" "common-space"} (enum-of (lex "siteAssessmentRecord") :siteClass))
      "siteClass is a bounded set"))

;; ── circular hand-off — waste is segregated and handed to another actor, not dumped ──
(deftest circular-waste-handoff
  (let [w (required-of (lex "wasteSegregationRecord"))]
    (is (contains? w "streams") "waste must be segregated into streams")
    (is (contains? w "handoffActor") "waste must be handed to another actor (circular, not dumped)")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'kiyome.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

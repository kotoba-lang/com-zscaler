;; madomori 窓守 — test suite (clojure.test, babashka-runnable).
;; Run: bb --classpath 20-actors 20-actors/madomori/methods/test_madomori.clj
;; Per ADR-2606142020 (madomori R0).
(ns madomori.methods.test-madomori
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.edn :as edn]
            [clojure.set]
            [clojure.string :as str]
            [madomori.methods.facade-path :as fp]
            [madomori.methods.wind-envelope :as we]
            [madomori.methods.adhesion :as ad]
            [madomori.methods.anchor :as anc]
            [madomori.methods.analyze :as az]
            [madomori.methods.datom-emit :as de]
            [madomori.methods.coverage :as cov]
            [madomori.methods.multi-face :as mf]
            [madomori.methods.water-recovery :as wr]
            [madomori.methods.handoff :as ho]))

;; ── facade_path ──────────────────────────────────────────────────────────────
(deftest boustrophedon-visits-every-pane-once
  (testing "S-shape coverage visits every pane exactly once"
    (let [path (fp/boustrophedon 3 4)
          cov (fp/coverage path 3 4)]
      (is (= 12 (count path)))
      (is (:complete? cov))
      (is (not (:duplicate? cov)))
      (is (empty? (:missing cov))))))

(deftest boustrophedon-alternates-direction
  (testing "row 0 left→right, row 1 right→left (minimal-turn sweep)"
    (let [path (fp/boustrophedon 2 3)]
      (is (= [[0 0] [0 1] [0 2] [1 2] [1 1] [1 0]] path)))))

(deftest boustrophedon-rejects-negative
  (is (thrown? clojure.lang.ExceptionInfo (fp/boustrophedon -1 4))))

(deftest path-length-and-budget-scale
  (testing "longer grids cost more path; budget scales per-pane (G2)"
    (let [p1 (fp/boustrophedon 2 2)
          p2 (fp/boustrophedon 4 4)]
      (is (< (fp/path-length-m p1 4.0 3.0) (fp/path-length-m p2 4.0 3.0)))
      (let [b (fp/consumable-budget 16 0.25 4.0)]
        (is (= 16 (:panes b)))
        (is (= 4.0 (:water-l b)))
        (is (= 64.0 (:agent-ml b)))))))

(deftest coverage-detects-missing-and-duplicate
  (testing "an incomplete or double-pass path is not complete"
    (is (not (:complete? (fp/coverage [[0 0] [0 1]] 2 2))))         ; missing
    (is (not (:complete? (fp/coverage [[0 0] [0 0] [0 1] [1 0] [1 1]] 2 2)))))) ; dup

;; ── wind_envelope (★ G5) ─────────────────────────────────────────────────────
(deftest sway-grows-with-wind-and-rope
  (testing "sway amplitude grows with wind speed and rope length"
    (is (< (we/sway-amplitude-m 50.0 14.0 4.0)
           (we/sway-amplitude-m 50.0 14.0 8.0)))      ; more wind
    (is (< (we/sway-amplitude-m 25.0 14.0 6.0)
           (we/sway-amplitude-m 80.0 14.0 6.0)))      ; more rope
    (is (thrown? clojure.lang.ExceptionInfo (we/sway-amplitude-m 50.0 0.0 4.0)))))  ; bad mass

(deftest wind-work-stop-raises-above-threshold
  (testing "★ G5 — work-permitted? RAISES at/above the wind work-stop threshold"
    (let [two [{:independent true} {:independent true}]]
      (is (true? (we/work-permitted? {:speed-mps 6.0 :gust-mps 8.0 :stop-threshold-mps 10.0} two)))
      (is (thrown? clojure.lang.ExceptionInfo
                   (we/work-permitted? {:speed-mps 11.0 :stop-threshold-mps 10.0} two)))
      ;; a gust alone above threshold also stops work
      (is (thrown? clojure.lang.ExceptionInfo
                   (we/work-permitted? {:speed-mps 6.0 :gust-mps 12.0 :stop-threshold-mps 10.0} two))))))

(deftest single-anchor-plan-raises
  (testing "★ G5 — a descent needs ≥2 independent anchors; single-anchor RAISES"
    (is (not (we/fall-arrest-redundant? [{:independent true}])))
    (is (we/fall-arrest-redundant? [{:independent true} {:independent true}]))
    ;; a non-independent second anchor does not count
    (is (not (we/fall-arrest-redundant? [{:independent true} {:independent false}])))
    (is (thrown? clojure.lang.ExceptionInfo
                 (we/work-permitted? {:speed-mps 3.0 :stop-threshold-mps 10.0}
                                     [{:independent true}])))))

;; ── adhesion (★ G7) ──────────────────────────────────────────────────────────
(deftest adhesion-fos-by-surface
  (testing "glass seals better than stone → higher factor-of-safety"
    (is (> (ad/factor-of-safety 900.0 :glass 14.0)
           (ad/factor-of-safety 900.0 :stone 14.0)))
    (is (thrown? clojure.lang.ExceptionInfo (ad/effective-adhesion-n 900.0 :unknown)))))

(deftest adhesion-safe-raises-below-margin
  (testing "★ G7 — adhesion-safe? RAISES when FoS is below the required factor"
    (is (true? (ad/adhesion-safe? {:suction-force-n 900.0 :surface :glass
                                   :mass-kg 14.0 :required-fos 2.5})))
    ;; porous stone (efficiency 0.45) can't make the margin at this load → RAISE
    (is (thrown? clojure.lang.ExceptionInfo
                 (ad/adhesion-safe? {:suction-force-n 900.0 :surface :stone
                                     :mass-kg 20.0 :required-fos 2.5})))
    ;; too heavy on glass → RAISE
    (is (thrown? clojure.lang.ExceptionInfo
                 (ad/adhesion-safe? {:suction-force-n 900.0 :surface :glass
                                     :mass-kg 60.0 :required-fos 2.5})))))

;; ── anchor (★ G5 per-anchor descent-load verification) ───────────────────────
(deftest rig-ok-with-two-well-rated-reachable-anchors
  (testing "★ G5 — ≥2 independent reachable anchors each rated ≥ load×FoS → rig-ok?"
    (let [anchors [{:anchor-id :a1 :rated-kn 12.0 :reachable? true}
                   {:anchor-id :a2 :rated-kn 10.0 :reachable? true}]]
      ;; working load 4 kN × FoS 2.0 = 8 kN required; both anchors clear it
      (is (true? (anc/rig-ok? anchors 4.0 2.0)))
      (is (true? (anc/rig-ok? anchors 4.0)))                 ; FoS defaults to 2.0
      (is (= 2 (count (anc/assert-rig! anchors 4.0 2.0))))    ; returns the qualifying anchors
      ;; a positive working load is required
      (is (thrown? clojure.lang.ExceptionInfo (anc/rig-ok? anchors 0.0 2.0))))))

(deftest under-rated-anchor-fails-and-raises
  (testing "★ G5 — an anchor below load×FoS does not count → rig-ok? false + assert-rig! RAISES"
    (let [anchors [{:anchor-id :b1 :rated-kn 6.0 :reachable? true}    ; < 4×2 = 8 kN
                   {:anchor-id :b2 :rated-kn 12.0 :reachable? true}]]
      (is (false? (anc/rig-ok? anchors 4.0 2.0)))             ; only 1 adequate anchor
      (is (thrown? clojure.lang.ExceptionInfo (anc/assert-rig! anchors 4.0 2.0))))))

(deftest single-reachable-anchor-fails-and-raises
  (testing "★ G5 — ≥2 independent anchors required; an unreachable second leaves only 1 → RAISES"
    (let [anchors [{:anchor-id :c1 :rated-kn 20.0 :reachable? true}
                   {:anchor-id :c2 :rated-kn 20.0 :reachable? false}]] ; unreachable → not counted
      (is (false? (anc/rig-ok? anchors 4.0 2.0)))             ; only 1 reachable, well-rated
      (is (thrown? clojure.lang.ExceptionInfo (anc/assert-rig! anchors 4.0 2.0)))
      ;; one well-rated reachable anchor is still a single point of failure → RAISES
      (is (thrown? clojure.lang.ExceptionInfo
                   (anc/assert-rig! [{:anchor-id :c1 :rated-kn 20.0 :reachable? true}] 4.0 2.0))))))

(deftest anchor-carries-no-imagery-or-person-data
  (testing "★ G3 — the rigging assessment is purely structural; no imagery/person/interior key"
    (let [anchors [{:anchor-id :a1 :rated-kn 12.0 :reachable? true}
                   {:anchor-id :a2 :rated-kn 10.0 :reachable? true}]
          a (anc/assess anchors 4.0 2.0)
          ks (keys a)]
      (is (every? keyword? ks))
      (is (not-any? (fn [k] (re-find #"(?i)image|photo|imagery|interior|person|biometric|camera"
                                     (name k)))
                    ks)))))

;; ── G3 privacy-by-construction (structural) ──────────────────────────────────
(def seed (az/load-seed "20-actors/madomori/data/facade.edn"))

(deftest imagery-is-on-device-only-and-no-recognition
  (testing "★ G3 — imagery never leaves the device; no person/interior recognition"
    (let [img (get-in seed [:robot :imagery])]
      (is (true? (:on-device-only img)))
      (is (= :pane-edge-only (:recognition img)))
      ;; the data model cannot express off-device imagery or biometric/interior recognition
      (is (not (contains? img :off-device)))
      (is (not (contains? img :cloud)))
      (is (not= :person (:recognition img)))
      (is (not= :interior (:recognition img)))
      (is (not= :biometric (:recognition img))))
    (testing "the emitted Datom log carries only the on-device imagery flag"
      (let [out (de/emit seed (az/run seed) 1)]
        (is (re-find #":mado\.robot/imagery-on-device true" out))
        (is (nil? (re-find #"(?i)off-device|cloud|biometric|interior" out)))))))

;; ── analyze + datom_emit (end-to-end over the seed) ──────────────────────────
(deftest analyze-end-to-end
  (let [res (az/run seed)]
    (testing "coverage is complete over the full pane grid"
      (is (:complete? (get-in res [:coverage :coverage])))
      (is (= (* (get-in res [:face :rows]) (get-in res [:face :cols]))
             (get-in res [:coverage :coverage :total])))
      (is (pos? (get-in res [:coverage :length-m]))))
    (testing "safety envelope + adhesion present; reference seed is a GO"
      (is (contains? (:envelope res) :permitted?))
      (is (:fall-arrest-redundant? (:envelope res)))          ; 2 independent anchors
      (is (:permitted? (:envelope res)))                       ; wind 6 m/s < 10
      (is (:safe? (:adhesion res)))                            ; glass FoS ≥ 2.5
      (is (true? (:go? res))))))

(deftest analyze-stops-on-high-wind
  (testing "★ G5 — a high-wind seed is NOT a GO (envelope refuses)"
    (let [windy (assoc-in seed [:wind :speed-mps] 14.0)
          res (az/run windy)]
      (is (not (:permitted? (:envelope res))))
      (is (false? (:go? res))))))

(deftest datom-emit-shape
  (let [res (az/run seed)
        out (de/emit seed res 1)]
    (testing "emits ground :add datoms + transient :derived readouts"
      (is (re-find #":mado\.face/surface" out))
      (is (re-find #":mado\.robot/required-fos" out))
      (is (re-find #":en/kind :anchored-by" out))
      (is (re-find #":mado\.anchor/independent" out))
      (is (re-find #":bond/adhesion-fos" out))
      (is (re-find #":bond/go" out))
      (is (re-find #":derived\]" out))
      ;; well-formed EDN vector of datoms
      (is (vector? (clojure.edn/read-string out))))))

;; ── handoff (cross-actor chain edge: madomori→tatekata repair-order) ─────────
(deftest outbound-to-tatekata
  (testing "a detected façade defect → tatekata repair-order intent, source-attributed"
    (let [h (ho/repair-handoff {:pane-id "p-12-04" :defect-kind :cracked-pane :severity :high})]
      (is (= "madomori" (:from-actor h)))
      (is (= "tatekata" (:to-actor h)))
      (is (= :repair-order (:kind h)))
      (is (= :cracked-pane (get-in h [:payload :defect-kind])))
      (is (= :high (get-in h [:payload :severity])))
      (is (= "p-12-04" (get-in h [:payload :pane-id]))))))

(deftest outbound-handoffs-maps-every-defect
  (testing "every detected defect becomes exactly one tatekata repair-order handoff"
    (let [hs (ho/outbound-handoffs [{:pane-id "p-1" :defect-kind :cracked-pane :severity :high}
                                    {:pane-id "p-2" :defect-kind :sealant-failure :severity :medium}
                                    {:pane-id "p-3" :defect-kind :spalling :severity :low}])]
      (is (= 3 (count hs)))
      (is (every? #(= "madomori" (:from-actor %)) hs))
      (is (every? #(= "tatekata" (:to-actor %)) hs))
      (is (every? #(= :repair-order (:kind %)) hs)))))

(deftest handoff-provenance-gate
  (testing "G9 — an orphan handoff (no source/destination) RAISES"
    (is (thrown? clojure.lang.ExceptionInfo (ho/assert-handoff! {:id "x" :to-actor "tatekata"})))
    (is (thrown? clojure.lang.ExceptionInfo (ho/assert-handoff! {:id "x" :from-actor "madomori"})))
    (is (= "madomori" (:from-actor (ho/assert-handoff! {:id "x" :from-actor "madomori" :to-actor "tatekata"}))))))

(deftest handoff-emit-shape
  (testing "emits well-formed EDN :handoff/* 縁 with actor provenance on every edge"
    (let [hs (ho/outbound-handoffs [{:pane-id "p-12-04" :defect-kind :cracked-pane :severity :high}
                                    {:pane-id "p-07-11" :defect-kind :sealant-failure :severity :medium}])
          out (ho/emit hs 1)]
      (is (re-find #":handoff/from-actor" out))
      (is (re-find #":handoff/to-actor" out))
      (is (re-find #"en\.handoff\.madomori\.tatekata\." out))
      (is (vector? (edn/read-string out))))))

(deftest handoff-carries-no-imagery-or-person-data
  (testing "★ G3 — a repair handoff carries ONLY the structural defect descriptor; no imagery/interior/person"
    (let [h (ho/repair-handoff {:pane-id "p-12-04" :defect-kind :cracked-pane :severity :high})
          payload-keys (set (keys (:payload h)))]
      (is (= #{:pane-id :defect-kind :severity} payload-keys))
      ;; no image/photo/imagery/interior/person/biometric/camera keys are representable
      (is (not-any? (fn [k] (re-find #"(?i)image|photo|imagery|interior|person|biometric|camera"
                                     (name k)))
                    payload-keys))
      ;; and the same holds through the emitted Datom log (structural defect only)
      (let [out (ho/emit [h] 1)]
        (is (nil? (re-find #"(?i)image|photo|imagery|interior|person|biometric|camera" out)))))))

;; ── coverage (HONEST occupation sub-task map; G5 sourcing-honesty) ───────────
(deftest coverage-fraction-is-honest
  (testing "coverage fraction in (0,1] and equals covered/total"
    (let [{:keys [total covered coverage]} (cov/report)]
      (is (pos? coverage))
      (is (<= coverage 1.0))
      (is (= coverage (/ (double covered) total))))))

(deftest coverage-gaps-are-exactly-the-uncovered
  (testing "★ G5 — :gaps is exactly the uncovered sub-tasks (honest; full coverage → empty)"
    (let [gaps (:gaps (cov/report))]
      (is (= (set (filter (complement :covered?) cov/sub-tasks)) (set gaps)))
      (is (not-any? :covered? gaps)))))

(deftest coverage-is-complete
  (testing "all 8 occupation sub-tasks are now backed by a real method (100%)"
    (let [{:keys [total covered coverage gaps]} (cov/report)]
      (is (= 8 total))
      (is (= total covered))
      (is (= 1.0 coverage))
      (is (empty? gaps)))))

(deftest coverage-covered-names-a-method
  (testing "every covered sub-task names a non-nil backing method"
    (is (every? (fn [s] (some? (:method s)))
                (filter :covered? cov/sub-tasks)))))

;; ── multi_face (multi-face campaign routing) ─────────────────────────────────
(def mf-faces
  [{:face-id :south :access-point [2 0]  :rows 12 :cols 8}
   {:face-id :north :access-point [2 30] :rows 12 :cols 8}
   {:face-id :east  :access-point [20 0] :rows 10 :cols 6}])

(def mf-pane {:pane-h-m 1.4 :pane-w-m 1.0})

(deftest face-sequence-visits-every-face-once
  (testing "the sequence visits every face exactly once (no skip, no repeat)"
    (let [{:keys [order reposition-distance]} (mf/face-sequence mf-faces [0 0])]
      (is (= (count mf-faces) (count order)))
      (is (= (set (map :face-id mf-faces)) (set order)))
      (is (= (count order) (count (distinct order))))
      (is (pos? reposition-distance)))))

(deftest face-sequence-nearest-first
  (testing "nearest-neighbour puts the closest access point first"
    ;; from dock [0 0]: south [2 0] is nearest (d=2), then east [20 0], then north [2 30]
    (let [{:keys [order]} (mf/face-sequence mf-faces [0 0])]
      (is (= :south (first order)))
      ;; explicit: a face whose access point is the closest to the origin leads
      (let [closer [{:face-id :far  :access-point [99 99] :rows 2 :cols 2}
                    {:face-id :near :access-point [1 0]   :rows 2 :cols 2}]]
        (is (= :near (first (:order (mf/face-sequence closer [0 0])))))))))

(deftest campaign-coverage-sums-per-face-lengths
  (testing "campaign coverage total = Σ per-face path lengths (positive)"
    (let [camp (mf/campaign-coverage mf-faces mf-pane [0 0])
          sum-per-face (reduce + 0.0 (map :coverage-length-m (:per-face camp)))]
      (is (pos? (:coverage-length-m camp)))
      (is (= (:coverage-length-m camp) sum-per-face))
      (is (every? #(pos? (:coverage-length-m %)) (:per-face camp)))
      ;; total = coverage + reposition, and exceeds coverage alone (reposition > 0)
      (is (= (:total-length-m camp)
             (+ (:coverage-length-m camp) (:reposition-distance camp))))
      (is (> (:total-length-m camp) (:coverage-length-m camp))))))

(deftest multi-face-carries-no-imagery-or-person-data
  (testing "★ G3 — no imagery/person/interior/biometric key in any returned map"
    (let [camp (mf/campaign-coverage mf-faces mf-pane [0 0])
          ks (fn collect [x]
               (cond
                 (map? x) (concat (mapcat (fn [[k v]] (cons k (collect v))) x))
                 (sequential? x) (mapcat collect x)
                 :else nil))
          all-keys (->> (concat (ks camp) (ks (mf/face-sequence mf-faces [0 0])))
                        (filter keyword?))]
      (is (seq all-keys))
      (is (not-any? (fn [k] (re-find #"(?i)image|photo|imagery|interior|person|biometric|camera"
                                     (name k)))
                    all-keys)))))

;; ── water_recovery (★ G2 eco — runoff/detergent capture water balance) ───────
(deftest water-balance-fraction-and-loss
  (testing "recovery-fraction = captured/applied; lost = applied - captured"
    (let [b (wr/water-balance {:applied-l 8.0 :captured-l 6.0 :detergent-ml 64.0})]
      (is (= 0.75 (:recovery-fraction b)))
      (is (= 2.0 (:lost-l b)))
      ;; a dry pass cannot be balanced
      (is (thrown? clojure.lang.ExceptionInfo (wr/water-balance {:applied-l 0.0 :captured-l 0.0}))))))

(deftest water-balance-compliance-by-threshold
  (testing "★ G2 — above the eco floor is compliant?, below is not (non-raising)"
    (is (:compliant? (wr/water-balance {:applied-l 8.0 :captured-l 6.4})))        ; 80% ≥ 70%
    (is (not (:compliant? (wr/water-balance {:applied-l 8.0 :captured-l 4.0}))))  ; 50% < 70%
    ;; an explicit stricter floor flips a borderline pass non-compliant
    (is (not (:compliant? (wr/water-balance {:applied-l 8.0 :captured-l 6.4 :min-recovery 0.9}))))))

(deftest assert-compliant-raises-below-floor
  (testing "★ G2 — assert-compliant! RAISES below the floor, returns the balance above"
    (let [b (wr/assert-compliant! {:applied-l 8.0 :captured-l 6.4 :detergent-ml 64.0})]
      (is (:compliant? b))
      (is (= 0.8 (:recovery-fraction b))))
    ;; a leaky pass that escapes its detergent runoff must surface, not be forced
    (is (thrown? clojure.lang.ExceptionInfo
                 (wr/assert-compliant! {:applied-l 8.0 :captured-l 4.0 :detergent-ml 64.0})))))

(deftest water-balance-carries-no-imagery-or-person-data
  (testing "★ G3 — the balance map is purely fluid quantities; no imagery/person/interior key"
    (let [b (wr/water-balance {:applied-l 8.0 :captured-l 6.0 :detergent-ml 64.0})
          ks (keys b)]
      (is (every? keyword? ks))
      (is (not-any? (fn [k] (re-find #"(?i)image|photo|imagery|interior|person|biometric|camera"
                                     (name k)))
                    ks)))))

;; ── run-day full-pipeline (R1 integration) ────────────────────────────────────
(def ^:private day-res
  (delay (az/run-day (az/load-seed "20-actors/madomori/data/facade.edn"))))

(deftest run-day-exercises-all-domain-methods
  (testing "R1 — run-day composes EVERY domain method end-to-end"
    (let [res @day-res
          want #{"multi_face" "facade_path" "wind_envelope" "adhesion"
                 "anchor" "water_recovery" "handoff"}]
      ;; all 7 domain modules exercised
      (is (clojure.set/subset? want (:methods res)))
      (is (>= (count (:methods res)) 7))
      ;; base `run` keys preserved (datom_emit depends on them)
      (is (contains? res :coverage))
      (is (contains? res :envelope))
      (is (contains? res :adhesion))
      (is (contains? res :go?))
      ;; the day artifacts are present
      (is (contains? res :day))
      (is (contains? res :pipeline)))))

(deftest run-day-report-lists-pipeline
  (testing "R1 — report-day-str names the methods exercised + each stage"
    (let [s (az/report-day-str @day-res)]
      (is (re-find #"methods exercised" s))
      (doseq [m ["multi_face" "facade_path" "wind_envelope" "adhesion"
                 "anchor" "water_recovery" "handoff"]]
        (is (re-find (re-pattern m) s) (str "report must mention " m))))))

(defn- deep-keys
  "Every map key (recursively) reachable in an arbitrary nested structure."
  [x]
  (cond
    (map? x) (concat (keys x) (mapcat deep-keys (vals x)))
    (sequential? x) (mapcat deep-keys x)
    :else nil))

(deftest run-day-has-no-imagery
  (testing "★ G3 — no imagery/person/interior/biometric/camera key anywhere in :day"
    (let [day (:day @day-res)
          ks (filter keyword? (deep-keys day))]
      (is (seq ks))   ; sanity: the day map actually has keys to scan
      (is (not-any? (fn [k] (re-find #"(?i)image|photo|imagery|interior|person|biometric|camera"
                                     (name k)))
                    ks)))))

(deftest datom-emit-day-captures-full-day
  (testing "emit-day captures the FULL run-day (handoff 縁 + madomori-specific day attrs)"
    (let [res     @day-res
          base    (de/emit seed (az/run seed) 1)
          day-out (de/emit-day seed res 1)]
      ;; handoff 縁 (defect→tatekata repair-order, same shape as kuramori)
      (is (re-find #":handoff/from-actor" day-out))
      (is (re-find #"en\.handoff\.madomori\.tatekata\." day-out))
      ;; madomori-specific day attrs (faces sequenced + water recovery + anchor rig)
      (is (re-find #":bond/faces-sequenced" day-out))
      (is (re-find #":bond/water-recovery-fraction" day-out))
      (is (re-find #":bond/anchor-rig-ok" day-out))
      (is (re-find #":mado\.campaign/face-sequenced" day-out))
      ;; well-formed EDN vector
      (is (vector? (edn/read-string day-out)))
      ;; the day log is a STRICT superset of the base log's datoms
      (let [base-set (set (edn/read-string base))
            day-set  (set (edn/read-string day-out))]
        (is (clojure.set/subset? base-set day-set))
        (is (> (count day-set) (count base-set))))
      ;; ★ G3 — NO imagery/person/interior/biometric/camera key substring in the
      ;; emitted DATA (parse to EDN to drop the generated header comments, then strip
      ;; the single allowed on-device-imagery flag — imagery off-device is unrepresentable)
      (let [data (pr-str (edn/read-string day-out))]
        (is (nil? (re-find #"(?i)image|photo|imagery|interior|person|biometric|camera"
                           (str/replace data #":mado\.robot/imagery-on-device" ""))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'madomori.methods.test-madomori)]
    (System/exit (if (pos? (+ fail error)) 1 0))))

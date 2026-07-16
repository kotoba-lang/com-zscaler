;; kudamori 管守 — test suite (clojure.test, babashka-runnable).
;; Run: bb --classpath 20-actors 20-actors/kudamori/methods/test_kudamori.clj
;; Per ADR-2606142030 (kudamori R0).
(ns kudamori.methods.test-kudamori
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.edn :as edn]
            [kudamori.methods.atmosphere :as atm]
            [kudamori.methods.pipe-nav :as nav]
            [kudamori.methods.jetting :as jet]
            [kudamori.methods.analyze :as az]
            [kudamori.methods.datom-emit :as de]
            [kudamori.methods.coverage :as cov]
            [kudamori.methods.campaign :as camp]
            [kudamori.methods.inspection :as insp]
            [kudamori.methods.rootcut :as rc]
            [kudamori.methods.relining :as rl]
            [kudamori.methods.handoff :as ho]))

;; ── atmosphere (★ G5 — the headline confined-space entry gate) ────────────────
(def safe-air  {:o2-pct 20.9 :h2s-ppm 0.0 :ch4-lel 0.0 :co-ppm 0.0})
(def foul-air  {:o2-pct 18.6 :h2s-ppm 22.0 :ch4-lel 14.0 :co-ppm 12.0})

(deftest entry-permitted-on-safe-air
  (testing "a fresh-air reading permits entry with no hazards"
    (is (atm/entry-permitted? safe-air))
    (is (empty? (atm/hazards safe-air)))
    (is (= safe-air (atm/assert-entry! safe-air)))))

(deftest entry-refused-on-unsafe-air
  (testing "★ G5 — an unsafe atmosphere refuses entry and assert-entry! RAISES"
    (is (not (atm/entry-permitted? foul-air)))
    (is (thrown? clojure.lang.ExceptionInfo (atm/assert-entry! foul-air)))))

(deftest each-gas-threshold-detected
  (testing "every individual breach (O2 low/high, H2S, CH4, CO) is caught"
    (is (not (atm/entry-permitted? {:o2-pct 18.0 :h2s-ppm 0 :ch4-lel 0 :co-ppm 0})))   ; O2 low
    (is (not (atm/entry-permitted? {:o2-pct 24.0 :h2s-ppm 0 :ch4-lel 0 :co-ppm 0})))   ; O2 high
    (is (not (atm/entry-permitted? {:o2-pct 20.9 :h2s-ppm 10 :ch4-lel 0 :co-ppm 0})))  ; H2S at limit
    (is (not (atm/entry-permitted? {:o2-pct 20.9 :h2s-ppm 0 :ch4-lel 10 :co-ppm 0})))  ; CH4 at LEL
    (is (not (atm/entry-permitted? {:o2-pct 20.9 :h2s-ppm 0 :ch4-lel 0 :co-ppm 35}))))) ; CO at limit

(deftest purge-to-entry-converges
  (testing "forced ventilation drives a foul atmosphere to a passing reading"
    (let [r (atm/purge-to-entry foul-air 0.25 60)]
      (is (:entry-permitted? r))
      (is (pos? (:minutes r)))
      ;; the post-purge reading actually passes the gate (no lying about safety)
      (is (atm/entry-permitted? (:reading r))))))

(deftest purge-honest-when-budget-exhausted
  (testing "★ G5 — if ventilation can't clear it in the budget, entry stays refused"
    (let [r (atm/purge-to-entry foul-air 0.25 0)]  ; zero-minute budget
      (is (not (:entry-permitted? r)))
      (is (seq (:hazards r))))))

;; ── pipe_nav ──────────────────────────────────────────────────────────────────
(deftest diameter-fit-check
  (testing "crawler fits a wide pipe, not a narrow one"
    (is (nav/fits? 200 300 30))
    (is (not (nav/fits? 200 220 30)))))   ; 200+30 = 230 > 220

(deftest assert-fit-raises-on-no-fit
  (testing "a pipe the crawler cannot clear RAISES"
    (let [robot {:od-mm 200 :clearance-mm 30}]
      (is (= {:id "ok" :id-mm 300} (nav/assert-fit! robot {:id "ok" :id-mm 300})))
      (is (thrown? clojure.lang.ExceptionInfo
                   (nav/assert-fit! robot {:id "tight" :id-mm 210}))))))

(def segs
  [{:id "a-b" :from "A" :to "B" :id-mm 300 :length-m 10.0}
   {:id "b-c" :from "B" :to "C" :id-mm 300 :length-m 10.0 :blocked? true}
   {:id "a-c" :from "A" :to "C" :id-mm 300 :length-m 30.0}])

(deftest shortest-route-bfs
  (testing "BFS finds the fewest-hop route; trivial start=goal is empty"
    (is (= {:nodes ["A"] :segments []} (nav/shortest-route segs "A" "A")))
    (is (= ["a-c"] (:segments (nav/shortest-route segs "A" "C"))))))   ; 1 hop beats A-B-C

(deftest route-around-blocked-segment
  (testing "avoid-blocked? excludes the blocked segment from the graph"
    ;; reaching C: with blocked allowed, A-C (1 hop) anyway; force via B by removing a-c
    (let [s2 [{:id "a-b" :from "A" :to "B" :id-mm 300 :length-m 10.0}
              {:id "b-c" :from "B" :to "C" :id-mm 300 :length-m 10.0 :blocked? true}]]
      (is (some? (nav/shortest-route s2 "A" "C" false)))          ; blocked allowed → reachable
      (is (nil? (nav/shortest-route s2 "A" "C" true))))))         ; route-around → unreachable

(deftest plan-nav-flags-blocked-target
  (testing "planning to a blocked target reports it blocked but still fits + routes"
    (let [robot {:od-mm 200 :clearance-mm 30}
          plan (nav/plan-nav robot segs "A" "b-c" false)]   ; allow the blocked target's neighbours
      (is (:target-blocked? plan))
      (is (:fits plan))
      (is (>= (:hops plan) 0)))))

;; ── jetting (★ G7 — no pipe over-pressure) ────────────────────────────────────
(deftest jet-pressure-safe-within-rating
  (testing "pressure at/below the material rating is safe"
    (is (jet/jet-pressure-safe? 120.0 :vcp))      ; rating 150
    (is (not (jet/jet-pressure-safe? 120.0 :pvc))))) ; rating 100

(deftest jet-over-pressure-raises
  (testing "★ G7 — over-pressure that would damage the pipe RAISES"
    (is (thrown? clojure.lang.ExceptionInfo (jet/assert-jet-pressure! 200.0 :pvc)))
    (is (= 90.0 (jet/assert-jet-pressure! 90.0 :pvc)))
    ;; an unknown material has no rating → conservative raise
    (is (thrown? clojure.lang.ExceptionInfo (jet/rating-for :mystery)))))

(deftest debris-and-water-balance
  (testing "debris removal positive; effluent hands off to mizuho, never discharged"
    (let [seg {:id "s" :id-mm 300 :length-m 50.0 :material :vcp}
          d (jet/debris-removed-m3 seg 0.35)
          wb (jet/water-balance 60.0 30.0 0.7)]
      (is (pos? d))
      (is (= 1800.0 (:used-l wb)))
      (is (= :mizuho (:handoff wb)))          ; G2 — untreated effluent → mizuho
      (is (< (Math/abs (- (:effluent-l wb) 540.0)) 1e-6)))))   ; 30% of 1800

;; ── analyze + datom_emit (end-to-end over the seed) ──────────────────────────
(def seed (az/load-seed "20-actors/kudamori/data/network.edn"))

(deftest analyze-end-to-end
  (let [res (az/run seed)]
    (testing "the foul entry atmosphere is purged to a permitted entry (G5)"
      (is (true? (get-in res [:entry :permitted?])))
      (is (false? (get-in res [:entry :raw-safe?])))   ; raw reading was unsafe
      (is (pos? (get-in res [:entry :purge :minutes]))))
    (testing "navigation reaches the blocked target and jetting is pressure-safe"
      (is (= "seg-2-3" (get-in res [:navigation :target])))
      (is (true? (get-in res [:navigation :target-blocked?])))
      (is (pos? (get-in res [:jetting :debris-removed-m3])))
      (is (<= (get-in res [:jetting :pressure-bar]) (get-in res [:jetting :rating-bar]))))))

(deftest gated-when-atmosphere-unrecoverable
  (testing "★ G5 — if entry cannot be made safe, navigation + jetting are GATED"
    (let [bad (assoc seed :blower {:air-changes-per-min 0.0}      ; no ventilation
                          :gas-reading {:node "mh-entry" :o2-pct 5.0 :h2s-ppm 500.0
                                        :ch4-lel 80.0 :co-ppm 400.0})
          res (az/run bad)]
      (is (false? (get-in res [:entry :permitted?])))
      (is (= :gated (:navigation res)))
      (is (= :gated (:jetting res))))))

(deftest datom-emit-shape
  (let [res (az/run seed)
        out (de/emit seed res 1)]
    (testing "emits ground :add datoms + transient :derived readouts"
      (is (re-find #":kuda\.pipe/material" out))
      (is (re-find #":kuda\.node/h2s-ppm" out))
      (is (re-find #":kuda\.robot/kind" out))
      (is (re-find #":en/kind :cleans" out))
      (is (re-find #":bond/entry-permitted" out))
      (is (re-find #":derived\]" out))
      ;; well-formed EDN vector of datoms (load-bearing: must parse)
      (is (vector? (edn/read-string out))))))

(deftest datom-emit-gated-shape
  (testing "a gated run emits the gate datom and no cleans 縁"
    (let [bad (assoc seed :blower {:air-changes-per-min 0.0}
                          :gas-reading {:node "mh-entry" :o2-pct 5.0 :h2s-ppm 500.0
                                        :ch4-lel 80.0 :co-ppm 400.0})
          res (az/run bad)
          out (de/emit bad res 1)]
      (is (re-find #":bond/jetting-gated true" out))
      (is (not (re-find #":en/kind :cleans" out)))
      (is (vector? (edn/read-string out))))))

;; ── handoff (cross-actor chain edges: kudamori→mizuho effluent) ──────────────
(deftest outbound-to-mizuho
  (testing "cleaned segments → mizuho wastewater-treatment intents, source-attributed"
    (let [hs (ho/outbound-handoff [{:segment-id "seg-1-2" :debris-m3 0.42 :effluent-l 540.0}
                                   {:segment-id "seg-2-3" :debris-m3 0.18 :effluent-l 360.0}])]
      (is (= 2 (count hs)))
      (is (every? #(= "kudamori" (:from-actor %)) hs))
      (is (every? #(= "mizuho" (:to-actor %)) hs))
      (is (= :effluent (:kind (first hs))))
      (is (= 0.42 (get-in (first hs) [:payload :debris-m3])))
      (is (= 540.0 (get-in (first hs) [:payload :effluent-l]))))))

(deftest effluent-handoff-single
  (testing "a single cleaned segment → one mizuho effluent handoff"
    (let [h (ho/effluent-handoff {:segment-id "seg-3-4" :debris-m3 0.25 :effluent-l 300.0})]
      (is (= "kudamori" (:from-actor h)))
      (is (= "mizuho" (:to-actor h)))
      (is (= :effluent (:kind h)))
      (is (= "seg-3-4" (get-in h [:payload :segment-id])))
      (is (= 300.0 (get-in h [:payload :effluent-l]))))))

(deftest handoff-provenance-gate
  (testing "G9 — an orphan handoff (no source/destination) RAISES"
    (is (thrown? clojure.lang.ExceptionInfo (ho/assert-handoff! {:id "x" :to-actor "mizuho"})))
    (is (thrown? clojure.lang.ExceptionInfo (ho/assert-handoff! {:id "x" :from-actor "kudamori"})))
    (is (= "kudamori" (:from-actor (ho/assert-handoff! {:id "x" :from-actor "kudamori" :to-actor "mizuho"}))))))

(deftest handoff-emit-shape
  (testing "emits well-formed EDN :handoff/* 縁 with actor provenance on every edge"
    (let [hs (ho/outbound-handoff [{:segment-id "seg-1-2" :debris-m3 0.42 :effluent-l 540.0}])
          out (ho/emit hs 1)]
      (is (re-find #":handoff/from-actor" out))
      (is (re-find #":handoff/to-actor" out))
      (is (re-find #"en\.handoff\.kudamori\.mizuho\." out))
      (is (vector? (edn/read-string out))))))

;; ── coverage (HONEST occupation sub-task map — G5 sourcing-honesty) ──────────
(deftest coverage-fraction-is-covered-over-total
  (testing "coverage fraction is in (0,1] and equals covered/total"
    (let [{:keys [total covered coverage]} (cov/report)]
      (is (pos? coverage))
      (is (<= coverage 1.0))
      (is (= coverage (/ (double covered) total))))))

(deftest coverage-gaps-are-exactly-the-uncovered
  (testing ":gaps is exactly the uncovered sub-tasks (now empty — 100% covered)"
    (let [{:keys [gaps coverage]} (cov/report)]
      (is (= (set gaps) (set (remove :covered? cov/sub-tasks))))
      (is (every? #(false? (:covered? %)) gaps))
      ;; the last two GAPs (root-cutting/relining) are now closed → 100% coverage
      (is (empty? gaps))
      (is (= 1.0 coverage)))))

(deftest coverage-covered-names-a-method
  (testing "every covered sub-task names a non-nil :method"
    (is (every? #(some? (:method %)) (filter :covered? cov/sub-tasks)))))

;; ── campaign (network-wide multi-segment cleaning planning) ──────────────────
(def network-segs
  [{:segment-id "seg-1-2" :blockage-risk 0.85 :last-cleaned-days 400 :access [10.0 0.0]}
   {:segment-id "seg-2-3" :blockage-risk 0.40 :last-cleaned-days 120 :access [10.0 12.0]}
   {:segment-id "seg-3-4" :blockage-risk 0.92 :last-cleaned-days 30  :access [25.0 12.0]}
   {:segment-id "seg-4-5" :blockage-risk 0.10 :last-cleaned-days 15  :access [25.0 0.0]}
   {:segment-id "seg-5-6" :blockage-risk 0.55 :last-cleaned-days 300 :access [40.0 5.0]}])

(deftest prioritize-ranks-higher-blockage-risk-first
  (testing "prioritize ranks high-blockage-risk segments ahead of low-risk ones"
    (let [ranked (camp/prioritize network-segs)]
      (is (= (count network-segs) (count ranked)))
      ;; scores are non-increasing high-first
      (is (apply >= (map :priority ranked)))
      ;; the lowest-risk, freshest segment lands last
      (is (= "seg-4-5" (:segment-id (last ranked))))
      ;; the highest-risk segment outranks the lowest-risk one
      (let [by-id (into {} (map (juxt :segment-id :priority) ranked))]
        (is (> (by-id "seg-3-4") (by-id "seg-4-5")))))))

(deftest campaign-tour-visits-each-once-positive-travel
  (testing "the tour visits each selected segment exactly once, travel is positive"
    (let [selected (filter #(>= (:blockage-risk %) 0.5) network-segs)
          {:keys [order travel-m]} (camp/campaign-tour selected)]
      (is (= (set (map :segment-id selected)) (set order)))
      (is (= (count selected) (count order)))   ; no duplicates / no drops
      (is (= (count order) (count (distinct order))))
      (is (pos? travel-m)))))

(deftest every-stop-rechecks-atmosphere-gate
  (testing "★ G5 — EVERY campaign stop carries :atmosphere-recheck-required true (no entry skips the gas gate)"
    (let [plan (camp/plan-campaign network-segs {:risk-threshold 0.5})]
      (is (seq (:stops plan)))
      (is (every? #(true? (:atmosphere-recheck-required %)) (:stops plan)))
      ;; and the per-entry gate is the real atmosphere assert (raises on unsafe air)
      (is (thrown? clojure.lang.ExceptionInfo
                   (atm/assert-entry! {:o2-pct 18.6 :h2s-ppm 22.0 :ch4-lel 14.0 :co-ppm 12.0}))))))

(deftest threshold-and-top-n-drop-low-priority
  (testing "risk-threshold and top-N selection drop low-priority segments"
    ;; risk threshold drops the 0.10 and 0.40 segments
    (let [thr (camp/plan-campaign network-segs {:risk-threshold 0.5})
          ids (set (map :segment-id (:stops thr)))]
      (is (= 3 (count (:stops thr))))
      (is (not (contains? ids "seg-4-5")))   ; 0.10 risk dropped
      (is (not (contains? ids "seg-2-3"))))  ; 0.40 risk dropped
    ;; top-N keeps only the N highest-priority
    (let [topn (camp/plan-campaign network-segs {:top-n 2})]
      (is (= 2 (count (:stops topn))))
      ;; the two highest-priority survive (seg-1-2 risk .85/stale, seg-3-4 risk .92)
      (is (= #{"seg-1-2" "seg-3-4"} (set (map :segment-id (:stops topn))))))))

;; ── inspection (in-pipe condition survey + PACP-like grading → campaign seam) ─
(def insp-survey
  [{:segment-id "seg-1-2"
    :observations [{:position-m 2.0 :defect-kind :roots    :severity 5}
                   {:position-m 7.5 :defect-kind :crack    :severity 2}]}
   {:segment-id "seg-2-3"
    :observations [{:position-m 4.0 :defect-kind :deposits :severity 3}]}
   {:segment-id "seg-3-4"
    :observations [{:position-m 1.0 :defect-kind :blockage :severity 5}
                   {:position-m 6.0 :defect-kind :fracture :severity 4}]}
   {:segment-id "seg-clean"
    :observations []}])

(deftest grade-is-max-severity-and-risk-in-unit-interval
  (testing "grade = max observed severity; blockage-risk stays in [0,1]"
    (let [g (insp/grade-segment "seg-3-4"
                                [{:position-m 1.0 :defect-kind :blockage :severity 5}
                                 {:position-m 6.0 :defect-kind :fracture :severity 4}])]
      (is (= 5 (:grade g)))                       ; max of {5,4}
      (is (= 2 (:defect-count g)))
      (is (<= 0.0 (:blockage-risk g) 1.0))
      ;; a full-severity outright blockage drives risk to the top of the range
      (is (= 1.0 (:blockage-risk g))))
    ;; every graded segment in a survey has a unit-interval risk
    (is (every? #(<= 0.0 (:blockage-risk %) 1.0) (insp/survey insp-survey)))))

(deftest survey-sorts-worst-first
  (testing "survey returns segments sorted worst-first (highest blockage-risk first)"
    (let [graded (insp/survey insp-survey)]
      (is (= (count insp-survey) (count graded)))
      ;; blockage-risk is non-increasing down the list
      (is (apply >= (map :blockage-risk graded)))
      ;; the outright-blockage segment is the worst → first
      (is (= "seg-3-4" (:segment-id (first graded))))
      ;; the clean segment is the best → last
      (is (= "seg-clean" (:segment-id (last graded)))))))

(deftest clean-segment-low-grade-low-risk
  (testing "a segment with no/low defects → low grade + low blockage-risk"
    (let [empty-g (insp/grade-segment "seg-clean" [])
          minor-g (insp/grade-segment "seg-minor"
                                      [{:position-m 3.0 :defect-kind :crack :severity 1}])]
      (is (= 1 (:grade empty-g)))                 ; no observations → sound grade 1
      (is (zero? (:blockage-risk empty-g)))
      (is (= 0 (:defect-count empty-g)))
      ;; a single low-severity structural crack: low grade, negligible blockage-risk
      (is (= 1 (:grade minor-g)))
      (is (< (:blockage-risk minor-g) 0.1)))))

(deftest survey-feeds-campaign-prioritize
  (testing "to-campaign-input output is accepted by campaign/prioritize (survey → prioritize)"
    (let [graded   (insp/survey insp-survey)
          meta-by  {"seg-1-2"   {:last-cleaned-days 400 :access [10.0 0.0]}
                    "seg-2-3"   {:last-cleaned-days 120 :access [10.0 12.0]}
                    "seg-3-4"   {:last-cleaned-days 30  :access [25.0 12.0]}
                    "seg-clean" {:last-cleaned-days 10  :access [40.0 5.0]}}
          camp-in  (insp/to-campaign-input graded meta-by)]
      ;; the adapted shape carries exactly the keys campaign/prioritize reads
      (is (every? #(every? (set (keys %)) [:segment-id :blockage-risk :last-cleaned-days :access])
                  camp-in))
      ;; the integration: prioritize runs without error over the inspection output
      (let [ranked  (camp/prioritize camp-in)
            by-id   (into {} (map (juxt :segment-id :priority) ranked))]
        (is (= (count camp-in) (count ranked)))
        (is (apply >= (map :priority ranked)))
        ;; the clean segment (no defects, recently cleaned) ranks LAST for cleaning
        (is (= "seg-clean" (:segment-id (last ranked))))
        ;; the worst-condition segment (the outright blockage) outranks the clean one
        (is (> (by-id "seg-3-4") (by-id "seg-clean"))))
      ;; and a full plan-campaign consumes it end-to-end
      (let [plan (camp/plan-campaign camp-in {:risk-threshold 0.5})]
        (is (seq (:stops plan)))
        (is (every? #(true? (:atmosphere-recheck-required %)) (:stops plan)))))))

;; ── rootcut (★ G7 — no pipe over-torque; root/obstruction cutting) ────────────
(deftest denser-roots-need-more-passes
  (testing "passes-needed rises monotonically with root density"
    (let [cutter {:id "cut-head-01"}
          light  (rc/plan-cut {:root-density 0.2 :pipe-diameter-mm 300 :pipe-material :ductile-iron} cutter)
          heavy  (rc/plan-cut {:root-density 0.8 :pipe-diameter-mm 300 :pipe-material :ductile-iron} cutter)]
      (is (> (:passes-needed heavy) (:passes-needed light)))
      (is (pos? (:passes-needed light)))
      (is (= 0 (rc/passes-needed 0.0))))))           ; no roots → no passes

(deftest cut-over-torque-raises
  (testing "★ G7 — a small/weak pipe choked with dense roots over-torques and RAISES"
    (let [cutter {:id "cut-head-01"}]
      ;; dense roots in a wide bore on weak PVC: required torque blows past the limit
      (is (thrown? clojure.lang.ExceptionInfo
                   (rc/plan-cut {:root-density 0.9 :pipe-diameter-mm 300 :pipe-material :pvc} cutter)))
      ;; an unknown material has no torque limit → conservative raise
      (is (thrown? clojure.lang.ExceptionInfo (rc/torque-limit-for :mystery))))))

(deftest cut-within-torque-limit-plans
  (testing "a modest cut inside the material's torque limit plans cleanly"
    (let [cutter {:id "cut-head-01"}
          plan   (rc/plan-cut {:root-density 0.3 :pipe-diameter-mm 150 :pipe-material :ductile-iron} cutter)]
      (is (<= (:required-torque-nm plan) (:torque-limit-nm plan)))
      (is (pos? (:passes-needed plan)))
      (is (= :ductile-iron (:material plan))))))

;; ── relining (trenchless CIPP / spot repair; honest-refusal on collapse-grade) ─
(deftest liner-thickness-scales-with-diameter
  (testing "liner thickness grows with host diameter"
    (let [thin  (rl/plan-reline {:pipe-diameter-mm 150 :defect-severity 3 :host-condition 2})
          thick (rl/plan-reline {:pipe-diameter-mm 600 :defect-severity 3 :host-condition 2})]
      (is (> (:liner-thickness-mm thick) (:liner-thickness-mm thin)))
      (is (= :cipp (:method thin))))))

(deftest reline-cure-time-positive
  (testing "cure time is positive and follows from liner thickness"
    (let [plan (rl/plan-reline {:pipe-diameter-mm 300 :defect-severity 4 :host-condition 3})]
      (is (pos? (:cure-time-min plan)))
      (is (pos? (:liner-thickness-mm plan))))))

(deftest collapse-grade-host-raises
  (testing "★ a collapse-imminent (grade 5) host is NOT relinable → RAISES (needs replacement)"
    (is (not (rl/relinable? 5)))
    (is (rl/relinable? 4))
    (is (thrown? clojure.lang.ExceptionInfo
                 (rl/plan-reline {:pipe-diameter-mm 300 :defect-severity 4 :host-condition 5})))))

;; ── run-day full-pipeline integration (R1: all 8 domain methods compose) ──────
;; via az/load-seed (not a raw edn/read-string+slurp) so this tolerates the
;; datomic/datascript tx-data shape the same way `seed` above does (Phase 4 edn-datomize).
(def day-seed
  (az/load-seed "20-actors/kudamori/data/network.edn"))

(deftest run-day-exercises-all-domain-methods
  (testing "run-day threads the day through ALL 8 domain methods end-to-end"
    (let [res (az/run-day day-seed)
          ms  (:methods res)]
      ;; the set of exercised modules ⊇ the 8 domain methods
      (is (every? ms #{"inspection" "campaign" "atmosphere" "pipe_nav"
                       "jetting" "rootcut" "relining" "handoff"}))
      (is (>= (count ms) 8))
      ;; base `run` keys are preserved (datom_emit depends on them)
      (is (contains? res :entry))
      (is (contains? res :navigation))
      (is (contains? res :jetting))
      ;; new run-day keys present
      (is (vector? (:pipeline res)))
      (is (map? (:day res)))
      ;; ★ G5 — the atmosphere entry gate stayed a REAL gate (re-checked, passed)
      (is (true? (get-in res [:day :atmosphere :permitted?]))))))

(deftest run-day-report-lists-pipeline
  (testing "report-day-str lists the methods exercised and each pipeline method name"
    (let [res (az/run-day day-seed)
          rpt (az/report-day-str res)]
      (is (re-find #"methods exercised" rpt))
      (doseq [m ["inspection" "campaign" "atmosphere" "pipe_nav"
                 "jetting" "rootcut" "relining" "handoff"]]
        (is (re-find (re-pattern m) rpt))))))

;; ── datom_emit-day (the canonical log captures the FULL run-day) ──────────────
(deftest datom-emit-day-captures-full-day
  (testing "emit-day projects the whole run-day (handoff 縁 + kudamori-specific day attrs), is a strict superset of emit, and parses as EDN"
    (let [day-res (az/run-day seed)
          base    (az/run seed)
          out-day (de/emit-day seed day-res 1)
          out     (de/emit seed base 1)]
      ;; the cross-actor handoff 縁 (same shape as kuramori): provenance + edge id
      (is (re-find #":handoff/from-actor" out-day))
      (is (re-find #"en\.handoff\.kudamori\.mizuho\." out-day))
      ;; at least one kudamori-specific run-day attr the base emit never carries
      (is (re-find #":kuda\.inspect/grade" out-day))
      ;; a DERIVED day metric
      (is (re-find #":bond/inspection-segments" out-day))
      ;; well-formed EDN vector (load-bearing: must parse)
      (let [v-day (edn/read-string out-day)
            v     (edn/read-string out)]
        (is (vector? v-day))
        (is (vector? v))
        ;; strict superset of the base emit: every base datom is present + there are MORE
        (is (> (count v-day) (count v)))
        (is (every? (set v-day) v))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'kudamori.methods.test-kudamori)]
    (System/exit (if (pos? (+ fail error)) 1 0))))

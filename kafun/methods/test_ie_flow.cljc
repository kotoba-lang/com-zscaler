#!/usr/bin/env bb
;; kafun 花粉 — ie-flow embedding tests (the energy-flow SoS leg).
;; Run:  bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" 20-actors/kafun/methods/test_ie_flow.cljc
(ns kafun.methods.test-ie-flow
  (:require [kafun.methods.kafun-edn :as ke]
            [kafun.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.embed :as embed]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/kafun/kotoba/seed.edn")
(defn- ss [] (ke/stands seed-path))

;; ── measurement: kafun assessment → well-formed ie-flow events ───────────────

(deftest events-well-formed
  (let [evs (ief/flow-events (ss))]
    (is (= 12 (count evs)) "one event per stand")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? #(:agent? %) evs) "kafun is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "kafun" (:actor %)) evs))))

;; ── the order calculus (folded through the SHARED ie-flow metrics) ───────────

(deftest order-is-added-and-flow-pays
  (let [st (ief/flow-state (ss))]
    (is (pos? (:order-index st)) "kafun RECTIFIES scattered burden → positive order-index")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — kafun returns more order than it consumes (共生)")))

(deftest eta-export-exceeds-consume
  (let [m (:metrics (ief/viz-model (ss)))]
    (is (> (:eta m) 1.0) "η = exported ÷ consumed ≫ 1 (a 利得, not a 課金される魔法陣)")
    (is (< (:h-after m) (:h-before m)) "entropy DROPS — scattered flow concentrated onto outcomes")))

;; ── value model: restoration concentrates; refuse exports protective-only ────

(deftest restoration-concentrates-refuse-exports-zero
  (let [evs (into {} (map (juxt :type identity) (ief/flow-events (ss))))
        ;; pick representative events by type
        by-type (group-by :type (ief/flow-events (ss)))]
    (is (every? #(zero? (:value %)) (get by-type "refuse"))
        "refuse is PROTECTIVE — exports 0 restoration-energy (disorder prevented, shown structurally)")
    (is (every? #(pos? (:value %)) (get by-type "reforest-priority"))
        "reforest-priority delivers realised restoration order")))

;; ── viz model: the 4-column SoS pipeline is complete + linked ────────────────

(deftest viz-model-is-a-system-of-systems
  (let [m (ief/viz-model (ss))
        col-ids (set (map :id (:columns m)))
        actor-ids (set (map :id (get-in m [:columns 3 :nodes])))]
    (is (= #{"sources" "gate" "routes" "sos"} col-ids) "4 columns: stands → gate → routes → downstream")
    (is (seq (:links m)) "flow links present")
    (is (contains? actor-ids "actor:sanae") "reforest-priority feeds sanae (planting robotics)")
    (is (contains? actor-ids "actor:inochi") "feeds inochi (biosphere restoration)")
    ;; every link endpoint resolves to a declared node
    (let [node-ids (set (concat (map :id (mapcat :nodes (:columns m)))))]
      (is (every? #(or (contains? node-ids (:from %)) (= (:from %) (get-in m [:gate :id]))) (:links m)))
      (is (every? #(or (contains? node-ids (:to %)) (= (:to %) (get-in m [:gate :id]))) (:links m))))))

(deftest html-is-self-contained
  (let [html (ief/render-html (ief/viz-model (ss)))]
    (is (clojure.string/includes? html "<canvas"))
    (is (clojure.string/includes? html "order-index"))
    (is (clojure.string/includes? html "system of systems"))
    (is (not (clojure.string/includes? html "http://")) "no external fetch — fully self-contained")))

;; ── record-flow! → the shared ie-flow ledger (the heartbeat/tool record! leg) ─

(deftest record-flow-roundtrips-to-the-shared-ledger
  (let [actor "kafun-test-record"
        log (str "80-data/ie-flow/" actor "/flow.kotoba.edn")
        del! (fn [] (let [f (io/file log)] (when (.exists f) (.delete f))
                      (let [d (io/file (str "80-data/ie-flow/" actor))]
                        (when (.exists d) (.delete d)))))]
    (del!)
    ;; record kafun's events under a throwaway actor id (real path is "kafun")
    (embed/record! actor (ief/flow-events (ss)) {:tx-id "t" :as-of "beat"})
    (let [st (embed/measure actor)]
      (is (= 12 (:flows-n st)) "all 12 stand→route events recorded + measured back")
      (is (> (:order-index st) 0.0) "the recorded ledger reproduces kafun's rectification order")
      (is (not (:parasitic? st)) "non-parasitic on the recorded ledger"))
    (del!)))

(deftest record-flow!-returns-ledger-summary
  (let [r (ief/record-flow! (ss) {:tx-id "t2" :as-of "beat"})]
    (is (= 12 (:events r)))
    (is (clojure.string/ends-with? (:flow-log r) "80-data/ie-flow/kafun/flow.kotoba.edn"))
    (is (number? (:order-index r)))
    ;; cleanup the real kafun ledger this test wrote (gitignored, but keep the tree clean)
    (let [f (io/file (:flow-log r))] (when (.exists f) (.delete f))
      (let [d (.getParentFile f)] (when (.exists d) (.delete d))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kafun.methods.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))

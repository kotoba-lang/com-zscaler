(ns kaname.tests.test-energy-join
  "kaname 要 — :energy domain layer tests (ADR-2606212000). Proves the system-of-systems
  energy-flow layer composes: amime 網目's COMMITTED mesh output (20-actors/amime/out/
  energy-sos.kotoba.edn) lifts into kaname's :energy domain, and kaname computes leverage
  natively over it. Running amime to (re)produce its output = G7; joining the committed
  output = what kaname does — exactly as for the chie/tsumugi/… mirrors."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            #?(:clj [clojure.java.io :as io])
            [kaname.methods.sos :as sos]
            [kaname.methods.join :as join]))

#?(:clj (def amime-out "20-actors/amime/out/energy-sos.kotoba.edn"))

(deftest energy-is-a-domain-layer
  (testing ":energy is an explicit multiplex layer; D grew 10→11"
    (is (some #{":energy"} sos/domains))
    (is (= 11 sos/D))))

(deftest amime-adapter-registered
  (testing "the :amime mirror adapter targets the energy mesh output in the :energy domain"
    (let [a (get join/mirror-adapters :amime)]
      (is (= ":energy" (:domain a)))
      (is (= ":amime" (:source a)))
      (is (= "amime/out/energy-sos.kotoba.edn" (:path a))))))

#?(:clj
   (deftest amime-output-lifts-into-energy-layer
     (testing "amime's committed mesh graph lifts into the :energy layer; flow → concentration"
       (let [g (join/read-graph amime-out)
             lifted (join/lift g ":energy" ":amime" (:kind-map (get join/mirror-adapters :amime)))
             edges (filter #(contains? % ":en/from") lifted)
             nodes (filter #(contains? % ":organism/id") lifted)
             graph (join/forms->graph lifted)
             res (sos/leverage (:nodes graph) (:edges graph))]
         (is (seq edges) "energy mesh produced 縁")
         (is (= 6 (count nodes)) "six sites lifted as :sos/entity nodes")
         (is (every? #(= ":energy" (get % ":en/domain")) edges) "every lifted 縁 is in :energy")
         (is (some #(= ":concentrates" (get % ":en/kind")) edges))
         ;; the datacenter (the largest importer) accumulates the most energy-flow concentration
         (let [kp (sos/kaname-point res (:nodes graph))]
           (is (= "amime/dc-load-d" (first kp)) "the datacenter is the energy-layer 要 (largest 取)")
           (is (> (nth kp 2) 0.0)))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kaname.tests.test-energy-join)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))

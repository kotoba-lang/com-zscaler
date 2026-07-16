(ns tsukuroi.methods.test-manifest-invariants
  "tsukuroi — manifest invariants (ported; reads manifest.edn blob, jsonld retired)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str] [clojure.edn :as edn]))
(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root "00-contracts/lexicons/com/etzhayyim/tsukuroi"))
(defn- manifest [] (:actor/manifest (edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
(defn- on-disk [] (->> (.listFiles lexdir) (map #(.getName ^java.io.File %)) (filter #(str/ends-with? % ".json")) (map #(subs % 0 (- (count %) 5))) set))
(deftest thirteen-gates-present
  (is (= (set (keys (get-in (manifest) ["constitutionalGates" "gates"]))) (set (map #(str "G" %) (range 1 14)))) "must pin exactly G1..G13"))
(deftest namespaces-match-disk-lexicons-bidirectionally
  (is (= (set (map #(last (str/split % #"\.")) (get (manifest) "lexiconNamespaces"))) (on-disk))))
(deftest did-name-tier
  (let [m (manifest)]
    (is (= (get m "id") "did:web:tsukuroi.etzhayyim.com"))
    (is (= (get m "name") "tsukuroi"))
    (is (= (get m "tier") "Tier-B"))))
(defn -main [& _] (let [r (run-tests 'tsukuroi.methods.test-manifest-invariants)] (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1))))

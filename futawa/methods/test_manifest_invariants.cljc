(ns futawa.methods.test-manifest-invariants
  "futawa — manifest invariants (ported; reads manifest.edn blob, jsonld retired)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str] [clojure.edn :as edn]))
(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root "00-contracts/lexicons/com/etzhayyim/futawa"))
(defn- manifest [] (:actor/manifest (edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
(defn- on-disk [] (->> (.listFiles lexdir) (map #(.getName ^java.io.File %)) (filter #(str/ends-with? % ".json")) (map #(subs % 0 (- (count %) 5))) set))
(deftest manifest-namespaces-match-disk
  (let [m (manifest)
        declared (set (map #(last (str/split % #"\.")) (or (get m "lexiconNamespaces") (get m "lexicons") [])))]
    (is (= declared (on-disk)))))
(deftest did
  (is (= (get (manifest) "id") "did:web:etzhayyim.com:futawa")))
(defn -main [& _] (let [r (run-tests 'futawa.methods.test-manifest-invariants)] (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1))))

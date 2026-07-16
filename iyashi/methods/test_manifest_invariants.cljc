(ns iyashi.methods.test-manifest-invariants
  "iyashi — manifest ↔ lexicon-disk invariants (ported from the manifest-reading half of
  70-tools/scripts/audit/test_iyashi_invariants.py). Reads manifest.edn (:actor/manifest
  blob); the jsonld is retired. Lexicon-hygiene checks stay in the Python audit suite."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.edn :as edn]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root "00-contracts/lexicons/com/etzhayyim/iyashi"))

(defn- manifest [] (:actor/manifest (edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))
(defn- on-disk []
  (->> (.listFiles lexdir) (map #(.getName ^java.io.File %))
       (filter #(str/ends-with? % ".json"))
       (map #(subs % 0 (- (count %) 5))) set))

(deftest manifest-namespaces-match-disk
  (let [declared (set (map #(last (str/split % #"\.")) (get (manifest) "lexiconNamespaces")))]
    (is (= declared (on-disk))
        (str "manifest namespaces vs disk drifted: " declared " / " (on-disk)))))

(deftest did-name-tier
  (let [m (manifest)]
    (is (= (get m "id") "did:web:iyashi.etzhayyim.com"))
    (is (= (get m "name") "iyashi"))))

(defn -main [& _]
  (let [r (run-tests 'iyashi.methods.test-manifest-invariants)]
    (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1))))

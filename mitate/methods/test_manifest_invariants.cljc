(ns mitate.methods.test-manifest-invariants
  "mitate — manifest ↔ lexicon-disk invariants (ported from the manifest-reading
  half of 70-tools/scripts/audit/test_mitate_invariants.py). Reads manifest.edn
  (:actor/manifest blob); the jsonld is retired. The PHI-encryption + lexicon-hygiene
  invariants stay in the Python audit suite (they read lexicon JSONs, not the manifest)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn]))

(def ^:private here (.getParentFile (java.io.File. ^String *file*)))
(def ^:private actor-dir (.getParentFile here))
(def ^:private root (.. actor-dir getParentFile getParentFile))
(def ^:private lexdir (java.io.File. root "00-contracts/lexicons/com/etzhayyim/mitate"))

(defn- manifest [] (:actor/manifest (edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))

(defn- declared []
  (let [m (manifest)]
    (->> (concat (when (sequential? (get m "lexicons")) (get m "lexicons"))
                 (when (sequential? (get m "lexiconNamespaces")) (get m "lexiconNamespaces")))
         (filter string?)
         (map #(last (str/split % #"\.")))
         set)))

(defn- on-disk []
  (->> (.listFiles lexdir)
       (map #(.getName ^java.io.File %))
       (filter #(str/ends-with? % ".json"))
       (map #(subs % 0 (- (count %) 5)))
       set))

(deftest every-disk-lexicon-is-declared
  (is (set/subset? (on-disk) (declared))
      (str "undeclared mitate lexicon(s) on disk (orphan): " (set/difference (on-disk) (declared)))))

(deftest diagnostic-consent-receipt-declared
  (is (contains? (declared) "diagnosticConsentReceipt")))

(deftest no-phantom-declaration
  (doseq [stem (declared)]
    (is (contains? (on-disk) stem)
        (str "manifest declares " (pr-str stem) " but no JSON file exists"))))

(defn -main [& _]
  (let [r (run-tests 'mitate.methods.test-manifest-invariants)]
    (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1))))

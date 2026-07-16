(ns noroshi.methods.test-governance
  "Governance / honest-framing tests for noroshi (烽) — ADR-2606051600 (item 4).
  1:1 Clojure port of methods/test_governance.py (pytest → clojure.test).

  Locks the charter-substrate boundary + report honesty. The Python forbidden-import scan
  globs **/*.py (impl only); here it scans the sibling **/*.cljc impl files (test_* excluded),
  the faithful equivalent for the Clojure port. report() honesty markers are checked on the
  6 method modules."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [noroshi.methods._edn :as edn]
            [noroshi.methods.link-budget :as link-budget]
            [noroshi.methods.isac-sim :as isac-sim]
            [noroshi.methods.active-alignment :as active-alignment]
            [noroshi.methods.cable-endpoint :as cable-endpoint]
            [noroshi.methods.kami-isac-bridge :as kami-isac-bridge]
            [noroshi.methods.pic-layout :as pic-layout]
            [noroshi.methods.device-design :as device-design]
            [noroshi.methods.reliability-qual :as reliability-qual]))

#?(:clj (def ^:private actor-root (-> (java.io.File. *file*) .getParentFile .getParentFile)))
#?(:clj (defn- manifest [] (edn/load-edn (java.io.File. actor-root "manifest.edn"))))
#?(:clj (defn- claude-md [] (slurp (java.io.File. actor-root "CLAUDE.md"))))

;; ── doc consistency: every gate/non-goal is documented ──────────────────────────
(deftest test-every-gate-appears-in-actor-claude-md
  (let [claude (claude-md)
        missing (filterv #(not (str/includes? claude %))
                         (map #(get % ":gate/id") (get (manifest) ":actor/gates")))]
    (is (empty? missing) (str "gates missing from CLAUDE.md: " missing))))

(deftest test-every-non-goal-appears-in-actor-claude-md
  (let [claude (claude-md)
        missing (filterv #(not (str/includes? claude %))
                         (map #(get % ":ng/id") (get (manifest) ":actor/non-goals")))]
    (is (empty? missing) (str "non-goals missing from CLAUDE.md: " missing))))

;; ── substrate boundary: stdlib + Murakumo-only ──────────────────────────────────
(def ^:private forbidden-imports
  ["risingwave" "psycopg" "kysely" "sqlalchemy"
   "require openai" "openai" "runpod" "boto3" "vertexai"
   "require anthropic" "anthropic"])

#?(:clj
   (def ^:private impl-cljc
     (->> (file-seq actor-root)
          (filter #(and (.isFile %) (str/ends-with? (.getName %) ".cljc")
                        (not (str/starts-with? (.getName %) "test_"))))
          (sort-by #(.getName %)))))

(deftest test-no-forbidden-substrate-or-inference-import
  (doseq [py impl-cljc]
    (let [text (str/lower-case (slurp py))
          hits (filterv #(str/includes? text %) forbidden-imports)]
      (is (empty? hits) (str (.getName py) " references forbidden substrate/inference: " hits " (G6/G9)")))))

;; ── honest framing: each report() carries an R0/R1 honesty marker ────────────────
(def ^:private honest-markers
  ["r0" "g7" "g8" ":representative" "honest" "no live" "no robot"
   "no foundry" "unpopulated" "gated" "simulation only"])

(deftest test-report-carries-honest-framing
  (doseq [[name report-fn] [["link_budget" link-budget/report]
                            ["isac_sim" isac-sim/report]
                            ["active_alignment" active-alignment/report]
                            ["cable_endpoint" cable-endpoint/report]
                            ["kami_isac_bridge" kami-isac-bridge/report]
                            ["pic_layout" pic-layout/report]
                            ["device_design" device-design/report]
                            ["reliability_qual" reliability-qual/report]]]
    (let [text (str/lower-case (report-fn))]
      (is (some #(str/includes? text %) honest-markers)
          (str name ".report() lost its honesty disclaimer")))))

#?(:clj (defn -main [& _] (run-tests 'noroshi.methods.test-governance)))

(ns tanemaki.tests.test-wasm
  "tanemaki 種蒔き — WASM component entry tests (ADR-2606122001). Pure, NETWORK-FREE.
  1:1 Clojure port of tests/test_wasm.py.

  The Python test imports `wasm/app.py` (the componentize-py entry) and exercises its five
  exports. `wasm/app.py` is NOT in this port's scope (it is the WASM glue, not a method module),
  so the five export functions are reconstructed HERE as `app-*` helpers that call the real
  tanemaki.methods.* siblings exactly as app.py does — keeping the test self-contained without
  creating an unlisted .cljc. The JSON exports are serialized with cheshire (json.dumps analogue,
  ensure_ascii=False ⇒ raw UTF-8) and re-parsed for the assertions, mirroring `json.loads(...)`.

  The __main__ runner is intentionally omitted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [tanemaki.methods.analyze :as analyze]
            [tanemaki.methods.datom-emit :as datom-emit]
            [tanemaki.methods.coverage-report :as coverage]
            [tanemaki.methods.propose :as propose]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-stewardship-graph.kotoba.edn"))

(defn- load* [] (analyze/load-file* seed))

;; ── wasm/app.py export reconstruction (calls the real siblings, 1:1 with app.py) ──────────

(defn app-analyze
  "app.analyze(): JSON {orgs (per-org route/dd_fit/coverage/conflicts/undetermined/synthetic),
  criteria, decidedBy}."
  []
  (let [{:keys [nodes edges]} (load*)
        res  (analyze/analyze nodes edges)
        orgs (reduce (fn [m oid]
                       (let [r (get-in res ["orgs" oid])]
                         (assoc m oid {"route" (get r "route")
                                       "dd_fit" (get r "dd_fit")
                                       "evidence_coverage" (get r "evidence_coverage")
                                       "conflicts" (get r "conflicts")
                                       "undetermined" (get r "undetermined")
                                       "synthetic" (get r "synthetic")})))
                     {} (keys (get res "orgs")))]
    (json/generate-string {"orgs" orgs "criteria" (get res "criteria")
                           "decidedBy" "1-sbt-1-vote"})))

(defn app-datoms
  ([] (app-datoms 1))
  ([tx]
   (let [{:keys [nodes edges]} (load*)]
     (datom-emit/emit nodes edges (analyze/analyze nodes edges) (long tx)))))

(defn app-coverage []
  (let [{:keys [nodes edges]} (load*)]
    (coverage/report nodes edges)))

(defn app-scorecard [org-id]
  (let [{:keys [nodes edges]} (load*)]
    (propose/render-scorecard org-id nodes edges)))

(defn app-propose [org-id amount-usdc-micros instrument justification]
  (let [{:keys [nodes edges]} (load*)]
    (try
      (json/generate-string
       (propose/build-proposal org-id nodes edges
                               {:amount-usdc-micros (long amount-usdc-micros)
                                :instrument (if (str/blank? instrument) ":grant" instrument)
                                :justification (or justification "")}))
      (catch Exception ex
        (json/generate-string {"$type" "com.etzhayyim.tanemaki.ddScorecard"
                               "orgId" org-id "refused" true
                               "reason" (or (.getMessage ex) "")})))))

;; ── tests ─────────────────────────────────────────────────────────────────────────────────

(deftest test-analyze-export-shape
  (let [out (json/parse-string (app-analyze))]
    (is (= #{"orgs" "criteria" "decidedBy"} (set (keys out))))
    (is (= "1-sbt-1-vote" (get out "decidedBy"))) ;; G1 surfaces in the export itself
    ;; G1 in the WASM export: no conflicted org is proposable
    (doseq [[oid r] (get out "orgs")]
      (when (seq (get r "conflicts"))
        (is (= ":excluded" (get r "route")) (str oid " conflicted but " (get r "route"))))
      (is (= true (get r "synthetic")))))) ;; G6

(deftest test-datoms-export-is-eavt-edn
  (let [edn (app-datoms 7)]
    (is (and (str/starts-with? (str/triml edn) ";;") (str/includes? edn " 7 :add]")))
    (is (str/includes? edn ":bond/is-transient true"))))

(deftest test-coverage-export-is-markdown
  (let [md (app-coverage)]
    (is (and (str/starts-with? md "# tanemaki") (str/includes? md "holds for all orgs")))))

(deftest test-scorecard-export-is-advisory
  (let [md (app-scorecard "org.osslib")]
    (is (and (str/includes? md "参考意見") (str/includes? md "1 SBT = 1 vote")
             (str/includes? md "FICTIONAL")))))

(deftest test-propose-export-refuses-excluded-org
  (let [out (json/parse-string (app-propose "org.surveil-vendor" 0 ":grant" ""))]
    (is (and (= true (get out "refused")) (str/includes? (get out "reason") "REFUSAL")))))

(deftest test-propose-export-refuses-investment-instrument
  (let [out (json/parse-string (app-propose "org.foodbank" 0 ":equity" ""))]
    (is (and (= true (get out "refused")) (str/includes? (get out "reason") "G2")))))

(deftest test-propose-export-builds-advisory-record
  (let [out (json/parse-string (app-propose "org.foodbank" 5000000000 ":grant" "食料再分配 commons"))]
    (is (= "com.etzhayyim.tanemaki.grantProposal" (get out "$type")))
    (is (and (= true (get out "advisory")) (= false (get out "bindsFund"))))
    (is (and (= "drafted-unsent" (get out "status")) (= "1-sbt-1-vote" (get out "decidedBy"))))))

(deftest test-exports-deterministic
  (is (= (app-analyze) (app-analyze)))
  (is (= (app-datoms 1) (app-datoms 1))))

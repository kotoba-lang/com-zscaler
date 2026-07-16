(ns shionome.methods.test-charter-invariants
  "Cross-language oracle tests for 潮目 THREE-PLACE invariant consistency (the charter tripwire).
  1:1 port of methods/test_charter_invariants.py. ADR-2606072200.

  Every structural invariant lives in three homes — the ontology :db/allowed / closed-vocab
  vectors, the lexicon :const / :enum, and the (now Clojure) weave constants. This suite parses
  all three and asserts they AGREE; adding a trade-bearing flow kind / private bucket scope /
  per-bucket rating / :published post status in ONE place fails here."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [shionome.methods.edn :as edn]
            [shionome.methods.weave :as weave]))

(def onto-path "00-contracts/schemas/capital-flow-ontology.kotoba.edn")
(def lex-dir "20-actors/shionome/lex")
(defn- onto [] (edn/load-edn onto-path))
(defn- lex [n] (edn/load-edn (str lex-dir "/" n)))
(defn- bare [x] (str/replace (str x) #"^:+" ""))
(defn- bare-vec [xs] (mapv bare xs))

;; ── ontology ↔ clojure vocab agreement ──────────────────────────────────────────
(deftest bucket-scopes-agree
  (is (= weave/BUCKET-SCOPES (bare-vec (get (onto) ":ontology/bucket-scopes")))))

(deftest flow-kinds-agree
  (is (= weave/FLOW-KINDS (bare-vec (get (onto) ":ontology/flow-kinds")))))

(deftest snapshot-metrics-agree
  (is (= weave/SNAPSHOT-METRICS (bare-vec (get (onto) ":ontology/snapshot-metrics")))))

;; ── no-trade invariant (G2 / トレードはしない) ──────────────────────────────────
(deftest no-trade-token-in-ontology-flow-kinds
  (doseq [k (bare-vec (get (onto) ":ontology/flow-kinds"))]
    (doseq [t weave/TRADE-TOKENS]
      (is (not (str/includes? k t)) (str "trade token " t " in flow kind " k)))))

(deftest no-person-scope-in-ontology
  (let [forbidden #{"individual" "person" "account" "portfolio" "trader" "investor"}]
    (is (empty? (filter forbidden (bare-vec (get (onto) ":ontology/bucket-scopes")))))))

;; ── G8 — post statuses are dry-run only ──────────────────────────────────────────
(deftest post-statuses-dry-run-only
  (is (= ["dry-run"] (bare-vec (get (onto) ":ontology/post-statuses")))))

;; ── G4 — ontology declares NO per-bucket rating/signal/target/score attr ──────────
(deftest ontology-has-no-bucket-score-attr
  (let [idents (set (map #(get % ":db/ident") (get (onto) ":schema")))]
    (doseq [forbidden [":bucket/rating" ":bucket/signal" ":bucket/target"
                       ":bucket/score" ":bucket/recommendation"]]
      (is (not (contains? idents forbidden)) forbidden))))

;; ── lexicon consistency ──────────────────────────────────────────────────────────
(defn- props [n] (get-in (lex n) [":defs" ":main" ":record" ":properties"]))

(deftest networkpost-status-const-dry-run
  (is (= "dry-run" (get-in (props "networkPost.edn") [":status" ":const"]))))

(deftest networkpost-is-mirror-const-true
  (is (= true (get-in (props "networkPost.edn") [":isMirror" ":const"]))))

(deftest networkpost-no-trade-const-true
  (is (= true (get-in (props "networkPost.edn") [":noTradeNotice" ":const"]))))

(deftest networkpost-server-held-key-const-false
  (is (= false (get-in (props "networkPost.edn") [":serverHeldKey" ":const"]))))

(deftest capitalflow-kind-enum-matches-flow-kinds
  (is (= weave/FLOW-KINDS (vec (get-in (props "capitalFlowObservation.edn") [":kind" ":enum"])))))

(deftest capitalflow-no-trade-token-in-enum
  (doseq [k (get-in (props "capitalFlowObservation.edn") [":kind" ":enum"])]
    (doseq [t weave/TRADE-TOKENS]
      (is (not (str/includes? (str k) t))))))

(deftest capitalflow-no-trade-notice-const
  (is (= true (get-in (props "capitalFlowObservation.edn") [":noTradeNotice" ":const"]))))

(deftest capitalflow-sources-min-two
  (is (= 2 (get-in (props "capitalFlowObservation.edn") [":sources" ":minLength"]))))

(deftest bucketsnapshot-metric-enum-matches
  (is (= weave/SNAPSHOT-METRICS (vec (get-in (props "bucketSnapshot.edn") [":metric" ":enum"])))))

(deftest rotationfinding-no-trade-const
  (is (= true (get-in (props "rotationFinding.edn") [":noTradeNotice" ":const"]))))

(deftest all-four-lexicons-present
  (doseq [n ["capitalFlowObservation.edn" "bucketSnapshot.edn" "rotationFinding.edn" "networkPost.edn"]]
    (is (.exists (java.io.File. (str lex-dir "/" n))))))

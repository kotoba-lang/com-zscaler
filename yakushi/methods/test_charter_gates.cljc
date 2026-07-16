(ns yakushi.methods.test-charter-gates
  "yakushi 薬師 — constitutional-gate conformance tests (manifest + local FIRST-TIER lexicons).

  Substrate-native Clojure (clj + datomic first tier). yakushi is OTC-only pharmaceutical
  manufacturing (eye-drops + perpetually-off-patent OTC APIs) — NOT prescription Rx, NOT a
  controlled substance, NOT a commercial sale model. It reads the first-tier `lex/*.edn`
  (datomic-native) via clojure.edn and the manifest via cheshire. Its G1..G14 gates (master
  charter §Decision 3) are declared in the manifest and encoded structurally as required-fields
  + by-absence across the 8 local lexicons. This suite pins them so a future R-phase cell wave
  cannot silently drift them:

    G9  witness invariant N≥2 — apiSynthesis (witness1+witness2), fillFinish (operator+QP witness)
    G4  QP-equivalent co-sign per lot — qcAttestation.qpDid; fillFinish witnessQp
    G10 patient identity non-traceable — adverseEventReport keyed by lot+severity+outcome,
        carries NO patient DID / name field
    G7  CWC dual-use precursor monitoring — rawMaterialAttestation hazardClass + cwcStatus
    G8  sterile process validation — fillFinishAttestation.sterileProcess required
    G3/G5 silen-pharma-review (Council ≥3) + adverse-event public reporting
    G12 no commercial sale model — settlement carries a tithe (10% Public Fund) + a buyer
        (member) signature ref; the maker payout is in-kind/grant, not a sale margin

  Reads local lexicons via clojure.edn. It weakens no gate; it asserts them. The no-server-key
  (G13: hardware-token/passkey QP key only) + Murakumo-only (manifest) invariants are untouched."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.edn :as edn]
            [cheshire.core :as json]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; yakushi/
     (def ^:private lexdir (java.io.File. actor-dir "lex"))
     (defn- unblob
       "lex/*.edn entries were datomized (ADR: EDN datomize fan-out) — non-scalar
       values (e.g. :defs) are pr-str'd blob strings. Parse back to a coll if so;
       leave genuinely-scalar strings (:id) untouched."
       [v]
       (if (string? v)
         (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
              (catch Exception _ v))
         v))
     (defn- reconstitute-entity
       "Reconstitutes the pre-datomize bare map ({:lexicon .. :id .. :defs ..})
       from a datomized tx-data entity ([{:db/id -1 :lex.<name>/lexicon .. ...}])
       so downstream key lookups (:defs / :id / :lexicon) keep working unchanged."
       [tx-data]
       (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
             (dissoc (first tx-data) :db/id)))
     (defn- lex [name]
       (let [content (edn/read-string (slurp (java.io.File. lexdir (str name ".edn"))))]
         (if (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id))
           (reconstitute-entity content)
           content)))
     (defn- manifest []
       (:actor/manifest (clojure.edn/read-string (slurp (java.io.File. actor-dir "manifest.edn")))))))

(defn- record-node [doc] (get-in doc [:defs :main :record]))
(defn- required-of [doc] (set (:required (record-node doc))))
(defn- prop-names [doc] (set (map name (keys (:properties (record-node doc))))))

;; ── 14 gates declared (manifest dict, keys G1…_… ) ──
(deftest all-14-gates-declared
  (let [gates (get (manifest) "constitutionalGates")
        nums  (->> (keys gates)
                   (keep #(second (re-matches #"G(\d+)_.*" %)))
                   (map #(Integer/parseInt %)) set)]
    (is (= (set (range 1 15)) nums) "manifest must declare G1–G14")))

;; ── G9 — witness invariant N≥2 on every manufacturing step ──
(deftest g9-witness-n2
  (let [a (required-of (lex "apiSynthesisAttestation"))]
    (is (and (contains? a "witness1") (contains? a "witness2"))
        "G9: apiSynthesis must require two witnesses"))
  (let [f (required-of (lex "fillFinishAttestation"))]
    (is (and (contains? f "witnessOperator") (contains? f "witnessQp"))
        "G9: fillFinish must require operator + QP witnesses")))

;; ── G4 — QP-equivalent co-sign per lot ──
(deftest g4-qp-cosign
  (is (contains? (required-of (lex "qcAttestation")) "qpDid")
      "G4: qcAttestation must require the QP DID")
  (is (contains? (required-of (lex "fillFinishAttestation")) "witnessQp")
      "G4: fillFinish must require the QP witness"))

;; ── G10 — patient identity non-traceable (no patient DID/name field) ──
(deftest g10-patient-non-traceable
  (let [ae (lex "adverseEventReport")
        keyed (required-of ae)
        props (prop-names ae)]
    (is (every? keyed ["lotId" "severity" "outcome"])
        "G10: adverse events keyed by lot + severity + outcome")
    (is (empty? (filter #(clojure.string/includes? (clojure.string/lower-case %) "patient") props))
        "G10: adverseEventReport must carry NO patient identity field")))

;; ── G7 — CWC dual-use precursor monitoring ──
(deftest g7-cwc-precursor
  (let [rm (lex "rawMaterialAttestation")]
    (is (contains? (required-of rm) "hazardClass") "G7: raw material must declare hazardClass")
    (is (contains? (prop-names rm) "cwcStatus") "G7: raw material must carry CWC status")))

;; ── G8 — sterile process validation ──
(deftest g8-sterile-process
  (is (contains? (required-of (lex "fillFinishAttestation")) "sterileProcess")
      "G8: fillFinish must require a validated sterile process"))

;; ── G3 silen-pharma-review (Council) + G5 adverse-event public reporting ──
(deftest g3-review-g5-adverse-event
  (is (contains? (prop-names (lex "silenPharmaReview")) "councilMsig")
      "G3: silen-pharma-review carries a Council multisig")
  (let [ae (required-of (lex "adverseEventReport"))]
    (is (every? ae ["severity" "outcome" "reportDate"])
        "G5: adverse-event report requires severity + outcome + reportDate")))

;; ── G12 — no commercial sale model: settlement carries a tithe + member signature ──
(deftest g12-tithe-member-settlement
  (let [s (lex "settlementIntent")]
    (is (contains? (required-of s) "titheMinor")
        "G12: settlement must carry a tithe (10% → Public Fund), not a pure sale")
    (is (contains? (prop-names s) "buyerSigRef")
        "G12/no-server-key: settlement is buyer(member)-signed")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'yakushi.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

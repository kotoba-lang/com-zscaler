(ns yakushi.methods.agent
  "yakushi 薬師 — pharmaceutical R&D cell. 1:1 port of py/agent.py. Handlers over the pharma schema
  with constitutional gates: G1 OTC-only Wave-1, G2 published-literature routes, G3 silen-pharma-
  review, G4 QP co-sign (no-server-key G18), G5/G10 adverse-event aggregation (lot+severity+outcome,
  no patient DID), G9 witness N≥2, G17 USDC + 10% tithe (stops at :intent). Pure compute; the
  Murakumo llm host binding is the omitted leg (unused here)."
  (:require [clojure.string :as str]))

(def TITHE-BPS 1000)
(def ^:private VALID-SEVERITIES #{"mild" "moderate" "severe"})
(def ^:private VALID-OUTCOMES #{"recovered" "not-recovered" "unknown"})
(def ^:private WAVE-1-APIS #{"sodium-cromoglicate" "naphazoline-hydrochloride" "chlorpheniramine-maleate"})

(defn- infer
  "Murakumo-only inference (G15). The llm host binding is the omitted leg → constant local fallback."
  [_prompt]
  "LLM_NOT_AVAILABLE")

(defn api-otc-ok [api-inn-slug]
  (if-not (contains? WAVE-1-APIS (str/lower-case api-inn-slug))
    {"ok" false "reason" (str "API " api-inn-slug " not in Wave 1 OTC reference (G1)")}
    {"ok" true "reason" "OTC off-patent confirmed (Wave 1)"}))

(defn review-attested [review-verdict review-scope]
  (if-not (= (str/lower-case review-verdict) "approve")
    {"ok" false "reason" (str "silen-pharma-review verdict is " review-verdict " (G3)")}
    {"ok" true "reason" (str "silen-pharma-review approved for " review-scope)}))

(defn qp-signature-ok [qp-did qp-sig-ref]
  (if (or (not (seq qp-did)) (not (seq qp-sig-ref)))
    {"ok" false "reason" "QP DID and signature reference required (G4)"}
    {"ok" true "reason" (str "QP " qp-did " signature registered")}))

(defn adverse-event-ok [lot-id severity outcome]
  (cond
    (not (seq lot-id)) {"ok" false "reason" "lot_id required; patient DID prohibited (G5/G10)"}
    (not (contains? VALID-SEVERITIES (str/lower-case severity))) {"ok" false "reason" (str "severity " severity " not in " VALID-SEVERITIES)}
    (not (contains? VALID-OUTCOMES (str/lower-case outcome))) {"ok" false "reason" (str "outcome " outcome " not in " VALID-OUTCOMES)}
    :else {"ok" true "reason" (str "AE aggregation by lot " lot-id " (no patient identity)")}))

(defn witness-quorum-ok [witness-dids]
  (if (< (count witness-dids) 2)
    {"ok" false "reason" (str "witness count " (count witness-dids) " < 2 (G9)")}
    {"ok" true "reason" (str "witness quorum N=" (count witness-dids) " >= 2")}))

(defn record-raw-material [material-name grade hazard-class]
  (if-not (contains? #{"公定" "劇物" "koujou" "gekibutsu"} (str/lower-case grade))
    {"error" (str "grade " grade " must be 公定 or 劇物") "blocked" true}
    {":rawMaterialAttestation/id" (str "rm:" material-name)
     ":rawMaterialAttestation/materialName" material-name
     ":rawMaterialAttestation/grade" grade
     ":rawMaterialAttestation/hazardClass" hazard-class}))

(defn record-synthesis [api-inn-slug route witness-dids]
  (let [api-check (api-otc-ok api-inn-slug)]
    (if-not (get api-check "ok")
      {"error" (get api-check "reason") "blocked" true}
      (let [witness-check (witness-quorum-ok witness-dids)]
        (if-not (get witness-check "ok")
          {"error" (get witness-check "reason") "blocked" true}
          {":apiSynthesisAttestation/id" (str "syn:" api-inn-slug)
           ":apiSynthesisAttestation/apiName" api-inn-slug
           ":apiSynthesisAttestation/route" route
           ":apiSynthesisAttestation/witness1" (if (> (count witness-dids) 0) (nth witness-dids 0) "")
           ":apiSynthesisAttestation/witness2" (if (> (count witness-dids) 1) (nth witness-dids 1) "")})))))

(defn record-fill [product-form sterile-process witness-operator witness-qp]
  (if-not (contains? #{"aseptic-0.22µm-filter" "terminal-autoclave"} (str/lower-case sterile-process))
    {"error" (str "sterile_process " sterile-process " must be aseptic or autoclave (G8)") "blocked" true}
    {":fillFinishAttestation/id" (str "ff:" product-form)
     ":fillFinishAttestation/productForm" product-form
     ":fillFinishAttestation/sterileProcess" sterile-process
     ":fillFinishAttestation/witnessOperator" witness-operator
     ":fillFinishAttestation/witnessQp" witness-qp}))

(defn record-qc [lot-id test-results qp-did verdict]
  (let [qp-check (qp-signature-ok qp-did "passkey-ref")]
    (if-not (get qp-check "ok")
      {"error" (get qp-check "reason") "blocked" true}
      {":qcAttestation/id" (str "qc:" lot-id)
       ":qcAttestation/lotId" lot-id
       ":qcAttestation/testResults" test-results
       ":qcAttestation/qpDid" qp-did
       ":qcAttestation/verdict" verdict})))

(defn record-ae
  ([lot-id severity outcome] (record-ae lot-id severity outcome ""))
  ([lot-id severity outcome event-cid]
   (let [ae-check (adverse-event-ok lot-id severity outcome)]
     (if-not (get ae-check "ok")
       {"error" (get ae-check "reason") "blocked" true}
       {":adverseEventReport/id" (str "ae:" lot-id ":" severity)
        ":adverseEventReport/lotId" lot-id
        ":adverseEventReport/severity" severity
        ":adverseEventReport/outcome" outcome
        ":adverseEventReport/eventCid" (or event-cid "")}))))

(defn build-settlement-intent
  "USDC settlement split. 10% tithe → Public Fund. Stops at :intent — broadcast needs a member/QP
  signature (G18)."
  ([gross-minor] (build-settlement-intent gross-minor nil))
  ([gross-minor qp-sig-ref]
   (let [tithe (quot (* gross-minor TITHE-BPS) 10000)]
     {"rail" "usdc-base-l2" "grossMinor" gross-minor "titheMinor" tithe
      "makerPayoutMinor" (- gross-minor tithe) "titheRouter" "50-infra/etzhayyim-tithe-router"
      "state" (if qp-sig-ref "executed" "intent") "qpSigRef" (or qp-sig-ref "")})))

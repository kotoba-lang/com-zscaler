(ns kanae.methods.assemble-flows
  "kanae 鼎 — budget ledger → fundFlowEdge assembler (1:1 Clojure port of
  methods/assemble_flows.py, ADR-2605302300). Turns a danjo budget ledger into
  lexicon-valid `fundFlowEdge` records (appropriation + outlay chain) for the
  global government fiscal-flow visualization.

  Charter gates (raised in code, defense-in-depth over the schema):
    G4  non-adjudicating — a verdict token anywhere in an edge is refused.
    G5  grounded chain  — every edge needs ≥2 sourceRecordCids; an outlay is
        grounded by its appropriation parent.
    G10 aggregate endpoints — endpoints carry no publiclyNamedBasis (no named party).

  Maps use string keys (mirroring the Python dicts). Pure — no I/O."
  (:require [clojure.string :as str]))

(def FLOW-CLASSES
  #{"appropriation" "outlay" "subaward" "procurement-award"
    "intergovernmental-transfer" "aid-disbursement" "loan" "repayment"})

;; Tokens that would turn a factual edge into a verdict — unrepresentable (G4).
(def ^:private verdict-tokens
  ["crime" "violation" "guilt" "fraud" "illegal" "違法" "不正" "有罪" "犯罪"])

(def CELL-DID "did:web:kanae.etzhayyim.com:cell:flow_assembler")
(def ATTEST-DID "did:web:kanae.etzhayyim.com")
(def METHOD-NOTE "kanae.methodNote:v1-global-seed:domestic-flow-chain-assembly:1.0.0-draft")
(def DEFAULT-CREATED-AT "2026-06-07T00:00:00.000Z")

(defn- endpoint
  "An aggregate endpoint (G10). No publiclyNamedBasis ⇒ no named party asserted."
  ([kind label] (endpoint kind label "jpn"))
  ([kind label jurisdiction]
   {"endpointKind" kind "label" label "jurisdiction" jurisdiction}))

(defn assert-non-adjudicating
  "G4: refuse any verdict token anywhere in the edge."
  [edge]
  (let [blob (str/lower-case
              (str/join " " [(str (get edge "flowClass" ""))
                             (str (get-in edge ["fromEndpoint" "label"] ""))
                             (str (get-in edge ["toEndpoint" "label"] ""))]))]
    (doseq [tok verdict-tokens]
      (when (str/includes? blob tok)
        (throw (ex-info (str "G4 violation: verdict token '" tok "' in a fundFlowEdge") {:token tok}))))))

(defn- py-empty?
  "Python truthiness of `not v` for the required-field check: nil / false / \"\" /
  empty collection are 'missing'. (Clojure treats \"\"/[] as truthy, Python doesn't.)"
  [v]
  (or (nil? v) (false? v)
      (and (string? v) (= v ""))
      (and (coll? v) (empty? v))))

(defn validate-edge
  "Lexicon + charter gate check (required fields, enum, ≥2 CIDs, no verdict token)."
  [edge]
  (let [required ["createdAt" "sourceCellDid" "flowClass" "fromEndpoint" "toEndpoint"
                  "amount" "currency" "period" "jurisdiction" "sourceRecordCids"
                  "methodNoteCid" "attestingDid"]]
    (doseq [f required]
      (let [v (get edge f)]
        (when (and (py-empty? v) (not= v 0))
          (throw (ex-info (str "fundFlowEdge missing required field '" f "'") {:field f})))))
    (when-not (contains? FLOW-CLASSES (get edge "flowClass"))
      (throw (ex-info (str "flowClass '" (get edge "flowClass") "' not in lexicon enum") {})))
    (when (< (count (get edge "sourceRecordCids")) 2)
      (throw (ex-info "G5 violation: fundFlowEdge needs ≥2 sourceRecordCids" {})))
    (assert-non-adjudicating edge)))

(defn assemble
  "Budget ledger → fundFlowEdge list (appropriation + outlay chain). Each dict is a
  lexicon-valid fundFlowEdge PLUS yoro-projection annotations."
  ([ledger] (assemble ledger DEFAULT-CREATED-AT))
  ([ledger created-at]
   (let [treasury (endpoint "fiscal-authority"
                            "日本国 一般会計 (Japan General Account / National Treasury)")]
     (reduce
      (fn [edges [_ g]]
        (let [appropriations (get g "appropriations")
              outlays (get g "outlays")]
          (if (empty? appropriations)
            edges                                   ; no appropriation parent → observable gap, skip
            (let [approp (first appropriations)
                  ministry-label (get approp "recipientName")
                  ministry (endpoint "fiscal-authority" ministry-label)
                  period (str "FY" (get g "fiscalYear"))
                  approp-cid (get approp "cid")
                  second-cid (cond
                               (seq outlays) (get (first outlays) "cid")
                               (> (count appropriations) 1) (get (second appropriations) "cid")
                               :else nil)
                  edges (if second-cid
                          (let [approp-edge
                                {"createdAt" created-at "sourceCellDid" CELL-DID
                                 "flowClass" "appropriation" "fromEndpoint" treasury
                                 "toEndpoint" ministry "amount" (str (get approp "amountLocal"))
                                 "currency" (get approp "currencyIso4217") "period" period
                                 "jurisdiction" (get g "jurisdiction")
                                 "sourceRecordCids" [approp-cid second-cid]
                                 "methodNoteCid" METHOD-NOTE
                                 "stateAlignedFlag" (get approp "stateAlignedFlag")
                                 "attestingDid" ATTEST-DID
                                 "observedAt" (get approp "awardDateUtc")
                                 "_ministryLabel" ministry-label "_recipientLabel" ministry-label
                                 "_programCode" (get g "programCode") "_sourceUrl" (get approp "sourceUrl")}]
                            (validate-edge approp-edge)
                            (conj edges approp-edge))
                          edges)]
              (reduce
               (fn [edges outlay]
                 (let [recipient (endpoint "recipient-class" (get outlay "recipientName"))
                       outlay-edge
                       {"createdAt" created-at "sourceCellDid" CELL-DID
                        "flowClass" "outlay" "fromEndpoint" ministry "toEndpoint" recipient
                        "amount" (str (get outlay "amountLocal"))
                        "currency" (get outlay "currencyIso4217") "period" period
                        "jurisdiction" (get g "jurisdiction")
                        "sourceRecordCids" [(get outlay "cid") approp-cid]
                        "methodNoteCid" METHOD-NOTE
                        "stateAlignedFlag" (get outlay "stateAlignedFlag")
                        "attestingDid" ATTEST-DID
                        "observedAt" (get outlay "awardDateUtc")
                        "_ministryLabel" ministry-label "_recipientLabel" (get outlay "recipientName")
                        "_programCode" (get g "programCode") "_sourceUrl" (get outlay "sourceUrl")}]
                   (validate-edge outlay-edge)
                   (conj edges outlay-edge)))
               edges
               outlays)))))
      []
      (sort-by key (get ledger "groups"))))))

(defn net-flows
  "Per-ENDPOINT fiscal net flow over the assembled fundFlowEdges: each fiscal-authority endpoint's
  total INFLOW (amounts on edges where it is the toEndpoint) minus its OUTFLOW (where it is the
  fromEndpoint) — the factual flow balance the viz renders as a node's net position (a net RECEIVER
  vs a net SOURCE of fiscal flows). Aggregate-first by construction (it rolls up to the
  fiscal-authority labels the assembled edges already carry — never a per-individual element, G10)
  and NON-adjudicating (a sum of DISCLOSED amounts, never a verdict on whether a flow is right or
  wrong; danjo finds, kanae renders, G4). Amounts are summed in the edges' own units, so scope to a
  single currency before calling if the ledger mixes them. Returns [{:endpoint :inflow :outflow
  :net} …] sorted by net descending (the biggest net receivers first)."
  [edges]
  (let [acc (reduce (fn [m e]
                      (let [amt (try (Double/parseDouble (str (get e "amount"))) (catch Exception _ 0.0))
                            from (get-in e ["fromEndpoint" "label"])
                            to (get-in e ["toEndpoint" "label"])]
                        (cond-> m
                          from (update-in [from :outflow] (fnil + 0.0) amt)
                          to (update-in [to :inflow] (fnil + 0.0) amt))))
                    {} edges)]
    (->> acc
         (map (fn [[ep {:keys [inflow outflow]}]]
                (let [i (or inflow 0.0) o (or outflow 0.0)]
                  {:endpoint ep :inflow i :outflow o :net (- i o)})))
         (sort-by (fn [{:keys [net endpoint]}] [(- net) (str endpoint)]))
         vec)))

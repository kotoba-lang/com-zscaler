(ns matsurigoto.methods.datoms
  "matsurigoto 政 — R1.B: persist module executions to the kotoba Datom log.
  1:1 Clojure port of `methods/datoms.py` (ADR-2606062300).

  Converts a service module's output (R0 returned in-memory maps) into APPEND-ONLY EAVT
  datoms over the `egov-exec-v1` graph, and builds an offline `kg.ingest_batch` body. State
  becomes canonical, as-of, replayable (ADR-2605262130 + 2605312345) — the same membrane
  ake/watari/kanjo use.

  A datom here is an [entity attribute value] triple (EAVT) — a 3-vector, NOT the 5-place
  [e a v tx op] of inochi/kaiyaku (this layer is the converter, not the log emitter).

  Invariants enforced HERE (mirroring 00-contracts/schemas/egov-execution-ontology.kotoba.edn):
    G1 no-operator-master-key : the tx datom asserts :egov.tx/server-held-authority false, and a
                                persisted certificate's :egov.cert/proof is forced to nil — a
                                module signs nothing (ADR-2605231525).
    G3 authority-bearing      : :operated-by ∈ {:etzhayyim-council, :adopting-government};
                                :authority-mode ∈ {:sovereign-governance, :supplied-to-state}.
    G5 append-only            : every record datom carries :egov.record/immutable true.
    G8 outward-gated          : kg-ingest-batch(:published true) RAISES — live ingest is
                                Council+operator gated; R0/R1 is dry-run body construction only.

  House style: Python ':…' keyword strings stay strings; module-output maps are string-keyed
  (\"liability\", \"effective_rate\", …) exactly as the Python dicts; pure fns; the closed-vocab
  gates raise ex-info; the datom-emit ORDER is the literal source order (G3 tx block, then the
  per-module record/assessment block, then the certificate block) — preserved via a vector,
  not a sorted/hashed map (::order is the source line order)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

(def allowed-operated-by #{":etzhayyim-council" ":adopting-government"})
(def allowed-authority-mode #{":sovereign-governance" ":supplied-to-state"})

(defn- python-repr
  "Mirror Python's `{operated_by!r}` for the ValueError message: a str → single-quoted with
  the value verbatim (the keys here are plain ':…' strings, no embedded quotes/backslashes)."
  [v]
  (if (string? v) (str "'" v "'") (pr-str v)))

(defn tx-datoms
  "Port of _tx_datoms. Returns the tx-entity EAVT block as a vector of [e a v] triples, in the
  exact Python list order; an :atlas-did triple is appended only when atlas-did is truthy.

  G3 gates raise ex-info (the ValueError analogue). G2 requires a non-empty :spec-basis.
  `tx` is the keyword-style kwargs map: keys :service :module :operated-by :authority-mode
  :as-of :spec-basis :sourcing (default \":representative\") :atlas-did (optional)."
  [tx-id {:keys [service module operated-by authority-mode as-of spec-basis sourcing atlas-did]
          :or {sourcing ":representative"}}]
  (when-not (contains? allowed-operated-by operated-by)
    (throw (ex-info (str "G3: :operated-by " (python-repr operated-by) " not allowed")
                    {:gate :G3 :operated-by operated-by})))
  (when-not (contains? allowed-authority-mode authority-mode)
    (throw (ex-info (str "G3: :authority-mode " (python-repr authority-mode) " not allowed")
                    {:gate :G3 :authority-mode authority-mode})))
  (when (or (nil? spec-basis) (= "" spec-basis) (false? spec-basis))
    (throw (ex-info "G2: :spec-basis required" {:gate :G2})))
  (let [d [[tx-id ":egov.tx/id" tx-id]
           [tx-id ":egov.tx/service" service]
           [tx-id ":egov.tx/module" module]
           [tx-id ":egov.tx/operated-by" operated-by]
           [tx-id ":egov.tx/authority-mode" authority-mode]
           [tx-id ":egov.tx/as-of" as-of]
           [tx-id ":egov.tx/spec-basis" spec-basis]
           [tx-id ":egov.tx/sourcing" sourcing]
           [tx-id ":egov.tx/server-held-authority" false]]] ; G1
    (if atlas-did
      (conj d [tx-id ":egov.tx/atlas-did" atlas-did])
      d)))

(defn- assert-unsigned!
  "G1: a module-produced artifact must be unsigned (proof nil, no server-held authority).
  Mirrors _assert_unsigned: `artifact.get('proof') is not None` and
  `artifact.get('server_held_authority') is not False`."
  [artifact]
  (when-not (nil? (get artifact "proof"))
    (throw (ex-info "G1: a module artifact must be unsigned (proof must be None)" {:gate :G1})))
  (when-not (false? (get artifact "server_held_authority"))
    (throw (ex-info "G1: server_held_authority must be False" {:gate :G1}))))

(defn cert-datoms
  "Port of _cert_datoms. Asserts the artifact is unsigned, then emits the certificate block
  keyed on `<tx-id>#cert`. :egov.cert/kind = artifact['kind'] or the last element of
  artifact['type'] (default ['' '?']); :egov.cert/proof is forced nil (G1)."
  [tx-id artifact]
  (assert-unsigned! artifact)
  (let [cert-e (str tx-id "#cert")
        ;; artifact.get("kind") or artifact.get("type", ["", "?"])[-1]
        kind (or (get artifact "kind")
                 (let [ty (get artifact "type" ["" "?"])]
                   (nth ty (dec (count ty)))))]
    [[cert-e ":egov.cert/of-tx" tx-id]
     [cert-e ":egov.cert/kind" kind]
     [cert-e ":egov.cert/status" (get artifact "status")]
     [cert-e ":egov.cert/proof" nil]])) ; G1 — nil until the governing organ signs externally

;; ── per-module converters (take the module's R0 output map) ──
(defn assessment-datoms
  "tax-assess output → datoms. 1:1 of assessment_datoms (module fixed to \"tax-assess\")."
  [out tx-id tx]
  (let [d (tx-datoms tx-id (assoc tx :module "tax-assess"))
        d (into d [[tx-id ":egov.assessment/of-tx" tx-id]
                   [tx-id ":egov.assessment/liability" (get out "liability")]
                   [tx-id ":egov.assessment/effective-rate" (get out "effective_rate")]
                   [tx-id ":egov.assessment/currency" (get out "currency" "XXX")]])]
    (if (contains? out "receipt")
      (into d (cert-datoms tx-id (get out "receipt")))
      d)))

(defn civil-datoms
  "civil-registry registration → datoms (append-only). 1:1 of civil_datoms."
  [out tx-id tx]
  (let [rec (get out "record")
        rid (get rec "record_id")
        d (tx-datoms tx-id (assoc tx :module "civil-registry"))
        d (into d [[rid ":egov.record/id" rid]
                   [rid ":egov.record/of-tx" tx-id]
                   [rid ":egov.record/kind" (get rec "vital_kind")]
                   [rid ":egov.record/immutable" true]])] ; G5
    (into d (cert-datoms tx-id (get out "certificate")))))

(defn incorporation-datoms
  "corp-registry incorporation → datoms. 1:1 of incorporation_datoms."
  [out tx-id tx]
  (let [rec (get out "record")
        rid (get rec "record_id")
        d (tx-datoms tx-id (assoc tx :module "corp-registry"))
        d (into d [[rid ":egov.record/id" rid]
                   [rid ":egov.record/of-tx" tx-id]
                   [rid ":egov.record/kind" "incorporation"]
                   [rid ":egov.record/immutable" true] ; G5
                   [rid ":egov.record/lei" (get rec "lei")]])]
    (into d (cert-datoms tx-id (get out "certificate")))))

(defn passport-datoms
  "credential-issue passport → datoms (MRZ kept off the log; only the issuance record).
  1:1 of passport_datoms."
  [out tx-id tx]
  (let [d (tx-datoms tx-id (assoc tx :module "credential-issue"))
        rid (str tx-id "#mrtd")
        d (into d [[rid ":egov.record/id" rid]
                   [rid ":egov.record/of-tx" tx-id]
                   [rid ":egov.record/kind" "passport"]
                   [rid ":egov.record/immutable" true]])] ; G5
    (into d (cert-datoms tx-id (get out "document")))))

(defn kg-ingest-batch
  "Build a `kg.ingest_batch` body. G8: :published true RAISES — live ingest is Council+operator
  gated. R1 constructs the dry-run body only. 1:1 of kg_ingest_batch."
  ([datoms] (kg-ingest-batch datoms {}))
  ([datoms {:keys [graph published] :or {graph "egov-exec-v1" published false}}]
   (when published
     (throw (ex-info (str "G8: live kotoba ingest is Council+operator gated (principal A: "
                          "Council Lv7+; principal B: adopting state). Construct the body and "
                          "hand off; do not publish here.")
                     {:gate :G8})))
   {"op" "kg.ingest_batch"
    "graph" graph
    "published" false
    "datoms" (vec datoms)
    "count" (count datoms)}))

;; ── EAVT EDN emit (byte-parity harness; NOT in the Python source — a deterministic
;;    serializer so the produced 3-vectors can be compared byte-for-byte against a Python
;;    json/edn dump of the same list-of-lists). House-style fmt: bool→true/false, nil→nil,
;;    \":…\"→literal, other string→quoted, double→{v:g}, else str. One datom per line.) ──
(defn- fmt-g
  "Mirror Python's f-string `{v:g}`: 6 significant digits, trailing zeros stripped, an integral
  value renders with no decimal point."
  [v]
  (let [d (double v)]
    (if (and (not (Double/isInfinite d)) (not (Double/isNaN d))
             (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn fmt
  "Render a datom value to its byte-parity text form."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (double? v) (fmt-g v)
    :else (str v)))

(defn emit
  "Serialize a vector of [e a v] datoms to canonical EDN text (one per line, trailing newline).
  Deterministic + order-preserving (the converter already emits in source order)."
  [datoms]
  (str (str/join "\n"
                 (concat
                  [";; matsurigoto 政 — GENERATED kotoba Datom log (ADR-2606062300). DO NOT hand-edit."
                   ";; APPEND-ONLY EAVT [e a v]. egov-exec-v1 graph. G1 unsigned / G5 immutable."
                   "["]
                  (map (fn [[e a v]] (str "[" (fmt e) " " a " " (fmt v) "]")) datoms)
                  ["]"]))
       "\n"))

#?(:clj
   (defn -main
     "CLI entry: emit a demo assessment's datoms → out/egov-datoms.kotoba.edn (I/O at the edge).
     Mirrors datoms.py's __main__ headline. Requires a tax-assess output passed inline; the
     module ports live in the test ns, so this -main builds a self-contained demo block."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           ;; demo: a tax assessment-shaped output (5,000,000 taxable, JP table → 572500)
           out {"liability" 572500.0 "effective_rate" 0.1145 "currency" "JPY"
                "receipt" {"proof" nil "server_held_authority" false "status" "assessed-unsigned"}}
           ds (assessment-datoms out "tx-demo"
                                 {:service "tax.income.file"
                                  :operated-by ":etzhayyim-council"
                                  :authority-mode ":sovereign-governance"
                                  :as-of "2026-06-06T00:00:00Z" :spec-basis "JP 速算表"})
           body (kg-ingest-batch ds)
           outf (io/file outdir "egov-datoms.kotoba.edn")]
       (.mkdirs outdir)
       (spit outf (emit ds))
       (println (str (get body "count") " datoms, published=" (get body "published")))
       0)))

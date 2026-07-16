(ns keizu.methods.weave
  "weave.cljc — 系図 (keizu) relation-graph build + aggregate concentration. ADR-2606066000.
  1:1 Clojure port of `methods/weave.py` (same house style as rasen/inochi/kabuto).

  THE HEART of the actor and the G1/G2/G3/G4 anchor. Given the seed graph it:
    1. VALIDATES every node / relation / committee / statement / money flow against the
       closed structural vocab (mirror of the ontology). A private-person node, a verdict
       relation kind, a bribe money kind, or an unsourced/under-sourced tie throws ex-info
       (mirroring Python's ValueError) — not a silent drop.
    2. WEAVES the validated records into an in-memory relation graph.
    3. Computes AGGREGATE, EDGE-PRIMARY concentration metrics (G4) — there is NO per-node
       power/influence score: committee cross-organ concentration, cross-committee
       co-membership, per-payee/payer money HHI, revolving-door chains, connector seats,
       award-and-fund co-occurrence, statement index, by-jurisdiction, integrity.

  CONSTITUTIONAL (read before any change):
    G1 — public-power-role only; a private-person node / a PII attr / a per-node power-score
      is unrepresentable (no-doxxing).
    G2 — non-adjudicating; verdict tokens are not enum members.
    G3 — ≥2 public-source citations on rel/money; ≥1 on committee/statement.
    G4 — edge-primary; concentration is computed on read, never a stored score.

  House style: Python ':…' keyword strings stay strings (incl. all :*/* attrs); validation /
  closed-vocab / gate violations throw ex-info; pure fns; file I/O only at the #?(:clj) edge.

  Insertion-order parity: Python iterates plain dicts (by_payee / by_payer / by_speaker /
  by_topic / juris) in insertion order, then stable-sorts. keizu's set iteration is ONLY ever
  `sorted(set(...))` (committee organs, cross-committee committees, connector organs) — the
  result is sorted, never CPython-set-order-dependent, so plain ::order insertion-tracking
  suffices (no siphash13/setobject port needed). The ::order metadata + stable sort-by ties
  the Python dict iteration order byte-for-byte for the `shares` rankings."
  (:require [clojure.string :as str]
            [clojure.set]
            #?(:clj [keizu.methods.edn :as kedn])))

;; ── closed vocab (mirror of the ontology :db/allowed) ───────────────────────────
(def NODE-SCOPES ["public-office" "public-org" "public-committee" "public-role"])
(def REL-KINDS
  ["committee-membership" "appointment" "advisory-role" "co-membership"
   "revolving-door" "funding-tie" "statement-attribution" "procurement-award"])
(def MONEY-KINDS ["procurement-award" "subsidy" "grant" "political-donation" "budget-outlay"])
(def SOURCING ["representative" "authoritative"])

;; Tokens that would turn a factual map into an adjudication — must never appear as a kind.
(def VERDICT-TOKENS
  ["corruption" "bribe" "kickback" "collusion" "guilt" "crime"
   "fraud" "illegal" "slush" "不正" "違法" "汚職" "賄賂"])

;; Charter Rider §2(e) / N5 — commercial gov-intelligence terminals are PROHIBITED as a
;; citation source. A derived datom citing one of these is refused on EVERY path.
(def SOURCE-DENY
  ["govwin" "bloomberg" "politico pro" "e&e news" "fiscalnote" "cq roll call"
   "四季報" "capital iq" "capiq" "refinitiv" "factset" "pitchbook" "crunchbase"
   "lexisnexis" "westlaw"])

(defn source-denied
  "Return the first prohibited commercial gov-intel term found in any source, or '' if clean."
  [sources]
  (let [blob (str/lower-case (str/join " " (map str (or sources []))))]
    (or (some (fn [d] (when (str/includes? blob d) d)) SOURCE-DENY) "")))

;; G9 / G1 no-doxxing — a node is a PUBLIC seat/organ, so a personal-contact or sensitive-PII
;; field on it is unrepresentable (any such datum lives encrypted off-graph, ADR-2605181100).
(def PII-FORBIDDEN-NODE-ATTRS
  #{"email" "phone" "tel" "mobile" "fax" "address" "home" "residence"
    "dob" "birthdate" "birthday" "ssn" "mynumber" "my-number" "passport"
    "personal-name" "private-name" "face" "photo" "headshot" "gender"
    "religion" "ethnicity" "health" "private"})

(defn- in-vec?
  "Membership test against a vector closed-vocab (Python `x in (...)` over a tuple)."
  [v x]
  (boolean (some #(= % x) v)))

(defn- kw*
  "Normalize an edn keyword/string to a bare lowercase token (':rel/kind' → 'kind')."
  [v]
  (let [s (-> (str (or v "")) (str/replace #"^:+" ""))]
    (-> (last (str/split s #"/" -1)) (str/lower-case))))

(defn- err [msg] (throw (ex-info msg {})))

;; ── validation (G1/G2/G3) ───────────────────────────────────────────────────────
(defn validate-node [n]
  (let [scope (kw* (get n ":node/scope" ""))]
    (when-not (in-vec? NODE-SCOPES scope)
      (err (str "G1: node scope '" scope "' not in " (pr-str NODE-SCOPES)
                " — a private person is unrepresentable "
                "(keizu maps public seats/organs, never individuals)")))
    (when (or (contains? n "power-score") (contains? n ":node/power-score")
              (contains? n ":node/influence") (contains? n ":node/rank"))
      (err "G4: a per-node power/influence/rank score is unrepresentable (edge-primary)"))
    (doseq [key (keys n)]
      (when (contains? PII-FORBIDDEN-NODE-ATTRS (kw* key))
        (err (str "G9/G1 no-doxxing: node field '" key "' is personal/sensitive PII — unrepresentable "
                  "on a public seat (any such datum lives encrypted off-graph, ADR-2605181100)"))))
    (when-not (in-vec? SOURCING (kw* (get n ":node/sourcing" "")))
      (err "G11: every node must declare :node/sourcing"))))

(defn validate-rel [r]
  (let [kind (kw* (get r ":rel/kind" ""))]
    (when (in-vec? VERDICT-TOKENS kind)
      (err (str "G2: relation kind '" kind "' is a verdict — unrepresentable (non-adjudicating)")))
    (when-not (in-vec? REL-KINDS kind)
      (err (str "G2: relation kind '" kind "' not in the factual closed vocab " (pr-str REL-KINDS))))
    (when-not (true? (get r ":rel/non-adjudicating-notice"))
      (err "G2: :rel/non-adjudicating-notice must be true"))
    (let [srcs (or (get r ":rel/sources") [])]
      (when-not (and (vector? srcs) (>= (count srcs) 2))
        (err (str "G3: relation '" (get r ":rel/id") "' needs ≥2 public-source citations")))
      (let [d (source-denied srcs)]
        (when (seq d)
          (err (str "Rider §2(e)/N5: source '" d "' is a commercial gov-intel terminal — prohibited citation")))))
    (when-not (in-vec? SOURCING (kw* (get r ":rel/sourcing" "")))
      (err "G11: every relation must declare :rel/sourcing"))))

(defn validate-committee
  "A committee / advisory-council composition snapshot. Needs an id + ≥1 public seat (G1) +
  ≥1 public source (G3, no prohibited terminal) + declared sourcing (G11)."
  [c]
  (when (str/blank? (str (get c ":committee/id" "")))
    (err "committee needs :committee/id"))
  (let [members (or (get c ":committee/members") [])]
    (when-not (and (vector? members) (>= (count members) 1))
      (err (str "G1: committee '" (get c ":committee/id") "' composition needs ≥1 public seat"))))
  (let [srcs (or (get c ":committee/sources") [])]
    (when-not (and (vector? srcs) (>= (count srcs) 1))
      (err (str "G3: committee '" (get c ":committee/id") "' needs ≥1 public source")))
    (let [d (source-denied srcs)]
      (when (seq d)
        (err (str "Rider §2(e)/N5: source '" d "' is a commercial gov-intel terminal — prohibited citation")))))
  (when-not (in-vec? SOURCING (kw* (get c ":committee/sourcing" "")))
    (err "G11: every committee must declare :committee/sourcing")))

(defn validate-statement
  "A public statement (発言) attributed to a public role. Must have a speaker + ≥1 public
  source (G3) + declared sourcing (G11)."
  [s]
  (when (str/blank? (str (get s ":statement/speaker" "")))
    (err (str "statement '" (get s ":statement/id") "' needs a :statement/speaker")))
  (let [srcs (or (get s ":statement/sources") [])]
    (when-not (and (vector? srcs) (>= (count srcs) 1))
      (err (str "G3: statement '" (get s ":statement/id") "' needs ≥1 public source")))
    (let [d (source-denied srcs)]
      (when (seq d)
        (err (str "Rider §2(e)/N5: source '" d "' is a commercial gov-intel terminal — prohibited citation")))))
  (when-not (in-vec? SOURCING (kw* (get s ":statement/sourcing" "")))
    (err "G11: every statement must declare :statement/sourcing")))

(defn- to-finite-double
  "float(m.get(':money/amount', 0.0)) with TypeError/ValueError → 'must be a number'."
  [v id]
  (cond
    (nil? v) 0.0
    (number? v) (double v)
    (string? v) (try #?(:clj (Double/parseDouble v) :cljs (let [n (js/parseFloat v)]
                                                            (if (js/isNaN n) (err "x") n)))
                     (catch #?(:clj Exception :cljs :default) _
                       (err (str "money flow '" id "' amount must be a number"))))
    :else (err (str "money flow '" id "' amount must be a number"))))

(defn- finite? [x] #?(:clj (and (not (Double/isNaN x)) (not (Double/isInfinite x)))
                      :cljs (js/isFinite x)))

(defn validate-money [m]
  (let [kind (kw* (get m ":money/kind" ""))]
    (when (in-vec? VERDICT-TOKENS kind)
      (err (str "G2: money kind '" kind "' is a verdict — unrepresentable")))
    (when-not (in-vec? MONEY-KINDS kind)
      (err (str "G2: money kind '" kind "' not in the disclosed-flow vocab " (pr-str MONEY-KINDS))))
    (let [srcs (or (get m ":money/sources") [])]
      (when-not (and (vector? srcs) (>= (count srcs) 2))
        (err (str "G3: money flow '" (get m ":money/id") "' needs ≥2 public-source citations")))
      (let [d (source-denied srcs)]
        (when (seq d)
          (err (str "Rider §2(e)/N5: source '" d "' is a commercial gov-intel terminal — prohibited citation")))))
    (let [amt (to-finite-double (get m ":money/amount" 0.0) (get m ":money/id"))]
      (when (or (not (finite? amt)) (< amt 0))
        (err (str "money flow '" (get m ":money/id") "' amount must be finite and ≥ 0 "
                  "(a negative/NaN amount corrupts the HHI/share math)"))))
    (when-not (in-vec? SOURCING (kw* (get m ":money/sourcing" "")))
      (err "G11: every money flow must declare :money/sourcing"))))

;; ── ordered map (mirror a Python plain dict's first-touch insertion order) ──────
;; ::order is a vector of keys in first-touch order; the stable sort-by on shares /
;; speakers / topics / juris then ties exactly the Python dict iteration order.
(defn- ordered-map [] ^{::order []} {})

(defn- omap-assoc
  "Set k → v, recording k's first-touch position in ::order metadata."
  [m k v]
  (let [had? (contains? m k)
        m' (assoc m k v)]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn- omap-update
  [m k f default]
  (omap-assoc m k (f (get m k default))))

(defn- omap-items
  "Items in first-touch order (matches Python `dict.items()` / iteration)."
  [d]
  (let [order (::order (meta d))]
    (if order (map (fn [k] [k (get d k)]) order) (seq d))))

(defn- omap
  "Build an ordered map (::order-tagged) from a flat seq of k v k v … so JSON serialization
  preserves the literal key order (mirroring a Python dict literal's insertion order)."
  [& kvs]
  (reduce (fn [m [k v]] (omap-assoc m k v)) (ordered-map) (partition 2 kvs)))

;; ── weave ───────────────────────────────────────────────────────────────────────
(defn weave
  "Validate + index the seed graph into an in-memory relation graph. Throws on a gate.
  `nodes`/`committees` are ordered maps (insertion order = seed order, like Python dicts)."
  [graph]
  (let [nodes (reduce (fn [m n] (omap-assoc m (get n ":node/id") n))
                      (ordered-map) (get graph ":nodes" []))
        committees (reduce (fn [m c] (omap-assoc m (get c ":committee/id") c))
                           (ordered-map) (get graph ":committees" []))
        rels (vec (get graph ":rels" []))
        money (vec (get graph ":money" []))
        statements (vec (get graph ":statements" []))]
    (doseq [n (map second (omap-items nodes))] (validate-node n))
    (doseq [c (map second (omap-items committees))] (validate-committee c))
    (doseq [r rels] (validate-rel r))
    (doseq [m money] (validate-money m))
    (doseq [s statements] (validate-statement s))
    {"nodes" nodes "committees" committees "rels" rels
     "money" money "statements" statements}))

;; ── float formatting (Python round + repr parity) ────────────────────────────────
(defn pyround
  "Python round(x, n): HALF_EVEN over the exact binary value of the double."
  [x n]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :cljs (let [f (Math/pow 10 n) y (* (double x) f)
                 r (Math/round y) ; JS round is half-up; acceptable for cljs path
                 ] (/ r f))))

;; ── aggregate, edge-primary concentration metrics (G4) ───────────────────────────
(defn committee-cross-organ
  "Per committee: how many DISTINCT organs its seats are drawn from. Aggregate, no per-person score."
  [g]
  (let [nodes (get g "nodes")
        out (mapv (fn [[cid c]]
                    (let [organs (mapv (fn [mid] (get-in nodes [mid ":node/organ"] "(unknown)"))
                                       (get c ":committee/members" []))
                          distinct- (vec (sort (set organs)))]
                      (omap "committee" cid
                            "label" (get c ":committee/label" cid)
                            "member_count" (count (get c ":committee/members" []))
                            "distinct_organs" (count distinct-)
                            "organs" distinct-)))
                  (omap-items (get g "committees")))]
    (vec (sort-by (juxt #(- (get % "member_count")) #(get % "committee")) out))))

(defn cross-committee-seats
  "Public seats that sit on >1 committee — surfaced from the EDGES (:committee-membership)."
  [g]
  (let [by-seat (reduce (fn [m r]
                          (if (= (kw* (get r ":rel/kind")) "committee-membership")
                            (omap-update m (get r ":rel/source")
                                         #(conj % (get r ":rel/target")) [])
                            m))
                        (ordered-map) (get g "rels"))
        out (keep (fn [[seat comms]]
                    (let [uniq (vec (sort (set comms)))]
                      (when (> (count uniq) 1)
                        (omap "seat" seat "committee_count" (count uniq) "committees" uniq))))
                  (omap-items by-seat))]
    (vec (sort-by (juxt #(- (get % "committee_count")) #(get % "seat")) out))))

(defn money-concentration
  "Per-payee money share + HHI over disclosed flows. HHI ∈ (0,1]. Aggregate, factual."
  [g]
  (let [[by-payee total]
        (reduce (fn [[bp t] m]
                  (let [amt (to-finite-double (get m ":money/amount" 0.0) (get m ":money/id"))
                        p (get m ":money/payee")]
                    [(omap-update bp p #(+ % amt) 0.0) (+ t amt)]))
                [(ordered-map) 0.0] (get g "money"))
        shares (reduce (fn [m [p v]]
                         (omap-assoc m p (if (not= total 0.0) (/ v total) 0.0)))
                       (ordered-map) (omap-items by-payee))
        hhi (reduce + 0.0 (map (fn [[_ s]] (* s s)) (omap-items shares)))
        ranked (vec (map (fn [[p v]] [p v])
                         (sort-by (fn [[_ v]] (- v)) (omap-items shares))))]
    (omap "total" total "hhi" (pyround hhi 4) "shares" ranked
          "by_payee" by-payee)))

(defn payer-concentration
  "Per-PAYER money share + HHI. The payer-side complement of money-concentration."
  [g]
  (let [[by-payer total]
        (reduce (fn [[bp t] m]
                  (let [amt (to-finite-double (get m ":money/amount" 0.0) (get m ":money/id"))
                        p (get m ":money/payer")]
                    [(omap-update bp p #(+ % amt) 0.0) (+ t amt)]))
                [(ordered-map) 0.0] (get g "money"))
        shares (reduce (fn [m [p v]]
                         (omap-assoc m p (if (not= total 0.0) (/ v total) 0.0)))
                       (ordered-map) (omap-items by-payer))
        hhi (reduce + 0.0 (map (fn [[_ s]] (* s s)) (omap-items shares)))
        ranked (vec (map (fn [[p v]] [p v])
                         (sort-by (fn [[_ v]] (- v)) (omap-items shares))))]
    (omap "total" total "hhi" (pyround hhi 4) "shares" ranked
          "by_payer" by-payer)))

(defn revolving-door-chains
  "Organ → committee-seat movements (:revolving-door edges)."
  [g]
  (let [nodes (get g "nodes")
        out (keep (fn [r]
                    (when (= (kw* (get r ":rel/kind")) "revolving-door")
                      (omap "from" (get r ":rel/source")
                            "from_label" (get-in nodes [(get r ":rel/source") ":node/label"] (get r ":rel/source"))
                            "to" (get r ":rel/target")
                            "to_label" (get-in nodes [(get r ":rel/target") ":node/label"] (get r ":rel/target"))
                            "as_of" (get r ":rel/as-of"))))
                  (get g "rels"))]
    (vec (sort-by #(str (get % "from")) out))))

(defn connector-seats
  "Cross-organ connectors: a public seat sitting on committees spanning MORE THAN ONE organ.
  Derived on read from :committee-membership edges + each committee's organ (edge-primary, G4)."
  [g]
  (let [comm-organ (reduce (fn [m [cid c]] (assoc m cid (get c ":committee/organ" "(unknown)")))
                           {} (omap-items (get g "committees")))
        by-seat (reduce (fn [m r]
                          (if (= (kw* (get r ":rel/kind")) "committee-membership")
                            (omap-update m (get r ":rel/source")
                                         #(conj % (get r ":rel/target")) [])
                            m))
                        (ordered-map) (get g "rels"))
        out (keep (fn [[seat comms]]
                    (let [uniq-comms (vec (sort (set comms)))
                          organs (vec (sort (set (map #(get comm-organ % "(unknown)") uniq-comms))))]
                      (when (and (> (count uniq-comms) 1) (> (count organs) 1))
                        (omap "seat" seat "committees" uniq-comms
                              "organs_bridged" (count organs) "organs" organs))))
                  (omap-items by-seat))]
    (vec (sort-by (juxt #(- (get % "organs_bridged")) #(get % "seat")) out))))

(defn active-as-of
  "G10 / 非終末論 — which relations + committee compositions are active as of `ts`."
  [g ts]
  (let [active-rels (filter #(<= (long (get % ":rel/as-of" 0)) ts) (get g "rels"))
        active-comms (filter #(<= (long (get % ":committee/term-from" 0)) ts)
                             (map second (omap-items (get g "committees"))))]
    (omap "ts" ts
          "active_rels" (count active-rels)
          "total_rels" (count (get g "rels"))
          "active_committees" (count active-comms)
          "total_committees" (count (omap-items (get g "committees"))))))

(defn award-and-fund
  "FACTUAL co-occurrence (non-adjudicating, G2): public roles that BOTH received public money
  AND made a political donation. Aggregate, edge-primary; never an allegation."
  [g]
  (let [[received donated]
        (reduce (fn [[rec don] m]
                  (let [kind (kw* (get m ":money/kind"))
                        amt (to-finite-double (get m ":money/amount" 0.0) (get m ":money/id"))]
                    [(if (in-vec? ["procurement-award" "subsidy" "grant" "budget-outlay"] kind)
                       (omap-update rec (get m ":money/payee")
                                    #(conj % [(get m ":money/payer") amt]) [])
                       rec)
                     (if (= kind "political-donation")
                       (omap-update don (get m ":money/payer")
                                    #(conj % [(get m ":money/payee") amt]) [])
                       don)]))
                [(ordered-map) (ordered-map)] (get g "money"))
        nodes (vec (sort (clojure.set/intersection (set (keys received)) (set (keys donated)))))]
    (mapv (fn [node]
            (omap "node" node
                  "received_from" (vec (sort (set (map first (get received node)))))
                  "received_total" (pyround (reduce + 0.0 (map second (get received node))) 2)
                  "donated_to" (vec (sort (set (map first (get donated node)))))
                  "donated_total" (pyround (reduce + 0.0 (map second (get donated node))) 2)))
          nodes)))

(defn check-integrity
  "Referential integrity: every reference must resolve to an existing entity (a data-quality
  diagnostic, not a charter gate)."
  [g]
  (let [nodes (set (keys (get g "nodes")))
        committees (set (keys (get g "committees")))
        statements (set (map #(get % ":statement/id") (get g "statements")))
        rel-space (clojure.set/union nodes committees statements)
        dangling (transient [])
        chk (fn [ref space kind owner field]
              (when (and ref (not (contains? space ref)))
                (conj! dangling (omap "kind" kind "owner" owner "field" field "ref" ref))))]
    (doseq [r (get g "rels")]
      (chk (get r ":rel/source") rel-space "rel" (get r ":rel/id") "source")
      (chk (get r ":rel/target") rel-space "rel" (get r ":rel/id") "target"))
    (doseq [m (get g "money")]
      (chk (get m ":money/payer") nodes "money" (get m ":money/id") "payer")
      (chk (get m ":money/payee") nodes "money" (get m ":money/id") "payee"))
    (doseq [[cid c] (omap-items (get g "committees"))]
      (doseq [mid (get c ":committee/members" [])]
        (chk mid nodes "committee" cid "member")))
    (doseq [s (get g "statements")]
      (chk (get s ":statement/speaker") nodes "statement" (get s ":statement/id") "speaker"))
    (let [d (persistent! dangling)]
      (omap "dangling_count" (count d) "dangling" d))))

(defn assert-integrity
  "Strict mode — throw if any reference dangles (ingest/bridge data-quality gate)."
  [g]
  (let [rep (check-integrity g)]
    (when (pos? (get rep "dangling_count"))
      (let [first- (first (get rep "dangling"))]
        (err (str "integrity: " (get rep "dangling_count") " dangling ref(s); e.g. "
                  (get first- "kind") " '" (get first- "owner") "' "
                  (get first- "field") "→'" (get first- "ref") "' (no such entity)"))))))

(defn by-jurisdiction
  "Per-jurisdiction slice: node + committee counts and total disbursed money (PAYER's
  jurisdiction). Aggregate, factual (G2/G3)."
  [g]
  (let [slot (fn [juris j]
               (let [j (if (or (nil? j) (= "" j)) "(unknown)" j)]
                 (if (contains? juris j)
                   juris
                   (omap-assoc juris j (omap "jurisdiction" j "nodes" 0 "committees" 0 "money_total" 0.0)))))
        juris (reduce (fn [ju n]
                        (let [j (let [x (get n ":node/jurisdiction" "")] (if (or (nil? x) (= "" x)) "(unknown)" x))
                              ju (slot ju j)]
                          (omap-assoc ju j (update (get ju j) "nodes" inc))))
                      (ordered-map) (map second (omap-items (get g "nodes"))))
        juris (reduce (fn [ju c]
                        (let [j (let [x (get c ":committee/jurisdiction" "")] (if (or (nil? x) (= "" x)) "(unknown)" x))
                              ju (slot ju j)]
                          (omap-assoc ju j (update (get ju j) "committees" inc))))
                      juris (map second (omap-items (get g "committees"))))
        juris (reduce (fn [ju m]
                        (let [payer (get (get g "nodes") (get m ":money/payer") {})
                              j (let [x (get payer ":node/jurisdiction" "")] (if (or (nil? x) (= "" x)) "(unknown)" x))
                              ju (slot ju j)
                              amt (to-finite-double (get m ":money/amount" 0.0) (get m ":money/id"))]
                          (omap-assoc ju j (update (get ju j) "money_total" #(+ % amt)))))
                      juris (get g "money"))
        rows (map (fn [[_ v]] (update v "money_total" #(pyround % 2))) (omap-items juris))]
    (vec (sort-by (juxt #(- (get % "nodes")) #(get % "jurisdiction")) rows))))

(defn statement-index
  "発言 aggregate: per-speaker statement count + per-topic speaker set. Non-adjudicating (G3)."
  [g]
  (let [[by-speaker by-topic]
        (reduce (fn [[bs bt] s]
                  (let [sp (get s ":statement/speaker" "?")
                        topic (get s ":statement/topic" "(untopiced)")]
                    [(omap-update bs sp inc 0)
                     (omap-update bt topic #(conj % sp) #{})]))
                [(ordered-map) (ordered-map)] (get g "statements"))]
    (omap "count" (count (get g "statements"))
          "by_speaker" (vec (map (fn [[k v]] [k v])
                                 (sort-by (juxt (fn [[_ v]] (- v)) (fn [[k _]] k)) (omap-items by-speaker))))
          "by_topic" (vec (sort-by #(get % "topic")
                                   (map (fn [[t sp]] (omap "topic" t "speakers" (vec (sort sp))))
                                        (omap-items by-topic)))))))

(defn concentration
  "The full aggregate-first concentration report (G3/G4). All metrics derived on read."
  [g]
  (omap "node_count" (count (omap-items (get g "nodes")))
        "committee_count" (count (omap-items (get g "committees")))
        "rel_count" (count (get g "rels"))
        "money_count" (count (get g "money"))
        "statement_count" (count (get g "statements"))
        "committee_cross_organ" (committee-cross-organ g)
        "cross_committee_seats" (cross-committee-seats g)
        "connector_seats" (connector-seats g)
        "money_concentration" (money-concentration g)
        "payer_concentration" (payer-concentration g)
        "revolving_door" (revolving-door-chains g)
        "award_and_fund" (award-and-fund g)
        "statement_index" (statement-index g)
        "by_jurisdiction" (by-jurisdiction g)
        "integrity" (check-integrity g)))

;; ── canonical JSON (json.dumps(ensure_ascii=False) parity, insertion-order keys) ──
(defn- py-float-repr
  "repr(float) for finite values in keizu's range: shortest round-trip plain decimal
  (Python prints exponential only for |x| ≥ 1e16 or < 1e-4; all keizu magnitudes are plain)."
  [^double x]
  #?(:clj
     (cond
       (zero? x) "0.0"
       :else
       (let [s (Double/toString x)
             ax (Math/abs x)]
         (if (and (>= ax 1.0e-4) (< ax 1.0e16))
           (let [p (.toPlainString (java.math.BigDecimal. s))]
             (if (str/includes? p ".")
               (let [t (str/replace p #"0+$" "")]
                 (if (str/ends-with? t ".") (str t "0") t))
               (str p ".0")))
           s)))
     :cljs (str x)))

(defn- json-str [s]
  (str \"
       (-> (str s)
           (str/replace "\\" "\\\\")
           (str/replace "\"" "\\\"")
           (str/replace "\n" "\\n")
           (str/replace "\t" "\\t")
           (str/replace "\r" "\\r"))
       \"))

(defn to-json
  "Canonical JSON of a weave/concentration value — matches Python json.dumps(ensure_ascii=False,
  sort_keys=False) with the default `, ` / `: ` separators. Maps keep insertion order
  (::order-aware); doubles use Python repr; longs print without a decimal point."
  [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (json-str v)
    #?(:clj (integer? v) :cljs (and (number? v) (== v (Math/floor v)) (not (instance? js/Number v)))) (str v)
    #?(:clj (instance? Double v) :cljs (number? v)) (py-float-repr (double v))
    #?@(:clj [(instance? Float v) (py-float-repr (double v))])
    (map? v) (str "{" (str/join ", " (map (fn [[k val]] (str (json-str k) ": " (to-json val)))
                                          (omap-items v))) "}")
    (sequential? v) (str "[" (str/join ", " (map to-json v)) "]")
    :else (json-str (str v))))

#?(:clj
   (defn -main
     "CLI: weave the seed → concentration → print canonical JSON (for byte-parity cmp)."
     [& argv]
     (let [here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (first argv)
                  (str (clojure.java.io/file here "data" "seed-relation-graph.kotoba.edn")))
           g (weave (kedn/load-edn seed))
           c (concentration g)]
       (println (to-json c))
       0)))

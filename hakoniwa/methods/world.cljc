(ns hakoniwa.methods.world
  "hakoniwa 箱庭 — world-graph loader for the forward-simulation scenario.
  1:1 Clojure port of `methods/world.py` (ADR-2606111500).

  Reads a kotoba-EDN scenario graph (:sim/* nodes + :en/* 縁 over the
  hakoniwa-scenario-ontology) into plain maps. The world is a CONTAINED miniature (箱庭)
  populated ONLY by FICTIONAL latent personas — never real people.

  CONSTITUTIONAL (read before any change):
    G1 — every :persona is SYNTHETIC (:persona/synthetic true): a cohort archetype, NOT a real
      individual. No PII, no real-person profile, no re-identifiable trait. `assert-synthetic`
      enforces this at load time and refuses to load a graph that violates it.
    G2 — the only thing hakoniwa asserts is a DISTRIBUTION (see distribution.cljc); never a point.
    G3 — the simulation is routed to RESILIENCE; :outcome/use enumerates resilience uses only.

  House style: Python ':…' keyword strings stay strings (NOT clojure keywords) so the whole
  pipeline is string-keyed, byte-for-byte the same as the Python port. Maps preserve insertion
  order (mirroring Python dict order) via an ordered carrier — array-map ≤8 keys, ::order meta
  for any size. Pure fns; file I/O only at the #?(:clj) edge. Portable .cljc.

  The embedded EDN reader mirrors world.py's `_TOK`/`_atom`/`_parse`/`read_edn` — the same
  family as mitooshi.methods.analyze and shionome.methods.edn (logic byte-identical)."
  (:require [clojure.string :as str]
            [clojure.set]
            #?(:clj [clojure.java.io :as io])))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, "string", num, bool, nil) ──
;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
(def ^:private tok-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

;; fields that would indicate a real-person model — FORBIDDEN by G1 (no PII, ever)
(def forbidden-persona-fields
  #{":person/id" ":person/name" ":individual/id" ":user/id" ":account/id"
    ":email" ":phone" ":address" ":geo/point" ":device/id" ":biometric"
    ":real-name" ":dob" ":ssn" ":handle"})

(defn tokens
  "Lazy seq of significant tokens (capture group 1 of each tok-re match; ws/comments dropped).
  Mirrors world.py `_tokens`: yields only matches where group(1) is not None."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t) (step) (cons t (step))))))))))

(defn atom-of
  "Port of world.py `_atom`: \"…\" → unescaped string; true/false/nil → bool/nil;
  \":…\" kept as the literal \":ns/name\" string; int → long; else float; else raw string."
  [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= as-dbl ::nan) as-dbl t))))))

;; map keys preserve insertion order via an ordered carrier (array-map ≤8 keys; ::order meta
;; tracks order for any size) — mirrors Python dict insertion order, which the whole stack
;; depends on for byte-parity (node walk order, edge id derivation, signal exposure folds).
(defn- ordered-assoc [m k v]
  (let [had? (contains? m k)
        m' (assoc m k v)]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order (fnil conj []) k)))))

(declare parse-form)

(defn- parse-seq
  "Consume forms until the closing delim `close` (\"]\" or \"}\"); returns [coll next-i]."
  [toks i close build]
  (loop [i i, acc (build)]
    (let [t (nth toks i)]
      (if (= t close)
        [acc (inc i)]
        (let [[x i] (parse-form toks i)]
          (recur i (conj acc x)))))))

(defn- parse-form
  "Consume one form from the token vector at index i. Returns [value next-i]."
  [toks i]
  (let [t (nth toks i)]
    (cond
      (= t "[")
      (parse-seq toks (inc i) "]" (constantly []))

      (= t "{")
      (loop [i (inc i), m (with-meta {} {::order []})]
        (let [tk (nth toks i)]
          (if (= tk "}")
            [m (inc i)]
            (let [[k i] (parse-form toks i)
                  [v i] (parse-form toks i)]
              (recur i (ordered-assoc m k v))))))

      (or (= t "]") (= t "}")) [::end (inc i)]

      :else
      [(atom-of t) (inc i)])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches world.py read_edn → _parse(_tokens))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-form toks 0))))

(defn ordered-keys
  "Keys of a parsed map in insertion order (::order meta if present, else seq order)."
  [m]
  (or (::order (meta m)) (keys m)))

;; ── G1 gate ─────────────────────────────────────────────────────────────────
(defn assert-synthetic
  "G1: every persona MUST be synthetic and MUST carry no PII-class field. Raises (ex-info) on
  breach. `nodes` is a {nid node} map (insertion-ordered)."
  [nodes]
  (doseq [nid (ordered-keys nodes)]
    (let [n (get nodes nid)]
      (when (= (get n ":sim/kind") ":persona")
        (when-not (true? (get n ":persona/synthetic"))
          (throw (ex-info (str "G1 violation: persona " nid " is not marked :persona/synthetic true")
                          {:gate "G1" :persona nid})))
        (let [leaked (clojure.set/intersection (set (keys n)) forbidden-persona-fields)]
          (when (seq leaked)
            (throw (ex-info (str "G1 violation: persona " nid " carries PII-class field(s) " leaked)
                            {:gate "G1" :persona nid :leaked leaked}))))))))

;; ── load ──────────────────────────────────────────────────────────────────
(defn load-forms
  "Build (nodes_by_id, edges) from parsed scenario EDN forms; enforces G1. Returns
  {:nodes {nid node} :edges [edge…]}. `nodes` carries ::node-order meta = first-touch :sim/id
  order so downstream emit walks nodes in EDN read order (mirrors Python dict iteration)."
  [forms]
  (let [maps (filter map? forms)
        {:keys [nodes edges order]}
        (reduce (fn [acc f]
                  (cond
                    (contains? f ":sim/id")
                    (-> acc
                        (update :nodes ordered-assoc (get f ":sim/id") f)
                        (update :order conj (get f ":sim/id")))
                    (and (contains? f ":en/from") (contains? f ":en/to"))
                    (update acc :edges conj f)
                    :else acc))
                {:nodes (with-meta {} {::order []}) :edges [] :order []}
                maps)
        nodes (with-meta nodes (assoc (meta nodes) ::node-order order))]
    (assert-synthetic nodes)
    {:nodes nodes :edges edges}))

(defn node-order
  "First-touch :sim/id order (::node-order meta from load-forms), else ordered-keys."
  [nodes]
  (or (::node-order (meta nodes)) (ordered-keys nodes)))

#?(:clj
   (defn load-file*
     "Read + parse a scenario EDN graph file → {:nodes :edges}; enforces G1. File I/O edge."
     [path]
     (load-forms (read-edn (slurp (str path))))))

;; ── kind selectors (insertion order preserved) ───────────────────────────────
(defn- by-kind [nodes kind]
  (reduce (fn [m nid]
            (let [n (get nodes nid)]
              (if (= (get n ":sim/kind") kind) (ordered-assoc m nid n) m)))
          (with-meta {} {::order []})
          (node-order nodes)))

(defn personas [nodes] (by-kind nodes ":persona"))
(defn signals  [nodes] (by-kind nodes ":signal"))
(defn outcomes [nodes] (by-kind nodes ":outcome"))

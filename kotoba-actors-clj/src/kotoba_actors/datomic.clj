(ns kotoba-actors.datomic
  "A minimal, in-process kotoba-datomic-style substrate: turn kotoba-EDN entity
  rows into EAVT datoms, index them, and query them with a tiny datalog.

  This is the parallel-Clojure refactor of the Python actors' hand-rolled
  map-walking: instead of filtering vectors of maps, we model each fact as a
  datom [e a v] and answer questions with `q`. The Python actors are untouched.

  ── CONTRACT (the kotoba-code task is to implement every `todo` below) ────────
  A `db` is whatever `build-db` returns (an EAVT index of your choosing).
  `q` is a tiny conjunctive datalog: a query is

    {:find  [?sym ...]        ; symbols starting with ? are logic vars
     :where [[?e :attr ?v]    ; each clause is an [e a v] triple-pattern;
             [?e :a2 const]]} ; non-?-symbols / keywords / literals are constants

  `q` returns a SET of tuples (vectors), one per :find symbol, for every
  consistent binding of the where-clauses (joins share vars across clauses).

  ── ENGINE EXTENSIONS ─────────────────────────────────────────────────────────
  - `_` is a WILDCARD in any clause position: it matches anything and binds
    nothing (use it for don't-care values instead of an unused ?var).
  - `q` accepts `:in [?x ...]` plus trailing input args, e.g.
    (q '{:find [?e] :in [?k] :where [[?e :organism/kind ?k]]} db :species)
  - EVERY row becomes datoms — a row WITHOUT a `*/id` key (edge / 縁 rows keyed
    on :en/from + :en/to) gets a deterministic synthetic entity id, so edges are
    first-class queryable datoms (no need to fall back to raw `load-rows`)."
  (:require [clojure.edn :as edn]))

;; ── ingest ───────────────────────────────────────────────────────────────────

(defn load-rows
  "Read a kotoba-EDN seed file (a single top-level vector of entity maps).
  Returns the vector of maps. (This edge is allowed to do file I/O.)"
  [path]
  (edn/read-string (slurp path)))

(defn rows->datoms
  "Flatten entity-map rows into a seq of [e a v] datoms.

  The entity id `e` is the value of the row's `*/id` attribute (e.g. the value
  under :company/id, :supply.edge/id, :product/id, :part/id, :material/id,
  :bom.edge/id). Every OTHER key/value in the row becomes a datom [e k v].
  A row WITHOUT any `*/id` key (edge / 縁 rows keyed on :en/from + :en/to) is
  given a deterministic synthetic id `:kotoba-actors.row/N` (N = row index) so
  its facts are still datoms — edges become first-class, queryable entities.

  Rows may additionally carry a Datomic/Datascript tx-data bookkeeping
  `:db/id` (a tempid, e.g. added by the repo-wide EDN->tx-data 'datomize' pass,
  manifest/edn-datomize.cljs) alongside their domain `*/id`. `:db/id` is
  intentionally EXCLUDED from `*/id`-key candidacy — `(name :db/id)` is \"id\"
  and would otherwise match `.endsWith … \"id\"` and get picked as the row's
  identity, replacing the real domain id (e.g. :organism/id's string value)
  with a numeric tempid and silently breaking id-based joins (e.g.
  kotoba-actors.tsugite/dangling-edges comparing node ids against 縁
  endpoint strings). It is also excluded from becoming a datom itself
  (bookkeeping only, not domain data)."
  [rows]
  (apply concat
   (map-indexed
    (fn [idx row]
      (let [id-k (some #(when (and (not= % :db/id) (.endsWith (name %) "id")) %) (keys row))
            e    (if id-k (get row id-k) (keyword "kotoba-actors.row" (str idx)))]
        (for [[k v] row :when (not (#{id-k :db/id} k))] [e k v])))
    rows)))

(defn build-db
  "Build an EAVT-indexed db from a seq of [e a v] datoms. Shape is your choice
  (e.g. {:eav {e {a #{v}}} :aev {a {e #{v}}} :ave {a {v #{e}}}}) as long as `q`
  can answer triple-pattern joins against it."
  [datoms]
  (let [index (fn [acc [e a v]]
                (-> acc
                    (update-in [:eav e a] (fnil conj #{}) v)
                    (update-in [:aev a e] (fnil conj #{}) v)
                    (update-in [:ave a v] (fnil conj #{}) e)))]
    (reduce index {} datoms)))

(defn db-from-seed
  "Convenience: path -> rows -> datoms -> db."
  [path]
  (-> path load-rows rows->datoms build-db))

;; ── query ─────────────────────────────────────────────────────────────────────

(defn- lvar? [x]
  (and (symbol? x) (.startsWith (name x) "?")))

(defn- wildcard? [x] (= x '_))

(defn- const-pos
  "If clause position `x` is a literal constant (neither a logic var nor the `_`
  wildcard), resolve it (through the binding for safety); else nil = unconstrained."
  [binding x]
  (when-not (or (lvar? x) (wildcard? x))
    (get binding x x)))

(defn- match-clause
  "Given a binding map and a clause [e-pattern a-pattern v-pattern], return the
  subset of datoms that match the (possibly partially bound) pattern."
  [db binding clause]
  (let [[ep ap vp] clause
        e-const (const-pos binding ep)
        a-const (const-pos binding ap)
        v-const (const-pos binding vp)]
    (cond
      ;; E is bound/constant: scan the entity sub-index.
      e-const
      (for [[a vs] (get-in db [:eav e-const])
            :when (or (nil? a-const) (= a a-const))
            v vs
            :when (or (nil? v-const) (= v v-const))]
        [e-const a v])

      ;; A is bound/constant: scan the attribute sub-index.
      a-const
      (let [es (get-in db [:aev a-const])]
        (for [[e vs] es
               v (if v-const (if (contains? (set vs) v-const) [v-const] []) vs)]
          [e a-const v]))

      ;; No constants at all: full datom scan.
      :else
      (for [[e avs] (:eav db)
            [a vs] avs
            v vs]
        [e a v]))))

(defn- project-clause
  "Apply a clause to a binding map, returning a seq of extended binding maps.

  A logic var in the clause must UNIFY with the incoming binding: if it is
  already bound, the datom value must equal the existing binding; if it is free,
  it is bound to the datom value. This makes shared variables across clauses act
  as joins rather than cross-products."
  [db binding clause]
  (for [datom (match-clause db binding clause)
        :let [[ep ap vp] clause
              [e a v] datom]
        :when (and (or (not (lvar? ep)) (= (get binding ep e) e))
                   (or (not (lvar? ap)) (= (get binding ap a) a))
                   (or (not (lvar? vp)) (= (get binding vp v) v)))
        :let [b2 (-> binding
                     (cond-> (lvar? ep) (assoc ep e))
                     (cond-> (lvar? ap) (assoc ap a))
                     (cond-> (lvar? vp) (assoc vp v)))]]
    b2))

(defn q
  "Tiny conjunctive datalog. See ns docstring for the query shape.
  Optional `:in [?x ...]` binds the trailing `inputs` before the where-clauses
  run. Returns a set of result tuples (vectors aligned to :find)."
  [query db & inputs]
  (let [find-syms (:find query)
        in-syms   (:in query)
        init      (if (seq in-syms) (zipmap in-syms inputs) {})
        clauses   (:where query)
        results   (reduce (fn [bindings clause]
                            (mapcat #(project-clause db % clause) bindings))
                          [init]
                          clauses)]
    (into #{} (map (fn [b] (mapv #(get b %) find-syms))) results)))

#!/usr/bin/env bb
;; atsurae 誂え — Product Line Engineering (PLE) feature-model engine (clj-native, pure stdlib).
(ns atsurae.methods.feature-model
  "atsurae 誂え — the Product Line Engineering (PLE) feature-model engine the roster lacked
  (ADR-2606212010). open-kyber 開 covers ERP, uchiwake 内訳 covers per-product BOM — but
  neither manages a FAMILY of products derived from one model with shared commonality and
  bounded variability. atsurae is that layer: a feature model (mandatory / optional / xor-
  alternative / or groups + requires/excludes cross-tree constraints), from which valid
  VARIANTS are derived (誂え = bespoke, configured-to-order), each variant's BOM composed from
  the parts its selected features bind.

  A feature model is a COMMONS spec, never a license key — atsurae binds no product to a vendor
  (G1: :atsurae/license-lock / :atsurae/drm unrepresentable). It is SPEC + DERIVATION only;
  it never manufactures (G2: :atsurae/manufacture unrepresentable — sanae/giemon/sarutahiko/
  funadaiku build, under Council gate). Validity is STRUCTURAL (constraint satisfaction), not a
  good/bad product verdict (G3, non-adjudicating).

  ── the model ──
  FEATURE {:id :parent :kind :group}
    :kind  ∈ {:root :mandatory :optional}   (governs a NON-grouped child)
    :group ∈ {:xor :or nil}                 (cardinality this feature imposes on ITS children:
                                             :xor = exactly 1 selected, :or = ≥1 selected)
  CONSTRAINT {:kind :from :to}   :kind ∈ {:requires :excludes}
  BINDING {:feature :parts [{:part :qty}…]}   feature → parts for BOM derivation

  ── what it computes ──
  valid-config?      structural + constraint satisfaction for a given selection
  variants           enumerate every valid complete configuration (bounded)
  commonality        feature → fraction of variants that include it (1.0 = common platform)
  variation-points   features with 0 < commonality < 1 (where the family actually varies)
  derive-bom         a variant's bill of materials (parts ∪ from selected features, qty summed)"
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

;; ── load ─────────────────────────────────────────────────────────────────────

(defn- unblob
  "seed.edn rows may carry pr-str'd (blob) values for non-scalar attrs (datomize
  transform, e.g. :atsurae.binding/parts). Read them back to live EDN; pass through
  unchanged otherwise."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :default :default) _ v))
    v))

(defn- reconstitute-row
  "Tolerates seed.edn rows in EITHER shape:
    legacy bare map    — {:type :feature :id :robot-base ...}
    datomized tx-data  — {:db/id -1 :atsurae.feature/type :feature :atsurae.feature/id :robot-base ...}
  Un-namespaces + un-blobs a tx-data row back to the original bare-key shape so
  downstream :type/:id/:parent/:kind/:group/:from/:to/:feature/:parts lookups keep
  working unchanged either way."
  [row]
  (if (contains? row :db/id)
    (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)])) (dissoc row :db/id))
    row))

(defn classify
  "Split the flat seed vector by :type → {:features :constraints :bindings}. Tolerates
  both the legacy bare-map seed.edn shape and the datomized tx-data shape (each row a
  {:db/id ... :atsurae.feature/* | :atsurae.constraint/* | :atsurae.binding/* ...} entity)."
  [rows]
  (let [rows (map reconstitute-row rows)]
    {:features    (vec (filter #(= (:type %) :feature) rows))
     :constraints (vec (filter #(= (:type %) :constraint) rows))
     :bindings    (vec (filter #(= (:type %) :binding) rows))}))

#?(:clj (defn load-edn [path] (clojure.edn/read-string (slurp path))))

(defn- by-id [features] (into {} (map (juxt :id identity) features)))
(defn- children [features id] (filter #(= id (:parent %)) features))
(defn- root-id [features] (some #(when (= :root (:kind %)) (:id %)) features))

;; ── structural + constraint validation (for an arbitrary selection) ───────────

(defn valid-config?
  "Validate a selection `sel` (set of feature ids) against the feature model + constraints.
  Returns {:valid? bool :violations [[kind & args]…]}."
  [{:keys [features constraints]} sel]
  (let [byid (by-id features)
        root (root-id features)
        vs (transient [])]
    ;; root present
    (when-not (contains? sel root) (conj! vs [:missing-root root]))
    ;; every selected feature's parent is selected (no orphans)
    (doseq [id sel :let [p (:parent (byid id))]]
      (when (and p (not (contains? sel p))) (conj! vs [:orphan id p])))
    ;; per-feature child rules
    (doseq [f features
            :let [id (:id f) p (:parent f)
                  parent-sel? (or (nil? p) (contains? sel p))]
            :when parent-sel?]
      (let [kids (children features id)
            ksel (filter #(contains? sel (:id %)) kids)]
        (cond
          ;; grouped feature: cardinality applies only when the group feature itself is selected
          (and (= :xor (:group f)) (contains? sel id) (not= 1 (count ksel)))
          (conj! vs [:xor-violation id (count ksel)])
          (and (= :or (:group f)) (contains? sel id) (< (count ksel) 1))
          (conj! vs [:or-violation id]))
        ;; non-grouped: mandatory children of a selected parent must be selected
        (when (nil? (:group f))
          (doseq [c kids :when (and (= :mandatory (:kind c)) (not (contains? sel (:id c))))]
            (conj! vs [:missing-mandatory (:id c) id])))))
    ;; cross-tree constraints
    (doseq [c constraints]
      (case (:kind c)
        :requires (when (and (contains? sel (:from c)) (not (contains? sel (:to c))))
                    (conj! vs [:requires (:from c) (:to c)]))
        :excludes (when (and (contains? sel (:from c)) (contains? sel (:to c)))
                    (conj! vs [:excludes (:from c) (:to c)]))
        nil))
    (let [violations (persistent! vs)]
      {:valid? (empty? violations) :violations violations})))

;; ── variant enumeration (bounded) ────────────────────────────────────────────

(defn- cartesian
  "Seq of seqs → seq of vectors (one pick from each)."
  [colls]
  (if (empty? colls) [[]]
      (for [x (first colls) more (cartesian (rest colls))] (cons x more))))

(defn- nonempty-subsets [xs]
  (let [v (vec xs) n (count v)]
    (when (> n 16) (throw (ex-info "or-group too large to enumerate (>16)" {:n n})))
    (for [bits (range 1 (bit-shift-left 1 n))]
      (vec (keep-indexed (fn [i x] (when (bit-test bits i) x)) v)))))

(defn subtree-configs
  "All valid selections for the subtree rooted at feature `id`, GIVEN `id` is selected.
  Returns a seq of sets (each contains `id`). Structural only — constraints filtered later."
  [features id]
  (let [byid (by-id features)
        f (byid id)
        kids (vec (children features id))]
    (if (empty? kids)
      [#{id}]
      (cond
        (= :xor (:group f))
        ;; exactly one child; that child contributes one of its subtree configs
        (for [c kids cfg (subtree-configs features (:id c))] (conj cfg id))

        (= :or (:group f))
        ;; a non-empty subset of children, each contributing one subtree config
        (for [subset (nonempty-subsets kids)
              combo  (cartesian (map #(subtree-configs features (:id %)) subset))]
          (conj (apply set/union #{} combo) id))

        :else
        ;; no group: mandatory kids always (one config each); optional kids absent or one config
        (let [mand (filter #(= :mandatory (:kind %)) kids)
              opt  (filter #(= :optional (:kind %)) kids)
              mand-opts (mapv #(subtree-configs features (:id %)) mand)
              opt-opts  (mapv #(cons #{} (subtree-configs features (:id %))) opt)
              all (concat mand-opts opt-opts)]
          (for [combo (cartesian all)] (conj (apply set/union #{} combo) id)))))))

(defn variants
  "Every VALID complete variant (structural enumeration ∩ constraint satisfaction). Each is a
  set of feature ids including the root. Deterministic (sorted by size then by sorted contents)."
  [{:keys [features constraints] :as model}]
  (let [root (root-id features)
        structural (subtree-configs features root)
        valid (filter #(:valid? (valid-config? model %)) structural)]
    (->> valid
         (sort-by (fn [s] [(count s) (vec (sort (map str s)))]))
         vec)))

;; ── commonality / variability ─────────────────────────────────────────────────

(defn commonality
  "feature id → fraction of `vs` variants that include it (1.0 = common platform; 0<·<1 = a
  variation point; 0.0 = never realisable under the constraints)."
  [{:keys [features]} vs]
  (let [n (count vs)]
    (into {} (for [f features]
               [(:id f) (if (zero? n) 0.0 (/ (count (filter #(contains? % (:id f)) vs))
                                             (double n)))]))))

(defn variation-points
  "Features whose commonality is strictly between 0 and 1 — where the product family varies."
  [comm]
  (->> comm (filter (fn [[_ c]] (and (> c 0.0) (< c 1.0)))) (map first) (sort-by str) vec))

;; ── variant → BOM derivation (composes with uchiwake / open-kyber) ────────────

(defn derive-bom
  "A variant's bill of materials: parts ∪ over the bindings of its selected features, qty summed.
  Returns part-id → total qty."
  [{:keys [bindings]} sel]
  (->> bindings
       (filter #(contains? sel (:feature %)))
       (mapcat :parts)
       (reduce (fn [m p] (update m (:part p) (fnil + 0) (or (:qty p) 1))) {})))

;; ── analysis summary + report (a commons spec map, never a license key) ───────

(defn analyze [model]
  (let [vs (variants model)
        comm (commonality model vs)
        vps (variation-points comm)]
    {:n-features (count (:features model))
     :n-variants (count vs)
     :commonality comm
     :variation-points vps
     :platform (->> comm (filter (fn [[_ c]] (>= c 1.0))) (map first) (sort-by str) vec)
     :dead (->> comm (filter (fn [[_ c]] (<= c 0.0))) (map first) (sort-by str) vec)
     :variants vs}))

(defn- pct [x] (format "%.0f%%" (* 100.0 (double x))))

(defn report-md [model]
  (let [a (analyze model)
        comm (:commonality a)
        ;; a representative bespoke variant: the largest (most-featured) valid one
        sample (last (:variants a))
        bom (derive-bom model sample)]
    (str
     "# atsurae 誂え — product-line feature-model report\n\n"
     "A feature model is a COMMONS spec, never a license key (no vendor lock). SPEC + DERIVATION "
     "only — atsurae never manufactures (sanae/giemon/sarutahiko/funadaiku build, under Council "
     "gate). Validity is STRUCTURAL, not a good/bad verdict. ADR-2606212010.\n\n"
     "**Family**: " (:n-features a) " features → **" (:n-variants a) "** valid variants · "
     (count (:platform a)) " common-platform features · " (count (:variation-points a))
     " variation points.\n\n"
     "## Commonality (platform vs. variation)\n\n"
     "| feature | commonality | role |\n|---|---:|---|\n"
     (str/join "\n"
       (for [[fid c] (sort-by (fn [[fid c]] [(- c) (str fid)]) comm)]
         (str "| " (name fid) " | " (pct c) " | "
              (cond (>= c 1.0) "platform (in every variant)"
                    (<= c 0.0) "dead (constraint-unreachable)"
                    :else "variation point") " |")))
     "\n\n## A bespoke variant (誂え) → derived BOM (→ uchiwake / open-kyber)\n\n"
     "selected: " (str/join ", " (map name (sort-by str sample))) "\n\n"
     "| part | qty |\n|---|---:|\n"
     (str/join "\n" (for [[p q] (sort-by key bom)] (str "| " (name p) " | " q " |")))
     "\n\n_The derived BOM hands off to uchiwake 内訳 (product BOM KG) + open-kyber 開 (ERP). "
     "A configured variant is a spec, never an order to build — manufacture is sanae/giemon/… "
     "under Council gate (G2).\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/atsurae/kotoba/seed.edn")
           model (classify (load-edn seed-path))
           a (analyze model)]
       (println (report-md model))
       (println (str "-- " (:n-features a) " features → " (:n-variants a) " valid variants --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))

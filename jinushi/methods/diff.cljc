(ns jinushi.methods.diff
  "jinushi 地主 — as-of DIFF (差分) between two ingest snapshots.

  Ownership is a moving target: re-ingesting a source yields a new snapshot, and what matters is
  WHAT CHANGED (a parcel changed hands, a building gained floors, an owner appeared/vanished).
  This computes added / removed / changed records between an OLD and a NEW snapshot keyed on the
  record's natural id — the as-of delta that feeds the append-only kotoba Datom log (Wellbecoming
  = trajectory, not snapshot). Pure + content-addressable; the diff itself is provenance."
  (:require [clojure.string :as str]))

(defn index-by
  "Index records by key-fn, skipping any record whose key is nil (a record missing the key would
  otherwise poison the later `sort` of the key set with a nil/heterogeneous comparator)."
  [key-fn records]
  (into {} (comp (map (juxt key-fn identity)) (filter (comp some? first))) records))

(defn diff
  "Compare old vs new record seqs keyed by `key-fn`. `compare-fields` (optional) restricts the
  change check to those keys (default: full record). Returns
  {:added [...] :removed [...] :changed [{:key :before :after :fields}] :unchanged n :counts {…}}."
  ([key-fn old new] (diff key-fn old new nil))
  ([key-fn old new compare-fields]
   (let [oi (index-by key-fn old) ni (index-by key-fn new)
         ok (set (keys oi)) nk (set (keys ni))
         added-keys (sort (remove ok nk))
         removed-keys (sort (remove nk ok))
         common (sort (filter ok nk))
         pick (fn [r] (if compare-fields (select-keys r compare-fields) r))
         changed (keep (fn [k]
                         (let [a (pick (oi k)) b (pick (ni k))]
                           (when (not= a b)
                             (let [fields (sort (distinct (concat (keys a) (keys b))))
                                   diffd (filter #(not= (get a %) (get b %)) fields)]
                               {:key k :before a :after b :fields (vec diffd)}))))
                       common)]
     {:added (mapv ni added-keys)
      :removed (mapv oi removed-keys)
      :changed (vec changed)
      :unchanged (- (count common) (count changed))
      :counts {:old (count old) :new (count new)
               :added (count added-keys) :removed (count removed-keys)
               :changed (count changed)}})))

(defn snapshot-diff
  "Diff two parsed snapshots (maps with :records). Auto-picks the key: :parcel/id, else :building,
  else :owner/key, else :record/id."
  ([old-snap new-snap] (snapshot-diff old-snap new-snap nil))
  ([old-snap new-snap compare-fields]
   (let [recs (:records new-snap)
         key-fn (cond (some :parcel/id recs) :parcel/id
                      (some :building recs)  :building
                      (some :owner/key recs) :owner/key
                      :else (some-fn :record/id :parcel/id :building))]
     (assoc (diff key-fn (:records old-snap) (:records new-snap) compare-fields)
            :source-id (:source-id new-snap)))))

(defn summary
  "One-line-per-section human summary of a diff result."
  [d]
  (let [c (:counts d)]
    (str/join "\n"
      (concat
       [(format "diff: +%d added, -%d removed, ~%d changed (old %d → new %d, %d unchanged)"
                (:added c) (:removed c) (:changed c) (:old c) (:new c) (:unchanged d))]
       (for [ch (take 8 (:changed d))]
         (format "  ~ %s : %s" (:key ch) (str/join ", " (map name (:fields ch)))))))))

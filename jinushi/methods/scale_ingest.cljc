(ns jinushi.methods.scale-ingest
  "jinushi 地主 — PRODUCTION-SCALE streaming ingest (R2 toward full-bulk).

  The sample-scale methods (nyc_pluto / dvf_values) slurp the whole file into a vector of record
  maps — fine for 1k–10k rows, but the production targets are full bulk: NYC PLUTO ~860k tax lots,
  nationwide DVF (~millions of mutations). This module streams a large CSV LINE BY LINE (never
  materializing the full vector-of-maps), keeping only bounded accumulators, and returns the same
  aggregate shape the sample methods produce — so the operator can run the SAME gates at scale.

  Charter holds at scale (the whole point): natural-person names are anonymized ON THE FLY
  (sha256-key, never accumulated) — the same publish-prudence as nyc_pluto, so a 860k-lot run never
  holds or emits an ordinary individual's name. DVF carries no owner identity. Aggregate-first (G2);
  no per-row record is retained beyond its contribution to the running aggregate.

  Operator-run (the bulk file is downloaded once by the operator via PRODUCTION.md, never by the
  loop — 負担をかけない): this is the pipeline, not a fetch."
  (:require [clojure.string :as str]
            [jinushi.methods.nyc-pluto :as pluto]
            #?(:clj [clojure.java.io :as io])))

(defn- median [xs]
  (let [v (vec (sort xs)) n (count v)]
    (when (pos? n) (if (odd? n) (nth v (quot n 2))
                      (/ (+ (nth v (dec (quot n 2))) (nth v (quot n 2))) 2.0)))))

(defn- num [s] (when (and s (not (str/blank? s))) (try (Double/parseDouble s) (catch #?(:clj Exception :cljs :default) _ nil))))

;; ── streaming DVF aggregate (bounded: per-type value vectors + per-commune; no record-map vector) ──
(defn dvf-aggregate-step
  "Fold one DVF CSV row (already comma-split) into the running aggregate `acc`."
  [acc cols]
  (let [price (num (nth cols 4 nil)) mutation (nth cols 0 nil)
        cc (nth cols 10 nil) type (nth cols 30 nil) bati (num (nth cols 31 nil))]
    (cond-> acc
      mutation (update-in [:mutations mutation] (fnil identity price))   ;; one price per mutation id
      (and type (not (str/blank? type)) price bati (pos? bati))
      (update-in [:type-eurm2 type] (fnil conj []) (/ price bati))
      (and (= "Appartement" type) price bati (pos? bati) cc)
      (update-in [:commune-appt cc] (fnil conj []) (/ price bati)))))

(defn dvf-finalize [acc]
  {:mutations (count (:mutations acc))
   :total-value-eur (reduce + 0.0 (filter some? (vals (:mutations acc))))
   :by-type (into (sorted-map) (map (fn [[t xs]] [t {:lines (count xs) :median-eur-m2 (median xs)}]) (:type-eurm2 acc)))
   :by-commune (into (sorted-map) (map (fn [[c xs]] [c {:appt-median-eur-m2 (median xs) :appt-lines (count xs)}]) (:commune-appt acc)))})

#?(:clj
   (defn dvf-stream-file
     "Bounded-memory streaming aggregate of a (possibly huge) geo-dvf CSV: reads line-by-line,
     never holds the full record-map vector."
     [path]
     (with-open [r (io/reader path)]
       (dvf-finalize
        (reduce (fn [acc line] (dvf-aggregate-step acc (str/split line #"," -1)))
                {} (rest (line-seq r)))))))   ;; rest = drop header

;; ── streaming PLUTO aggregate (person names anonymized on the fly; never accumulated) ──
(defn pluto-row->owner
  "Map a PLUTO row {:ownername :numfloors :bbl :bldgclass} → {:key :type :name|nil :floors}.
  Person name is hashed into :key and DROPPED (publish-prudence) — never returned in plaintext."
  [{:keys [ownername numfloors]}]
  (let [o (pluto/org? ownername)]
    {:key (pluto/owner-key ownername o) :type (if o :org :natural-person)
     :name (when o ownername) :floors (some-> numfloors num Math/round)}))

(defn pluto-aggregate-step [acc row]
  (let [{:keys [key type name floors]} (pluto-row->owner row)]
    (-> acc
        (update-in [:owner-types type] (fnil inc 0))
        (update-in [:parcels] (fnil inc 0))
        (update-in [:owners key :lots] (fnil inc 0))
        (update-in [:owners key :floors] (fnil + 0) (or floors 0))
        (assoc-in [:owners key :type] type)
        (cond-> name (assoc-in [:owners key :name] name)))))   ;; name only for orgs

(defn pluto-finalize [acc]
  {:parcels (or (:parcels acc) 0)
   :owner-types (:owner-types acc)
   :distinct-owners (count (:owners acc))
   :top-org-owners (->> (:owners acc) (filter (fn [[_ v]] (= :org (:type v))))
                        (map (fn [[k v]] {:owner k :name (:name v) :lots (:lots v) :floors (:floors v)}))
                        (sort-by :lots >) (take 10) vec)})

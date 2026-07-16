(ns jinushi.methods.datom-emit
  "jinushi 地主 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).

  Projects the world land-ownership ACQUISITION view into append-only kotoba Datoms
  [e a v tx op].

    GROUND (durable, op :add) — :owner/* node datoms + :parcel/* node datoms. The ingested
      registry record IS the canonical acquisition state; it is a DISCLOSED fact (G2).
    DERIVED (transient, :bond/is-transient) — land-取-concentration (HHI, top-holder share),
      acquisition coverage (world fraction), and ADVISORY commons-return candidates. Computed
      on READ, never persisted as a verdict (G2), and routed to a human/Council, never a
      write-back to the LandRegistry (G3 — jinushi cannot move land).

  G1 — the emitted log carries NO :person/* dimension and NO precise dwelling coordinate:
    natural-person land is an :owner/aggregate bucket; centroids are coarse region centroids.
    Enforced structurally here + asserted by tests (no :person token may appear)."
  (:require [clojure.string :as str]
            [jinushi.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

(def owner-attrs [:owner/name :owner/type :owner/aggregate])
(def parcel-attrs [:parcel/country :parcel/region :parcel/area-m2 :parcel/owner
                   :parcel/source :owner/name-norm :owner/type])

(defn- fmt-g
  "Render a double with 6 significant digits, trailing zeros stripped; integral → no point."
  [v]
  (let [d (double v)]
    (if (and (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn fmt
  "Datom value formatter: keyword → :kw; string → escaped+quoted; bool → true/false;
  nil → nil; non-integer number → {:g}; integer → plain."
  [v]
  (cond
    (keyword? v) (str v)
    (nil? v) "nil"
    (true? v) "true"
    (false? v) "false"
    (string? v) (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\"")
    (and (number? v) (not (integer? v))) (fmt-g v)
    :else (str v)))

(defn- eid
  "Stable entity keyword for a string id (dots/hyphens kept): \"JP-13-0001\" → :parcel.JP-13-0001."
  [prefix id]
  (keyword (str prefix "." id)))

(defn emit
  "Faithful EAVT emit. Returns the kotoba Datom-log EDN text (trailing newline).
  `data` is the raw seed map {:owners :parcels}; `res` is (analyze/analyze data)."
  ([data res] (emit data res 1))
  ([data res tx]
   (let [L (transient [])
         owners-idx (into {} (map (juxt :owner/key identity)) (:owners data))]
     (conj! L ";; jinushi 地主 — GENERATED kotoba Datom log. DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable (owner + parcel registry records, DISCLOSED facts).")
     (conj! L ";; DERIVED :bond/is-transient = concentration/coverage computed on read (G2).")
     (conj! L ";; G1: natural-person land folds to :owner/aggregate; centroids coarse; no individual dimension.")
     (conj! L "[")

     ;; ── GROUND: owner node datoms (seed order)
     (doseq [o (:owners data)]
       (let [e (eid "owner" (:owner/key o))]
         (doseq [a owner-attrs]
           (when (contains? o a)
             (conj! L (str "[" (fmt e) " " a " " (fmt (get o a)) " " tx " :add]"))))))

     ;; ── GROUND: parcel node datoms (deterministic by parcel id)
     (doseq [p (sort-by :parcel/id (:parcels res))]
       (let [e (eid "parcel" (:parcel/id p))]
         (doseq [a parcel-attrs]
           (when (contains? p a)
             (conj! L (str "[" (fmt e) " " a " " (fmt (get p a)) " " tx " :add]"))))
         ;; centroid coarse [lat lon] — emit as two scalar datoms, coarse only (G1)
         (when-let [[lat lon] (:parcel/centroid p)]
           (conj! L (str "[" (fmt e) " :parcel/centroid-lat " (fmt (double lat)) " " tx " :add]"))
           (conj! L (str "[" (fmt e) " :parcel/centroid-lon " (fmt (double lon)) " " tx " :add]")))))

     ;; ── DERIVED: concentration (transient)
     (conj! L ";; ── DERIVED concentration (transient; aggregate of disclosed areas) ──")
     (let [c (:concentration res)]
       (conj! L (str "[:world.land :jinushi/hhi " (fmt (:hhi c)) " " tx " :derived] ;; :bond/is-transient true"))
       (conj! L (str "[:world.land :jinushi/owner-count " (fmt (:owner-count c)) " " tx " :derived] ;; :bond/is-transient true"))
       (when-let [th (:top-holder c)]
         (conj! L (str "[:world.land :jinushi/top-holder " (fmt (eid "owner" (:key th)))
                       " " tx " :derived] ;; :bond/is-transient true"))
         (conj! L (str "[:world.land :jinushi/top-holder-share " (fmt (:share th))
                       " " tx " :derived] ;; :bond/is-transient true"))))

     ;; ── DERIVED: acquisition coverage (transient)
     (conj! L ";; ── DERIVED acquisition coverage (transient) ──")
     (let [cov (:coverage res)]
       (conj! L (str "[:world.land :jinushi/countries-touched " (fmt (:countries-touched cov)) " " tx " :derived] ;; :bond/is-transient true"))
       (conj! L (str "[:world.land :jinushi/acquired-area-km2 " (fmt (:acquired-area-km2 cov)) " " tx " :derived] ;; :bond/is-transient true"))
       (conj! L (str "[:world.land :jinushi/world-coverage-frac " (fmt (:world-coverage-frac cov)) " " tx " :derived] ;; :bond/is-transient true")))

     ;; ── DERIVED: routed commons-return candidates (advisory; aggregate; G1)
     (conj! L ";; ── routed RETURN-to-commons candidates (transient; advisory to Council, never a write-back — G1/G3) ──")
     (doseq [r (:return-candidates res)]
       (conj! L (str "[:world.land :jinushi/return-candidate " (fmt (eid "owner" (:owner r)))
                     " " tx " :derived] ;; :bond/is-transient true share=" (fmt (:share r)))))

     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*)) .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-parcels.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx")))) 1)
           data (analyze/load-file* seed)
           res (analyze/analyze data)
           out (io/file outdir "jinushi-datoms.kotoba.edn")]
       (.mkdirs outdir)
       (spit out (emit data res tx))
       (println (str "jinushi datom log → " out " (" (count (:owners data)) " owners + "
                     (count (:parcels res)) " parcels, tx=" tx ")"))
       0)))

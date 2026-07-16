(ns hoshimori.methods.ingest
  "hoshimori 星守 — orbital-catalog ingest bridge (ADR-2606073600 §G7).
  1:1 Clojure port of `methods/ingest.py`.

  Bridges the PUBLIC CelesTrak SATCAT (https://celestrak.org/pub/satcat.csv) into the
  orbital organism graph as **AGGREGATE-ONLY** stewardship facts, then merges with the
  curated seed (seed wins on :organism/id collision; new owners append).

  G1 (constitutional — the hard rule this file is built around): hoshimori is a
  STEWARDSHIP map, NEVER a targeting / interception aid. This ingest therefore emits
  **only regime-aggregate object COUNTS per owner and per orbital shell** — it NEVER
  stores a per-object state vector (apogee/perigee/inclination/epoch). The per-object
  orbital elements in the SATCAT are read transiently to BUCKET an object into a shell
  regime and are then discarded; only the bucket COUNTS persist. ASAT / kinetic-intercept
  uses stay unrepresentable (§1.12): there is no per-object positional datom to target.

  NETWORK DISCIPLINE (G7): live fetch requires HOSHIMORI_OPERATOR_GATE=1 (Council+operator).
  Offline default reads a pre-downloaded data/ingest/satcat.csv if present, else re-emits
  the seed unchanged. Catalog-derived counts are :authoritative; the seed sample stays as-is.

  House style (mirrors analyze.cljc / datom_emit.cljc): pure fns; Python ':…' keyword
  strings stay strings; file/network I/O only at the edge in `#?(:clj -main)`. Portable .cljc."
  (:require [clojure.string :as str]))

(def satcat-url "https://celestrak.org/pub/satcat.csv")

;; CelesTrak SATCAT OWNER code → [id-slug display ISO-3166-jurisdiction]. Bounded map of the
;; major catalog owners; unmapped codes pass through with the raw code as jurisdiction.
(def owner
  {"US"   ["us" "United States" "US"]            "CIS"  ["ru" "Russia / CIS" "RU"]
   "PRC"  ["cn" "China (PRC)" "CN"]              "ESA"  ["esa" "European Space Agency" "EU"]
   "JPN"  ["jp" "Japan" "JP"]                    "IND"  ["in" "India" "IN"]
   "FR"   ["fr" "France" "FR"]                   "UK"   ["gb" "United Kingdom" "GB"]
   "GER"  ["de" "Germany" "DE"]                  "ITSO" ["itso" "Intelsat (ITSO)" "INT"]
   "SES"  ["lu" "SES (Luxembourg)" "LU"]         "ORB"  ["orb" "Orbcomm" "US"]
   "GLOB" ["glob" "Globalstar" "US"]             "SKOR" ["kr" "South Korea" "KR"]
   "TWN"  ["tw" "Taiwan" "TW"]                   "CA"   ["ca" "Canada" "CA"]
   "LUXE" ["lu2" "Luxembourg" "LU"]              "NETH" ["nl" "Netherlands" "NL"]
   "SPN"  ["es" "Spain" "ES"]                    "ITLY" ["it" "Italy" "IT"]
   "BRAZ" ["br" "Brazil" "BR"]                   "AUS"  ["au" "Australia" "AU"]
   "SAFR" ["za" "South Africa" "ZA"]             "ISRA" ["il" "Israel" "IL"]
   "ARGN" ["ar" "Argentina" "AR"]                "TURK" ["tr" "Turkey" "TR"]
   "UAE"  ["ae" "United Arab Emirates" "AE"]     "INDO" ["id" "Indonesia" "ID"]
   "EUME" ["eume" "EUMETSAT" "EU"]               "EUTE" ["eute" "Eutelsat" "FR"]
   "NOR"  ["no" "Norway" "NO"]                   "SAUD" ["sa" "Saudi Arabia" "SA"]
   "THAI" ["th" "Thailand" "TH"]                 "MEX"  ["mx" "Mexico" "MX"]
   "VENZ" ["ve" "Venezuela" "VE"]                "EGYP" ["eg" "Egypt" "EG"]
   "PAKI" ["pk" "Pakistan" "PK"]})

(defn- to-double
  "Python float(x) with the (ValueError, TypeError) guard → nil on failure."
  [x]
  (try
    (cond (nil? x) nil
          (number? x) (double x)
          (= "" (str/trim (str x))) nil
          :else (Double/parseDouble (str/trim (str x))))
    (catch Exception _ nil)))

(defn regime
  "Bucket an object into a shell regime from its orbital elements (then discard them, G1).
  Returns \":geo\" | \":heo\" | \":leo\" | \":meo\" | nil."
  [apogee perigee period]
  (let [ap (to-double apogee)
        pe (to-double perigee)
        _pd (or (to-double period) 0.0)]
    (cond
      (or (nil? ap) (nil? pe)) nil
      (<= ap 0) nil
      (and (<= 35000 ap 37000) (>= pe 33000)) ":geo"
      (or (> ap 37000) (> (- ap pe) 20000)) ":heo"
      (<= ap 2000) ":leo"
      (<= ap 35000) ":meo"
      :else ":geo")))

(defn- get-trim [r k] (str/trim (or (get r k) "")))

(defn aggregate
  "SATCAT rows (header-keyed maps) → [owners regimes].
  owners: code → {:pay :rb :deb :total}; regimes: regime-string → on-orbit payload+body count.
  No per-object retention (G1)."
  [rows]
  (loop [rows rows
         owners {}
         regimes {}]
    (if-let [r (first rows)]
      (if (seq (get-trim r "DECAY_DATE"))   ; decayed = no longer on orbit
        (recur (rest rows) owners regimes)
        (let [code  (let [o (get-trim r "OWNER")] (if (seq o) o "TBD"))
              otype (get-trim r "OBJECT_TYPE")
              o (get owners code {:pay 0 :rb 0 :deb 0 :total 0})
              o (update o :total inc)
              o (cond (= otype "PAY") (update o :pay inc)
                      (= otype "R/B") (update o :rb inc)
                      (= otype "DEB") (update o :deb inc)
                      :else o)
              reg (regime (get r "APOGEE") (get r "PERIGEE") (get r "PERIOD"))
              regimes (if (and reg (contains? #{"PAY" "R/B"} otype))
                        (update regimes reg (fnil inc 0))
                        regimes)]
          (recur (rest rows) (assoc owners code o) regimes)))
      [owners regimes])))

(defn- s
  "Port of _s: quote + escape backslash and double-quote → an EDN string literal."
  [x]
  (str "\"" (-> (str x) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))

(defn emit-operator
  "Port of emit_operator: one per-owner aggregate node EDN string (counts only)."
  [code agg]
  (let [[slug label juris] (get owner code [(-> code str/lower-case (str/replace "/" "-")) code code])]
    (str "{:organism/id \"orbit.cat." slug "\" :organism/kind :operator :organism/label "
         (s (str label " (cataloged objects)")) " "
         ":op/kind :catalog-owner :op/jurisdiction \"" juris "\" :op/object-count " (:total agg) " "
         ":op/payload-count " (:pay agg) " :op/rocket-body-count " (:rb agg)
         " :op/debris-count " (:deb agg) " :organism/sourcing :authoritative}")))

(defn emit-occupancy
  "Port of emit_occupancy: one per-regime on-orbit occupancy node EDN string."
  [reg n]
  (let [bare (str/replace reg #"^:+" "")]
    (str "{:organism/id \"orbit.occ." bare "\" :organism/kind :occupancy :organism/label "
         (s (str "On-orbit occupancy " (str/upper-case bare))) " "
         ":occ/regime " reg " :occ/on-orbit-count " n " :organism/sourcing :authoritative}")))

(defn merge-graph
  "Pure core of main(): seed text + aggregated counts → merged graph EDN text.
  `seed-text` is the curated seed (rstripped); returns the merged document string.
  Mirrors the body-splice in ingest.py main() (seed wins; aggregate counts appended)."
  [seed-text owners regimes]
  (let [top (->> owners
                 (sort-by (fn [[_ a]] (- (:total a))))
                 (filter (fn [[_ a]] (>= (:total a) 20)))
                 (take 60))
        ops (map (fn [[c a]] (emit-operator c a)) top)
        occ (map (fn [reg] (emit-occupancy reg (get regimes reg))) (sort (keys regimes)))
        body (-> seed-text (subs 0 (str/last-index-of seed-text "]")) str/trimr)
        extras (str "\n ;; ── CelesTrak SATCAT aggregate ingest (:authoritative; counts only, G1 no-ephemeris) ──\n "
                    (str/join "\n " (concat ops occ)))]
    (str body extras "\n]\n")))

#?(:clj
   (do
     (require '[clojure.java.io :as io]
              '[clojure.data.csv :as csv])

     (defn- rows-from-csv
       "DictReader-equivalent: header row → seq of header-keyed maps."
       [reader]
       (let [[header & data] (csv/read-csv reader)]
         (map #(zipmap header %) data)))

     (defn- fetch-satcat! [dest]
       (when (not= (System/getenv "HOSHIMORI_OPERATOR_GATE") "1")
         (binding [*out* *err*]
           (println "refused: live CelesTrak fetch requires HOSHIMORI_OPERATOR_GATE=1 (G7 Council+operator)."))
         (System/exit 1))
       (let [conn (.openConnection (java.net.URL. satcat-url))]
         (.setRequestProperty conn "User-Agent" "etzhayyim-hoshimori research jun@etzhayyim.group")
         (.setConnectTimeout conn 60000)
         (.setReadTimeout conn 60000)
         (with-open [in (.getInputStream conn)]
           (io/copy in (io/file dest)))))

     (defn -main
       "CLI entry (file/network I/O at the edge). Mirrors ingest.py main(argv)."
       [& argv]
       (let [argv   (vec argv)
             here   (-> *file* io/file .getParentFile .getParentFile)
             seed   (io/file here "data" "seed-orbit-graph.kotoba.edn")
             out    (io/file here "data" "orbit-catalog.merged.kotoba.edn")
             ingest (io/file here "data" "ingest")
             satcat (io/file ingest "satcat.csv")]
         (.mkdirs ingest)
         (when (some #{"--fetch"} argv)
           (fetch-satcat! satcat)
           (println (str "hoshimori.ingest: fetched CelesTrak SATCAT → " satcat
                         " (" (.length satcat) " bytes)")))
         (let [seed-text (str/trimr (slurp seed))]
           (if-not (.exists satcat)
             (do (spit out (str seed-text "\n"))
                 (println "hoshimori.ingest: no satcat.csv — seed is the graph (drop CelesTrak CSV in data/ingest/).")
                 0)
             (let [[owners regimes] (with-open [r (io/reader satcat)] (aggregate (doall (rows-from-csv r))))
                   top (->> owners (sort-by (fn [[_ a]] (- (:total a))))
                            (filter (fn [[_ a]] (>= (:total a) 20))) (take 60))
                   ops-n (count top)
                   occ-n (count regimes)
                   total-objs (reduce + (map (comp :total val) owners))]
               (spit out (merge-graph seed-text owners regimes))
               (println (str "hoshimori.ingest: " (count owners) " owners / " total-objs
                             " on-orbit objects aggregated → " ops-n " owner nodes + " occ-n
                             " regime-occupancy nodes (counts only)"))
               (println (str "  regimes: "
                             (str/join " " (map (fn [[r n]] (str (str/replace r #"^:+" "") "=" n))
                                                (sort-by key regimes)))))
               (println (str "  → " out))
               0)))))))

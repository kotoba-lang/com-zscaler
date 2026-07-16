(ns jinushi.methods.osm-buildings
  "jinushi 地主 — OSM building-stock source (5th source; ODbL, open-crowd reliability tier).

  OpenStreetMap is the widest building-stock source: `building:levels` (floors) is mapped at
  scale, with `operator`/`name`/`addr` where contributors added them. It is OPEN-CROWD provenance
  (confidence tier :osm = 0.60, below curated Wikidata 0.70 and authoritative cadastres 0.95) —
  so OSM contributes broad FLOOR coverage + an operator (owner-ish) signal, and the confidence
  model resolves it against higher-trust sources when they overlap.

  Owner: OSM rarely carries true ownership; `operator` (who runs the building) is the available
  owner-ish signal → owner-type :org when present, else :unmapped (honest — not guessed). OSM
  operators are organizations (Marriott, transit, retailers); no natural persons. ODbL — store
  attribution; G1: no person dimension, no precise dwelling pin beyond the public building tag."
  (:require [clojure.string :as str]
            [jinushi.methods.datom-emit :as de]
            #?(:clj [clojure.java.io :as io])))

(defn parse-levels
  "building:levels can be \"3\", \"3.5\", \"2;3\", \"4-6\" — take the first number, round."
  [v]
  (when v
    (some-> (re-find #"\d+(?:\.\d+)?" (str v))
            (#(try (Math/round (Double/parseDouble %)) (catch #?(:clj Exception :cljs :default) _ nil))))))

(defn normalize
  "Pure: Overpass elements → building records. cc/region tag the queried area."
  [elements cc region]
  (->> elements
       (keep (fn [e]
               (let [t (:tags e) op (:operator t)]
                 (when (:id e)
                   {:building (str "osm-" (name (or (:type e) :way)) "-" (:id e))
                    :cc cc :region region
                    :floors (parse-levels (:building:levels t))
                    :name (:name t)
                    :owner (when op op)                       ;; operator = owner-ish (open-crowd)
                    :owner-type (if op :org :unmapped)
                    :source :osm}))))
       (sort-by :building) vec))

(defn analyze*
  [records]
  (let [with-floors (filter :floors records)
        with-owner (filter :owner records)
        by-op (->> with-owner (reduce (fn [m r] (-> m (update-in [(:owner r) :count] (fnil inc 0))
                                                     (update-in [(:owner r) :floors] (fnil + 0) (or (:floors r) 0)))) {})
                   (map (fn [[k v]] {:operator k :buildings (:count v) :floors (:floors v)}))
                   (sort-by :floors >) (take 10) vec)]
    {:buildings (count records)
     :with-floors (count with-floors)
     :with-owner (count with-owner)
     :total-floors (reduce + 0 (keep :floors records))
     :top-operators-by-floors by-op}))

(defn datoms
  ([records] (datoms records 1))
  ([records tx]
   (let [q (fn [v] (str "\"" (str/replace (str v) "\"" "'") "\""))
         L (transient [])]
     (conj! L ";; jinushi 地主 — OSM building stock (ODbL; open-crowd, confidence :osm). [e a v tx op].")
     (conj! L ";; floors + operator (owner-ish); no person dimension (G1).")
     (conj! L "[")
     (doseq [r records]
       (let [e (str "building." (:building r))]
         (conj! L (str "[:" e " :building/source :osm " tx " :add]"))
         (when (:floors r) (conj! L (str "[:" e " :building/floors " (:floors r) " " tx " :add]")))
         (when (:owner r)
           (conj! L (str "[:" e " :building/operator " (q (:owner r)) " " tx " :add]")))))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn load-raw [dir]
     (let [f (io/file dir "osm-tokyo.raw.json")]
       (when (.exists f) (:elements (cheshire.core/parse-string (slurp f) true))))))

#?(:clj
   (defn -main [& _argv]
     (let [here (or (some-> (when (and *file* (not= *file* "NO_SOURCE_PATH")) (io/file *file*))
                            .getParentFile .getParentFile) (io/file "20-actors/jinushi"))
           root (or (some-> here .getParentFile .getParentFile) (io/file "."))
           dir (io/file root "80-data" "jinushi-land")
           els (load-raw dir)]
       (if-not els
         (println "no osm-tokyo.raw.json — operator fetch first (Overpass, ODbL)")
         (let [recs (normalize els "JP" "JP-13") a (analyze* recs)]
           (println (format "OSM: %d buildings, %d with floors (%d total floors), %d with operator"
                            (:buildings a) (:with-floors a) (:total-floors a) (:with-owner a)))
           (doseq [t (take 6 (:top-operators-by-floors a))]
             (println (format "  %-28s %d floors / %d buildings" (:operator t) (:floors t) (:buildings t)))))))
     0))

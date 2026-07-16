#!/usr/bin/env bb
;; umisachi 海幸 — public-dataset → kotoba-EAVT bridge (FAO/RAM/ICES-shaped → marine-bounty graph).
(ns umisachi.methods.ingest
  "ingest.clj — umisachi public marine-dataset bridge (ADR-2606074200).

  Normalizes a public seafood/fisheries dataset (FAO FishStat / RAM Legacy / ICES-shaped JSON:
  a flat list of records each with an `id`, a `kind` ∈ {stock fishery market logistics pressure}
  and domain fields) into the umisachi ontology (`:organism/id` + `:organism/kind` + domain
  attrs), then merges it with the curated seed graph (dedup by :organism/id, seed wins) — the
  watatsuna ingest pattern, adapted to umisachi's node-keyed marine graph.

  GATES (enforced in the bridge itself, so the output is charter-clean by construction):
   - G1 RESTORATION map, never a catch-target list — coordinate-shaped fields (lat/lon/gps/…)
     are DROPPED at the bridge, never emitted (analyze would refuse them at load anyway).
   - G4 destructive method is UNREPRESENTABLE as a :fishery — a fishery whose method is in
     `forbidden-methods` (factory-fishing/bottom-trawl/driftnet/iuu/…) is **reclassified to a
     :pressure node** (the disclosed harm, routed to restoration), NEVER bridged as a :fishery.
   - G5 sourcing-honesty — offline/sample = :representative; only a live operator-gated attributed
     fetch would tag :authoritative.
   - G7 outward-gated — a --live fetch needs UMISACHI_OPERATOR_GATE and even then is an R0 scaffold.

  Run:  bb --classpath 20-actors 20-actors/umisachi/methods/ingest.clj [src.json ...]"
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [umisachi.methods.analyze :as a]))

(def ^:private this-file *file*)
(defn- actor-root [] (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile))

(defn- slug [s]
  (-> (str/lower-case (str s)) (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-+|-+$" "")))

(def ^:private coord-fields #{"lat" "lon" "lng" "latitude" "longitude" "gps" "geohash"
                              "coord" "coords" "coordinate" "coordinates" "northing" "easting"})

(defn bridge-record
  "One public record → one umisachi node map (or nil to drop). G1 drops coordinate fields; G4
  reclassifies a forbidden-method fishery into a :pressure node."
  [rec & {:keys [sourcing] :or {sourcing "representative"}}]
  (when (and (map? rec) (get rec "id") (get rec "kind"))
    (let [src (keyword sourcing)
          id (slug (get rec "id"))
          kind (keyword (get rec "kind"))
          method (some-> (get rec "method") keyword)
          ;; G4: a destructive-method fishery is reclassified to a disclosed :pressure node.
          forbidden? (and (= kind :fishery) (a/forbidden-methods method))
          kind (if forbidden? :pressure kind)]
      (case kind
        :stock (cond-> {:organism/id (keyword (str "stock." id)) :organism/kind :stock
                        :organism/sourcing src}
                 (get rec "label")   (assoc :organism/label (get rec "label"))
                 (get rec "species") (assoc :stock/species (get rec "species"))
                 (get rec "status")  (assoc :stock/status (keyword (get rec "status")))
                 (get rec "region")  (assoc :stock/region (get rec "region")))
        :fishery {:organism/id (keyword (str "fishery." id)) :organism/kind :fishery
                  :organism/sourcing src :fishery/method method
                  :fishery/scale (some-> (get rec "scale") keyword)}
        :market (cond-> {:organism/id (keyword (str "market." id)) :organism/kind :market
                         :organism/sourcing src}
                  (get rec "market_kind") (assoc :market/kind (keyword (get rec "market_kind")))
                  (get rec "access")      (assoc :market/access (keyword (get rec "access"))))
        :logistics (cond-> {:organism/id (keyword (str "logi." id)) :organism/kind :logistics
                            :organism/sourcing src}
                     (get rec "logistics_kind") (assoc :logistics/kind (keyword (get rec "logistics_kind"))))
        :pressure (cond-> {:organism/id (keyword (str "pressure." id)) :organism/kind :pressure
                           :organism/sourcing src
                           ;; G4: the reclassified fishery's method becomes the pressure kind.
                           :pressure/kind (or (some-> (get rec "pressure_kind") keyword) method)}
                    (get rec "label") (assoc :organism/label (get rec "label")))
        nil))))

(defn bridge-source
  "One public dataset JSON file (a flat list of records, or {records:[…]}) → vector of umisachi
  node maps. Returns {:nodes [...] :reclassified n :dropped n}."
  [path & {:keys [sourcing] :or {sourcing "representative"}}]
  (let [doc (json/parse-string (slurp (io/file path)) false)
        recs (if (map? doc) (get doc "records") doc)
        reclassified (atom 0)]
    (let [nodes (vec (keep (fn [r]
                             (let [forbidden? (and (= "fishery" (get r "kind"))
                                                   (a/forbidden-methods (some-> (get r "method") keyword)))]
                               (when forbidden? (swap! reclassified inc))
                               (bridge-record r :sourcing sourcing)))
                           (filter map? recs)))]
      {:nodes nodes :reclassified @reclassified :dropped (- (count (filter map? recs)) (count nodes))})))

(defn merge-graph
  "Merge seed + bridged node maps, dedup by :organism/id (seed wins). Edges (no :organism/id)
  pass through from the seed."
  [seed bridged]
  (first
   (reduce (fn [[out seen] rec]
             (if-not (map? rec)
               [(conj out rec) seen]
               (let [k (:organism/id rec)]
                 (cond
                   (nil? k) [(conj out rec) seen]          ; an edge / non-node form
                   (seen k) [out seen]                      ; dup, seed (seen first) wins
                   :else    [(conj out rec) (conj seen k)]))))
           [[] #{}] (concat seed bridged))))

(defn live-refusal
  "G7: returns the refusal message if --live is requested, else nil (offline needs no flag)."
  [argv env-gate]
  (when (some #{"--live"} argv)
    (if (str/blank? (str env-gate))
      (str "REFUSED: live marine-dataset ingest is G7/Council-gated. Set UMISACHI_OPERATOR_GATE="
           "<council-token> + supply an operator DID. Offline mode (no --live) needs no flag.")
      "REFUSED: live fetch is an R0 scaffold — wire the FAO/RAM/ICES public feed via @etzhayyim/sdk under Council ratification, tag :authoritative, then re-run.")))

(defn main [& argv]
  (when-let [msg (live-refusal argv (System/getenv "UMISACHI_OPERATOR_GATE"))]
    (println msg) (System/exit 2))
  (let [root (actor-root)
        srcs (let [explicit (remove #(str/starts-with? % "--") argv)]
               (if (seq explicit) (map io/file explicit)
                   (let [d (io/file root "data" "ingest")]
                     (when (.isDirectory d)
                       (sort (filter #(str/ends-with? (.getName %) ".json") (.listFiles d)))))))
        results (mapv (fn [s] [(io/file s) (bridge-source s)]) srcs)
        bridged (vec (mapcat (fn [[f {:keys [nodes reclassified dropped]}]]
                               (println (format "  bridged %s: %d nodes · %d reclassified→:pressure (G4) · %d dropped"
                                                 (.getName f) (count nodes) reclassified dropped))
                               nodes)
                             results))]
    (println (format "= %d bridged node(s) — RESTORATION map, never a catch-target list (G1)" (count bridged)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply main *command-line-args*))

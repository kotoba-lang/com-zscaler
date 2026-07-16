#!/usr/bin/env bb
(ns umisachi.methods.analyze
  "umisachi 海幸 — edge-primary nourishment / depletion analyzer over the marine-bounty graph.

  海幸 (umi-sachi, 'the bounty of the sea') is the marine sibling of mitsuho 瑞穂 (rice/land
  abundance) and the marine handoff target of mitsuho's N9 (which keeps only freshwater
  aquaculture and reserves the ocean for a separate actor). It is a KG-mirror in the
  inochi / asobi lineage: it weaves stocks / fisheries / logistics / markets and the
  PRESSURES that gate sustainable provisioning into the kotoba Datom log, and surfaces, on
  read:

    nourishment[node]   = Σ incident inbound provisioning/stewardship load × disclosed access
                          weight of the receiving node — the equitable-provisioning surface to widen
    depletion[node]     = Σ incident inbound :depletes/:encloses/:gates load — the 取 a stock /
                          habitat / market bears, routed to RESTORATION
    depletion-out[holder] = Σ outbound depletion load — the 取-holder gating sustainable bounty
                          (cross-link to tsumugi/danjo/kabuto where a power-entity operates it)

  G1 — RESTORATION / NOURISHMENT map, NEVER a catch-target list: coordinate-shaped keys are
       refused at load (no fishing-ground GPS).
  G4 — destructive methods (factory-fishing / bottom-trawl / driftnet / IUU / dynamite /
       cyanide) are UNREPRESENTABLE as a :fishery method — refused at load. They may exist
       ONLY as a :pressure node (the disclosed harm), routed to restoration.

  Run:  bb --classpath 20-actors 20-actors/umisachi/methods/analyze.clj  -> out/nourishment-report.md"
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private this-file *file*)
(defn- actor-root [] (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile))

;; provisioning-equity weight of the RECEIVING node (commons widest, proprietary narrowest)
(def access-weight {:commons 1.0 :open 0.9 :regulated 0.7 :enclosed 0.3 :proprietary 0.1})
(def provisioning-kinds #{:provisions :nourishes :stewards :restores :cultivates})
(def depletion-kinds    #{:depletes :encloses :gates})

;; G4 — destructive methods that can never be an umisachi fishery (only a disclosed pressure)
(def forbidden-methods #{:factory-fishing :bottom-trawl :driftnet :iuu
                         :dynamite :cyanide :ghost-gear :gillnet-drift})

;; G1 — coordinate-shaped keys are refused (no "where the fish are")
(def ^:private coord-tokens
  #{"lat" "lon" "lng" "latitude" "longitude" "coord" "coords" "coordinate"
    "coordinates" "gps" "geohash" "northing" "easting"})

(defn- coord-key? [k]
  (let [parts (remove nil? [(when (keyword? k) (namespace k))
                            (if (keyword? k) (name k) (str k))])
        toks  (set (mapcat #(str/split (str/lower-case %) #"[./_-]") parts))]
    (boolean (some coord-tokens toks))))

(defn assert-charter-clean
  "G1/G4 load-time refusal. Throws ex-info on any violating node; returns the node otherwise."
  [n]
  (when-let [bad (some #(when (coord-key? %) %) (keys n))]
    (throw (ex-info "G1: coordinate-shaped key is unrepresentable (no fishing-ground location)"
                    {:node (:organism/id n) :key bad})))
  (when (and (= (:organism/kind n) :fishery)
             (forbidden-methods (:fishery/method n)))
    (throw (ex-info "G4: destructive fishing method is unrepresentable as an umisachi fishery"
                    {:node (:organism/id n) :method (:fishery/method n)})))
  n)

(defn load-graph
  "Return [nodes-by-id edges] from an umisachi EDN graph string. Every node passes the
  G1/G4 charter guard at load (a violating seed cannot be analyzed)."
  [text]
  (let [forms (edn/read-string text)
        maps  (filter map? forms)]
    (reduce (fn [[nodes edges] f]
              (cond
                (:organism/id f) [(assoc nodes (:organism/id f) (assert-charter-clean f)) edges]
                (and (:en/from f) (:en/to f)) [nodes (conj edges f)]
                :else [nodes edges]))
            [{} []]
            maps)))

(defn load-file [path]
  (load-graph (slurp (io/file path))))

(defn analyze
  "Edge-primary integrals (computed on read; transient — N1/G2)."
  [nodes edges]
  (reduce
   (fn [acc e]
     (let [kind (:en/kind e)
           load (double (or (:en/load e) 0.0))
           src  (:en/from e)
           dst  (:en/to e)]
       (cond
         (provisioning-kinds kind)
         (let [bearer (get nodes dst {})
               w (get access-weight (:market/access bearer) 0.6)]
           (update-in acc [:nourishment dst] (fnil + 0.0) (* load w)))

         (depletion-kinds kind)
         (-> acc
             (update-in [:depletion dst] (fnil + 0.0) load)
             (update-in [:depletion-out src] (fnil + 0.0) load))

         :else acc)))
   {:nourishment {} :depletion {} :depletion-out {}}
   edges))

(defn rank [d nodes limit]
  (->> d
       (sort-by (comp - val))
       (take limit)
       (mapv (fn [[nid v]] [nid (get-in nodes [nid :organism/label] (str nid)) v]))))

(defn- kind-count [nodes k]
  (count (filter #(= (:organism/kind %) k) (vals nodes))))

(defn report-md [nodes edges res]
  (let [auth (count (filter #(= (:organism/sourcing %) :authoritative) (vals nodes)))
        line (fn [[_ label v]] (format "| %s | %.3f |" label (double v)))]
    (str/join
     "\n"
     (concat
      ["# umisachi 海幸 — marine-bounty provisioning report (aggregate-first)"
       ""
       (str "> **G1 — NOURISHMENT / RESTORATION map, NEVER a catch-target list.** No stock "
            "coordinates, no fishing-ground GPS, no per-stock catch score. The 取-holder is the "
            "PRESSURE; the bearer is the stock / habitat / market; the routing is RESTORATION + "
            "equitable NOURISHMENT. Stock-status & access are DISCLOSED facts (FAO/RAM/ICES/IUCN/"
            "CCAMLR/ICCAT/MSC), not umisachi verdicts (N3). 取 lives only on edges, on read (N1).")
       ""
       (format "**Graph**: %d nodes (%d stocks · %d fisheries · %d logistics · %d markets · %d pressures) · %d 縁 · %d/%d :authoritative"
               (count nodes) (kind-count nodes :stock) (kind-count nodes :fishery)
               (kind-count nodes :logistics) (kind-count nodes :market) (kind-count nodes :pressure)
               (count edges) auth (count nodes))
       ""
       "## Provisioning-nourishment — the equitable-bounty surface to widen"
       "_Σ incident provisioning/stewardship load × disclosed access weight of the receiver._"
       ""
       "| node | nourishment |"
       "|---|---:|"]
      (map line (rank (:nourishment res) nodes 20))
      [""
       "## Depletion concentration — 取-holders gating the sea's bounty (route to restoration)"
       "_Σ outbound depletion/enclosure load; cross-link to tsumugi/danjo/kabuto where a power-entity operates the pressure._"
       ""
       "| pressure | depletion-load |"
       "|---|---:|"]
      (map line (rank (:depletion-out res) nodes 20))
      [""
       "## Depleted bounty — stocks / habitats / markets bearing the most 取 (restore these)"
       ""
       "| node | depletion-load |"
       "|---|---:|"]
      (map line (rank (:depletion res) nodes 12))
      [""
       "---"
       (str "_umisachi 海幸 · ADR-2606074200 · marine sibling of mitsuho 瑞穂 · mirror-only · "
            "non-adjudicating · edge-primary · restoration-routed · factory-fishing unrepresentable. "
            "Live ingest (FAO/RFMO catch stats) is G7/Council-gated._")
       ""]))))

(defn main [& argv]
  (let [seed (or (first (remove #(str/starts-with? % "--") argv))
                 (str (io/file (actor-root) "data" "seed-umisachi-graph.kotoba.edn")))
        [nodes edges] (load-file seed)
        res (analyze nodes edges)
        out (io/file (actor-root) "out")]
    (.mkdirs out)
    (spit (io/file out "nourishment-report.md") (report-md nodes edges res))
    (println (format "umisachi: %d nodes, %d 縁 → out/nourishment-report.md" (count nodes) (count edges)))
    (when-let [top (first (rank (:nourishment res) nodes 1))]
      (println (format "  top nourishment: %s (%.3f)" (nth top 1) (double (nth top 2)))))
    (when-let [top (first (rank (:depletion-out res) nodes 1))]
      (println (format "  top 取-holder:    %s (%.3f)" (nth top 1) (double (nth top 2)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply main *command-line-args*))

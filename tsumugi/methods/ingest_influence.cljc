(ns tsumugi.methods.ingest-influence
  "tsumugi 紡ぎ — influence-history INGEST membrane (ADR-2606061500).
  Clojure port of methods/ingest_influence.py (1:1, pure-logic offline core).

  Maps external documented-influence sources (Wikidata P737-shaped fixtures / Pantheon CSV
  shapes) into :hist/* nodes + :flow/* influence 縁, merging them with the seed. Every
  candidate crosses the charter membrane before admission:

    N1 edge-primary — notability / HPI is NEVER a node score; influence lives on :flow/*.
    N2 mirror       — every ingested node gets :mirror/is-mirror true + disclaimer.
    N3 non-adjud.   — :flow records documented influence, never a truth verdict.
    N4 public+settled+no PII — admitted ONLY if it has a deathYear (historical, settled).
                      Living / uncertain entities are REFUSED.
    N5 temporal DAG — source.year-from > receiver.year-to edges are DROPPED (reported).
    G5 sourcing     — all ingested data is :sourcing :representative, :source :scholarship.
    G7 outward-gated — LIVE network fetch functions are NOT ported here; they require
                       TSUMUGI_OPERATOR_GATE=1 + TSUMUGI_OPERATOR_DID in Python main.
                       This port covers: slug, to-node-id, make-node, make-edge,
                       era-for-year, edn-node, normalize-entity, ingest-offline (pure logic),
                       year*, parse-wikidata-sparql, normalize-wikidata-rows.
                       Omitted (IO / network / CLI): LiveGateRefused, fetch-wikidata-influence,
                       fetch-pantheon-people, write-merge, main.

  Depends on analyze-influence (load + node-year). File I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]
            [tsumugi.methods.analyze-influence :as ai]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [cheshire.core :as json])))

(def DISCLAIMER-FIG
  "観察像 — 本人ではない (an observational mirror, not the person)")

;; ── slug ─────────────────────────────────────────────────────────────────────
(defn slug
  "Lower-case, non-alnum→'-', strip leading/trailing '-'. Prefer surname-ish tail.
  Mirrors: re.sub(r'[^a-z0-9]+', '-', label.lower()).strip('-'); then take tail after last '-'."
  [label]
  (let [s (-> (str (or label ""))
              str/lower-case
              (str/replace #"[^a-z0-9]+" "-")
              (str/replace #"^-+|-+$" ""))]
    (if (and (not (str/blank? s)) (str/includes? s "-"))
      (last (str/split s #"-"))
      (if (str/blank? s) "" s))))

;; ── to-node-id ───────────────────────────────────────────────────────────────
(defn to-node-id
  "Resolve a reference string to a canonical node id.
  Existing seed prefixes are returned as-is; labels are looked up in label-to-id
  or slugged into 'fig.<slug>'."
  [ref label-to-id]
  (if (or (str/starts-with? ref "fig.")
          (str/starts-with? ref "doc.")
          (str/starts-with? ref "trad.")
          (str/starts-with? ref "event.")
          (str/starts-with? ref "self."))
    ref
    (or (get label-to-id ref)
        (str "fig." (slug ref)))))

;; ── era-for-year ─────────────────────────────────────────────────────────────
(defn era-for-year
  "Map a signed year to the matching era keyword string."
  [y]
  (let [y (int y)]
    (cond
      (< y -1200) ":bronze-age"
      (< y  -800) ":iron-age"
      (< y  -200) ":axial"
      (< y     0) ":2nd-temple"
      (< y   476) ":late-antiquity"
      (< y  1000) ":early-medieval"
      (< y  1450) ":medieval"
      (< y  1648) ":reformation"
      (< y  1800) ":enlightenment"
      (< y  1945) ":modern"
      :else        ":contemporary")))

;; ── make-node ────────────────────────────────────────────────────────────────
(defn make-node
  "Build a new :hist/* node map from public structured data (N2/N4 guaranteed by caller).
  trad may be nil (defaults to [:secular-philosophy]), a string, or a seq of strings."
  [label birth death trad era]
  (let [traditions (mapv (fn [t]
                           (let [ts (str t)]
                             (if (str/starts-with? ts ":") ts (str ":" ts))))
                         (or (when (seq trad) (if (sequential? trad) trad [trad]))
                             [":secular-philosophy"]))
        era-kw (if (str/starts-with? (str era) ":") (str era) (str ":" era))]
    {":organism/id"        (str "fig." (slug label))
     ":organism/kind"      ":institutional"
     ":organism/label"     label
     ":organism/standing"  ":historical-public"
     ":hist/subkind"       ":figure"
     ":hist/year-from"     (int birth)
     ":hist/year-to"       (int death)
     ":hist/era"           era-kw
     ":hist/tradition"     traditions
     ":hist/dating-confidence" ":attested"
     ":mirror/is-mirror"   true
     ":mirror/performer-type" ":historical-figure"
     ":mirror/disclaimer"  DISCLAIMER-FIG
     ":influence/affect-class" ":inquiring"
     ":hist/sourcing"      ":representative"}))

;; ── make-edge ────────────────────────────────────────────────────────────────
(defn make-edge
  "Build a :flow/* influence edge map from src → dst node ids."
  [src dst]
  {":flow/id"            (str "fl." (slug src) "." (slug dst))
   ":flow/kind"          ":influences"
   ":flow/from"          src
   ":flow/to"            dst
   ":flow/signed-weight" 0.5
   ":flow/strain"        0.5
   ":flow/thermo-length" 0.5
   ":flow/source"        ":scholarship"
   ":flow/sourcing"      ":representative"})

;; ── edn-node (EDN serializer) ─────────────────────────────────────────────────
(defn edn-node
  "Serialize a node/edge map to a one-line EDN string. Mirrors Python edn_node()."
  [m]
  (let [parts (mapv (fn [[k v]]
                      (cond
                        (= v true)  (str k " true")
                        (= v false) (str k " false")
                        (sequential? v)
                        (str k " [" (str/join " " (mapv str v)) "]")
                        (and (string? v)
                             (or (str/starts-with? v ":")
                                 (re-matches #"-?[0-9]+" v)))
                        (str k " " v)
                        (string? v)
                        (str k " \"" (-> v
                                        (str/replace "\\" "\\\\")
                                        (str/replace "\"" "\\\"")) "\"")
                        :else (str k " " v)))
                    m)]
    (str "{" (str/join " " parts) "}")))

;; ── normalize-entity (N4 gate) ────────────────────────────────────────────────
(defn normalize-entity
  "Admits a Wikidata-fixture entity map if it has a deathYear (N4). Returns nil otherwise."
  [e]
  (when (contains? e "deathYear")
    (make-node (get e "label") (int (get e "birthYear" 0)) (int (get e "deathYear"))
               (get e "tradition") (get e "era" "modern"))))

;; ── _year (ISO date → signed int) ────────────────────────────────────────────
(defn year*
  "Wikidata ISO date string → signed year int (BCE negative). '-0563-...' → -563."
  [iso]
  (when (and iso (not (str/blank? iso)))
    (let [s (str/trim (str iso))
          neg (str/starts-with? s "-")
          s (if neg (subs s 1) s)
          head (first (str/split s #"-" 2))]
      (try
        (let [y #?(:clj (Integer/parseInt head) :cljs (js/parseInt head 10))]
          (if neg (- y) y))
        (catch #?(:clj Exception :cljs :default) _
          nil)))))

;; ── parse-wikidata-sparql ─────────────────────────────────────────────────────
(defn parse-wikidata-sparql
  "WDQS JSON object (as Clojure map) → list of row maps. Each row has
  :pLabel, :pBirth, :pDeath, :infLabel, :infBirth, :infDeath."
  [obj]
  (let [bindings (get-in obj ["results" "bindings"] [])]
    (mapv (fn [b]
            (let [g (fn [k] (get-in b [k "value"]))]
              {"pLabel"   (g "pLabel")
               "pBirth"   (year* (g "pBirth"))
               "pDeath"   (year* (g "pDeath"))
               "infLabel" (g "infLabel")
               "infBirth" (year* (g "infBirth"))
               "infDeath" (year* (g "infDeath"))}))
          bindings)))

;; ── ingest-offline ────────────────────────────────────────────────────────────
#?(:clj
   (defn ingest-offline
     "Read fixture *.json files from fixtures-dir; merge new nodes + flows into the seed.
     Returns [nodes flows new-nodes new-flows dropped].
     fixtures-dir and seed are path strings."
     [fixtures-dir seed]
     (let [[nodes flows] (ai/load seed)
           seen-nodes (atom (set (keys nodes)))
           seen-flows (atom (set (map #(get % ":flow/id") flows)))
           new-nodes  (atom [])
           new-flows  (atom [])
           dropped    (atom [])
           ;; build label→id index and raw entity list from ALL fixture files
           fixture-files (sort (seq (.listFiles (io/file (str fixtures-dir)))))
           fixture-files (filter #(str/ends-with? (.getName %) ".json") (or fixture-files []))
           raw        (atom [])
           label-to-id (atom {})]
       (doseq [fx fixture-files]
         (let [text (slurp fx)
               obj  (json/parse-string text)
               entities (get obj "entities" [])]
           (doseq [e entities]
             (swap! raw conj e)
             (when (contains? e "deathYear")
               (swap! label-to-id assoc (get e "label") (str "fig." (slug (get e "label"))))))))
       ;; build year-range index from seed + new fixture nodes
       (let [yr (atom (into {} (map (fn [[nid nd]]
                                      [nid [(ai/node-year nd ":hist/year-from")
                                            (ai/node-year nd ":hist/year-to")]])
                                    nodes)))]
         (doseq [e @raw]
           (when (contains? e "deathYear")
             (let [fid (str "fig." (slug (get e "label")))]
               (swap! yr assoc fid [(int (get e "birthYear" 0)) (int (get e "deathYear"))]))))
         (doseq [e @raw]
           (let [n (normalize-entity e)]
             (if (nil? n)
               (swap! dropped conj [(get e "label") "N4 living/unsettled (no deathYear)"])
               (let [nid (get n ":organism/id")]
                 (when (not (contains? @seen-nodes nid))
                   (swap! new-nodes conj n)
                   (swap! seen-nodes conj nid))
                 (doseq [ref (get e "influencedBy" [])]
                   (let [src (to-node-id ref @label-to-id)
                         dst nid
                         src-yr (get @yr src)
                         dst-yr (get @yr dst)]
                     (cond
                       (or (nil? src-yr) (nil? dst-yr))
                       (swap! dropped conj [(str src "->" dst) "unknown endpoint"])
                       (> (first src-yr) (second dst-yr))
                       (swap! dropped conj [(str src "->" dst) "N5 backward-in-time"])
                       :else
                       (let [fid (str "fl." (slug src) "." (slug dst))]
                         (when (not (contains? @seen-flows fid))
                           (swap! seen-flows conj fid)
                           (swap! new-flows conj (make-edge src dst)))))))))))
         [nodes flows @new-nodes @new-flows @dropped]))))

;; ── normalize-wikidata-rows ───────────────────────────────────────────────────
#?(:clj
   (defn normalize-wikidata-rows
     "Apply the charter membrane to a seq of WDQS row maps (from parse-wikidata-sparql)
     against an existing seed path. Returns [nodes flows new-nodes new-flows dropped]."
     [rows seed]
     (let [[nodes flows] (ai/load seed)
           seen-nodes (atom (set (keys nodes)))
           seen-flows (atom (set (map #(get % ":flow/id") flows)))
           yr         (atom (into {} (map (fn [[nid nd]]
                                           [nid [(ai/node-year nd ":hist/year-from")
                                                 (ai/node-year nd ":hist/year-to")]])
                                         nodes)))
           new-nodes  (atom [])
           new-flows  (atom [])
           dropped    (atom [])
           ensure     (fn [label birth death]
                        (if (or (nil? label) (nil? birth) (nil? death))
                          (do (swap! dropped conj [(or label "?") "N4 missing dates"]) nil)
                          (let [nid (str "fig." (slug label))]
                            (when (not (contains? @seen-nodes nid))
                              (swap! new-nodes conj (make-node label birth death nil (era-for-year death)))
                              (swap! seen-nodes conj nid))
                            (swap! yr assoc nid [birth death])
                            nid)))]
       (doseq [r rows]
         (let [src (ensure (get r "infLabel") (get r "infBirth") (get r "infDeath"))
               dst (ensure (get r "pLabel")   (get r "pBirth")   (get r "pDeath"))]
           (when (and src dst (not= src dst))
             (let [src-yr (get @yr src)
                   dst-yr (get @yr dst)]
               (if (> (first src-yr) (second dst-yr))
                 (swap! dropped conj [(str src "->" dst) "N5 backward-in-time"])
                 (let [fid (str "fl." (slug src) "." (slug dst))]
                   (when (not (contains? @seen-flows fid))
                     (swap! seen-flows conj fid)
                     (swap! new-flows conj (make-edge src dst)))))))))
       [nodes flows @new-nodes @new-flows @dropped])))

#?(:clj
   (defn -main
     "CLI entry: mirrors ingest_influence.main offline path — ingest-offline(fixtures, seed) →
     out/influence-ingested.kotoba.edn + out/seed-plus-ingest.kotoba.edn (out/ only; promotion
     into the seed is a separate human PR). --live (Wikidata P737) is G7-gated network and not
     ported (refused). ADR-2606261200 cljc-native operator leg."
     [& argv]
     (if (some #{"--live"} (vec argv))
       (binding [*out* *err*]
         (println (str "REFUSED: live influence ingest (Wikidata P737) is G7-gated network and "
                       "an R0 scaffold (not ported). Requires TSUMUGI_OPERATOR_GATE=1 + "
                       "TSUMUGI_OPERATOR_DID; offline fixture ingest runs without --live.")))
       (let [here    (let [f  (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                           pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                       (if (and pp (.isDirectory (io/file pp "data"))) pp (io/file "20-actors" "tsumugi")))
             seed    (io/file here "data" "seed-influence-history.kotoba.edn")
             fixtures (io/file here "data" "ingest-influence")
             out     (io/file here "out")
             [_nodes _flows new-nodes new-flows _dropped] (ingest-offline (.getPath fixtures) (.getPath seed))
             enode   (fn [coll] (str/join "\n" (map #(str " " (edn-node %)) coll)))]
         (.mkdirs out)
         (let [add-lines (concat [";; tsumugi 紡ぎ — GENERATED ingest (ADR-2606061500). DO NOT hand-edit."
                                  ";; :representative :scholarship; mirror-only (N2); out/ only — promotion into the committed seed is a separate human-reviewed PR."
                                  "[" ";; ── ingested figure nodes ──"]
                                 (map #(str " " (edn-node %)) new-nodes)
                                 [";; ── ingested influence 縁 ──"]
                                 (map #(str " " (edn-node %)) new-flows)
                                 ["]"])
               add-file (io/file out "influence-ingested.kotoba.edn")
               base     (str/trimr (slurp seed))
               combined (io/file out "seed-plus-ingest.kotoba.edn")]
           (spit add-file (str (str/join "\n" add-lines) "\n"))
           (spit combined (str (subs base 0 (dec (count base)))
                               "\n ;; ── INGESTED ──\n" (enode new-nodes) "\n" (enode new-flows) "]\n"))
           (println (str "[tsumugi/ingest-influence] +" (count new-nodes) " nodes · +"
                         (count new-flows) " 縁 → " (.getPath add-file))))))))

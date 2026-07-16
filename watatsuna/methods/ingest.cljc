(ns watatsuna.methods.ingest
  "ingest.cljc — watatsuna 綿津綱 TeleGeography-bridge ingester. ADR-2606012600.
  Clojure port of `methods/ingest.py`.

  Normalizes a public submarine-cable dataset (submarinecablemap.com / TeleGeography-
  shaped JSON: cables[] + landing_points[]) into the watatsuna kotoba ontology
  (:cable/* :station/* :cable.link/*), merges it with the curated seed graph (dedup
  by id; seed wins), and emits the merged graph as kotoba-EDN.

  CONSTITUTIONAL GATES (mirror ingest.py):
    G1 public-only  — public-record infrastructure only.
    G2 resilience-not-interdiction — chokepoint tags come ONLY from the input, and
       only KNOWN names; this ingester NEVER synthesizes a chokepoint.
    G5 sourcing-honesty — offline/sample ingest is tagged :representative.
    G7 outward-gated — LIVE fetch is refused (R0 scaffold); offline needs no flag.

  This module does NOT content-address anything (ingest.py has no sha256/canonical-
  JSON CID — it writes plain EDN files); so there is NO kotoba.datom reuse and NO
  CID-parity here (cf. autorun/kotoba, which DO content-address). Pure fns;
  I/O (JSON read, EDN write) at the #?(:clj) edge.

  CONVENTIONS (root CLAUDE.md): kebab keyword keys in Clojure code; Python \":…\"
  strings (incl. all datom attrs + record keys) stay STRINGS; closed-vocab →
  ex-info; trailing opts maps."
  (:require [watatsuna.methods._edn :as edn]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [cheshire.core :as json])))

;; ── KNOWN_CHOKEPOINTS (G1/G2 — the closed vocab; never synthesized) ────────

(def known-chokepoints
  #{"luzon-strait" "malacca" "suez-red-sea" "south-china-sea" "gibraltar"
    "hormuz" "bab-el-mandeb" "sunda" "lombok" "taiwan-strait"})

;; ── _slug ──────────────────────────────────────────────────────────────────
;; Python: re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")

(defn slug
  "Lowercase `s`, collapse runs of non-[a-z0-9] into a single '-', strip leading/
  trailing '-' (mirror of Python `_slug`)."
  [s]
  (-> (str/lower-case s)
      (str/replace #"[^a-z0-9]+" "-")
      (as-> x (str/replace x #"^-+" ""))
      (as-> x (str/replace x #"-+$" ""))))

;; ── value renderer for to-edn (mirror of the nested `v`) ───────────────────

(defn- render-num
  "Render a number the way Python str() would for the values ingest produces:
  ints have no decimal point; floats keep one (Python `float(x)` → 60.0)."
  [n]
  (cond
    (integer? n) (str n)
    ;; a float that is integral prints as e.g. 60.0 (Python float repr)
    (and (number? n) (== n (Math/floor (double n))) (not (infinite? (double n))))
    (str (long n) ".0")
    :else (str n)))

(defn- render-val
  "to_edn's inner `v`: bool → true/false, vector → \"[ … ]\", string → kept if it
  starts with ':' else double-quoted, number → Python-ish str()."
  [x]
  (cond
    (boolean? x) (if x "true" "false")
    (or (vector? x) (seq? x)) (str "[" (str/join " " (map render-val x)) "]")
    (string? x) (if (str/starts-with? x ":") x (str "\"" x "\""))
    (number? x) (render-num x)
    :else (str x)))

;; ── to-edn ─────────────────────────────────────────────────────────────────
;; recs are sequences of [k v] PAIRS (ordered) — mirrors Python dict insertion order.

(defn rec->pairs
  "A record may be a vector of [k v] pairs (ordered) or a map. Return ordered pairs."
  [rec]
  (if (map? rec) (seq rec) rec))

(defn to-edn
  "Render `recs` (each an ordered seq of [k v] pairs, or a map) as a kotoba-EDN
  vector preceded by `header` comment lines. Mirrors Python `to_edn`."
  [recs header]
  (let [body (map (fn [r]
                    (str " {"
                         (str/join " " (map (fn [[k v]] (str k " " (render-val v)))
                                            (rec->pairs r)))
                         "}"))
                  recs)
        lines (concat header ["["] body ["]"])]
    (str (str/join "\n" lines) "\n")))

;; ── bridge-source (mirror `bridge_source`) ─────────────────────────────────
;; One public dataset JSON map → {:cables [...] :stations [...] :links [...]},
;; each record a vector of ordered [k v] pairs so to-edn emits Python-order keys.

(defn bridge-doc
  "Pure core: bridge an already-parsed dataset doc (a Clojure map with string keys
  \"cables\" / \"landing_points\") → {:cables :stations :links}, each a vector of
  ordered records ([k v] pairs). Mirrors `bridge_source` minus the file read.
  `sourcing` (default \"representative\") tags every record's :*/sourcing as \":<sourcing>\"."
  ([doc] (bridge-doc doc {}))
  ([doc {:keys [sourcing] :or {sourcing "representative"}}]
   (let [src (str ":" sourcing)
         lps (get doc "landing_points" [])
         ;; input lp.id → kotoba :station/id
         station-id (reduce (fn [m p]
                              (let [sid (str "station."
                                             (str/lower-case (get p "country" "xx"))
                                             "." (slug (get p "name")))]
                                (assoc m (get p "id") sid)))
                            {} lps)
         stations (mapv
                   (fn [p]
                     (let [sid (get station-id (get p "id"))
                           cps (filterv known-chokepoints (or (get p "chokepoints") []))]
                       (cond-> [[":station/id" sid]
                                [":station/name" (get p "name")]
                                [":station/country" (get p "country" "??")]]
                         (contains? p "lat") (conj [":station/lat" (get p "lat")])
                         (contains? p "lon") (conj [":station/lon" (get p "lon")])
                         (seq cps)           (conj [":station/chokepoint" cps])
                         :always             (conj [":station/sourcing" src]))))
                   lps)
         cables-out (atom [])
         links-out  (atom [])]
     (doseq [c (get doc "cables" [])]
       (let [cid (str "cable." (slug (get c "id")))
             rec (cond-> [[":cable/id" cid]
                          [":cable/name" (get c "name")]
                          [":cable/status" ":in-service"]]
                   (seq (get c "owners"))
                   (conj [":cable/owner-consortium" (get c "owners")])
                   (contains? c "length_km")
                   (conj [":cable/length-km" (long (get c "length_km"))])
                   (contains? c "design_capacity_tbps")
                   (conj [":cable/design-capacity-tbps" (double (get c "design_capacity_tbps"))])
                   (contains? c "rfs")
                   (conj [":cable/rfs-year" (long (get c "rfs"))])
                   :always
                   (conj [":cable/sourcing" src]))]
         (swap! cables-out conj rec)
         (doseq [raw-lp (get c "landing_point_ids" [])]
           (when-let [sid (get station-id raw-lp)]
             (swap! links-out conj
                    [[":cable.link/id"
                      (str "lk." (slug (get c "id")) "." (last (str/split sid #"\.")))]
                     [":cable.link/cable" cid]
                     [":cable.link/station" sid]
                     [":cable.link/sourcing" src]])))))
     {:cables @cables-out :stations stations :links @links-out})))

#?(:clj
   (defn bridge-source
     "Read a dataset JSON file at `path` and bridge it (cf. `bridge_source`).
     Returns {:cables :stations :links}."
     ([path] (bridge-source path {}))
     ([path opts]
      (bridge-doc (json/parse-string (slurp path)) opts))))

;; ── rec-key (mirror `_key`) — first id attr present ────────────────────────

(def ^:private id-attrs
  [":cable/id" ":station/id" ":cable.link/id" ":cable.seg/id" ":cable.fault/id"])

(defn rec-key
  "The dedup key of a record: the first present id attr's value, or nil. Works on
  both ordered [k v] pair-vectors and maps."
  [rec]
  (let [m (if (map? rec) rec (into {} rec))]
    (some #(get m %) id-attrs)))

;; ── merge (mirror the seed + bridged dedup loop) ───────────────────────────

(defn merge-graph
  "Concat `seed` records then `bridged` records, keep only maps/records carrying an
  id, dedup by `rec-key` (first wins → seed wins on conflict). Returns the merged
  vector. Mirrors the Python merge loop (`seed + bridged`, dedup by id, seed wins)."
  [seed bridged]
  (loop [recs (concat seed bridged)
         seen #{}
         out  []]
    (if (empty? recs)
      out
      (let [rec (first recs)
            is-record? (or (map? rec) (and (vector? rec) (every? vector? rec)))
            k (when is-record? (rec-key rec))]
        (if (or (not is-record?) (nil? k) (contains? seen k))
          (recur (rest recs) seen out)
          (recur (rest recs) (conj seen k) (conj out rec)))))))

(defn bridged-flat
  "Flatten one bridge result {:cables :stations :links} into a single ordered
  record seq (cables ++ stations ++ links), as the Python `cs + ss + ls` does."
  [{:keys [cables stations links]}]
  (vec (concat cables stations links)))

(defn count-by-attr
  "Count records (maps or pair-vectors) carrying `attr` (e.g. \":cable/id\")."
  [recs attr]
  (count (filter (fn [r] (get (if (map? r) r (into {} r)) attr)) recs)))

;; ── headers (verbatim from ingest.py) ──────────────────────────────────────

(def bridge-header
  [";; watatsuna — GENERATED bridge graph (public dataset → kotoba EAVT). DO NOT hand-edit."
   ";; :representative (offline/sample). Live operator-gated fetch would tag :authoritative (G5/G7)."])

(def merged-header
  [";; watatsuna — GENERATED merged graph (seed + ingest bridge). DO NOT hand-edit."
   ";; dedup by id, seed wins. aggregate-first · sourcing-honest (ADR-2606012600)."])

;; ── -main (mirror `main`; offline default; --live refused per G7) ──────────

#?(:clj
   (defn -main [& argv]
     (let [actor (io/file (System/getProperty "user.dir") "20-actors" "watatsuna")]
       (when (some #{"--live"} argv)
         (if-not (System/getenv "WATATSUNA_OPERATOR_GATE")
           (throw (ex-info (str "REFUSED: live cable-dataset ingest is G7/Council-gated. "
                                "Set WATATSUNA_OPERATOR_GATE=<council-token> + supply an "
                                "operator DID. Offline mode (no --live) needs no flag.")
                           {:gate :G7}))
           (throw (ex-info (str "REFUSED: live fetch is an R0 scaffold — not implemented.")
                           {:gate :G7 :scaffold true}))))
       (let [args (remove #(str/starts-with? % "--") argv)
             srcs (if (seq args)
                    (map io/file args)
                    (sort (filter #(str/ends-with? (.getName ^java.io.File %) ".json")
                                  (.listFiles (io/file actor "data" "ingest")))))
             bridged (vec (mapcat (fn [s]
                                    (let [r (bridge-source s)]
                                      (println (format "  bridged %s: %d cables · %d stations · %d links"
                                                       (.getName ^java.io.File s)
                                                       (count (:cables r)) (count (:stations r)) (count (:links r))))
                                      (bridged-flat r)))
                                  srcs))
             bridge-out (io/file actor "data" "ingest" "telegeography-bridge.kotoba.edn")
             _ (spit bridge-out (to-edn bridged bridge-header))
             seed (edn/load-edn (io/file actor "data" "seed-cable-graph.kotoba.edn"))
             merged (merge-graph seed bridged)
             merged-out (io/file actor "data" "cable-graph.merged.kotoba.edn")]
         (spit merged-out (to-edn merged merged-header))
         (println (format "= merged graph: %d cables · %d stations · %d total records"
                          (count-by-attr merged ":cable/id")
                          (count-by-attr merged ":station/id")
                          (count merged)))
         (println (str "✓ wrote " bridge-out))
         (println (str "✓ wrote " merged-out))))))

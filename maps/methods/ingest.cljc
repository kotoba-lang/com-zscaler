(ns maps.methods.ingest
  "maps — legacy vertex_spatial → kotoba :feature/* normalizer + ingest (R0).
  1:1 Clojure port of `methods/ingest.py` (ADR-2606064500).

  Pure normalization logic (label mapping, H3 cell stamping stub, batch
  building). The --push path (HTTP kg.ingest_batch) is `#?(:clj ...)` only.

  CONSTITUTIONAL (ADR-2606064500 gates):
    G3 — every normalized feature carries :feature/sourcing :representative.
    G4 no-server-key — live push uses member/operator-DID bearer, no platform key.
    G7 outward-gated — push requires MAPS_OPERATOR_GATE=1 + --push flag.
    G9 — a feature is a PLACED THING, never a person.

  Portable .cljc."
  (:require [clojure.string :as str]
            [maps.methods.search :as search-ns]))

;; H3 resolutions the client queries (zoom→LOD ladder, mirrors ontology §2).
(def cell-resolutions [2 4 6 8 10 12])

(def ^:dynamic *getenv*
  "Injectable env-var lookup for the G4/G7 gates below (maps3d-bpmn, maps3d-persist,
  and this ns's own push-batch call sites read MAPS_OPERATOR_GATE / KOTOBA_AUTH /
  KOTOBA_ENDPOINT through here). Tests rebind it to simulate gate on/off without
  touching real process env."
  #?(:clj (fn [k] (System/getenv k))
     :cljs (fn [_] nil)))

;; ── inlined JSON codec (Python json.dumps-style ", "/": " separators — the
;;    maps3d net/BPMN/persist layers round-trip through this, not cheshire,
;;    to match the wire format the original ingest.py produced) ──────────────

(defn- json-escape ^String [^String s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

(defn json-encode
  "value → JSON text. Python json.dumps-compatible spacing (', ' / ': ')."
  [v]
  (cond
    (nil? v)     "null"
    (string? v)  (str "\"" (json-escape v) "\"")
    (true? v)    "true"
    (false? v)   "false"
    (number? v)  (str v)
    (map? v)     (str "{" (str/join ", " (map (fn [[k val]]
                                                 (str "\"" (json-escape (str k)) "\": " (json-encode val)))
                                               v))
                       "}")
    (sequential? v) (str "[" (str/join ", " (map json-encode v)) "]")
    :else        (str "\"" (json-escape (str v)) "\"")))

#?(:clj
   (do
     (declare ^:private json-value)

     (defn- skip-ws [^String s i]
       (loop [i i]
         (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
           (recur (inc i)) i)))

     (defn- json-string* [^String s i]
       (loop [i (inc i), sb (StringBuilder.)]
         (let [c (nth s i)]
           (cond
             (= c \") [(.toString sb) (inc i)]
             (= c \\)
             (let [e (nth s (inc i))]
               (case e
                 \" (do (.append sb \") (recur (+ i 2) sb))
                 \\ (do (.append sb \\) (recur (+ i 2) sb))
                 \/ (do (.append sb \/) (recur (+ i 2) sb))
                 \b (do (.append sb \backspace) (recur (+ i 2) sb))
                 \f (do (.append sb \formfeed) (recur (+ i 2) sb))
                 \n (do (.append sb \newline) (recur (+ i 2) sb))
                 \r (do (.append sb \return) (recur (+ i 2) sb))
                 \t (do (.append sb \tab) (recur (+ i 2) sb))
                 \u (let [cp (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)]
                      (.append sb (char cp)) (recur (+ i 6) sb))
                 (do (.append sb e) (recur (+ i 2) sb))))
             :else (do (.append sb c) (recur (inc i) sb))))))

     (defn- json-number* [^String s i]
       (let [end (loop [j i]
                   (if (and (< j (count s))
                            (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                     (recur (inc j)) j))
             tok (subs s i end)]
         [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))

     (defn- json-array* [^String s i]
       (loop [i (skip-ws s (inc i)), out []]
         (if (= (nth s i) \])
           [out (inc i)]
           (let [[v i] (json-value s i) i (skip-ws s i)]
             (if (= (nth s i) \,)
               (recur (skip-ws s (inc i)) (conj out v))
               [(conj out v) (inc i)])))))

     (defn- json-object* [^String s i]
       (loop [i (skip-ws s (inc i)), out {}]
         (if (= (nth s i) \})
           [out (inc i)]
           (let [[k i] (json-string* s i) i (skip-ws s i)
                 [v i] (json-value s (skip-ws s (inc i))) out (assoc out k v) i (skip-ws s i)]
             (if (= (nth s i) \,)
               (recur (skip-ws s (inc i)) out)
               [out (inc i)])))))

     (defn- json-value [^String s i]
       (let [i (skip-ws s i) c (nth s i)]
         (cond
           (= c \{) (json-object* s i)
           (= c \[) (json-array* s i)
           (= c \") (json-string* s i)
           (= c \t) [true (+ i 4)]
           (= c \f) [false (+ i 5)]
           (= c \n) [nil (+ i 4)]
           :else (json-number* s i))))

     (defn parse-json
       "JSON text → Clojure value (maps get string keys; ints parse as Long, floats
       as Double). Whitespace-tolerant, unicode-escape-aware."
       [text]
       (first (json-value (or text "{}") 0)))))

(def label-map
  {"Place"          ":place"   "Road"          ":road"      "Railway"     ":railway"
   "Building"       ":building" "River"        ":river"     "Lake"        ":lake"
   "Coastline"      ":coastline" "AdminArea"   ":admin-area" "Mountain"  ":mountain"
   "Port"           ":port"    "Airport"       ":airport"   "Station"     ":station"
   "BusStop"        ":bus-stop" "BusRoute"     ":bus-route" "SeaRoute"   ":sea-route"
   "AirRoute"       ":air-route" "LegalEntity" ":legal-entity" "LandRegistry" ":registry"
   "SatelliteScene" ":satellite-scene" "Spot"  ":place"})

(defn normalize-label [s]
  (or (get label-map (str s))
      (str ":" (-> (str s) str/trim str/lower-case (str/replace " " "-")))))

(defn normalize-row
  "One legacy vertex_spatial row → a kotoba :feature/* map (+ H3 cell stubs).
  Returns nil when the row lacks an id."
  [row]
  (let [fid (or (get row "vertex_id") (get row "id"))]
    (when fid
      (let [lat (get row "lat")
            lon (or (get row "lng") (get row "lon"))
            props (let [p (get row "props")]
                    (cond
                      (map? p) p
                      (string? p)
                      (try #?(:clj ((requiring-resolve 'cheshire.core/parse-string) p)
                              :cljs (js->clj (js/JSON.parse p)))
                           (catch #?(:clj Exception :cljs :default) _ {}))
                      :else {}))
            feat (cond-> {":feature/id"       (str fid)
                          ":feature/label"    (normalize-label (get row "label"))
                          ":feature/sourcing" ":representative"}
                   (get row "name")         (assoc ":feature/name" (get row "name"))
                   (get row "display_name") (assoc ":feature/display-name" (get row "display_name"))
                   (get row "category")     (assoc ":feature/category" (get row "category"))
                   (get row "source_did")   (assoc ":feature/source-did" (get row "source_did"))
                   (number? lat)            (assoc ":feature/lat" lat)
                   (number? lon)            (assoc ":feature/lon" lon))
            h-m (or (get props "heightM") (get props "height_m"))
            lvl (or (get props "levels") (get props "floors"))
            geom (get props "geometry")
            rest-props (dissoc props "geometry" "heightM" "height_m" "levels" "floors")
            feat (cond-> feat
                   (number? h-m)  (assoc ":feature/height-m" (double h-m))
                   (number? lvl)  (assoc ":feature/levels" (long lvl))
                   (some? geom)   (assoc ":feature/geometry"
                                         #?(:clj ((requiring-resolve 'cheshire.core/generate-string) geom)
                                            :cljs (.stringify js/JSON (clj->js geom))))
                   (seq rest-props) (assoc ":feature/props"
                                           #?(:clj ((requiring-resolve 'cheshire.core/generate-string) rest-props)
                                              :cljs (.stringify js/JSON (clj->js rest-props)))))]
        feat))))

(defn normalize
  "export = {\"rows\" [...]} or a bare vector.
  Returns [feats-by-id stamped-count unstamped-count]."
  [export]
  (let [rows (if (map? export) (get export "rows" export) export)
        feats (volatile! {}) stamped (volatile! 0) unstamped (volatile! 0)]
    (doseq [r rows]
      (when-let [f (normalize-row r)]
        (let [fid (get f ":feature/id")]
          (vswap! feats assoc fid f)
          (if (some #(str/starts-with? (str %) ":feature.cell/") (keys f))
            (vswap! stamped inc)
            (when (contains? f ":feature/lat")
              (vswap! unstamped inc))))))
    [@feats @stamped @unstamped]))

(defn to-kg-batch
  "feature dicts → kg.ingest_batch body {\"entities\" [...]}."
  [feats]
  (let [entities
        (mapv (fn [[fid f]]
                (let [name-toks (if (fn? search-ns/name-tokens)
                                  (reduce (fn [s k]
                                            (if-let [v (get f k)]
                                              (into s (search-ns/name-tokens (str v)))
                                              s))
                                          #{}
                                          [":feature/name" ":feature/display-name"])
                                  #{})
                      claims (into (mapv (fn [[k v]]
                                           {"pred" (subs (str k) 1) "value" (str v)})
                                         (dissoc f ":feature/id"))
                                   (map (fn [t] {"pred" "feature/name-token" "value" t})
                                        (sort name-toks)))]
                  {"id"       fid
                   "type"     "maps-feature"
                   "label_en" (or (get f ":feature/name")
                                  (get f ":feature/display-name") fid)
                   "claims"   claims
                   "relations" []}))
              feats)]
    {"entities" entities}))

(defn render-features-edn
  "feats {feature-id → ordered field-map} → a human-diffable .edn text: a
  `;;`-commented, sorted-by-id vector of the feature maps. Field keys/values
  that are colon-prefixed strings (this ns's string-keyed EAVT convention,
  e.g. \":feature/label\" \":building\") render as bare EDN keywords; plain
  strings render quoted/escaped."
  [feats]
  (let [edn-tok (fn [v]
                  (cond
                    (and (string? v) (str/starts-with? v ":")) v
                    (string? v) (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\"")
                    (nil? v)    "nil"
                    :else       (str v)))
        render-feat (fn [f]
                      (str "{" (str/join " " (map (fn [[k v]] (str (edn-tok k) " " (edn-tok v))) f)) "}"))]
    (str ";; generated by maps.methods.ingest/render-features-edn — do not hand-edit\n"
         "[" (str/join "\n " (map (comp render-feat second) (sort-by first feats)))
         "]")))

#?(:clj
   (defn push-batch
     "POST kg.ingest_batch JSON to the kotoba endpoint. Returns [status body].
     Uses babashka.http-client, not raw HttpURLConnection — babashka's SCI sandbox
     disallows HttpURLConnection/setRequestMethod, which broke every real-server
     integration test that exercised this path."
     [batch auth endpoint]
     (let [nsid "com.etzhayyim.apps.kotobase.kg.ingest_batch"
           url  (str (str/replace endpoint #"/$" "") "/xrpc/" nsid)
           post (requiring-resolve 'babashka.http-client/post)
           resp (post url {:headers {"content-type" "application/json"
                                      "authorization" (str "Bearer " auth)}
                            :body ((requiring-resolve 'cheshire.core/generate-string) batch)
                            :timeout 30000
                            :throw false})]
       [(:status resp) (:body resp)])))

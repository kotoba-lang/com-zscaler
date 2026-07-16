(ns maps.methods.onsen
  "maps — 温泉(onsen) discovery + transparent recommendation (ADR-2606064500 R2+).
  stdlib (+ cheshire at the JSON edge only). Portable .cljc.

  The Google-Maps-shaped onsen finder the roster lacked: it INGESTS hot-spring
  POIs from OpenStreetMap via Overpass (open ODbL data, scope-guard compliant)
  into the kotoba :feature/* substrate as :feature/label :onsen, and RANKS them
  for an 'osusume (おすすめ)' answer.

  THREE pure stages + two #?(:clj) I/O edges:
    overpass-ql      (pure)  bbox → Overpass QL string for onsen/hot-springs
    parse-overpass   (pure)  OSM JSON {\"elements\" [...]} → [:feature/* onsen maps]
    score-onsen      (pure)  one feature's OSM tags → {:score :why} — EXPLAINABLE
    recommend        (read)  query-fn → AVET(:feature/label :onsen) → scored, ranked
    fetch-overpass   (#?clj) HTTP read of public OSM (no key; rate-limit friendly)
    ingest-region!   (#?clj) fetch → parse → to-kg-batch → gated push (G4/G7)

  CONSTITUTIONAL (ADR-2606064500 gates):
    G3 — sourcing honesty: real OSM elements are :authoritative; absence ≠ 'no onsen'.
    G4 — no-server-key: push uses member/operator-DID bearer only.
    G7 — outward-gated: live push needs MAPS_OPERATOR_GATE=1. The Overpass READ is
         open public data (OSM ODbL), so it is not gated — but it is explicit/opt-in.
    G9 — a feature is a PLACED THING, never a person. Ranking scores BATHS, not bathers:
         every signal is a public place-fact (spring type, notability, amenities) —
         NO per-person affect/profile/engagement metric, NO dark pattern, fully
         transparent and recomputable from the stored tags (mirrors shiori's G1)."
  (:require [clojure.string :as str]
            [maps.methods.ingest :as ingest]
            [maps.methods.reverse :as rev]))

;; ── JSON edge (mirror ingest.cljc's requiring-resolve pattern) ────────────────
(defn- json-decode [s]
  (when (and (string? s) (seq s))
    #?(:clj  ((requiring-resolve 'cheshire.core/parse-string) s)
       :cljs (js->clj (.parse js/JSON s)))))

(defn- json-encode [x]
  #?(:clj  ((requiring-resolve 'cheshire.core/generate-string) x)
     :cljs (.stringify js/JSON (clj->js x))))

;; ── 1. Overpass QL (pure) ─────────────────────────────────────────────────────
;; All the ways an onsen shows up in OSM. `out center` gives a centroid for
;; ways/relations so every element yields a (lat,lon).
(def ^:private onsen-selectors
  ["node[\"natural\"=\"hot_spring\"](%s);"
   "way[\"natural\"=\"hot_spring\"](%s);"
   "node[\"amenity\"=\"public_bath\"][\"bath:type\"=\"onsen\"](%s);"
   "way[\"amenity\"=\"public_bath\"][\"bath:type\"=\"onsen\"](%s);"
   "node[\"amenity\"=\"public_bath\"](%s);"
   "way[\"amenity\"=\"public_bath\"](%s);"
   "node[\"leisure\"=\"spa\"](%s);"
   "way[\"leisure\"=\"spa\"](%s);"
   "node[\"onsen\"=\"yes\"](%s);"
   "way[\"onsen\"=\"yes\"](%s);"])

(defn overpass-ql
  "Build an Overpass QL query for onsen/hot-springs inside bbox [south west north east].
  `timeout-s` defaults to 60. Returns a string."
  [[south west north east] & {:keys [timeout-s] :or {timeout-s 60}}]
  (let [bbox (str/join "," (map #(format "%.6f" (double %)) [south west north east]))
        body (str/join "\n  " (map #(format % bbox) onsen-selectors))]
    (str "[out:json][timeout:" timeout-s "];\n(\n  " body "\n);\nout center tags;")))

;; ── tags we keep for scoring + read-through (props bag) ────────────────────────
(def ^:private kept-tags
  ["natural" "amenity" "leisure" "bath:type" "onsen" "bath:open_air" "sauna"
   "wikidata" "wikipedia" "name" "name:ja" "name:en" "opening_hours"
   "website" "contact:website" "phone" "contact:phone" "wheelchair" "tourism"])

(defn- onsen-element?
  "Is this OSM element actually a bath/hot-spring (not just any spa keyword soup)?"
  [tags]
  (boolean
   (or (= "hot_spring" (get tags "natural"))
       (= "public_bath" (get tags "amenity"))
       (= "spa" (get tags "leisure"))
       (= "yes" (get tags "onsen"))
       (some? (get tags "bath:type")))))

(defn- element->feature
  "One Overpass element → a :feature/* onsen map, or nil if not an onsen / no centroid."
  [el]
  (let [tags (get el "tags" {})]
    (when (onsen-element? tags)
      (let [otype (get el "type")
            oid   (get el "id")
            center (get el "center")
            lat (or (get el "lat") (get center "lat"))
            lon (or (get el "lon") (get center "lon"))
            nm  (or (get tags "name:ja") (get tags "name") (get tags "name:en"))
            kept (into {} (keep (fn [k] (when-let [v (get tags k)] [k v])) kept-tags))]
        (when (and otype oid (number? lat) (number? lon))
          (cond-> {":feature/id"       (str "osm/" otype "/" oid)
                   ":feature/label"    ":onsen"
                   ":feature/source"   ":osm"
                   ":feature/source-did" "did:web:maps.etzhayyim.com:infrastructure"
                   ":feature/sourcing" ":authoritative"   ;; a real public OSM element (G3)
                   ":feature/lat"      (double lat)
                   ":feature/lon"      (double lon)
                   ":feature/props"    (json-encode kept)}
            nm (assoc ":feature/name" nm)))))))

(defn parse-overpass
  "OSM/Overpass JSON {\"elements\" [...]} → {feature-id feature-map} (ingest-ready).
  Pure; safe on partial/empty input."
  [osm-json]
  (let [els (get osm-json "elements" [])]
    (reduce (fn [acc el]
              (if-let [f (element->feature el)]
                (assoc acc (get f ":feature/id") f)
                acc))
            {} els)))

;; ── 2. transparent recommendation score (pure, EXPLAINABLE) ───────────────────
;; Every component is a PUBLIC PLACE-FACT from OSM tags — never a person-fact.
;; Score is fully recomputable from the stored tags; `:why` makes it auditable.
(defn score-onsen
  "Score one onsen from its OSM tag map → {:score double :why [string]}.
  Higher = a more recommendable BATH (authenticity + notability + completeness)."
  [tags]
  (let [t   (or tags {})
        has #(let [v (get t %)] (and v (not= "" v) (not= "no" v)))
        eq  #(= %2 (get t %1))
        add (fn [[s why] pts label cond?]
              (if cond? [(+ s pts) (conj why label)] [s why]))
        complete (count (filter true?
                                [(boolean (has "opening_hours"))
                                 (boolean (or (has "website") (has "contact:website")))
                                 (boolean (or (has "phone") (has "contact:phone")))
                                 (boolean (eq "wheelchair" "yes"))]))
        authentic? (or (eq "natural" "hot_spring") (eq "onsen" "yes")
                       (eq "bath:type" "onsen"))]
    (-> [0.0 []]
        (add 3.0 "authentic-hot-spring" authentic?)
        (add 2.0 "notable(wikidata/wikipedia)" (or (has "wikidata") (has "wikipedia")))
        (add 1.0 "named"               (has "name"))
        (add 1.0 "open-air-rotenburo"  (eq "bath:open_air" "yes"))
        (add 0.5 "has-sauna"           (has "sauna"))
        (add (* 0.5 complete) (str "amenity-info×" complete) (pos? complete))
        (->> (zipmap [:score :why])))))

;; ── 3. recommend (read path over the kotoba :onsen substrate) ─────────────────
(defn- claim-val [claims pred]
  (some #(when (= (get % "pred") pred) (get % "value")) claims))

(defn recommend
  "Rank onsen for an 'osusume' answer.
  query-fn: (fn [pred objects limit] → entity-seq) — AVET probe (kotoba or test store).
  Opts:
    :near  [lat lon] — bias by great-circle proximity (adds :distance-m, used as tiebreak)
    :limit  result cap (default 20)
    :pool   how many :onsen features to pull before scoring (default 500)
  Returns [{:id :name :lat :lon :score :why :distance-m}], best first."
  [query-fn & {:keys [near limit pool] :or {limit 20 pool 500}}]
  (let [ents (query-fn "feature/label" [":onsen"] pool)
        scored
        (keep
         (fn [e]
           (let [claims (get e "claims" [])
                 nm  (claim-val claims "feature/name")
                 lat (some-> (claim-val claims "feature/lat") str Double/parseDouble)
                 lon (some-> (claim-val claims "feature/lon") str Double/parseDouble)
                 tags (or (json-decode (claim-val claims "feature/props")) {})
                 {:keys [score why]} (score-onsen tags)
                 dist (when (and near lat lon)
                        (rev/haversine-m (double (first near)) (double (second near))
                                         lat lon))]
             (when (get e "id")
               (cond-> {:id (get e "id") :name nm :lat lat :lon lon
                        :score score :why why}
                 dist (assoc :distance-m dist)))))
         ents)
        ;; near → reward closeness without letting it swamp quality:
        ;; effective = score + proximity-bonus(≤2.0, decays over ~50 km), tiebreak by distance.
        ranked
        (sort-by
         (fn [r]
           (let [d (:distance-m r)
                 prox (if d (/ 2.0 (+ 1.0 (/ (double d) 50000.0))) 0.0)]
             [(- (+ (:score r) prox)) (or d 0.0) (or (:name r) "")]))
         scored)]
    (vec (take limit ranked))))

;; ── 4. I/O edges (#?(:clj)) ───────────────────────────────────────────────────
(def default-overpass "https://overpass-api.de/api/interpreter")

#?(:clj
   (defn fetch-overpass
     "POST an Overpass QL query → parsed OSM JSON map. Reads PUBLIC OSM data
     (ODbL, no API key — G4 no-server-key holds trivially). NOT gated (open read),
     but explicit/opt-in. `endpoint` defaults to the public instance; be polite to it."
     ([ql] (fetch-overpass ql default-overpass))
     ([ql endpoint]
      (let [body (.getBytes (str "data=" (java.net.URLEncoder/encode ^String ql "UTF-8")) "UTF-8")
            conn (doto (.openConnection (java.net.URL. endpoint))
                   (.setRequestMethod "POST")
                   (.setDoOutput true)
                   (.setConnectTimeout 30000)
                   (.setReadTimeout 90000)
                   (.setRequestProperty "content-type" "application/x-www-form-urlencoded")
                   (.setRequestProperty "user-agent" "etzhayyim-maps-onsen/0.1 (+did:web:maps.etzhayyim.com)"))]
        (.connect conn)
        (with-open [os (.getOutputStream conn)] (.write os body))
        (json-decode (slurp (.getInputStream conn)))))))

#?(:clj
   (defn ingest-region!
     "Fetch onsen in bbox [south west north east] from Overpass, normalize to
     :feature/* and push into the kotoba Datom log. Push is G4/G7 gated:
     needs MAPS_OPERATOR_GATE=1 + KOTOBA_AUTH (member/operator bearer) + KOTOBA_ENDPOINT.
     Returns a summary map; throws ex-info on a gate/credential refusal."
     [bbox & {:keys [endpoint] :or {endpoint default-overpass}}]
     (let [osm   (fetch-overpass (overpass-ql bbox) endpoint)
           feats (parse-overpass osm)
           batch (ingest/to-kg-batch feats)]
       (when (not= (System/getenv "MAPS_OPERATOR_GATE") "1")
         (throw (ex-info (str "maps G7: live onsen push is Council+operator gated. "
                              "Set MAPS_OPERATOR_GATE=1 with attestation to enable. "
                              "(" (count feats) " onsen parsed, not pushed.)")
                         {:exit 1 :parsed (count feats)})))
       (let [auth (System/getenv "KOTOBA_AUTH")
             ep   (System/getenv "KOTOBA_ENDPOINT")]
         (when-not (and (seq auth) (seq ep))
           (throw (ex-info "maps G4/G7: push needs KOTOBA_AUTH + KOTOBA_ENDPOINT (no-server-key)."
                           {:exit 1})))
         (let [[status _] (ingest/push-batch batch auth ep)]
           {:bbox bbox :parsed (count feats) :pushed (count (get batch "entities"))
            :status status})))))

(ns tsumugi.methods.publish
  "tsumugi 紡ぎ — PUBLISH the woven power-graph as self-sovereign linked data.
  Clojure port of methods/publish.py (1:1). ADR-2606092000.

  etzhayyim becomes a DATA PROVIDER: it publishes its woven power-graph under its OWN
  resolvable vocabulary (epw:) so anyone can load it into a triplestore and federate.
  Self-sovereign — the dataset is content-addressed (sha256 over the canonical N-Triples
  is its identity), licensed, provenance-honest (G5).

  Emits (local files): etzhayyim-power-graph.{nt,jsonld} + dataset-manifest.json. NOT IPFS,
  NOT a network publish. stdlib only; its only dependency is the already-ported analyze-scale
  (load-graph + analyze). SHA-256 via java.security.MessageDigest; file I/O at the #?(:clj) edge."
  (:require [clojure.string :as str]
            [tsumugi.methods.analyze-scale :as a]
            #?(:clj [clojure.java.io :as io]))
  #?(:clj (:import [java.security MessageDigest])))

(def ^:private NS "https://etzhayyim.com/ns/power#")
(def ^:private ID "https://etzhayyim.com/id/power/")
(def ^:private RDF "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
(def ^:private RDFS "http://www.w3.org/2000/01/rdf-schema#")
(def ^:private XSD "http://www.w3.org/2001/XMLSchema#")
(def ^:private PUBLISHER-DID "did:web:etzhayyim.com:actor:tsumugi")
(def LICENSE "Apache-2.0 + etzhayyim Charter Compliance Rider v3.1 (/CHARTER-RIDER.md)")

(def ^:private SCALE-JA
  {":san" "industry/産" ":kan" "government/官" ":gaku" "academia/学"
   ":hou" "press/報" ":min" "civil/民" ":kin" "finance/金"})

(def TIE-PRED
  {":custodies" "custodies" ":depends-on" "dependsOn" ":funds" "funds"
   ":awards" "awards" ":seats-on" "seatsOn" ":co-member" "coMemberOf"
   ":supplies" "supplies" ":covers" "covers" ":employs" "employs" ":follows" "follows"})

(defn- ent-iri [pwr-id] (str ID pwr-id))
(defn- lstrip-colon [s] (str/replace (str s) #"^:+" ""))
(defn- esc [s] (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"") (str/replace "\n" "\\n")))

(defn- t-iri [s p o] (str "<" s "> <" p "> <" o "> ."))
(defn- t-lit [s p o] (str "<" s "> <" p "> \"" (esc o) "\" ."))
(defn- t-typed [s p o typ] (str "<" s "> <" p "> \"" (esc o) "\"^^<" typ "> ."))

(defn build-triples
  "Return N-Triples lines: org/seat nodes + relations + the etzhayyim-DERIVED per-locality
  concentration layer. Person-excluded by construction (S2 holds upstream)."
  [nodes ties result]
  (let [node-lines
        (mapcat (fn [[nid n]]
                  (let [s (ent-iri nid)
                        standing (lstrip-colon (get n ":pwr/standing" ":institutional"))
                        cls (if (= standing "public-seat") "PublicSeat" "Org")]
                    (concat
                     [(t-iri s (str RDF "type") (str NS cls))]
                     (when (get n ":pwr/label") [(t-lit s (str RDFS "label") (get n ":pwr/label"))])
                     (keep (fn [[attr pred]]
                             (when-let [v (get n attr)] (t-lit s (str NS pred) (lstrip-colon (str v)))))
                           [[":pwr/scale" "scale"] [":pwr/sector" "sector"]
                            [":pwr/locality" "locality"] [":pwr/collective-kind" "collectiveKind"]])
                     (when (get n ":pwr/sourcing")
                       [(t-lit s (str NS "sourcing") (lstrip-colon (get n ":pwr/sourcing")))]))))
                nodes)
        tie-lines
        (keep (fn [tie]
                (let [f (get tie ":tie/from") to (get tie ":tie/to") pred (get TIE-PRED (get tie ":tie/kind"))]
                  (when (and (contains? nodes f) (contains? nodes to) pred)
                    (t-iri (ent-iri f) (str NS pred) (ent-iri to)))))
              ties)
        loc-lines
        (mapcat (fn [x]
                  (let [liri (str ID "locality/" (get x "locality"))]
                    [(t-iri liri (str RDF "type") (str NS "Locality"))
                     (t-lit liri (str RDFS "label") (get x "locality"))
                     (t-typed liri (str NS "concentration") (str (get x "concentration")) (str XSD "decimal"))
                     (t-typed liri (str NS "sectorDiversity") (str (get x "sector_diversity")) (str XSD "integer"))
                     (t-lit liri (str NS "derivedBy") PUBLISHER-DID)]))
                (get result "localities"))]
    (vec (concat node-lines tie-lines loc-lines))))

(defn build-jsonld
  "JSON-LD @graph over the etzhayyim vocabulary."
  [nodes ties result]
  (let [out-edges (reduce (fn [acc tie]
                            (let [f (get tie ":tie/from") to (get tie ":tie/to")
                                  pred (get TIE-PRED (get tie ":tie/kind"))]
                              (if (and (contains? nodes f) (contains? nodes to) pred)
                                (update acc f (fnil conj []) [pred to])
                                acc)))
                          {} ties)
        node-graph
        (map (fn [[nid n]]
               (let [standing (lstrip-colon (get n ":pwr/standing" ":institutional"))
                     base {"@id" (ent-iri nid)
                           "@type" (if (= standing "public-seat") "epw:PublicSeat" "epw:Org")
                           "rdfs:label" (get n ":pwr/label" nid)}
                     base (reduce (fn [m [attr pred]]
                                    (if (get n attr) (assoc m (str "epw:" pred) (lstrip-colon (str (get n attr)))) m))
                                  base [[":pwr/scale" "scale"] [":pwr/sector" "sector"]
                                        [":pwr/locality" "locality"] [":pwr/collective-kind" "collectiveKind"]
                                        [":pwr/sourcing" "sourcing"]])]
                 (reduce (fn [m [pred to]]
                           (update m (str "epw:" pred) (fnil conj []) {"@id" (ent-iri to)}))
                         base (get out-edges nid []))))
             nodes)
        loc-graph
        (map (fn [x] {"@id" (str ID "locality/" (get x "locality")) "@type" "epw:Locality"
                      "rdfs:label" (get x "locality")
                      "epw:concentration" (get x "concentration")
                      "epw:sectorDiversity" (get x "sector_diversity")
                      "epw:derivedBy" PUBLISHER-DID})
             (get result "localities"))]
    {"@context" {"epw" NS "rdfs" RDFS "rdfs:label" {"@id" (str RDFS "label")}}
     "@graph" (vec (concat node-graph loc-graph))}))

(defn- sha256-hex [^String s]
  #?(:clj (let [md (MessageDigest/getInstance "SHA-256")]
            (.update md (.getBytes s "UTF-8"))
            (apply str (map #(format "%02x" (bit-and % 0xFF)) (.digest md))))
     :cljs (throw (ex-info "sha256 requires the JVM" {}))))

(defn build-manifest
  "DCAT/VoID-style manifest: title/license/provenance/counts + the content-address."
  [nodes ties result nt-lines]
  (let [cid-hash (str "sha256:" (sha256-hex (str/join "\n" nt-lines)))
        sectors (sort (distinct (keep #(get % ":pwr/sector") (vals nodes))))
        edges (count (filter (fn [t] (and (contains? nodes (get t ":tie/from"))
                                          (contains? nodes (get t ":tie/to"))
                                          (contains? TIE-PRED (get t ":tie/kind")))) ties))]
    {"@type" "dcat:Dataset"
     "title" "etzhayyim power-dynamics knowledge graph (tsumugi 紡ぎ)"
     "description" (str "A self-sovereign linked-data power-graph: organisations + their custody/"
                        "dependency 縁, enriched with etzhayyim's original 産官学報 cross-sector "
                        "concentration, scale, collective-kind, and vertical-integration layers.")
     "publisher" PUBLISHER-DID
     "license" LICENSE
     "vocabulary" NS
     "entityNamespace" ID
     "contentHash" cid-hash
     "counts" {"nodes" (count nodes) "edges" edges
               "localities" (count (get result "localities")) "triples" (count nt-lines)}
     "sectors" (mapv #(get SCALE-JA % %) sectors)
     "provenance" {"upstreamSources" ["Wikidata (P749 parent organization, WDQS)"
                                       "GLEIF Level-2 Relationship Records (api.gleif.org)"
                                       "curated structural-public facts (:representative)"]
                   "etzhayyimDerived" ["産官学報 cross-sector concentration (epw:concentration)"
                                       "scale / sector / collective-kind enrichment"
                                       "vertical integration across scales"
                                       "旗 hata ideology/faction (separate dataset)"]
                   "note" (str "Per-edge citations are preserved in the canonical kotoba-EDN seed. "
                               "Derived layers are etzhayyim's authored contribution (not in any upstream).")}
     "gates" (str "S1 edge-primary · S2 person-excluded (institutional/public-seat only) · "
                  "S5 non-adjudicating (no verdict predicate) · G5 sourcing-honest")
     "interop" {"jsonld" "etzhayyim-power-graph.jsonld" "ntriples" "etzhayyim-power-graph.nt"
                "canonical" "data/seed-scale-power.kotoba.edn (kotoba Datom EDN)"}}))

#?(:clj
   (defn publish
     "Build + write the three artifacts locally. Returns the manifest. Uses analyze-scale's
     load-graph + analyze. seed-path defaults to the committed scale seed."
     ([out-dir] (publish out-dir "20-actors/tsumugi/data/seed-scale-power.kotoba.edn"))
     ([out-dir seed-path]
      (let [[nodes ties] (a/load-graph seed-path)
            result (a/analyze nodes ties)
            nt (build-triples nodes ties result)
            jsonld (build-jsonld nodes ties result)
            manifest (build-manifest nodes ties result nt)]
        (io/make-parents (str out-dir "/x"))
        (spit (str out-dir "/etzhayyim-power-graph.nt") (str (str/join "\n" nt) "\n"))
        (spit (str out-dir "/etzhayyim-power-graph.jsonld") (pr-str jsonld))
        (spit (str out-dir "/dataset-manifest.json") (pr-str manifest))
        manifest))))

#?(:clj
   (defn -main
     "CLI entry: mirrors publish.main — serialize the power-graph as N-Triples + JSON-LD +
     a content-addressed dataset-manifest.json (etzhayyim as a power-data PROVIDER). All offline
     serialization (no network); IPFS pin is publish_ipfs (separate). ADR-2606261200."
     [& argv]
     (let [argv (vec argv)
           here (let [f  (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (io/file pp "data"))) pp (io/file "20-actors" "tsumugi")))
           out  (if (some #{"--out"} argv) (io/file (nth argv (inc (.indexOf argv "--out")))) (io/file here "out"))
           m    (publish (.getPath out))
           ch   (str (or (get m :contentHash) (get m "contentHash")))
           cnt  (or (get m :counts) (get m "counts"))]
       (println (str "[tsumugi/publish] etzhayyim is now a power-data PROVIDER — "
                     (or (get cnt :nodes) (get cnt "nodes")) " nodes / "
                     (or (get cnt :triples) (get cnt "triples")) " triples / "
                     (subs ch 0 (min 23 (count ch))) "… → out/etzhayyim-power-graph.{nt,jsonld} + dataset-manifest.json")))))

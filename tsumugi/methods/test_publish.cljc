(ns tsumugi.methods.test-publish
  "Cross-language oracle tests for tsumugi.methods.publish — the Clojure port of
  methods/publish.py.

  No test_publish.py existed. Two oracle layers, both from the REAL Python:
  (1) a small ORDERED fixture (2 nodes + 1 tie + 1 locality) → the exact 18 N-Triples
      lines and the exact sha256 content-address (68cbd922…) — byte-identical because the
      fixture's iteration order is fixed; (2) the committed seed → order-independent counts
      (628 nodes / 637 edges / 31 localities / 5179 triples)."
  (:require [clojure.test :refer [deftest is testing]]
            [tsumugi.methods.publish :as pub]
            [tsumugi.methods.analyze-scale :as a]))

(def fixture-nodes
  (array-map
   "org.a" {":pwr/id" "org.a" ":pwr/label" "Alpha" ":pwr/standing" ":institutional"
            ":pwr/scale" ":national" ":pwr/sector" ":san" ":pwr/locality" "jp.x"
            ":pwr/collective-kind" ":org" ":pwr/sourcing" ":representative"}
   "seat.b" {":pwr/id" "seat.b" ":pwr/label" "Beta seat" ":pwr/standing" ":public-seat"
             ":pwr/scale" ":municipal" ":pwr/sector" ":kan" ":pwr/locality" "jp.x"}))
(def fixture-ties [{":tie/id" "t1" ":tie/from" "org.a" ":tie/to" "seat.b" ":tie/kind" ":funds"
                    ":tie/sources" ["a" "b"] ":tie/grasping-load" 0.5}])
(def fixture-result {"localities" [{"locality" "jp.x" "concentration" 2.0 "sector_diversity" 2}]})

(def expected-nt
  ["<https://etzhayyim.com/id/power/org.a> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://etzhayyim.com/ns/power#Org> ."
   "<https://etzhayyim.com/id/power/org.a> <http://www.w3.org/2000/01/rdf-schema#label> \"Alpha\" ."
   "<https://etzhayyim.com/id/power/org.a> <https://etzhayyim.com/ns/power#scale> \"national\" ."
   "<https://etzhayyim.com/id/power/org.a> <https://etzhayyim.com/ns/power#sector> \"san\" ."
   "<https://etzhayyim.com/id/power/org.a> <https://etzhayyim.com/ns/power#locality> \"jp.x\" ."
   "<https://etzhayyim.com/id/power/org.a> <https://etzhayyim.com/ns/power#collectiveKind> \"org\" ."
   "<https://etzhayyim.com/id/power/org.a> <https://etzhayyim.com/ns/power#sourcing> \"representative\" ."
   "<https://etzhayyim.com/id/power/seat.b> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://etzhayyim.com/ns/power#PublicSeat> ."
   "<https://etzhayyim.com/id/power/seat.b> <http://www.w3.org/2000/01/rdf-schema#label> \"Beta seat\" ."
   "<https://etzhayyim.com/id/power/seat.b> <https://etzhayyim.com/ns/power#scale> \"municipal\" ."
   "<https://etzhayyim.com/id/power/seat.b> <https://etzhayyim.com/ns/power#sector> \"kan\" ."
   "<https://etzhayyim.com/id/power/seat.b> <https://etzhayyim.com/ns/power#locality> \"jp.x\" ."
   "<https://etzhayyim.com/id/power/org.a> <https://etzhayyim.com/ns/power#funds> <https://etzhayyim.com/id/power/seat.b> ."
   "<https://etzhayyim.com/id/power/locality/jp.x> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://etzhayyim.com/ns/power#Locality> ."
   "<https://etzhayyim.com/id/power/locality/jp.x> <http://www.w3.org/2000/01/rdf-schema#label> \"jp.x\" ."
   "<https://etzhayyim.com/id/power/locality/jp.x> <https://etzhayyim.com/ns/power#concentration> \"2.0\"^^<http://www.w3.org/2001/XMLSchema#decimal> ."
   "<https://etzhayyim.com/id/power/locality/jp.x> <https://etzhayyim.com/ns/power#sectorDiversity> \"2\"^^<http://www.w3.org/2001/XMLSchema#integer> ."
   "<https://etzhayyim.com/id/power/locality/jp.x> <https://etzhayyim.com/ns/power#derivedBy> \"did:web:etzhayyim.com:actor:tsumugi\" ."])

(deftest triples-byte-identical-on-fixture
  (is (= expected-nt (pub/build-triples fixture-nodes fixture-ties fixture-result))))

(deftest manifest-content-hash-byte-identical
  (let [m (pub/build-manifest fixture-nodes fixture-ties fixture-result
                              (pub/build-triples fixture-nodes fixture-ties fixture-result))]
    (is (= "sha256:68cbd92209af04646a4f4854e0aeff734091a7e09e33331fda72203e86187a15"
           (get m "contentHash")))
    (is (= {"nodes" 2 "edges" 1 "localities" 1 "triples" 18} (get m "counts")))
    (is (= ["government/官" "industry/産"] (get m "sectors")))))

(deftest jsonld-graph-shape
  (let [jl (pub/build-jsonld fixture-nodes fixture-ties fixture-result)
        n0 (first (get jl "@graph"))]
    (is (= 3 (count (get jl "@graph"))))
    (is (= "https://etzhayyim.com/id/power/org.a" (get n0 "@id")))
    (is (= "epw:Org" (get n0 "@type")))
    (is (= "Alpha" (get n0 "rdfs:label")))
    (is (= "national" (get n0 "epw:scale")))
    (is (= [{"@id" "https://etzhayyim.com/id/power/seat.b"}] (get n0 "epw:funds")))))

(deftest real-seed-counts
  (let [[nodes ties] (a/load-graph "20-actors/tsumugi/data/seed-scale-power.kotoba.edn")
        result (a/analyze nodes ties)
        nt (pub/build-triples nodes ties result)
        m (pub/build-manifest nodes ties result nt)]
    (is (= 628 (get-in m ["counts" "nodes"])))
    (is (= 637 (get-in m ["counts" "edges"])))
    (is (= 31 (get-in m ["counts" "localities"])))
    (is (= 5179 (get-in m ["counts" "triples"])))))

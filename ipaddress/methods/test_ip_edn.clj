#!/usr/bin/env bb
;; Clojure test for methods/ip_edn.cljc — reader + classify + edn serializer.
;; (No python test existed for ip_edn; fresh coverage, parity with ip_edn.py.)
(ns ipaddress.methods.test-ip-edn
  "Guards classify bucket counts against the ip_edn.py baseline on the real seed
  (rirs 5 / asns 17 / ranges 12 / ips 3 / announces 12 / members 3 / geos 2 /
  rdns 2 / whois 3), keyed-vs-list buckets, and the edn-val serializer."
  (:require [ipaddress.methods.ip-edn :as ip]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private here (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile))
(defn- seed [name] (str (io/file here "data" name)))

(def EXPECT {"rirs" 5 "asns" 17 "ranges" 12 "ips" 3 "announces" 12
             "members" 3 "geos" 2 "rdns" 2 "whois" 3})

(deftest classify-seed-bucket-counts
  (let [c (ip/classify (ip/read-file (seed "seed-ip-network.kotoba.edn")))]
    (doseq [[bucket n] EXPECT]
      (is (= n (count (get c bucket))) (str bucket " count")))))

(deftest keyed-buckets-are-maps-edges-are-lists
  (let [c (ip/classify (ip/read-file (seed "seed-ip-network.kotoba.edn")))]
    (doseq [k ["rirs" "asns" "ranges" "ips"]] (is (map? (get c k)) (str k " keyed")))
    (doseq [l ["announces" "members" "geos" "rdns" "whois"]] (is (vector? (get c l)) (str l " list")))
    ;; keyed by the row's own id
    (is (every? (fn [[k v]] (= k (get v ":asn/id"))) (get c "asns")))))

(deftest edn-val-serialization
  (is (= "true" (ip/edn-val true)))
  (is (= "false" (ip/edn-val false)))
  (is (= "42" (ip/edn-val 42)))
  (is (= ":rir/arin" (ip/edn-val ":rir/arin")))
  (is (= "\"1.2.3.4\"" (ip/edn-val "1.2.3.4")))
  (is (= "[1 :k]" (ip/edn-val [1 ":k"]))))

(deftest to-edn-roundtrips-readable
  (let [recs [(array-map ":asn/id" "as13335" ":asn/name" "CLOUDFLARE" ":asn/peers" 42)]
        reparsed (ip/read-all (ip/to-edn recs [";; header"]))]
    (is (= 1 (count reparsed)))
    (is (= "as13335" (get (first reparsed) ":asn/id")))
    (is (= 42 (get (first reparsed) ":asn/peers")))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'ipaddress.methods.test-ip-edn)]
    (System/exit (if (zero? (+ fail error)) 0 1))))

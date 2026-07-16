(ns kaname.tests.test-bridge
  "kaname 要 — LIVE-engine bridge tests (ADR-2606172100 R1). Deterministic: host allowlist, graph-CID
  parity, tx_edn provenance, and the exactly-once cursor — all via a STUB transport (no live engine)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [kaname.methods.sos :as sos]
            [kaname.methods.centrality :as c]
            [kaname.methods.kotoba :as kkot]
            [kaname.methods.kotoba-bridge :as br]
            [kotoba.datom :as kd]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed (io/file actor-dir "data" "seed-sos.kotoba.edn")))

(deftest test-host-allowlist
  (testing "fleet hosts pass; anything else is refused BEFORE I/O (charter invariant)"
    (is (nil? (br/assert-kotoba "http://127.0.0.1:8077/x")))
    (is (nil? (br/assert-kotoba "http://192.168.1.70:8077/x")))
    (is (thrown? clojure.lang.ExceptionInfo (br/assert-kotoba "http://evil.example.com:8077/x")))
    (is (thrown? clojure.lang.ExceptionInfo (br/assert-kotoba "https://127.0.0.1:8077/x")))))  ; lookalike scheme

#?(:clj
   (deftest test-graph-cid
     (testing "graph-cid is a deterministic CIDv1 (base32 'b' prefix), stable per name"
       (is (= (br/graph-cid "kaname") (br/graph-cid "kaname")))
       (is (str/starts-with? (br/graph-cid "kaname") "b"))
       (is (> (count (br/graph-cid "kaname")) 40))
       (is (not= (br/graph-cid "kaname") (br/graph-cid "chie"))))))

(deftest test-tx-edn-provenance
  (testing "tx->edn-vec carries the local datoms + :kaname.tx/* provenance"
    (let [tx {:tx/id "kaname-0" :tx/cid "bLOCAL" :tx/prev "" :tx/as-of "as-of:0"
              :tx/datoms [[":db/add" "kaname:sos" ":kaname/point" "OpenAI"]]}
          s (br/tx->edn-vec tx)]
      (is (str/includes? s ":kaname/point"))
      (is (str/includes? s ":kaname.tx/id"))
      (is (str/includes? s ":kaname.tx/local-cid"))
      (is (str/includes? s "bLOCAL")))))

#?(:clj
   (deftest test-push-dry-run-and-cursor
     (let [{:keys [nodes edges]} (sos/load-file* seed)
           res1 (c/leverage-r1 nodes edges (sos/leverage nodes edges))
           ds (kkot/leverage->datoms nodes res1 [:chie :web])
           tmp (str (java.io.File/createTempFile "kaname-bridge" ".edn"))]
       (.delete (io/file tmp))
       (kkot/persist! ds {:tx-id "kaname-0" :as-of "as-of:0" :log-path tmp})
       (testing "dry-run returns the bodies + graph-cid, pushes nothing"
         (let [r (br/push tmp {})]
           (is (= "dry-run" (:mode r)))
           (is (= 1 (:pending r)))
           (is (str/starts-with? (:graph-cid r) "b"))))
       (testing "LIVE push via a STUB transport commits + writes an exactly-once :bridge/ cursor"
         (let [stub (fn [_url _body] {:status "ok" :tx_cid "bREMOTE" :commit_cid "bCOMMIT" :datom_count (count ds)})
               r (br/push tmp {:live true :transport stub})]
           (is (= "live" (:mode r)))
           (is (= 1 (:pushed r)))
           (is (= ["bREMOTE"] (:remote-tx-cids r)))
           (is (true? (:ok (kd/verify-chain tmp))))))    ; the appended checkpoint keeps the chain valid
       (testing "re-push is a no-op (exactly-once — the cursor advanced)"
         (let [r2 (br/push tmp {:live true :transport (fn [_ _] (throw (ex-info "should not be called" {})))})]
           (is (= 0 (:pushed r2)))))
       (.delete (io/file tmp)))))

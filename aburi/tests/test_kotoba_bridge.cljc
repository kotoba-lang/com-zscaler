(ns aburi.tests.test-kotoba-bridge
  "aburi 炙り — kotoba-bridge IO-leg tests (cljc, ADR-2606161630).
  Covers: request shaping (URL/body/headers), dry-run mode, bridge_state cursor replay,
  exactly-once idempotency. All assertions offline — NO real network calls."
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [clojure.string :as str]
            [aburi.methods.kotoba :as kt]
            [aburi.methods.kotoba-bridge :as kb]))

;; ─── helpers ──────────────────────────────────────────────────────────────────

(defn- make-test-log []
  ;; Returns a fresh temp file path (clj-only)
  #?(:clj
     (let [f (java.io.File/createTempFile "aburi-bridge-test-" ".kotoba.edn")]
       (.deleteOnExit f)
       f)))

(defn- seed-log
  "Append n synthetic data txs to the log; returns the log file."
  [log n]
  (doseq [i (range n)]
    (let [ds [(kt/add (str "surf-" i) ":organism/id"    (str "surf-" i))
              (kt/add (str "surf-" i) ":organism/label" (str "Surface " i))]
          tx (kt/make-tx ds (inc i) (+ 2606161630 i) (kt/head-cid log))]
      (kt/append-tx tx log)))
  log)

;; ─── assert-kotoba ─────────────────────────────────────────────────────────────

(deftest test-assert-kotoba-allows-loopback
  (testing "assert-kotoba passes for loopback endpoint"
    (is (nil? (kb/assert-kotoba "http://127.0.0.1:8077/xrpc/anything")))
    (is (nil? (kb/assert-kotoba "http://localhost:8077/xrpc/anything")))
    (is (nil? (kb/assert-kotoba "http://192.168.1.70:8077/xrpc/anything")))))

(deftest test-assert-kotoba-blocks-external
  (testing "assert-kotoba throws on external hosts"
    (is (thrown?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         (kb/assert-kotoba "http://evil.example.com/xrpc/transact")))
    (is (thrown?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         (kb/assert-kotoba "https://127.0.0.1:8077/xrpc/transact")))   ;; https not allowed
    (is (thrown?
         #?(:clj clojure.lang.ExceptionInfo :default js/Error)
         (kb/assert-kotoba "http://127.0.0.1:9090/xrpc/transact")))))  ;; wrong port

;; ─── tx->edn-vec ──────────────────────────────────────────────────────────────

(deftest test-tx->edn-vec-shape
  (testing "tx->edn-vec returns an EDN vector string"
    (let [ds [(kt/add "e1" ":organism/id" "s1")
              (kt/add "e1" ":organism/label" "Test")]
          tx (kt/make-tx ds 42 2606161640 "")
          s  (kb/tx->edn-vec tx)]
      (is (str/starts-with? s "["))
      (is (str/ends-with? s "]"))
      ;; provenance attrs present
      (is (str/includes? s ":aburi.tx/id"))
      (is (str/includes? s ":aburi.tx/local-cid"))
      (is (str/includes? s ":aburi.tx/local-prev"))
      (is (str/includes? s ":aburi.tx/as-of"))
      ;; datom content present
      (is (str/includes? s ":organism/id"))
      (is (str/includes? s ":organism/label")))))

;; ─── bridge-state cursor ──────────────────────────────────────────────────────

(deftest test-bridge-state-initial
  (testing "bridge-state is empty on a fresh log with no bridge txs"
    #?(:clj
       (let [log (seed-log (make-test-log) 2)
             st  (kb/bridge-state (kt/read-log log))]
         (is (= 0  (:pushed-to st)))
         (is (= "" (:parent-commit st)))))))

(deftest test-bridge-state-replay
  (testing "bridge-state replays the cursor from pushed checkpoint"
    ;; Manually inject a bridge checkpoint tx into a fresh log and check replay
    (let [fake-txs [{":tx/id"     1
                     ":tx/as-of"  2606161631
                     ":tx/prev"   ""
                     ":tx/cid"    "bfake1"
                     ":tx/count"  1
                     ":tx/datoms" [[":db/add" "e1" ":organism/id" "s1"]]}
                    {":tx/id"     "bridge-2"
                     ":tx/as-of"  2606161632
                     ":tx/prev"   "bfake1"
                     ":tx/cid"    "bfake2"
                     ":tx/count"  2
                     ":tx/datoms" [[":db/add" "bridge-e" ":aburi-bridge/pushed-to-tx" 1]
                                   [":db/add" "bridge-e" ":aburi-bridge/parent-commit" "bremote42"]]}]
          st (kb/bridge-state fake-txs)]
      (is (= 1         (:pushed-to st)))
      (is (= "bremote42" (:parent-commit st))))))

;; ─── pending-txs ──────────────────────────────────────────────────────────────

(deftest test-pending-txs-all-new
  (testing "all data txs are pending when cursor is at 0"
    (let [fake-txs [{":tx/id" 1 ":tx/datoms" [[":db/add" "e" ":organism/id" "x"]]
                     ":tx/cid" "b1" ":tx/prev" "" ":tx/as-of" 1 ":tx/count" 1}
                    {":tx/id" 2 ":tx/datoms" [[":db/add" "e2" ":organism/id" "y"]]
                     ":tx/cid" "b2" ":tx/prev" "b1" ":tx/as-of" 2 ":tx/count" 1}]
          pending (kb/pending-txs fake-txs)]
      (is (= 2 (count pending))))))

(deftest test-pending-txs-respects-cursor
  (testing "pending-txs skips already-pushed txs"
    (let [fake-txs [{":tx/id" 1 ":tx/datoms" [[":db/add" "e" ":organism/id" "x"]]
                     ":tx/cid" "b1" ":tx/prev" "" ":tx/as-of" 1 ":tx/count" 1}
                    {":tx/id" 2 ":tx/datoms" [[":db/add" "e2" ":organism/id" "y"]]
                     ":tx/cid" "b2" ":tx/prev" "b1" ":tx/as-of" 2 ":tx/count" 1}
                    ;; bridge checkpoint: pushed-to = 1
                    {":tx/id" "bridge-3"
                     ":tx/datoms" [[":db/add" "br" ":aburi-bridge/pushed-to-tx" 1]
                                   [":db/add" "br" ":aburi-bridge/parent-commit" "bremote"]]
                     ":tx/cid" "b3" ":tx/prev" "b2" ":tx/as-of" 3 ":tx/count" 2}]
          pending (kb/pending-txs fake-txs)]
      (is (= 1 (count pending)))
      (is (= 2 (get (first pending) ":tx/id"))))))

;; ─── dry-run mode ──────────────────────────────────────────────────────────────

(deftest test-push-dry-run
  (testing "push returns dry-run result when ABURI_KOTOBA_LIVE is not set"
    #?(:clj
       (let [log (seed-log (make-test-log) 3)
             res (kb/push log {:live false})]
         (is (= "dry-run" (:mode res)))
         (is (= 3 (:pending res)))
         ;; bodies are formed: each has :graph and :tx_edn
         (is (every? #(and (contains? % :graph) (contains? % :tx_edn)) (:bodies res)))
         ;; every tx_edn starts with "["
         (is (every? #(str/starts-with? (:tx_edn %) "[") (:bodies res)))))))

;; ─── injected transport / live mode ──────────────────────────────────────────

(deftest test-push-live-with-fake-transport
  (testing "push live with fake transport: request shaping + exactly-once cursor"
    #?(:clj
       (let [calls  (atom [])
             transport (fn [_url body]
                         (swap! calls conj body)
                         {:status "ok"
                          :tx_cid (str "bremote" (count @calls))
                          :commit_cid (str "bcommit" (count @calls))
                          :datom_count 2})
             log    (seed-log (make-test-log) 2)
             res    (kb/push log {:live true :transport transport})]
         ;; 2 txs pushed
         (is (= "live" (:mode res)))
         (is (= 2 (:pushed res)))
         ;; transport called twice
         (is (= 2 (count @calls)))
         ;; each call body has :graph and :tx_edn
         (is (every? #(contains? % :graph) @calls))
         (is (every? #(str/starts-with? (:tx_edn %) "[") @calls))
         ;; 2nd call has expected_parent (from 1st commit_cid)
         (is (contains? (second @calls) :expected_parent))
         (is (str/starts-with? (str (:expected_parent (second @calls))) "bcommit"))
         ;; datoms-confirmed accumulated
         (is (= 4 (:datoms-confirmed res)))
         ;; a bridge checkpoint was appended (exactly-once cursor)
         (let [txs (kt/read-log log)]
           (is (kb/bridge-state txs))
           (let [st (kb/bridge-state txs)]
             (is (= 2 (:pushed-to st)))
             (is (str/starts-with? (:parent-commit st) "bcommit"))))))))

(deftest test-push-live-idempotent
  (testing "second push with same log is a no-op (exactly-once)"
    #?(:clj
       (let [calls     (atom [])
             transport (fn [_url _body]
                         (swap! calls conj true)
                         {:status "ok" :tx_cid "brc1" :commit_cid "bcc1" :datom_count 1})
             log       (seed-log (make-test-log) 1)]
         ;; first push
         (kb/push log {:live true :transport transport})
         (let [calls-after-1 (count @calls)]
           ;; second push — nothing new
           (let [res2 (kb/push log {:live true :transport transport})]
             (is (= "live" (:mode res2)))
             (is (= 0 (:pushed res2)))
             ;; transport NOT called again
             (is (= calls-after-1 (count @calls)))))))))

;; ─── request shaping — graph + tx_edn in body ─────────────────────────────────

(deftest test-request-body-shape
  (testing "push body has :graph and :tx_edn keys"
    #?(:clj
       (let [calls     (atom [])
             transport (fn [_url body]
                         (swap! calls conj body)
                         {:status "ok" :tx_cid "b1" :commit_cid "bc1" :datom_count 1})
             log       (seed-log (make-test-log) 1)
             _         (kb/push log {:live true :graph "aburi" :transport transport})]
         (let [body (first @calls)]
           (is (= "aburi" (:graph body)))
           (is (string? (:tx_edn body)))
           (is (str/starts-with? (:tx_edn body) "[")))))))

;; ─── entry point ──────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& _args]
     (let [result (run-tests 'aburi.tests.test-kotoba-bridge)]
       (System/exit (if (and (zero? (:fail result)) (zero? (:error result))) 0 1)))))

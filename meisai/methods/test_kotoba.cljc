(ns meisai.methods.test-kotoba
  "Clojure tests for meisai.methods.kotoba, asserting exact cross-language oracle values."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [meisai.methods.kotoba :as k])
  #?(:clj (:import [java.io File])))

(defn- temp-log []
  #?(:clj
     (let [f (File/createTempFile "kotoba-test-" ".edn")]
       (.delete f)
       (.getAbsolutePath f))
     :cljs nil))

(deftest add-test
  (is (= (k/add "e" "a" "v") [":db/add" "e" "a" "v"])))

(deftest tx-cid-test
  (let [d1 [(k/add "meisai-stmt:sumitclub:2026-05" ":meisai.stmt/total-jpy" 12345)
            (k/add "meisai-stmt:sumitclub:2026-05" ":meisai.stmt/source" ":sumitclub")]
        d2 [(k/add "meisai-row:abc" ":meisai.row/amount-jpy" 980)]
        cid1 (k/tx-cid d1 "")]
    (is (= (k/tx-cid d1 "")
           "ba0f8ed84b210641fc08a574c02ca1d3a6f47cee30b748edd496671f4ec6293c6"))
    (is (= (k/tx-cid d2 cid1)
           "b3c65e2acceb5a61840f5d9f1e85bd9501a90d4ebd21f1ecb16e328e112c46817"))))

(deftest make-tx-test
  (let [d1 [(k/add "meisai-stmt:sumitclub:2026-05" ":meisai.stmt/total-jpy" 12345)
            (k/add "meisai-stmt:sumitclub:2026-05" ":meisai.stmt/source" ":sumitclub")]
        tx (k/make-tx d1 1 100 "")]
    (is (= (get tx ":tx/cid")
           "ba0f8ed84b210641fc08a574c02ca1d3a6f47cee30b748edd496671f4ec6293c6"))
    (is (= (get tx ":tx/count") 2))))

(deftest parse-edn-test
  (is (= (k/parse-edn "[:a 1 true nil \"x\"]")
         [":a" 1 true nil "x"])))

(deftest roundtrip-test
  (let [d1 [(k/add "meisai-stmt:sumitclub:2026-05" ":meisai.stmt/total-jpy" 12345)
            (k/add "meisai-stmt:sumitclub:2026-05" ":meisai.stmt/source" ":sumitclub")]
        tx (k/make-tx d1 1 100 "")
        edn (k/tx->edn tx)
        parsed (k/parse-edn edn)]
    (is (= (get parsed ":tx/cid")
           "ba0f8ed84b210641fc08a574c02ca1d3a6f47cee30b748edd496671f4ec6293c6"))
    (is (= (first (get parsed ":tx/datoms"))
           [":db/add" "meisai-stmt:sumitclub:2026-05" ":meisai.stmt/total-jpy" 12345]))))

(deftest verify-chain-test
  #?(:clj
     (let [path (temp-log)
           d1 [(k/add "meisai-stmt:sumitclub:2026-05" ":meisai.stmt/total-jpy" 12345)
               (k/add "meisai-stmt:sumitclub:2026-05" ":meisai.stmt/source" ":sumitclub")]
           d2 [(k/add "meisai-row:abc" ":meisai.row/amount-jpy" 980)]
           tx1 (k/make-tx d1 1 100 "")
           tx2 (k/make-tx d2 2 200 (get tx1 ":tx/cid"))]
       (k/append-tx tx1 path)
       (k/append-tx tx2 path)
       (let [result (k/verify-chain path)]
         (is (:ok result))
         (is (= (:length result) 2)))
       (.delete (File. path)))
     :cljs
     (is true)))

(ns okaimono.kotoba.test-ingest-internal
  "Tests for the okaimono Ring 1 internal-catalog ingester (ADR-2606012100 §R1 port). Unit-tests
  the pure parsers — split-top-maps (multi-map, comment-strip, nested-map depth) and kv* (quoted
  string unquote / keyword / number / nil) — and pins collect over the real maker products.edn to
  the Python golden (14 internal products as of 2026-07-07, 0 problems; was 11 at the
  original Python golden — yakushi's catalog grew from 2 to 5 OTC/disinfectant SKUs since)."
  (:require [clojure.test :refer [deftest is]]
            [okaimono.kotoba.ingest-internal :as g]))

(deftest test-split-top-maps
  (is (= ["{:a 1}" "{:b 2}" "{:c 3}"] (g/split-top-maps "[ {:a 1} {:b 2} {:c 3} ]")))
  ;; nested maps stay within their top-level map (depth tracking)
  (is (= ["{:a {:b 1}}" "{:c 2}"] (g/split-top-maps "[{:a {:b 1}} {:c 2}]")))
  ;; ;-comments are stripped before splitting
  (is (= ["{:a 1}"] (g/split-top-maps "[\n  ;; a comment\n  {:a 1}  ;; trailing\n]")))
  ;; no outer vector → empty
  (is (= [] (g/split-top-maps "no brackets here"))))

(deftest test-kv
  (is (= "int.x.y" (g/kv* "{:product/id \"int.x.y\" :product/ring :internal}" ":product/id")))
  (is (= ":internal" (g/kv* "{:product/ring :internal}" ":product/ring")))   ; keyword token as-is
  (is (= "-1.5" (g/kv* "{:price -1.5}" ":price")))                            ; number token as-is
  (is (nil? (g/kv* "{:a 1}" ":product/id"))))                                 ; absent → nil

(deftest test-collect-golden
  (let [[products problems] (g/collect)]
    (is (= 14 (count products)))                ; was 11 at the original Python golden
    (is (= [] problems))                         ; all entries pass the Ring 1 gates
    (let [pids (set (map second products))]
      (is (contains? pids "int.makura.foam-pillow"))
      (is (contains? pids "int.hikari.storage-5kwh"))
      (is (contains? pids "int.yakushi.acetaminophen-tab")))
    ;; every collected product is [actor pid ent] with the maker-actor matching its dir
    (is (every? (fn [[actor _ ent]] (re-find (re-pattern (str ":product/maker-actor\\s+\"" actor "\"")) ent))
                products))
    ;; the makers actually represented
    (is (= #{"makura" "mitsuho" "yakushi" "tsutae" "futawa" "hikari"}
           (set (map first products))))))

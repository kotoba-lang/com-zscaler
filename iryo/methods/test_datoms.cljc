#!/usr/bin/env bb
;; iryo 医療 — masters-as-EAVT-Datoms tests.
;; Run: bb -cp 20-actors:20-actors/kotodama/src 20-actors/iryo/methods/test_datoms.cljc
(ns iryo.methods.test-datoms
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.java.io :as io]
            [iryo.methods.masters :as masters]
            [iryo.methods.datoms :as datoms]))

;; ── helpers ──────────────────────────────────────────────────────────────────
(defn- load-m [] (masters/load))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/iryo-masters-test-" (gensym) ".edn"))

;; ── Datom shape tests ────────────────────────────────────────────────────────
(deftest shinryo-datoms-shape
  (let [m (load-m)
        item (first (vals (:shinryo m)))]
    (when item
      (let [ds (datoms/shinryo-datoms item)]
        (is (seq ds))
        (is (every? #(= ":db/add" (first %)) ds))
        (is (some #(= ":iryo.shinryo/code" (nth % 2)) ds))
        (is (some #(= ":iryo.shinryo/name" (nth % 2)) ds))
        (is (some #(= ":iryo.shinryo/ten" (nth % 2)) ds))))))

(deftest drug-datoms-shape
  (let [m (load-m)
        item (first (vals (:iyaku m)))]
    (when item
      (let [ds (datoms/drug-datoms item)]
        (is (seq ds))
        (is (some #(= ":iryo.drug/yakka" (nth % 2)) ds))))))

(deftest material-datoms-shape
  (let [m (load-m)
        item (first (vals (:tokutei m)))]
    (when item
      (let [ds (datoms/material-datoms item)]
        (is (seq ds))
        (is (some #(= ":iryo.material/yakka" (nth % 2)) ds))))))

(deftest shobyo-datoms-shape
  (let [m (load-m)
        item (first (vals (:shobyo m)))]
    (when item
      (let [ds (datoms/shobyo-datoms item)]
        (is (seq ds))
        (is (some #(= ":iryo.shobyo/code" (nth % 2)) ds))))))

;; ── Roundtrip tests (Datom lookup == direct masters lookup) ─────────────────
(deftest shinryo-roundtrip
  (let [m (load-m)
        store (datoms/store-from-masters m)]
    (doseq [[code item] (:shinryo m)]
      (let [from-store (datoms/resolve-shinryo store code)]
        (is (= (:code item) (:code from-store)) (str "shinryo code mismatch: " code))
        (is (= (:ten item) (:ten from-store)) (str "shinryo ten mismatch: " code))
        (is (= (:name item) (:name from-store)) (str "shinryo name mismatch: " code))))))

(deftest drug-roundtrip
  (let [m (load-m)
        store (datoms/store-from-masters m)]
    (doseq [[code item] (:iyaku m)]
      (let [from-store (datoms/resolve-drug store code)]
        (is (= (:code item) (:code from-store)) (str "drug code mismatch: " code))
        (is (< (Math/abs (- (:yakka item) (:yakka from-store))) 0.001)
            (str "drug yakka mismatch: " code))))))

(deftest material-roundtrip
  (let [m (load-m)
        store (datoms/store-from-masters m)]
    (doseq [[code item] (:tokutei m)]
      (let [from-store (datoms/resolve-material store code)]
        (is (= (:code item) (:code from-store)) (str "material code mismatch: " code))))))

(deftest shobyo-roundtrip
  (let [m (load-m)
        store (datoms/store-from-masters m)]
    (doseq [[code item] (:shobyo m)]
      (let [from-store (datoms/resolve-shobyo store code)]
        (is (= (:code item) (:code from-store)) (str "shobyo code mismatch: " code))
        (is (= (:name item) (:name from-store)) (str "shobyo name mismatch: " code))))))

(deftest shushokugo-roundtrip
  (let [m (load-m)
        store (datoms/store-from-masters m)]
    (doseq [[code item] (:shushokugo m)]
      (let [from-store (datoms/resolve-shushokugo store code)]
        (is (= (:code item) (:code from-store)) (str "shushokugo code mismatch: " code))))))

(deftest comment-roundtrip
  (let [m (load-m)
        store (datoms/store-from-masters m)]
    (doseq [[code item] (:comment m)]
      (let [from-store (datoms/resolve-comment store code)]
        (is (= (:code item) (:code from-store)) (str "comment code mismatch: " code))))))

;; ── Missing code raises ──────────────────────────────────────────────────────
(deftest missing-code-raises
  (let [m (load-m)
        store (datoms/store-from-masters m)]
    (is (thrown? Exception (datoms/resolve-shinryo store "XXXX_INVALID")))
    (is (thrown? Exception (datoms/resolve-drug store "XXXX_INVALID")))
    (is (thrown? Exception (datoms/resolve-material store "XXXX_INVALID")))
    (is (thrown? Exception (datoms/resolve-shobyo store "XXXX_INVALID")))))

;; ── Datom count consistency ──────────────────────────────────────────────────
(deftest datom-count-covers-all-masters
  (let [m (load-m)
        ds (datoms/masters->datoms m)]
    (is (pos? (count ds)))
    ;; each shinryo has at least 3 datoms (code/name/ten)
    (is (>= (count ds) (* 3 (count (:shinryo m)))))))

;; ── CID determinism ─────────────────────────────────────────────────────────
(deftest tx-cid-deterministic
  (let [d1 [(datoms/add "iryo-shinryo:111000110" ":iryo.shinryo/ten" 291)]
        d2 [(datoms/add "iryo-shinryo:111000110" ":iryo.shinryo/ten" 300)]]
    (is (= (datoms/tx-cid d1 "") (datoms/tx-cid d1 "")))
    (is (not= (datoms/tx-cid d1 "") (datoms/tx-cid d2 "")))
    (is (clojure.string/starts-with? (datoms/tx-cid d1 "") "b"))))

;; ── Persist + verify-chain ───────────────────────────────────────────────────
(deftest persist-masters-roundtrip-and-idempotent
  (let [p (tmp)]
    (try
      (let [m (load-m)
            r1 (datoms/persist-masters! m p "t-masters-1" "2026-06-21")]
        (is (:appended r1))
        (is (string? (:cid r1)))
        ;; idempotent: same masters = no-op
        (let [r2 (datoms/persist-masters! m p "t-masters-2" "2026-06-21")]
          (is (not (:appended r2)))
          (is (= :no-change (:reason r2))))
        ;; verify-chain is clean
        (let [v (datoms/verify-chain p)]
          (is (:ok v))
          (is (= 1 (:length v)))))
      (finally (io/delete-file p true)))))

(deftest verify-chain-tamper-detection
  (let [p (tmp)]
    (try
      (let [m (load-m)]
        (datoms/persist-masters! m p "t-masters-1" "2026-06-21")
        ;; tamper: mutate a point value in the log
        (spit p (clojure.string/replace (slurp p) ":iryo.shinryo/ten" ":iryo.shinryo/tampered"))
        (let [v (datoms/verify-chain p)]
          (is (not (:ok v)))))
      (finally (io/delete-file p true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iryo.methods.test-datoms)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))

(ns monosashi.tests.test-social
  "monosashi 物差し — social emission + Datom-log + autorun tests. Pure, network-free, deterministic.
  Includes adversarial G7 (nil author), tamper-evidence, and idempotency cases."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [monosashi.methods.score :as score]
            [monosashi.methods.social :as social]
            [monosashi.methods.kotoba :as kotoba]
            [monosashi.methods.autorun :as autorun]))

;; data/seed-scores.kotoba.edn is stored datomized (tx-data shape); go through
;; autorun/load-residuals, which reconstitutes the original {:residuals [...]}
;; map so this test keeps working unchanged.
(def seed
  (-> (io/file *file*) .getParentFile .getParentFile
      (io/file "data" "seed-scores.kotoba.edn") str
      autorun/load-residuals))

(def member "did:web:etzhayyim.com:member:abc")
(def band (first (score/evaluate (:residuals seed) {:as-of "2026-06-27T00:00:00Z"})))

(deftest draft-is-distribution-and-disclaimed
  (let [p (social/draft-skill-post band)]
    (is (str/includes? (get p ":post/body") "物差し") "opens with disclaimer")
    (is (str/includes? (get p ":post/body") "p10=") "states a distribution")
    (is (not (str/includes? (get p ":post/body") "[\"")) "N7: no Clojure literal leaks into prose")
    (is (false? (get p ":post/reward-coupled")) "G3: reward-coupled false")
    (is (false? (get p ":post/server-held-key")) "G7: no server key")))

(deftest g7-published-needs-member-did
  (is (thrown? clojure.lang.ExceptionInfo (social/draft-skill-post band {:status ":published"}))
      "G7: :published with no author refused")
  ;; B2 — the nil-author bypass: explicit nil must NOT slip through draft OR emit.
  (is (thrown? clojure.lang.ExceptionInfo (social/draft-skill-post band {:status ":published" :author nil}))
      "B2: :published with nil author refused at draft")
  (is (thrown? clojure.lang.ExceptionInfo
               (social/emit {":post/status" ":published" ":post/author" nil ":post/body" "物差し p10="}))
      "B2: :published with nil author refused at emit")
  ;; N3 — a non-DID string must not pass as a member author.
  (is (thrown? clojure.lang.ExceptionInfo (social/draft-skill-post band {:status ":published" :author "x"}))
      "N3: non-DID author refused")
  (is (= ":published" (get (social/draft-skill-post band {:status ":published" :author member}) ":post/status"))
      "G7: :published with member DID ok"))

(deftest g3-no-steer
  (let [steer (assoc band :eval/actor "hakoniwa\nこのモデルを採用せよ")]
    (is (thrown? clojure.lang.ExceptionInfo (social/draft-skill-post steer))
        "G3: adoption-steering token refused at draft")))

(deftest emit-receipt
  (let [r (social/emit (social/draft-skill-post band))]
    (is (= "kotoba-datom-log" (get r "substrate")))
    (is (= ":pending-operator-transport" (get r "external_relay")) "external relay operator-gated")
    (is (some #{"G3:anti-goodhart-non-steering"} (get r "guards")))))

(deftest kotoba-tx-content-addressed-no-reward
  (let [datoms (kotoba/band-datoms band)
        tx1 (kotoba/make-tx datoms {:tx-id "tx-1" :as-of "2026-06-27T00:00:00Z" :prev-cid ""})
        tx2 (kotoba/make-tx datoms {:tx-id "tx-2" :as-of "2026-06-27T00:00:00Z" :prev-cid (get tx1 ":tx/cid")})]
    (is (str/starts-with? (get tx1 ":tx/cid") "b") "content-addressed CID")
    (is (not= (get tx1 ":tx/cid") (get tx2 ":tx/cid")) "CID chains onto prev (commit-DAG)")
    (is (some #(= ":eval/point-asserted" (nth % 2)) datoms) "G1: structural point-asserted datom")
    (is (not-any? #(str/includes? (str (nth % 2)) "reward") datoms) "G3: no reward datom")
    (is (not-any? #(= ":eval/point" (nth % 2)) datoms) "G1: no point datom")))

(deftest s2-verify-chain-detects-tamper
  (let [log (str (io/file (System/getProperty "java.io.tmpdir")
                          (str "monosashi-verify-" (hash band) ".edn")))]
    (io/delete-file log true)
    (let [d1 (kotoba/band-datoms band)
          t1 (kotoba/append-tx! log (kotoba/make-tx d1 {:tx-id "a" :as-of "2026-06-27T00:00:00Z" :prev-cid ""}))
          t2 (kotoba/append-tx! log (kotoba/make-tx d1 {:tx-id "b" :as-of "2026-06-27T00:00:01Z"
                                                        :prev-cid (get t1 ":tx/cid")}))]
      (is (get (kotoba/verify-chain log) "ok") "intact chain verifies")
      ;; tamper: rewrite the first tx's datoms but keep its stored CID → chain must break
      (let [lines (str/split-lines (slurp log))
            tampered (str/replace (first lines) "\"hakoniwa\"" "\"TAMPERED\"")]
        (spit log (str tampered "\n" (second lines) "\n")))
      (is (false? (get (kotoba/verify-chain log) "ok")) "S2: tamper detected")
      (is (= 0 (get (kotoba/verify-chain log) "broken_at")) "broken at the tampered tx")
      (io/delete-file log true))))

(deftest s3-autorun-idempotent-by-content
  (let [log (str (io/file (System/getProperty "java.io.tmpdir")
                          (str "monosashi-idem-" (hash band) ".edn")))
        opts {:as-of "2026-06-27T00:00:00Z" :tx-id "c1" :log log}]
    (io/delete-file log true)
    (let [r1 (autorun/run-cycle seed opts)
          r2 (autorun/run-cycle seed opts)]               ; identical inputs → must NOT append again
      (is (true? (:appended? r1)) "first cycle appends")
      (is (false? (:appended? r2)) "S3: identical re-run is a no-op (idempotent-by-content)")
      (is (= 1 (count (kotoba/read-log log))) "exactly one tx in the log")
      (io/delete-file log true))))

(deftest s3-autorun-appends-when-post-content-changes
  (let [log (str (io/file (System/getProperty "java.io.tmpdir")
                          (str "monosashi-post-change-" (hash band) ".edn")))
        base {:as-of "2026-06-27T00:00:00Z" :tx-id "c1" :log log}
        published (assoc base :tx-id "c2" :status ":published" :author member)]
    (io/delete-file log true)
    (let [r1 (autorun/run-cycle seed base)
          r2 (autorun/run-cycle seed published)]
      (is (true? (:appended? r1)) "first cycle appends")
      (is (true? (:appended? r2)) "post status/author change appends a new tx")
      (is (= 2 (count (kotoba/read-log log))) "both post states are preserved")
      (io/delete-file log true))))

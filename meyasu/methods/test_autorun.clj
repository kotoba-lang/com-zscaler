(ns meyasu.methods.test-autorun
  "meyasu 目安 — autonomous fuse→persist heartbeat + kotoba Datom-log invariants (clojure.test).
  clj-native SSoT (ADR-2606142300 D1 — authored in Clojure, no Python twin) + ADR-2606073201.

  Guards the autonomy + persistence + gate contract: one content-addressed tx per beat to an
  append-only verifiable commit-DAG; deterministic / resume-safe (same cycles → same CIDs);
  tamper detected; G2 a point-asserted/speculative forecast is REFUSED at fuse (never persisted);
  G1 no trade/speculation attr; append-only :db/add; a frozen golden head-CID regression guard."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [meyasu.methods.autorun :as autorun]
            [meyasu.methods.kotoba :as k]))

(def ^:private seed-path (str (io/file "20-actors/meyasu/kotoba/seed.json")))

(defn- tmp [suffix]
  (let [f (java.io.File/createTempFile "meyasu-test" suffix)] (.delete f) (str f)))

(defn- write-seed [items]
  (let [p (tmp ".seed.json")] (spit p (json/generate-string {:items items})) p))

(deftest heartbeat-persists
  (let [log (tmp ".edn")]
    (try
      (let [res (autorun/run-autonomous 3 seed-path log)]
        (is (= 3 (:log-length res)) "one tx per beat")
        (is (every? #(pos? (:cards %)) (:beats res)) "every beat fuses + persists cards")
        (is (:ok (:chain res)) "commit-DAG verifies")
        (is (str/starts-with? (:head-cid res) "b") "head CID is content-addressed"))
      (finally (.delete (io/file log))))))

(deftest deterministic-resume-safe
  (let [a (tmp ".edn") b (tmp ".edn")]
    (try
      (is (= (map :cid (:beats (autorun/run-autonomous 3 seed-path a)))
             (map :cid (:beats (autorun/run-autonomous 3 seed-path b))))
          "same cycles → same CIDs")
      (finally (.delete (io/file a)) (.delete (io/file b))))))

(deftest append-only-and-tamper
  (let [log (tmp ".edn")]
    (try
      (autorun/run-cycle 1 seed-path log)
      (autorun/run-cycle 2 seed-path log)
      (is (= 2 (count (k/read-log log))) "two beats append")
      (is (= (get (second (k/read-log log)) ":tx/prev") (get (first (k/read-log log)) ":tx/cid"))
          "tx 2 links tx 1 (commit-DAG)")
      (spit log (str/replace (slurp log) "0.2188" "9.9"))
      (is (false? (:ok (k/verify-chain log))) "tamper detected")
      (finally (.delete (io/file log))))))

(deftest g2-point-asserted-forecast-refused
  ;; A point-asserted forecast must be REFUSED at fuse — its card is never persisted (G2).
  (let [seed (write-seed [{:productId "p_clean"
                           :kakaku {:spread 100 :spreadFraction 0.1 :notable true
                                    :cheapestMerchant "a" :supplyDemandIndex 0.1 :reading "balanced"}
                           :mitooshi {:mean 0.4 :sd 0.2 :use ":resilience" :pointAsserted false}}
                          {:productId "p_speculative"
                           :kakaku {:spread 200 :spreadFraction 0.2 :notable true
                                    :cheapestMerchant "b" :supplyDemandIndex 0.0 :reading "balanced"}
                           :mitooshi {:mean 0.9 :sd 0.1 :use ":resilience" :pointAsserted true}}])
        log (tmp ".edn")]
    (try
      (let [r (autorun/run-cycle 1 seed log)
            attrs (mapcat (fn [tx] (map #(nth % 1) (get tx ":tx/datoms"))) (k/read-log log))
            products (set (keep #(when (= ":meyasu.card/product" (nth % 2)) (nth % 3))
                                (get (first (k/read-log log)) ":tx/datoms")))]
        (is (= 1 (:cards r)) "only the non-speculative card is fused + persisted")
        (is (= 1 (:refused r)) "the point-asserted forecast is refused (G2)")
        (is (not (some #(str/includes? % "p_speculative") attrs))
            "the refused product never enters the Datom log")
        (is (contains? products "p_clean") "the clean card is persisted"))
      (finally (.delete (io/file seed)) (.delete (io/file log))))))

(deftest g1-no-trade-append-only
  (let [log (tmp ".edn")]
    (try
      (autorun/run-cycle 1 seed-path log)
      (let [tx (first (k/read-log log))
            attrs (set (map #(str (nth % 2)) (get tx ":tx/datoms")))
            ops (set (map first (get tx ":tx/datoms")))]
        (doseq [forbidden [":meyasu.card/trade" ":trade" ":speculation" ":meyasu.card/price-target"
                           ":meyasu.card/buy-sell"]]
          (is (not (contains? attrs forbidden)) (str "no trade/speculation attr `" forbidden "` (G1)")))
        (is (= #{":db/add"} ops) "every datom is append-only :db/add"))
      (finally (.delete (io/file log))))))

(deftest cid-golden-stable
  ;; Frozen golden head CID over the committed seed (3 cycles) — byte-stability regression guard.
  (let [log (tmp ".edn")]
    (try
      (autorun/run-autonomous 3 seed-path log)
      (is (= "b2077da706d74e2c7bae3ab3af414ca030e4e78ce55cfd024482dcc80c7d0f110"
             (k/head-cid log)) "head CID stays byte-stable (frozen golden value)")
      (finally (.delete (io/file log))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [r (run-tests 'meyasu.methods.test-autorun)]
    (when (pos? (+ (:fail r) (:error r))) (System/exit 1))))

(ns hakoniwa.tests.test-runtime
  "test_runtime.py — hakoniwa 箱庭 runtime tests: social emission + Murakumo fallback + autonomous
  loop. 1:1 Clojure port of tests/test_runtime.py (ADR-2606111500 R1). Network-free, deterministic."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [hakoniwa.methods.world :as w]
            [hakoniwa.methods.simulate :as s]
            [hakoniwa.methods.distribution :as d]
            [hakoniwa.methods.murakumo :as m]
            [hakoniwa.methods.social :as soc]
            [hakoniwa.methods.autorun :as autorun]
            [hakoniwa.methods.kotoba :as k]))

(def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def ^:private seed-file (io/file actor-dir "data" "seed-scenario.kotoba.edn"))

(defn- dist-fixture []
  (let [{:keys [nodes edges]} (w/load-file* seed-file)
        [results _] (s/ensemble nodes edges {:steps 12 :replicas 64 :seed 7})]
    (d/distribution results)))

(defn- tmp-log [nm]
  (str (System/getProperty "java.io.tmpdir") "/hakoniwa-test-" nm "-" (System/nanoTime) ".edn"))

(deftest test-post-guards-clean-pass
  (let [p (soc/draft-distribution-post "町の洪水避難訓練の自主採用" (dist-fixture)
                                       {:narration "架空ペルソナによるシナリオ探索です。"})]
    (is (true? (get p ":post/distribution-only")))
    (is (true? (get p ":post/non-steering")))
    (is (false? (get p ":post/server-held-key")))
    (is (= ":dry-run" (get p ":post/status")))
    (is (= "kotoba-datom-log" (get (soc/emit p) "substrate")))))

(deftest test-g2-no-point-refused
  (let [d (dist-fixture)]
    (doseq [bad ["必ず採用される" "確実に普及する未来" "we predict that adoption is guaranteed"]]
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (soc/draft-distribution-post "s" d {:narration bad}))))))

(deftest test-g3-no-steer-refused
  (let [d (dist-fixture)]
    (doseq [bad ["今すぐ投票しよう" "あなたは支持せよ" "you should sign up now" "ボイコットしよう"]]
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (soc/draft-distribution-post "s" d {:narration bad}))))))

(deftest test-g7-published-needs-author
  (let [d (dist-fixture)]
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (soc/draft-distribution-post "s" d {:status ":published"})))
    (let [p (soc/draft-distribution-post "s" d {:author "did:web:etzhayyim.com:member:test"
                                                :status ":published"})]
      (is (= ":published" (get p ":post/status")))
      (let [p2 (assoc p ":post/author" "")]
        (is (thrown? #?(:clj Exception :cljs js/Error) (soc/emit p2)))))))

(deftest test-murakumo-fallback-is-deterministic-and-guard-clean
  (let [d (dist-fixture)
        n1 (m/narrate "町の洪水避難訓練の自主採用" d false)
        n2 (m/narrate "町の洪水避難訓練の自主採用" d false)]
    (is (= n1 n2))
    (is (= ":template-fallback" (get n1 "via")))
    (let [p (soc/draft-distribution-post "町の洪水避難訓練の自主採用" d {:narration (get n1 "text")})]
      (is (get p ":post/body")))))

(deftest test-persona-step-fallback-clamps
  (let [r (m/persona-step 0.5 0.9 0.6 0.4 false)]
    (is (= ":kernel-fallback" (get r "via")))
    (is (<= 0.0 (get r "stance") 1.0))))

(deftest test-autonomous-loop-persists-and-verifies
  (let [log (tmp-log "persist")
        res (autorun/run-autonomous 3 {:log-path log})]
    (try
      (is (= 3 (get res "cycles")))
      (is (= 3 (get res "log_length")))
      (is (get (get res "chain") "ok"))
      (is (every? (fn [b] (> (get b "datoms") 50)) (get res "beats")))
      (doseq [b (get res "beats")]
        (is (= "kotoba-datom-log" (get (get b "emit") "substrate"))))
      (finally (io/delete-file log true)))))

(deftest test-autonomous-loop-resume-safe-identical-cids
  (let [la (tmp-log "a") lb (tmp-log "b")
        a (autorun/run-autonomous 3 {:log-path la})
        b (autorun/run-autonomous 3 {:log-path lb})]
    (try
      (is (= (mapv #(get % "cid") (get a "beats"))
             (mapv #(get % "cid") (get b "beats"))))
      (finally (io/delete-file la true) (io/delete-file lb true)))))

(deftest test-published-mode-persists
  (let [log (tmp-log "pub")
        res (autorun/run-autonomous 2 {:log-path log
                                       :author "did:web:etzhayyim.com:member:founder"
                                       :publish true})]
    (try
      (is (get (get res "chain") "ok"))
      (is (every? (fn [b] (= ":published" (get b "post_status"))) (get res "beats")))
      (finally (io/delete-file log true)))))

(deftest test-tamper-breaks-chain
  (let [log (tmp-log "tamper")]
    (autorun/run-autonomous 2 {:log-path log})
    (try
      (let [lines (str/split-lines (slurp log))
            tampered (mapv (fn [ln]
                             (if (str/starts-with? ln "{:tx/id 1 ")
                               (str/replace ln ":forecast/point-asserted false"
                                            ":forecast/point-asserted true")
                               ln))
                           lines)]
        (spit log (str (str/join "\n" tampered) "\n"))
        (is (false? (get (k/verify-chain log) "ok"))))
      (finally (io/delete-file log true)))))

#?(:clj (defn -main [& _] (run-tests 'hakoniwa.tests.test-runtime)))

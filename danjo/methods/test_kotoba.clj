;; test_kotoba.clj — danjo local Datom-log writer: byte-identical tx_cid parity with kotoba.py
;; + commit-DAG round-trip + tamper-detection. Run: bb test_kotoba.clj   (from methods/).
(ns root.danjo.methods.test-kotoba
  (:require [clojure.string :as str])
  (:import [java.io File]))

(load-file "analyze.cljc")   ; canonical danjo analyze (ns danjo.methods.analyze); .clj dup removed
(load-file "kotoba.cljc")
(alias 'an 'danjo.methods.analyze)
(alias 'ko 'danjo.methods.kotoba)

(def checks (atom 0)) (def fails (atom 0))
(defn check [l p] (swap! checks inc) (if p (println "  ok  " l) (do (swap! fails inc) (println "  FAIL" l))))

;; NOTE: kotoba.cljc's house style (see its ns docstring) keeps EAVT op tags and every map key as
;; VERBATIM STRINGS mirroring the Python port (":db/add", ":tx/cid", "ok", "broken_at" -- note the
;; underscore, not a hyphen) -- not Clojure keywords. Access below uses string literals / (get ...
;; "field"), not :keyword access, throughout.

(let [corpus (an/load-json "../data/corpus.seed.json")
      meths  (an/load-json "v1-jp-seed.json")
      obs    (an/run-all corpus meths)
      gd     (ko/graph-datoms (get corpus "procurementRecords"))
      dd     (ko/derived-datoms obs)]

  ;; ── datom shapes ──
  (check "graph-datoms → 77 EAVT assertions" (= 77 (count gd)))
  (check "derived-datoms → 7 EAVT assertions" (= 7 (count dd)))
  (check "every datom op is :db/add (append-only, no :db/retract)"
         (every? #(= ":db/add" (first %)) (concat gd dd)))

  ;; ── G4: non-adjudication is structural ──
  (check "every observation carries :danjo.obs/non-adjudicating true (G4)"
         (= 1 (count (filter (fn [[_ _ a v]] (and (= ":danjo.obs/non-adjudicating" a) (true? v))) dd))))
  (check "no derived attr contains a verdict token (G4)"
         (not-any? (fn [[_ _ a _]]
                     (some #(str/includes? (str/lower-case (str a)) %) ko/forbidden-verdict-tokens))
                   dd))

  ;; ── byte-identical tx_cid parity with kotoba.py (the content-bearing derived tx) ──
  (check "tx-cid(derived-datoms, \"\") == kotoba.py golden"
         (= "b028f0f845c1278cdf6c4e1064d886cdfdececc3c8863393242dd0778ecda5c85"
            (ko/tx-cid dd "")))
  (check "tx-cid is content-addressed (\"b\" + 64 hex)"
         (re-matches #"b[0-9a-f]{64}" (ko/tx-cid dd "")))
  (check "different prev → different cid (commit-DAG chaining)"
         (not= (ko/tx-cid dd "") (ko/tx-cid dd "bdeadbeef")))

  ;; ── make-tx (accepts an options map: (make-tx datoms {:tx-id .. :as-of ..}), verified directly) ──
  (let [tx (ko/make-tx dd {:tx-id 1 :as-of 1000})]
    (check "make-tx :tx/count == datom count" (= 7 (get tx ":tx/count")))
    (check "make-tx :tx/cid == tx-cid golden"
           (= "b028f0f845c1278cdf6c4e1064d886cdfdececc3c8863393242dd0778ecda5c85" (get tx ":tx/cid"))))

  ;; ── append → read-back → verify-chain (round-trip on a real temp log) ──
  (let [tmp (File/createTempFile "danjo-kotoba-test" ".edn")
        path (.getAbsolutePath tmp)]
    (.delete tmp)                                  ; let append-tx write the header fresh
    (try
      (let [c1 (ko/append-tx (ko/make-tx dd {:tx-id 1 :as-of 1000}) path)
            c2 (ko/append-tx (ko/make-tx gd {:tx-id 2 :as-of 2000 :prev-cid c1}) path)
            back (ko/read-log path)]
        (check "read-log round-trips 2 transactions" (= 2 (count back)))
        (check "head-cid == last appended cid" (= c2 (ko/head-cid path)))
        (check "verify-chain :ok on intact 2-tx DAG"
               (let [v (ko/verify-chain path)] (and (get v "ok") (= 2 (get v "length")))))
        (check "read-back tx-cid stable (edn keyword/string round-trip)"
               (= c1 (get (first back) ":tx/cid")))
        ;; tamper: corrupt the header-less log by rewriting tx1's cid → chain must break
        (let [lines (str/split-lines (slurp path))
              corrupted (str/replace-first (slurp path) c1 "bdeadbeefdeadbeef")]
          (spit path corrupted)
          (check "verify-chain detects tampering (:ok false)"
                 (false? (get (ko/verify-chain path) "ok")))
          (check "tamper located at the corrupted tx index"
                 (>= (get (ko/verify-chain path) "broken_at") 0))
          (identity lines)))
      (finally (.delete (File. path))))))

(println (format "── test_kotoba: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))

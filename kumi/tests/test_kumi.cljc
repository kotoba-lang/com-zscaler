(ns kumi.tests.test-kumi
  "kumi 組 — community-entity graph + system-dynamics tests (ADR-2607101800
  family). Verifies the pipeline's mathematical + constitutional invariants on
  the synthetic, fictional seed."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [kumi.methods.kumi :as k]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed-path (io/file actor-dir "data" "seed-communities.kotoba.edn")))
#?(:clj (def seed (k/load-seed seed-path)))

;; ── shape / graph ──────────────────────────────────────────────────────────

(deftest test-graph-parses
  (let [g (k/graph seed)]
    (is (= 9 (count (:order g))) "9 distinct communities")
    (is (contains? (:communities g) "harborview-civic-renewal-local"))
    (is (= (vec (sort (keys (:communities g)))) (:order g)) "node order is deterministic (sorted)")))

(deftest test-domain-classes-present
  (testing "seed spans at least political/religious/sports/cultural/historical (ADR requirement)"
    (let [g (k/graph seed)
          classes (set (map (fn [c] (get c ":domain-class")) (vals (:communities g))))]
      (doseq [required [":political" ":religious" ":sports" ":cultural" ":historical"]]
        (is (contains? classes required) (str required " present in the seed"))))))

;; ── G1 person-excluded ───────────────────────────────────────────────────

(deftest test-G1-person-node-refused
  (testing "a person/human node is refused at parse (community-only, no individuals)"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :default :default)
                 (k/parse {:communities [{":id" "a" ":name" "A" ":domain-class" ":civic-neighborhood"}
                                          {":id" "alice" ":sev/human" true ":name" "Alice"}]
                           :ties []})))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :default :default)
                 (k/parse {:communities [{":person/name" "bob"}] :ties []})))))

;; ── G2 public-declaration-only sourcing (>=2 citations on depends-on) ─────

(deftest test-G2-under-sourced-depends-on-refused
  (testing "a :kumi/depends-on tie with <2 :sources is refused at parse"
    (let [communities [{":id" "a" ":name" "A" ":domain-class" ":civic-neighborhood"}
                        {":id" "b" ":name" "B" ":domain-class" ":labor"}]]
      (is (thrown? #?(:clj clojure.lang.ExceptionInfo :default :default)
                   (k/parse {:communities communities
                             :ties [{":kumi/from" "a" ":kumi/to" "b" ":kumi/kind" ":depends-on"
                                     ":sources" ["one-source-only"]}]})))
      (is (thrown? #?(:clj clojure.lang.ExceptionInfo :default :default)
                   (k/parse {:communities communities
                             :ties [{":kumi/from" "a" ":kumi/to" "b" ":kumi/kind" ":depends-on"}]})))
      ;; 2 sources is accepted
      (is (some? (k/parse {:communities communities
                            :ties [{":kumi/from" "a" ":kumi/to" "b" ":kumi/kind" ":depends-on"
                                    ":sources" ["s1" "s2"]}]}))))))

;; ── G4 no-causal-overclaim on :kumi/influences ────────────────────────────

(deftest test-G4-influences-requires-noncausal-marker
  (testing "an :influences tie without :co-occurrence-observed true is refused at parse"
    (let [communities [{":id" "a" ":name" "A" ":domain-class" ":cultural"}
                        {":id" "b" ":name" "B" ":domain-class" ":historical"}]]
      (is (thrown? #?(:clj clojure.lang.ExceptionInfo :default :default)
                   (k/parse {:communities communities
                             :ties [{":kumi/from" "a" ":kumi/to" "b" ":kumi/kind" ":influences"}]})))
      (is (some? (k/parse {:communities communities
                            :ties [{":kumi/from" "a" ":kumi/to" "b" ":kumi/kind" ":influences"
                                    ":co-occurrence-observed" true}]}))))))

(deftest test-G4-no-causal-field-anywhere-in-output
  (testing "no :causal-claim / :cause / :causes field is representable in beat's output"
    (let [r (k/beat seed)]
      (letfn [(walk-keys [x]
                (cond
                  (map? x) (concat (keys x) (mapcat walk-keys (vals x)))
                  (sequential? x) (mapcat walk-keys x)
                  :else []))]
        (is (not-any? (fn [k] (str/includes? (str/lower-case (str k)) "causal")) (walk-keys r)))))))

;; ── loop classification (on the real seed, verified by running the beat) ──

(deftest test-loop-classification-on-seed
  (testing "junkan-style regimes read off the seed's dyad/triad cycles"
    (let [r (k/beat seed)]
      (is (= 5 (count (:loops r))) "5 loops (3 dyad + 2 triad) detected")
      (is (= {:virtuous 2 :vicious 1 :neutral 1 :transitioning 1} (:regimes r)))
      (is (some (fn [l] (and (= (:kind l) :dyad)
                             (= (set (:nodes l)) #{"cedar-creek-youth-football" "maple-grove-neighborhood-assoc"})
                             (= (:regime l) ":virtuous")))
                (:loops r))
          "mutual declared :follows => virtuous dyad")
      (is (some (fn [l] (and (= (:kind l) :dyad)
                             (= (set (:nodes l)) #{"lantern-district-arts-society" "old-mill-heritage-society"})
                             (= (:regime l) ":neutral")))
                (:loops r))
          "mutual correlation-only :influences => neutral dyad (lower confidence, G4)")
      (is (some (fn [l] (and (= (:kind l) :triad) (= (:regime l) ":vicious")
                             (= (set (:nodes l))
                                #{"harborview-civic-renewal-local" "dockworkers-mutual-aid-447"
                                  "riverside-fellowship-congregation"})))
                (:loops r))
          "one-directional :depends-on chain, no reciprocation => vicious triad")
      (is (some (fn [l] (= (:regime l) ":transitioning")) (:loops r))
          "partially-reciprocated triad => transitioning"))))

;; ── leverage-read (G6 — computed on read only, never stored) ──────────────

(deftest test-leverage-argmax-on-seed
  (testing "leverage-read picks the highest cross-domain-versatility community"
    (let [r (k/beat seed)]
      (is (= "harborview-civic-renewal-local" (:leverage-community r))
          "harborview bridges political + labor + religious + cultural neighbors"))))

(deftest test-G6-leverage-never-stored-on-community
  (testing "no community record carries a stored power/leverage/score attribute"
    (let [g (k/graph seed)]
      (doseq [[_id c] (:communities g)]
        (is (not-any? (fn [k] (or (str/includes? (str k) "leverage")
                                  (str/includes? (str k) "power-score")
                                  (str/includes? (str k) "score")))
                      (keys c)))))
    ;; leverage-read is a pure function of the graph — calling it twice does
    ;; not mutate the graph or the community records (re-derive, don't cache).
    (let [g (k/graph seed)
          r1 (k/leverage-read g)
          r2 (k/leverage-read g)]
      (is (= r1 r2) "leverage-read is pure/deterministic")
      (is (= g (k/graph seed)) "graph itself is untouched by computing leverage"))))

;; ── G5 no actuator / append-only findings ─────────────────────────────────

(deftest test-G5-no-actuator-append-only-findings
  (testing "beat's output has no dispatch/post/execute path; findings are append-only"
    (let [r (k/beat seed)]
      (is (false? (get-in r [:findings :actuation-taken])))
      (is (= ":append-only" (get-in r [:findings :status])))
      (letfn [(walk-keys [x]
                (cond
                  (map? x) (concat (keys x) (mapcat walk-keys (vals x)))
                  (sequential? x) (mapcat walk-keys x)
                  :else []))]
        (doseq [k (walk-keys r)]
          (is (not (#{:dispatch :post :execute :mention :publish} k))
              (str "no actuator-shaped key " k " anywhere in beat output")))))))

;; ── determinism ────────────────────────────────────────────────────────────

(deftest test-beat-is-deterministic
  (testing "the beat is pure — identical input => identical readout"
    (is (= (k/beat seed) (k/beat seed)))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'kumi.tests.test-kumi)]
       (System/exit (if (pos? (+ fail error)) 1 0)))))

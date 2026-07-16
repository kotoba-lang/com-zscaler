(ns hinagata.tests.test-analyze
  "hinagata 雛形 — analyzer tests (ADR-2606111954). 1:1 Clojure port of the PURE assertions of
  tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), seed is non-trivial, no dangling 縁
    - edge-primary (N1): template groundedness is the integral of incident clause
      :cites-statute/:mandated-by × disclosed optionality weight — recomputed independently
      here and asserted equal; and NO stored per-template :bond/* / :lt/score-of-template key
    - the top-groundedness template rests on a statute-mandated clause (sanity of the lens)
    - statute pull is non-empty and every puller is a :statute node
    - G1: no advice / party / matter fields anywhere (commons, not the practice of law); every
      :cites-statute / :mandated-by edge binds a clause/template → a real :statute node
    - N3/G5: every statute carries a citation + public official URL

  NOTE on scope: the Python test_analyze additionally exercises the `datom_emit` sibling
  (test_datom_emit_ground_and_transient + test_determinism). Those two assertions depend on the
  unported `datom_emit` module, so they are intentionally DEFERRED here (the datom_emit port is
  a separate unit, mirroring the inochi/kadode/rasen precedent). All six PURE analyze assertions
  are ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [hinagata.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-legal-template-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (>= (count nodes) 50) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 90) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":lt/kind") (vals nodes)))]
      (is (clojure.set/subset? #{":template" ":clause" ":statute" ":jurisdiction" ":concept"} kinds)
          (str "missing core kinds: " kinds)))
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-edge-primary-integral
  (testing "N1: template groundedness MUST equal the independent integral of incident statute 縁."
    (let [{:keys [nodes edges]} (load-seed)
          res (analyze/analyze nodes edges)
          ;; rebuild clause→templates independently
          clause-templates (reduce
                            (fn [m e]
                              (if (= ":has-clause" (get e ":en/kind"))
                                (update m (get e ":en/to") (fnil conj []) (get e ":en/from"))
                                m))
                            {} edges)
          expect (reduce
                  (fn [m e]
                    (if (not (contains? analyze/cite-kinds (get e ":en/kind")))
                      m
                      (let [load- (double (get e ":en/binding-load"))
                            src (get e ":en/from")
                            src-kind (get-in nodes [src ":lt/kind"])]
                        (cond
                          (= src-kind ":clause")
                          (let [w (get analyze/optionality-weight
                                       (get-in nodes [src ":clause/optionality"]) 0.6)]
                            (reduce (fn [mm tmpl]
                                      (update mm tmpl (fnil + 0.0) (* load- w)))
                                    m (get clause-templates src [])))
                          (= src-kind ":template")
                          (update m src (fnil + 0.0) load-)
                          :else m))))
                  {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in res ["grounded" nid]) v)) 1e-9)
            (str nid ": " (get-in res ["grounded" nid]) " != " v)))
      ;; there is NO stored per-template score key on any node (edge-primary only)
      (doseq [n (vals nodes)]
        (is (not (some #(or (str/starts-with? % ":bond/") (= % ":lt/score-of-template"))
                       (keys n))))))))

(deftest test-top-groundedness-is-statute-mandated
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        top (key (apply max-key val (get res "grounded")))]
    (is (= ":template" (get-in nodes [top ":lt/kind"])) (str "top " top " is not a template"))
    ;; the top template must own at least one clause that is :mandated-by a statute
    (let [clauses (set (for [e edges
                             :when (and (= ":has-clause" (get e ":en/kind"))
                                        (= top (get e ":en/from")))]
                         (get e ":en/to")))
          mandated (some #(and (= ":mandated-by" (get % ":en/kind"))
                               (contains? clauses (get % ":en/from")))
                         edges)]
      (is mandated (str "top template " top " has no statute-mandated clause")))))

(deftest test-statute-pull-nonempty-and-is-statute
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)]
    (is (seq (get res "statute_pull")) "no statute pull computed")
    (doseq [nid (keys (get res "statute_pull"))]
      (is (= ":statute" (get-in nodes [nid ":lt/kind"])) (str "puller " nid " is not a statute")))))

(deftest test-g1-commons-not-advice
  (testing "G1: a COMMONS — no advice / party / matter / client fields anywhere."
    (let [{:keys [nodes edges]} (load-seed)
          forbidden #{":advice/text" ":matter/id" ":client/id" ":party/name" ":opinion"
                      ":recommendation" ":case/id" ":retainer"}]
      (doseq [n (vals nodes)]
        (is (empty? (clojure.set/intersection (set (keys n)) forbidden))
            (str "practice-of-law field leaked: " (clojure.set/intersection (set (keys n)) forbidden))))
      ;; every statute-citation edge binds a clause OR template to a real statute node
      (doseq [e edges
              :when (contains? analyze/cite-kinds (get e ":en/kind"))]
        (is (= ":statute" (get-in nodes [(get e ":en/to") ":lt/kind"]))
            (str "cites-statute target is not a statute: " (get e ":en/to")))
        (is (contains? #{":clause" ":template"} (get-in nodes [(get e ":en/from") ":lt/kind"]))
            (str "cites-statute source is not a clause/template: " (get e ":en/from")))))))

(deftest test-every-statute-has-public-source
  (testing "N3/G5: a statute is a DISCLOSED public fact — citation + official URL."
    (let [{:keys [nodes]} (load-seed)]
      (doseq [[nid n] nodes
              :when (= ":statute" (get n ":lt/kind"))]
        (is (get n ":statute/citation") (str nid " missing :statute/citation"))
        (is (str/starts-with? (str (get n ":statute/url" "")) "http")
            (str nid " missing public :statute/url"))))))

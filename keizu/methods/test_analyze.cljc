(ns keizu.methods.test-analyze
  "test_analyze.cljc — 系図 (keizu) end-to-end membrane (dry-run). ADR-2606066000.
  1:1 Clojure port of `methods/test_analyze.py` (clojure.test). Every Python assertion ported.
  Since the Clojure `run` is pure over a parsed graph (I/O at the #?(:clj) -main edge), the report
  is exercised via `report-md` / `render-json` rather than a temp-dir write — same bytes."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [keizu.methods.weave :as w]
            #?(:clj [keizu.methods.edn :as e])
            [keizu.methods.analyze :as a]))

(def seed-path "20-actors/keizu/data/seed-relation-graph.kotoba.edn")

;; the empty seed (_EMPTY_SEED) — exercises the "(none in seed)" fallbacks of report-md.
(def empty-seed
  (str "{:graph {:name \"t\" :visibility :public} "
       ":nodes [] :committees [] :rels [] :money [] :statements []}"))

#?(:clj
   (defn- run-seed []
     (let [res (a/run (e/load-edn seed-path))
           c (get res "concentration")]
       {:res res
        :report (a/report-md c (get res "posts"))
        :graph (a/graph-edn (get res "graph"))})))

;; ── test_empty_seed_report_renders_none_fallbacks ─────────────────────────────────────────────
#?(:clj
   (deftest test-empty-seed-report-renders-none-fallbacks
  (let [res (a/run (e/parse-edn empty-seed))
        c (get res "concentration")
        report (a/report-md c (get res "posts"))]
    (is (str/includes? report "(none in seed)"))        ;; empty-section fallbacks rendered
    (is (str/includes? report "0 dangling reference(s)")) ;; empty graph has no dangling refs
    (is (= [] (get res "posts")))                         ;; no committee/money posts on empty graph
    (is (= [] (get-in res ["kanae_flows" "flows"]))))))   ;; nothing to export

;; ── test_kanae_render_artifact_written ────────────────────────────────────────────────────────
#?(:clj
   (deftest test-kanae-render-artifact-written
     (let [res (a/run (e/load-edn seed-path))
           c (get res "concentration")
           ;; render-json is sort_keys=True; check the structural fields the Python test loads.
           payload-actor (str/includes? (a/render-json c) "\"actor\": \"keizu\"")
           payload-mirror (str/includes? (a/render-json c) "\"isMirror\": true")]
       (is payload-actor)
       (is payload-mirror)
       (is (seq (get-in res ["kanae_flows" "flows"])))             ;; fiscal flows exported
       (is (>= (get-in res ["kanae_flows" "skipped_count"]) 1))))) ;; political-donation skipped

;; ── test_runs_and_writes ──────────────────────────────────────────────────────────────────────
#?(:clj
   (deftest test-runs-and-writes
     (let [{:keys [res report graph]} (run-seed)]
       (is (>= (get-in res ["concentration" "node_count"]) 15))
       (is (str/includes? report "keizu"))
       (is (str/includes? graph ":rel/id")))))

;; ── test_report_is_mirror_and_non_adjudicating ────────────────────────────────────────────────
#?(:clj
   (deftest test-report-is-mirror-and-non-adjudicating
     (let [{:keys [report]} (run-seed)]
       (is (str/includes? report "NOT a target-list"))
       (is (str/includes? report "Non-adjudicating")))))

;; ── test_posts_are_dry_run ────────────────────────────────────────────────────────────────────
#?(:clj
   (deftest test-posts-are-dry-run
     (let [{:keys [res]} (run-seed)
           posts (get res "posts")]
       (is (seq posts))  ;; expected at least one dry-run post
       (doseq [p posts]
         (is (= ":dry-run" (get p ":post/status")))
         (is (false? (get p ":post/server-held-key")))))))

;; ── test_money_hhi_reported ───────────────────────────────────────────────────────────────────
#?(:clj
   (deftest test-money-hhi-reported
     (is (str/includes? (:report (run-seed)) "HHI="))))

;; ── test_both_payee_and_payer_sides_reported ──────────────────────────────────────────────────
#?(:clj
   (deftest test-both-payee-and-payer-sides-reported
     (let [{:keys [report]} (run-seed)]
       (is (and (str/includes? report "by payee") (str/includes? report "by payer"))))))

;; ── test_connector_section_reported ───────────────────────────────────────────────────────────
#?(:clj
   (deftest test-connector-section-reported
     (is (str/includes? (:report (run-seed)) "Cross-organ connector seats"))))

;; ── test_by_jurisdiction_section_reported ─────────────────────────────────────────────────────
#?(:clj
   (deftest test-by-jurisdiction-section-reported
     (is (str/includes? (:report (run-seed)) "## By jurisdiction"))))

;; ── test_statements_section_reported ──────────────────────────────────────────────────────────
#?(:clj
   (deftest test-statements-section-reported
     (let [{:keys [report]} (run-seed)]
       (is (str/includes? report "Statements (発言)"))
       (is (str/includes? report "never rated true/false")))))  ;; non-adjudicating framing

;; ── test_integrity_line_reported_clean ────────────────────────────────────────────────────────
#?(:clj
   (deftest test-integrity-line-reported-clean
     (is (str/includes? (:report (run-seed)) "referential integrity: 0 dangling reference(s)"))))

;; ── test_award_and_fund_section_is_non_adjudicating ───────────────────────────────────────────
#?(:clj
   (deftest test-award-and-fund-section-is-non-adjudicating
     (let [{:keys [report]} (run-seed)]
       (is (str/includes? report "Award-and-fund co-occurrence"))
       (is (str/includes? report "NOT an allegation")))))  ;; G2 framing on the most sensitive section

;; ── test_report_carries_no_verdict_language ───────────────────────────────────────────────────
#?(:clj
   (deftest test-report-carries-no-verdict-language
     (let [{:keys [report]} (run-seed)
           low (str/lower-case report)]
       (doseq [tok ["corruption" "bribe" "guilty" "illegal" "汚職" "賄賂"]]
         (is (not (str/includes? low tok)) (str "verdict token " tok " leaked into the report")))
       (is (seq w/VERDICT-TOKENS)))))  ;; the closed list exists and is the single source

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-analyze)))

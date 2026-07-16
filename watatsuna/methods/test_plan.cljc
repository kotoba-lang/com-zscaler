(ns watatsuna.methods.test-plan
  "Tests for the watatsuna 綿津綱 → watatsumi resilience plan (methods/plan.cljc).
  1:1 port of `methods/test_plan.py`.

  The load-bearing invariant (G2 + watatsumi N8): the plan can ONLY add resilience —
  :lay-diverse-route / :pre-stage-repair / :monitor. There is NO interdiction/cut output by
  construction. This suite proves no other plan kind can appear."
  (:require [clojure.test :refer [deftest is]]
            [clojure.set :as set]
            [clojure.string :as str]
            [watatsuna.methods.analyze :as a]
            [watatsuna.methods.plan :as plan]
            [watatsuna.methods._edn :as edn]
            [clojure.java.io :as io]))

;; _SEED = pathlib.Path(__file__).resolve().parent.parent / "data" / "seed-cable-graph.kotoba.edn"
(def seed
  (-> (io/file *file*) .getParentFile .getParentFile
      (io/file "data" "seed-cable-graph.kotoba.edn")))

(def allowed #{":lay-diverse-route" ":pre-stage-repair" ":monitor"})

(defn- plan*
  "Mirror test_plan._plan(): rows → classify → analyze → build-plan. Returns
   {:cables :stations :a :recs}."
  []
  (let [rows (edn/load-edn seed)
        {:keys [cables stations links segs faults]} (#'a/attach-orders (a/classify rows))
        an (a/analyze cables stations links segs faults)]
    {:cables cables :stations stations :a an :recs (plan/build-plan cables stations an)}))

;; ── test_plan_is_non_empty ──────────────────────────────────────────────────
(deftest plan-is-non-empty
  (let [{:keys [recs]} (plan*)]
    (is (> (count recs) 0))))

;; ── test_every_plan_kind_is_resilience_only ─────────────────────────────────
(deftest every-plan-kind-is-resilience-only
  (let [{:keys [recs]} (plan*)
        kinds (set (map #(get % ":plan/kind") recs))]
    (is (set/subset? kinds allowed)
        (str "non-resilience plan kind present: " (set/difference kinds allowed)))))

;; ── test_no_interdiction_kind_representable ──────────────────────────────────
(deftest no-interdiction-kind-representable
  (let [{:keys [recs]} (plan*)]
    (doseq [r recs]
      (let [k (get r ":plan/kind")]
        (is (not (some (fn [bad] (str/includes? k bad))
                       ["cut" "sever" "interdict" "disable" "attack"])))))))

;; ── test_lay_diverse_route_targets_redundancy_gaps ──────────────────────────
(deftest lay-diverse-route-targets-redundancy-gaps
  (let [{:keys [a recs]} (plan*)
        lay (filter #(= (get % ":plan/kind") ":lay-diverse-route") recs)
        gaps (get a "redundancy_gap")]
    (is (or (>= (count lay) (count gaps)) (= 0 (count gaps))))))

;; ── test_rendered_edn_marks_g2_invariant ────────────────────────────────────
(deftest rendered-edn-marks-g2-invariant
  (let [{:keys [recs]} (plan*)
        edn-text (plan/render-edn recs)]
    (is (str/includes? edn-text "redundancy + repair + monitor ONLY"))
    (is (or (not (str/includes? (str/lower-case edn-text) "interdiction"))
            (str/includes? edn-text "No interdiction")))))

;; ── test_rendered_md_states_watatsuna_knows_watatsumi_acts ───────────────────
(deftest rendered-md-states-watatsuna-knows-watatsumi-acts
  (let [{:keys [recs]} (plan*)
        md (plan/render-md recs)]
    (is (and (str/includes? md "watatsuna knows") (str/includes? md "watatsumi acts")))
    (is (str/includes? md "No interdiction output by construction"))))

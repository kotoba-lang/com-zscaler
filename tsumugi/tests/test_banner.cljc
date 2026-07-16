(ns tsumugi.tests.test-banner
  "tsumugi (B / 旗 hata) analyze_banner tests — one per thought-policing gate.
  1:1 port of tests/test_banner.py (ADR-2606092000).

  Verifies:
    - camps + bridges + genealogy compute from the seed
    - H1: an :inferred basis RAISES (no imputed ideology)
    - H2: a threat token (:過激) as :banner/kind RAISES
    - H3: a :ent/ideology-score node attr RAISES (no score-of-conviction)
    - H4: a :flies/who pointing at a non-institutional (private) entity RAISES
    - H6: an entity flying ≥2 banners appears as a BRIDGE (plural allowed)
    - H7: an under-sourced :flies (<2) RAISES
    - non-adjudicating: no threat token anywhere in the rendered output
    - determinism"
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [tsumugi.methods.analyze-banner :as B]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (defn- seed-path []
     (let [f (when (and *file* (not (str/blank? *file*))) (io/file *file*))
           pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
       (if (and pp (.isDirectory (io/file pp "data")))
         (io/file pp "data" "seed-banner.kotoba.edn")
         (io/file "20-actors" "tsumugi" "data" "seed-banner.kotoba.edn")))))

#?(:clj
   (defn- load-seed []
     (B/load-validate (B/read-edn (slurp (seed-path))))))

(defn- load-text
  "load() over an in-memory EDN string (mirrors expect_raise's tempfile path)."
  [edn-text]
  (B/load-validate (B/read-edn edn-text)))

(defn- raises? [edn-text]
  (try (load-text edn-text) false
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) _ true)))

(def ent-ok "{:ent/id \"e\" :ent/label \"e\" :ent/standing :institutional}")
(def ban-ok "{:banner/id \"b\" :banner/label \"b\" :banner/kind :policy-stance}")

#?(:clj
   (deftest banner-tests
     (let [{:keys [banners ents flies]} (load-seed)
           result (B/analyze banners ents flies)]

       ;; camps computed
       (is (= (count (get result "camps")) (count banners)))
       ;; genealogy links banners to streams
       (is (>= (count (get result "genealogy")) 4))

       ;; H6 — pluralism: the centrist party flies ≥2 banners → a bridge
       (let [bridge-ids (set (map #(get % "ent") (get result "bridges")))]
         (is (contains? bridge-ids "org.party.jp.c")))

       ;; H5 — etzhayyim's own banner is inbound-only (not a camp it recruits others into)
       (let [self-camp (first (filter #(= (get % "banner") "banner.etzhayyim.charter")
                                      (get result "camps")))]
         (is (= (get self-camp "member_count") 0)))

       ;; non-adjudicating — no threat token leaks into the rendered report
       (let [report (B/render-report result)]
         (is (not (some (fn [tok] (str/includes? report (str/replace tok #"^:" "")))
                        B/threat-tokens))))

       ;; gate breaches must RAISE
       (is (raises?
            (str "[" ban-ok " " ent-ok
                 " {:flies/id \"f\" :flies/who \"e\" :flies/banner \"b\" :flies/basis :inferred "
                 ":flies/weight 0.5 :flies/sources [\"a\" \"b\"] :flies/sourcing :representative}]")))
       (is (raises? "[{:banner/id \"b\" :banner/label \"b\" :banner/kind :過激}]"))
       (is (raises? "[{:ent/id \"e\" :ent/label \"e\" :ent/standing :institutional :ent/ideology-score 9}]"))
       (is (raises?
            (str "[" ban-ok
                 " {:ent/id \"e\" :ent/label \"e\" :ent/standing :private-person}"
                 " {:flies/id \"f\" :flies/who \"e\" :flies/banner \"b\" :flies/basis :self-declared "
                 ":flies/weight 0.5 :flies/sources [\"a\" \"b\"] :flies/sourcing :representative}]")))
       (is (raises?
            (str "[" ban-ok " " ent-ok
                 " {:flies/id \"f\" :flies/who \"e\" :flies/banner \"b\" :flies/basis :self-declared "
                 ":flies/weight 0.5 :flies/sources [\"only-one\"] :flies/sourcing :representative}]")))

       ;; determinism
       (let [{b2 :banners e2 :ents f2 :flies} (load-seed)
             r2 (B/analyze b2 e2 f2)]
         (is (= (B/render-report result) (B/render-report r2)))))))

#?(:clj
   (defn -main [& _]
     (run-tests 'tsumugi.tests.test-banner)))

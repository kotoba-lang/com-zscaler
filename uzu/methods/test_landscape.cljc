#!/usr/bin/env bb
;; uzu 渦 — viability-envelope tests: fitness is joint (meaning × niche).
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_landscape.cljc
(ns uzu.methods.test-landscape
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.landscape :as ls]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def meanings (mapv #(select-keys % [:id :prefs]) (ue/organisms "20-actors/uzu/kotoba/seed.edn")))
(def rows (ls/survey meanings (ls/default-niches) 3))
(defn cell [id niche]
  (->> rows (filter #(= id (:id %))) first :cells (filter #(= niche (:niche %))) first))

(deftest matrix-shape
  (is (= 3 (count rows)))
  (is (every? #(= 3 (count (:cells %))) rows) "3 niches per meaning")
  (is (= ["abundant" "mixed" "scarce"] (map :niche (:cells (first rows))))))

(deftest a-good-meaning-needs-a-good-niche
  ;; kurage thrives in abundance, starves in scarcity — fitness is not the meaning alone
  (is (true?  (:alive? (cell "kurage" "abundant"))))
  (is (false? (:alive? (cell "kurage" "scarce")))))

(deftest pathology-is-only-exposed-by-a-punishing-niche
  ;; meial's threat-seeking is FATAL only where there is hazard to walk into:
  ;; harmless in an abundant niche (no hostile steps), lethal in mixed/scarce
  (is (true?  (:alive? (cell "meial" "abundant"))) "no hazard ⇒ the pathology never triggers")
  (is (false? (:alive? (cell "meial" "mixed")))    "hostile steps present ⇒ it forages into them and dies")
  (is (false? (:alive? (cell "meial" "scarce")))))

(deftest asceticism-starves-even-in-plenty
  ;; gyoja never forages (wants nothing) ⇒ dies even in an abundant niche
  (is (false? (:alive? (cell "gyoja" "abundant"))) "a meaning that draws nothing starves in any niche"))

(deftest datoms-cover-every-cell
  (let [ds (ls/datoms rows)
        cells (set (map #(nth % 1) ds))]
    (is (= 9 (count (filter #(= ":uzu.landscape/alive" (nth % 2)) ds))) "one alive fact per (meaning,niche)")
    (is (contains? cells "uzu:landscape/kurage/abundant"))
    (is (every? #(= 4 (count %)) ds))))

(deftest report-renders-a-grid
  (let [r (ls/report rows)]
    (is (str/includes? r "viability envelope"))
    (is (str/includes? r "abundant"))
    (is (str/includes? r "kurage"))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-landscape)]
  (when (pos? (+ fail error)) (System/exit 1)))

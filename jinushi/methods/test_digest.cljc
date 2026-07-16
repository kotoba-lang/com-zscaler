(ns jinushi.methods.test-digest
  "jinushi 地主 — capstone cross-layer digest tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [jinushi.methods.digest :as d]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def data-dir (io/file repo-root "80-data" "jinushi-land"))
(defn m [] (d/collect data-dir))

(deftest test-collect-all-layers
  (let [c (m)]
    (is (get-in c [:land :coverage]) "land layer collected")
    (is (get-in c [:buildings :a]) "building layer collected")
    (is (:company c) "company linkage collected")
    (is (pos? (:jurisdictions c)) "jurisdiction gate present")))

(deftest test-render-sections
  (let [txt (d/render (m))]
    (is (str/includes? txt "LAND") "land section")
    (is (str/includes? txt "BUILDINGS") "buildings section")
    (is (str/includes? txt "COMPANY LINKAGE") "company section")
    (is (str/includes? txt "PUBLIC-RECORD GATE") "gate section")
    (is (str/includes? txt "ビルのフロア") "vertical floor concentration surfaced")
    (is (str/includes? txt "VALUE") "DVF value layer (€/m²) fused")
    (is (str/includes? txt "RELIABILITY") "信頼度 / reconcile layer fused")
    (is (str/includes? txt "相互監視") "charter framing present")
    (is (not (str/includes? txt ":person")) "no person dimension token")))

(deftest test-render-has-numbers
  (let [txt (d/render (m))]
    (is (re-find #"world land: \*\*[\d.]+%\*\*" txt) "national-park land share rendered")
    (is (re-find #"buildings: \*\*\d+\*\*" txt) "building count rendered")
    (is (re-find #"GLEIF: \*\*\d+\*\*" txt) "GLEIF linkage count rendered")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-digest)]
    (System/exit (+ (or fail 0) (or error 0)))))

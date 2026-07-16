(ns tasuke.methods.test-app-parity
  "Parity + invariant drift-lock for the 助 (tasuke) browser-local app (app/index.html).
  1:1 port of `methods/test_app_parity.py` (pytest → clojure.test).

  Guards that the in-browser JS reimplementation can never drift from the kotoba ontology, AND that
  it keeps its load-bearing promises STRUCTURALLY: its closed vocab == the ontology; it makes NO
  network call; it states free (¥0), member-submitted, and on-device."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [tasuke.methods.edn :as tedn]))

;; ROOT/20-actors/tasuke via *file* (…/tasuke/methods/test_app_parity.cljc → up 2 = tasuke)
#?(:clj (def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def ^:private repo-dir (-> actor-dir .getParentFile .getParentFile)))
#?(:clj (def ^:private app-file (io/file actor-dir "app" "index.html")))
#?(:clj (def ^:private ontology-file (io/file repo-dir "00-contracts" "schemas" "cybercrime-victim-support-ontology.kotoba.edn")))

#?(:clj (defn- html [] (slurp app-file)))

(defn- js-array
  "Mirror of the Python `_js_array`: find `const NAME = [ … ];` and pull every \"quoted\" token."
  [name]
  (let [m (re-find (re-pattern (str "const " name "\\s*=\\s*\\[([\\s\\S]*?)\\];")) (html))]
    (is m (str name " array not found in app"))
    (mapv second (re-seq #"\"([^\"]+)\"" (second m)))))

(defn- onto-kw [key]
  (set (map #(str/replace % #"^:+" "") (get (tedn/load-edn ontology-file) key))))

;; ── vocab parity with the ontology ───────────────────────────────────────────
(deftest test-app-scam-kinds-match-ontology
  (is (= (set (js-array "SCAM_KINDS")) (onto-kw ":ontology/scam-kinds"))))

(deftest test-app-doc-kinds-match-ontology
  (is (= (set (js-array "DOC_KINDS")) (onto-kw ":ontology/doc-kinds"))))

(deftest test-app-window-codes-match-ontology
  (is (= (set (js-array "WINDOW_CODES")) (onto-kw ":ontology/referral-windows"))))

;; ── the on-device / no-server-key guarantee, as a property of the file ───────
(deftest test-app-makes-no-network-call
  (let [h (str/lower-case (html))]
    (doseq [forbidden ["fetch(" "xmlhttprequest" "navigator.sendbeacon" "<form action" "websocket"]]
      (is (not (str/includes? h forbidden))
          (str "app must not " (pr-str forbidden) " (G6/G7 on-device, no upload)")))
    ;; no external script/style/img src — fully self-contained
    (is (not (re-find #"src\s*=\s*\"https?://" (html))) "app must load nothing external")))

;; ── it states the charter promises the victim relies on ──────────────────────
(deftest test-app-states-free-member-submitted-on-device
  (let [h (html)]
    (is (and (str/includes? h "¥0") (str/includes? h "無料")))
    (is (or (str/includes? h "本人作成・本人提出") (str/includes? h "本人が作成・署名・提出")))
    (is (and (str/includes? h "端末") (str/includes? h "出ません")))   ;; PII never leaves the device
    (is (str/includes? h "did:web:etzhayyim.com:actor:tasuke"))))      ;; it is the etzhayyim.com actor

#?(:clj (defn -main [& _] (run-tests 'tasuke.methods.test-app-parity)))

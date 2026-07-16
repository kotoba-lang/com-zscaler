(ns tate.tests.test-case-actors
  "tate 盾 — case-actor generator tests (wave 41).
  1:1 Clojure port of tests/test_case_actors.py (stdlib asserts → clojure.test).

  Generates into a temp dir at load time, then asserts 1-actor-per-procedure, keyless
  no-server-key DID, profile downloads/consultation, faithful disclaimed case.json,
  checklist + template, cases index, and deploy-copy parity. Inlines a minimal JSON
  reader (the generated files are JSON). File I/O behind #?(:clj …)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set :as set]
            [tate.methods.terms-scan :as ts]
            [tate.methods.respond-plan :as rp]
            [tate.methods.case-actors-gen :as cag]))

;; ── minimal JSON reader (string-keyed maps) ──────────────────────────────────
(declare json-value)
(defn- skip-ws [^String s i]
  (loop [i i]
    (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
      (recur (inc i)) i)))
(defn- json-string [^String s i]
  (loop [i (inc i), sb (StringBuilder.)]
    (let [c (nth s i)]
      (cond
        (= c \") [(.toString sb) (inc i)]
        (= c \\) (let [e (nth s (inc i))]
                   (case e
                     \" (do (.append sb \") (recur (+ i 2) sb))
                     \\ (do (.append sb \\) (recur (+ i 2) sb))
                     \/ (do (.append sb \/) (recur (+ i 2) sb))
                     \n (do (.append sb \newline) (recur (+ i 2) sb))
                     \t (do (.append sb \tab) (recur (+ i 2) sb))
                     \u (do (.append sb (char (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)))
                            (recur (+ i 6) sb))
                     (do (.append sb e) (recur (+ i 2) sb))))
        :else (do (.append sb c) (recur (inc i) sb))))))
(defn- json-number [^String s i]
  (let [end (loop [j i] (if (and (< j (count s))
                                 (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                          (recur (inc j)) j))
        tok (subs s i end)]
    [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))
(defn- json-array [^String s i]
  (loop [i (skip-ws s (inc i)), out []]
    (if (= (nth s i) \]) [out (inc i)]
        (let [[v i] (json-value s i) i (skip-ws s i)]
          (if (= (nth s i) \,) (recur (skip-ws s (inc i)) (conj out v)) [(conj out v) (inc i)])))))
(defn- json-object [^String s i]
  (loop [i (skip-ws s (inc i)), out {}]
    (if (= (nth s i) \}) [out (inc i)]
        (let [[k i] (json-string s i) i (skip-ws s i)
              [v i] (json-value s (skip-ws s (inc i))) out (assoc out k v) i (skip-ws s i)]
          (if (= (nth s i) \,) (recur (skip-ws s (inc i)) out) [out (inc i)])))))
(defn- json-value [^String s i]
  (let [i (skip-ws s i) c (nth s i)]
    (cond (= c \{) (json-object s i) (= c \[) (json-array s i) (= c \") (json-string s i)
          (= c \t) [true (+ i 4)] (= c \f) [false (+ i 5)] (= c \n) [nil (+ i 4)]
          :else (json-number s i))))
(defn- read-json [path] (first (json-value (slurp path) 0)))

(def ^:private tmp
  (let [d (java.nio.file.Files/createTempDirectory "tate-actor" (make-array java.nio.file.attribute.FileAttribute 0))]
    (clojure.java.io/file (.toFile d) "actor")))

(def ^:private index (cag/generate tmp "https://example.test"))

(defn- f [& parts] (apply clojure.java.io/file tmp parts))

(deftest test-one-actor-per-procedure
  (let [procs (rp/load-procs)]
    (is (= (count index) (count procs)))
    (doseq [p procs]
      (let [d (f (cag/slug (get p ":proc/id")))]
        (doseq [fn* ["did.json" "profile.json" "case.json" "checklist.md" "template.md"]]
          (is (.exists (clojure.java.io/file d fn*)) [d fn*]))))))

(deftest test-did-keyless-no-server-key
  (let [d (read-json (f "tate-shiharai-tokusoku" "did.json"))]
    (is (= (get d "id") "did:web:etzhayyim.com:actor:tate-shiharai-tokusoku"))
    (is (= (get d "verificationMethod") []))
    (let [types (set (map #(get % "type") (get d "service")))]
      (is (set/subset? #{"EtzhayyimCaseData" "EtzhayyimCaseChecklist" "EtzhayyimCaseGuide"} types)))))

(deftest test-profile-downloads-and-consultation
  (let [p (read-json (f "tate-de-kuendigung" "profile.json"))
        dl (get-in p ["_etzhayyim" "downloads"])
        cons* (get-in p ["_etzhayyim" "consultation"])]
    (is (str/ends-with? (get dl "case_json") "/actor/tate-de-kuendigung/case.json"))
    (is (str/ends-with? (get dl "checklist_md") "/checklist.md"))
    (is (and (get cons* "free_referrals") (get cons* "fraud_help")))
    (is (str/includes? (get cons* "yoro_convo") "operator"))))

(deftest test-case-json-faithful-and-disclaimed
  (let [c (read-json (f "tate-ch-zahlungsbefehl" "case.json"))]
    (is (str/includes? (get c "disclaimer") "法的助言ではありません"))
    (is (some #(and (get % "critical") (str/includes? (get % "anchor") "SchKG")) (get c "deadlines")))
    (is (every? #(get % "verify_service_date") (get c "deadlines")))
    (is (= (get c "verify_current_law") true))))

(deftest test-checklist-disclaimer-and-critical
  (let [md (slurp (f "tate-au-unfair-dismissal" "checklist.md"))]
    (is (and (str/includes? md "法的助言ではありません") (str/includes? md "⚠") (str/includes? md "🛡")))))

(deftest test-cases-index
  (let [idx (read-json (f "tate" "cases.json"))
        tracks (set (map #(get % "track") (get idx "cases")))]
    (is (= (get idx "count") (count index)))
    (is (and (contains? tracks ":labor") (contains? tracks ":housing")))))

(deftest test-deploy-copy-in-sync
  (let [deploy (clojure.java.io/file (ts/here) ".." ".." "50-infra" "etzhayyim-did-web" "public" "actor")
        deployed (set (for [d (.listFiles deploy)
                            :when (str/starts-with? (.getName d) "tate-")]
                        (.getName d)))
        fresh (set (map #(get % "slug") index))]
    (is (= deployed fresh) [(take 3 (sort (set/difference fresh deployed)))
                            (take 3 (sort (set/difference deployed fresh)))])
    (is (.exists (clojure.java.io/file deploy "tate" "cases.json")))))

(deftest test-templates-fill-in-and-upl
  (let [procs (rp/load-procs)]
    (doseq [p procs]
      (let [md (slurp (f (cag/slug (get p ":proc/id")) "template.md"))
            subs* (filterv #(= (get % ":opt/kind") ":self-submit") (get p ":proc/options" []))]
        (is (str/includes? md "法的助言ではありません") (get p ":proc/id"))
        (if (seq subs*)
          (is (and (str/includes? md "【") (str/includes? md "提出前チェック")) (get p ":proc/id"))
          (is (str/includes? md "定型の提出書面はありません") (get p ":proc/id")))))
    (let [ca (slurp (f "tate-ca-bia-notice" "template.md"))]
      (is (str/includes? ca "公式様式")))))

#?(:clj (defn -main [& _] (run-tests 'tate.tests.test-case-actors)))

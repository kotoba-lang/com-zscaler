(ns tasuke.methods.test-packet
  "Tests for 助 (tasuke) packet generator — the usable \"誰でも使える\" surface.
  1:1 port of `methods/test_packet.py` (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [tasuke.methods.edn :as tedn]
            [tasuke.methods.packet :as packet]))

;; ROOT/20-actors/tasuke via *file* (…/tasuke/methods/test_packet.cljc → up 2 = tasuke)
#?(:clj (def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def ^:private seed-file (io/file actor-dir "data" "seed-cybercrime-cases.kotoba.edn")))

(defn- cases [] (get (tedn/load-edn seed-file) ":case/batch"))

(defn- doc-kinds [docs]
  (map #(str/replace (get % ":doc/kind") #"^:+" "") docs))

;; ── the packet is free + complete for every seed case ────────────────────────
(deftest test-build-packet-every-case-free-and-documented
  (doseq [c (cases)]
    (let [p (packet/build-packet c)]
      (is (= 0 (get p "cost")))
      (is (seq (get p "documents")) (str (get p "caseId") " produced no documents"))
      (doseq [d (get p "documents")]
        (is (= ":member" (get d ":doc/authored-by")))
        (is (= 0 (get d ":doc/support-cost-jpy")))
        (is (= true (get d ":doc/needs-member-signature")))
        (is (= false (get d ":doc/published")))))))

(deftest test-document-selection-by-kind
  (let [by-id (into {} (map (fn [c] [(get c ":case/id") c]) (cases)))
        fund (packet/build-packet (get by-id "c-fund-1"))
        kinds (doc-kinds (get fund "documents"))
        to (packet/build-packet (get by-id "c-takeover-1"))
        tkinds (doc-kinds (get to "documents"))]
    (is (some #{"bank-freeze-request"} kinds))          ;; money moved → bank組戻し
    (is (and (some #{"recovery-plan"} tkinds) (some #{"platform-request"} tkinds)))))

(deftest test-police-core-always-present
  (let [core #{"damage-report" "incident-statement" "evidence-index" "damage-calculation"}]
    (doseq [c (cases)]
      (let [kinds (set (doc-kinds (get (packet/build-packet c) "documents")))]
        (is (clojure.set/subset? core kinds))))))

;; ── regression: the seed registry must be reachable (the stray-brace bug) ─────
(deftest test-windows-are-populated-from-registry
  (let [p (packet/build-packet (first (cases)))]
    (is (seq (get p "windows")))
    ;; every window resolves to a real registry name, not just the bare code
    (doseq [w (get p "windows")]
      (is (and (seq (get w "name")) (not= (get w "name") (get w "code")))))))

;; ── the cover restates the charter promises ──────────────────────────────────
(deftest test-cover-states-free-and-member-submitted
  (let [cover (packet/cover (packet/build-packet (first (cases))))]
    (is (and (str/includes? cover "¥0") (str/includes? cover "全て無料")))
    (is (str/includes? cover "本人が作成・署名・提出"))))

;; ── write_packet emits printable files ───────────────────────────────────────
#?(:clj
   (deftest test-write-packet-emits-cover-and-docs
     (let [p (packet/build-packet (first (cases)))
           td (java.nio.file.Files/createTempDirectory "tasuke-pk" (make-array java.nio.file.attribute.FileAttribute 0))
           out (packet/write-packet p (io/file (.toFile td) "pk"))
           files (sort (map #(.getName %) (.listFiles out)))]
       (is (some #{"00-COVER.md"} files))
       (is (= (count (get p "documents"))
              (count (filter #(str/ends-with? % ".txt") files)))))))

;; ── G1/G7 gate still bites through the packet path ───────────────────────────
(deftest test-packet-refuses-non-consented-case
  (let [bad (assoc (first (cases)) ":case/consent" false)]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G7"
          (packet/build-packet bad)))))

(deftest test-packet-refuses-priced-case
  (let [bad (assoc (first (cases)) ":case/support-cost-jpy" 1000)]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G1"
          (packet/build-packet bad)))))

#?(:clj (defn -main [& _] (run-tests 'tasuke.methods.test-packet)))

(ns keizu.methods.test-charter-invariants
  "test_charter_invariants.py — 系図 (keizu) structural-invariant drift-lock. ADR-2606066000.
  1:1 Clojure port (stdlib _t harness → clojure.test).

  Parses the THREE homes of each invariant (ontology :db/allowed/enum vectors · lexicon
  :const/:enum · the seed values) and asserts they agree and carry no representable charter
  violation. Touch one home without the others and this suite fails loudly.

  The keizu.methods.edn reader keeps EDN keywords as ':ns/name' STRINGS, so ontology vocab tokens
  come back as ':public-office' etc. (stripped of the leading ':' to compare to lexicon enum
  strings), while lexicon map keys are also ':…' strings ([\":defs\"][\":main\"][\":record\"]…).
  All file I/O is behind #?(:clj …)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set :as set]
            #?(:clj [keizu.methods.edn :as kedn])
            [keizu.methods.weave :as w]))

;; ── paths (all *file*-relative, matching the Python pathlib.parents arithmetic) ──
;; *file* = …/20-actors/keizu/methods/test_charter_invariants.cljc
;; methods → keizu → 20-actors → ROOT (parents[3] in Python = ROOT)
#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))            ;; methods/
     (def ^:private keizu-dir (.getParentFile here))                                 ;; keizu/
     (def ^:private actors-dir (.getParentFile keizu-dir))                           ;; 20-actors/
     (def ^:private root (.getParentFile actors-dir))                                ;; ROOT
     (def ^:private ONT (java.io.File. root "00-contracts/schemas/government-relations-ontology.kotoba.edn"))
     (def ^:private LEXDIR (java.io.File. keizu-dir "lex"))
     (def ^:private SEED (java.io.File. (java.io.File. keizu-dir "data") "seed-relation-graph.kotoba.edn"))))

(def ^:private PRIVATE-TOKENS
  ["private-person" "individual" "citizen" "person" "natural-person"])

;; strip ALL leading ':' (Python str.lstrip(":"))
(defn- lstrip-colon [s] (str/replace (str s) #"^:+" ""))

#?(:clj (defn- ont [] (kedn/load-edn ONT)))

#?(:clj
   (defn- datom [ident]
     (or (first (filter #(= ident (get % ":db/ident")) (get (ont) ":schema")))
         (throw (ex-info (str "no schema datom " ident) {})))))

#?(:clj (defn- lex [name] (kedn/load-edn (java.io.File. LEXDIR (str name ".edn")))))
#?(:clj (defn- props [name] (get-in (lex name) [":defs" ":main" ":record" ":properties"])))

;; ── ontology closed-vocab invariants ────────────────────────────────────────────
(deftest test-ont-node-scopes-no-private
  #?(:clj
     (let [scopes (mapv lstrip-colon (get (ont) ":ontology/node-scopes"))]
       (doseq [tok PRIVATE-TOKENS]
         (is (not (some #{tok} scopes)) (str "G1: " tok " must not be a node scope"))))
     :cljs (is true)))

(deftest test-ont-rel-kinds-no-verdict
  #?(:clj
     (let [kinds (mapv lstrip-colon (get (ont) ":ontology/rel-kinds"))]
       (doseq [tok w/VERDICT-TOKENS]
         (is (not (some #{tok} kinds)) (str "G2: verdict " tok " must not be a rel kind"))))
     :cljs (is true)))

(deftest test-ont-money-kinds-no-verdict
  #?(:clj
     (let [kinds (mapv lstrip-colon (get (ont) ":ontology/money-kinds"))]
       (doseq [tok w/VERDICT-TOKENS]
         (is (not (some #{tok} kinds)) (str "G2: verdict " tok " must not be a money kind"))))
     :cljs (is true)))

(deftest test-ont-post-status-dry-run-only
  #?(:clj
     (let [statuses (mapv lstrip-colon (get (ont) ":ontology/post-statuses"))]
       (is (= ["dry-run"] statuses) (str "G8: post status must be dry-run only, got " statuses)))
     :cljs (is true)))

;; ── ontology schema :db/allowed invariants ──────────────────────────────────────
(deftest test-schema-scope-allowed
  #?(:clj
     (let [allowed (mapv lstrip-colon (get (datom ":node/scope") ":db/allowed"))]
       (doseq [tok PRIVATE-TOKENS]
         (is (not (some #{tok} allowed)))))
     :cljs (is true)))

(deftest test-schema-no-power-score-attr
  #?(:clj
     (let [idents (set (map #(get % ":db/ident") (get (ont) ":schema")))]
       (doseq [bad [":node/power-score" ":node/influence" ":node/rank" ":node/score-of-soul"]]
         (is (not (contains? idents bad)) (str "G4: " bad " must not exist"))))
     :cljs (is true)))

(deftest test-schema-rel-notice-true-only
  #?(:clj (is (= [true] (get (datom ":rel/non-adjudicating-notice") ":db/allowed"))) :cljs (is true)))

(deftest test-schema-post-status-dry-run-only
  #?(:clj (is (= ["dry-run"] (mapv lstrip-colon (get (datom ":post/status") ":db/allowed")))) :cljs (is true)))

(deftest test-schema-post-server-key-false-only
  #?(:clj (is (= [false] (get (datom ":post/server-held-key") ":db/allowed"))) :cljs (is true)))

(deftest test-schema-post-is-mirror-true-only
  #?(:clj (is (= [true] (get (datom ":post/is-mirror") ":db/allowed"))) :cljs (is true)))

;; ── lexicon :enum/:const invariants ─────────────────────────────────────────────
(deftest test-lex-rel-kind-no-verdict
  #?(:clj
     (let [enum (get-in (props "relationEdge") [":kind" ":enum"])]
       (doseq [tok w/VERDICT-TOKENS] (is (not (some #{tok} enum)))))
     :cljs (is true)))

(deftest test-lex-rel-notice-const-true
  #?(:clj (is (true? (get-in (props "relationEdge") [":nonAdjudicatingNotice" ":const"]))) :cljs (is true)))

(deftest test-lex-rel-sources-min-two
  #?(:clj (is (= 2 (get-in (props "relationEdge") [":sources" ":minLength"]))) :cljs (is true)))

(deftest test-lex-money-kind-no-verdict
  #?(:clj
     (let [enum (get-in (props "moneyFlowObservation") [":kind" ":enum"])]
       (doseq [tok w/VERDICT-TOKENS] (is (not (some #{tok} enum)))))
     :cljs (is true)))

(deftest test-lex-money-sources-min-two
  #?(:clj (is (= 2 (get-in (props "moneyFlowObservation") [":sources" ":minLength"]))) :cljs (is true)))

(deftest test-lex-post-status-const-dry-run
  #?(:clj (is (= "dry-run" (get-in (props "networkPost") [":status" ":const"]))) :cljs (is true)))

(deftest test-lex-post-is-mirror-const-true
  #?(:clj (is (true? (get-in (props "networkPost") [":isMirror" ":const"]))) :cljs (is true)))

(deftest test-lex-post-server-key-const-false
  #?(:clj (is (false? (get-in (props "networkPost") [":serverHeldKey" ":const"]))) :cljs (is true)))

;; ── seed value invariants ───────────────────────────────────────────────────────
(deftest test-seed-nodes-public-scope
  #?(:clj
     (let [seed (kedn/load-edn SEED)
           allowed (set (map lstrip-colon (get (ont) ":ontology/node-scopes")))]
       (doseq [n (get seed ":nodes")]
         (is (contains? allowed (lstrip-colon (get n ":node/scope"))))
         (is (not (contains? n ":node/power-score")))))
     :cljs (is true)))

(deftest test-seed-nodes-carry-no-pii
  #?(:clj
     (do
       (is (seq w/PII-FORBIDDEN-NODE-ATTRS))  ;; the closed no-doxxing set exists
       (let [seed (kedn/load-edn SEED)]
         (doseq [n (get seed ":nodes")]
           (doseq [key (keys n)]
             (is (not (contains? w/PII-FORBIDDEN-NODE-ATTRS
                                 (str/lower-case (last (str/split (lstrip-colon key) #"/"))))) key)))))
     :cljs (is true)))

(deftest test-seed-rels-two-sources-and-factual
  #?(:clj
     (let [seed (kedn/load-edn SEED)
           kinds (set (map lstrip-colon (get (ont) ":ontology/rel-kinds")))]
       (doseq [r (get seed ":rels")]
         (is (>= (count (get r ":rel/sources")) 2) (get r ":rel/id"))
         (is (true? (get r ":rel/non-adjudicating-notice")))
         (is (contains? kinds (lstrip-colon (get r ":rel/kind"))))))
     :cljs (is true)))

(deftest test-seed-money-two-sources-and-factual
  #?(:clj
     (let [seed (kedn/load-edn SEED)
           kinds (set (map lstrip-colon (get (ont) ":ontology/money-kinds")))]
       (doseq [m (get seed ":money")]
         (is (>= (count (get m ":money/sources")) 2) (get m ":money/id"))
         (is (contains? kinds (lstrip-colon (get m ":money/kind"))))))
     :cljs (is true)))

;; ── lexicon ⊆ ontology drift-lock (BOTH directions) ─────────────────────────────
(deftest test-lex-rel-kind-subset-of-ontology
  #?(:clj
     (let [enum (set (get-in (props "relationEdge") [":kind" ":enum"]))
           vocab (set (map lstrip-colon (get (ont) ":ontology/rel-kinds")))]
       (is (set/subset? enum vocab)
           (str "lexicon rel kinds not in ontology: " (set/difference enum vocab))))
     :cljs (is true)))

(deftest test-ontology-rel-kinds-all-in-lex
  #?(:clj
     (let [enum (set (get-in (props "relationEdge") [":kind" ":enum"]))
           vocab (set (map lstrip-colon (get (ont) ":ontology/rel-kinds")))]
       (is (set/subset? vocab enum)
           (str "ontology rel kinds missing from lexicon: " (set/difference vocab enum))))
     :cljs (is true)))

(deftest test-lex-money-kind-subset-of-ontology
  #?(:clj
     (let [enum (set (get-in (props "moneyFlowObservation") [":kind" ":enum"]))
           vocab (set (map lstrip-colon (get (ont) ":ontology/money-kinds")))]
       (is (set/subset? enum vocab)
           (str "lexicon money kinds not in ontology: " (set/difference enum vocab))))
     :cljs (is true)))

(deftest test-ontology-money-kinds-all-in-lex
  #?(:clj
     (let [enum (set (get-in (props "moneyFlowObservation") [":kind" ":enum"]))
           vocab (set (map lstrip-colon (get (ont) ":ontology/money-kinds")))]
       (is (set/subset? vocab enum)
           (str "ontology money kinds missing from lexicon: " (set/difference vocab enum))))
     :cljs (is true)))

(deftest test-lex-sourcing-matches-ontology-grades
  #?(:clj
     (let [grades (set (map lstrip-colon (get (ont) ":ontology/sourcing-grades")))]
       (doseq [lx ["relationEdge" "moneyFlowObservation" "committeeComposition"]]
         (let [enum (set (get-in (props lx) [":sourcing" ":enum"]))]
           (is (= enum grades) (str lx " sourcing " enum " != ontology grades " grades)))))
     :cljs (is true)))

(deftest test-post-status-const-matches-ontology
  #?(:clj
     (let [statuses (set (map lstrip-colon (get (ont) ":ontology/post-statuses")))]
       (is (= #{(get-in (props "networkPost") [":status" ":const"])} statuses)))
     :cljs (is true)))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-charter-invariants)))

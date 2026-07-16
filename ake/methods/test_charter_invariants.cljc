(ns ake.methods.test-charter-invariants
  "test_charter_invariants.cljc — structural charter-invariant tests for 朱 (ake).
  1:1 Clojure port of `methods/test_charter_invariants.py` (clojure.test). Every Python
  assertion ported. Asserts the invariants STRUCTURALLY over the parsed ontology /
  lexicons / code constants — never by grepping prose. A regression here means an
  invariant was weakened: 信者-gated + no-server-key (G1), LLM-non-adjudicating (G2),
  mirror-preserving (G3), sourcing-mandatory (G4), append-only (G5), invariant-lock (G7),
  outward-gated (G8).

  Ontology / lexicon EDN is read via `ake.methods._edn` (the #?(:clj) file edge); the code
  constants come from `ake.methods.triage` (ROUTES / route-for). The __main__ standalone
  runner is omitted."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [ake.methods.triage :as triage]
            #?(:clj [ake.methods._edn :as edn])))

#?(:clj
   (def ^:private lex-dir "20-actors/ake/lex"))

#?(:clj
   (def ^:private ontology
     "00-contracts/schemas/community-edit-ontology.kotoba.edn"))

#?(:clj
   (defn- onto [] (edn/load-edn ontology)))

#?(:clj
   (defn- props [lex-name]
     (-> (edn/reconstitute (edn/load-edn (str lex-dir "/" lex-name ".edn")) (str "lex." lex-name))
         (get-in [":defs" ":main" ":record" ":properties"]))))

#?(:clj
   (defn- required [lex-name]
     (-> (edn/reconstitute (edn/load-edn (str lex-dir "/" lex-name ".edn")) (str "lex." lex-name))
         (get-in [":defs" ":main" ":record" ":required"]))))

;; ── G3 mirror-preserving: only facts/profiles, never speech-as-entity ───────
#?(:clj
   (deftest test-ontology-target-kinds-exclude-impersonation
     (let [tk (get (onto) ":ontology/target-kinds")]
       (is (= [":kg-fact" ":actor-profile"] tk))
       (doseq [forbidden [":entity-speech" ":impersonate" ":speak-as-entity"]]
         (is (not (some #{forbidden} tk)))))))

#?(:clj
   (deftest test-lexicon-target-kind-enum-excludes-impersonation
     (let [enum (set (get-in (props "editProposal") [":targetKind" ":enum"] []))]
       (is (= #{"kg-fact" "actor-profile"} enum))
       (is (empty? (clojure.set/intersection
                    enum #{"entity-speech" "impersonate" "speak-as-entity"}))))))

;; ── G5 append-only: no delete / overwrite anywhere ──────────────────────────
#?(:clj
   (deftest test-ontology-edit-ops-are-append-only
     (let [ops (get (onto) ":ontology/edit-ops")]
       (is (and (not (some #{":delete"} ops)) (not (some #{":overwrite"} ops)))))))

#?(:clj
   (deftest test-lexicon-op-enums-exclude-delete-overwrite
     (doseq [[lex prop] [["editProposal" ":op"] ["revisionEntry" ":op"]]]
       (let [enum (set (get-in (props lex) [prop ":enum"] []))]
         (is (empty? (clojure.set/intersection enum #{"delete" "overwrite"})))))))

;; ── G1 信者-gated + no-server-key ──────────────────────────────────────────
#?(:clj
   (deftest test-ontology-author-kinds-member-only
     (let [ak (get (onto) ":ontology/author-kinds")]
       (is (= [":member"] ak))
       (is (and (not (some #{":server"} ak)) (not (some #{":anon"} ak)))))))

#?(:clj
   (deftest test-proposal-author-kind-const-member
     (is (= "member" (get-in (props "editProposal") [":authorKind" ":const"])))))

#?(:clj
   (deftest test-proposal-server-held-key-const-false
     (is (false? (get-in (props "editProposal") [":serverHeldKey" ":const"])))))

#?(:clj
   (deftest test-promotion-server-held-key-const-false
     (is (false? (get-in (props "editPromotion") [":serverHeldKey" ":const"])))))

;; ── G4 sourcing-mandatory ───────────────────────────────────────────────────
#?(:clj
   (deftest test-proposal-requires-provenance
     (is (some #{"provenance"} (required "editProposal")))))

;; ── G2 LLM non-adjudicating: triage has NO decision field ───────────────────
#?(:clj
   (deftest test-triage-lexicon-has-no-decision-field
     (let [p (props "editTriage")]
       (is (not (contains? p ":decision")))
       (is (not (some #(str/includes? (str/lower-case (str %)) "decision") (keys p)))))))

#?(:clj
   (deftest test-triage-route-enum-has-no-bare-accept-reject
     (let [enum (set (get-in (props "editTriage") [":route" ":enum"] []))]
       ;; 'auto-accept' is a ROUTE (the optimistic rule), a bare 'accept'/'reject' verdict is not
       (is (and (not (contains? enum "accept")) (not (contains? enum "reject"))))
       (is (= #{"auto-accept" "vote" "council-lv7" "refused"} enum)))))

#?(:clj
   (deftest test-ontology-schema-has-no-triage-decision-ident
     (let [idents (set (keep #(when (map? %) (get % ":db/ident")) (get (onto) ":schema")))]
       (is (not (contains? idents ":triage/decision"))))))

;; ── G8 outward-gated: R0 promotion never publishes ──────────────────────────
#?(:clj
   (deftest test-promotion-published-const-false
     (is (false? (get-in (props "editPromotion") [":published" ":const"])))))

;; ── G7 invariant-lock: Council review is Lv7 ────────────────────────────────
#?(:clj
   (deftest test-council-review-level-const-lv7
     (is (= "Lv7" (get-in (props "councilEditReview") [":level" ":const"])))))

;; ── code constants agree with the ontology (single source of truth) ─────────
#?(:clj
   (deftest test-code-route-table-matches-ontology
     (let [onto-routes (set (map #(str/replace % #"^:+" "")
                                 (get (onto) ":ontology/triage-routes")))]
       (is (= (set triage/ROUTES) onto-routes)))))

(deftest test-code-invariant-attrs-block-optimistic-accept
  ;; a license/charter edit can never auto-accept, regardless of quality
  (is (= "council-lv7" (triage/route-for "invariant" 1.0 ""))))

#?(:clj (defn -main [& _] (run-tests 'ake.methods.test-charter-invariants)))

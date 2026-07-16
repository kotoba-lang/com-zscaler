(ns shomei.methods.test-lexicons
  "test_lexicons.cljc — the 4 shomei lexicons parse + their enums match factors.cljc (one SSoT).
  1:1 Clojure port of `methods/test_lexicons.py`. ADR-2606072100. clojure.test.

  Loads the lexicon JSON via `shomei.methods.edn/parse-json` (string-keyed maps, same shape as
  `json.loads`). The lexicon dir is resolved *file*-relative behind #?(:clj) like
  kanae/test_pipeline.cljc: …/20-actors/shomei/methods/test_lexicons.cljc → up 4 = repo root →
  00-contracts/lexicons/com/etzhayyim/shomei. The `__main__` demo (`run(\"lexicons\", CASES)`)
  is omitted (clojure.test drives the suite)."
  (:require [clojure.test :refer [deftest is run-tests]]
            #?(:clj [clojure.java.io :as io])
            [shomei.methods.edn :as e]
            [shomei.methods.factors :refer [CLASSES FACTOR_KINDS PROOF_KINDS REVOCATION_REASONS]]))

#?(:clj
   (def ^:private lex-dir
     ;; …/methods/test_lexicons.cljc → methods → shomei → 20-actors → root
     (-> *file* io/file .getParentFile .getParentFile .getParentFile .getParentFile
         (io/file "00-contracts" "lexicons" "com" "etzhayyim" "shomei"))))

(defn- load-lex [name]
  #?(:clj (e/parse-json (slurp (io/file lex-dir (str name ".json"))))
     :cljs (throw (ex-info "lexicon fixtures require :clj host I/O" {}))))

(defn- props [lex]
  (get-in lex ["defs" "main" "record" "properties"]))

(deftest test-all-four-present-and-well-formed
  (doseq [n ["verificationChallenge" "identityClaim" "personhoodCredential" "bindingRevocation"]]
    (let [lex (load-lex n)]
      (is (= 1 (get lex "lexicon")))
      (is (= (str "com.etzhayyim.shomei." n) (get lex "id")))
      (is (= "record" (get-in lex ["defs" "main" "type"]))))))

(deftest test-identity-claim-enums-match-factors
  (let [p (props (load-lex "identityClaim"))]
    (is (= (set FACTOR_KINDS) (set (get-in p ["factorKind" "enum"]))))
    (is (= (set PROOF_KINDS) (set (get-in p ["proofKind" "enum"]))))
    (is (= (set CLASSES) (set (get-in p ["factorClass" "enum"]))))))

(deftest test-identity-claim-required-subject-sig-no-server-sig
  (let [lex (load-lex "identityClaim")
        req (get-in lex ["defs" "main" "record" "required"])
        p (props lex)]
    (is (some #{"subjectSig"} req))
    (doseq [forbidden ["serverSig" "platformSig" "operatorSig" "adminSig"]]
      (is (not (contains? p forbidden)) (str "G7: " forbidden " must not be a lexicon field")))))

(deftest test-challenge-factor-enum-matches
  (let [p (props (load-lex "verificationChallenge"))]
    (is (= (set FACTOR_KINDS) (set (get-in p ["factorKind" "enum"]))))))

(deftest test-personhood-enums-and-no-social-credit
  (let [p (props (load-lex "personhoodCredential"))]
    (is (= (set FACTOR_KINDS) (set (get-in p ["verifiedFactors" "items" "enum"]))))
    (is (= [0 1 2 3 4] (get-in p ["assuranceLevel" "enum"])))
    (doseq [forbidden ["score" "rank" "reputation" "trustScore" "worth" "socialCredit"]]
      (is (not (contains? p forbidden)) (str "G8: " forbidden " must not be a credential field")))))

(deftest test-revocation-reason-enum-matches
  (let [p (props (load-lex "bindingRevocation"))]
    (is (= (set REVOCATION_REASONS) (set (get-in p ["reason" "enum"]))))))

#?(:clj (defn -main [& _] (run-tests 'shomei.methods.test-lexicons)))

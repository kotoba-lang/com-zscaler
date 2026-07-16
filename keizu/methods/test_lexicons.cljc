(ns keizu.methods.test-lexicons
  "test_lexicons.py — 系図 (keizu) lexicon well-formedness. ADR-2606066000.
  1:1 Clojure port (stdlib unittest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [keizu.methods._edn :as edn]))

(def ^:private lex-dir
  #?(:clj (io/file (-> *file* io/file .getParentFile .getParentFile) "lex")))

(def ^:private lexes
  ["relationEdge" "committeeComposition" "moneyFlowObservation" "networkPost"])

(deftest test-all-four-present
  (doseq [name lexes]
    (is (.exists #?(:clj (io/file lex-dir (str name ".edn"))))
        name)))

(deftest test-ids-namespaced
  (doseq [name lexes]
    (let [lx (edn/load-edn #?(:clj (io/file lex-dir (str name ".edn"))))
          id (get lx ":id")]
      (is (str/starts-with? id "com.etzhayyim.keizu."))
      (is (str/ends-with? id name)))))

(deftest test-each-is-a-record
  (doseq [name lexes]
    (let [lx (edn/load-edn #?(:clj (io/file lex-dir (str name ".edn"))))
          main (get-in lx [":defs" ":main"])]
      (is (= "record" (get main ":type")))
      (is (contains? main ":record"))
      (is (contains? (get main ":record") ":properties")))))

(deftest test-required-fields-exist-in-properties
  (doseq [name lexes]
    (let [lx (edn/load-edn #?(:clj (io/file lex-dir (str name ".edn"))))
          rec (get-in lx [":defs" ":main" ":record"])
          props (set (keys (get rec ":properties")))
          required (get rec ":required" [])]
      (doseq [req required]
        (is (contains? props (str ":" req))
            (str name ": required " req " missing from properties"))))))

(deftest test-committee-members-min-one
  (let [lx (edn/load-edn #?(:clj (io/file lex-dir "committeeComposition.edn")))
        members (get-in lx [":defs" ":main" ":record" ":properties" ":members"])]
    (is (= 1 (get members ":minLength")))))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-lexicons)))

#!/usr/bin/env bb
;; uzu 渦 — maturity self-audit: the actor reports + verifies its own inventory.
(ns uzu.methods.scorecard
  "scorecard.cljc — uzu 渦 maturity self-audit (ADR-2606211500).

  Self-reporting, the maturity counterpart of the colony's digest self-reflection: the actor
  reads its OWN manifest, tallies its inventory (methods / gates / non-goals / lexicons / test
  suites), and AUDITS that every declared method file, test suite, and lexicon file actually
  exists on disk — catching manifest↔filesystem drift structurally. Pure tally; :clj does the
  existence checks. No network (no-server-key). It does not run the tests (that would shell out
  + be circular); it reports the manifest's declared test status and verifies the files are there."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

(defn tally
  "Pure inventory counts read from the manifest map."
  [manifest]
  {:id (:actor/id manifest)
   :status (:actor/status manifest)
   :adr (:actor/adr manifest)
   :methods (count (:actor/methods manifest))
   :gates (count (:actor/gates manifest))
   :non-goals (count (:actor/non-goals manifest))
   :lexicons (count (:actor/lex manifest))
   :suites (count (get-in manifest [:actor/tests :suites]))
   :related (count (:actor/related manifest))
   :test-status (get-in manifest [:actor/tests :status])})

#?(:clj
   (defn audit
     "Verify every declared method file, test suite, and lexicon file exists on disk.
     actor-dir e.g. \"20-actors/uzu\"; lex-root e.g. \"00-contracts/lexicons/com/etzhayyim/uzu\".
     Returns {:ok :missing-methods :missing-suites :missing-lexicons}."
     [manifest actor-dir lex-root]
     (let [exists? (fn [f] (.exists (io/file f)))
           miss-m (->> (:actor/methods manifest)
                       (remove #(exists? (io/file actor-dir (:method/file %))))
                       (mapv :method/file))
           miss-s (->> (get-in manifest [:actor/tests :suites])
                       (remove #(exists? (io/file actor-dir %)))
                       vec)
           miss-l (->> (:actor/lex manifest)
                       (remove #(exists? (io/file lex-root (str (:lex/id %) ".json"))))
                       (mapv :lex/id))]
       {:ok (and (empty? miss-m) (empty? miss-s) (empty? miss-l))
        :missing-methods miss-m :missing-suites miss-s :missing-lexicons miss-l})))

(defn datoms
  "EAVT datoms for the maturity scorecard (:uzu.scorecard/*)."
  [t audit]
  (let [e "uzu:scorecard/self"]
    [[":db/add" e ":uzu.scorecard/methods" (:methods t)]
     [":db/add" e ":uzu.scorecard/gates" (:gates t)]
     [":db/add" e ":uzu.scorecard/non-goals" (:non-goals t)]
     [":db/add" e ":uzu.scorecard/lexicons" (:lexicons t)]
     [":db/add" e ":uzu.scorecard/suites" (:suites t)]
     [":db/add" e ":uzu.scorecard/audit-ok" (boolean (:ok audit))]
     [":db/add" e ":uzu/derived" true]
     [":db/add" e ":uzu/sourcing" ":synthetic"]]))

(defn report
  "Markdown self-report rendering."
  [t audit]
  (str/join "\n"
    [(str "## uzu 渦 self-audit — " (:id t) " (" (:status t) ", ADR-" (:adr t) ")")
     ""
     (format "- methods: **%d** · gates: **%d** · non-goals: **%d** · lexicons: **%d** · test suites: **%d** · related ADRs: **%d**"
             (:methods t) (:gates t) (:non-goals t) (:lexicons t) (:suites t) (:related t))
     (str "- declared test status: " (:test-status t))
     (str "- manifest↔filesystem audit: " (if (:ok audit) "✅ OK (all method/suite/lexicon files present)"
                                              (str "❌ DRIFT — missing methods=" (:missing-methods audit)
                                                   " suites=" (:missing-suites audit)
                                                   " lexicons=" (:missing-lexicons audit))))]))

#?(:clj
   (defn -main [& args]
     (let [actor-dir (or (first args) "20-actors/uzu")
           lex-root (or (second args) "00-contracts/lexicons/com/etzhayyim/uzu")
           manifest (edn/read-string (slurp (str actor-dir "/manifest.edn")))
           t (tally manifest)
           a (audit manifest actor-dir lex-root)]
       (println (report t a))
       (when-not (:ok a) (System/exit 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))

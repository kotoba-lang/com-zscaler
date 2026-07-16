(ns mimamori.methods.coverage-report
  "mimamori 見守り — AGGREGATE-ONLY coverage report (G5: NEVER-a-throne).
  Clojure port of `methods/coverage_report.py`.

  誰の保持者でもない人間を作らない — measured without naming anyone.
  The report contains COUNTS only. No DID, no per-person line, ever
  (test-enforced: \"did:\" must not appear in the output)."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [mimamori.methods.bond :as bond]))

(defn coverage-of-engine
  "Aggregate-only coverage over a (possibly already-mutated) engine (G5)."
  [m roster-list]
  (let [roster (set roster-list)
        by-state (fn [st] (into #{} (keep (fn [[bid s]] (when (= s st) (get-in m [:kept bid]))))
                                (:state m)))
        kept-active (by-state ":active")
        kept-pending (by-state ":offered")]
    {:members-total (count roster)
     :with-keeper (count (set/intersection kept-active roster))
     :offers-pending (count (set/intersection (set/difference kept-pending kept-active) roster))
     :unkept-count (count (set/difference roster kept-active kept-pending))
     :active-bonds (count (filter #(= ":active" (val %)) (:state m)))
     :relays (count (filter #(= ":handed-off" (val %)) (:state m)))
     :datoms (count (:datoms m))}))

(defn coverage [seed]
  (coverage-of-engine (bond/replay seed) (:roster seed)))

(defn render [c]
  (str/join
   "\n"
   ["# mimamori 見守り — coverage report (AGGREGATE-ONLY, G5)"
    ""
    "GENERATED — do not hand-edit. No DID appears here, by construction."
    ""
    (str "- members (synthetic roster): " (:members-total c))
    (str "- with an active keeper:      " (:with-keeper c))
    (str "- offers pending:             " (:offers-pending c))
    (str "- **unkept (the gap)**:       " (:unkept-count c))
    (str "- active bonds:               " (:active-bonds c))
    (str "- relays (継ぎ):              " (:relays c))
    (str "- datoms (append-only):       " (:datoms c))
    ""
    "The unkept are not listed (G5). The offer-matching cell reaches them"
    "directly, one covenant offer at a time (ADR-2606112300 §D4; G7-gated)."
    ""]))

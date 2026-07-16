#!/usr/bin/env bb
;; chigiri 契 — kotoba Datom-log (EAVT) emitter for the legal-aid referral registry.
(ns chigiri.methods.datom-emit
  "datom_emit.cljc — chigiri 契 referral-registry → kotoba Datom log (EAVT) projector
  (ADR-2605262700, on ADR-2605312345). Brings chigiri to the same datomic-isomorphic
  canonical-state surface the other actors carry (it previously had a JSON registry but
  no EAVT projection at all).

  GROUND op :add = durable: one entity per referral body
  ('referral:<referralId>') carrying its disclosed wayfinding attributes
  (title / jurisdiction / bloc / authority / channel / legal-basis / language /
  provenance / confidence / last-verified / verification-status).

  G8/G14: this is a REFERRAL registry projection — a map of where to route a consenting
  member to LICENSED human counsel, NEVER advice and NEVER a verdict. :verification-status
  is preserved verbatim (every seed entry is :unverified-seed; emission never upgrades it)."
  (:require [clojure.string :as str]
            [chigiri.methods.registry :as reg]))

(defn fmt
  "bool → true/false; nil → nil; \":…\" kept literal; other string → quoted (\\ and \" escaped);
  double → str; else str."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    :else (str v)))

(defn emit
  "Render the kotoba Datom-log EDN text (trailing newline) for the referral registry."
  ([referrals] (emit referrals 1))
  ([referrals tx]
   (let [L (transient [])]
     (conj! L ";; chigiri 契 — GENERATED kotoba Datom log (ADR-2605262700). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; G8/G14: a REFERRAL registry projection — routing to LICENSED human counsel,")
     (conj! L ";; NEVER advice. :verification-status preserved verbatim (all :unverified-seed).")
     (conj! L "[")
     (doseq [r referrals]
       (let [eid (reg/entity-id (get r ":referral/id"))]
         (doseq [a reg/referral-attrs :when (and (contains? r a) (some? (get r a)))]
           (conj! L (str "[" (fmt eid) " " a " " (fmt (get r a)) " " tx " :add]")))))
     (conj! L "]")
     (conj! L (str ";; referrals=" (count referrals)))
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     "CLI: project the registry → out/chigiri-datoms.kotoba.edn (file I/O at the edge)."
     [& args]
     (let [referrals (reg/load-referrals)
           tx (if (and (seq args) (re-matches #"\d+" (first args)))
                (Long/parseLong (first args)) 1)
           text (emit referrals tx)
           out-dir (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                       (clojure.java.io/file "out"))]
       (.mkdirs out-dir)
       (spit (clojure.java.io/file out-dir "chigiri-datoms.kotoba.edn") text)
       (println (str "chigiri datoms → out/chigiri-datoms.kotoba.edn ("
                     (count referrals) " referrals, tx=" tx ")")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))

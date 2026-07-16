#!/usr/bin/env bb
;; junkan 循環 — datom index queries (EAVT / AVET / VAET arrangements over findings).
(ns junkan.methods.query
  "query.cljc — read-only Datalog-style queries over junkan's findings datoms,
  realizing the kotoba-kqe arrangement model the ADR specifies (ADR-2605290927 +
  ADR-2605262130: EAVT / AEVT / AVET / VAET indexes over content-addressed datoms).

  The datoms are the [op entity attr value] vectors emitted by analyze.cljc
  (op = \":db/add\"; entity/attr/value as strings). These helpers are the read
  PATH over them — the same questions the ADR lists as representative queries:
    - EAVT  'all attributes of entity E'           → entity / value-of
    - AVET  'all entities where attr = value'       → by-av (e.g. regime = :vicious)
    - VAET  'which entities reference value V'       → referencing
  plus governance-specific convenience queries.

  Read-only (G4): no mutation, no I/O, no outward channel — pure functions over a
  datom vector. Returns plain data for humans / other actors to consume."
  (:require [clojure.string :as str]))

(defn- e [d] (nth d 1))
(defn- a [d] (nth d 2))
(defn- v [d] (nth d 3))

;; ── EAVT — all attributes of one entity ──────────────────────────────────────
(defn entity
  "EAVT: map of {attr → value} for entity `ent`."
  [datoms ent]
  (reduce (fn [m d] (if (= ent (e d)) (assoc m (a d) (v d)) m)) {} datoms))

(defn value-of
  "EAVT point lookup: the value of `attr` on `ent` (or nil)."
  [datoms ent attr]
  (some (fn [d] (when (and (= ent (e d)) (= attr (a d))) (v d))) datoms))

;; ── AVET — all entities where attr = value ───────────────────────────────────
(defn by-av
  "AVET: distinct entities whose `attr` equals `val`."
  [datoms attr val]
  (->> datoms (filter #(and (= attr (a %)) (= val (v %)))) (map e) distinct vec))

;; ── VAET — which entities/attrs reference a value ────────────────────────────
(defn referencing
  "VAET-style: [[entity attr] …] pairs whose value = `val`."
  [datoms val]
  (->> datoms (filter #(= val (v %))) (map (juxt e a)) distinct vec))

;; ── governance-specific convenience queries ──────────────────────────────────
(defn instruments-in
  "All instrument entities in a given jurisdiction (e.g. \"RU\")."
  [datoms jurisdiction]
  (by-av datoms ":junkan.gov.instr/jurisdiction" jurisdiction))

(defn instruments-by-polarity
  "All instrument entities with a given polarity (e.g. \":widen\")."
  [datoms polarity]
  (by-av datoms ":junkan.gov.instr/polarity" polarity))

(defn stocks-by-regime
  "All stock entities currently reading a given regime (e.g. \":vicious\")."
  [datoms regime]
  (by-av datoms ":junkan.gov.stock/regime" regime))

(defn vicious-stocks [datoms] (stocks-by-regime datoms ":vicious"))

(defn loops-including-stock
  "Loop entities whose dominant stock = `stock-kw` (e.g. \":information-asymmetry\")."
  [datoms stock-kw]
  (by-av datoms ":junkan.gov.loop/dominant-stock" stock-kw))

(defn jurisdictions
  "Distinct jurisdictions present in the datoms."
  [datoms]
  (->> datoms (filter #(= ":junkan.gov.instr/jurisdiction" (a %))) (map v) distinct sort vec))

(defn instruments-by-stock
  "All instrument entities feeding a given asymmetry stock (e.g. \":economic-capture\")."
  [datoms stock]
  (by-av datoms ":junkan.gov.instr/stock" stock))

(defn instruments-by-kind
  "All instrument entities of a given kind (e.g. \":law\" / \":doctrine\" / \":value\")."
  [datoms kind]
  (by-av datoms ":junkan.gov.instr/kind" kind))

(defn enactor-of
  "WHO established a given instrument entity (誰が定めたか) — straight from the datoms."
  [datoms ent]
  (value-of datoms ent ":junkan.gov.instr/enactor"))

(defn origin-of
  "The CIRCUMSTANCES (経緯) of a given instrument's establishment, from the datoms."
  [datoms ent]
  (value-of datoms ent ":junkan.gov.instr/origin"))

(defn summary
  "A small read-only digest assembled purely from the datoms (no recompute)."
  [datoms]
  {:instruments (count (by-av datoms ":junkan/derived" true)) ;; superset; see counts below
   :jurisdictions (count (jurisdictions datoms))
   :vicious-stocks (vicious-stocks datoms)
   :widen-instruments (count (instruments-by-polarity datoms ":widen"))
   :narrow-instruments (count (instruments-by-polarity datoms ":narrow"))})

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
           jur (second args)]
       (require 'junkan.methods.junkan-edn 'junkan.methods.analyze)
       (let [je (resolve 'junkan.methods.junkan-edn/instruments)
             az-analyze (resolve 'junkan.methods.analyze/analyze)
             az-datoms (resolve 'junkan.methods.analyze/datoms)
             is (je seed)
             ds (az-datoms is (az-analyze is))]
         (println "jurisdictions:" (jurisdictions ds))
         (println "vicious stocks:" (vicious-stocks ds))
         (when jur
           (println (str "instruments in " jur ":") (instruments-in ds jur)))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))

(ns saisei.methods.datom-emit
  "saisei 再生 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).

  GROUND (durable, op :add) — the coded procedure/jurisdiction registries and the
  member's situations (synthetic at R0). DERIVED (transient, :bond/is-transient
  true) — filing plans, computed on READ and never stored as ground state (G2).

  House style: ':…' strings stay strings; pure fn + #?(:clj) I/O edge."
  (:require [clojure.string :as str]
            [saisei.methods.filing-plan :as plan]))

(def sit-attrs [":sit/label" ":sit/jurisdiction" ":sit/sourcing"])
(def proc-attrs [":proc/label" ":proc/jurisdiction" ":proc/track" ":proc/verify-current-law"])

(defn- fmt [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":") v
                    (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    :else (str v)))

(defn emit
  ([] (emit 1))
  ([tx]
   (let [situations (plan/load-situations)
         procs (plan/load-procs)
         ps (plan/plans situations procs)
         L (transient [])]
     (conj! L ";; saisei 再生 — GENERATED kotoba Datom log (ADR-2607061800). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (G2).")
     (conj! L "")
     (conj! L ";; ── GROUND: registries (disclosed shapes)")
     (doseq [p procs a proc-attrs :when (contains? p a)]
       (conj! L (str "[" (fmt (get p ":proc/id")) " " a " " (fmt (get p a)) " " tx " :add]")))
     (conj! L "")
     (conj! L ";; ── GROUND: member situations (synthetic at R0 — G1)")
     (doseq [s situations a sit-attrs :when (contains? s a)]
       (conj! L (str "[" (fmt (get s ":sit/id")) " " a " " (fmt (get s a)) " " tx " :add]")))
     (conj! L "")
     (conj! L ";; ── DERIVED (transient — plans computed on read, G2)")
     (doseq [p ps]
       (let [eid (fmt (str "plan:" (get p "situation")))]
         (conj! L (str "[" eid " :bond/is-transient true " tx " :add]"))
         (conj! L (str "[" eid " :saisei/status " (get p "status") " " tx " :add]"))
         (conj! L (str "[" eid " :saisei/tracks " (count (get p "tracks")) " " tx " :add]"))))
     (conj! L "")
     (conj! L (str ";; situations=" (count situations) " plans=" (count ps)))
     (str (str/join "\n" (persistent! L)) "\n"))))

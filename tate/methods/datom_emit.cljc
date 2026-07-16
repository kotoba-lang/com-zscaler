(ns tate.methods.datom-emit
  "tate 盾 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  1:1 Clojure port of `methods/datom_emit.py`.

  GROUND (durable, op :add) — the member's docs/notices (synthetic at R0) and the coded
  registries. DERIVED (transient, :bond/is-transient true) — clause flags + plan status,
  computed on READ and never stored as ground state (G2).

  House style: ':…' strings stay strings; fmt-g for {v:g}; pure fn + #?(:clj) I/O edge."
  (:require [clojure.string :as str]
            [tate.methods.terms-scan :as terms]
            [tate.methods.respond-plan :as respond]))

(def doc-attrs [":doc/label" ":doc/jurisdiction" ":doc/context" ":doc/sourcing"])
(def notice-attrs [":notice/label" ":notice/jurisdiction" ":notice/channel"
                   ":notice/claim-jpy" ":notice/claim-amount" ":notice/claim-currency"
                   ":notice/sourcing"])
(def clause-attrs [":clause/label" ":clause/jurisdiction" ":clause/context" ":clause/risk"
                   ":clause/anchor" ":clause/route" ":clause/verify-current-law"])
(def proc-attrs [":proc/label" ":proc/jurisdiction" ":proc/verify-current-law"])

(defn- fmt-g
  "Python f'{v:g}' for a double."
  [v]
  #?(:clj (let [s (String/format java.util.Locale/ROOT "%g" (object-array [(double v)]))]
            ;; %g leaves trailing zeros / trailing dot; Python :g strips them.
            (if (str/includes? s ".")
              (let [t (str/replace s #"0+$" "")] (if (str/ends-with? t ".") (subs t 0 (dec (count t))) t))
              s))
     :cljs (str v)))

(defn- fmt [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":") v
                    (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (and (number? v) (not (integer? v)) (float? v)) (fmt-g v)
    :else (str v)))

(defn emit
  ([] (emit 1))
  ([tx]
   (let [[docs notices] (terms/load-docs)
         patterns (terms/load-patterns)
         procs (respond/load-procs)
         res (terms/scan docs patterns)
         ps (respond/plans notices procs)
         L (transient [])]
     (conj! L ";; tate 盾 — GENERATED kotoba Datom log (ADR-2606112301). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (G2).")
     (conj! L "")
     (conj! L ";; ── GROUND: registries (disclosed shapes)")
     (doseq [p patterns a clause-attrs :when (contains? p a)]
       (conj! L (str "[" (fmt (get p ":clause/id")) " " a " " (fmt (get p a)) " " tx " :add]")))
     (doseq [p procs a proc-attrs :when (contains? p a)]
       (conj! L (str "[" (fmt (get p ":proc/id")) " " a " " (fmt (get p a)) " " tx " :add]")))
     (conj! L "")
     (conj! L ";; ── GROUND: member docs/notices (synthetic at R0 — G1)")
     (doseq [d docs a doc-attrs :when (contains? d a)]
       (conj! L (str "[" (fmt (get d ":doc/id")) " " a " " (fmt (get d a)) " " tx " :add]")))
     (doseq [n notices a notice-attrs :when (contains? n a)]
       (conj! L (str "[" (fmt (get n ":notice/id")) " " a " " (fmt (get n a)) " " tx " :add]")))
     (conj! L "")
     (conj! L ";; ── DERIVED (transient — flags/plans computed on read, G2)")
     (doseq [[i f] (map-indexed vector (get res "flags"))]
       (let [eid (fmt (format "flag:%03d" i))]
         (conj! L (str "[" eid " :bond/is-transient true " tx " :add]"))
         (conj! L (str "[" eid " :tate/doc " (fmt (get f "doc")) " " tx " :add]"))
         (conj! L (str "[" eid " :tate/clause " (fmt (get f "clause")) " " tx " :add]"))
         (conj! L (str "[" eid " :tate/risk " (get f "risk") " " tx " :add]"))
         (conj! L (str "[" eid " :tate/route " (get f "route") " " tx " :add]"))))
     (doseq [p ps]
       (let [eid (fmt (str "plan:" (get p "notice")))]
         (conj! L (str "[" eid " :bond/is-transient true " tx " :add]"))
         (conj! L (str "[" eid " :tate/status " (get p "status") " " tx " :add]"))
         (conj! L (str "[" eid " :tate/options " (count (get p "options")) " " tx " :add]"))))
     (conj! L "")
     (conj! L (str ";; flags=" (count (get res "flags")) " plans=" (count ps)))
     (str (str/join "\n" (persistent! L)) "\n"))))

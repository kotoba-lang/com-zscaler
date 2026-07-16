(ns post-quantum-compat.methods.datom-emit
  "post_quantum-compat — kotoba Datom-log emitter (canonical EAVT state,
  ADR-2605312345).

  1:1 Clojure port of `methods/datom_emit.py`. Projects the pqh-v1 migration
  registry into append-only kotoba Datoms [e a v tx op]. Two strata
  (inochi pattern, ADR-2606073000):

    GROUND (durable, op :add) — one datom per (layer, attribute, value) and per
      suite component: the migration state IS the Datom log.

    DERIVED (transient, :pq/is-transient true) — the coverage readout
      (migrated-fraction etc.) is computed on READ and emitted in a flagged
      block so a reader never mistakes it for persisted state.

  Pure stdlib. Keywords are kept as \":ns/name\" strings to mirror Python."
  (:require [clojure.string :as str]
            [post-quantum-compat.methods.suite :as suite]))

;; LAYER_ATTRS — emit order for the durable scalar layer attributes.
(def LAYER-ATTRS suite/LAYER-ATTRS)

(defn- fmt
  "Mirror Python datom_emit._fmt. Booleans → true/false, nil → nil,
  \":\"-prefixed strings emit bare, other strings quoted+escaped, floats via
  Python %g semantics, vectors space-joined in brackets, ints/longs as-is."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (and (number? v) (or (float? v) (double? v)))
    ;; Python f"{v:g}" — strip trailing zeros, drop the dot if integral.
    (let [g (format "%g" (double v))
          g (if (str/includes? g ".")
              (-> g (str/replace #"0+$" "") (str/replace #"\.$" ""))
              g)]
      g)
    (sequential? v) (str "[" (str/join " " (map fmt v)) "]")
    :else (str v)))

(defn emit
  "Render the full EAVT datom log as a string. Defaults tx=1."
  ([] (emit 1))
  ([tx]
   (let [L (transient [])
         add! #(conj! L %)]
     (add! ";; post_quantum-compat — GENERATED kotoba Datom log (ADR-2606111300). DO NOT hand-edit.")
     (add! ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (add! ";; GROUND op :add = durable. DERIVED :pq/is-transient = computed on read.")
     (add! "")
     (doseq [layer suite/LAYERS]
       (let [e (get layer ":layer/id")]
         (doseq [a LAYER-ATTRS]
           (when (contains? layer a)
             (add! (str "[" e " " a " " (fmt (get layer a)) " " tx " :add]"))))
         (when (contains? layer ":layer/pr")
           (add! (str "[" e " :layer/pr " (fmt (get layer ":layer/pr")) " " tx " :add]")))))
     (add! "")
     (doseq [[sid suite-m] suite/SUITES]
       (doseq [[a v] suite-m]
         (if (map? v)
           (doseq [[ka kv] v]
             (add! (str "[" sid " " ka " " (fmt kv) " " tx " :add]")))
           (add! (str "[" sid " " a " " (fmt v) " " tx " :add]")))))
     (add! "")
     (add! ";; ── DERIVED (transient — recompute on read, do not persist) ──")
     (let [cov (suite/coverage-report)]
       (doseq [[a v] cov]
         (add! (str "[:pq/coverage " a " " (fmt v) " " tx " :add] ;; :pq/is-transient true"))))
     (str (str/join "\n" (persistent! L)) "\n"))))

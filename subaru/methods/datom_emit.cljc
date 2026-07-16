(ns subaru.methods.datom-emit
  "subaru 昴 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345). 1:1 Clojure port
  of methods/datom_emit.py (ADR-2606162355).

  GROUND (durable, op :add) — node + 縁 datoms. DERIVED (transient, :bond/is-transient true) —
  coverage reach / min link margin / deorbit debt, computed on read (N1).
  G1: no DPI / user-location / targeting-relay attribute is emitted (none exists; check-g1 runs)."
  (:require [clojure.string :as str]
            [subaru.methods.link-budget :as core]
            [subaru.methods.coverage :as coverage]
            [subaru.methods.stewardship :as stewardship]))

(def node-attrs [":organism/kind" ":organism/label" ":organism/sourcing"
                 ":constellation/sat-count" ":constellation/open-bus"
                 ":bus/power-w" ":bus/darksat" ":shell/regime" ":shell/alt-band-km"
                 ":link/kind" ":link/band" ":link/eirp-dbw" ":link/path-loss-db"
                 ":link/gt-dbk" ":link/required-cn-db" ":gs/role"
                 ":area/region" ":area/unconnected" ":area/disaster"
                 ":entitlement/kind" ":disposal/method" ":disposal/deorbit-debt"])
(def edge-attrs [":en/from" ":en/to" ":en/kind" ":en/coverage-pct" ":organism/sourcing"])

(defn- fmt [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (string? v) (if (str/starts-with? v ":") v
                    (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    (and (number? v) (not (integer? v)))
    (let [d (double v)] (if (== d (Math/rint d)) (str (long d)) (str d)))
    :else (str v)))

(defn- fmtg
  "Mirror Python f\"{v:g}\": integers print bare; non-integers print 6 significant digits with
  trailing zeros stripped."
  [v]
  (let [d (double v)]
    (if (== d (Math/rint d))
      (str (long d))
      (-> (format "%.6g" d) (str/replace #"0+$" "") (str/replace #"\.$" "")))))
(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn emit
  ([nodes edges] (emit nodes edges 1))
  ([nodes edges tx]
   (core/check-g1 nodes)
   (let [lb (core/link-budget nodes edges)
         cov (coverage/coverage nodes edges)
         stw (stewardship/stewardship nodes edges)
         L (transient [])]
     (conj! L ";; subaru 昴 — GENERATED kotoba Datom log (ADR-2606162355). DO NOT hand-edit.")
     (conj! L ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].")
     (conj! L ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1).")
     (conj! L ";; G1: connectivity commons — no DPI / user-location / targeting-relay attribute exists.")
     (conj! L "[")
     (doseq [nid (core/node-ids nodes)]
       (let [n (get nodes nid)]
         (doseq [a node-attrs]
           (when (and (contains? n a) (some? (get n a)))
             (conj! L (str "[" (fmt nid) " " a " " (fmt (get n a)) " " tx " :add]"))))))
     (doseq [e edges]
       (let [eid (str "en." (get e ":en/from") "." (lstrip-colon (get e ":en/kind"))
                      "." (get e ":en/to"))]
         (doseq [a edge-attrs]
           (when (and (contains? e a) (some? (get e a)))
             (conj! L (str "[" (fmt eid) " " a " " (fmt (get e a)) " " tx " :add]"))))))
     (conj! L ";; ── DERIVED readouts (transient; computed on read) ──")
     (let [cid "con.constellation.subaru"]
       (conj! L (str "[" (fmt cid) " :bond/ss-reach " (fmtg (:ss_reach cov)) " " tx " :derived] ;; :bond/is-transient true"))
       (conj! L (str "[" (fmt cid) " :bond/min-link-margin-db " (fmtg (:min_margin_db lb)) " " tx " :derived] ;; :bond/is-transient true"))
       (conj! L (str "[" (fmt cid) " :bond/deorbit-debt " (fmtg (:total_deorbit_debt stw)) " " tx " :derived] ;; :bond/is-transient true")))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (clojure.java.io/file (or (System/getenv "SUBARU_ACTOR_DIR") "20-actors/subaru"))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-constellation.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           tx (if (some #{"--tx"} argv) (Long/parseLong (nth argv (inc (.indexOf argv "--tx")))) 1)
           {:keys [nodes edges]} (core/load-file* seed)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "constellation-datoms.kotoba.edn") (emit nodes edges tx))
       (println (str "subaru datom log → "
                     (clojure.java.io/file outdir "constellation-datoms.kotoba.edn")
                     " (" (count nodes) " nodes + " (count edges) " 縁, tx=" tx ")"))
       0)))

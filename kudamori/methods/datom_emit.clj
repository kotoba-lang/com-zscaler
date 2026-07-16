;; kudamori 管守 — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
;;
;; Projects the sewer graph into append-only kotoba Datoms [e a v tx op].
;;   GROUND (op :add, durable) — node / segment / robot node datoms + entry-gas reading
;;     + cleans 縁. This IS the Datom log.
;;   DERIVED (op :derived, transient :bond/is-transient true) — entry-permitted gate,
;;     route hops, debris removed, water effluent; computed on READ, NOT persisted
;;     (N1/G2 pattern, mirrors asobi/kuramori datom_emit).
;;
;; `emit` projects the base `run` result; `emit-day` projects the FULL run-day pipeline
;; (inspection grades / campaign tour / atmosphere / root-cut / reline / effluent handoff
;; 縁) so the canonical log captures the whole day, not just the base clean.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142030 (kudamori R0).
(ns kudamori.methods.datom-emit
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kudamori.methods.analyze :as az]))

(defn fmt
  "Format a value as an EDN Datom field: keywords bare, strings quoted, bools/nil literal."
  [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (nil? v) "nil"
    (keyword? v) (str v)
    (string? v) (if (str/starts-with? v ":")
                  v
                  (str \" (str/escape v {\\ "\\\\" \" "\\\""}) \"))
    (float? v) (format "%g" v)
    :else (str v)))

(defn datom [e a v tx op]
  (str "[" (fmt e) " " (str a) " " (fmt v) " " tx " " (str op) "]"))

(defn- base-lines
  "Inner GROUND + DERIVED datom lines for the base `run` result (no header/brackets).
   Byte-identical to the R0 emitter body."
  [seed res tx]
  (let [gated? (= :gated (:jetting res))]
    (concat
     ;; GROUND — nodes (manholes)
     (mapcat (fn [n] [(datom (:id n) :kuda.node/kind (:kind n) tx :add)
                      (datom (:id n) :kuda.node/access (boolean (:access n)) tx :add)])
             (:nodes seed))
     ;; GROUND — pipe segments (+ from/to 縁)
     (mapcat (fn [s] [(datom (:id s) :kuda.pipe/id-mm (:id-mm s 0) tx :add)
                      (datom (:id s) :kuda.pipe/material (:material s) tx :add)
                      (datom (:id s) :kuda.pipe/length-m (double (:length-m s 0)) tx :add)
                      (datom (:id s) :kuda.pipe/blocked (boolean (:blocked? s)) tx :add)
                      (datom (:id s) :kuda.pipe/from (:from s) tx :add)
                      (datom (:id s) :kuda.pipe/to (:to s) tx :add)])
             (:segments seed))
     ;; GROUND — robot (crawler)
     (let [r (:robot seed)]
       [(datom (:id r) :kuda.robot/kind (:kind r) tx :add)
        (datom (:id r) :kuda.robot/od-mm (:od-mm r 0) tx :add)])
     ;; GROUND — entry-manhole gas reading (the safety record)
     (let [g (:gas-reading seed)]
       [(datom (:node g) :kuda.node/o2-pct (double (:o2-pct g 0)) tx :add)
        (datom (:node g) :kuda.node/h2s-ppm (double (:h2s-ppm g 0)) tx :add)
        (datom (:node g) :kuda.node/ch4-lel (double (:ch4-lel g 0)) tx :add)
        (datom (:node g) :kuda.node/co-ppm (double (:co-ppm g 0)) tx :add)])
     ;; GROUND — cleans 縁 (robot → target segment), only when entry was permitted
     (when-not gated?
       (let [r (:robot seed)
             tgt (get-in res [:navigation :target])
             en (str "en." (:id r) ".cleans." tgt)]
         [(datom en :en/from (:id r) tx :add)
          (datom en :en/to tgt tx :add)
          (datom en :en/kind :cleans tx :add)]))
     ;; DERIVED — transient readouts (computed on read; not durable)
     [";; ── DERIVED readouts (transient; computed on read) ──"]
     (let [fid (:id (:facility seed))]
       (cons
        (datom fid :bond/entry-permitted (get-in res [:entry :permitted?]) tx :derived)
        (if gated?
          [(datom fid :bond/jetting-gated true tx :derived)]
          [(datom fid :bond/route-hops (get-in res [:navigation :hops]) tx :derived)
           (datom fid :bond/debris-removed-m3
                  (double (get-in res [:jetting :debris-removed-m3])) tx :derived)
           (datom fid :bond/effluent-l
                  (double (get-in res [:jetting :water :effluent-l])) tx :derived)]))))))

(defn- day-lines
  "Inner datom lines for the FULL run-day pipeline artifacts — so the canonical log
   captures the whole day (inspection grades, campaign tour, atmosphere entry-permit,
   jetting debris, reline liner) not just the base clean. GROUND :add for operations
   + the cross-actor handoff 縁 (kudamori→mizuho effluent); DERIVED for metrics.
   Guards every missing :day key."
  [res tx]
  (let [day (:day res)]
    (concat
     [";; ── run-day operations (GROUND) ──"]
     ;; effluent handoff 縁 (kudamori → mizuho), same shape as kuramori's handoff 縁
     (mapcat (fn [h]
               (let [e (str "en.handoff." (:from-actor h) "." (:to-actor h) "." (:id h))]
                 [(datom e :handoff/from-actor (:from-actor h) tx :add)
                  (datom e :handoff/to-actor (:to-actor h) tx :add)
                  (datom e :handoff/kind (:kind h) tx :add)]))
             (:handoff day))
     ;; inspection grades (worst-first survey: per-segment structural grade + blockage-risk)
     (mapcat (fn [g]
               [(datom (:segment-id g) :kuda.inspect/grade (:grade g) tx :add)
                (datom (:segment-id g) :kuda.inspect/blockage-risk
                       (double (:blockage-risk g)) tx :add)])
             (:inspection day))
     ;; campaign tour stops (each confined-space entry re-checks atmosphere, G5)
     (mapcat (fn [s]
               [(datom (:segment-id s) :kuda.campaign/priority
                       (double (:priority s)) tx :add)
                (datom (:segment-id s) :kuda.campaign/atmosphere-recheck-required
                       (boolean (:atmosphere-recheck-required s)) tx :add)])
             (get-in day [:campaign :stops]))
     ;; atmosphere entry-permit at the campaign gate (the G5 safety record)
     (when-let [a (:atmosphere day)]
       [(datom "facility" :kuda.atmosphere/entry-permitted
               (boolean (:permitted? a)) tx :add)])
     ;; reline liner design (trenchless CIPP, honest-refusal gate cleared)
     (when-let [rl (:relining day)]
       [(datom "facility" :kuda.reline/liner-thickness-mm
               (double (:liner-thickness-mm rl)) tx :add)])
     ;; DERIVED day metrics (computed on read; not durable)
     [";; ── run-day metrics (DERIVED) ──"]
     (when-let [c (:campaign day)]
       [(datom "facility" :bond/campaign-travel-m (double (:travel-m c)) tx :derived)])
     (when-let [rc (:rootcut day)]
       [(datom "facility" :bond/rootcut-passes (:passes-needed rc) tx :derived)])
     [(datom "facility" :bond/inspection-segments (count (:inspection day)) tx :derived)])))

(defn- wrap [lines]
  (str ";; kudamori 管守 — GENERATED kotoba Datom log (ADR-2606142030). DO NOT hand-edit.\n"
       ";; Canonical EAVT state (ADR-2605312345). [e a v tx op].\n"
       ";; GROUND op :add = durable. DERIVED :bond/is-transient = computed on read (N1/G2).\n"
       "[\n" (str/join "\n" (remove nil? lines)) "\n]\n"))

(defn emit
  "Emit the sewer Datom log as an EDN string. `seed` is the loaded map,
   `res` the analyze/run result, `tx` the transaction number. Output unchanged from R0."
  [seed res tx]
  (wrap (base-lines seed res tx)))

(defn emit-day
  "Emit the FULL day Datom log — base GROUND/DERIVED PLUS the run-day pipeline
   artifacts (inspection/campaign/atmosphere/reline/rootcut + the kudamori→mizuho
   effluent handoff 縁). `day-res` = az/run-day."
  [seed day-res tx]
  (wrap (concat (base-lines seed day-res tx) (day-lines day-res tx))))

(defn -main [& args]
  (let [path (or (first args) "20-actors/kudamori/data/network.edn")
        seed (az/load-seed path)
        day-res (az/run-day seed)]
    (print (emit-day seed day-res 1))
    (flush)))

(ns itonami.methods.ingest
  "itonami 営み — R3 SCADA/OT scan-cycle ingest adapter (ADR-2606082300).
  1:1 Clojure port of `methods/ingest.py`.

  Folds a kotoba-os plc-host-runner Datom stream (scan-cycle = Datom transaction) into
  itonami :tick/* observations, which analyze / optimize / inspect then consume.

    parse-scan-datoms  — read a recorded stream of raw [e a v tx op] scan datoms
    fold-to-ticks      — EAVT fold → reconstruct scan-report entities → aggregate reports
                         sharing (:scan/station, :scan/t) into one interval tick. Wh→kWh.
                         Tick state = MOST-SEVERE report state (:down > :idle > :run).
    to-tick-edn        — render ticks back as a :tick/* EDN seed (round-trips into analyze)

  CONSTITUTIONAL: G6 OFFLINE REPLAY ONLY (ingest-live refuses); G1 read-only (no PLC write-back);
  G2 station/PLC scale (a stream with :worker/:person/:operator is rejected).

  House style: Python ':…' keyword strings stay strings; string-keyed data; pure fns;
  file I/O only at #?(:clj) edges. Reuses itonami.methods.analyze (read-edn)."
  (:require [clojure.string :as str]
            [itonami.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

(def ^:private state-severity {":run" 0 ":idle" 1 ":down" 2})
(def ^:private forbidden-attr-prefixes [":worker" ":person" ":operator" ":face" ":biometric"])

(defn parse-scan-datoms
  "Return the list of [e a v tx op] scan datoms from a recorded kotoba-os stream."
  [text]
  (let [forms (analyze/read-edn text)]
    (vec (filter (fn [f]
                   (and (vector? f) (= (count f) 5)
                        (string? (nth f 1)) (str/starts-with? (nth f 1) ":scan/")))
                 forms))))

(defn- guard-no-person
  [datoms]
  (doseq [d datoms]
    (let [a (nth d 1)]
      (doseq [bad forbidden-attr-prefixes]
        (when (and (string? a) (str/starts-with? a bad))
          (throw (ex-info (str "G2 violation: scan stream carries a person/worker attr " (pr-str a))
                          {:attr a})))))))

(defn- to-long
  "Python int(m.get(k, 0) or 0) — truncation toward zero; nil/0 → 0."
  [v]
  (let [v (if (or (nil? v) (false? v)) 0 v)]
    (long (cond (number? v) (if (double? v) (long v) v)
                :else (Long/parseLong (str v))))))

(defn- to-double
  [v]
  (let [v (if (or (nil? v) (false? v)) 0 v)]
    (double (if (number? v) v (Double/parseDouble (str v))))))

(defn fold-to-ticks
  "EAVT-fold raw scan datoms into per-(station,t) interval ticks."
  [datoms]
  (guard-no-person datoms)
  ;; 1) EAVT fold: entity -> {attr value}, last write (by tx order) wins; :retract removes.
  ;;    Python sorted() is STABLE; sort-by is stable here too.
  (let [ordered (sort-by #(nth % 3) datoms)
        ;; track entity first-touch order to mirror Python defaultdict
        [ent ent-order]
        (reduce (fn [[ent order] d]
                  (let [e (nth d 0) a (nth d 1) v (nth d 2) op (nth d 4)
                        seen? (contains? ent e)
                        order (if seen? order (conj order e))
                        m (get ent e {})
                        m (if (= op ":retract") (dissoc m a) (assoc m a v))]
                    [(assoc ent e m) order]))
                [{} []] ordered)
        ;; 2) aggregate reconstructed report entities by (station, t), insertion order
        [agg agg-order]
        (reduce (fn [[agg order] e]
                  (let [m (get ent e)
                        st (get m ":scan/station")
                        t (get m ":scan/t")]
                    (if (or (nil? st) (nil? t))
                      [agg order]
                      (let [k [st t]
                            seen? (contains? agg k)
                            order (if seen? order (conj order k))
                            g (get agg k {"good" 0 "scrap" 0 "energy_wh" 0.0 "dt_s" 0.0 "states" []})
                            g (-> g
                                  (update "good" + (to-long (get m ":scan/good" 0)))
                                  (update "scrap" + (to-long (get m ":scan/scrap" 0)))
                                  (update "energy_wh" + (to-double (get m ":scan/energy-wh" 0)))
                                  (update "dt_s" + (to-double (get m ":scan/dt-s" 0)))
                                  (update "states" conj (get m ":scan/state" ":run")))]
                        [(assoc agg k g) order]))))
                [{} []] ent-order)
        ;; 3) build ticks (sorted by (str station, t)); state = most-severe; Wh→kWh
        sorted-keys (sort-by (fn [[st t]] [(str st) t]) agg-order)]
    (mapv (fn [[st t :as k]]
            (let [g (get agg k)
                  states (get g "states")
                  state (reduce (fn [best s]
                                  (if (> (get state-severity s 0) (get state-severity best 0)) s best))
                                (first states) (rest states))
                  good (long (get g "good"))
                  scrap (long (get g "scrap"))]
              {":tick/station" st ":tick/t" t ":tick/state" state
               ":tick/cycles" (+ good scrap) ":tick/good" good ":tick/scrap" scrap
               ":tick/kwh" (/ (get g "energy_wh") 1000.0)
               ":tick/interval-s" (get g "dt_s")}))
          sorted-keys)))

(defn- fmt-g
  [v]
  (let [d (double v)]
    (if (and (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn- fmt-v
  "Port of ingest._fmt: ':…' literal; other string quoted; float {:g}; else str()."
  [v]
  (cond
    (string? v) (if (str/starts-with? v ":") v (str "\"" v "\""))
    (double? v) (fmt-g v)
    :else (str v)))

(def ^:private tick-keys
  [":tick/station" ":tick/t" ":tick/state" ":tick/cycles"
   ":tick/good" ":tick/scrap" ":tick/kwh" ":tick/interval-s"])

(defn to-tick-edn
  "Render ticks as a :tick/* EDN seed (round-trips into analyze)."
  [ticks]
  (let [L (transient
           [";; itonami — GENERATED :tick/* observations folded from a kotoba-os scan-cycle stream."
            ";; Source: offline Datom replay (G6). DO NOT hand-edit."
            "["])]
    (doseq [tk ticks]
      (let [pairs (str/join " " (for [k tick-keys :when (contains? tk k)]
                                  (str k " " (fmt-v (get tk k)))))]
        (conj! L (str "{" pairs "}"))))
    (conj! L "]")
    (str (str/join "\n" (persistent! L)) "\n")))

(defn ingest-live
  "LIVE OT ingest — gated by construction (G6)."
  [& _]
  (throw (ex-info (str "G6: live OT ingest (Modbus/OPC-UA/EtherCAT via kotoba-os device worlds) "
                       "requires Council + operator DID. itonami R3 supports OFFLINE replay of a "
                       "recorded scan-cycle Datom stream only; there is no live socket and no PLC "
                       "write-back (no-server-key).")
                  {:gate :G6})))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           stream (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                    (io/file (first argv))
                    (io/file here "data" "seed-scancycle-stream.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           datoms (parse-scan-datoms (slurp stream))
           ticks (fold-to-ticks datoms)]
       (.mkdirs outdir)
       (spit (io/file outdir "ingested-ticks.kotoba.edn") (to-tick-edn ticks))
       0)))

(ns ake.methods.contributor
  "contributor.cljc — 朱 (ake) anti-vandalism rate + Wellbecoming trajectory (G9).
  ADR-2606052100. Clojure port of `methods/contributor.py`.

  G9 has two halves, and the SECOND is the charter-sensitive one:

    1. a per-DID RATE LIMIT over a sliding window (a vandal cannot flood the membrane); and
    2. a Wellbecoming ACCEPTANCE TRAJECTORY — an `as-of` history of accepted/refused outcomes,
       NEVER a punitive score-of-soul (kizashi G8). The defining property: throttling is
       **recoverable** — a contributor who starts proposing well again is un-throttled by their
       own next accepted edit. There is no permanent ban, no minted reputation number, no
       ranking of contributors against each other.

  All state is an append-only event list; every helper RETURNS A NEW map (never mutates) so the
  trajectory only ever grows, mirroring the Datom-log discipline of revision.cljc. Time (`now`,
  `as-of`) is passed in — deterministic and testable, no wall-clock.

  Convention (root CLAUDE.md): trajectory keys are DID strings; event maps + the trajectory view
  use \"string\" keys (\"outcome\", \"as_of\", \"accepted\", …) matching the Python dicts so the
  analyze stack + report read on the same shape. Pure fns; no I/O. closed-vocab → ex-info."
  (:require [clojure.string :as str]))

;; defaults — a window and a flood ceiling; both generous (this throttles vandalism, not zeal)
(def RATE-WINDOW 3600)          ;; seconds
(def RATE-MAX-IN-WINDOW 20)     ;; proposals per DID per window
(def THROTTLE-RECENT 5)         ;; look at the last N outcomes
(def THROTTLE-REFUSED-RUN 5)    ;; …throttle only if ALL of the last N were refused (a clear run)

(def ^:private ACCEPT "accepted")
(def ^:private REFUSE "refused")

(defn empty*
  "A fresh trajectory store: {did -> [event, ...]}, event = {\"outcome\" \"as_of\"}."
  []
  {})

(defn record
  "Append one outcome event for `did`. Returns a NEW map (append-only, never mutates)."
  [traj did outcome as-of]
  (let [outcome (-> (str outcome)
                    (str/replace #"^:+" ""))]
    (when-not (or (= outcome ACCEPT) (= outcome REFUSE))
      (throw (ex-info (str "outcome must be '" ACCEPT "' or '" REFUSE "', not '" outcome "'")
                      {:outcome outcome})))
    (update traj did (fnil conj []) {"outcome" outcome "as_of" (long as-of)})))

(defn events
  "Events for `did` sorted by as_of (the as-of order, regardless of record order)."
  [traj did]
  (vec (sort-by (fn [e] (long (get e "as_of"))) (get traj did []))))

(defn counts
  "{\"accepted\" n \"refused\" m} for `did`."
  [traj did]
  (let [evs (get traj did [])
        acc (count (filter #(= (get % "outcome") ACCEPT) evs))
        ref (count (filter #(= (get % "outcome") REFUSE) evs))]
    {"accepted" acc "refused" ref}))

(defn acceptance-rate
  "round(accepted / total, 4), or nil if no events (Python None)."
  [traj did]
  (let [c (counts traj did)
        total (+ (get c "accepted") (get c "refused"))]
    (if (zero? total)
      nil
      ;; round(x, 4) — HALF_EVEN over the exact double, mirroring Python round()
      #?(:clj (-> (java.math.BigDecimal. (double (/ (get c "accepted") total)))
                  (.setScale 4 java.math.RoundingMode/HALF_EVEN)
                  (.doubleValue))
         :cljs (/ (js/Math.round (* (/ (get c "accepted") total) 10000)) 10000)))))

(defn rate-ok
  "True if `did` may submit one more proposal at `now` without exceeding the flood ceiling."
  ([traj did now] (rate-ok traj did now RATE-WINDOW RATE-MAX-IN-WINDOW))
  ([traj did now window max-in-window]
   (let [recent (filter (fn [e] (> (long (get e "as_of")) (- now window)))
                        (get traj did []))]
     (< (count recent) max-in-window))))

(defn throttled?
  "Throttled ⟺ the last `recent` outcomes exist and are an unbroken run of refusals.

  RECOVERABLE by construction: a single accepted edit anywhere in the recent window breaks the
  run, so a contributor un-throttles themselves by proposing well again. This is a behavioural
  signal, NOT a stored score and NOT permanent (G9 — no score-of-soul)."
  ([traj did] (throttled? traj did THROTTLE-RECENT THROTTLE-REFUSED-RUN))
  ([traj did recent refused-run]
   (let [evs (events traj did)]
     (if (< (count evs) recent)
       false
       (let [tail (subvec evs (- (count evs) recent))]
         (and (>= (count tail) refused-run)
              (every? #(= (get % "outcome") REFUSE) tail)))))))

(defn trajectory
  "A Wellbecoming view (as-of), NOT a ranking: counts + rate + current throttle state."
  [traj did]
  (let [c (counts traj did)]
    {"did" did
     "accepted" (get c "accepted")
     "refused" (get c "refused")
     "acceptanceRate" (acceptance-rate traj did)
     "throttled" (throttled? traj did)
     "events" (count (get traj did []))}))

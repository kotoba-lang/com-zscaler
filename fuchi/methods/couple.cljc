(ns fuchi.methods.couple
  "couple.cljc — 扶持 (fuchi) R1(d): Displacement-Dividend cohort coupling. 1:1 Clojure port
  of `methods/couple.py` (ADR-2606052300 + ADR-2606032130 G2).

  A displacing actor's surplus → donation → TitheRouter 10% split → per-cohort Public-Fund
  EARMARK (a budget ceiling for that cohort's in-kind sustenance). G2 coupling gate: no live
  displacement without a funded cohort — admissible iff the earmark is FUNDED and the
  committed in-kind floor is ≤ the earmark.

  House style: ':…' strings stay strings; pure fns; exact integer split; gates → ex-info.
  Portable .cljc."
  (:require [fuchi.methods.live-gate :as live-gate]))

(def TITHE-BPS 1000)  ;; 10% TitheRouter split (basis points of 10_000)

(defn make-displacement-event
  [{:keys [displacing-actor cohort-id displaced-count surplus-usd-micros-yr funded]
    :or {funded false}}]
  (when (< surplus-usd-micros-yr 0)
    (throw (ex-info "surplus cannot be negative" {})))
  (when (< displaced-count 0)
    (throw (ex-info "displaced_count cannot be negative" {})))
  {:displacing-actor displacing-actor :cohort-id cohort-id
   :displaced-count (long displaced-count) :surplus-usd-micros-yr (long surplus-usd-micros-yr)
   :funded (boolean funded)})

(defn make-cohort-earmark
  [{:keys [cohort-id displacing-actor gross-usd-micros-yr tithe-usd-micros
           earmark-usd-micros-yr funded]}]
  (when (not= (+ tithe-usd-micros earmark-usd-micros-yr) gross-usd-micros-yr)
    (throw (ex-info "TitheRouter split INVARIANT: gross must equal tithe + earmark exactly" {})))
  {:cohort-id cohort-id :displacing-actor displacing-actor
   :gross-usd-micros-yr gross-usd-micros-yr :tithe-usd-micros tithe-usd-micros
   :earmark-usd-micros-yr earmark-usd-micros-yr :funded (boolean funded)})

(defn earmark-from-surplus
  "Apply the 10% TitheRouter split → a per-cohort earmark. gross = tithe + earmark (exact)."
  [event]
  (let [gross (long (:surplus-usd-micros-yr event))
        tithe (quot (* gross TITHE-BPS) 10000)
        earmark (- gross tithe)]
    (make-cohort-earmark
     {:cohort-id (:cohort-id event) :displacing-actor (:displacing-actor event)
      :gross-usd-micros-yr gross :tithe-usd-micros tithe
      :earmark-usd-micros-yr earmark :funded (boolean (:funded event))})))

(defn coupling-gate
  "G2 coupling gate — is this displacement admissible? Admissible iff the earmark is FUNDED
  and the committed in-kind floor is within it."
  [event earmark committed-floor-usd-micros-yr]
  (let [committed (long committed-floor-usd-micros-yr)]
    (cond
      (not (:funded earmark))
      {"event" (:displacing-actor event) "cohort" (:cohort-id event)
       "committed" committed "headroom" 0 "admissible" false
       "reason" (str "G2: no funded cohort earmark — displacement REFUSED "
                     "(surplus→donation has not landed in the Public Fund)")}
      (> committed (:earmark-usd-micros-yr earmark))
      {"event" (:displacing-actor event) "cohort" (:cohort-id event)
       "committed" committed "headroom" (- (:earmark-usd-micros-yr earmark) committed)
       "admissible" false
       "reason" (str "G2: committed sustenance " committed " exceeds funded earmark "
                     (:earmark-usd-micros-yr earmark) " — displacement REFUSED "
                     "(cannot shed toil faster than the cohort can be sustained)")}
      :else
      {"event" (:displacing-actor event) "cohort" (:cohort-id event)
       "committed" committed "headroom" (- (:earmark-usd-micros-yr earmark) committed)
       "admissible" true
       "reason" "G2: funded cohort earmark covers the committed sustenance — admissible"})))

(defn events-from-seed
  [records]
  (mapv
   (fn [r]
     (make-displacement-event
      {:displacing-actor (get r ":event/displacing-actor" "?")
       :cohort-id (get r ":event/cohort-id" "?")
       :displaced-count (long (get r ":event/displaced-count" 0))
       :surplus-usd-micros-yr (long (get r ":event/surplus-usd-micros-yr" 0))
       :funded (boolean (get r ":event/funded" false))}))
   records))

(defn commit-live
  "Bind a displacement to its funded cohort earmark (LIVE). require-gate passes (R2 autonomous);
  the G2 coupling-gate still raises if not funded or over-committed."
  [event earmark committed-floor-usd-micros-yr gate & {:keys [env]}]
  (live-gate/require-gate gate env)
  (let [g (coupling-gate event earmark committed-floor-usd-micros-yr)]
    (when-not (get g "admissible")
      (throw (ex-info (get g "reason") {})))
    {:cohort-id (:cohort-id earmark) :displacing-actor (:displacing-actor earmark)
     :committed-usd-micros-yr (long committed-floor-usd-micros-yr)
     :operator-did (:operator-did gate) :council-level (:council-level gate)
     :member-signature (:member-signature gate) :admissible true}))

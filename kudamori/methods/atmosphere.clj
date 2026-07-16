;; kudamori 管守 — confined-space atmosphere entry gate (THE headline safety gate, ★ G5).
;;
;; A foul-sewer headspace is the documented killer: O2 displacement + H2S + CH4 + CO.
;; This is the gate that removes a human from the confined space — entry on an unsafe
;; reading is UNREPRESENTABLE: `entry-permitted?` returns false and `assert-entry!`
;; RAISES. A purge-to-entry model (forced ventilation) drives concentrations toward the
;; threshold over time, mirroring niyaku/kamado purge-to-entry discipline.
;;
;; Thresholds (industrial confined-space norm):
;;   O2   safe 19.5 % .. 23.5 %      (below = asphyxiation, above = enriched/fire)
;;   H2S  < 10 ppm                   (硫化水素)
;;   CH4  < 10 %LEL                  (methane lower-explosive-limit fraction)
;;   CO   < 35 ppm                   (一酸化炭素)
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable. Pure compute;
;; it gates no real entry (G1 no-server-key / R0 design+sim).
;; Per ADR-2606142030 (kudamori R0). Clojure-first (the GAP-actor wave).
(ns kudamori.methods.atmosphere)

;; ── safe-atmosphere thresholds ───────────────────────────────────────────────
(def ^:const o2-min-pct 19.5)
(def ^:const o2-max-pct 23.5)
(def ^:const h2s-max-ppm 10.0)
(def ^:const ch4-max-lel 10.0)
(def ^:const co-max-ppm 35.0)

(defn- field [reading k default]
  (let [v (get reading k default)] (if (number? v) (double v) default)))

(defn hazards
  "Return the seq of {:gas :value :limit :kind} for every reading that BREACHES a
   confined-space threshold. Empty seq = a passing atmosphere. Pure inspection."
  [reading]
  (let [o2  (field reading :o2-pct 0.0)
        h2s (field reading :h2s-ppm 999.0)
        ch4 (field reading :ch4-lel 999.0)
        co  (field reading :co-ppm 999.0)]
    (cond-> []
      (< o2 o2-min-pct)  (conj {:gas :o2  :value o2  :limit o2-min-pct :kind :oxygen-deficient})
      (> o2 o2-max-pct)  (conj {:gas :o2  :value o2  :limit o2-max-pct :kind :oxygen-enriched})
      (>= h2s h2s-max-ppm) (conj {:gas :h2s :value h2s :limit h2s-max-ppm :kind :toxic})
      (>= ch4 ch4-max-lel) (conj {:gas :ch4 :value ch4 :limit ch4-max-lel :kind :flammable})
      (>= co  co-max-ppm)  (conj {:gas :co  :value co  :limit co-max-ppm  :kind :toxic}))))

(defn entry-permitted?
  "True iff EVERY gas is within its safe band. NEVER true on an unsafe reading."
  [reading]
  (empty? (hazards reading)))

(defn assert-entry!
  "Return the reading if entry is permitted; RAISE otherwise (★ G5 — an unsafe
   atmosphere refuses entry; entry without a passing atmosphere is unrepresentable)."
  [reading]
  (let [hz (hazards reading)]
    (when (seq hz)
      (throw (ex-info "confined-space entry refused: unsafe atmosphere (G5)"
                      {:reading reading :hazards hz})))
    reading))

;; ── purge-to-entry (forced ventilation) ──────────────────────────────────────
;; Well-mixed dilution: each air change scales every CONTAMINANT toward 0 by a fixed
;; fraction; O2 is restored toward fresh-air 20.9 % from whichever side it sits on.
(def ^:const fresh-o2-pct 20.9)

(defn- purge-step [reading air-changes]
  (let [decay (Math/exp (- (max 0.0 (double air-changes))))   ; e^{-N} → 0 over changes
        o2 (field reading :o2-pct 0.0)]
    {:o2-pct  (+ fresh-o2-pct (* (- o2 fresh-o2-pct) decay))   ; relax toward fresh air
     :h2s-ppm (* (field reading :h2s-ppm 0.0) decay)
     :ch4-lel (* (field reading :ch4-lel 0.0) decay)
     :co-ppm  (* (field reading :co-ppm 0.0) decay)}))

(defn purge-to-entry
  "Forced-ventilation model. Given an initial `reading`, the blower's
   `air-changes-per-min`, and a `max-min` ventilation budget, step minute-by-minute
   until the atmosphere passes (`entry-permitted?`) or the budget is exhausted.
   Returns {:entry-permitted? bool :minutes n :reading <post-purge> :hazards […]}.
   NEVER reports :entry-permitted? true unless the post-purge reading actually passes."
  [reading air-changes-per-min max-min]
  (loop [m 0]
    (let [post (purge-step reading (* air-changes-per-min m))]
      (cond
        (entry-permitted? post) {:entry-permitted? true  :minutes m :reading post :hazards []}
        (>= m max-min)          {:entry-permitted? false :minutes m :reading post
                                 :hazards (hazards post)}
        :else                   (recur (inc m))))))

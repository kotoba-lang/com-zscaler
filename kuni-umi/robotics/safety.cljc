(ns kuni-umi.robotics.safety
  "safety — cross-cutting structural gates for infra-robotics. 1:1 cljc port of
  `robotics/safety.py`.

  These are *structural* gates: a forbidden intent raises (`ex-info` with
  `:safety/error true`) before any motion is planned, never returns a value the
  caller could ignore. The software embodiment of the constitutional invariants —
  NOT a substitute for the certified IEC 61508/61511 parallel safety PLC."
  (:require [clojure.string :as str]))

(def MIN-WITNESS-SIGS
  "Constitutional witness quorum (G8, ADR-2605201400)."
  2)

(def FORBIDDEN-USES
  "N1 (Mission Charter §1.12): weaponisation / covert force can never be energised."
  #{"weapon" "directed-energy" "munition" "fire-control"
    "interdiction" "covert-force" "surveillance-targeting"})

(defn- safety-error [msg]
  (throw (ex-info msg {:safety/error true})))

(defn assert-civilian
  "Closed-world civilian-use gate (N1). Raise unless `use` is explicitly permitted.
  Port of `assert_civilian`. `permitted` is the domain's civilian allowlist."
  [use permitted]
  (cond
    (contains? FORBIDDEN-USES use)
    (safety-error (str "N1: use " (pr-str use) " is a forbidden-force use and can never be "
                       "energised (Mission Charter §1.12 constitutional invariant)"))
    (not (contains? (set permitted) use))
    (safety-error (str "N1: use " (pr-str use) " is not in the civilian allowlist "
                       (pr-str permitted) "; closed-world refusal (only explicitly-permitted "
                       "civilian uses run)"))
    :else nil))

(defn require-member-signature
  "No-server-key gate (G15/G7, ADR-2605231525). Raise unless a member/operator
  signs and the platform holds no key. Port of `require_member_signature`."
  ([member-sig] (require-member-signature member-sig ""))
  ([member-sig server-sig]
   (cond
     (and server-sig (not (str/blank? server-sig)))
     (safety-error (str "G15/G7 violation: a server/platform signature was supplied; the "
                        "platform holds no key and never signs actuation (ADR-2605231525)"))
     (or (nil? member-sig) (str/blank? member-sig))
     (safety-error (str "G15/G7 violation: a member/operator signature is required to "
                        "authorise any actuation (no-server-key)"))
     :else nil)))

(defn witness-quorum-ok
  "Witness quorum ≥2 independent robot DIDs (G8); N<2 or duplicates rejected.
  Returns a map (does not raise). Port of `witness_quorum_ok`."
  [witness-sigs]
  (cond
    (< (count witness-sigs) MIN-WITNESS-SIGS)
    {:ok false
     :reason (str "witness quorum " (count witness-sigs) " < " MIN-WITNESS-SIGS " (G8 constitutional)")
     :escalate-council-lv6 true}
    (< (count (distinct witness-sigs)) MIN-WITNESS-SIGS)
    {:ok false
     :reason "duplicate witness DIDs detected (G8)"
     :escalate-council-lv6 true}
    :else
    {:ok true :reason "witness quorum satisfied"}))

;; ── SafetyEnvelope ────────────────────────────────────────────────

(defn safety-envelope
  "A motion safety envelope for an install/service robot. Port of the
  `SafetyEnvelope` dataclass defaults."
  [& {:keys [max-joint-speed human-proximity-speed max-reach]
      :or   {max-joint-speed 1.0 human-proximity-speed 0.25
             max-reach Double/POSITIVE_INFINITY}}]
  {:max-joint-speed (double max-joint-speed)
   :human-proximity-speed (double human-proximity-speed)
   :max-reach (double max-reach)})

(defn check-trajectory
  "Validate a joint-space trajectory. Returns {:ok bool :violations [...]}.
  Port of `SafetyEnvelope.check_trajectory`. Each element of `trajectory` is a
  joint-configuration vector; per-step joint rate |Δq|/dt must stay under the
  applicable ceiling (the lower human-proximity ceiling when `human-present`)."
  [{:keys [max-joint-speed human-proximity-speed]} trajectory dt
   & {:keys [human-present] :or {human-present false}}]
  (let [ceiling (if human-present human-proximity-speed max-joint-speed)
        dt (double dt)
        violations
        (reduce
         (fn [vs i]
           (let [prev (nth trajectory (dec i))
                 cur  (nth trajectory i)]
             (if (not= (count prev) (count cur))
               (conj vs (str "step " i ": joint-count mismatch"))
               (reduce
                (fn [vs2 j]
                  (let [a (nth prev j) b (nth cur j)
                        rate (if (> dt 0) (/ (Math/abs (double (- b a))) dt)
                                 Double/POSITIVE_INFINITY)]
                    (if (> rate (+ ceiling 1e-9))
                      (conj vs2 (str "step " i " joint " j ": rate "
                                     (format "%.4f" rate) " > ceiling "
                                     (format "%.4f" ceiling)
                                     (if human-present " (human present)" "")))
                      vs2)))
                vs
                (range (count prev))))))
         []
         (range 1 (count trajectory)))]
    {:ok (empty? violations) :violations violations}))

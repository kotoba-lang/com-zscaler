(ns noroshi.methods.reliability-qual
  "noroshi (烽) packaged-photonic-module reliability-qualification PASS/FAIL engine —
  the packaging face's qualification core (ADR-2606051600, matures the
  `reliability_qual` cell from a pure `.edn` scaffold to real, tested logic).

  GR-468 SHAPE ONLY (G1 open-standard vocabulary, per manifest.edn's E3 tier note
  and this cell's own original docstring: \"using the Telcordia GR-468 test SHAPE
  only ... names as neutral vocabulary\"). Test CATEGORY names (thermal cycling,
  damp heat, mechanical shock, fibre pull) and the PASS/FAIL judgment STRUCTURE are
  drawn from the public Telcordia GR-468-CORE vocabulary. Every numeric acceptance
  threshold in `default-suite` is REPRESENTATIVE — assembled from commonly-
  published engineering literature (datasheets / app notes), NOT a verified
  citation to the licensed standard's clause text (G10 sourcing-honesty). An
  operator qualifying a real device MUST replace `default-suite` with the actual
  licensed GR-468-CORE thresholds for their device category before this judgment
  carries any real regulatory/customer-facing weight.

  No live chamber, no live test (G8 outward-gated — no chamber exists at R0):
  every judge-* function evaluates CALLER-SUPPLIED test results (from a prior
  physical run, or a paper exercise), never drives hardware. This mirrors
  `methods/active-alignment`'s `enable-laser` gate shape (raise ex-info exactly
  at the violated criterion) but for post-hoc acceptance judgment rather than a
  pre-actuation refusal.

  House style: kebab keyword keys; pure fns; no I/O; closed-vocab violations are
  data (a violations vector), not exceptions — a qualification FAILING a test is
  an ordinary outcome to report, not an error to throw. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── representative GR-468-SHAPE acceptance criteria (G10: NOT verified citations) ──
(def default-suite
  "test-type -> representative acceptance criteria. :representative true on every
  entry (G10) — an honest marker that these are NOT verbatim GR-468-CORE clause
  thresholds."
  {:thermal-cycling  {:low-temp-c -40.0 :high-temp-c 85.0 :min-cycles 500
                      :max-param-drift-pct 10.0 :representative true}
   :damp-heat        {:temp-c 85.0 :rh-pct 85.0 :min-hours 500.0
                      :max-param-drift-pct 10.0 :representative true}
   :mechanical-shock {:min-peak-g 1500.0 :duration-ms 0.5 :duration-tol-pct 20.0
                      :min-pulses 5 :representative true}
   :fibre-pull       {:min-force-n 5.0 :min-hold-s 5.0 :representative true}})

(def test-types
  "The closed vocabulary of GR-468-SHAPE test categories this engine judges."
  (set (keys default-suite)))

(defn- violation-list
  "Build a vector of violation-reason strings from [fail? reason] pairs, dropping
  any pair whose condition is false."
  [& pairs]
  (vec (keep (fn [[fail? reason]] (when fail? reason)) (partition 2 pairs))))

;; ── per-test PASS/FAIL judgment (pure; over submitted results, never live I/O) ──
(defn judge-thermal-cycling
  "result: {:cycles-completed n :achieved-low-temp-c :achieved-high-temp-c
  :param-drift-pct}. Returns {:pass? bool :violations [..]}."
  [criteria result]
  (let [{:keys [low-temp-c high-temp-c min-cycles max-param-drift-pct]} criteria
        {:keys [cycles-completed achieved-low-temp-c achieved-high-temp-c param-drift-pct]} result
        vs (violation-list
            (< cycles-completed min-cycles)
            (str "cycles-completed " cycles-completed " < required " min-cycles)
            (> achieved-low-temp-c low-temp-c)
            (str "achieved low temp " achieved-low-temp-c "°C did not reach required " low-temp-c "°C")
            (< achieved-high-temp-c high-temp-c)
            (str "achieved high temp " achieved-high-temp-c "°C did not reach required " high-temp-c "°C")
            (> (Math/abs (double param-drift-pct)) max-param-drift-pct)
            (str "parametric drift " param-drift-pct "% exceeds " max-param-drift-pct "%"))]
    {:pass? (empty? vs) :violations vs}))

(defn judge-damp-heat
  "result: {:hours-completed :achieved-temp-c :achieved-rh-pct :param-drift-pct}."
  [criteria result]
  (let [{:keys [temp-c rh-pct min-hours max-param-drift-pct]} criteria
        {:keys [hours-completed achieved-temp-c achieved-rh-pct param-drift-pct]} result
        vs (violation-list
            (< hours-completed min-hours)
            (str "hours-completed " hours-completed " < required " min-hours)
            (< achieved-temp-c temp-c)
            (str "achieved temp " achieved-temp-c "°C did not reach required " temp-c "°C")
            (< achieved-rh-pct rh-pct)
            (str "achieved RH " achieved-rh-pct "% did not reach required " rh-pct "%")
            (> (Math/abs (double param-drift-pct)) max-param-drift-pct)
            (str "parametric drift " param-drift-pct "% exceeds " max-param-drift-pct "%"))]
    {:pass? (empty? vs) :violations vs}))

(defn judge-mechanical-shock
  "result: {:pulses-completed :achieved-peak-g :achieved-duration-ms :functional-after?}."
  [criteria result]
  (let [{:keys [min-peak-g duration-ms duration-tol-pct min-pulses]} criteria
        {:keys [pulses-completed achieved-peak-g achieved-duration-ms functional-after?]} result
        dur-lo (* duration-ms (- 1.0 (/ duration-tol-pct 100.0)))
        dur-hi (* duration-ms (+ 1.0 (/ duration-tol-pct 100.0)))
        vs (violation-list
            (< pulses-completed min-pulses)
            (str "pulses-completed " pulses-completed " < required " min-pulses)
            (< achieved-peak-g min-peak-g)
            (str "achieved peak " achieved-peak-g "g did not reach required " min-peak-g "g")
            (or (< achieved-duration-ms dur-lo) (> achieved-duration-ms dur-hi))
            (str "achieved pulse duration " achieved-duration-ms "ms outside "
                 duration-tol-pct "% tolerance of " duration-ms "ms")
            (not functional-after?)
            "device non-functional after shock")]
    {:pass? (empty? vs) :violations vs}))

(defn judge-fibre-pull
  "result: {:applied-force-n :held-s :delaminated?}."
  [criteria result]
  (let [{:keys [min-force-n min-hold-s]} criteria
        {:keys [applied-force-n held-s delaminated?]} result
        vs (violation-list
            (< applied-force-n min-force-n)
            (str "applied force " applied-force-n "N < required " min-force-n "N")
            (< held-s min-hold-s)
            (str "held " held-s "s < required " min-hold-s "s")
            delaminated?
            "fibre delaminated under pull test")]
    {:pass? (empty? vs) :violations vs}))

(defn judge-one
  "Dispatch to the right judge-* fn for test-type. Raises on an unknown test-type
  (a closed-vocab violation, not a PASS/FAIL outcome)."
  [test-type criteria result]
  (case test-type
    :thermal-cycling  (judge-thermal-cycling criteria result)
    :damp-heat        (judge-damp-heat criteria result)
    :mechanical-shock (judge-mechanical-shock criteria result)
    :fibre-pull       (judge-fibre-pull criteria result)
    (throw (ex-info (str "unknown GR-468-SHAPE test type " (pr-str test-type)
                         " — not in the closed vocabulary " test-types)
                    {:noroshi/violation :unknown-test-type :test-type test-type}))))

(defn judge-suite
  "Judge `selected-tests` (a subset of test-types) against `results` (test-type ->
  result-map). A selected test with no submitted result is reported as FAILED with
  a :not-submitted violation — never silently passed (G10: no fabricated
  coverage). Returns {:overall-pass? bool :per-test {test-type {...}}}."
  ([results] (judge-suite test-types default-suite results))
  ([selected-tests results] (judge-suite selected-tests default-suite results))
  ([selected-tests suite results]
   (let [judged (into {}
                       (for [tt selected-tests]
                         [tt (if-let [r (get results tt)]
                               (judge-one tt (get suite tt) r)
                               {:pass? false :violations ["not-submitted"]})]))]
     {:overall-pass? (and (seq judged) (every? :pass? (vals judged)))
      :per-test judged})))

;; ── qual-plan record (the kotoba :qual/* datom shape) ────────────────────────
(defn qual-plan
  "Build the qual-plan record for a judged suite. dry-run is always true at R0
  (G8 — no live chamber exists)."
  [id device-id selected-tests judgment]
  {:id id
   :device-id device-id
   :suite (vec selected-tests)
   :acceptance (if (:overall-pass? judgment) :pass :fail)
   :dry-run true
   :representative true
   :judgment judgment})

;; ── report (honest R0 framing marker for test_governance's scan) ────────────
(defn report
  "Render an offline reliability-qualification report against `default-suite`,
  demonstrating one PASS and one FAIL device."
  []
  (let [pass-results {:thermal-cycling {:cycles-completed 600 :achieved-low-temp-c -42.0
                                        :achieved-high-temp-c 87.0 :param-drift-pct 2.0}
                      :damp-heat {:hours-completed 550.0 :achieved-temp-c 85.0
                                  :achieved-rh-pct 85.0 :param-drift-pct 1.5}
                      :mechanical-shock {:pulses-completed 6 :achieved-peak-g 1550.0
                                        :achieved-duration-ms 0.52 :functional-after? true}
                      :fibre-pull {:applied-force-n 6.0 :held-s 6.0 :delaminated? false}}
        fail-results (assoc-in pass-results [:thermal-cycling :cycles-completed] 100)
        pass-j (judge-suite test-types pass-results)
        fail-j (judge-suite test-types fail-results)
        lines ["# noroshi 烽 — reliability qualification (Telcordia GR-468 SHAPE only)"
               ""
               "> Test CATEGORY names + PASS/FAIL structure are the open GR-468 vocabulary (G1)."
               "> Every numeric threshold below is REPRESENTATIVE (G10) — not a verified citation"
               "> to the licensed standard text. No live chamber exists at R0 (G8)."
               ""
               "## representative acceptance criteria (default-suite)"]
        lines (into lines (for [tt (sort test-types)] (str "- " (name tt) ": " (get default-suite tt))))
        lines (into lines
                    ["" "## example device — all four tests pass"
                     (str "- overall: " (if (:overall-pass? pass-j) "PASS" "FAIL"))
                     "" "## example device — thermal cycling under-ran (fails)"
                     (str "- overall: " (if (:overall-pass? fail-j) "PASS" "FAIL"))
                     (str "- thermal-cycling violations: "
                          (str/join "; " (get-in fail-j [:per-test :thermal-cycling :violations])))
                     ""
                     "> R0 design-only; :representative parameters; no live chamber test (G8-gated)."])]
    (str/join "\n" lines)))

#?(:clj
   (defn -main
     "CLI entry: print the offline reliability-qualification report."
     [& _argv]
     (println (report))
     0))

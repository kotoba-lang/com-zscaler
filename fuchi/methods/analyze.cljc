(ns fuchi.methods.analyze
  "analyze.cljc — 扶持 (fuchi) end-to-end allocation membrane over the :representative seed.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606052300; R0 + R1 a/b/c/d + R1-live).

  Runs each seed maintainer through the full pipeline:
    covenant check → sustenance envelope → tenure-weighted allocation (cash≡0)
                   → in-kind rail decomposition → governance gate
                   → [auto | sbt-vote (R1b real 1 SBT=1 vote + 48h timelock) | council-lv7 | refused]
                   → R1a provisioning intents → R1c toritate booking + kanae flow graph
                   → R1d Displacement-Dividend cohort coupling + G2 gate → R1-live gate status

  and emits an offline scorecard (Markdown) + derived datoms (edn). NO live disbursement — dry-run.

  House style: ':…' strings stay strings; pure fns; file I/O only at #?(:clj) edges;
  the give-only gate (equity/debt/ROI/exit unrepresentable, cash≡0) is ported 1:1 from the
  sibling modules. Portable .cljc."
  (:require [clojure.string :as str]
            [fuchi.methods.edn :as edn]
            [fuchi.methods.allocate :as allocate]
            [fuchi.methods.route :as route]
            [fuchi.methods.provision :as prov]
            [fuchi.methods.book :as book]
            [fuchi.methods.couple :as couple]
            [fuchi.methods.vote :as vote]
            [fuchi.methods.live-gate :as live-gate]))

(defn- kw* [v]
  (-> (str (or v "")) (#(if (str/starts-with? % ":") (subs % 1) %))
      (str/split #"/") last str/lower-case))

(defn- envelopes-for [seed did]
  (filterv #(= (get % ":envelope/maintainer") did) (get seed ":envelope/batch" [])))

(defn- ballots-for [seed did]
  (or (some #(when (= (get % ":gov/maintainer") did) %) (get seed ":gov/ballots" []))
      (array-map)))

(defn run
  "End-to-end run over the seed map → {:rows :derived :intents :ledger :flows :coupling :live-status}."
  [seed]
  (let [records (get seed ":maintainer/batch" [])
        ceiling (long (get seed ":graph/stage-ceiling-usd-micros-yr" 30000000000))
        now-h (long (get seed ":graph/now-hours" 0))
        cohort (allocate/cohort-from-seed records)
        allocs (into (array-map) (map (fn [a] [(:maintainer-did a) a])
                                      (allocate/allocate cohort ceiling 0 "sustenance")))
        note-of (into (array-map) (map (fn [r] [(get r ":maintainer/did")
                                                (get r ":maintainer/note" "")]) records))
        maintains-of (into (array-map)
                           (map (fn [r] [(get r ":maintainer/did")
                                         (str/join " " (or (get r ":maintainer/maintains") []))])
                                records))]
    (loop [rs records, as-of 3000
           rows [] derived [] intents [] ledger [] flows [] in-kind-by-actor (array-map)]
      (if (empty? rs)
        ;; R1(d) coupling + R1(live) gate status
        (let [coupling (mapv
                        (fn [ev]
                          (let [em (couple/earmark-from-surplus ev)
                                committed (get in-kind-by-actor (:displacing-actor ev) 0)
                                g (couple/coupling-gate ev em committed)]
                            {"earmark" em "gate" g}))
                        (couple/events-from-seed (get seed ":cohort/displacement" [])))
              live-status (mapv (fn [leg]
                                  (live-gate/gate-status (live-gate/make-live-gate {:leg leg}) (array-map)))
                                (keys live-gate/LEG-POLICY))]
          {:rows rows :derived derived :intents intents :ledger ledger
           :flows flows :coupling coupling :live-status live-status})
        (let [r (first rs)
              as-of (+ as-of 10)
              did (get r ":maintainer/did")
              cov (kw* (get r ":maintainer/covenant" ":vowed"))
              env (envelopes-for seed did)
              rails-or-err (try {:rails (route/route-envelope env)}
                                (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) exc
                                  {:err (#?(:clj #(.getMessage %) :cljs ex-message) exc)}))]
          (if (:err rails-or-err)
            (recur (rest rs) as-of
                   (conj rows {"did" did "covenant" cov "route" ":refused-at-intake"
                               "imputed" "-" "in_kind" "-" "outcome" "refused" "note" (:err rails-or-err)})
                   derived intents ledger flows in-kind-by-actor)
            (let [rails (:rails rails-or-err)
                  imputed-total (reduce + 0 (map :imputed-usd-micros-yr rails))
                  coverage (route/in-kind-coverage rails)
                  ctx (str (get note-of did "") " " (get maintains-of did ""))
                  rider (route/rider-hit ctx)
                  inv (route/touches-invariant ctx)
                  route0 (route/gov-route imputed-total inv rider)
                  [outcome route]
                  (cond
                    (= route0 "auto") ["accepted" route0]
                    (= route0 "sbt-vote")
                    (let [v (ballots-for seed did)
                          ballots (vote/ballots-from-seed (get v ":gov/votes" []))
                          t (vote/tally ballots (long (get v ":gov/opened-at-hours" 0)) now-h
                                        (long (get v ":gov/timelock-h" 48)))]
                      [(get t "outcome")
                       (str "sbt-vote " (get t "yes") "-" (get t "no") "/" (get t "timelock_h")
                            "h" (if (get t "finalizable") "✓" "…"))])
                    (= route0 "council-lv7") ["pending" route0]
                    (= route0 "refused") ["refused" route0]
                    :else ["pending" route0])
                  a (get allocs did)
                  rows' (conj rows
                              {"did" did "covenant" cov "route" (str ":" route) "imputed" imputed-total
                               "in_kind" coverage "share" (if a (:share a) 0.0)
                               "rank" (if a (:priority-rank a) "-")
                               "floor" (if a (:floor-usd-micros-yr a) 0)
                               "outcome" outcome "note" (get note-of did "")})]
              (if (and (some? a) (contains? #{"accepted" "pending"} outcome))
                (let [in-kind-imputed (allocate/round-half-even (* imputed-total coverage))
                      acts (let [m (get maintains-of did "")] (if (str/blank? m) [] (str/split m #"\s+")))
                      ikba' (reduce (fn [m act] (update m act (fnil + 0) in-kind-imputed))
                                    in-kind-by-actor acts)
                      these-intents (prov/provision rails (:maintainer-did a))
                      these-ledger (book/book-toritate rails (:maintainer-did a) (:maintainer-did a))
                      these-flows (book/flow-graph rails (:maintainer-did a) (:maintainer-did a))]
                  (recur (rest rs) as-of rows'
                         (conj derived
                               (array-map
                                ":alloc/maintainer" did ":alloc/instrument" (str ":" (:instrument a))
                                ":alloc/share" (:share a) ":alloc/priority-rank" (:priority-rank a)
                                ":alloc/floor-usd-micros-yr" (:floor-usd-micros-yr a)
                                ":alloc/cash-usd-micros" 0 ":alloc/server-held-key" false
                                ":gov/route" (str ":" (first (str/split route #"\s+")))
                                ":gov/outcome" (str ":" outcome)
                                ":rail/coverage-in-kind" coverage
                                ":prov/intents" (count these-intents) ":book/entries" (count these-ledger)
                                ":wb/as-of" as-of))
                         (into intents these-intents)
                         (into ledger these-ledger)
                         (into flows these-flows)
                         ikba'))
                (recur (rest rs) as-of rows' derived intents ledger flows in-kind-by-actor)))))))))

;; ── report rendering (1:1 with _report's f-strings) ─────────────────────────

(defn- round-half-even-long [x]
  #?(:clj (-> (BigDecimal. (double x)) (.setScale 0 java.math.RoundingMode/HALF_EVEN) .longValueExact)
     :cljs (let [f (js/Math.floor x) d (- x f)]
             (cond (< d 0.5) f (> d 0.5) (inc f) :else (if (even? f) f (inc f))))))

(defn- comma-group
  "Group an integer with thousands commas (Python ',' format spec)."
  [n]
  (let [neg (neg? n) s (str (Math/abs (long n)))
        grouped (->> (reverse s)
                     (partition-all 3)
                     (map #(apply str (reverse %)))
                     reverse
                     (str/join ","))]
    (str (when neg "-") grouped)))

(defn- usd-micros->dollar-str
  "Python f\"${v/1_000_000:,.0f}\" — divide by 1e6, round HALF_EVEN to int, comma-group, $-prefix."
  [micros]
  (str "$" (comma-group (round-half-even-long (/ (double micros) 1000000.0)))))

(defn- pct-str
  "Python f\"{ik*100:.0f}%\" — HALF_EVEN to int percent."
  [ik]
  (str (round-half-even-long (* (double ik) 100.0)) "%"))

(defn- float-str
  "Python str(float) — shortest round-trip repr (Java Double.toString matches for these)."
  [x]
  (cond
    (and (number? x) (not (integer? x))) (str (double x))
    :else (str x)))

(defn- last-seg [s] (last (str/split (str s) #":")))

(defn- _m [b] (if b "✓" "✗"))

(defn report
  "Render the allocation dry-run report markdown (1:1 with _report). NOTE: the R2 live_gate
  emits conditions {autonomous_r2_mode true}, so the R1(live) table's per-condition lookups
  (c[\"operator_flag\"] …) reproduce the Python reference's KeyError-equivalent — by design,
  this fn is a faithful port of the merged-R2 reference (which itself crashes there)."
  [res]
  (let [out (transient
             ["# 扶持 (fuchi) — maintainer-sustenance allocation dry-run (R0 + R1 a/b/c)\n"
              (str "The charter-clean INVERSE of an investment fund over the `:representative` seed. "
                   "No equity, no ROI, no exit; **cash≡0** everywhere. No live disbursement / provisioning / "
                   "land grant / binding vote (G10).\n")
              "## Allocation routing + governance\n"
              "| maintainer | covenant | imputed USD/yr | in-kind | share | rank | floor USD/yr | route | outcome |"
              "|---|---|---|---|---|---|---|---|---|"])]
    (doseq [r (:rows res)]
      (let [imp (get r "imputed")
            imp-s (if (integer? imp) (usd-micros->dollar-str imp) imp)
            floor (get r "floor" 0)
            floor-s (if (integer? floor) (usd-micros->dollar-str floor) floor)
            ik (get r "in_kind")
            ik-s (if (float? ik) (pct-str ik) ik)
            did (last-seg (get r "did"))]
        (conj! out (str "| " did " | " (get r "covenant") " | " imp-s " | " ik-s " | "
                        (float-str (get r "share" 0.0)) " | " (get r "rank" "-") " | "
                        floor-s " | " (get r "route") " | " (get r "outcome") " |"))))

    (conj! out "\n## R1(a) provisioning intents → real producing actors (dry-run, published=false)\n")
    (conj! out "| alloc | rail | provider | kind | imputed USD/yr | member-principal |")
    (conj! out "|---|---|---|---|---|---|")
    (doseq [i (:intents res)]
      (conj! out (str "| " (last-seg (:alloc-id i)) " | " (:rail-kind i) " | "
                      (last-seg (:provider-did i)) " | " (:provider-kind i) " | "
                      (usd-micros->dollar-str (:imputed-usd-micros-yr i)) " | "
                      (if (:member-principal i) "✓ (warifu 0%)" "—") " |")))

    (conj! out "\n## R1(c) toritate ledgerEntry booking (cashStipendUsd ≡ 0)\n")
    (conj! out "| alloc | category | imputed USD/yr | cash |")
    (conj! out "|---|---|---|---|")
    (doseq [e (:ledger res)]
      (conj! out (str "| " (last-seg (:alloc-id e)) " | " (:category e) " | "
                      (usd-micros->dollar-str (:imputed-usd-micros-yr e)) " | $"
                      (:cash-usd-micros e) " |")))

    (conj! out "\n## R1(c) kanae-renderable internal sustenance-flow graph\n")
    (conj! out "| from | to | class | imputed USD/yr | in-kind |")
    (conj! out "|---|---|---|---|---|")
    (doseq [f (:flows res)]
      (conj! out (str "| " (last-seg (:frm f)) " | " (last-seg (:to f)) " | " (:flow-class f) " | "
                      (usd-micros->dollar-str (:imputed-usd-micros-yr f)) " | "
                      (if (:in-kind f) "✓" "— (member loan)") " |")))

    (conj! out "\n## R1(d) Displacement-Dividend coupling (G2: no displacement without a funded cohort)\n")
    (conj! out "| cohort | displacing actor | gross USD/yr | tithe 10% | earmark 90% | funded | committed | admissible |")
    (conj! out "|---|---|---|---|---|---|---|---|")
    (doseq [c (:coupling res)]
      (let [em (get c "earmark") g (get c "gate")]
        (conj! out (str "| " (:cohort-id em) " | " (:displacing-actor em) " | "
                        (usd-micros->dollar-str (:gross-usd-micros-yr em)) " | "
                        (usd-micros->dollar-str (:tithe-usd-micros em)) " | "
                        (usd-micros->dollar-str (:earmark-usd-micros-yr em)) " | "
                        (if (:funded em) "✓" "—") " | "
                        (usd-micros->dollar-str (get g "committed")) " | "
                        (if (get g "admissible") "✓ admissible" "✗ REFUSED") " |"))))

    (conj! out "\n## R1(live) outward-leg gate (default = REFUSED; G10)\n")
    (conj! out (str "Each live leg fires only when its operator flag + attestation + Council level + "
                    "member signature are ALL present. In a dry run none are, so every leg is refused.\n"))
    (conj! out "| leg | env flag | min Council | operator flag | attested | ratified | member-signed | admissible |")
    (conj! out "|---|---|---|---|---|---|---|---|")
    (doseq [s (:live-status res)]
      (let [c (get s "conditions")]
        ;; 1:1 with the reference: per-condition lookups on the R2 conditions map.
        (conj! out (str "| " (get s "leg") " | `" (get s "env_flag") "` | Lv" (get s "min_council") " | "
                        (_m (get c "operator_flag")) " | " (_m (get c "operator_attested")) " | "
                        (_m (get c "council_ratified")) " | " (_m (get c "member_signed")) " | "
                        (if (get s "admissible") "✓ admissible" "✗ REFUSED") " |"))))

    (conj! out "\n## Invariants held\n")
    (conj! out "- **cash≡0** — every allocation / provisioning intent / ledgerEntry carries 0 cash (N1).")
    (conj! out "- **no investment vehicle** — instrument ∈ sustenance set; equity/debt/ROI/exit unrepresentable (G1).")
    (conj! out "- **1 SBT = 1 vote + 48h timelock** — escalated allocations finalize only after the window closes (R1b).")
    (conj! out "- **in-kind first** — provisioning routed to real producing actors; external fiat only as member-principal warifu 0% (G3).")
    (conj! out "- **booked, not paid** — toritate cashStipendUsd ≡ 0; no payroll/wage category (R1c).")
    (conj! out (str "- **coupled** — a displacement is admissible only against a funded cohort earmark; "
                    "10% TitheRouter split exact (R1d)."))
    (conj! out (str "- **outward-gated** — every live leg (provision/vote/book/couple) REFUSES unless "
                    "operator flag + attestation + Council Lv6+/Lv7+ + member signature; default = refused (R1-live).\n"))
    (str/join "\n" (persistent! out))))

;; ── EDN emit (1:1 with main's `emit` helper) ─────────────────────────────────

(defn- edn-val [v]
  (cond
    (boolean? v) (str v)                       ;; "true"/"false" (Python str(bool).lower())
    (and (string? v) (not (str/starts-with? v ":"))) (str "\"" v "\"")
    :else (str v)))

(defn emit
  "Mirror main's `emit(records)` — one `  {k v …}` line per map, in insertion order."
  [records]
  (str/join "\n"
            (map (fn [d]
                   (str "  {" (str/join " " (map (fn [[k v]] (str k " " (edn-val v))) d)) "}"))
                 records)))

#?(:clj
   (def ^:private SEED-PATH
     (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
         (clojure.java.io/file "data" "seed-sustenance-graph.kotoba.edn") str)))

#?(:clj
   (defn -main
     "CLI entry: run analyze over the seed, write report + derived EDN files. File I/O at the edge."
     [& argv]
     (let [seed (edn/load-edn SEED-PATH)
           res (run seed)
           here (-> (clojure.java.io/file *file*) .getParentFile)
           outdir (clojure.java.io/file here "out")]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "allocation-dryrun.md") (report res))
       (spit (clojure.java.io/file outdir "allocations.kotoba.edn")
             (str "{:fuchi/derived\n [\n" (emit (:derived res)) "\n ]}\n"))
       (println (str "wrote allocation-dryrun.md + allocations.kotoba.edn → " outdir))
       0)))

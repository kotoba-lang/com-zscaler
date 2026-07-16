(ns tanemaki.methods.analyze
  "tanemaki 種蒔き — edge-primary Public-Fund DD analyzer over the fund-stewardship graph.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606122001).

  Reads a kotoba-EDN stewardship graph (:fs/* nodes + :en/* 縁) and surfaces, per candidate
  ORG: the hard SCREEN findings (charter eligibility), the weighted CRITERION fit (the
  disclosed rubric, weights public + Σ=1.0), evidence coverage, and the ROUTE —
  :excluded | :insufficient-evidence | :propose — routed to a PUBLIC, ADVISORY scorecard,
  never to a funding decision.

  CONSTITUTIONAL (read before any change):
    G1 — steward not sovereign. tanemaki EVALUATES + DRAFTS; it NEVER decides. Every grant is
      decided by 1 SBT = 1 vote (GrantGovernor, ADR-2605192145). The analyzer ENFORCES the
      structural half: an org with ANY :conflicts screen finding can NEVER route to :propose —
      recommend-route throws if the computation would. There is no :fund route at all.
    G2 — no investment instrument. The fund GIVES (grant/milestone-escrow/in-kind); equity/debt/
      ROI vocabulary is unrepresentable (see propose.assert_instrument, mirrors fuchi G1).
    N1 / G4 — edge-primary + public. DD-fit lives ONLY on :meets edges, integrated on READ;
      no stored per-org score. Criteria weights are disclosed and must sum to 1.0 (throws if not).
    N3 / G3 — non-adjudicating. A screen finding / evidence weight is a DISCLOSED public fact
      with a named source, never a verdict on an org's worth.
    G5 — evidence honesty. Coverage below the disclosed floor, or any screen :undetermined /
      unevaluated, routes to :insufficient-evidence — tanemaki never proposes on thin DD.

  House style: Python ':…' keyword strings stay strings (incl. all :fs/* / :en/* attrs); pure
  fns; file I/O only at edges via clojure.java.io. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, "string", num, bool, nil)
;; Mirrors analyze.py's _TOK / _tokens / _atom / _parse faithfully. Keywords are kept as
;; ":ns/name" strings (NOT clojure keywords) so the whole pipeline stays string-keyed,
;; byte-for-byte the same as the Python port.

(def ^:private tok-re
  ;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Lazy seq of significant tokens (group 1 of each tok-re match that captured)."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t)
              (step)
              (cons t (step))))))))))

(defn atom-of
  "Port of _atom: \"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string;
  int → long; else float; else raw string."
  [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= as-dbl ::nan) as-dbl t))))))

(def ^:private end-marker ::end)

(defn- parse-step
  "Consume one form from the token vector at index i. Returns [value next-i] or
  [end-marker next-i] when a closing ] or } is hit (matching _parse's _END sentinel)."
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker)
            [out i]
            (recur i (conj out x)))))

      (= t "{")
      (loop [i i, out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))

      (or (= t "]") (= t "}"))
      [end-marker i]

      :else
      [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches read_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── disclosed constants (the honesty floor + route enum; mirror the schema)
(def COVERAGE-FLOOR 0.6) ;; fraction of criteria needing ≥1 evidence edge before :propose
(def ROUTES [":excluded" ":insufficient-evidence" ":propose"]) ;; NO :fund — funding is a VOTE

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  Insertion order of nodes is preserved (::order metadata) to match Python dict order."
  [forms]
  (let [acc (reduce
             (fn [{:keys [nodes edges order] :as acc} f]
               (cond
                 (not (map? f)) acc
                 (contains? f ":fs/id")
                 (let [nid (get f ":fs/id")]
                   (-> acc
                       (assoc-in [:nodes nid] f)
                       (update :order (fn [o] (if (contains? nodes nid) o (conj o nid))))))
                 (and (contains? f ":en/from") (contains? f ":en/to"))
                 (update acc :edges conj f)
                 :else acc))
             {:nodes {} :edges [] :order []}
             forms)]
    {:nodes (with-meta (:nodes acc) {::order (:order acc)})
     :edges (:edges acc)}))

#?(:clj
   (defn load-file*
     "Read + parse a stewardship EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- node-order
  "First-touch node-id order: ::order metadata if present (load-graph), else (keys nodes)."
  [nodes]
  (or (::order (meta nodes)) (keys nodes)))

(defn- ->float
  "float(x or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [v]
  (if (or (nil? v) (false? v)) 0.0 (double v)))

(defn criteria
  "The disclosed rubric. Throws unless the public weights sum to 1.0 (rubric integrity).
  Returns an ordered map (::order = first-touch node insertion order) of criterion nodes."
  [nodes]
  (let [order (node-order nodes)
        crit-order (filterv #(= ":criterion" (get-in nodes [% ":fs/kind"])) order)
        crit (reduce (fn [m nid] (assoc m nid (get nodes nid))) {} crit-order)
        total (reduce + 0.0 (map #(->float (get % ":criterion/weight")) (vals crit)))]
    (when (> (Math/abs (- total 1.0)) 1e-9)
      (throw (ex-info
              (str "rubric integrity violation: disclosed criterion weights sum to " total
                   ", not 1.0 — a non-normalized rubric is a hidden re-weighting (G4)")
              {:total total})))
    (with-meta crit {::order crit-order})))

(defn- crit-order [crit] (or (::order (meta crit)) (keys crit)))

(defn screen-findings
  "Per-screen DISCLOSED conformance findings for an org (missing screens reported).
  Returns an ordered map keyed by SORTED screen id (matches Python's `sorted(...)`)."
  [org-id nodes edges]
  (let [screens (sort (for [nid (keys nodes)
                            :when (= ":screen" (get-in nodes [nid ":fs/kind"]))]
                        nid))
        findings (reduce (fn [m e]
                           (if (and (= ":screened" (get e ":en/kind"))
                                    (= org-id (get e ":en/from")))
                             (assoc m (get e ":en/to") (get e ":en/finding"))
                             m))
                         {} edges)]
    ;; ordered by sorted screen id; value None (nil) = unevaluated
    (with-meta
      (reduce (fn [m s] (assoc m s (get findings s))) {} screens)
      {::order (vec screens)})))

(defn- sf-order [findings] (or (::order (meta findings)) (keys findings)))

(defn dd-fit
  "Edge-primary fit: Σ weight_c × min(1, Σ incident :meets weight) — computed on READ (N1).
  Returns [fit coverage per evidence] where per/evidence are ordered maps (first-touch)."
  [org-id nodes edges crit]
  (let [;; accumulate per-criterion weight + evidence in first-touch (edge-scan) order
        {:keys [per evidence order]}
        (reduce
         (fn [{:keys [per evidence order] :as acc} e]
           (if (and (= ":meets" (get e ":en/kind"))
                    (= org-id (get e ":en/from"))
                    (contains? crit (get e ":en/to")))
             (let [c (get e ":en/to")
                   seen? (contains? per c)]
               {:per (update per c (fnil + 0.0) (->float (get e ":en/weight")))
                :evidence (update evidence c (fnil conj []) (get e ":en/evidence"))
                :order (if seen? order (conj order c))})
             acc))
         {:per {} :evidence {} :order []}
         edges)
        fit (reduce + 0.0
                    (for [c order]
                      (* (->float (get-in crit [c ":criterion/weight"]))
                         (min 1.0 (get per c)))))
        coverage (if (seq crit) (/ (double (count per)) (count crit)) 0.0)]
    [fit coverage
     (with-meta per {::order order})
     (with-meta evidence {::order order})]))

(defn score-contributions
  "Decompose a candidate's DD fit into PER-CRITERION contributions: weight_c × min(1, evidence_c), the
  amount each rubric criterion adds to the total `dd-fit` score (the fit is exactly the SUM of these).
  It names WHICH criteria drove the score — mission-fit vs openness vs additionality — so a voter can
  SEE why a candidate scored as it did, the transparency the public-weights rubric exists for (G4;
  voters verify the bytes). Advisory detail on the advisory scorecard — a 参考意見 decomposition over
  DISCLOSED weights + evidence, never a verdict on the org's worth (G3) and never the decision (G1 —
  the vote decides, 1 SBT = 1 vote). Takes the rubric `crit` (from `criteria`, whose values carry the
  criterion's weight/code/label) + the `per` evidence map from `dd-fit`; returns
  [criterion contribution weight code label] by contribution descending."
  [crit per]
  (->> crit
       (map (fn [[c node]]
              (let [w (->float (get node ":criterion/weight"))
                    met (min 1.0 (double (get per c 0.0)))]
                [c (* w met) w (get node ":criterion/code") (get node ":fs/label" c)])))
       (sort-by (fn [[_ contrib _ _ _]] (- contrib)))
       vec))

(defn- round6 [v] (/ (Math/rint (* (double v) 1000000.0)) 1000000.0))

(defn recommend-route
  "The lawful route for a candidate org (edge-primary, G1/G5-enforced).

    :excluded              — ANY screen finding is :conflicts (charter screens are structural)
    :insufficient-evidence — any screen :undetermined/unevaluated, OR coverage < floor
    :propose               — draft an ADVISORY proposal for the 1-SBT-1-vote decision
  Throws if a screen-conflicting org would route to :propose (G1 tripwire) — funding is
  NEVER a route tanemaki can emit."
  [org-id nodes edges]
  (let [org (get nodes org-id {})]
    (when (not= ":org" (get org ":fs/kind"))
      (throw (ex-info (str "not an org: " org-id) {:org org-id})))
    (let [crit (criteria nodes)
          findings (screen-findings org-id nodes edges)
          [fit coverage per evidence] (dd-fit org-id nodes edges crit)
          forder (sf-order findings)
          conflicts (vec (sort (for [s forder :when (= ":conflicts" (get findings s))] s)))
          undetermined (vec (sort (for [s forder
                                        :let [f (get findings s)]
                                        :when (or (= ":undetermined" f) (nil? f))] s)))
          route (cond
                  (seq conflicts) ":excluded"
                  (or (seq undetermined) (< coverage COVERAGE-FLOOR)) ":insufficient-evidence"
                  :else ":propose")]
      ;; G1 tripwire — structural, test-covered: a conflicted org must never be proposable
      (when (and (= route ":propose") (seq conflicts))
        (throw (ex-info
                (str "G1 VIOLATION: org " org-id " has screen conflicts " conflicts
                     " but routed to :propose — charter screens are structural, not advisory")
                {:org org-id :conflicts conflicts})))
      (assert (some #{route} ROUTES)) ;; no :fund route exists; funding is the members' vote
      {"org" org-id
       "synthetic" (boolean (get org ":org/synthetic"))
       "route" route
       "screen_findings" findings
       "conflicts" conflicts
       "undetermined" undetermined
       "dd_fit" (round6 fit)
       "evidence_coverage" (round6 coverage)
       "per_criterion" per
       "evidence" evidence})))

(defn analyze
  "All orgs through screens + rubric (transient readouts — N1/G4).
  `orgs` is keyed by SORTED org id (matches Python's `sorted(...)`); `criteria` preserves
  first-touch node order (matches the Python dict comprehension over criteria(nodes))."
  [nodes edges]
  (let [orgs (sort (for [nid (keys nodes)
                         :when (= ":org" (get-in nodes [nid ":fs/kind"]))]
                     nid))
        crit (criteria nodes)
        orgs-map (reduce (fn [m o] (assoc m o (recommend-route o nodes edges))) {} orgs)
        crit-map (reduce (fn [m c] (assoc m c (->float (get-in crit [c ":criterion/weight"]))))
                         {} (crit-order crit))]
    {"orgs" (with-meta orgs-map {::order (vec orgs)})
     "criteria" (with-meta crit-map {::order (crit-order crit)})}))

;; ── report rendering (matches report_md's f-strings) ────────────────────────

(defn- fmt2 [v] (format "%.2f" (double v)))
(defn- fmt3 [v] (format "%.3f" (double v)))

(defn- fmt-pct0
  "Python f'{x:.0%}' — multiply by 100, round, append %."
  [x]
  (str (long (Math/rint (* (double x) 100.0))) "%"))

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- count-kind [nodes k]
  (count (filter #(= k (get % ":fs/kind")) (vals nodes))))

(defn- ordered-keys
  "Keys of a map in ::order metadata if present, else (keys m)."
  [m]
  (or (::order (meta m)) (keys m)))

(defn report-md
  "Render the DD report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [L (transient [])]
    (conj! L "# tanemaki 種蒔き — Public Fund stewardship (DD) report\n")
    (conj! L (str "> **G1 — tanemaki is a STEWARD, never a sovereign.** This scorecard is a 参考意見 "
                  "(advisory): every grant is DECIDED by 1 SBT = 1 vote on the GrantGovernor "
                  "(ADR-2605192145) behind a timelock. **G2 — the Public Fund GIVES, never INVESTS**: "
                  "instruments are grant / milestone-escrow / in-kind only; equity, ROI and every "
                  "investment-return shape are unrepresentable. Screen findings and evidence weights "
                  "are DISCLOSED public facts with named sources (N3), never verdicts on an "
                  "organization's worth. **All orgs in this report are FICTIONAL (G6)** — evaluating "
                  "a real org is a G7-gated live leg from primary disclosure only.\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" (count-kind nodes ":org") " orgs · "
                  (count-kind nodes ":screen") " screens · " (count-kind nodes ":criterion")
                  " criteria · " (count-kind nodes ":source") " sources · "
                  (count-kind nodes ":instrument") " instruments · "
                  (count-kind nodes ":milestone") " milestones) · " (count edges) " 縁\n"))

    (conj! L "\n## The disclosed rubric (public weights, Σ = 1.0)\n")
    (conj! L "| criterion | weight |")
    (conj! L "|---|---:|")
    (let [crit-map (get res "criteria")
          ;; sorted(res["criteria"].items(), key=lambda kv: -kv[1]) — stable on -weight,
          ;; ties keep dict (first-touch) insertion order
          items (->> (ordered-keys crit-map) (map (fn [c] [c (get crit-map c)])))]
      (doseq [[c w] (sort-by (fn [[_ w]] (- (double w))) items)]
        (conj! L (str "| " (get-in nodes [c ":fs/label"] c) " | " (fmt2 w) " |"))))

    (conj! L "\n## Route per candidate org (screens fire BEFORE weighting)\n")
    (conj! L "| org | screens | DD fit | evidence coverage | route |")
    (conj! L "|---|---|---:|---:|---|")
    (doseq [oid (sort (keys (get res "orgs")))]
      (let [r (get-in res ["orgs" oid])
            label (get-in nodes [oid ":fs/label"] oid)
            scr (cond
                  (seq (get r "conflicts"))
                  (str "✗ conflicts: "
                       (str/join ", " (map #(get-in nodes [% ":screen/code"] %) (get r "conflicts"))))
                  (seq (get r "undetermined"))
                  (str "△ undetermined: "
                       (str/join ", " (map #(get-in nodes [% ":screen/code"] %) (get r "undetermined"))))
                  :else "○ all conform")]
        (conj! L (str "| " label " | " scr " | " (fmt3 (get r "dd_fit")) " | "
                      (fmt-pct0 (get r "evidence_coverage")) " | "
                      (lstrip-colon (get r "route")) " |"))))

    (conj! L (str "\n---\n_tanemaki 種蒔き · ADR-2606122001 · steward-not-sovereign · "
                  "non-adjudicating · edge-primary · vote-decided (1 SBT = 1 vote). Submitting a "
                  "proposal on-chain and evaluating real orgs are G7-gated._\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/dd-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-stewardship-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "dd-report.md") (report-md nodes edges res))
       (println (str "tanemaki: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "dd-report.md")))
       0)))

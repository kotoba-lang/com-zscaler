(ns meisai.methods.recurring
  "recurring.cljc — meisai 明細: recurring-charge detection over :meisai.row/* → kaiyaku 解約 handoff.
  clj-native (ADR-2606122400 R1; repo rule: new operational code = clj/bb over the kotoba Datom log).

  Folds the member's OWN statement rows (already on the local Datom log) into recurring-charge
  CANDIDATES — a merchant billed across ≥N distinct months at a stable amount looks like a
  subscription — and emits a kaiyaku-consumable handoff worklist (the meisai→kaiyaku wiring the
  README/CLAUDE.md flagged as R1). It mirrors the tate→kaiyaku handoff pattern (out/*-handoff.edn
  → kaiyaku handoff-ingest), pointed at card statements instead of contract clauses.

  meisai SURFACES, it never DECIDES: every candidate carries :handoff/action :review and
  :handoff/advisory true. keep/review/sever is kaiyaku's call (its G2 edge-primary burden +
  member-sig + dry-run gates), never meisai's. A merchant is a SERVICE candidate, never a person
  (kaiyaku N1). No :sever is representable here (test-enforced).

  G1/G3 hold: the input is the member's OWN local log and the handoff OUTPUT is personal data
  (it reveals subscriptions) → it is written under the gitignored data/, never committed/pinned/
  posted. The pure fns operate on datoms only, so they are tested on SYNTHETIC datoms with no file."
  (:require [clojure.string :as str]
            [meisai.methods.kotoba :as kotoba]
            [meisai.methods.fx :as fx]
            #?(:clj [clojure.java.io :as io])))

;; ── EAVT → row view ──────────────────────────────────────────────────────────
(defn entity-view
  "Fold append-only [op e a v] datoms into {entity {attr value}} (last value wins)."
  [datoms]
  (reduce (fn [m d]
            (if (= 4 (count d))
              (assoc-in m [(nth d 1) (nth d 2)] (nth d 3))
              m))
          {} datoms))

(defn rows
  "Reconstruct statement rows joined to their statement's month + source:
   [{:merchant :amount :currency :month :source}]. amount = integer minor units
   (:meisai.row/amount-jpy → :jpy, else generic :meisai.row/amount + :meisai.row/currency)."
  [datoms]
  (let [ev (entity-view datoms)]
    (->> ev
         (keep (fn [[_eid attrs]]
                 (when-let [stmt (get attrs ":meisai.row/stmt")]
                   (let [s (get ev stmt)
                         jpy (get attrs ":meisai.row/amount-jpy")]
                     {:merchant (str (get attrs ":meisai.row/merchant" "?"))
                      :amount (long (or jpy (get attrs ":meisai.row/amount") 0))
                      :currency (if jpy ":jpy" (str (get attrs ":meisai.row/currency" ":jpy")))
                      :month (str (get s ":meisai.stmt/month" "?"))
                      :source (str (get s ":meisai.stmt/source" ":unknown"))}))))
         (sort-by (juxt :merchant :currency :month))
         vec)))

;; ── recurring detection ──────────────────────────────────────────────────────
(defn- median-long [xs]
  (let [v (vec (sort xs))] (nth v (quot (count v) 2))))

(defn recurring
  "Group rows by (merchant, currency); a group billed across ≥ :min-months distinct months is a
  recurring candidate. :amount-tol = relative spread allowed for :amount-stable?. Deterministic."
  ([datoms] (recurring datoms {}))
  ([datoms {:keys [min-months amount-tol] :or {min-months 2 amount-tol 0.15}}]
   (->> (rows datoms)
        (group-by (juxt :merchant :currency))
        (keep (fn [[[merchant currency] rs]]
                (let [months (vec (sort (distinct (map :month rs))))
                      amounts (map :amount rs)
                      typical (median-long amounts)
                      stable? (or (zero? typical)
                                  (every? #(<= (Math/abs (- % typical))
                                               (* amount-tol (double typical))) amounts))]
                  (when (>= (count months) min-months)
                    {:merchant merchant
                     :currency currency
                     :source (:source (first rs))
                     :months months
                     :occurrences (count rs)
                     :typical-amount typical
                     :amount-stable? stable?
                     :recurring? true}))))
        (sort-by (juxt (comp - :occurrences) :merchant))
        vec)))

(defn price-increases
  "Recurring charges whose amount has CREPT UP across the statements — the stealth subscription price
  hike. `recurring` reports an :amount-stable? flag (a yes/no within a tolerance) but not the
  DIRECTION or MAGNITUDE of a change; this reconstructs the member's OWN rows, groups by merchant,
  orders each group by statement MONTH, and surfaces those whose latest amount exceeds the earliest by
  more than `:min-pct` (default 0.05) — a stronger 解約 (kaiyaku) REVIEW signal than 'unstable'. meisai
  SURFACES; kaiyaku decides keep/review/sever (a merchant is a SERVICE, never a person). Read-only over
  the local Datom log (G3/G4); member-own only (G1); no credential/PAN — only merchant + amount-minor +
  month (G2). Returns [{:merchant :currency :first-amount :last-amount :increase :pct :months}] by pct
  descending."
  ([datoms] (price-increases datoms {}))
  ([datoms {:keys [min-pct] :or {min-pct 0.05}}]
   (->> (rows datoms)
        (group-by (juxt :merchant :currency))
        (keep (fn [[[merchant currency] rs]]
                (let [sorted (sort-by :month rs)
                      months (count (distinct (map :month rs)))
                      f (double (:amount (first sorted)))
                      l (double (:amount (last sorted)))]
                  (when (and (> months 1) (pos? f) (> l (* f (+ 1.0 min-pct))))
                    {:merchant merchant :currency currency
                     :first-amount (long f) :last-amount (long l)
                     :increase (long (- l f)) :pct (/ (- l f) f)
                     :months months}))))
        (sort-by (comp - :pct))
        vec)))

;; ── kaiyaku handoff ──────────────────────────────────────────────────────────
(defn handoff
  "Recurring candidates → kaiyaku-consumable handoff records. meisai PROPOSES :review only; the
  keep/review/sever decision stays with kaiyaku (+ member-sig). No :sever is representable.
  opts `:rates` (currency → JPY per major unit), when present, adds a REPORT-TIME JPY-equivalent
  to non-JPY records via fx/enrich-handoff (advisory; never persisted as a Datom)."
  ([datoms] (handoff datoms {}))
  ([datoms opts]
   (let [base (mapv (fn [c]
                      {":handoff/source" ":meisai"
                       ":handoff/svc" (:merchant c)
                       ":handoff/merchant" (:merchant c)
                       ":handoff/recurring" true
                       ":handoff/months" (:months c)
                       ":handoff/occurrences" (:occurrences c)
                       ":handoff/typical-amount" (:typical-amount c)
                       ":handoff/currency" (:currency c)
                       ":handoff/amount-stable" (:amount-stable? c)
                       ":handoff/action" ":review"
                       ":handoff/advisory" true})
                    (recurring datoms opts))]
     (if-let [rates (:rates opts)]
       (fx/enrich-handoff base rates)
       base))))

(defn- edn-scalar [v]
  (cond
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (and (string? v) (str/starts-with? v ":")) v
    (string? v) (str \" (str/replace v "\"" "\\\"") \")
    (sequential? v) (str "[" (str/join " " (map edn-scalar v)) "]")
    :else (str v)))

(defn handoff->edn
  "Render the handoff vector as an EDN list (kaiyaku handoff-ingest reads ':…' keyword strings)."
  [hs]
  (str ";; meisai 明細 → kaiyaku 解約 handoff — recurring-charge candidates (notice-window review).\n"
       ";; PERSONAL data (reveals subscriptions): lives under gitignored data/, NEVER committed (G3).\n"
       ";; meisai SURFACES only — :handoff/action is :review; keep/sever is kaiyaku + member-sig.\n"
       "[" (str/join "\n "
                     (map (fn [h]
                            (str "{" (str/join " " (map (fn [[k v]] (str k " " (edn-scalar v))) h)) "}"))
                          hs))
       "]\n"))

;; ── IO (clj only) ────────────────────────────────────────────────────────────
#?(:clj
   (do
     (def ^:private here (-> (io/file *file*) .getParentFile .getParentFile))

     (defn run
       "Read the local Datom log → handoff EDN under data/ (gitignored). Returns a summary."
       [log-path out-path opts]
       (let [datoms (mapcat #(get % ":tx/datoms") (kotoba/read-log log-path))
             hs (handoff datoms opts)
             f (io/file out-path)]
         (when-let [p (.getParentFile f)] (.mkdirs p))
         (spit f (handoff->edn hs))
         {:candidates (count hs) :out out-path}))

     (defn -main [& argv]
       (let [argv (vec argv)
             opt (fn [f d] (let [i (.indexOf argv f)]
                             (if (and (>= i 0) (< (inc i) (count argv))) (nth argv (inc i)) d)))
             log-path (opt "--log" (str (io/file here "data" "persisted" "meisai.datoms.kotoba.edn")))
             out-path (opt "--out" (str (io/file here "data" "kaiyaku-handoff.edn")))
             min-m (Long/parseLong (str (opt "--min-months" "2")))
             r (run log-path out-path {:min-months min-m})]
         (println (str "meisai → kaiyaku: " (:candidates r) " recurring-charge candidates → "
                       (:out r) " (advisory :review; severance stays kaiyaku + member-sig)"))))))

(ns keizu.cells.money-graph.state-machine
  "Phase state machine for the 系図 (keizu) money_graph cell.
  1:1 port of cells/money_graph/state_machine.py (ADR-2606066000).

  Aggregates disclosed money flows into per-payee shares + HHI (aggregate, factual; G2/G4).
  Self-contained.")

(def phase-init "init")
(def phase-aggregated "aggregated")
(def phase-refused "refused")

(def state-defaults
  {"phase"   phase-init
   "money"   []
   "total"   0.0
   "hhi"     0.0
   "shares"  []
   "refusal" ""})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn- round4 [x]
  (/ (Math/round (* (double x) 10000.0)) 10000.0))

(defn transition-to-aggregated [state]
  (let [cs0 (cell-state state)
        cs  (assoc cs0 "money" (get state "money" (get cs0 "money")))
        money (get cs "money")]
    (if (empty? money)
      {"cell_state" (assoc cs "refusal" "no money flows to aggregate" "phase" phase-refused)}
      (let [;; by_payee preserving insertion order of first appearance
            order (distinct (map #(get % "payee") money))
            sums  (reduce (fn [acc m]
                            (update acc (get m "payee")
                                    (fnil + 0.0) (double (get m "amount" 0.0))))
                          {} money)
            by-payee (map (fn [p] [p (get sums p)]) order)
            total (reduce + 0.0 (map second by-payee))
            shares (map (fn [[p v]] [p (if (not= total 0.0) (/ v total) 0.0)]) by-payee)
            hhi (round4 (reduce + 0.0 (map (fn [[_ s]] (* s s)) shares)))
            ;; stable sort by -share keeps insertion order on ties (Python sorted is stable)
            shares-sorted (vec (sort-by (fn [[_ s]] (- (round4 s)))
                                        (map (fn [[p s]] [p (round4 s)]) shares)))]
        {"cell_state" (assoc cs
                             "total" total
                             "hhi" hhi
                             "shares" shares-sorted
                             "phase" phase-aggregated)}))))

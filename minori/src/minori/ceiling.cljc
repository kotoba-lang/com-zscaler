(ns minori.ceiling
  "Convergence / ceiling evaluation — the mature '成長を評価する' readout. minori distinguishes
   SELF-DRIVABLE levers (its own net-giver η, already earned) from EXTERNALLY-GATED levers
   (capture = real settled revenue, G7-operator; Φ = closing the SoS running-gap rollout; adoption =
   the registered roster). It computes the weighted headroom still available per lever and FLAGS
   :converged when self-drivable growth is exhausted AND recent beats show no real upward progress —
   so the loop reports an HONEST plateau (with the external blockers named) instead of spinning.")

(def lever-meta
  "Whether each score lever is something minori can move ALONE, and the gate if not."
  {:eta      {:self-drivable? true  :gate :earned}        ; minori's own net-giver behaviour
   :adoption {:self-drivable? false :gate :roster}        ; registered SoS-spec holders (saturated)
   :capture  {:self-drivable? false :gate :G7-operator}   ; real settled donation/OSS revenue
   :phi      {:self-drivable? false :gate :sos-rollout}}) ; close the measured-running gap

(defn headroom
  "Per-lever weighted distance to its 1.0 ceiling = how much G each lever can still add."
  [weights components]
  (for [[k w] weights
        :let [c (double (get components k 0.0))
              m (get lever-meta k {:self-drivable? false :gate :unknown})]]
    (assoc m :lever k :value c :headroom (* (double w) (- 1.0 c)))))

(defn evaluate
  "Convergence verdict. `recent-dG` = the last K beats' dG (incl. this one). Converged when the
   self-drivable headroom ≈ 0 AND no recent beat made upward progress (max recent-dG ≤ eps)."
  [weights components {:keys [recent-dG eps] :or {eps 1e-6}}]
  (let [hs        (headroom weights components)
        self-head (reduce + 0.0 (map :headroom (filter :self-drivable? hs)))
        ext-head  (reduce + 0.0 (map :headroom (remove :self-drivable? hs)))
        progressed? (and (seq recent-dG) (> (apply max (map double recent-dG)) eps))
        converged?  (and (< self-head eps) (not progressed?))]
    {:converged? converged?
     :self-drivable-headroom self-head
     :external-headroom ext-head
     :blocked-on (->> hs (remove :self-drivable?) (filter #(> (:headroom %) eps))
                      (sort-by :headroom >)
                      (mapv #(-> (select-keys % [:lever :headroom :gate])
                                 (update :headroom (fn [h] (Double/parseDouble (format "%.4f" h)))))))
     :verdict (if converged?
                "converged — self-drivable growth exhausted; remaining headroom is externally gated"
                "still advancing")}))

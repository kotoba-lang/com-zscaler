(ns hataori.cells.finishing-packing.state-machine
  "1:1 port of cells/finishing_packing/state_machine.py (ADR-2606032100 + 2606032130) — the
  constitutional heart of hataori 畳 (garment/apparel robotics): the terminal cell emits a finished
  garment lot ONLY together with a fair-labor provenance record. A lot cannot be attested unless
  BOTH hold: G9 no displaced worker re-employed below the Basic-High-Income standard, and G2 the
  displaced cohort is registered for the tenure-weighted Displacement Dividend. N4 forbids fast-
  fashion overproduction (quantity ≤ made-to-need ceiling). Pure phase-progression init → finished →
  folded → lot_attested. FinishingState dataclass → string-keyed map under \"cell_state\"; ValueError
  → (throw (ex-info ...)).")

(def ^:private defaults
  {"phase" "init" "lot_id" "did:web:hataori.etzhayyim.com/lot/demo-0001" "garment_type" "work-shirt"
   "quantity" 0 "made_to_need_ceiling" 0 "offcut_waste_permille" 0
   "displaced_cohort_id" "" "no_worker_below_bhi" true "dividend_attested" false
   "robot_sigs" [] "payload" {}})

(defn- state* [state] (merge defaults (get state "cell_state" {})))

(defn transition-to-finished [state]
  (let [cs (state* state)
        quantity (int (get state "quantity" 0))
        ceiling (int (get state "made_to_need_ceiling" quantity))]
    (when (> quantity ceiling)
      (throw (ex-info (str "N4 violation: quantity " quantity " exceeds made-to-need ceiling " ceiling)
                      {:hataori/violation :n4})))
    {"cell_state" (assoc cs "phase" "finished" "quantity" quantity "made_to_need_ceiling" ceiling
                         "offcut_waste_permille" (int (get state "offcut_waste_permille" 0)))
     "next_node" "folded"}))

(defn transition-to-folded [state]
  {"cell_state" (assoc (state* state) "phase" "folded") "next_node" "lot_attested"})

(defn transition-to-lot-attested [state]
  (let [cs (assoc (state* state)
                  "displaced_cohort_id" (get state "displaced_cohort_id" "")
                  "no_worker_below_bhi" (boolean (get state "no_worker_below_bhi" true))
                  "dividend_attested" (boolean (get state "dividend_attested" false))
                  "robot_sigs" (vec (get state "robot_sigs" [])))]
    ;; G9: the actor exists to END sweatshop labour
    (when-not (get cs "no_worker_below_bhi")
      (throw (ex-info "G9 violation: a displaced worker would be re-employed below the Basic-High-Income standard"
                      {:hataori/violation :g9})))
    ;; G2: no live displacement without a funded tenure-weighted dividend cohort (ADR-2606032130)
    (when (or (not (get cs "dividend_attested")) (empty? (get cs "displaced_cohort_id")))
      (throw (ex-info "G2 violation: displaced cohort not registered for the Displacement Dividend (ADR-2606032130)"
                      {:hataori/violation :g2})))
    {"cell_state" (assoc cs "phase" "lot_attested"
                         "payload" {"finished_lot" {"lotId" (get cs "lot_id")
                                                    "garmentType" (get cs "garment_type")
                                                    "quantity" (get cs "quantity")
                                                    "offcutWastePermille" (get cs "offcut_waste_permille")}
                                    "fair_labor_provenance" {"lotId" (get cs "lot_id")
                                                             "displacedCohortId" (get cs "displaced_cohort_id")
                                                             "noWorkerBelowBhi" true
                                                             "dividendAttested" (get cs "dividend_attested")}})
     "next_node" "end"}))

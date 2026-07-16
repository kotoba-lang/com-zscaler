(ns fuchi.cells.need-assessment.state-machine
  "Phase state machine for 扶持 (fuchi) need_assessment — the G2/G3 in-kind envelope.
  1:1 port of cells/need_assessment/state_machine.py (ADR-2606052300). A maintainer's sustenance NEED
  is assessed as in-kind lines; REFUSES any line with nonzero cash (G2 cash≡0) or not a covered in-kind
  line (G3 cash/stipend/disbursement unrepresentable)."
  (:require [clojure.string :as str]))

(def lines- #{"housing" "food" "energy" "compute" "tooling" "care" "liquidity"})
(def state-defaults {"phase" "init" "did" "" "lines" [] "imputed_total" 0 "refusal" "" "payload" []})
(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- kw [v] (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))
(defn- to-int [v] (long (or v 0)))

(defn transition-to-assessed [state]
  (let [cs (cell-state state)
        cs (assoc cs "did" (get state "did" (get cs "did")))
        raw (get state "lines" (get cs "lines"))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" "refused")})]
    (loop [ls raw out [] total 0]
      (if (empty? ls)
        {"cell_state" (assoc cs "payload" out "imputed_total" total "refusal" "" "phase" "assessed")}
        (let [ln (first ls)
              kind (kw (get ln "line" ""))
              cash (to-int (get ln "cash_usd_micros" 0))
              imputed (to-int (get ln "imputed_usd_micros_yr" 0))]
          (cond
            (contains? #{"cash" "stipend" "cash-disbursement"} kind)
            (refuse (str "G3: line " (pr-str kind) " unrepresentable (cash≡0; 扶持 never pays cash)"))
            (not (contains? lines- kind))
            (refuse (str "G3: line " (pr-str kind) " has no in-kind rail"))
            (not= cash 0)
            (refuse "G2/cash≡0: a sustenance line cannot carry cash")
            :else
            (recur (rest ls)
                   (conj out {"maintainerDid" (get cs "did") "line" kind "imputedUsdMicrosYr" imputed "cashUsdMicros" 0})
                   (+ total imputed))))))))

(defn solve [_input-state]
  (throw (ex-info "fuchi R0 scaffold: activate need_assessment via Council ADR (post-2606052300 ratification)" {:scaffold true})))

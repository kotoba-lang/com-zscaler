(ns fuchi.cells.routing-dispatch.state-machine
  "Phase state machine for 扶持 (fuchi) routing_dispatch — the G3 in-kind rail decomposition.
  1:1 port of cells/routing_dispatch/state_machine.py (ADR-2606052300). Decomposes an assessed
  envelope into delivery RAILS over producing actors; liquidity becomes a MEMBER-PRINCIPAL warifu rail
  (扶持 never the creditor/payer); a cash line is unrepresentable (cash≡0). REFUSAL gate."
  (:require [clojure.string :as str]))

(def line-to-rail
  {"housing"   ["housing-commons" "commons-land"]
   "food"      ["food-mitsuho" "mitsuho"]
   "energy"    ["energy-hikari" "hikari"]
   "compute"   ["compute-murakumo" "murakumo"]
   "tooling"   ["tooling-okaimono" "okaimono"]
   "care"      ["care-iyashi" "iyashi"]
   "liquidity" ["liquidity-warifu" "warifu"]})

(def state-defaults {"phase" "init" "did" "" "rails" [] "in_kind_coverage" 1.0 "refusal" ""})
(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- kw [v] (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))
(defn- to-int [v] (long (or v 0)))
(defn- pyround4 [x] (/ (Math/round (* (double x) 10000.0)) 10000.0))

(defn transition-to-routed [state]
  (let [cs (cell-state state)
        cs (assoc cs "did" (get state "did" (get cs "did")))
        lines (get state "lines" [])
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" "refused")})]
    (loop [ls lines rails [] total 0 in-kind 0]
      (if (empty? ls)
        {"cell_state" (assoc cs "rails" rails
                             "in_kind_coverage" (if (> total 0) (pyround4 (/ in-kind total)) 1.0)
                             "refusal" "" "phase" "routed")}
        (let [ln (first ls)
              kind (kw (get ln "line" ""))
              cash (to-int (get ln "cash_usd_micros" 0))
              imputed (to-int (get ln "imputed_usd_micros_yr" 0))]
          (cond
            (or (contains? #{"cash" "stipend" "cash-disbursement"} kind) (not= cash 0))
            (refuse "cash≡0: a cash rail is UNREPRESENTABLE (扶持 never pays cash)")
            (not (contains? line-to-rail kind))
            (refuse (str "G3: line " (pr-str kind) " has no in-kind rail"))
            :else
            (let [[rail-kind provider] (get line-to-rail kind)
                  member-principal (= kind "liquidity")]
              (recur (rest ls)
                     (conj rails {"allocId" (get cs "did") "kind" rail-kind "providerActor" provider
                                  "imputedUsdMicrosYr" imputed "memberPrincipal" member-principal})
                     (+ total imputed)
                     (if member-principal in-kind (+ in-kind imputed))))))))))

(defn solve [_input-state]
  (throw (ex-info "fuchi R0 scaffold: activate routing_dispatch via Council ADR (post-2606052300 ratification)" {:scaffold true})))

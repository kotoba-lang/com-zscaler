(ns shionome.cells.ingest.state-machine
  "Phase state machine for the 潮目 shionome ingest cell — the G1/G2/G3 intake membrane.
  1:1 port of cells/ingest/state_machine.py (ADR-2606072200). A public market-data batch enters;
  each record is SCREENED against the closed structural vocab: G1 bucket scope ∈ public capital
  buckets (no person/account/portfolio); G2 flow kind is a factual observation (no trade token,
  トレードはしない); G3 ≥2 public-source citations. Clean batch RECORDED (counts only); any violation REFUSES."
  (:require [clojure.string :as str]))

(def bucket-scopes #{"asset-class" "sector" "region" "theme"})
(def flow-kinds #{"rotation" "fund-inflow" "fund-outflow" "price-move" "cross-correlation" "volume-shift" "yield-shift" "fx-flow"})
(def trade-tokens ["buy" "sell" "long" "short" "overweight" "underweight" "recommend"
                   "target price" "target-price" "推奨" "買い" "売り" "目標株価" "空売り"])

(def state-defaults {"phase" "init" "buckets" [] "flows" [] "snapshots" [] "recorded" 0 "refusal" ""})
(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- kw [v] (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))
(defn- trade-token [text]
  (let [blob (str/lower-case (str (or text "")))]
    (some #(when (str/includes? blob %) %) trade-tokens)))

(defn transition-to-screened [state]
  (let [cs (cell-state state)
        cs (assoc cs "buckets" (get state "buckets" (get cs "buckets"))
                  "flows" (get state "flows" (get cs "flows"))
                  "snapshots" (get state "snapshots" (get cs "snapshots")))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" "refused")})
        bad-bucket (first (filter #(not (contains? bucket-scopes (kw (get % "scope")))) (get cs "buckets")))]
    (if bad-bucket
      (refuse (str "G1: bucket scope " (pr-str (get bad-bucket "scope")) " unrepresentable (no person/account)"))
      ;; flows: first violation (trade-token → G2 token; not-a-kind → G2; <2 sources → G3)
      (loop [fs (get cs "flows")]
        (if (empty? fs)
          {"cell_state" (assoc cs "refusal" "" "phase" "screened")}
          (let [f (first fs) k (kw (get f "kind")) t (trade-token k)]
            (cond
              t (refuse (str "G2: flow kind contains trade token " (pr-str t) " — unrepresentable (トレードはしない)"))
              (not (contains? flow-kinds k)) (refuse (str "G2: flow kind " (pr-str k) " not a factual observation"))
              (< (count (get f "sources" [])) 2) (refuse "G3: a flow needs ≥2 public sources")
              :else (recur (rest fs)))))))))

(defn transition-to-recorded [state]
  (let [cs (cell-state state)]
    (if (not= (get cs "phase") "screened")
      {"cell_state" (assoc cs "refusal" "cannot record a batch that was not screened clean" "phase" "refused")}
      {"cell_state" (assoc cs "recorded" (+ (count (get cs "buckets")) (count (get cs "flows")) (count (get cs "snapshots")))
                           "phase" "recorded")})))

(defn solve [_input-state]
  (throw (ex-info "shionome R0 scaffold: activate ingest via Council ADR (post-2606072200 ratification)" {:scaffold true})))

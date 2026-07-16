(ns shionome.cells.social-post.state-machine
  "Phase state machine for the 潮目 shionome social_post cell.
  1:1 port of cells/social_post/state_machine.py (ADR-2606072200). Drafts a DRY-RUN post and REFUSES
  if the body carries a trade/advisory token (G2 トレードはしない) or <2 sources (G3); status dry-run only (G8)."
  (:require [clojure.string :as str]))

(def trade-tokens ["buy" "sell" "long" "short" "overweight" "underweight" "recommend"
                   "target price" "target-price" "推奨" "買い" "売り" "目標株価" "空売り"])

(def state-defaults {"phase" "init" "body" "" "sources" [] "status" "" "refusal" ""})
(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- trade-token [text]
  (let [blob (str/lower-case (str (or text "")))]
    (some #(when (str/includes? blob %) %) trade-tokens)))

(defn transition-to-drafted [state]
  (let [cs (cell-state state)
        cs (assoc cs "body" (get state "body" (get cs "body")) "sources" (get state "sources" (get cs "sources")))
        refuse (fn [msg] {"cell_state" (assoc cs "refusal" msg "phase" "refused")})
        t (trade-token (get cs "body"))]
    (cond
      t (refuse (str "G2: post body contains trade token " (pr-str t) " — refused (トレードはしない)"))
      (< (count (filter #(seq (str/trim (str %))) (get cs "sources"))) 2)
      (refuse "G3: a post needs ≥2 public sources")
      :else
      {"cell_state" (assoc cs "refusal" "" "status" "dry-run" "phase" "drafted")})))

(defn solve [_input-state]
  (throw (ex-info "shionome R0 scaffold: activate social_post via Council ADR (post-2606072200 ratification)" {:scaffold true})))

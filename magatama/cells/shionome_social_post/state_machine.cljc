(ns magatama.cells.shionome-social-post.state-machine
  "shionome_social_post — DRY-RUN capital-flow social post (shionome).
  Per ADR-2606072200. Mirror (G5), no-trade body scan (G2), dry-run only (G8).
  Live posting Council Lv6+ + operator + member-signature gated.
  Port of shionome_social_post/cell.py."
  (:require [magatama.cells.shionome-core :as core]))

;; ── State ────────────────────────────────────────────────────────────────────────
;; {:context map? :regime map? :post map? :refusal str?}

(defn draft
  "Draft a dry-run post. Mirrors Python _draft node."
  [state]
  (let [ctx     (or (:context state) {})
        reg     (or (:regime state) (:regime ctx) (get ctx "regime") {})
        sources (or (:sources ctx) (get ctx "sources") [])
        label   (or (:regime reg) (get reg "regime") "indeterminate")
        rn      (or (:risk_net reg) (get reg "risk_net") 0)
        sn      (or (:safe_net reg) (get reg "safe_net") 0)
        body    (format "クロスアセット観測: %s — リスク資産 net %+.1fbn / 安全資産 net %+.1fbn。記述であり助言ではありません。"
                        label (double rn) (double sn))]
    (try
      (assoc state :post (core/draft-dry-run-post body sources) :refusal "")
      (catch Exception e
        (assoc state :post {} :refusal (ex-message e))))))

(defn run-chain
  "Thread state through: START → draft → END."
  [state]
  (draft state))

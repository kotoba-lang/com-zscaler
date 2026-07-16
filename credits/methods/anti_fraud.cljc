(ns credits.methods.anti-fraud
  "credits -- anti-fraud pure predicates (CLAUDE.md § Anti-Fraud, G4-G7): rate
  limits, high-value-earn reject, HC reputation gate, duplicate-reward reject.
  Pure functions over an explicit event-count / state map supplied by the
  caller -- no real clock, no live db, no fabricated user data.")

(def spend-rate-limit-per-hour
  "G4: spend rate limit, per user_id." 60)

(def earn-rate-limit-per-hour
  "G4: earn rate limit, per user_id." 30)

(def high-value-earn-reject-threshold
  "G5: a single earn transaction strictly above this amount is rejected." 50)

(def hc-reputation-gate-min-approval-rate
  "G6: HC-sourced reward requires approval_rate >= this (0..1 fraction)." 0.5)

(defn check-spend-allowed
  "{:recent-spend-count n} -> {:allowed bool :reason (when denied)}. G4."
  [{:keys [recent-spend-count] :or {recent-spend-count 0}}]
  (if (>= recent-spend-count spend-rate-limit-per-hour)
    {:allowed false :reason "spend-rate-limit-exceeded"}
    {:allowed true}))

(defn check-earn-allowed
  "{:recent-earn-count n :amount n? :approval-rate r? :task-id id? :session-id id?
  :seen-task-ids #{} :seen-session-ids #{}} -> {:allowed bool :reason (when denied)}.
  Checked in gate order G4 -> G5 -> G6 -> G7 (first failing gate wins)."
  [{:keys [recent-earn-count amount approval-rate task-id session-id
           seen-task-ids seen-session-ids]
    :or {recent-earn-count 0 seen-task-ids #{} seen-session-ids #{}}}]
  (cond
    (>= recent-earn-count earn-rate-limit-per-hour)
    {:allowed false :reason "earn-rate-limit-exceeded"}

    (and (some? amount) (> amount high-value-earn-reject-threshold))
    {:allowed false :reason "high-value-earn-reject"}

    (and (some? approval-rate) (< approval-rate hc-reputation-gate-min-approval-rate))
    {:allowed false :reason "hc-reputation-gate"}

    (and (some? task-id) (contains? seen-task-ids task-id))
    {:allowed false :reason "duplicate-reward-task"}

    (and (some? session-id) (contains? seen-session-ids session-id))
    {:allowed false :reason "duplicate-reward-session"}

    :else {:allowed true}))

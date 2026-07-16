(ns keizu.cells.social-post.state-machine
  "Phase state machine for the 系図 (keizu) social_post cell — the G2/G5/G7/G8 publication membrane.
  1:1 port of cells/social_post/state_machine.py (ADR-2606066000).

  A finding enters; it is DRAFTED into a dry-run post ONLY if:
    G3 — >=2 public-source citations are present;
    G5 — the post is a mirror (isMirror), opening with the accountability disclaimer;
    G7 — server-held-key is false (the member signs, the server never does);
    G8 — the status is dry-run (a 'published' request REFUSES — live needs Council Lv6+ + operator).
  Self-contained."
  (:require [clojure.string :as str]))

(def disclaimer "【観測ミラー / accountability map — non-adjudicating】")

(def phase-init "init")
(def phase-drafted "drafted")
(def phase-refused "refused")

(def state-defaults
  {"phase"            phase-init
   "subject"          ""
   "sources"          []
   "requested_status" "dry-run"
   "server_held_key"  false
   "payload"          {}
   "refusal"          ""})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn- lstrip-colon [s]
  (str/replace (str s) #"^:+" ""))

(defn transition-to-drafted [state]
  (let [cs0 (cell-state state)
        cs  (assoc cs0
                   "subject"          (get state "subject" (get cs0 "subject"))
                   "sources"          (get state "sources" (get cs0 "sources"))
                   "requested_status" (lstrip-colon (get state "requested_status" (get cs0 "requested_status")))
                   "server_held_key"  (boolean (get state "server_held_key" (get cs0 "server_held_key"))))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})]
    (cond
      (< (count (get cs "sources")) 2)
      (refuse "G3: a post needs ≥2 public-source citations")

      (get cs "server_held_key")
      (refuse "G7/no-server-key: server-held-key must be false (ADR-2605231525)")

      (not= (get cs "requested_status") "dry-run")
      (refuse "G8: only dry-run posts at R0; live publication is Council Lv6+ + operator gated")

      :else
      (let [payload {":post/subject" (get cs "subject")
                     ":post/body" (str disclaimer " " (get cs "subject"))
                     ":post/status" ":dry-run"
                     ":post/is-mirror" true
                     ":post/non-adjudicating-notice" true
                     ":post/server-held-key" false
                     ":post/sources" (get cs "sources")}]
        {"cell_state" (assoc cs "payload" payload "refusal" "" "phase" phase-drafted)}))))

(ns fuchi.methods.vote
  "vote.cljc — 扶持 (fuchi) R1(b): real 1 SBT = 1 vote with a 48h timelock. 1:1 Clojure port
  of `methods/vote.py` (ADR-2606052300).

    - 1 SBT = 1 vote — each member DID casts exactly ONE ballot (a second from the same DID
      is rejected at cast time); every ballot has weight == 1 (no token-weighted plutocracy).
    - no-server-key — a :server voter is unrepresentable; ballots are member-signed (G9).
    - 48h timelock — a tally cannot be FINALIZED before opened-at + timelock; an out-of-window
      ballot is not counted.
    - quorum — a minimum number of participating ballots is required, else rejected.

  House style: ':…' strings stay strings; pure fns; gates → ex-info. Time is an integer hour
  stamp (passed in). Portable .cljc."
  (:require [clojure.string :as str]
            [fuchi.methods.live-gate :as live-gate]))

(def DEFAULT-TIMELOCK-H 48)
(def DEFAULT-QUORUM 3)
(def CHOICES #{"yes" "no" "abstain"})

(defn- kw* [v]
  (-> (str (or v "")) (#(if (str/starts-with? % ":") (subs % 1) %))
      (str/split #"/") last str/lower-case))

(defn make-ballot
  "Ballot dataclass equivalent. __post_init__ enforces weight==1, no-server-key,
  no :server/:anon voter, valid choice."
  [{:keys [voter-did choice cast-at weight server-held-key]
    :or {weight 1 server-held-key false}}]
  (when (not= weight 1)
    (throw (ex-info "1 SBT = 1 vote INVARIANT: ballot weight must be 1" {})))
  (when server-held-key
    (throw (ex-info "no-server-key INVARIANT (G9): a ballot is member-signed" {})))
  (let [v (str/lower-case (str voter-did))]
    (when (or (some #(str/starts-with? v %) ["server" "did:server" ":server"])
              (contains? #{"server" "anon"} v))
      (throw (ex-info "G9/G4: a :server / :anon voter is unrepresentable" {})))
    (when-not (contains? CHOICES (kw* choice))
      (throw (ex-info (str "ballot choice '" choice "' not in " CHOICES) {:choice choice}))))
  {:voter-did voter-did :choice choice :cast-at (long cast-at)
   :weight 1 :server-held-key false})

(defn cast
  "Append a ballot, enforcing 1 SBT = 1 vote (a duplicate voter DID is rejected)."
  [ballots ballot]
  (when (some #(= (:voter-did %) (:voter-did ballot)) ballots)
    (throw (ex-info (str "1 SBT = 1 vote: " (:voter-did ballot) " has already voted") {})))
  (conj (vec ballots) ballot))

(defn ballots-from-seed
  "Build ballots from seed maps; rejects duplicate voters (1 SBT = 1 vote)."
  [records]
  (reduce
   (fn [out r]
     (cast out (make-ballot
                {:voter-did (get r ":ballot/voter" (get r "voter" "?"))
                 :choice (kw* (get r ":ballot/choice" (get r "choice" "yes")))
                 :cast-at (long (get r ":ballot/cast-at" (get r "cast_at" 0)))})))
   [] records))

(defn tally
  "Tally a vote. Only ballots cast within [opened-at, opened-at+timelock-h] count.
  Outcome: pending (window open) | rejected (no quorum) | accepted (finalizable+quorum+yes>no)
  | rejected (otherwise)."
  ([ballots opened-at now] (tally ballots opened-at now DEFAULT-TIMELOCK-H DEFAULT-QUORUM))
  ([ballots opened-at now timelock-h] (tally ballots opened-at now timelock-h DEFAULT-QUORUM))
  ([ballots opened-at now timelock-h quorum]
   (let [close (+ opened-at timelock-h)
         in-window (filter #(<= opened-at (:cast-at %) close) ballots)
         yes (count (filter #(= (kw* (:choice %)) "yes") in-window))
         no (count (filter #(= (kw* (:choice %)) "no") in-window))
         abstain (count (filter #(= (kw* (:choice %)) "abstain") in-window))
         participating (+ yes no)
         finalizable (>= now close)
         quorum-met (>= participating quorum)
         outcome (cond
                   (not finalizable) "pending"
                   (not quorum-met) "rejected"
                   (> yes no) "accepted"
                   :else "rejected")]
     {"yes" yes "no" no "abstain" abstain "voters" (count in-window)
      "opened_at" opened-at "close" close "now" now "timelock_h" timelock-h
      "quorum" quorum "quorum_met" quorum-met "finalizable" finalizable
      "outcome" outcome})))

(defn finalize
  "Strict finalize — RAISES if the 48h timelock has not elapsed (no early close)."
  ([ballots opened-at now] (finalize ballots opened-at now DEFAULT-TIMELOCK-H DEFAULT-QUORUM))
  ([ballots opened-at now timelock-h] (finalize ballots opened-at now timelock-h DEFAULT-QUORUM))
  ([ballots opened-at now timelock-h quorum]
   (when (< now (+ opened-at timelock-h))
     (throw (ex-info (str "timelock INVARIANT: cannot finalize before " (+ opened-at timelock-h)
                          "h (now=" now "h, window=" timelock-h "h)") {})))
   (tally ballots opened-at now timelock-h quorum)))

(defn finalize-binding
  "Finalize a vote as BINDING (the on-chain outcome). require-gate passes (R2 autonomous);
  the 48h timelock still applies strictly via finalize."
  [ballots opened-at now gate
   & {:keys [timelock-h quorum env] :or {timelock-h DEFAULT-TIMELOCK-H quorum DEFAULT-QUORUM}}]
  (live-gate/require-gate gate env)
  (let [result (finalize ballots opened-at now timelock-h quorum)]
    (assoc result "binding" true "ratified_by" (:operator-did gate)
           "council_level" (:council-level gate))))

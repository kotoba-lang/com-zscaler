(ns keizu.cells.ingest.state-machine
  "Phase state machine for the 系図 (keizu) ingest cell — the G1/G2/G3 intake membrane.
  1:1 port of cells/ingest/state_machine.py (ADR-2606066000).

  A public-source batch enters. Each record is SCREENED against the closed structural vocab:
    G1 — a node scope must be a public seat/organ (no private person);
    G2 — a relation/money kind must be factual (no verdict token);
    G3 — a relation/money flow must carry >=2 public-source citations.
  A clean batch is RECORDED (counts only at R0); any violation REFUSES the whole batch.
  Self-contained (no methods import).

  Conventions: dataclass IngestState -> a plain map with the SAME string field keys the Python
  cs.__dict__ round-trips; phase enum value identities stay strings."
  (:require [clojure.string :as str]))

(def node-scopes #{"public-office" "public-org" "public-committee" "public-role"})
(def rel-kinds #{"committee-membership" "appointment" "advisory-role" "co-membership"
                 "revolving-door" "funding-tie" "statement-attribution" "procurement-award"})
(def money-kinds #{"procurement-award" "subsidy" "grant" "political-donation" "budget-outlay"})
(def verdict #{"corruption" "bribe" "kickback" "collusion" "guilt" "crime" "不正" "違法" "汚職" "賄賂"})

;; ── IngestPhase (enum — Python value identities preserved) ──
(def phase-init "init")
(def phase-screened "screened")
(def phase-recorded "recorded")
(def phase-refused "refused")

;; ── IngestState (dataclass → plain map, string keys + field defaults) ──
(def state-defaults
  {"phase"    phase-init
   "nodes"    []
   "rels"     []
   "money"    []
   "recorded" 0
   "refusal"  ""})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn- kw
  "_kw: lowercased last path segment with a leading ':' stripped."
  [v]
  (-> (str (or v ""))
      (str/replace #"^:+" "")
      (str/split #"/")
      last
      str/lower-case))

(defn transition-to-screened [state]
  (let [cs0 (cell-state state)
        cs  (assoc cs0
                   "nodes" (get state "nodes" (get cs0 "nodes"))
                   "rels"  (get state "rels"  (get cs0 "rels"))
                   "money" (get state "money" (get cs0 "money")))
        refuse (fn [msg]
                 {"cell_state" (assoc cs "refusal" msg "phase" phase-refused)})
        nodes (get cs "nodes")
        rels  (get cs "rels")
        money (get cs "money")
        node-violation
        (some (fn [n]
                (when-not (contains? node-scopes (kw (get n "scope")))
                  (refuse (str "G1: node scope " (pr-str (get n "scope"))
                               " unrepresentable (no private person)"))))
              nodes)
        rel-violation
        (when-not node-violation
          (some (fn [r]
                  (let [k (kw (get r "kind"))]
                    (cond
                      (contains? verdict k)
                      (refuse (str "G2: relation kind " (pr-str k) " is a verdict — unrepresentable"))
                      (not (contains? rel-kinds k))
                      (refuse (str "G2: relation kind " (pr-str k) " not factual"))
                      (< (count (get r "sources" [])) 2)
                      (refuse "G3: a relation needs ≥2 public sources"))))
                rels))
        money-violation
        (when (and (not node-violation) (not rel-violation))
          (some (fn [m]
                  (let [k (kw (get m "kind"))]
                    (cond
                      (contains? verdict k)
                      (refuse (str "G2: money kind " (pr-str k) " is a verdict — unrepresentable"))
                      (not (contains? money-kinds k))
                      (refuse (str "G2: money kind " (pr-str k) " not factual"))
                      (< (count (get m "sources" [])) 2)
                      (refuse "G3: a money flow needs ≥2 public sources"))))
                money))]
    (or node-violation rel-violation money-violation
        {"cell_state" (assoc cs "refusal" "" "phase" phase-screened)})))

(defn transition-to-recorded [state]
  (let [cs (cell-state state)]
    (if (not= (get cs "phase") phase-screened)
      {"cell_state" (assoc cs "refusal" "cannot record a batch that was not screened clean"
                           "phase" phase-refused)}
      {"cell_state" (assoc cs
                           "recorded" (+ (count (get cs "nodes"))
                                         (count (get cs "rels"))
                                         (count (get cs "money")))
                           "phase" phase-recorded)})))

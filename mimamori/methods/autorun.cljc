(ns mimamori.methods.autorun
  "mimamori deterministic heartbeat (ADR-2606112300; pattern: ADR-2606091000).
  Clojure port of `methods/autorun.py`.

  One cycle = replay seed → offer-matching pass (§D4) → aggregate coverage →
  keeper-side social-capital mint (moyai reuse) → persist ONE content-addressed
  transaction to the local append-only kotoba log.

    - NO external I/O: offline seed in, LOCAL log out. Live legs (real roster /
      §1.16 outreach / musubi ceremony / social-capital mint) stay G7-gated.
    - Deterministic + resume-safe: the cycle number derives from the log length
      (no wall clock, no randomness); same seed + same cycle → same CID — and the
      CID is byte-compatible with the Python implementation (kotoba.datom parity).
    - The G1..G7 gates are enforced INSIDE the loop by the bond engine's own
      validator — the heartbeat cannot emit what the schema cannot represent."
  (:require [kotoba.datom :as kd]
            [mimamori.methods.bond :as bond]
            [mimamori.methods.coverage-report :as cov]
            [mimamori.methods.match :as match]
            [mimamori.methods.shakai :as shakai]))

#?(:clj
   (defn run-cycle
     "One heartbeat. Returns an aggregate-only summary (G5)."
     ([seed log-path] (run-cycle seed log-path []))
     ([seed log-path ledger-log]
      (let [cycle (inc (count (kd/read-log log-path)))
            engine (bond/replay seed)                      ;; gates enforced in replay
            [engine summary] (match/match-cycle engine (:roster seed))
            coverage (cov/coverage-of-engine engine (:roster seed))
            [ledger-log shakai-summary] (shakai/mint-from-keeping engine ledger-log cycle)
            bond-datoms (mapv (fn [[e a v _tx _op]] (kd/add e a v)) (:datoms engine))
            coverage-datoms (into [(kd/add (str "coverage." cycle) ":mimamori.coverage/cycle" cycle)]
                                  (map (fn [k]
                                         (kd/add (str "coverage." cycle)
                                                 (str ":mimamori.coverage/" (name k))
                                                 (get coverage k)))
                                       [:members-total :with-keeper :offers-pending
                                        :unkept-count :active-bonds :relays]))
            datoms (into (into bond-datoms coverage-datoms)
                         (shakai/social-capital-datoms ledger-log cycle))
            tx (kd/make-tx datoms {:tx-id cycle :as-of cycle :prev-cid (kd/head-cid log-path)})
            cid (kd/append-tx! tx log-path)
            chain (kd/verify-chain log-path)]
        (when-not (:ok chain)
          (throw (ex-info (str "kotoba log chain broken at " (:broken-at chain)) chain)))
        ;; aggregate-only: counts and CIDs, never a DID
        (merge {:cycle cycle
                :cid cid
                :datoms (count datoms)
                :chain-length (:length chain)
                :shakai shakai-summary
                :coverage coverage}
               summary)))))

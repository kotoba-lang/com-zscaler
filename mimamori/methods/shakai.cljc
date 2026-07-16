(ns mimamori.methods.shakai
  "shakai — keeper-side social-capital mint bridge (ADR-2606082100 Part A reuse).
  Clojure port of `methods/shakai.py`; the ledger primitive is `moyai.ledger`,
  reused verbatim (NOT reimplemented — same invariants, same fold semantics).

      keeping act (content-free heartbeat on a consented bond)
          → mint 1 unit of social capital TO THE KEEPER (capped per epoch)
          → decays (a flow, never a store) — keeping must be lived, not banked

  mimamori-specific invariants enforced HERE:
    - mints go to the KEEPER only — there is no path that writes anything about
      the kept (G2 no-score: the kept's side of a keeping act is not a record)
    - provenance refs are sha256-opaque — a ledger entry never carries a kept DID
    - per-keeper per-epoch earn cap (moyai anti-sybil pattern): keeping is
      covenant, not a mining surface (G8 no-gamification)
    - grants-governance-weight? / grants-benefit-or-stage? are const-false —
      social capital never buys votes or 救済 stage (moyai invariants verbatim)"
  (:require [kotoba.datom :as kd]
            [moyai.ledger :as ledger]))

(def mint-per-act 1)        ;; one keeping act = one unit (integer, implied units)
(def earn-cap-per-epoch 3)  ;; per-keeper cap: keeping is covenant, not a mining surface

(defn opaque-ref
  "Provenance ref with NO DID inside — linkable by the parties who hold the keep
  entity id, opaque to everyone else (the ledger never becomes a who-keeps-whom registry)."
  [keep-entity]
  (str "keep:" (subs (kd/*sha256-hex* keep-entity) 0 16)))

(defn keeping-acts
  "[keeper-did keep-entity] per content-free heartbeat. Consent is guaranteed by
  construction: the bond engine refuses a heartbeat on any non-consented bond (G3)."
  [m]
  (let [keep-bond (into {} (keep (fn [[e a v _t _o]]
                                   (when (= a ":mishmeret.keep/bond") [e v]))
                                 (:datoms m)))]
    (into []
          (keep (fn [[e a v _t _o]]
                  (when (and (= a ":mishmeret.keep/act") (= v ":reached-out"))
                    (let [bid (keep-bond e)]
                      [(get-in m [:keeper bid]) e]))))
          (:datoms m))))

(defn mint-from-keeping
  "Mint social capital to keepers for this engine's keeping acts (capped).
  Returns [ledger-log' summary] — the summary is aggregate-only (G5); per-keeper
  balances live in the ledger, DID-bound (Soulbound)."
  [m ledger-log epoch]
  (let [[log minted capped per-keeper]
        (reduce (fn [[log minted capped per-keeper] [keeper keep-entity]]
                  (if (>= (get per-keeper keeper 0) earn-cap-per-epoch)
                    [log minted (inc capped) per-keeper]
                    [(ledger/mint log keeper mint-per-act epoch (opaque-ref keep-entity))
                     (inc minted) capped (update per-keeper keeper (fnil inc 0))]))
                [ledger-log 0 0 {}]
                (keeping-acts m))]
    (ledger/assert-conservation log)
    [log {:acts (+ minted capped)
          :minted-units (* minted mint-per-act)
          :capped-acts capped
          :keepers-minted (count per-keeper)}]))

(defn social-capital-datoms
  "EAVT assertions for the epoch's mint entries (Soulbound: holder DID is the
  keeper's own earned credit; refs are opaque — no kept DID anywhere)."
  [ledger-log epoch]
  (into []
        (comp (map-indexed vector)
              (filter (fn [[_i e]] (= epoch (:epoch e))))
              (mapcat (fn [[i e]]
                        (let [eid (str "shakai." epoch "." i)]
                          [(kd/add eid ":social.capital/holder" (:holder-did e))
                           (kd/add eid ":social.capital/op" (str (:op e)))
                           (kd/add eid ":social.capital/units" (:units e))
                           (kd/add eid ":social.capital/epoch" (:epoch e))
                           (kd/add eid ":social.capital/ref" (:ref e))]))))
        ledger-log))

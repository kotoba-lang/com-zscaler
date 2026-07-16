(ns meisai.methods.fx
  "fx.cljc — meisai 明細 REPORT-TIME foreign-exchange conversion for multi-currency rows.
  clj-native (ADR-2606122400 R1; repo clj/bb rule).

  A worldwide card statement may bill in any currency; meisai stores the native amount as
  integer minor units on `:meisai.row/{amount,currency}` (no floats). For a member-facing report
  or the kaiyaku handoff, a JPY-equivalent is useful — but it is a DERIVED, time-varying view:

    **FX is REPORT-TIME ONLY. It is NEVER persisted as a `:meisai.row/*` datom.** A rate snapshot
    goes stale; baking it into the append-only log would assert a false as-of truth. So fx/* only
    annotates the (already non-committed, gitignored) handoff/report, marked `:fx-advisory`, with
    the rate it used. The canonical truth on the log stays the native amount + currency.

  Rates are an INPUT (the member's own local snapshot, or a future G7-gated live leg), never a
  committed table here (a committed rate would rot). `rates` = {currency-string → JPY per 1 MAJOR
  unit}, e.g. {\":usd\" 150.0 \":eur\" 162.0}. Pure; no I/O; deterministic."
  (:require [clojure.string :as str]))

;; minor-unit exponent per currency (default 2); JPY/KRW/… are 0-exponent.
(def ^:private currency-exponent
  {":jpy" 0 ":krw" 0 ":vnd" 0 ":clp" 0 ":isk" 0
   ":bhd" 3 ":kwd" 3 ":omr" 3 ":tnd" 3})

(defn- exp-of [cur] (get currency-exponent cur 2))

(defn to-jpy
  "Integer minor-unit `amount` in `currency` → whole yen, using `rates` (JPY per 1 MAJOR unit).
  JPY passes through (minor unit == yen). Returns nil when no rate is known (caller leaves the
  charge un-priced → kaiyaku routes it to :review, never auto-:sever)."
  [amount currency rates]
  (cond
    (= currency ":jpy") (long amount)
    :else (when-let [rate (get rates currency)]
            (let [major (/ (double amount) (Math/pow 10 (exp-of currency)))]
              (long (Math/round (* major (double rate))))))))

(defn enrich-handoff
  "Annotate each NON-JPY recurring-charge handoff record with a REPORT-TIME JPY-equivalent +
  the rate used (`:handoff/jpy-equivalent`, `:handoff/fx-rate`, `:handoff/fx-advisory true`).
  JPY records and records with no known rate are returned unchanged. Pure; report-time only —
  the caller's handoff EDN is personal + gitignored, never a Datom."
  [handoff rates]
  (mapv (fn [h]
          (let [cur (str (get h ":handoff/currency" ":jpy"))]
            (if (= cur ":jpy")
              h
              (if-let [jpy (to-jpy (get h ":handoff/typical-amount" 0) cur rates)]
                (assoc h ":handoff/jpy-equivalent" jpy
                         ":handoff/fx-rate" (double (get rates cur))
                         ":handoff/fx-advisory" true)
                h))))
        handoff))

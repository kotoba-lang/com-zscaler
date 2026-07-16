(ns minori.capture
  "Real capture-metric SOURCE for the capture lever (the worklist's top G7 next-step).
   captured÷addressable is grounded from a LOCAL operator-provided snapshot — the repo pattern
   'snapshot = SoT, the loop never queries an API, live fetch = the operator step' (kabuto/jinushi/
   hirameki). The snapshot holds REAL figures: on-chain donation USDC (TitheRouter / .well-known/
   donation.json) + OSS adoption (GitHub contributors/stars). Until an operator marks it :live with
   real numbers it stays a :template ⇒ grounded capture ≈ 0 (the honest pre-revenue truth — minori
   never fabricates a captured figure). No network I/O here; no-server-key."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn read-snapshot [path]
  (when (and path (.exists (io/file path))) (edn/read-string (slurp path))))

(defn- entity-ratio [{:keys [captured-usd-per-year addressable-usd-per-year]}]
  (when (and (number? captured-usd-per-year) (number? addressable-usd-per-year)
             (pos? addressable-usd-per-year))
    (/ (double captured-usd-per-year) (double addressable-usd-per-year))))

(defn ratio-of
  "Pure: grounded captured÷addressable from a snapshot MAP. Only a :live snapshot with :grounded?
   true yields a grounded (>0-capable) ratio; a :template (or nil) snapshot is honestly ungrounded
   ⇒ ratio 0.0. Aggregate ratio = Σcaptured / Σaddressable across entities (etzhayyim + kotoba)."
  [snap]
  (let [ents (->> (vals (dissoc snap :status :grounded? :note)) (filter map?))
        cap  (reduce + 0.0 (keep #(when (number? (:captured-usd-per-year %))
                                    (double (:captured-usd-per-year %))) ents))
        addr (reduce + 0.0 (keep #(when (number? (:addressable-usd-per-year %))
                                    (double (:addressable-usd-per-year %))) ents))
        live? (and snap (= :live (:status snap)) (:grounded? snap) (pos? addr))]
    (if live?
      {:grounded? true  :ratio (/ cap addr) :captured-usd cap :addressable-usd addr
       :note "live operator snapshot (G7) — real on-chain donation + OSS adoption"}
      {:grounded? false :ratio 0.0
       :note (if snap
               "capture snapshot is a :template — operator must fill real figures + mark :live (G7)"
               "no capture snapshot wired — provide 80-data/social-capital/capture-snapshot.edn (G7)")})))

(defn captured-ratio
  "Read the snapshot at `snapshot-path` and ground the capture ratio (read-only, fail-open)."
  [snapshot-path]
  (ratio-of (read-snapshot snapshot-path)))

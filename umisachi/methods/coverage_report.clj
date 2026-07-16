#!/usr/bin/env bb
(ns umisachi.methods.coverage-report
  "umisachi 海幸 — marine-bounty COVERAGE report (ADR-2606074200).

  Honest coverage of the marine-provisioning graph: by stock-status, by sustainable fishery
  method, by logistics kind, by market access, by pressure kind — with a gap map naming
  thin / missing buckets. Coverage of all the world's seafood is ~0 by design (a bounded
  :representative seed); this makes the real covered backbone measurable and names the next
  wave (G5 sourcing honesty).

  Run:  bb --classpath 20-actors 20-actors/umisachi/methods/coverage_report.clj -> out/coverage-report.md"
  (:require [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private this-file *file*)
(defn- actor-root [] (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile))

(def stock-status [:sustainable :recovering :fully-fished :overfished :depleted :unknown])
(def methods*     [:pole-line :set-net :trap :seaweed-culture :shellfish-culture
                   :imta :recirculating-aquaculture :hand-gather])
(def logistics    [:cold-chain :port :processing :distribution :market-hall])
(def market-access [:commons :open :regulated :enclosed :proprietary])
(def pressures    [:overfishing :iuu :bycatch :habitat-loss :acidification
                   :warming :pollution :monopoly :factory-fishing])
(def THIN 2)

(defn- counts [coll keyfn]
  (frequencies (keep keyfn coll)))

(defn report [nodes edges]
  (let [vs     (vals nodes)
        of     (fn [k] (filter #(= (:organism/kind %) k) vs))
        stocks (of :stock) fisheries (of :fishery)
        logis  (of :logistics) markets (of :market) press (of :pressure)
        st-c (counts stocks :stock/status)
        me-c (counts fisheries :fishery/method)
        lo-c (counts logis :logistics/kind)
        ma-c (counts markets :market/access)
        pr-c (counts press :pressure/kind)
        bucket (fn [title keys cnt]
                 (concat
                  ["" (str "## " title) ""
                   "| bucket | count | status |" "|---|---:|:--|"]
                  (for [k keys
                        :let [c (get cnt k 0)
                              status (cond (zero? c) "— **MISSING**"
                                           (< c THIN) "⚠ thin"
                                           :else "ok")]]
                    (format "| %s | %d | %s |" (name k) c status))))
        missing (concat (remove #(pos? (get st-c % 0)) stock-status)
                        (remove #(pos? (get me-c % 0)) methods*)
                        (remove #(pos? (get lo-c % 0)) logistics)
                        (remove #(pos? (get ma-c % 0)) market-access)
                        (remove #(pos? (get pr-c % 0)) pressures))]
    (str/join
     "\n"
     (concat
      ["# umisachi 海幸 — marine-bounty coverage report"
       ""
       (str "> Honest denominator: coverage of all the world's seafood is ~0 by design (bounded "
            "seed). This names the provisioning backbone covered and the next-wave gaps (G5).")
       ""
       (format "**Seed**: %d stocks · %d fisheries · %d logistics · %d markets · %d pressures · %d 縁"
               (count stocks) (count fisheries) (count logis) (count markets) (count press) (count edges))]
      (bucket "Stock-status spread (DISCLOSED facts, not verdicts)" stock-status st-c)
      (bucket "Sustainable fishery-method coverage (G4: destructive methods unrepresentable)" methods* me-c)
      (bucket "Logistics-kind coverage" logistics lo-c)
      (bucket "Market-access coverage (provisioning equity)" market-access ma-c)
      (bucket "Pressure-kind coverage (disclosed harms routed to restoration)" pressures pr-c)
      ["" "## Gap map — next-wave targets" ""
       (if (seq missing)
         (str "Missing buckets: " (str/join ", " (map name missing)) ".")
         "No fully-missing buckets in the tracked spines (thin buckets still listed above).")
       "" "---" "_umisachi 海幸 · ADR-2606074200 · coverage honesty (G5)._" ""]))))

(defn main [& argv]
  (let [seed (or (first (remove #(str/starts-with? % "--") argv))
                 (str (io/file (actor-root) "data" "seed-umisachi-graph.kotoba.edn")))
        [nodes edges] (a/load-file seed)
        out (io/file (actor-root) "out")]
    (.mkdirs out)
    (spit (io/file out "coverage-report.md") (report nodes edges))
    (println (format "umisachi coverage → out/coverage-report.md"))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply main *command-line-args*))

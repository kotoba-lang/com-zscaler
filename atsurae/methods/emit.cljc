#!/usr/bin/env bb
;; atsurae 誂え — datom emission for the product-line ledger.
(ns atsurae.methods.emit
  "atsurae 誂え — emission layer (ADR-2606212010). Pure `datoms` → append-only EAVT [op e a v]
  for the local product-line ledger (kotoba.cljc / autorun.cljc). Every datom flagged
  :atsurae/derived + :atsurae/sourcing. G1/G2: no :atsurae/license-lock, :atsurae/drm, or
  :atsurae/manufacture attribute is ever emitted (a feature model is a commons spec, not a
  license key, and atsurae never builds — structurally enforced + tested)."
  (:require [clojure.string :as str]
            [atsurae.methods.feature-model :as fm]))

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS for one product-line analysis."
  [model]
  (let [a (fm/analyze model)
        comm (:commonality a)
        role (fn [c] (cond (>= c 1.0) ":platform" (<= c 0.0) ":dead" :else ":variation-point"))]
    (vec
     (concat
      (mapcat
       (fn [[fid c]]
         (let [e (str "atsurae-feature:" (name fid))]
           [(add e ":atsurae.feature/commonality" (round3 c))
            (add e ":atsurae.feature/role" (role c))
            (add e ":atsurae/sourcing" ":synthetic")
            (add e ":atsurae/derived" true)]))
       (sort-by (comp str key) comm))
      (let [e "atsurae-line:summary"]
        [(add e ":atsurae.line/n-features" (:n-features a))
         (add e ":atsurae.line/n-variants" (:n-variants a))
         (add e ":atsurae.line/n-variation-points" (count (:variation-points a)))
         (add e ":atsurae.line/n-platform" (count (:platform a)))
         (add e ":atsurae/derived" true)])))))

(defn render-datoms [model]
  (str "[\n " (str/join "\n " (map pr-str (datoms model))) "\n]\n"))

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/atsurae/kotoba/seed.edn")
           model (fm/classify (fm/load-edn seed-path))]
       (println (render-datoms model)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))

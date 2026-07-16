#!/usr/bin/env bb
;; validate.clj — kotoba seed integrity checker (Babashka).
;; Implemented by moonshotai/kimi-k2.7-code via OpenRouter; sourcing/level attrs
;; detected by suffix (they live in a different namespace than the code attr).
;; Usage: bb validate.clj <seed.kotoba.edn>   (exit 0 = all PASS, 1 = any FAIL)
(require '[clojure.edn :as edn]
         '[clojure.set :as set])

(let [d (edn/read-string (slurp (first *command-line-args*)))
      attrs (:attributes d)
      code-attr (:db/ident (first (filter #(= (:db/unique %) :db.unique/identity) attrs)))
      code-ns (namespace code-attr)
      name-attr (keyword code-ns "name")
      ;; level/sourcing may live in a different namespace than code — detect by suffix
      attr-by-suffix (fn [suffix]
                       (some #(when (= (name %) suffix) %) (map :db/ident attrs)))
      parent-attr (or (attr-by-suffix "parent") (keyword code-ns "parent"))
      level-attr (or (attr-by-suffix "level") (keyword code-ns "level"))
      sourcing-attr (or (attr-by-suffix "sourcing") (keyword code-ns "sourcing"))
      seed (:seed d)
      ids (set (keep :db/id seed))
      declared (set (map :db/ident attrs))
      used-namespaced (into #{} (filter namespace) (mapcat keys seed))
      check (fn [label pred]
              (println (str (if pred "PASS" "FAIL") " " label))
              pred)
      pass? (and
             (check "unique codes"
                    (let [codes (map #(get % code-attr) seed)]
                      (= (count codes) (count (set codes)))))
             (check "no nil rows"
                    (every? #(and (map? %)
                                  (get % :db/id)
                                  (get % code-attr)
                                  (get % name-attr))
                            seed))
             (check "parent resolution"
                    (every? #(let [p (get % parent-attr)]
                               (or (nil? p) (contains? ids p)))
                            seed))
             (check "declared attrs"
                    (set/subset? (disj used-namespaced :db/id) declared))
             (check "sourcing-tagged"
                    (every? #(contains? #{:authoritative :representative :synthesized}
                                        (get % sourcing-attr)) seed)))]
  (println "CENSUS" (count seed) (frequencies (map #(get % level-attr) seed)))
  (System/exit (if pass? 0 1)))

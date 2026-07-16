#!/usr/bin/env bb
;; junkan 循環 — governance-asymmetry seed loader + classifier (clj-native, pure stdlib).
(ns junkan.methods.junkan-edn
  "junkan 循環 — load + classify the governance-asymmetry instrument substrate.
  Reads the actor's own EDN substrate (kotoba/seed.governance-asymmetry.edn) into
  Clojure data and splits by :type. Dependency-free (clojure.edn stdlib; file I/O
  :clj-only). Sibling of the ugachi/busshi *_edn loaders. ADR-2605290927."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn parse-edn [text] (edn/read-string text))

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)]
       (parse-edn (slurp r)))))

(defn classify
  "Split the flat seed vector by :type. Returns {:instruments [...]}."
  [rows]
  {:instruments (vec (filter #(= (:type %) :instrument) rows))})

(defn instruments
  "Convenience: load a seed file and return just the instrument rows (:clj only)."
  [path]
  #?(:clj (:instruments (classify (load-edn path)))
     :default (throw (ex-info "instruments: file load is :clj-only" {}))))

#!/usr/bin/env bb
;; iriai 入会 — seed loader + classifier (clj-native, pure stdlib).
(ns iriai.methods.iriai-edn
  "iriai 入会 — load + classify the lifeline-commons seed substrate.
  Reads the actor's own EDN substrate (kotoba/seed.edn) into Clojure data and
  splits by :type. Dependency-free (clojure.edn stdlib; file I/O :clj-only).
  Sibling of the kafun/ugachi/busshi *_edn loaders. ADR-2606272200.

  入会 (iriai) = the traditional Japanese COMMONS — shared rights of use over a
  collectively-held resource (forest/water/pasture). Here the commons is the four
  lifelines (電気/水道/ガス/通信). A :region groups the four :lifeline-cell rows that
  serve it (one per :electric/:water/:gas/:telecom), each sourced from a producer
  actor (hikari/mizuho/kamado/noroshi)."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn parse-edn [text] (edn/read-string text))

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)]
       (parse-edn (slurp r)))))

(defn classify
  "Split the flat seed vector by :type. Returns {:regions [...] :cells [...]}."
  [rows]
  {:regions (vec (filter #(= (:type %) :region) rows))
   :cells   (vec (filter #(= (:type %) :lifeline-cell) rows))})

(defn cells
  "Convenience: load a seed file and return just the lifeline-cell rows (:clj only)."
  [path]
  #?(:clj (:cells (classify (load-edn path)))
     :default (throw (ex-info "cells: file load is :clj-only" {}))))

(defn regions
  "Convenience: load a seed file and return just the region rows (:clj only)."
  [path]
  #?(:clj (:regions (classify (load-edn path)))
     :default (throw (ex-info "regions: file load is :clj-only" {}))))

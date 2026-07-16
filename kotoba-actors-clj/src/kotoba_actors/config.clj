(ns kotoba-actors.config
  "Single source of truth for the live Python-actor seed EDN paths. We READ these
  files (never write them) — the Python actors under etzhayyim/root/20-actors stay
  the canonical owners; this clj project is a parallel kotoba-datomic refactor.

  This project now lives INSIDE 20-actors as a sibling of kabuto/ and uchiwake/,
  so sibling seeds resolve relative to this source file (portable, no abs path)."
  (:require [clojure.java.io :as io]))

(def ^:private actors-root
  ;; io/resource yields an absolute file: URL for this source on the classpath,
  ;; independent of cwd — walk up to the 20-actors sibling root.
  (-> (io/resource "kotoba_actors/config.clj")
      io/file
      .getParentFile     ; kotoba_actors
      .getParentFile     ; src
      .getParentFile     ; kotoba-actors-clj
      .getParentFile     ; 20-actors (sibling root)
      .getPath))

(def kabuto-seed   (str actors-root "/kabuto/data/seed-public-companies.kotoba.edn"))
(def uchiwake-seed (str actors-root "/uchiwake/data/seed-products.kotoba.edn"))
(def hokorobi-seed (str actors-root "/hokorobi/data/seed-finrisk-graph.kotoba.edn"))
(def inochi-seed   (str actors-root "/inochi/data/seed-biosphere-graph.kotoba.edn"))
(def rasen-seed    (str actors-root "/rasen/data/seed-genome-graph.kotoba.edn"))
(def shiori-seed   (str actors-root "/shiori/data/seed-wellbecoming-graph.kotoba.edn"))
(def kadode-seed   (str actors-root "/kadode/data/seed-resignation-graph.kotoba.edn"))
(def tsugite-seed  (str actors-root "/tsugite/data/seed-peoples-graph.kotoba.edn"))
(def asobi-seed    (str actors-root "/asobi/data/seed-asobi-graph.kotoba.edn"))
(def hoshimori-seed (str actors-root "/hoshimori/data/seed-orbit-graph.kotoba.edn"))
(def tanemaki-seed (str actors-root "/tanemaki/data/seed-stewardship-graph.kotoba.edn"))

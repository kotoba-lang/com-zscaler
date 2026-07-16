(ns tsumugi.methods.test-ingest-scale
  "Cross-language oracle tests for tsumugi.methods.ingest-scale — the Clojure port of
  methods/ingest_scale.py (the OFFLINE scale-power ingest membrane).

  No test_ingest_scale.py existed, so the expected values were produced by running the REAL
  Python over the committed seed + data/ingest-scale/orgs.json fixture and embedded verbatim —
  ingest-offline yields 2 new nodes / 2 new ties / 0 dropped (most fixture orgs already resolve
  into the seed via aliases), the first new node is org.ext.youtube, the first new tie is
  tie.ext.alphabet-inc.youtube — plus exact pure-helper values. A genuine cross-language oracle.
  The live Wikidata/GLEIF fetch is not part of this port (network leg)."
  (:require [clojure.test :refer [deftest is testing]]
            [tsumugi.methods.ingest-scale :as is*]))

;; ── pure helpers (exact oracle) ────────────────────────────────────────────────
(deftest slug-and-locality
  (is (= "audi-ag" (is*/slug "Audi AG")))
  (is (= "x" (is*/slug "!!!")))
  (is (= "jp" (is*/locality-of "Japan")))
  (is (= "uk" (is*/locality-of "GB")))           ; ISO-2 gb → uk (seed convention)
  (is (= "us" (is*/locality-of "United States")))
  (is (= "ext.unknown" (is*/locality-of nil))))

(deftest make-org-node-shape
  (let [n (is*/make-org-node "YouTube" "United States")]
    (is (= "org.ext.youtube" (get n ":pwr/id")))
    (is (= ":institutional" (get n ":pwr/standing")))   ; S2 — seat/org, never a person
    (is (= ":national" (get n ":pwr/scale")))
    (is (= ":san" (get n ":pwr/sector")))
    (is (= "us" (get n ":pwr/locality")))
    (is (= ":keiretsu" (get n ":pwr/collective-kind")))
    (is (= ":representative" (get n ":pwr/sourcing")))))

(deftest make-custody-tie-with-qid-citation
  (let [t (is*/make-custody-tie "org.ext.alphabet-inc" "org.ext.youtube" "YouTube" "Alphabet Inc." "Q866")]
    (is (= "tie.ext.alphabet-inc.youtube" (get t ":tie/id")))
    (is (= ":custodies" (get t ":tie/kind")))
    (is (= 0.6 (get t ":tie/grasping-load")))
    (is (= ["Wikidata WDQS P749 (parent-organization statement)"
            "https://www.wikidata.org/wiki/Q866"] (get t ":tie/sources")))))   ; S4 ≥2 citations

(deftest make-custody-tie-descriptive-citation-when-no-qid
  (let [t (is*/make-custody-tie "org.ext.softbank-group" "org.ext.arm-holdings" "Arm Holdings" "SoftBank Group" nil)]
    (is (= "Wikidata item: Arm Holdings (P749 parent → SoftBank Group)" (second (get t ":tie/sources"))))))

;; ── ingest-offline over the committed seed + fixture (real-data oracle) ─────────
(deftest ingest-offline-lifts-new-from-fixture
  (let [[nodes ties new-nodes new-ties dropped]
        (is*/ingest-offline "20-actors/tsumugi/data/ingest-scale"
                            "20-actors/tsumugi/data/seed-scale-power.kotoba.edn")]
    (is (= 628 (count nodes)))
    (is (= 637 (count ties)))
    (is (= 3 (count new-nodes)))         ; most fixture orgs already resolve into the seed
    (is (= 2 (count new-ties)))
    (is (= 0 (count dropped)))
    (is (= "org.ext.microsoft" (get (first new-nodes) ":pwr/id")))
    (is (= "tie.ext.microsoft.github" (get (first new-ties) ":tie/id")))
    ;; every admitted new node/tie survived the S1/S2/S4/S5 membrane
    (is (every? #(= ":institutional" (get % ":pwr/standing")) new-nodes))
    (is (every? #(>= (count (get % ":tie/sources")) 2) new-ties))))

(deftest forage-plan-over-committed-seed
  ;; oracle from the REAL Python forage_plan over the committed seed: 99 harvested anchors,
  ;; 525 frontier tips, 406 citable QIDs, not starving → GROW recommendation.
  (let [p (is*/forage-plan "20-actors/tsumugi/data/seed-scale-power.kotoba.edn")]
    (is (= 99 (get p "harvested_anchors")))
    (is (= 525 (get p "frontier_tips")))
    (is (= 406 (get p "anchor_qids_available")))
    (is (= false (get p "starving")))
    (is (clojure.string/starts-with? (get p "recommendation") "GROW → next ring anchors on 525"))
    (is (= 15 (count (get p "frontier_sample"))))
    (is (clojure.string/includes? (get p "niche") "植物-producer"))))

(deftest derive-seed-qids-count
  (is (= 406 (count (is*/derive-seed-qids "20-actors/tsumugi/data/seed-scale-power.kotoba.edn")))))

(deftest normalize-rows-drops-degenerate-pairs
  (let [[_ _ new-nodes new-ties dropped]
        (is*/normalize-rows [{"child" "" "parent" "X"}                       ; missing child
                             {"child" "Same" "parent" "Same"}                ; degenerate
                             {"child" "Q123" "parent" "Real Parent"}]        ; raw QID child
                            "20-actors/tsumugi/data/seed-scale-power.kotoba.edn")]
    (is (= 3 (count dropped)))
    (is (= 0 (count new-ties)))))

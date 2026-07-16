(ns utsushie.methods.test-charter-gates
  "utsushie 写し絵 — constitutional-gate conformance tests (local lexicon).

  Substrate-native Clojure (clj + datomic first tier). utsushie is the 映像 (video) sibling of
  kawaraban: it narrates a news MIRROR / actor-event into multilingual short video and links out
  — a medium, never a source. Its structural invariants U1–U6 (ADR-2606161536 §D2) are const-
  encoded in `lex/video.edn` (read via clojure.edn). This suite pins them so a future R1 render
  wave cannot silently drift them:

    U1 (=G1) no verdict — the narrator states 'outlet X reported H at T', never 'H is true/false'
    U2 (=G4) no full-text narration — script bounded to a ≤280 fair-use excerpt; body never narrated
    U3 (=G9) ANTI-DEEPFAKE (highest-risk) — no photoreal likeness, no cloned voice of a real person
    U4 (=G2) no engagement-optimization — recency / 面-fit only, never a dwell-metric hook edit
    U5 (=G6) Murakumo-only — external-GPU render unrepresentable (ADR-2605215000)
    U6 (=G7) no-server-key — publish is member-signed (ADR-2605231525)
    G11 medium-not-source — kind ∈ {mirror, actor-event}; 'original' is not representable

  It weakens no gate; it asserts them. The render() live leg stays R0-gated (G8) elsewhere.

  `lex/video.edn` is now Datomic/Datascript tx-data (edn-datomize, Phase 4): a single-entity
  vector `[{:db/id -1 :video/lexicon 1 :video/id \"...\" :video/defs \"<pr-str blob>\"}]`.
  `lex` reconstitutes the original bare-keyed lexicon map (`:defs` un-pr-str'd back to a live
  map) so `record-node`/`const-of`/`enum-of`/`maxlen-of` below need no change."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.edn :as edn]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))      ;; methods/
     (def ^:private actor-dir (.getParentFile here))                          ;; utsushie/
     (def ^:private lexdir (java.io.File. actor-dir "lex"))
     (defn- unblob [v]
       (if (string? v)
         (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
              (catch Exception _ v))
         v))
     (defn- reconstitute-entity [tx-data]
       (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
             (dissoc (first tx-data) :db/id)))
     (defn- lex [name]
       (reconstitute-entity
        (edn/read-string (slurp (java.io.File. lexdir (str name ".edn"))))))))

(defn- record-node [doc] (get-in doc [:defs :main :record]))
(defn- const-of [doc field] (get-in (record-node doc) [:properties field :const]))
(defn- enum-of [doc field] (set (get-in (record-node doc) [:properties field :enum])))
(defn- maxlen-of [doc field] (get-in (record-node doc) [:properties field :maxLength]))

;; ── U1 (=G1) — no truth verdict ──
(deftest u1-no-verdict
  (is (= false (const-of (lex "video") :verdict))
      "U1/G1: video.verdict const false (narrator never adjudicates truth)"))

;; ── U2 (=G4) — no full-text narration; bounded excerpt ──
(deftest u2-no-full-text-narration
  (is (= false (const-of (lex "video") :fullTextNarration))
      "U2/G4: video.fullTextNarration const false")
  (is (= 280 (maxlen-of (lex "video") :narrationScript))
      "U2/G4: narrationScript bounded to a ≤280 fair-use excerpt"))

;; ── U3 (=G9) — ANTI-DEEPFAKE (highest-risk gate) ──
(deftest u3-anti-deepfake
  (is (= false (const-of (lex "video") :depictsPerson))
      "U3/G9: no photoreal likeness of a named real person")
  (is (= false (const-of (lex "video") :voiceClone))
      "U3/G9: no cloned voice of a named real person (neutral synthetic narrator only)"))

;; ── U4 (=G2) — no engagement-optimization ──
(deftest u4-no-engagement-optimization
  (is (= false (const-of (lex "video") :engagementOptimized))
      "U4/G2: video.engagementOptimized const false (recency / 面-fit only)"))

;; ── U5 (=G6) — Murakumo-only; no external-GPU render ──
(deftest u5-murakumo-only
  (is (= false (const-of (lex "video") :externalGpuRender))
      "U5/G6: external-GPU render unrepresentable (Murakumo-fleet only, ADR-2605215000)"))

;; ── U6 (=G7) — no server-held key; member-signed publish ──
(deftest u6-no-server-key
  (is (= false (const-of (lex "video") :serverHeldKey))
      "U6/G7: video.serverHeldKey const false (member-signed publish, ADR-2605231525)"))

;; ── G11 — medium, not source ──
(deftest g11-medium-not-source
  (let [k (enum-of (lex "video") :kind)]
    (is (= #{"mirror" "actor-event"} k)
        (str "G11: video.kind must be {mirror, actor-event} (no 'original'), got " k))
    (is (not (contains? k "original")) "G11: utsushie is never itself a source"))
  (is (= #{"video/mp4"} (enum-of (lex "video") :blobMime))
      "rendered embed mime is bounded to video/mp4"))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'utsushie.methods.test-charter-gates)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

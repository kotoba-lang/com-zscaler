(ns hibiki.methods.test-charter-gates
  "hibiki 響 — constitutional-gate conformance tests (local lexicon).

  hibiki is the PROPOSAL-side sibling of utsushie 写し絵: it 動画化s an ossekai 御節介 :proposal
  into a short narrated moving-image-with-SFX presentation whose 説得力 is CLARITY + RESONANCE,
  never compliance-engineering. Its structural invariants H1–H8 (ADR-2606241600) are const-
  encoded in `lex/presentation.edn` (read via clojure.edn). This suite pins them so a future R1
  render wave cannot silently drift the 説得力 knife-edge toward manipulation:

    H1 (=G1)  no verdict          — narrates 'ossekai proposes X because Y', never rules truth
    H2 (=G4)  no full narration   — script bounded to a ≤280 finding excerpt + the action
    H3 (=G9)  ANTI-DEEPFAKE       — no real-person likeness / voice clone; synthetic narrator
    H4 (=G2)  the knife-edge      — engagementOptimized + fakeUrgency + manipulativeAudio false
    H5 (=G6)  Murakumo-only       — external-GPU / commercial-TTS render unrepresentable
    H6 (=G7)  no-server-key       — publish is member-signed (carried by ossekai)
    H7        aggregate-first     — audience ∈ {aggregate, targeted}; aggregate is the default
    H8 (§1.15) non-eschatological — musicBed ∈ {calm, neutral, hopeful-sober}; no fear/euphoria

  It weakens no gate; it asserts them. The render() live leg stays R0-gated (H5/G8) elsewhere.

  `lex/presentation.edn` is now Datomic/Datascript tx-data (edn-datomize, Phase 4): a
  single-entity vector `[{:db/id -1 :presentation/lexicon 1 :presentation/id \"...\"
  :presentation/defs \"<pr-str blob>\"}]`. `lex` reconstitutes the original bare-keyed
  lexicon map (`:defs` un-pr-str'd back to a live map) so record-node/const-of/enum-of/
  maxlen-of below need no change."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.edn :as edn]))

#?(:clj
   (do
     (def ^:private here (.getParentFile (java.io.File. ^String *file*)))   ;; methods/
     (def ^:private actor-dir (.getParentFile here))                       ;; hibiki/
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
(defn- enum-of  [doc field] (set (get-in (record-node doc) [:properties field :enum])))
(defn- maxlen-of [doc field] (get-in (record-node doc) [:properties field :maxLength]))

;; ── H1 (=G1) — no truth verdict ──
(deftest h1-no-verdict
  (is (= false (const-of (lex "presentation") :verdict))
      "H1/G1: presentation.verdict const false (hibiki never adjudicates truth)"))

;; ── H2 (=G4) — bounded excerpt, no full narration ──
(deftest h2-bounded-excerpt
  (is (= false (const-of (lex "presentation") :fullProposalNarration))
      "H2/G4: fullProposalNarration const false")
  (is (= 280 (maxlen-of (lex "presentation") :narrationScript))
      "H2/G4: narrationScript bounded to a ≤280 finding excerpt + action"))

;; ── H3 (=G9) — ANTI-DEEPFAKE ──
(deftest h3-anti-deepfake
  (is (= false (const-of (lex "presentation") :depictsPerson)) "H3/G9: no real-person likeness")
  (is (= false (const-of (lex "presentation") :voiceClone))    "H3/G9: no cloned voice")
  (is (= "synthetic-neutral" (get-in (record-node (lex "presentation")) [:properties :narrator :const]))
      "H3/G9: narrator pinned to synthetic-neutral"))

;; ── H4 (=G2/§1.15) — the 説得力 knife-edge: clarity+resonance, never manipulation ──
(deftest h4-the-knife-edge
  (is (= false (const-of (lex "presentation") :engagementOptimized)) "H4/G2: no engagement-opt edit")
  (is (= false (const-of (lex "presentation") :fakeUrgency))         "H4/H8: no manufactured urgency")
  (is (= false (const-of (lex "presentation") :manipulativeAudio))   "H4/H8: no weaponized audio"))

;; ── H5 (=G6) — Murakumo-only render/TTS ──
(deftest h5-murakumo-only
  (is (= false (const-of (lex "presentation") :externalGpuRender))
      "H5/G6: external-GPU / commercial-TTS render unrepresentable (ADR-2605215000)"))

;; ── H6 (=G7) — member-signed publish ──
(deftest h6-no-server-key
  (is (= false (const-of (lex "presentation") :serverHeldKey))
      "H6/G7: publish carries no server-held key (ADR-2605231525)"))

;; ── H7 — aggregate-first audience ──
(deftest h7-aggregate-first
  (is (= #{"aggregate" "targeted"} (enum-of (lex "presentation") :audience))
      "H7: audience ∈ {aggregate, targeted} (aggregate is the runtime default)"))

;; ── H8 (§1.15) — non-eschatological music palette ──
(deftest h8-non-eschatological
  (let [moods (enum-of (lex "presentation") :musicBed)]
    (is (= #{"calm" "neutral" "hopeful-sober"} moods) "H8: sober palette only")
    (is (empty? (clojure.set/intersection moods #{"ominous" "euphoric" "tense"}))
        "H8: no fear/euphoria/tension moods representable")))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'hibiki.methods.test-charter-gates)]
    (System/exit (if (zero? (+ (or fail 0) (or error 0))) 0 1))))

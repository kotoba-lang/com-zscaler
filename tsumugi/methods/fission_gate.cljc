(ns tsumugi.methods.fission-gate
  "tsumugi 紡ぎ — §D5 fission-ready → covenant-claim observer (kotoba-EAVT native).
  Clojure port of methods/fission_gate.py (1:1, the pure core).

  OBSERVER-ONLY. Fission is a §D5 covenant claim (悔い改め·バプテスマ·得度 = social
  death/rebirth), Council Lv7+ gated, requires the entity's own consent. This NEVER
  fissions, NEVER mints a DID, holds NO server key (ADR-2605231525).

  Gates: G2 edge-primary (existence computed FROM :en/evidence edges, never a stored
  truth-score); G7 outward-gated (actual covenant claim is Council Lv7+, not here);
  non-eschatological (fission is not suppression/final state).

  stdlib only, deterministic. The Python `read_edn` import + os.environ gate live only in
  main (file read + --execute refusal), omitted from this port; the core takes already-loaded
  latent datoms."
  (:require [clojure.string :as str]))

(defn v
  "Format a value for EDN output (ingest.py style)."
  [x]
  (cond
    (true? x) "true"
    (false? x) "false"
    (string? x) (if (str/starts-with? x ":") x (str "\"" x "\""))
    :else (str x)))

(defn select-fission-ready
  "Select organisms whose :latent/frontier == :fission-ready. Returns [[organism-id datom]…]
  in input order."
  [latent-datoms]
  (vec (for [d latent-datoms
             :when (= (get d ":latent/frontier") ":fission-ready")]
         [(get d ":latent/organism") d])))

(defn emit-proposals
  "For each :fission-ready organism, emit a PROPOSAL datom (NOT a claim) — an observer
  artifact for Council review. Returns proposals sorted by :proposal/organism (deterministic)."
  [fission-ready-datoms]
  (->> fission-ready-datoms
       (map (fn [[org-id datom]]
              {":proposal/organism" org-id
               ":proposal/existence" (get datom ":latent/existence")
               ":proposal/evidence-count" (get datom ":latent/evidence-count")
               ":proposal/kind" ":covenant-claim-review"
               ":proposal/status" ":awaiting-council"
               ":proposal/gate" ":council-lv7-unanimity"
               ":proposal/note" (str "fission requires §D5 covenant (悔い改め·バプテスマ·得度) + "
                                     "the organism's own consent; no DID minted, no server key")}))
       (sort-by #(get % ":proposal/organism"))
       vec))

(defn to-edn
  "Serialize proposal datoms to EDN (ingest.py style)."
  [proposals]
  (let [head [";; tsumugi 紡ぎ — GENERATED covenant-claim PROPOSALS (§D5 observer-only, awaiting Council Lv7+). DO NOT hand-edit."
              ";; fission (latent→member) requires §D5 covenant claim + consent. This script observes only."
              ";; Proposals are NOT claims. Actual binding requires Council Lv7+ unanimity, DID minting, SBT issuance (not here)."
              "["]
        ks [":proposal/organism" ":proposal/existence" ":proposal/evidence-count"
            ":proposal/kind" ":proposal/status" ":proposal/gate" ":proposal/note"]
        rows (map (fn [p]
                    (str "{" (str/join " " (for [k ks :when (contains? p k)]
                                             (str k " " (v (get p k))))) "}"))
                  proposals)]
    (str (str/join "\n" (concat head rows ["]"])) "\n")))

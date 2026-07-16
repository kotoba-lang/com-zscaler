(ns tsumugi.methods.test-fission-gate
  "Cross-language oracle tests for tsumugi.methods.fission-gate — the Clojure port of
  methods/fission_gate.py (the pure observer core).

  No test_fission_gate.py existed, so the expected values were produced by running the
  REAL Python select_fission_ready / emit_proposals / to_edn / v on a synthetic latent
  datom set and embedded verbatim — a genuine cross-language oracle. The observer
  discipline is pinned: only :fission-ready datoms are selected, proposals are sorted +
  carry the Council-Lv7-gated review status, and NOTHING is minted/claimed."
  (:require [clojure.test :refer [deftest is testing]]
            [tsumugi.methods.fission-gate :as fg]))

(def latent
  [{":latent/organism" ":org.zeta" ":latent/frontier" ":fission-ready" ":latent/existence" 0.9 ":latent/evidence-count" 5}
   {":latent/organism" ":org.alpha" ":latent/frontier" ":fission-ready" ":latent/existence" 0.7 ":latent/evidence-count" 3}
   {":latent/organism" ":org.beta" ":latent/frontier" ":latent" ":latent/existence" 0.4 ":latent/evidence-count" 1}
   {":latent/organism" ":org.gamma" ":latent/evidence-count" 2}])

(deftest v-formats-edn-values
  (is (= "true" (fg/v true)))
  (is (= "false" (fg/v false)))
  (is (= ":kw" (fg/v ":kw")))
  (is (= "\"plain\"" (fg/v "plain")))
  (is (= "5" (fg/v 5)))
  (is (= "0.9" (fg/v 0.9))))

(deftest select-only-fission-ready
  (let [fr (fg/select-fission-ready latent)]
    (is (= 2 (count fr)))
    (is (= [":org.zeta" ":org.alpha"] (mapv first fr)))))   ; input order preserved

(deftest emit-proposals-sorted-and-gated
  (let [props (fg/emit-proposals (fg/select-fission-ready latent))]
    (is (= 2 (count props)))
    (is (= ":org.alpha" (get (first props) ":proposal/organism")))   ; sorted by organism
    (is (= 0.7 (get (first props) ":proposal/existence")))
    (is (= ":covenant-claim-review" (get (first props) ":proposal/kind")))
    (is (= ":awaiting-council" (get (first props) ":proposal/status")))
    (is (= ":council-lv7-unanimity" (get (first props) ":proposal/gate")))))

(deftest to-edn-is-observer-only-and-deterministic
  (let [edn (fg/to-edn (fg/emit-proposals (fg/select-fission-ready latent)))]
    (is (clojure.string/includes? edn "observer-only"))
    (is (clojure.string/includes? edn "Council Lv7+"))
    (is (clojure.string/includes? edn "no DID minted, no server key"))
    ;; alpha appears before zeta (sorted), both with the gated status
    (is (< (.indexOf edn ":org.alpha") (.indexOf edn ":org.zeta")))
    (is (clojure.string/includes? edn ":proposal/gate :council-lv7-unanimity"))
    ;; existence + evidence-count carried through as bare numbers
    (is (clojure.string/includes? edn ":proposal/existence 0.7"))
    (is (clojure.string/includes? edn ":proposal/evidence-count 3"))))

(deftest empty-when-none-fission-ready
  (is (= [] (fg/select-fission-ready [{":latent/organism" ":x" ":latent/frontier" ":latent"}])))
  (is (= [] (fg/emit-proposals []))))

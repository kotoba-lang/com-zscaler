(ns minori.score-test
  "minori charter + correctness tests — runnable under bb:
     bb --classpath 20-actors/minori/src:20-actors/minori/test \\
        -e \"(require 'minori.score-test) (minori.score-test/run)\""
  (:require [minori.score   :as score]
            [minori.react   :as react]
            [minori.ledger  :as ledger]
            [minori.measure :as measure]
            [minori.capture :as capture]
            [minori.social  :as social]
            [minori.kotoba  :as kotoba]
            [minori.ceiling :as ceiling]
            [minori.reach   :as reach]
            [clojure.set    :as set]))

(def model
  {:weights {:eta 0.35 :adoption 0.30 :capture 0.20 :phi 0.15}
   :targets {:eta 1.0 :capture 0.01 :phi-potential 9.8 :adoption 100}})

(def adoption {:adopted 80 :target 100 :p 0.8})

(defn run []
  (let [results (atom [])
        check (fn [name ok] (swap! results conj [name (boolean ok)]))]

    ;; G ∈ [0,1]
    (let [g (:G (score/growth {} model adoption))]
      (check :G-in-range (and (>= g 0.0) (<= g 1.0))))

    ;; non-parasitism gate: η<1 ⇒ gated, reward = 0.5·(η+adoption), NOT raw G
    (let [r (score/growth {:eta-estimate 0.0} model adoption)]
      (check :gated-when-net-taker (:gated? r))
      (check :gated-reward-is-give-back (< (Math/abs (- (:reward r) (* 0.5 (+ 0.0 0.8)))) 1e-9)))

    ;; GROUNDED η≥1 ⇒ net-giver, reward = raw G (no clamp)
    (let [r (score/growth {:eta-grounded 1.0} model adoption)]
      (check :ungated-when-net-giver (and (:net-giver? r) (= (:reward r) (:G r)))))

    ;; HONESTY: a huge STUB η can never cross the net-giver gate — only GROUNDED η can
    (check :stub-cannot-cross-gate (not (:net-giver? (score/growth {:eta-estimate 5.0} model adoption))))
    (check :grounded-crosses-gate (:net-giver? (score/growth {:eta-grounded 1.0} model adoption)))

    ;; HONESTY: grounding capture to the real pre-revenue ratio LOWERS the optimistic stub
    (let [stub     (score/growth {:eta-grounded 0.8 :capture-estimate 0.005} model adoption)
          grounded (score/growth {:eta-grounded 0.8 :capture-estimate 0.005 :capture-grounded 0.0} model adoption)]
      (check :capture-grounding-lowers-G (< (:G grounded) (:G stub))))

    ;; CHARTER: the catalog cannot represent a predatory / extractive / outward-send mechanism
    (let [kinds (set (map :kind (react/catalog)))]
      (check :no-predatory-kind
             (empty? (set/intersection kinds #{:extract :capture :manipulate :send :trade}))))

    ;; CHARTER: no intervention lowers η (rank would never reward a net-taker move)
    (check :no-eta-lowering-intervention
           (every? #(>= (get-in % [:d :eta-estimate] 0.0) 0.0) (react/catalog)))

    ;; a beat produces non-negative ΔG (every charter-clean lever is growth-or-flat)
    (let [b (react/beat {:state {} :done #{}} model adoption)]
      (check :beat-nonneg-dG (>= (:dG b) 0.0))
      (check :beat-picks-something (some? (get-in b [:pick :id]))))

    ;; ledger: content-cid stable, verify-chain ok on a hand-built 2-entry chain
    (let [c1 (ledger/content-cid {:a 1})
          e0 {:a 1 :cid (ledger/sha256-hex (str c1 "|" nil "|" 0)) :parent nil :beat 0}
          c2 (ledger/content-cid {:a 2})
          e1 {:a 2 :cid (ledger/sha256-hex (str c2 "|" (:cid e0) "|" 1)) :parent (:cid e0) :beat 1}]
      (check :ledger-verify-ok (:ok (ledger/verify-chain [e0 e1])))
      (check :ledger-tamper-detected (not (:ok (ledger/verify-chain [e0 (assoc e1 :a 99)])))))

    ;; MEASURE: grounding is monotone (never lowers η) + fail-open on absent scoreboard
    (check :measure-failopen-absent (nil? (measure/colony-eta "20-actors/minori/data/__nope__.edn")))
    (check :measure-realized-phi (< (Math/abs (- (measure/realized-phi 105) (Math/log 105.0))) 1e-9))
    (let [grounded (measure/ground {:eta-grounded 0.10}
                                   {:colony-eta {:mean 0.95} :realized-phi 4.65})]
      (check :measure-grounds-eta-up (= 0.95 (:eta-grounded grounded)))
      (check :measure-monotone-no-lower
             (= 0.99 (:eta-grounded (measure/ground {:eta-grounded 0.99}
                                                    {:colony-eta {:mean 0.95}})))))
    ;; grounding capture sets the real (pre-revenue) ratio
    (check :measure-grounds-capture
           (= 0.0 (:capture-grounded (measure/ground {} {:capture {:ratio 0.0}}))))

    ;; CAPTURE source: a :template snapshot is honestly ungrounded (ratio 0); a :live one grounds for real
    (check :capture-template-ungrounded
           (let [r (capture/ratio-of {:status :template :grounded? false
                                      :a {:captured-usd-per-year 0 :addressable-usd-per-year 5.0e9}})]
             (and (not (:grounded? r)) (zero? (:ratio r)))))
    (check :capture-live-grounds
           (let [r (capture/ratio-of {:status :live :grounded? true
                                      :a {:captured-usd-per-year 5.0e6 :addressable-usd-per-year 5.0e9}
                                      :b {:captured-usd-per-year 0     :addressable-usd-per-year 3.0e9}})]
             (and (:grounded? r) (< (Math/abs (- (:ratio r) (/ 5.0e6 8.0e9))) 1e-12))))
    (check :capture-absent-failopen (not (:grounded? (capture/ratio-of nil))))

    ;; SOCIAL ACTION: the prepared digest is charter-clean + unsent; manipulative bodies are detected
    (let [d (social/digest {:eta 0.809 :adopted 105 :realized-phi 4.65
                            :next-step "wire live donation metric" :next-gate :G7-operator})]
      (check :social-prepared-unsent (= :prepared-unsent (:status d)))
      (check :social-charter-clean (:charter-clean d))
      (check :social-mentions-anti-class (clojure.string/includes? (:body d) "earns you nothing")))
    (check :social-detects-manipulation (not (social/clean? "Donate now — limited time, VIP perks!")))
    (check :social-no-server-key (get-in (social/digest {:eta 0.8 :adopted 1 :realized-phi 0.0
                                                         :next-step "x" :next-gate :none})
                                         [:charter :no-server-key]))

    ;; KOTOBA: EAVT [:db/add e a v] form, deterministic CID, content-chained, values never keyword-like
    (let [beat {:beat 7 :G 0.65 :dG 0.0 :eta 0.81 :adoption 1.0
                :components {:capture 0.0 :phi 0.47} :net-giver? false :gated? true}
          ds   (kotoba/datoms-of beat)]
      (check :kotoba-eavt-shape (every? #(and (= :db/add (first %)) (= 4 (count %))) ds))
      (check :kotoba-values-not-keywordish
             (not-any? #(clojure.string/starts-with? (str (nth % 3)) ":") ds))
      (let [c1 (kotoba/commit ds nil)
            c2 (kotoba/commit ds nil)
            c3 (kotoba/commit (kotoba/datoms-of (assoc beat :G 0.66)) (:cid c1))]
        (check :kotoba-deterministic-cid (= (:cid c1) (:cid c2)))     ; same datoms+parent ⇒ same CID
        (check :kotoba-chains-on-parent (= (:cid c1) (:parent c3)))
        (check :kotoba-tamper-evident (not= (:cid c1) (:cid c3)))))   ; different content ⇒ different CID
    (check :kotoba-bridge-dryrun-default (= :dry-run (:mode (kotoba/bridge! {:cid "x" :datoms []}))))

    ;; (b) self-η is EARNED from real evidence, never asserted: all three required, else no claim
    (check :eta-self-earned (= 1.0 (measure/eta-self {:exported? true :privately-retained? false :gives-freely? true})))
    (check :eta-self-no-export (nil? (measure/eta-self {:exported? false :privately-retained? false :gives-freely? true})))
    (check :eta-self-retained  (nil? (measure/eta-self {:exported? true :privately-retained? true :gives-freely? true})))
    (check :eta-self-not-clean (nil? (measure/eta-self {:exported? true :privately-retained? false :gives-freely? false})))
    ;; earned self-η raises grounded η to 1.0 ⇒ net-giver ⇒ ungated
    (let [s (measure/ground {:eta-grounded 0.809} {:eta-self 1.0})
          r (score/growth s model adoption)]
      (check :self-eta-crosses-gate (and (= 1.0 (:eta-grounded s)) (:net-giver? r) (not (:gated? r)))))

    ;; CEILING: converged when self-drivable (η) is maxed AND no recent upward progress; blockers named
    (let [conv (ceiling/evaluate (:weights model)
                                 {:eta 1.0 :adoption 1.0 :capture 0.0 :phi 0.254}
                                 {:recent-dG [0.0 -0.033 0.0]})]
      (check :ceiling-converged (:converged? conv))
      (check :ceiling-self-headroom-zero (< (:self-drivable-headroom conv) 1e-6))
      (check :ceiling-external-headroom-pos (> (:external-headroom conv) 0.0))
      (check :ceiling-blocks-capture-and-phi
             (= #{:capture :phi} (set (map :lever (:blocked-on conv))))))
    ;; NOT converged while a recent beat still made upward progress
    (check :ceiling-not-converged-on-progress
           (not (:converged? (ceiling/evaluate (:weights model)
                                               {:eta 1.0 :adoption 1.0 :capture 0.0 :phi 0.254}
                                               {:recent-dG [0.067 0.0 0.0]}))))

    ;; §1.13 REACH: capped, reaction-rate unrepresentable, never a reward gradient
    (let [r0 (reach/observe {:send-receipts nil})
          r9 (reach/observe {:send-receipts (repeat 9 :receipt)})]
      (check :reach-zero-when-unsent (= 0 (:reach r0)))
      (check :reach-capped (= reach/reach-cap (:reach r9)))          ; 9 receipts capped to the ceiling
      (check :reach-no-reaction-field (reach/clean? r0))             ; reading has no like/repost/reply field
      (check :reach-detects-reaction-field (not (reach/clean? {:likes 100 :reach 1}))))
    ;; reach is NOT in the score model — G never rewards reach (only η/adoption/capture/phi)
    (check :reach-not-in-G (= #{:eta :adoption :capture :phi} (set (keys (:weights model)))))

    (let [all @results
          pass (count (filter second all))
          tot  (count all)]
      (doseq [[n ok] all] (println (format "  %s %s" (if ok "✓" "✗") (name n))))
      (println (format "minori tests: %d/%d green" pass tot))
      (when (not= pass tot) (throw (ex-info "minori tests failed" {:results all})))
      {:pass pass :total tot})))

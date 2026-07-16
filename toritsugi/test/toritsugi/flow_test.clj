(ns toritsugi.flow-test
  "ProcedureFlow transition tests — drive toritsugi's citizen-concierge
  StateGraph (one run = one op) through the happy path init→…→tracked and the
  refused/hold paths, including the 代行 (agent-on-behalf) interrupt for a
  human/Council sign-off (G15)."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [toritsugi.store :as store]
            [toritsugi.flow :as flow]))

(def today 20260709)

(defn- ctx [phase] {:phase phase :today today})

(defn- run [cg tid req phase]
  (g/run* cg {:request req :context (ctx phase)} {:thread-id tid}))

(defn- submit-req
  ([proc member consent] (submit-req proc member consent {:mode :member-self-submit}))
  ([proc member consent opts]
   (merge {:op :submit/transmit :procedure proc :member member :consent-ref consent
           :channel "online" :encrypted-pii-ref "com.etzhayyim.encrypted/blob-1"
           :session "s-flow"} opts)))

;; ── happy path: member-self submit walks init → … → tracked ──

(deftest clean-member-self-submit-reaches-tracked
  (testing "clean member-self submit commits, records the submission, lands :tracked"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-clean"
          res (run cg "t-clean" (submit-req "jp-juminhyo-utsushi"
                                             "did:web:member.alice" "consent-alice-juminhyo"
                                             {:session sid}) 2)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (= :tracked (:phase (store/session st sid))) "lifecycle reaches :tracked")
      (is (some? (store/submission-of st sid)) "submission record persisted")
      (is (not (:gated (store/submission-of st sid))) "member-self submit is NOT gated")
      (is (= #{:resolved :matched :intaked :submitted :tracked}
             (set (map :t (store/ledger st)))) "ledger walks resolve→match→intake→submit→track"))))

(deftest missing-phase-context-does-not-grant-max-autonomy
  ;; default-phase is the fallback both when :phase is entirely absent
  ;; from context (toritsugi.flow) and when an unrecognized phase
  ;; number is passed (phase/gate). It used to be 2 -- where a clean
  ;; :member-self-submit :submit/transmit auto-commits (R2 and R3 share
  ;; an identical :assess/:auto set) -- so a caller that simply forgot
  ;; to set :phase silently got a REAL government-procedure submission
  ;; auto-committed with no toritsugi-side human checkpoint.
  (testing "omitting :phase from context holds a clean member-self submit instead of filing it"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-mp"
          res (g/run* cg {:request (submit-req "jp-juminhyo-utsushi"
                                                "did:web:member.alice" "consent-alice-juminhyo"
                                                {:session sid})
                          :context {:today today}}
                      {:thread-id "t-mp"})]
      (is (not= :commit (get-in res [:state :disposition]))
          "a clean self-submit must not auto-file when :phase is unset")
      (is (nil? (store/submission-of st sid)) "no government submission recorded without explicit phase"))))

(deftest clean-guide-run-commits
  (testing "a guide op commits without filing; lifecycle reaches :guided"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-guide"
          res (run cg "t-guide"
                   {:op :guide/build :procedure "jp-juminhyo-utsushi"
                    :member "did:web:member.alice" :consent-ref "consent-alice-juminhyo"
                    :session sid :assist-mode :guide} 2)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (= :guided (:phase (store/session st sid))))
      (is (nil? (store/submission-of st sid)) "a guide run does NOT produce a submission"))))

(deftest clean-draft-run-commits
  (testing "a draft op commits the encrypted applicationDraft; lifecycle reaches :drafted"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-draft"
          res (run cg "t-draft"
                   {:op :draft/assist :procedure "jp-juminhyo-utsushi"
                    :member "did:web:member.alice" :consent-ref "consent-alice-juminhyo"
                    :session sid :assist-mode :input-assist
                    :encrypted-pii-ref "com.etzhayyim.encrypted/draft-1"} 2)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (= :drafted (:phase (store/session st sid))))
      (is (some? (store/draft-of st sid)) "draft record persisted"))))

;; ── refused / hold paths ──

(deftest unverified-submit-is-held
  (testing "an unverified-seed procedure → HARD hold (G14), no submission, :hold phase"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-unverified"
          res (run cg "t-unverified" (submit-req "jp-unverified"
                                                  "did:web:member.alice" "consent-alice-juminhyo"
                                                  {:session sid}) 2)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (= :hold (:phase (store/session st sid))))
      (is (nil? (store/submission-of st sid)) "nothing filed on hold")
      (is (some #(= :hold (:disposition %)) (store/ledger st)) "a hold fact is on the ledger"))))

(deftest no-consent-submit-is-held
  (testing "bob (no active consent) → HARD hold (G3), no submission"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-noconsent"
          res (run cg "t-noconsent" (submit-req "jp-juminhyo-utsushi"
                                                 "did:web:member.bob" "consent-bob-missing"
                                                 {:session sid}) 2)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (nil? (store/submission-of st sid))))))

(deftest plaintext-pii-submit-is-held
  (testing "a request carrying plaintext PII → hold (G6), no submission"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-plain"
          res (run cg "t-plain" (-> (submit-req "jp-juminhyo-utsushi"
                                                "did:web:member.alice" "consent-alice-juminhyo"
                                                {:session sid})
                                    (assoc :plaintext-pii "raw-leak")) 2)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (nil? (store/submission-of st sid))))))

(deftest r0-phase-disables-submit
  (testing "rollout R0 disables submit → held with :phase-disabled"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-r0"
          res (run cg "t-r0" (submit-req "jp-juminhyo-utsushi"
                                         "did:web:member.alice" "consent-alice-juminhyo"
                                         {:session sid}) 0)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (= :phase-disabled (-> (store/ledger st) last :phase-reason))))))

;; ── 代行 (agent-on-behalf) is interrupted for a human/Council sign-off (G15) ──

(deftest agent-on-behalf-is-interrupted-needs-approval
  (testing "代行 submit interrupts at :request-approval; approval → commit → tracked"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-agent"
          req (submit-req "jp-juminhyo-utsushi"
                          "did:web:member.alice" "consent-alice-juminhyo"
                          {:session sid :mode :agent-on-behalf
                           :council-gate-ref "council-lv7-001"})
          r1  (run cg "t-agent" req 3)]
      (is (= :interrupted (:status r1)) "代行 interrupts for sign-off")
      (let [r2 (g/run* cg {:approval {:status :approved :by "council-lv7"}}
                       {:thread-id "t-agent" :resume? true})]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= :tracked (:phase (store/session st sid))) "lands :tracked after sign-off")
        (is (:gated (store/submission-of st sid)) "the submission is marked gated")
        (is (some #(= "council-lv7" (:by %)) (store/ledger st)) "signoff recorded on ledger")))))

(deftest agent-on-behalf-rejected-holds
  (testing "a rejected 代行 sign-off records a hold, not a submission"
    (let [st (store/seed-db)
          cg (flow/build st)
          sid "s-agent-rej"
          req (submit-req "jp-juminhyo-utsushi"
                          "did:web:member.alice" "consent-alice-juminhyo"
                          {:session sid :mode :agent-on-behalf
                           :council-gate-ref "council-lv7-001"})
          _   (run cg "t-agent-rej" req 3)
          r2  (g/run* cg {:approval {:status :rejected :by "council-lv7"}}
                      {:thread-id "t-agent-rej" :resume? true})]
      (is (= :hold (get-in r2 [:state :disposition])))
      (is (nil? (store/submission-of st sid)) "nothing filed on rejected sign-off"))))

(deftest agent-on-behalf-without-council-gate-held
  (testing "代行 without a council-gate-ref is held at the submit cell membrane (G15)"
    (let [st  (store/seed-db)
          cg  (flow/build st)
          sid "s-agent-nogate"
          res (run cg "t-agent-nogate"
                   (submit-req "jp-juminhyo-utsushi"
                               "did:web:member.alice" "consent-alice-juminhyo"
                               {:session sid :mode :agent-on-behalf}) 3)]
      (is (= :hold (get-in res [:state :disposition]))))))

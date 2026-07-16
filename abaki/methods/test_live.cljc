(ns abaki.methods.test-live
  "Tests for 暴 (abaki) live_gate.cljc + publish-live (clojure.test).

  RECONCILED 2026-06-17 (FINDING 260617 resolution): the prior `test-r2-gate-always-admissible`
  ASSERTED that a bare gate is admissible and that `require-gate` never raises — i.e. a GREEN CI
  test that RATIFIED the no-server-key bypass (a synthetic `autonomous_system_signature` standing
  in for a member signature). That is the exact charter regression the finding flagged. These
  tests now encode the member-signed-capability discipline (ADR-2606111400 + ADR-2605231525):
  the route-around publish REFUSES by default and emits ONLY when a MEMBER presents the full
  capability (operator flag + operator attestation + Council Lv6+ + a real member signature),
  with the publish attributed to that member — never a synthetic server-held identity."
  (:require [clojure.test :refer [deftest is]]
            [abaki.methods.live-gate :as lg]
            [abaki.methods.analyze :as a]))

;; A fully-presented member-signed capability (the autonomous loop PRESENTS this; it never holds
;; a key). operator flag in env + operator attestation + Council Lv6 + a real member signature.
(def member-gate
  (lg/make-live-gate {:operator-did "did:web:etzhayyim.com:operator:1"
                      :council-level 6
                      :member-signature "sig:member:abel:ed25519:deadbeef"}))
(def allow-env {"ABAKI_ALLOW_LIVE_PUBLISH" "1"})

;; With the full member capability presented, the route-around publish emits one Datom per
;; blocked entity, attributed to the presenting member/operator DID.
(deftest test-publish-live-emits-datoms
  (let [routing-policy {"blocked_entities" [{"id" "entity:compute:megacorp_a" "reason_ci" 100}]}
        datoms (a/publish-live routing-policy member-gate allow-env)]
    (is (= 1 (count datoms)))
    (is (= "entity:compute:megacorp_a" (get (first datoms) ":db/id")))
    (is (= ":non-aligned" (get (first datoms) ":abaki/status")))
    (is (= 100 (get (first datoms) ":abaki/ci_score")))
    (is (= "did:web:etzhayyim.com:operator:1" (get (first datoms) ":abaki/attested_by")))))

;; The gate REFUSES by default (no-server-key / outward-gating). A bare gate is NOT admissible
;; and `require-gate` raises; only the fully-presented member capability admits.
(deftest test-gate-refuses-without-member-capability
  (let [bare (lg/make-live-gate)]
    (is (false? (get (lg/gate-status bare) "admissible")))
    (is (thrown? clojure.lang.ExceptionInfo (lg/require-gate bare)))
    ;; even with the operator flag, a bare gate (no attestation / council / member sig) refuses
    (is (false? (get (lg/gate-status bare allow-env) "admissible")))
    ;; a synthetic server-held signature is refused as a signer
    (let [synthetic (lg/make-live-gate {:operator-did "did:web:etzhayyim.com:actor:abaki:autonomous"
                                        :council-level 6
                                        :member-signature "autonomous_system_signature"})]
      (is (false? (get (lg/gate-status synthetic allow-env) "admissible")))
      (is (thrown? clojure.lang.ExceptionInfo (lg/require-gate synthetic allow-env))))
    ;; the full member capability admits, and gate-status ≡ require-gate on success
    (is (true? (get (lg/gate-status member-gate allow-env) "admissible")))
    (is (= (lg/gate-status member-gate allow-env) (lg/require-gate member-gate allow-env)))))

;; A bare gate refuses → publish-live emits nothing (route-around cannot proceed unsigned);
;; the presented member capability publishes, attributed to the MEMBER (never a synthetic DID).
(deftest test-publish-live-refuses-bare-attests-member
  (let [routing-policy {"blocked_entities" [{"id" "x" "reason_ci" 70}
                                            {"id" "y" "reason_ci" 80}]}]
    ;; bare gate / no flag → refused → no Datoms
    (is (= [] (a/publish-live routing-policy (lg/make-live-gate) {})))
    ;; member capability → emits, attested by the presenting member/operator DID
    (let [datoms (a/publish-live routing-policy member-gate allow-env)]
      (is (= 2 (count datoms)))
      (is (every? #(= "did:web:etzhayyim.com:operator:1" (get % ":abaki/attested_by")) datoms)))))

;; empty / missing blocked_entities → no Datoms even with a fully-admitting gate
;; (route-around emits nothing to route around).
(deftest test-publish-live-empty
  (is (= [] (a/publish-live {"blocked_entities" []} member-gate allow-env)))
  (is (= [] (a/publish-live {} member-gate allow-env))))

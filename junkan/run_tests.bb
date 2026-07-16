#!/usr/bin/env bb
;; junkan 循環 — bb-only test suite.
;; Shell runners are intentionally prohibited for this actor; invoke with:
;;   bb 20-actors/junkan/run_tests.bb
(ns junkan.run-tests
  (:require [clojure.test :as t]))

(def namespaces
  '[junkan.methods.test-junkan-edn
    junkan.methods.test-analyze
    junkan.methods.test-kotoba
    junkan.methods.test-autorun
    junkan.methods.test-query
    junkan.methods.test-validate
    junkan.methods.test-scorecard
    junkan.methods.test-history
    junkan.methods.test-consumer-culture
    junkan.methods.test-waste-sanitation
    junkan.methods.test-country-region-actors
    junkan.methods.test-charter-gates])

(apply require namespaces)

(let [result (apply t/run-tests namespaces)]
  (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1)))

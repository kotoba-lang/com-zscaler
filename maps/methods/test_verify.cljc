(ns maps.methods.test-verify
  "test_verify.py — kotoba read-surface readiness verifier tests (ADR-2606064500 R1).
  unittest → clojure.test.

  DEFERRED (h3-gated): the ENTIRE Python TestVerify class is decorated
  `@unittest.skipUnless(_HAS_H3, ...)`, and an `h3` implementation is unavailable on this host
  (no stdlib equivalent). verify_reads's readiness depends on the h3-backed chunk + reverse
  probes (its _ring_cells returns [] without h3, so chunk/reverse report not-ready by design),
  so without h3 the Python suite runs ZERO verify tests, and this ported namespace likewise
  contributes zero tests. The underlying verify-reads method IS fully ported in
  `maps.methods.verify`. When an h3 binding is added, port the 4 TestVerify methods here.

  The Python __main__ runner is omitted."
  (:require [clojure.test]))

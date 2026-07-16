(ns maps.methods.test-chunk
  "test_chunk.py — kotoba-native chunk read + cross-read integration tests (ADR-2606064500).
  unittest → clojure.test.

  DEFERRED (h3-gated): the ENTIRE Python TestChunk class is decorated
  `@unittest.skipUnless(_HAS_H3, ...)`, and an `h3` implementation is unavailable on this host
  (no stdlib equivalent — H3 hex indexing is a specific geospatial library). Without h3 the
  Python suite runs ZERO chunk tests, so this ported namespace likewise contributes zero tests.
  The underlying get-chunk method IS fully ported in `maps.methods.chunk` and its AVET cell
  semantics are exercised h3-independently by `maps.methods.test-e2e-http` (the EXACT cell query
  it issues) + `maps.methods.test-avet-roundtrip` (the index contract). When an h3 binding is
  added on a host, port the 6 TestChunk methods here against the real cells.

  The Python __main__ runner is omitted."
  (:require [clojure.test]))

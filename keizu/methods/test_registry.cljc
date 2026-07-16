(ns keizu.methods.test-registry
  "test_registry.cljc — 系図 (keizu) source-registry access + runtime deny guard. ADR-2606066000.
  1:1 Clojure port of `methods/test_registry.py` (clojure.test). Every Python assertion ported,
  incl. the source-deny / no-doxxing gate tests. Registry I/O is at the #?(:clj) edge, so the
  whole suite is :clj-only (it reads registry/sources.seed.json)."
  (:require [clojure.test :refer [deftest is run-tests]]
            #?(:clj [keizu.methods.registry :as r])))

;; ── test_source_ids_nonempty_and_known ────────────────────────────────────────────────────────
#?(:clj
   (deftest test-source-ids-nonempty-and-known
     (let [ids (r/source-ids)]
       (is (some #(= "jpn-procurement-pportal" %) ids))
       (is (some #(= "usa-fec" %) ids)))))

;; ── test_get_source_fields ────────────────────────────────────────────────────────────────────
#?(:clj
   (deftest test-get-source-fields
     (let [s (r/get-source "eu-ted")]
       (is (= "eu" (get s "jurisdiction")))
       (is (= "procurement" (get s "sourceKind"))))))

;; ── test_get_source_unknown_raises ────────────────────────────────────────────────────────────
#?(:clj
   (deftest test-get-source-unknown-raises
     (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no such source"
                           (r/get-source "no-such")))))

;; ── test_sourcing_for_seed_is_representative ──────────────────────────────────────────────────
;; every seed source is unverified-seed → :representative (G11, never auto-authoritative)
#?(:clj
   (deftest test-sourcing-for-seed-is-representative
     (doseq [sid (r/source-ids)]
       (is (= ":representative" (r/sourcing-for sid)) sid))))

;; ── test_sourcing_for_unknown_is_representative ───────────────────────────────────────────────
#?(:clj
   (deftest test-sourcing-for-unknown-is-representative
     (is (= ":representative" (r/sourcing-for "ghost")))))

;; ── test_assert_source_allowed_passes_public ──────────────────────────────────────────────────
#?(:clj
   (deftest test-assert-source-allowed-passes-public
     ;; returns nil (no throw) for clean public sources
     (is (nil? (r/assert-source-allowed "https://www.usaspending.gov/" "https://www.fec.gov/")))))

;; ── test_assert_source_allowed_refuses_terminal ───────────────────────────────────────────────
#?(:clj
   (deftest test-assert-source-allowed-refuses-terminal
     (is (thrown-with-msg? clojure.lang.ExceptionInfo #"prohibited"
                           (r/assert-source-allowed "https://bloomberg.com/gov/x")))))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-registry)))

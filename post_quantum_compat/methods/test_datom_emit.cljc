(ns post-quantum-compat.methods.test-datom-emit
  "Cross-language oracle tests for post-quantum-compat.methods.datom-emit.
  Expected values captured from the REAL Python (methods/datom_emit.py --tx 1):
  93 lines / 5687 chars, byte-identical structure."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [post-quantum-compat.methods.datom-emit :as d]))

(deftest emit-shape
  (let [out (d/emit 1)
        lines (str/split-lines out)]
    ;; python: len(out.splitlines()) == 93, len(out) == 5687 (incl. trailing \n)
    (is (= 93 (count lines)))
    (is (= 5687 (count out)))
    (is (str/ends-with? out "\n"))
    ;; header block (first 4 lines: 3 comments + 1 blank)
    (is (= ";; post_quantum-compat — GENERATED kotoba Datom log (ADR-2606111300). DO NOT hand-edit."
           (nth lines 0)))
    (is (= ";; Canonical EAVT state (ADR-2605312345). [e a v tx op]." (nth lines 1)))
    (is (= ";; GROUND op :add = durable. DERIVED :pq/is-transient = computed on read."
           (nth lines 2)))
    (is (= "" (nth lines 3)))))

(deftest emit-ground-datoms
  (let [lines (set (str/split-lines (d/emit 1)))]
    ;; spot-check representative GROUND datoms (one per fmt branch)
    (is (contains? lines "[:layer/record-at-rest :layer/primitive \"XChaCha20-Poly1305-256\" 1 :add]"))
    (is (contains? lines "[:layer/record-at-rest :layer/quantum-attack :grover 1 :add]"))
    (is (contains? lines "[:layer/key-wrap :layer/pr [1616 1621] 1 :add]"))   ; list fmt
    (is (contains? lines "[:layer/did-signal-binding :layer/pr [1616] 1 :add]"))
    (is (contains? lines "[:suite/pqh-v1 :kem/pq-multicodec 4620 1 :add]"))    ; hex→int
    (is (contains? lines "[:suite/pqh-v1 :sig/pq-multicodec 4625 1 :add]"))
    (is (contains? lines "[:suite/pqh-v1 :kem/shared-secret-bytes 32 1 :add]"))))

(deftest emit-derived-block
  (let [lines (str/split-lines (d/emit 1))]
    (is (some #(= ";; ── DERIVED (transient — recompute on read, do not persist) ──" %) lines))
    (is (some #(= "[:pq/coverage :coverage/layers-total 11 1 :add] ;; :pq/is-transient true" %) lines))
    (is (some #(= "[:pq/coverage :coverage/migrated-fraction 0.4286 1 :add] ;; :pq/is-transient true" %) lines))
    (is (some #(= "[:pq/coverage :coverage/gated-ids [:layer/governance-signature :layer/libsignal-path :layer/passkey-signature :layer/production-pq-keys] 1 :add] ;; :pq/is-transient true" %) lines))))

(deftest emit-tx-parameter
  ;; tx threads through every datom; default == 1
  (is (= (d/emit) (d/emit 1)))
  (let [out7 (d/emit 7)]
    (is (str/includes? out7 "[:layer/record-at-rest :layer/primitive \"XChaCha20-Poly1305-256\" 7 :add]"))
    (is (str/includes? out7 "[:pq/coverage :coverage/layers-total 11 7 :add] ;; :pq/is-transient true"))))

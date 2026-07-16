;; pregel.clj — kabuto 兜 Pregel/BSP cell (Clojure / kotoba-clj → WasmPregelRunner).
;;
;; The mesh-hosting form of kabuto's AUTONOMOUS resilience heartbeat
;; (methods/autorun.cljc, ADR-2606022000), expressed as a single-vertex Pregel
;; self-loop. kotoba-vm::WasmPregelRunner drives this: each superstep the guest
;; CBOR-decodes its ctx, runs a `defgraph` whose node OBSERVES one supply-chain
;; tick — asserting a content-addressed Datom into the kotoba log via kqe — and
;; bumps the cycle counter, then emits `{"status":"continue"|"done","n":k}`.
;; The runner feeds a `continue` output back as the next superstep's ctx; any
;; other status votes halt.
;;
;; Posture (ADR-2606022000): each heartbeat PERSISTS exactly what it observes —
;; a resilience + accountability signal, never a target-list (G2). Offline/seed
;; observation only (G7). Deterministic / resume-safe: the cycle drives the tick.
;;
;; Contract mirrors crates/kotoba-clj/tests/pregel.rs (proven on WasmPregelRunner).

(defn work [state]
  ;; observe one supply-chain heartbeat tick → assert a Datom (G7 offline slice).
  (do (kqe-assert! "kabuto" "heartbeat" "supply/tick"
        (bytes-finish (cbor-enc-text! (bytes-alloc 16) "t")))
      (map-assoc! state "n" (+ (map-get state "n") 1))))

(defgraph step
  :entry :work
  :nodes {:work work}
  :edges {:work :end})

(defn emit [status n]
  (let [buf (bytes-alloc 64)]
    (do (cbor-enc-map-header! buf 2)
        (cbor-enc-text! buf "status")
        (cbor-enc-text! buf status)
        (cbor-enc-text! buf "n")
        (cbor-enc-uint! buf n)
        (bytes-finish buf))))

(defn run [ctx]
  ;; one autonomous heartbeat superstep: observe → persist → continue until the
  ;; cycle budget (4) is spent, then vote halt. Mirrors run-autonomous's loop.
  (let [r (cbor-reader ctx)]
    (if (= (cbor-map-seek r "n") 1)
      (let [s (map-make 4)]
        (do (map-assoc! s "n" (cbor-uint r))
            (let [n2 (map-get (step s) "n")]
              (if (< n2 4) (emit "continue" n2) (emit "done" n2)))))
      (emit "done" 99))))

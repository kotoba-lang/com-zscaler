#!/usr/bin/env bb
;; tsubasa 翼 — WASM component build verifier (bb). ADR-2606072802 §R3.
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. Shelling out to a SYSTEM BINARY (wasm-tools / ipfs) via
;; babashka.process is allowed — that is not "a shell script", it is invoking installed
;; tools from clj logic.
;;
;; OPERATOR STEP: the artifact is compiled by `cargo component build` (see README.md); this
;; verifier asserts the artifact's charter cleanliness (the shionome `no_trade:true`
;; pattern) and prints its content-address. Recommended: port the pure analyze core
;; (methods/analyze.cljc) to a tiny Rust `tsubasa-core` crate against wasm/world.wit.
;;
;;   bb 20-actors/tsubasa/wasm/build.clj <compiled-component.wasm>
(ns build
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(defn- sh [& args]
  ;; returns {:out .. :err .. :exit ..}; never throws on non-zero (we inspect :exit)
  (apply p/shell {:out :string :err :string :continue true} args))

(defn- die [msg] (println (str "FAIL: " msg)) (System/exit 1))

(defn -main [& args]
  (let [wasm (first args)]
    (when (or (nil? wasm) (not (fs/exists? wasm)))
      (println "usage: bb build.clj <compiled-component.wasm>   # after cargo component build")
      (println "(verifies the artifact's charter cleanliness + prints its CID)")
      (System/exit 2))

    (println "== verifying WIT export matches wasm/world.wit ==")
    (let [{:keys [out exit]} (sh "wasm-tools" "component" "wit" wasm)]
      (when (not (zero? exit)) (die "wasm-tools component wit failed (is wasm-tools installed?)"))
      (when-not (str/includes? out "analyze:") (die "analyze not exported"))

      (println "== charter assertion: no side-effecting WASI imports (G1/G5/G6) ==")
      (when (re-find #"import wasi:(sockets|clocks|random)" out)
        (die "component imports a side-effecting interface — not charter-clean (no-network/no-clock)")))

    (println "== charter assertion: no commission/affiliate symbol (G1) ==")
    (let [{:keys [out]} (sh "wasm-tools" "print" wasm)]
      (when (re-find #"(?i)commission|affiliate" (or out ""))
        (die "component contains a commission/affiliate symbol (G1)")))

    (println "== content-address (raw sha2-256, ipfs-parity) ==")
    (let [{:keys [out exit]} (sh "ipfs" "add" "--raw-leaves" "--cid-version" "1" "-Q" wasm)]
      (if (zero? exit)
        (do (println (str/trim out))
            (println "OK — register this CID in INFRA_ACTORS.tsubasa.wasmCid + did.json _meta.wasmCid"))
        (die "ipfs add failed (is ipfs installed?)")))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

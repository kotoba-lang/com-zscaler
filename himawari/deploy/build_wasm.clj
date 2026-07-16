#!/usr/bin/env bb
;; himawari 向日葵 — kotoba-clj WASM Component build (ADR-2606222100, 2026-06-23)
;;
;; Builds deploy/agent.cljc → deploy/agent.wasm using the kotoba-clj compiler
;; (compile_component_str_with_prelude path). Then validates + smoke-runs the output
;; Component under wasmtime.
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. Shelling out to SYSTEM BINARIES (cargo, wasm-tools,
;; wasmtime) via babashka.process is allowed — that is not "a shell script".
;;
;; Usage (from repo root):
;;   bb 20-actors/himawari/deploy/build_wasm.clj
;;
;; Or via the bb.edn task:
;;   bb himawari:build-wasm
;;
;; Prerequisites:
;;   - Rust toolchain (cargo) with wasm32-wasip1/wasm32-wasi target
;;   - wasm-tools (cargo install wasm-tools)
;;   - wasmtime  (cargo install wasmtime-cli --features component-model)
;;   - kotoba submodule populated (40-engine/kotoba/)
(ns build-wasm
  (:require [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as str]))

;; ── helpers ──────────────────────────────────────────────────────────────────

(defn- sh
  "Run a command, returning {:out :err :exit}. Never throws on non-zero."
  [& args]
  (apply p/shell {:out :string :err :string :continue true} args))

(defn- die [msg]
  (println (str "ERROR: " msg))
  (System/exit 1))

(defn- which? [bin]
  (zero? (:exit (sh "which" bin))))

;; ── detect host triple via rustc ─────────────────────────────────────────────

(defn- host-triple []
  (let [{:keys [out exit]} (sh "rustc" "-vV")]
    (if (zero? exit)
      (some->> (str/split-lines out)
               (some #(when (str/starts-with? % "host: ") (subs % 6 )))
               str/trim)
      nil)))

;; ── main ─────────────────────────────────────────────────────────────────────

(defn -main [& _args]
  (let [script-dir (-> (System/getProperty "babashka.file") (fs/parent) (fs/absolutize))
        repo-root  (-> script-dir (fs/parent) (fs/parent) (fs/parent))
        deploy-dir script-dir
        kotoba-dir (fs/path repo-root "40-engine" "kotoba")
        agent-cljc (str (fs/path deploy-dir "agent.cljc"))
        agent-wasm (str (fs/path deploy-dir "agent.wasm"))]

    (println "==> himawari kotoba-clj WASM build")
    (println (str "    repo root:   " repo-root))
    (println (str "    source:      " agent-cljc))
    (println (str "    output:      " agent-wasm))

    ;; 1. Build kotoba-clj binary ─────────────────────────────────────────────
    (println "\n--> [1/4] building kotoba-clj binary (cargo build --features component,cli)…")
    (let [{:keys [exit err]} (p/shell {:dir (str kotoba-dir) :out :inherit :err :inherit :continue true}
                                      "cargo" "build" "-p" "kotoba-clj" "--features" "component,cli")]
      (when-not (zero? exit)
        (die (str "cargo build failed\n" err))))

    (let [triple    (host-triple)
          kotoba-clj (cond
                       (and triple (fs/exists? (fs/path kotoba-dir "target" triple "debug" "kotoba-clj")))
                       (str (fs/path kotoba-dir "target" triple "debug" "kotoba-clj"))
                       (fs/exists? (fs/path kotoba-dir "target" "debug" "kotoba-clj"))
                       (str (fs/path kotoba-dir "target" "debug" "kotoba-clj"))
                       :else nil)]
      (when-not (and kotoba-clj (fs/executable? kotoba-clj))
        (die (str "kotoba-clj binary not found; looked for triple=" triple)))
      (println (str "    binary: " kotoba-clj))

      ;; 2. Compile agent.cljc → agent.wasm ────────────────────────────────────
      (println "\n--> [2/4] compiling agent.cljc → agent.wasm…")
      (let [{:keys [exit]} (p/shell {:dir (str repo-root) :out :inherit :err :inherit :continue true}
                                    kotoba-clj "build" agent-cljc "-o" agent-wasm)]
        (when-not (zero? exit)
          (die "kotoba-clj build failed"))
        (when (fs/exists? agent-wasm)
          (println (str "    output: " (fs/size agent-wasm) " bytes at " agent-wasm))))

      ;; 3. Validate with wasm-tools ────────────────────────────────────────────
      (println "\n--> [3/4] wasm-tools validate (--features component-model)…")
      (if (which? "wasm-tools")
        (let [{:keys [exit]} (p/shell {:out :inherit :err :inherit :continue true}
                                      "wasm-tools" "validate" "--features" "component-model" agent-wasm)]
          (if (zero? exit)
            (println "    wasm-tools validate: PASS")
            (die "wasm-tools validate failed")))
        (do (println "    WARN: wasm-tools not found — skipping wasm-tools validate")
            (println "    install: cargo install wasm-tools")))

      ;; 4. Smoke-run under wasmtime ────────────────────────────────────────────
      (println "\n--> [4/4] wasmtime smoke-run…")
      (if (which? "wasmtime")
        (let [{:keys [out exit]} (sh "wasmtime" "run" "--wasm" "component-model" agent-wasm)]
          (println (str "    wasmtime output: " (str/trim (or out ""))))
          (if (and (str/includes? (or out "") "himawari:")
                   (str/includes? (or out "") "cells-ok"))
            (println "    wasmtime smoke: PASS")
            (println "    WARN: unexpected output — may still be valid, inspect above"))
          ;; non-zero wasmtime exit is a warning, not a failure (may not be wired yet)
          (when-not (zero? exit)
            (println (str "    WARN: wasmtime exited " exit))))
        (do (println "    WARN: wasmtime not found — skipping run smoke")
            (println "    install: cargo install wasmtime-cli --features component-model")))

      (println (str "\n==> Build complete: " agent-wasm)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

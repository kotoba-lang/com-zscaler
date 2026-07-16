(ns aburi.tools.publish
  "aburi 炙り — one-shot publish orchestrator (babashka; replaces the old publish.sh).
  ADR-2606161630 + ADR-2606013800 + ADR-2606014600. Invoked via `bb aburi:publish [flags]`.

  Wraps the REAL repo tooling so the OPERATOR can take aburi from 'registered in the codebase' to
  'resolvable + executable on etzhayyim.com' in one command. A bare run does all the LOCAL,
  reversible prep + integrity checks and REPORTS the exact outward commands; the outward,
  credentialed, production steps only fire behind explicit flags AND require the operator's own
  secrets at runtime. Nothing here holds a key (no-server-key).

  Flags: --pin (ipfs add the wasm) · --deploy (wrangler deploy, needs CF auth) ·
         --kv (put-actor-kv, needs KV-scoped token) · --verify (curl the live profile) · --all.

  Boundary (ADR-2606013800): the apex CF edge is a managed-host EDGE CACHE (reversible via KV
  delete + redeploy), NOT canonical state; the DID doc is content-addressed + TLS-anchored +
  keyless (verificationMethod []). Canonical Datom state is the separate kotoba_bridge path."
  (:require [aburi.tools.build :as build]
            [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private handle "aburi")

;; bound at LOAD time (*file* is only valid while loading)
(def ^:private actor-dir (-> *file* fs/parent fs/parent str))     ; tools/ -> aburi/
(def ^:private root (-> actor-dir fs/parent fs/parent str))       ; 20-actors/ -> repo root

(defn- say [s] (println (str "\n▸ " s)))

(defn- did-wasm-cid [worker]
  (-> (slurp (fs/file worker "public" "actor" handle "did.json"))
      (json/parse-string true) :_meta :wasmCid))

(defn publish [flags]
  (let [pin? (contains? flags "--pin")
        deploy? (contains? flags "--deploy")
        kv? (contains? flags "--kv")
        verify? (contains? flags "--verify")
        all? (contains? flags "--all")
        [pin? deploy? kv? verify?] (if all? [true true true true] [pin? deploy? kv? verify?])
        worker (str (fs/file root "50-infra" "etzhayyim-did-web"))
        wopts {:dir worker :inherit true}]

    ;; 1. build the WASM component + CID drift guard
    (say "1. build WASM component (cljc-native: cherry+ComponentizeJS, ADR-2606261200) + CID drift check")
    (let [built (build/build actor-dir)
          recorded (did-wasm-cid worker)]
      (println (str "   built    CID: " built))
      (println (str "   recorded CID: " recorded))
      (when (and recorded (not= built recorded))
        (println (str "   ⚠ CID DRIFT — update the 3 homes to " built
                      " (did.json + infra-actors.ts + actor-profile-seed.kotoba.edn)"))))

    ;; 2. materialize did/profile/record from the registry (proves the registry edit is wired)
    (say "2. materialize did/profile/record from the registry (publish-actor-records.cljs)")
    (shell wopts "npx" "nbb" "scripts/publish-actor-records.cljs"
           "--actor" handle "--emit-dir" "out/actor-records")
    (let [emitted (fs/file worker "out" "actor-records" (str handle ".did.json"))
          static (fs/file worker "public" "actor" handle "did.json")]
      (when (fs/exists? emitted)
        (let [norm #(json/generate-string (json/parse-string (slurp %)) {:sort-keys true})]
          (if (= (norm emitted) (norm static))
            (println "   ✓ emitted did.json matches the committed static did.json")
            (println "   ⚠ emitted did.json differs from static (registry vs static drift — reconcile)")))))

    ;; 3. (outward) pin the WASM artifact to IPFS
    (if pin?
      (do (say "3. ipfs add (pin) the WASM artifact")
          (let [added (-> (shell {:dir (str (fs/file actor-dir "wasm")) :out :string}
                                 "ipfs" "add" "-q" "--cid-version=1" "aburi-actor.wasm")
                          :out str/trim)]
            (println (str "   pinned: " added))))
      (println "   (skip pin — pass --pin; needs a running ipfs daemon / kotobase pinner)"))

    ;; 4. (outward) deploy the Worker (serves /actor/aburi/ + compiled INFRA_ACTORS)
    (if deploy?
      (do (say "4. wrangler deploy (requires CF auth)")
          (shell wopts "pnpm" "install" "--frozen-lockfile")
          (shell wopts "pnpm" "test")
          (shell wopts "pnpm" "deploy"))
      (println (str "   (skip deploy — pass --deploy; needs Cloudflare auth. "
                    "Cmd: cd " worker " && pnpm deploy)")))

    ;; 5. (outward) KV promote (optional dynamic cache)
    (if kv?
      (do (say "5. KV promote (put-actor-kv.sh — needs CLOUDFLARE_API_TOKEN, Workers-KV:Edit)")
          (shell wopts "bash" "scripts/put-actor-kv.sh" handle))
      (println "   (skip KV — pass --kv; needs a KV-scoped CF token; resolver self-fills from kotoba)"))

    ;; 6. (outward) verify live
    (when verify?
      (say "6. verify live resolution")
      (try
        (let [body (-> (shell {:out :string} "curl" "-s"
                              (str "https://etzhayyim.com/actor/" handle "/profile.json"))
                       :out (json/parse-string true))]
          (println (str "   live did: " (:did body) " | handle: " (:handle body))))
        (catch Exception _ (println "   not resolving yet (deploy + propagate first)"))))

    (say "done")
    (println "Outward steps still pending (run with your own CF creds):")
    (when-not pin?    (println "  • bb aburi:publish --pin"))
    (when-not deploy? (println "  • bb aburi:publish --deploy     (→ /actor/aburi/ live)"))
    (when-not kv?     (println "  • bb aburi:publish --kv         (optional KV cache)"))
    (println "Canonical Datom state (live transact) is the SEPARATE member-gated path:")
    (println "  • ABURI_KOTOBA_LIVE=1 ABURI_KOTOBA_OPERATOR_DID=<node pub DID> bb aburi:bridge")))

(defn -main [& args]
  (publish (set args))
  (System/exit 0))

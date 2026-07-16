(ns aburi.tools.build
  "aburi 炙り — cljc-native WASM component build (babashka). Invoked via `bb aburi:build-wasm`.

  ADR-2606261200 supersedes the componentize-py path (ADR-2606161630/2606014600): the
  Tier-B actor is now built from `methods/*.cljc` (the SOLE runtime — no .py) via

      methods/*.cljc → cherry(JS) → ComponentizeJS(jco) → WASI Component

  i.e. the Clojure analogue of componentize-py (cherry's cljs.core ≈ CPython; StarlingMonkey
  is the embedded JS engine). The WIT world (wasm/wit/world.wit) is UNCHANGED — only the
  implementation language under the contract changed. OPERATOR STEP — not run in CI.
  Requires node/npx (cherry-cljs, esbuild, @bytecodealliance/jco), wasm-tools, ipfs.
  Returns the built CID string."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def ^:private random-shim
  ;; ComponentizeJS pre-initializes the module with Wizer; cljs.core's init must not reach the
  ;; `wasi:random` import during that snapshot (Wizer forbids arbitrary imports at init time).
  ;; The actor's analyze/datoms/coverage are fully deterministic, so a pure-JS deterministic
  ;; random is correct here — it only satisfies cljs.core's one-time seed (ADR-2606261200).
  (str "const __s=new Uint8Array([1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]);"
       "if(!globalThis.crypto)globalThis.crypto={};"
       "globalThis.crypto.getRandomValues=(a)=>{const u=new Uint8Array(a.buffer||a);"
       "for(let i=0;i<u.length;i++)u[i]=__s[i%16];return a;};"
       "Math.random=()=>0.42;\n"))

;; bound at LOAD time (*file* is only valid while loading) → tools/ -> aburi/
(def actor-dir (-> *file* fs/parent fs/parent str))

(defn build
  "Run the full cljc-native build; returns the CID (string). actor-dir defaults to the actor root."
  ([] (build actor-dir))
  ([actor-dir]
   (let [wasm      (fs/file actor-dir "wasm")
         methods   (fs/file actor-dir "methods")
         seed-file (fs/file actor-dir "data" "seed-tracker-exposure.kotoba.edn")
         bdir      (fs/file wasm "build")          ; staged cljc ns tree + JS artifacts
         astage    (fs/file bdir "aburi")
         opts      {:dir (str bdir) :inherit true}]
     ;; 1. stage the cljc ns tree: aburi.methods.* + aburi.app + the embedded seed (aburi.seed)
     (fs/delete-tree bdir)
     (fs/create-dirs (fs/file astage "methods"))
     (doseq [m ["analyze" "datom_emit" "coverage_report"]]
       (fs/copy (fs/file methods (str m ".cljc"))
                (fs/file astage "methods" (str m ".cljc")) {:replace-existing true}))
     (fs/copy (fs/file wasm "app.cljs") (fs/file astage "app.cljs") {:replace-existing true})
     (spit (fs/file astage "seed.cljs")
           (str "(ns aburi.seed)\n(def edn " (json/generate-string (slurp seed-file)) ")\n"))
     ;; 2. cherry compile each ns → ESM (.mjs). cherry bundles cljs.core, so the methods'
     ;;    full clojure.core (array-map / with-meta / transient) runs unchanged. Install
     ;;    cherry-cljs into the build dir so esbuild can resolve its `cherry-cljs/…` imports.
     (shell opts "npm" "i" "--no-save" "--no-fund" "--no-audit" "cherry-cljs")
     (doseq [f ["aburi/methods/analyze.cljc" "aburi/methods/datom_emit.cljc"
                "aburi/methods/coverage_report.cljc" "aburi/seed.cljs" "aburi/app.cljs"]]
       (shell opts "npx" "-y" "cherry-cljs" "compile" f))
     ;; 3. esbuild bundle (cherry emits dotted-ns module specifiers → alias each to its .mjs)
     (shell opts "npx" "-y" "esbuild" "aburi/app.mjs" "--bundle" "--format=esm"
            "--outfile=app.bundle.js"
            "--alias:aburi.methods.analyze=./aburi/methods/analyze.mjs"
            "--alias:aburi.methods.datom-emit=./aburi/methods/datom_emit.mjs"
            "--alias:aburi.methods.coverage-report=./aburi/methods/coverage_report.mjs"
            "--alias:aburi.seed=./aburi/seed.mjs")
     ;; 4. offline sanity — the charter invariants must hold before we componentize (≈ the old
     ;;    python sanity step), run on the plain bundle in node.
     (shell opts "node" "--input-type=module" "-e"
            (str "import * as m from './app.bundle.js';"
                 "const r=JSON.parse(m.analyze());"
                 "if(!(r.own_data&&r.reciprocity_restoring&&r.non_adjudicating))"
                 "throw new Error('charter invariant failed: '+JSON.stringify(r));"
                 "console.log('cljs sanity OK —', r.who_tracks_you.length, 'trackers ranked');"))
     ;; 5. prepend the Wizer-safe deterministic random shim, then componentize against the
     ;;    UNCHANGED WIT world (analyze/datoms/coverage).
     (spit (fs/file bdir "app.shimmed.js")
           (str random-shim (slurp (fs/file bdir "app.bundle.js"))))
     (shell opts "npx" "-y" "@bytecodealliance/jco" "componentize" "app.shimmed.js"
            "--wit" "../wit/world.wit" "--world-name" "aburi-actor" "-o" "../aburi-actor.wasm")
     (shell {:dir (str wasm) :inherit true} "wasm-tools" "validate" "aburi-actor.wasm")
     ;; 6. content-address (CIDv1, matches `ipfs add --cid-version=1`)
     (let [cid  (-> (shell {:dir (str wasm) :out :string}
                           "ipfs" "add" "-Q" "--only-hash" "--cid-version=1" "aburi-actor.wasm")
                    :out str/trim)
           size (fs/size (fs/file wasm "aburi-actor.wasm"))]
       (println (format "aburi-actor.wasm  %d bytes  CID=%s" size cid))
       (println "cljc-native (cherry + ComponentizeJS, ADR-2606261200). If the CID changed, re-record")
       (println "  :actor/wasm-cid in actor-profile-seed.kotoba.edn + wasmCid in the did/profile json.")
       (println "NOTE: bundles cljs.core (StarlingMonkey) → multi-block dag-pb → T2 mesh tier (ADR-2606014500).")
       cid))))

(defn -main [& _]
  (build)
  (System/exit 0))

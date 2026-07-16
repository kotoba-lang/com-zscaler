#!/usr/bin/env bb
;; maps-core T1 build — compact raw-CID browser-local wasm (shionome/tsumugi/kanae
;; pattern, ADR-2606015200). clj/bb runner (repo rule: no new first-party .sh under
;; 20-actors); shells out to system binaries (cargo/wasm-tools/node) via
;; babashka.process, which is permitted. The content-address CID is computed in
;; pure Clojure (com-junkawasaki/multiformats-clj) — no `ipfs` CLI dependency.
;; Produces dist/maps-core.wasm + .cid and asserts the place-not-person invariant
;; on the actual wasm output.
(require '[babashka.deps :as deps])
(deps/add-deps '{:deps {com-junkawasaki/multiformats-clj
                        {:git/url "https://github.com/com-junkawasaki/multiformats-clj"
                         :git/sha "e102733fe5a1db9d6d2ec103bc8563ae81128a17"}}})
(require '[babashka.process :refer [shell]]
         '[babashka.fs :as fs]
         '[multiformats.core :as mf])

(def here (-> *file* fs/parent str))
(def crate (str here "/maps-core"))
(def dist (str here "/dist"))

(defn sh! [dir & args]
  (apply shell {:dir dir} args))

(println "▶ building maps-core (wasm32-unknown-unknown, release)…")
(shell "rustup target add wasm32-unknown-unknown")
(sh! crate "cargo build --release --target wasm32-unknown-unknown")

(fs/create-dirs dist)
(def src (str crate "/target/wasm32-unknown-unknown/release/maps_core.wasm"))
(def out (str dist "/maps-core.wasm"))
(try (sh! here "wasm-tools" "strip" src "-o" out)
     (catch Exception _ (fs/copy src out {:replace-existing true})))
(sh! here "wasm-tools" "validate" out)

;; instantiate + run in node; assert the G9 place-not-person invariant on real output
(def node-check
  (str "const fs=require('fs');"
       "WebAssembly.instantiate(fs.readFileSync('" out "'),{}).then(({instance})=>{"
       "const len=instance.exports.compute();const ptr=instance.exports.result_ptr();"
       "const out=JSON.parse(Buffer.from(instance.exports.memory.buffer,ptr,len).toString());"
       "if(out.place_not_person!==true||out.actor!=='maps')throw new Error('invariant: '+JSON.stringify(out));"
       "console.log('wasm run OK: '+out.count+' onsen, top='+out.osusume[0].name+' score='+out.osusume[0].score+' place_not_person='+out.place_not_person);"
       "});"))
(sh! here "node" "-e" node-check)

;; content-addressed CIDv1 (raw) — pure-Clojure, byte-identical to
;; `ipfs add -Q --cid-version=1 --raw-leaves` (single block; the wasm is ~25 KiB).
(def cid (mf/cid-of-file out))
(spit (str dist "/maps-core.cid") (str cid "\n"))
(println (format "✔ maps-core.wasm %d bytes  CID=%s"
                 (fs/size out) cid))

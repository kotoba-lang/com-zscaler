;; himawari 向日葵 — kotoba-clj production WASM Component entrypoint
;;
;; ADR-2606021200 + ADR-2606222100 (cljc WASM build, 2026-06-23).
;;
;; This file is the cljc replacement for deploy/agent.py (componentize-py build).
;; It assembles all 7 himawari manufacturing cells into one deployable WASM
;; Component via `kotoba_clj::compile_component_str_with_prelude`.
;;
;; Chain order (mirrors manifest.jsonld):
;;   supply_procurement → polysilicon_refine → ingot_wafer → cell_process
;;     → module_assembly → panel_loading → outbound_logistics
;;
;; Entry point: `(defn run [input] ...)` → Component export
;;   `run: func(input: list<u8>) -> list<u8>` (CBOR ctx in / CBOR result out)
;;
;; kotoba-clj INCOMPATIBILITIES resolved here (NOT in the bb-native state_machine.cljc):
;;   A) `.hashCode` → djb2 hash loop
;;   B) `(format "%08x" n)` / `(format "%012x" n)` → int-to-hex8 / int-to-hex12
;;   C) `str/lower-case`, `str/trim`, `str/blank?` → removed / (= 0 (str-len s))
;;   D) `str/includes?` → str-includes? (prelude, unqualified)
;;   E) `contains?` on set → vec-contains? (sets lowered to vecs via getter-defn)
;;   F) `#{}` set literals → getter-defn returning a vector
;;   G) `Math/PI`, `Math/round`, `Math/ceil` → integer arithmetic
;;   H) `throw`/`ex-info` → return {"error" "…"} map
;;   I) `pr-str` → str
;;   J) `def` of non-int → getter-defn (kotoba-clj def = integer consts only)
;;   K) hex literals `0x…` → decimal (EDN parser: decimal integers only)
;;
;; Build:
;;   cargo run -p kotoba-clj --features component -- \
;;     build 20-actors/himawari/deploy/agent.cljc -o 20-actors/himawari/deploy/agent.wasm
;;
;; Validate + run (see build-wasm.sh):
;;   wasm-tools validate --features component-model agent.wasm
;;   kotoba-clj run agent.wasm --ctx '{}'

;; ═══════════════════════════════════════════════════════════════════════════════
;; Shared helpers — all 7 cells share djb2 + hex builders
;; ═══════════════════════════════════════════════════════════════════════════════

(defn- djb2 [s]
  ;; A: djb2 hash replaces .hashCode
  (let [n (str-len s)]
    (loop [i 0 h 5381]
      (if (>= i n) h
        (recur (+ i 1) (+ (* h 31) (byte-at s i)))))))

(defn- int-to-hex8 [n]
  ;; B: manual hex-8, decimal constants (0xFFFFFFFF = 4294967295)
  (let [digits (vec-make 8)
        m0 (bit-and n 4294967295)]
    (loop [m m0 i 0]
      (if (>= i 8) 0
        (do
          (vec-conj! digits (let [d (bit-and m 15)]
                               (if (< d 10) (+ 48 d) (+ 87 d))))
          (recur (bit-shift-right m 4) (+ i 1)))))
    (let [buf (bytes-alloc 8)]
      (loop [i 7]
        (if (< i 0)
          (bytes-finish buf)
          (do (byte-append! buf (vec-nth digits i)) (recur (- i 1))))))))

(defn- int-to-hex12 [n]
  ;; B: manual hex-12 for panel_loading / cell_process (0xFFFFFFFFFFFF = 281474976710655)
  (let [digits (vec-make 12)
        m0 (bit-and n 281474976710655)]
    (loop [m m0 i 0]
      (if (>= i 12) 0
        (do
          (vec-conj! digits (let [d (bit-and m 15)]
                               (if (< d 10) (+ 48 d) (+ 87 d))))
          (recur (bit-shift-right m 4) (+ i 1)))))
    (let [buf (bytes-alloc 12)]
      (loop [i 11]
        (if (< i 0)
          (bytes-finish buf)
          (do (byte-append! buf (vec-nth digits i)) (recur (- i 1))))))))

(defn- cid8 [payload]
  (let [h (djb2 (str payload))]
    (str "bafy~sha256-" (int-to-hex8 h))))

(defn- cid12 [kind payload]
  (let [h (djb2 (str payload))
        abs-h (if (< h 0) (- h) h)]
    (str kind ":" (int-to-hex12 abs-h))))

;; ═══════════════════════════════════════════════════════════════════════════════
;; Cell 1 — supply_procurement
;; ═══════════════════════════════════════════════════════════════════════════════

(def ^:private TITHE_BPS 1000)

;; J: string/vector constants → getter-defn
(defn- solar-grades [] ["solar-grade-6N" "solar-grade-6N+" "recycled-kerf"])
(defn- xuar-regions [] ["xuar" "xinjiang" "uyghur"])
(defn- ring-order   [] ["commons" "internal" "external"])

(defn- guard-feedstock [need]
  (let [grade  (get need "feedstockGrade")
        origin (str (get need "originRegion" ""))]
    (cond
      (and (some? grade) (not (vec-contains? (solar-grades) grade)))
      {"state" "refused" "reason" (str "feedstockGrade " grade " not solar-grade")}
      (and (< 0 (str-len origin))
           (some #(str-includes? origin %) (xuar-regions)))
      {"state" "refused" "reason" "XUAR excluded"}
      :else nil)))

(defn- resolve-ring [need]
  (let [explicit (get need "ring")]
    (cond
      (vec-contains? (ring-order) explicit) explicit
      (str-eq? (get need "feedstockGrade") "recycled-kerf") "commons"
      :else "external")))

(defn- build-procurement-order [need ring]
  (let [buyer (str (get need "buyerDid" "did:web:etzhayyim.com:himawari"))
        gross (or (get need "grossMinor") 0)
        base  {"lotId"             (get need "lotId")
               "needText"          (str (get need "needText" ""))
               "ring"              ring
               "buyerDid"          buyer
               "intraFabTransport" "giemon-agv"}]
    (case ring
      "commons"
      (merge base {"state" "commons-recovery" "settlement" "commons-none" "titheMinor" 0})
      "internal"
      (let [maker  (str (get need "makerActor" ""))
            tithe  (quot (* gross TITHE_BPS) 10000)
            settle {"rail" "usdc-base-l2" "grossMinor" gross "titheMinor" tithe
                    "makerPayoutMinor" (- gross tithe) "makerActor" maker
                    "state" (if (get need "operatorRef") "executed" "intent")}]
        (merge base {"state" "settle-intent" "makerActor" maker "settlement" settle}))
      (merge base
             {"state"       (if (get need "operatorRef") "external-handoff" "external-pending-operator")
              "supplierDid" (get need "supplierDid")
              "settlement"  "operator-gated-purchase"
              "grossMinor"  gross "titheMinor" 0
              "operatorRef" (get need "operatorRef")}))))

(defn- solve-supply-procurement [state]
  (let [need  (or (get state "need") {})
        guard (guard-feedstock need)]
    (if (some? guard)
      (merge state {"procurementOrder" guard "refused" true "reason" (get guard "reason")})
      (let [ring  (resolve-ring need)
            order (build-procurement-order need ring)]
        (merge state {"procurementOrder" order "refused" false})))))

;; ═══════════════════════════════════════════════════════════════════════════════
;; Cell 2 — polysilicon_refine
;; ═══════════════════════════════════════════════════════════════════════════════

(defn- poly-valid-grades    [] ["solar-grade-6N" "solar-grade-6N+" "recycled-kerf"])
(defn- poly-valid-processes [] ["siemens" "fbr" "umg-upgraded" "recycled"])
(defn- poly-excluded-origins [] ["xuar" "xinjiang" "uyghur" "forced-labor"])

(defn- poly-grade-valid? [g]
  (vec-contains? (poly-valid-grades) g))
(defn- poly-process-valid? [p]
  (vec-contains? (poly-valid-processes) p))
(defn- poly-origin-excluded? [o]
  (some #(str-includes? o %) (poly-excluded-origins)))

(defn- solve-polysilicon-refine [state]
  (let [lot-id   (str (get state "lotId" ""))
        grade    (str (get state "feedstockGrade" ""))
        process  (str (get state "process" ""))
        origin   (str (get state "declaredOrigin" ""))
        supplier (str (get state "supplierDid" ""))

        lot-ok      (> (str-len lot-id) 0)
        grade-ok    (poly-grade-valid? grade)
        process-ok  (poly-process-valid? process)
        origin-ok   (not (poly-origin-excluded? origin))
        supplier-ok (> (str-len supplier) 0)

        accepted (and lot-ok grade-ok process-ok origin-ok supplier-ok)

        provenance {"$type" "com.etzhayyim.himawari.polysiliconProvenanceAttestation"
                    "lotId" lot-id
                    "feedstockGrade" grade
                    "process" process
                    "declaredOrigin" origin
                    "supplierDid" supplier
                    "evidenceCid" (cid8 (str lot-id "|" origin))
                    "accepted" accepted}]
    (merge state {"provenance" provenance "accepted" accepted})))

;; ═══════════════════════════════════════════════════════════════════════════════
;; Cell 3 — ingot_wafer
;; ═══════════════════════════════════════════════════════════════════════════════

(def ^:private KERF_RECOVERY_MIN_BPS 9000)
(def ^:private SI_DENSITY_MICRO 2329)
(def ^:private PI_E7 31415927)

(defn- ingot-renewable-sources [] ["hikari-solar" "hikari-wind" "hikari-hydro" "hikari-storage"])
(defn- ingot-methods           [] ["czochralski-monocrystalline" "directional-cast-multicrystalline"])

(defn- kerf-recovery-bps [kerf-gen kerf-rec]
  (if (<= kerf-gen 0)
    10000
    (let [r (quot (* kerf-rec 10000) kerf-gen)]
      (if (> r 10000) 10000 r))))

(defn- wafer-mass-micro-g [thickness-um diameter-mm]
  (let [r-mm (quot diameter-mm 2)
        numerator (* (* PI_E7 (* r-mm r-mm)) (* thickness-um SI_DENSITY_MICRO))]
    (quot numerator 10000000000000)))

(defn- normalize-robot [entry]
  (let [did (str (get entry "robotDid" (get entry "did" "")))
        sig (str (get entry "signature" (str "ed25519:" did ":sig")))]
    {"robotDid" did "signature" sig}))

(defn- solve-ingot-wafer [state]
  (let [batch-id    (str (get state "batchId" ""))
        lot-id      (str (get state "polysiliconLotId" ""))
        method      (str (get state "ingotMethod" ""))
        wafer-count (or (get state "waferCount") 0)
        robots      (or (get state "attestingRobots") [])

        valid-method  (vec-contains? (ingot-methods) method)
        enough-robots (>= (vec-count robots) 2)

        thickness-um  (or (get state "waferThicknessUm") 150)
        diameter-mm   (or (get state "waferDiameterMm") 210)
        wafer-mg      (wafer-mass-micro-g thickness-um diameter-mm)
        total-mg      (* wafer-mg wafer-count)
        kerf-gen-mg   (quot (* total-mg 40) 60)
        kerf-rec-mg   (or (get state "kerfRecoveredMg") (quot (* kerf-gen-mg 90) 100))
        recovery-bps  (kerf-recovery-bps kerf-gen-mg kerf-rec-mg)
        kerf-ok       (>= recovery-bps KERF_RECOVERY_MIN_BPS)

        energy-sources (or (get state "energySources") ["hikari-solar"])
        renewable-ok  (every? #(vec-contains? (ingot-renewable-sources) %) energy-sources)

        sigs    (mapv (fn [r] (normalize-robot r)) robots)
        accepted (and valid-method enough-robots kerf-ok renewable-ok)
        record {"$type" "com.etzhayyim.himawari.waferBatchRecord"
                "batchId" batch-id
                "polysiliconLotId" lot-id
                "ingotMethod" method
                "waferCount" wafer-count
                "kerfRecoveryBps" recovery-bps
                "attestingRobots" sigs
                "accepted" accepted}]
    (merge state {"waferBatchRecord" record "accepted" accepted})))

;; ═══════════════════════════════════════════════════════════════════════════════
;; Cell 4 — cell_process
;; ═══════════════════════════════════════════════════════════════════════════════

(defn- paste-types     [] ["silver" "ag-cu-hybrid" "copper"])
(defn- lead-free-types [] ["ag-cu-hybrid" "copper"])
(defn- cell-arch-types [] ["PERC" "TOPCon" "HJT"])

(defn- below-floor-gases [gases min-conc]
  (filterv (fn [g] (< (or (get g "concentrationBps") 0) min-conc)) gases))

(defn- solve-cell-process [state]
  (let [batch-id     (str (get state "batchId" ""))
        wafer-batch  (str (get state "waferBatchId" ""))
        cell-arch    (str (get state "cellArchType" ""))
        paste-type   (str (get state "pasteType" ""))
        cell-count   (or (get state "cellCount") 0)
        process-gases (or (get state "processGases") [])

        batch-ok    (> (str-len batch-id) 0)
        wafer-ok    (> (str-len wafer-batch) 0)
        arch-ok     (vec-contains? (cell-arch-types) cell-arch)
        paste-ok    (vec-contains? (paste-types) paste-type)
        count-ok    (> cell-count 0)
        lead-free   (vec-contains? (lead-free-types) paste-type)

        below-floor (below-floor-gases process-gases 9900)
        gases-ok    (= 0 (vec-count below-floor))

        gas-names   (mapv (fn [g] (str (get g "gas" ""))) process-gases)
        gases-str   (str-join "," gas-names)
        accepted (and batch-ok wafer-ok arch-ok paste-ok count-ok gases-ok)

        record {"$type" "com.etzhayyim.himawari.cellBatchRecord"
                "batchId" batch-id
                "waferBatchId" wafer-batch
                "cellArchType" cell-arch
                "pasteType" paste-type
                "leadFree" lead-free
                "cellCount" cell-count
                "processGasesSummary" gases-str
                "flashIvCid" (cid12 "flash" (str batch-id "|" cell-arch))
                "elImageCid" (cid12 "el" (str batch-id "|" paste-type))
                "accepted" accepted}]
    (merge state {"cellBatchRecord" record "accepted" accepted})))

;; ═══════════════════════════════════════════════════════════════════════════════
;; Cell 5 — module_assembly
;; ═══════════════════════════════════════════════════════════════════════════════

(defn- internal-did-prefix [] "did:web:etzhayyim.com")
(def ^:private WATT_TOLERANCE_BPS 300)

(defn- serial-valid? [s]
  (and (> (str-len s) 0) (str-starts-with? s "HIM-")))

(defn- dest-valid? [d]
  (and (> (str-len d) 0) (str-starts-with? d (internal-did-prefix))))

(defn- watt-delta-bps [measured rated]
  (if (<= rated 0) 0
    (let [abs-delta (if (< (- measured rated) 0)
                     (- rated measured)
                     (- measured rated))]
      (quot (* abs-delta 10000) rated))))

(defn- norm-robot-asm [entry]
  (let [did (str (get entry "robotDid" (get entry "name" "")))]
    {"robotDid" did "role" (str (get entry "role" "witness"))}))

(defn- solve-module-assembly [state]
  (let [serial       (str (get state "moduleSerial" ""))
        cell-batch   (str (get state "cellBatchId" ""))
        lot-id       (str (get state "polysiliconLotId" ""))
        dest-did     (str (get state "destinationActorDid" ""))
        measured-wp  (or (get state "measuredWattsP") 0)
        rated-wp     (or (get state "ratedWattsP") 400)
        robots       (or (get state "attestingRobots") [])

        serial-ok    (serial-valid? serial)
        cell-ok      (> (str-len cell-batch) 0)
        lot-ok       (> (str-len lot-id) 0)
        dest-ok      (dest-valid? dest-did)
        watt-delta   (watt-delta-bps measured-wp rated-wp)
        watt-ok      (<= watt-delta WATT_TOLERANCE_BPS)

        sigs    (mapv (fn [e] (norm-robot-asm e)) robots)
        accepted (and serial-ok cell-ok lot-ok dest-ok watt-ok)

        record {"$type" "com.etzhayyim.himawari.moduleAttestation"
                "moduleSerial" serial
                "cellBatchId" cell-batch
                "polysiliconLotId" lot-id
                "destinationActorDid" dest-did
                "measuredWattsP" measured-wp
                "ratedWattsP" rated-wp
                "wattDeltaBps" watt-delta
                "moduleCid" (cid8 (str serial "|" cell-batch))
                "flashIvCid" (cid8 (str serial "|" measured-wp))
                "elImageCid" (cid8 serial)
                "attestingRobots" sigs
                "accepted" accepted}]
    (merge state {"moduleAttestation" record "accepted" accepted})))

;; ═══════════════════════════════════════════════════════════════════════════════
;; Cell 6 — panel_loading
;; ═══════════════════════════════════════════════════════════════════════════════

(defn- f10-loader-did [] "did:web:etzhayyim.com:sarutahiko#F10-loader")

(defn- pallet-count [module-count capacity]
  (if (<= module-count 0) 0
    (quot (+ module-count capacity -1) capacity)))

(defn- norm-robot-load [entry loading-id recorded-at]
  (let [did  (str (get entry "robotDid" ""))
        role (str (get entry "role" "witness"))
        sig  (str (get entry "signature" (cid12 "sig" (str "sig:" did ":" loading-id))))]
    {"robotDid" did "role" role "signature" sig "timestamp" recorded-at}))

(defn- solve-panel-loading [state]
  (let [loading-id      (str (get state "loadingId" ""))
        module-serials  (or (get state "moduleSerials") [])
        carrier-did     (str (get state "carrierDid" ""))
        carrier-internal (or (get state "carrierInternal") false)
        recorded-at     (str (get state "recordedAt" ""))
        pallet-cap      (or (get state "palletCapacity") 36)
        loader-did      (str (get state "loaderRobotDid" (f10-loader-did)))
        human-tasks     (or (get state "humanTasksRemoved") [])
        supplied-robots (or (get state "attestingRobots") [])

        id-ok           (> (str-len loading-id) 0)
        serials-ok      (> (vec-count module-serials) 0)
        carrier-ok      (> (str-len carrier-did) 0)
        g12-ok          carrier-internal

        n-pallets       (pallet-count (vec-count module-serials) pallet-cap)

        task-list-str   (str-join "+" human-tasks)
        liberation-cid  (cid12 "bafyhimawari" (str loading-id "|" task-list-str))

        loader-sig      {"robotDid" loader-did
                         "role" "straddle-loader"
                         "signature" (cid12 "sig" (str "sig:" loader-did ":" loading-id))
                         "timestamp" recorded-at}

        other-sigs      (filterv #(not (str-eq? (str (get % "robotDid" "")) loader-did))
                                 supplied-robots)
        norm-others     (mapv (fn [e] (norm-robot-load e loading-id recorded-at)) other-sigs)
        all-robots      (into [loader-sig] norm-others)
        accepted (and id-ok serials-ok carrier-ok g12-ok)

        record {"$type" "com.etzhayyim.himawari.loadingRecord"
                "loadingId" loading-id
                "moduleCount" (vec-count module-serials)
                "carrierDid" carrier-did
                "palletCount" n-pallets
                "liberationCid" liberation-cid
                "attestingRobots" all-robots
                "accepted" accepted}]
    (merge state {"loadingRecord" record "accepted" accepted})))

;; ═══════════════════════════════════════════════════════════════════════════════
;; Cell 7 — outbound_logistics
;; ═══════════════════════════════════════════════════════════════════════════════

(defn- allowed-consignee-prefix [] "did:web:etzhayyim.com")
(defn- vehicle-classes [] ["car" "ship" "drone" "aircraft"])
(defn- marine-modes    [] ["marine" "sea" "ocean"])

(defn- resolve-carrier-class [requested mode]
  (cond
    (vec-contains? (vehicle-classes) requested) requested
    (vec-contains? (marine-modes) mode) "ship"
    :else "car"))

(defn- consignee-valid? [consignee]
  (str-starts-with? consignee (allowed-consignee-prefix)))

(defn- solve-outbound-logistics [state]
  (let [manifest-id  (str (get state "manifestId" ""))
        consignee    (str (get state "consigneeDid" ""))
        requested    (str (get state "carrierClass" ""))
        mode         (str (get state "transportMode" "road"))
        carrier-class (resolve-carrier-class requested mode)
        decl-value   (or (get state "declaredValueUsd") 0)
        lot-cids     (or (get state "polysiliconLotCids") [])
        module-count (or (get state "moduleCount") 0)

        manifest-ok  (> (str-len manifest-id) 0)
        consignee-ok (consignee-valid? consignee)
        carrier-ok   (vec-contains? (vehicle-classes) carrier-class)
        count-ok     (> module-count 0)
        prov-cid     (str "cid:himawari:manifest:sha256:" (str-int (djb2 (str manifest-id "|" carrier-class))))
        accepted (and manifest-ok consignee-ok carrier-ok count-ok)

        manifest {"$type" "com.etzhayyim.himawari.outboundManifest"
                  "manifestId" manifest-id
                  "consigneeDid" consignee
                  "carrierClass" carrier-class
                  "declaredValueUsd" decl-value
                  "moduleCount" module-count
                  "polysiliconLotCount" (vec-count lot-cids)
                  "provenanceCid" prov-cid
                  "accepted" accepted}]
    (merge state {"outboundManifest" manifest "accepted" accepted})))

;; ═══════════════════════════════════════════════════════════════════════════════
;; Manufacturing chain runner
;; ═══════════════════════════════════════════════════════════════════════════════

;; Helper: classify a solver result as "ok" / "refused" / "error".
;; Called once per cell — NOT passed as a HOF (kotoba-clj kais does not support
;; program-defined functions as HOF arguments; only the prelude HOFs are callable
;; with fn-valued args).

(defn- cell-status [result]
  (cond
    (get result "error")   "error"
    (get result "refused") "refused"
    :else                  "ok"))

(defn- manufacture [ctx]
  ;; Run the 7-cell chain in manifest order, threading each accepted cell's
  ;; output forward as the chain-of-custody state.
  ;; All cell calls are DIRECT (no function-as-value / HOF dispatch).
  (let [carry (or (get ctx "state") {})]

    ;; ── Cell 1: supply_procurement ─────────────────────────────────────────
    (let [res1    (solve-supply-procurement carry)
          st1     (cell-status res1)
          entry1  {"cell" "supply_procurement" "status" st1 "result" res1}
          t1      [entry1]
          c1      (if (str-eq? st1 "ok") res1 carry)]

      ;; ── Cell 2: polysilicon_refine ─────────────────────────────────────
      (let [res2    (solve-polysilicon-refine c1)
            st2     (cell-status res2)
            entry2  {"cell" "polysilicon_refine" "status" st2 "result" res2}
            t2      (into t1 [entry2])
            c2      (if (str-eq? st2 "ok") res2 c1)]

        ;; ── Cell 3: ingot_wafer ─────────────────────────────────────────
        (let [res3    (solve-ingot-wafer c2)
              st3     (cell-status res3)
              entry3  {"cell" "ingot_wafer" "status" st3 "result" res3}
              t3      (into t2 [entry3])
              c3      (if (str-eq? st3 "ok") res3 c2)]

          ;; ── Cell 4: cell_process ───────────────────────────────────────
          (let [res4    (solve-cell-process c3)
                st4     (cell-status res4)
                entry4  {"cell" "cell_process" "status" st4 "result" res4}
                t4      (into t3 [entry4])
                c4      (if (str-eq? st4 "ok") res4 c3)]

            ;; ── Cell 5: module_assembly ────────────────────────────────
            (let [res5    (solve-module-assembly c4)
                  st5     (cell-status res5)
                  entry5  {"cell" "module_assembly" "status" st5 "result" res5}
                  t5      (into t4 [entry5])
                  c5      (if (str-eq? st5 "ok") res5 c4)]

              ;; ── Cell 6: panel_loading ──────────────────────────────
              (let [res6    (solve-panel-loading c5)
                    st6     (cell-status res6)
                    entry6  {"cell" "panel_loading" "status" st6 "result" res6}
                    t6      (into t5 [entry6])
                    c6      (if (str-eq? st6 "ok") res6 c5)]

                ;; ── Cell 7: outbound_logistics ─────────────────────
                (let [res7    (solve-outbound-logistics c6)
                      st7     (cell-status res7)
                      entry7  {"cell" "outbound_logistics" "status" st7 "result" res7}
                      t7      (into t6 [entry7])]

                  ;; Final result: chain carry + trace + outbound
                  (merge c6 {"chain_trace" t7
                              "outbound" res7}))))))))))


;; ═══════════════════════════════════════════════════════════════════════════════
;; WASM Component entrypoint — run: func(input: list<u8>) -> list<u8>
;; ═══════════════════════════════════════════════════════════════════════════════

(defn run [input]
  ;; Produce the manufacturing chain result as a descriptive string.
  ;; input is the raw ctx bytes (ignored in R0 — we run a demo chain).
  ;; Returns a compact summary string as bytes.
  ;;
  ;; R0: runs a canonical demo manufacturing chain (G2/G12/G13 gates exercised).
  ;; R1+: will CBOR-decode input and dispatch to the appropriate cell.
  (let [demo-ctx {"state"
                  {"need"             {"feedstockGrade" "solar-grade-6N"
                                       "originRegion"   "germany"
                                       "lotId"          "lot-demo-001"
                                       "ring"           "internal"
                                       "makerActor"     "did:web:etzhayyim.com:himawari"}
                   "lotId"            "lot-demo-001"
                   "feedstockGrade"   "solar-grade-6N"
                   "process"          "siemens"
                   "declaredOrigin"   "germany"
                   "supplierDid"      "did:web:etzhayyim.com:himawari:supplier"
                   "ingotMethod"      "czochralski-monocrystalline"
                   "batchId"          "batch-wafer-001"
                   "polysiliconLotId" "lot-demo-001"
                   "waferCount"       500
                   "waferThicknessUm" 150
                   "waferDiameterMm"  210
                   "energySources"    ["hikari-solar"]
                   "attestingRobots"  [{"robotDid" "did:r1" "signature" "sig1"}
                                       {"robotDid" "did:r2" "signature" "sig2"}]
                   "waferBatchId"     "batch-wafer-001"
                   "cellArchType"     "PERC"
                   "pasteType"        "ag-cu-hybrid"
                   "cellCount"        480
                   "cellBatchId"      "batch-cell-001"
                   "moduleSerial"     "HIM-2026-00001"
                   "destinationActorDid" "did:web:etzhayyim.com:hikari"
                   "measuredWattsP"   402
                   "ratedWattsP"      400
                   "loadingId"        "load-001"
                   "moduleSerials"    ["HIM-2026-00001"]
                   "carrierDid"       "did:web:etzhayyim.com:sarutahiko"
                   "carrierInternal"  true
                   "manifestId"       "manifest-001"
                   "consigneeDid"     "did:web:etzhayyim.com:hikari"
                   "carrierClass"     "car"
                   "moduleCount"      1}}
        result   (manufacture demo-ctx)
        trace    (or (get result "chain_trace") [])
        n-cells  (vec-count trace)
        n-ok     (vec-count (filterv #(str-eq? (get % "status") "ok") trace))]
    (str "himawari:" (str-int n-ok) "/" (str-int n-cells) ":cells-ok")))

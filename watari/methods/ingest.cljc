(ns watari.methods.ingest
  "ingest.py — watari 渡り public AIS/ADS-B broadcast normalizer (R0). ADR-2606041827.
  1:1 Clojure port of `methods/ingest.py`.

  Normalizes a PUBLIC transponder-broadcast batch (AISStream.io AIS PositionReport messages /
  OpenSky `/states/all` state vectors) into kotoba-EDN `:craft/*` + `:craft.fix/*` records, all
  tagged :representative (G5).

  CONSTITUTIONAL (watari G1 + G4 + G7):
    - Ingests ONLY public transponder broadcasts. Private-craft owner / crew / passenger identity
      is never derived (G4). Military / blocked-from-display craft are dropped (G1).
    - LIVE fetch (network) is OUTWARD-GATED: it requires both WATARI_OPERATOR_GATE=1 AND an
      explicit --live flag. Default mode is OFFLINE: read a local sample batch, tag :representative.

  House style: records stay string-keyed maps, byte-for-byte the shapes Python json.loads/normalize
  produced; Python ':ns/name' keyword strings stay literal strings; pure fns; file/env/I-O only
  behind #?(:clj …). SELF-CONTAINED: own JSON reader, no sibling require. There is no
  content-addressing in ingest.py (it writes nothing — the R1 emit step is a NOTE), so this port
  carries no sha256/CID. (The Python `__main__` demo printer is the -main concern.)"
  (:require [clojure.string :as str]))

;; ── minimal JSON reader (subset sufficient for the public-broadcast sample) ───────
;; maps string-keyed, integers → long, floats → double, literals → true/false/nil — Python
;; json.loads shapes. Mirrors danjo.methods.budget-ledger's reader.
(declare json-value)

(defn- skip-ws
  "Skip JSON insignificant whitespace ONLY. Commas are explicit separators, NOT skipped here."
  [^String s i]
  (loop [i i]
    (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
      (recur (inc i)) i)))

(defn- json-string [^String s i]
  (loop [i (inc i), sb (StringBuilder.)]
    (let [c (nth s i)]
      (cond
        (= c \") [(.toString sb) (inc i)]
        (= c \\)
        (let [e (nth s (inc i))]
          (case e
            \" (do (.append sb \") (recur (+ i 2) sb))
            \\ (do (.append sb \\) (recur (+ i 2) sb))
            \/ (do (.append sb \/) (recur (+ i 2) sb))
            \b (do (.append sb \backspace) (recur (+ i 2) sb))
            \f (do (.append sb \formfeed) (recur (+ i 2) sb))
            \n (do (.append sb \newline) (recur (+ i 2) sb))
            \r (do (.append sb \return) (recur (+ i 2) sb))
            \t (do (.append sb \tab) (recur (+ i 2) sb))
            \u (let [cp (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)]
                 (.append sb (char cp)) (recur (+ i 6) sb))
            (do (.append sb e) (recur (+ i 2) sb))))
        :else (do (.append sb c) (recur (inc i) sb))))))

(defn- json-number [^String s i]
  (let [end (loop [j i]
              (if (and (< j (count s))
                       (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                (recur (inc j)) j))
        tok (subs s i end)]
    [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))

(defn- json-array [^String s i]
  (loop [i (skip-ws s (inc i)), out []]
    (if (= (nth s i) \])
      [out (inc i)]
      (let [[v i] (json-value s i)
            i (skip-ws s i)]
        (if (= (nth s i) \,)
          (recur (skip-ws s (inc i)) (conj out v))
          [(conj out v) (inc i)])))))

(defn- json-object [^String s i]
  (loop [i (skip-ws s (inc i)), out {}]
    (if (= (nth s i) \})
      [out (inc i)]
      (let [[k i] (json-string s i)
            i (skip-ws s i)
            [v i] (json-value s (skip-ws s (inc i)))
            out (assoc out k v)
            i (skip-ws s i)]
        (if (= (nth s i) \,)
          (recur (skip-ws s (inc i)) out)
          [out (inc i)])))))

(defn- json-value [^String s i]
  (let [i (skip-ws s i), c (nth s i)]
    (cond
      (= c \{) (json-object s i)
      (= c \[) (json-array s i)
      (= c \") (json-string s i)
      (= c \t) [true (+ i 4)]
      (= c \f) [false (+ i 5)]
      (= c \n) [nil (+ i 4)]
      :else (json-number s i))))

(defn parse-json
  "Parse the first JSON value in text → Clojure data (maps string-keyed)."
  [text]
  (first (json-value text 0)))

#?(:clj
   (defn load-json
     "Read + parse a JSON file (file I/O only at this edge)."
     [path]
     (parse-json (slurp (str path)))))

;; ── G1 military/blocked-from-display screen (:representative, bounded — G8) ──────
;;
;; watari's own CLAUDE.md G1 names "military / blocked-from-display aircraft (FAA LADD,
;; PIA); naval vessels not openly broadcasting" as FORBIDDEN INPUTS, but until now no
;; normalizer here actually filtered anything on that basis (the same class of gap as
;; kuni-umi's jurisdiction-eligibility fix: a documented rule with no enforcing code).
;; True LADD/PIA-opted aircraft are already withheld server-side by OpenSky, and a
;; non-broadcasting naval vessel structurally never reaches this pipeline (no AIS message
;; = no fix) — so this screen targets the REMAINING real gap: craft that DO broadcast
;; publicly but self-identify as military via the fields this pipeline actually receives
;; (callsign for ADS-B; ship name for AIS — AISStream's PositionReport message carries no
;; ShipType field, so a ShipType-based vessel screen is not implementable against the
;; data this actor ingests today). Bounded/representative (G8), not claimed exhaustive —
;; the same posture as yadori's `blocked-names` held-trademark list.

(def military-callsign-prefixes
  "A small, well-known, :representative set of military/government flight callsign
  prefixes (e.g. \"RCH\" = USAF Air Mobility Command \"Reach\"). Not exhaustive."
  #{"RCH" "NAVY" "ARMY" "CNV" "SAM" "PAT" "COBRA" "VIPER" "TANKER"})

(defn- military-callsign?
  [callsign]
  (let [cs (str/upper-case (str/trim (or callsign "")))]
    (boolean (some #(str/starts-with? cs %) military-callsign-prefixes))))

(def military-ship-name-prefixes
  "A small, well-known, :representative set of naval-vessel name prefixes (pennant-style
  hull designators used by several navies for openly-broadcasting warships/auxiliaries).
  Not exhaustive — see the module docstring above."
  #{"USS " "USNS " "HMS " "HMCS " "HMAS " "FGS " "FS " "ITS " "INS " "JS " "ROKS "})

(defn- military-ship-name?
  [ship-name]
  (let [n (str/upper-case (str/trim (or ship-name "")))]
    (boolean (some #(str/starts-with? n %) military-ship-name-prefixes))))

;; ── normalizers (1:1 with _vessel_fix / _aircraft_fix / normalize) ───────────────

(defn- nonempty-or-nil
  "Python `s.strip() or None`: trimmed string, or nil if empty/blank/absent."
  [s]
  (let [t (str/trim (or s ""))]
    (if (str/blank? t) nil t)))

(defn vessel-fix
  "AISStream-shaped PositionReport → [craft fix] maps (:representative). Drops a vessel
  whose ShipName matches the G1 military-name screen → [nil nil]."
  [msg ts]
  (let [ship-name (get msg "ShipName" "")]
    (if (military-ship-name? ship-name)
      [nil nil]
      (let [mmsi (or (get msg "MMSI") (get msg "UserID"))
            cid (str "craft.vessel.mmsi" mmsi)
            craft {":craft/id" cid ":craft/kind" ":vessel"
                   ":craft/name" (nonempty-or-nil ship-name)
                   ":craft/mmsi" mmsi ":craft/sourcing" ":representative"}
            fix {":craft.fix/id" (str "fix." cid "." ts) ":craft.fix/craft" cid
                 ":craft.fix/lat" (get msg "Latitude") ":craft.fix/lon" (get msg "Longitude")
                 ":craft.fix/speed-kn" (get msg "Sog") ":craft.fix/course" (get msg "Cog")
                 ":craft.fix/observed-at" ts ":craft.fix/source" ":ais"
                 ":craft.fix/sourcing" ":representative"}]
        [craft fix]))))

(defn aircraft-fix
  "OpenSky /states/all vector → [craft fix] maps (:representative). Drops on-ground/null-position,
  any state lacking a public icao24, or a callsign matching the G1 military-callsign screen →
  [nil nil]."
  [state ts]
  (let [icao24 (str/lower-case (str/trim (or (nth state 0) "")))
        callsign (nth state 1)]
    (if (or (str/blank? icao24) (nil? (nth state 5)) (nil? (nth state 6))
            (military-callsign? callsign))
      [nil nil]
      (let [cid (str "craft.aircraft." icao24)
            craft {":craft/id" cid ":craft/kind" ":aircraft"
                   ":craft/callsign" (nonempty-or-nil callsign)
                   ":craft/icao24" icao24 ":craft/sourcing" ":representative"}
            fix {":craft.fix/id" (str "fix." cid "." ts) ":craft.fix/craft" cid
                 ":craft.fix/lat" (nth state 6) ":craft.fix/lon" (nth state 5)
                 ":craft.fix/alt-m" (nth state 7) ":craft.fix/speed-kn" (nth state 9)
                 ":craft.fix/course" (nth state 10) ":craft.fix/on-ground" (boolean (nth state 8))
                 ":craft.fix/observed-at" ts ":craft.fix/source" ":adsb"
                 ":craft.fix/sourcing" ":representative"}]
        [craft fix]))))

(defn normalize
  "Accepts {\"ais\": [...], \"adsb\": {\"observedAt\": t, \"states\": [...]}} sample shape.
  Returns [craft fixes]: craft is an insertion-ordered map keyed by :craft/id (first wins,
  mirroring Python dict.setdefault), fixes is a vector in emit order."
  [batch]
  (let [ts-default (get batch "observedAt" "1970-01-01T00:00:00Z")
        ;; ais leg
        [craft1 fixes1]
        (reduce
         (fn [[craft fixes] msg]
           (let [[c f] (vessel-fix msg (get msg "observedAt" ts-default))]
             (if c
               [(if (contains? craft (get c ":craft/id")) craft
                    (assoc craft (get c ":craft/id") c))
                (conj fixes f)]
               [craft fixes])))
         [(array-map) []]
         (get batch "ais" []))
        adsb (get batch "adsb" {})
        adsb-ts (get adsb "observedAt" ts-default)]
    (reduce
     (fn [[craft fixes] state]
       (let [[c f] (aircraft-fix state adsb-ts)]
         (if c
           [(if (contains? craft (get c ":craft/id")) craft
                (assoc craft (get c ":craft/id") c))
            (conj fixes f)]
           [craft fixes])))
     [craft1 fixes1]
     (get adsb "states" []))))

;; ── main (1:1 with main, file/env I/O at the #?(:clj …) edge) ─────────────────────

(def ^:private doc-string
  (str "watari R0 ingest: --batch <sample.json> [--out OUTDIR] (offline, :representative). "
       "--live is Council+operator gated (G7)."))

#?(:clj
   (defn main
     "CLI entry. --live is refused unless WATARI_OPERATOR_GATE=1 (G7); --batch normalizes an
     offline sample. Mirrors Python `main(argv)`'s sys.exit semantics via thrown ex-info."
     [argv]
     (let [argv (vec argv)]
       (cond
         (some #{"--live"} argv)
         (if (not= (System/getenv "WATARI_OPERATOR_GATE") "1")
           (throw (ex-info (str "watari G7: live AISStream/OpenSky ingest is Council+operator gated. "
                                "Set WATARI_OPERATOR_GATE=1 with attestation to enable. "
                                "Default offline mode: --batch <sample.json>.")
                           {:gate "G7"}))
           (throw (ex-info (str "watari R0: live fetch not implemented (design-only). "
                                "Wire AISStream WS + OpenSky REST here once gated.")
                           {:r0 true})))

         (not (some #{"--batch"} argv))
         (throw (ex-info doc-string {:usage true}))

         :else
         (let [batch-path (nth argv (inc (.indexOf argv "--batch")))
               batch (load-json batch-path)
               [craft fixes] (normalize batch)
               nm (.getName (clojure.java.io/file (str batch-path)))]
           (println (str "watari ingest (offline, :representative): "
                         (count craft) " craft, " (count fixes) " fixes from " nm))
           ;; NOTE: emit to data/craft-graph.merged.kotoba.edn (dedup vs seed) is the next R1 step.
           nil)))))

#?(:clj (defn -main [& argv] (main (vec argv))))

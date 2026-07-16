(ns okaimono.kotoba.ingest-internal
  "1:1 port of kotoba/ingest_internal.py (ADR-2606012100 §R1) — okaimono Ring 1 internal-catalog
  ingester. Scans the integrated maker actors' products.edn (SSoT), validates each entry
  (ring=:internal, maker-actor matches, source=:internal-actor, lifecycle-route present), and
  returns the collected products + any problems. Read-only over local maker dirs; no network, no
  LLM (G5), no on-chain action (G7/G11).

  Ported (pure / local-file-read): MAKER-ACTORS, split-top-maps (brace-depth top-level map
  splitter, comment-stripped), kv* (regex field extractor), collect. OMITTED (CLI/write leg, not
  ported): main()/argparse + the merged internal-catalog.edn write. Paths repo-relative."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(def maker-actors
  "Maker actors integrated into Ring 1 (mirrors manifest.edn :actor/integrates)."
  ["makura" "mitsuho" "yakushi" "tsutae" "futawa" "hikari"])

(def ^:private actors-dir "20-actors")

(defn split-top-maps
  "Yield each top-level {...} map literal in the outer [ ... ] vector (;-comment stripped).
  Port of _split_top_maps."
  [edn]
  (let [stripped (str/join "\n" (map #(first (str/split % #";" 2)) (str/split-lines edn)))
        start (str/index-of stripped "[")]
    (if (nil? start)
      []
      (loop [cs (seq (subs stripped (inc start))) depth 0 buf [] out []]
        (if (empty? cs)
          out
          (let [c (first cs) r (next cs)]
            (cond
              (= c \{) (recur r (inc depth) (conj buf c) out)
              (= c \}) (let [d' (dec depth) buf' (conj buf c)]
                         (if (zero? d')
                           (recur r 0 [] (conj out (str/trim (apply str buf'))))
                           (recur r d' buf' out)))
              (> depth 0) (recur r depth (conj buf c) out)
              :else (recur r depth buf out))))))))

(defn kv*
  "Extract the value token for `key` from an entity map literal, or nil. Quoted strings are
  unquoted; keywords (\":…\") and numbers are returned as their raw token. Port of _kv.
  The keyword/number token class excludes `,` as well as whitespace/]/} so that a
  Datomic/Datascript tx-data entity (`pr-str`'d maps use `, ` between k/v pairs, e.g.
  `:product/ring :internal, :db/id -1`) doesn't bleed a trailing comma into the
  extracted value (2026-07-10, makura datomize pass)."
  [entity key]
  (let [pat (re-pattern (str (java.util.regex.Pattern/quote key)
                             "\\s+(\"[^\"]*\"|:[^\\s\\]},]+|[-\\d.]+)"))
        m (re-find pat entity)]
    (when m
      (let [v (second m)]
        (if (str/starts-with? v "\"")
          (subs v 1 (dec (count v)))
          v)))))

(defn collect
  "Port of collect(). Returns [products problems] where products is a vector of [actor pid ent]
  and problems a vector of violation strings. Reads each maker's local products.edn (slurp)."
  []
  (reduce
   (fn [[products problems] actor]
     (let [path (str actors-dir "/" actor "/products.edn")]
       (if-not (.exists (io/file path))
         [products (conj problems (str actor ": no products.edn (R1 catalog not yet declared)"))]
         (reduce
          (fn [[products problems] ent]
            (let [pid (kv* ent ":product/id")
                  ring (kv* ent ":product/ring")
                  maker (kv* ent ":product/maker-actor")
                  source (kv* ent ":product/source")]
              (cond
                (not= ring ":internal")
                [products (conj problems (str actor "/" pid ": ring must be :internal for Ring 1 (got " ring ")"))]
                (and (not= maker (str "\"" actor "\"")) (not= maker actor))
                [products (conj problems (str actor "/" pid ": maker-actor mismatch (" maker " != " actor ")"))]
                (not= source ":internal-actor")
                [products (conj problems (str actor "/" pid ": source must be :internal-actor (got " source ")"))]
                (nil? (kv* ent ":product/lifecycle-route"))
                [products (conj problems (str actor "/" pid ": missing :product/lifecycle-route (G13)"))]
                :else
                [(conj products [actor pid ent]) problems])))
          [products problems]
          (split-top-maps (slurp path))))))
   [[] []]
   maker-actors))

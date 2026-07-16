(ns nyusatsu.methods.ingest
  "ingest.cljc — 入札 (nyusatsu) offline OCDS ingest membrane. ADR-2606271700.

  Reads a LOCAL OCDS release-package JSON file and normalizes each release into validated
  `:bid/*` datoms via `normalize/release->bid`, deduped by ocid. At R1 this is an OFFLINE
  normalizer only: it never fetches a remote portal and never writes to the kotoba log.

  `--live` is REFUSED (G8: live ingest = Council Lv6+ + operator + member signature). The
  OCDS fast-path needs no LLM — structured releases map straight to datoms; the Murakumo
  HTML/PDF extract path (non-OCDS jurisdictions, e.g. JP GEPS) is a separate cell (R3).

  SELF-CONTAINED minimal JSON reader (no cheshire/data.json); file I/O behind #?(:clj …).
  House style mirrors kosatsu.methods.ingest."
  (:require [nyusatsu.methods.normalize :as norm]))

;; ── minimal JSON reader (subset; string-keyed maps, json.loads shapes) ───────
(declare json-value)

(defn- skip-ws [^String s i]
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

;; ── OCDS release-package → deduped, validated bids ───────────────────────────
(defn package->bids
  "Normalize an OCDS release-package map under `ctx` (see normalize/release->bid) into
  deduped, validated `:bid/*` datoms. Accepts either {\"releases\" [...]} or a bare release
  vector."
  [package ctx]
  (let [releases (if (sequential? package) package (get package "releases" []))]
    (->> releases
         (map #(norm/release->bid % ctx))
         norm/dedupe-bids)))

#?(:clj
   (defn ingest-file
     "Read a local OCDS release-package JSON at `path` and return deduped validated bids.
     `ctx` carries jurisdiction/issuer-did/source-url/source-lang/sourcing. Offline only."
     [path ctx]
     (package->bids (parse-json (slurp (str path))) ctx)))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)]
       (cond
         (some #{"--live"} argv)
         (do (binding [*out* *err*]
               (println (str "REFUSED: live procurement ingest is G8-gated (Council Lv6+ + operator + "
                             "member signature). nyusatsu R1 is an OFFLINE OCDS normalizer only — "
                             "pass a local OCDS release-package JSON path.")))
             2)
         (< (count argv) 1)
         (do (binding [*out* *err*]
               (println "usage: ingest <ocds-release-package.json> [jurisdiction] [issuer-did]   (offline; --live refused)"))
             1)
         :else
         (let [[path juris issuer source-url] argv
               ctx {:jurisdiction (or juris "??")
                    :issuer-did   (or issuer "did:web:gov.etzhayyim.com:country:zzz:unknown")
                    ;; provenance (G3): the source URL the package was fetched from; falls back to
                    ;; the local file path so an offline normalize still carries ≥1 citation.
                    :source-url   (or source-url (str "file://" path))
                    :sourcing     ":representative"}
               bids (ingest-file path ctx)]
           (println (str "# normalized " (count bids) " procurement bids (offline, NOT written to the log)"))
           (doseq [b bids]
             (println (get b ":bid/ocid") (get b ":bid/jurisdiction")
                      (get b ":bid/method") (get b ":bid/status")
                      (str (get b ":bid/value-amount") (get b ":bid/value-currency"))
                      "—" (get b ":bid/title")))
           0)))))

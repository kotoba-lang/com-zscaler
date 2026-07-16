(ns kabuto.methods.social
  "social.py — kabuto 兜 atproto-compatible social post composer + publisher. ADR-2606022000.
  1:1 Clojure port of `methods/social.py`.

  Composes app.bsky.feed.post-shaped records from the company / supply-edge / intel-report graph.
  The live publication path writes into the kotoba Datom log via kotoba-server
  com.etzhayyim.apps.kotoba.atproto.repo.write — that I/O lives behind #?(:clj …).

  OUTWARD-GATED (kabuto G11): live publication requires BOTH KABUTO_LIVE_POST=1 and KOTOBA_ENDPOINT,
  plus operator auth. Default is a DRY-RUN: prints each record + its AT-URI and writes nothing.

  AGGREGATE-FIRST + non-adjudicating (G3/G4): post bodies state public facts and computed
  concentration only. Every body passes a Charter Rider §2(a)-(h) content scan (G-charter) before it
  is eligible to publish.

  House style: pure compose; the Python ':…' keyword strings stay strings; host/network I/O behind
  #?(:clj …). SELF-CONTAINED: requires the actor's existing kabuto-edn sibling; sha-256 (for the
  rkey) via the kotoba sibling's host seam. (The Python `__main__` CLI is preserved as -main.)"
  (:require [clojure.string :as str]
            [kabuto.methods.kabuto-edn :as kedn]
            [kabuto.methods.kotoba :as kotoba]
            #?(:clj [clojure.java.io :as io])))

(def actor-did "did:web:etzhayyim.com:actor:kabuto")

;; Minimal local Charter Rider §2(a)-(h) deny-scan. Conservative substring match (lower-cased).
(def ^:private charter-deny
  ["weapon design" "covert force" "how to attack" "where to cut"
   "child sexual" "non-consensual" "gore for" "ad network" "adsense"])

(defn charter-rider-clean
  "True iff `text` carries none of the Charter Rider deny phrases (case-insensitive)."
  [text]
  (let [t (str/lower-case text)]
    (not (some #(str/includes? t %) charter-deny))))

(defn rkey
  "Deterministic record key: 'kabuto-' + sha256(subject-id)[:13]."
  [subject-id]
  (str "kabuto-" (subs (kotoba/*sha256-hex* (str subject-id)) 0 13)))

(defn- now-iso
  "Explicit UTC ISO-8601 with Z (atproto datetime). Wall-clock — behind the #?(:clj) edge."
  []
  #?(:clj (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss'.000Z'")
                   (java.time.ZonedDateTime/now java.time.ZoneOffset/UTC))
     :default "1970-01-01T00:00:00.000Z"))

(defn post-record
  "app.bsky.feed.post record (text capped to 300 chars; langs default [\"en\"])."
  [text langs]
  {"$type" "app.bsky.feed.post"
   "text" (subs text 0 (min 300 (count text)))
   "langs" (if (seq langs) langs ["en"])
   "createdAt" (now-iso)})

(defn- ->crit
  "float(e.get(':supply.edge/criticality', 0) or 0) — coerce, 0.0 on nil/false."
  [e]
  (let [v (get e ":supply.edge/criticality")]
    (if (or (nil? v) (false? v)) 0.0 (double v))))

(defn- num-str
  "str() of a number value (mirrors Python f-string interpolation of the raw criticality)."
  [v]
  (cond
    (nil? v) "None"
    (integer? v) (str v)
    (number? v) (str (double v))
    :else (str v)))

(defn compose
  "Yield (subject-id kind text) tuples — aggregate-first, public-facts-only. Mirrors compose()."
  [companies edges report-summary]
  (let [head (when (and report-summary (not= report-summary ""))
               [["kabuto.report.supply-concentration" "intel-report"
                 (str "kabuto 兜 supply-chain concentration map (aggregate-first, public record): "
                      report-summary
                      " — a redundancy/accountability map, not a target-list. #supplychain")]])
        top (take 8 (sort-by #(- (->crit %)) edges))
        edge-posts
        (map (fn [e]
               (let [s (get e ":supply.edge/from")
                     c (get e ":supply.edge/to")
                     sn (get-in companies [s ":company/name"] s)
                     cn (get-in companies [c ":company/name"] c)
                     commodity (-> (str (get e ":supply.edge/commodity" ":unknown"))
                                   (str/replace #"^:+" ""))
                     crit (get e ":supply.edge/criticality")]
                 [(get e ":supply.edge/id") "supply-edge"
                  (str "Disclosed supply dependency (public record): " cn " relies on " sn
                       " for " commodity " (est. concentration " (num-str crit) "). "
                       "Diversify to build resilience. #supplychain #" commodity)]))
             top)]
    (concat (or head []) edge-posts)))

;; ── live publish + CLI (host/network I/O at the #?(:clj) edge) ────────────────

#?(:clj
   (defn -main
     "CLI entry — compose posts; DRY-RUN by default (G11). Live publish (KABUTO_LIVE_POST=1 +
     KOTOBA_ENDPOINT + operator auth) writes via kotoba-server atproto.repo.write. The Python demo's
     network leg is intentionally minimal here; default dry-run prints each record + AT-URI."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-public-companies.kotoba.edn"))
           rows (kedn/load-edn seed)
           {:keys [companies edges]} (kedn/classify rows)
           limit (let [i (.indexOf argv "--limit")] (if (>= i 0) (Long/parseLong (nth argv (inc i))) 9))]
       (println (str "kabuto.social: mode=DRY-RUN actor=" actor-did))
       (loop [posts (compose companies edges "") n 0]
         (if (or (empty? posts) (>= n limit))
           (println (str "kabuto.social: " n " post(s) composed (dry-run); charter-scan applied "
                         "to every body."))
           (let [[subject-id _kind text] (first posts)
                 clean (charter-rider-clean text)
                 uri (str "at://" actor-did "/app.bsky.feed.post/" (rkey subject-id))]
             (if-not clean
               (do (println (str "  [SKIP charter §2] " subject-id))
                   (recur (rest posts) n))
               (do (println (str "  [dry-run] " uri))
                   (recur (rest posts) (inc n))))))))))

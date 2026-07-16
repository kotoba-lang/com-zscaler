#!/usr/bin/env bb
;; umisachi 海幸 — atproto-compatible restoration-advisory composer + Charter gate (dry-run).
(ns umisachi.methods.social
  "social.clj — umisachi marine-bounty social post composer + Charter-Rider gate (ADR-2606074200).

  Composes app.bsky.feed.post-shaped RESTORATION advisories from analyze's depletion/nourishment
  surfaces. AGGREGATE-FIRST + non-adjudicating: bodies state public facts + computed depletion
  load only, and EVERY body is framed as routed-to-RESTORATION (G1 — a restoration map, NEVER a
  catch-target list: no coordinates, never 'where to fish'). Each body passes a Charter Rider
  §2(a)-(h) deny-scan + a G1 catch-target deny-scan before it is eligible to publish.

  OUTWARD-GATED: live publication (UMISACHI_LIVE_POST=1 + operator auth) is an operator-only leg —
  the clj surface carries the pure composer + gate + dry-run; the live HTTP write stays out
  (no-server-key / no external I/O here).

  Run:  bb --classpath 20-actors 20-actors/umisachi/methods/social.clj [seed.edn] [--limit N]"
  (:require [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private this-file *file*)
(defn- actor-root [] (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile))
(def actor-did "did:web:etzhayyim.com:actor:umisachi")

;; Charter Rider §2(a)-(h) deny + a G1 catch-target deny (no coordinates / 'where to fish' /
;; 'catch here' — a restoration map must never read as an exploitation target-list).
(def ^:private charter-deny
  ["weapon design" "covert force" "how to attack" "where to cut"
   "child sexual" "non-consensual" "gore for" "ad network" "adsense"])
(def ^:private g1-deny
  ["catch here" "where to fish" "fishing ground at" "gps" "lat/lon" "coordinates" "best spot to"])

(defn charter-rider-clean
  "True iff `text` trips neither the Charter Rider §2 deny patterns nor the G1 catch-target
  patterns (lower-cased substring)."
  [text]
  (let [t (str/lower-case text)]
    (and (not-any? #(str/includes? t %) charter-deny)
         (not-any? #(str/includes? t %) g1-deny))))

(defn rkey [subject-id]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        hx (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest md (.getBytes (str subject-id) "UTF-8"))))]
    (str "umisachi-" (subs hx 0 13))))

(defn- now []
  (str (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss")
                (java.time.LocalDateTime/now (java.time.ZoneOffset/UTC)))
       ".000Z"))

(defn post-record [text langs]
  {"$type" "app.bsky.feed.post"
   "text" (subs text 0 (min 300 (count text)))
   "langs" (or (seq langs) ["en"])
   "createdAt" (now)})

(defn- r2 [x] (/ (Math/round (* (double x) 100.0)) 100.0))

(defn compose
  "Return a seq of [subject-id kind text] — aggregate-first, restoration-framed, public-facts-only.
  Deterministic: depletion bearers sorted by load desc, then id."
  [nodes res limit]
  (let [dep (sort-by (juxt (comp - val) (comp str key)) (:depletion res))]
    (concat
     [["umisachi.report.restoration-map" "intel-report"
       (str "umisachi 海幸 marine-bounty restoration map (aggregate-first, public record): "
            (count dep) " stocks/habitats/markets bear disclosed pressure routed to RESTORATION; "
            "provisioning across " (count (:nourishment res)) " bearers. "
            "A restoration map, never a catch-target list. #restoration #seafood")]]
     (for [[nid load] (take (max 0 (dec limit)) dep)]
       (let [label (get-in nodes [nid :organism/label] (str nid))]
         [nid "restoration-advisory"
          (str "Disclosed pressure on " label " (est. depletion load " (r2 load)
               ") — routed to RESTORATION, not exploitation. Public record; aggregate-first. "
               "#restoration")])))))

(defn main [& argv]
  (let [args (vec argv)
        li (.indexOf args "--limit")
        limit-val (when (>= li 0) (nth args (inc li)))
        limit (if (>= li 0) (Integer/parseInt limit-val) 9)
        seed (or (first (remove #(or (str/starts-with? % "--") (= % limit-val)) args))
                 (str (io/file (actor-root) "data" "seed-umisachi-graph.kotoba.edn")))
        [nodes edges] (a/load-file seed)
        res (a/analyze nodes edges)
        posts (compose nodes res limit)]
    (println (str "umisachi.social: mode=DRY-RUN actor=" actor-did " (live posting is the operator leg)"))
    (let [n (reduce (fn [n [sid _kind text]]
                      (if (>= n limit) n
                        (if-not (charter-rider-clean text)
                          (do (println (str "  [SKIP charter/G1] " sid)) n)
                          (let [uri (str "at://" actor-did "/app.bsky.feed.post/" (rkey sid))]
                            (println (str "  [dry-run] " uri))
                            (inc n)))))
                    0 posts)]
      (println (format "umisachi.social: %d post(s) composed (dry-run); charter+G1 scan applied to every body." n)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply main *command-line-args*))

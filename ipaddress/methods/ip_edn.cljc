(ns ipaddress.methods.ip-edn
  "ipaddress — minimal EDN-subset reader + datom classifier + EDN serializer
  (1:1 Clojure port of methods/ip_edn.py, ADR-2605301400 §T2). Keeps the ipaddress
  cells dependency-free, mirroring the kabuto/kasa/yabai *_edn family.

  Reader: top-level vector of maps; values are strings, longs, doubles, keywords
  (kept as \":ns/name\" strings), nil/true/false, nested vectors; maps preserve
  insertion order. Classifier buckets rirs/asns/ranges/ips (keyed by id) +
  announces/members/geos/rdns/whois (lists)."
  (:require [clojure.string :as str]))

;; ── reader (same proven char-cursor reader as the *_edn family) ─────────────
(def ^:private eof ::eof)
(defn- ws? [c] (or (= c \space) (= c \tab) (= c \return) (= c \newline) (= c \,)))
(defn- delim? [c] (or (ws? c) (= c \[) (= c \]) (= c \{) (= c \}) (= c \")))

(defn- ordered-assoc [m k v]
  (if (and (instance? clojure.lang.PersistentArrayMap m) (< (count m) 8) (not (contains? m k)))
    (assoc m k v)
    (let [had? (contains? m k)
          base (if (::order (meta m)) m (with-meta (into (array-map) m) {::order (vec (keys m))}))
          m' (assoc base k v)]
      (if had? (with-meta m' (meta base))
          (with-meta m' (update (meta base) ::order (fnil conj []) k))))))

(defn- skip-ws [^String s n i]
  (loop [i i]
    (if (>= i n) i
        (let [c (.charAt s i)]
          (cond
            (= c \;) (recur (loop [j i] (if (and (< j n) (not= (.charAt s j) \newline)) (recur (inc j)) j)))
            (ws? c) (recur (inc i))
            :else i)))))

(declare read-form)
(defn- read-vec [^String s n i]
  (loop [i i, out []]
    (let [i (skip-ws s n i)]
      (cond
        (>= i n) (throw (ex-info "unterminated vector" {}))
        (= (.charAt s i) \]) [out (inc i)]
        :else (let [[v i] (read-form s n i)] (recur i (conj out v)))))))
(defn- read-map [^String s n i]
  (loop [i i, out (array-map)]
    (let [i (skip-ws s n i)]
      (cond
        (>= i n) (throw (ex-info "unterminated map" {}))
        (= (.charAt s i) \}) [out (inc i)]
        :else (let [[k i] (read-form s n i) [v i] (read-form s n i)]
                (recur i (ordered-assoc out k v)))))))
(defn- read-str [^String s n i]
  (let [sb (StringBuilder.)]
    (loop [i i]
      (if (>= i n) (throw (ex-info "unterminated string" {}))
          (let [c (.charAt s i)]
            (cond
              (= c \\) (let [nxt (if (< (inc i) n) (.charAt s (inc i)) \space)
                             rep (case nxt \n \newline \t \tab \r \return nxt)]
                         (.append sb rep) (recur (+ i 2)))
              (= c \") [(.toString sb) (inc i)]
              :else (do (.append sb c) (recur (inc i)))))))))
(defn- read-kw [^String s n i]
  (let [j i] (loop [i (inc i)] (if (and (< i n) (not (delim? (.charAt s i)))) (recur (inc i)) [(subs s j i) i]))))
(defn- numeric-token? [^String tok]
  (and (some #(or (= % \.) (= % \e) (= % \E)) tok)
       (let [stripped (-> tok (str/replace #"^[-+]+" "") (str/replace "." "")
                          (str/replace "e" "") (str/replace "E" "") (str/replace "-" "") (str/replace "+" ""))]
         (and (seq stripped) (every? #(Character/isDigit ^char %) stripped)))))
(defn- read-atom [^String s n i]
  (let [j i]
    (loop [i i]
      (if (and (< i n) (not (delim? (.charAt s i)))) (recur (inc i))
          (let [tok (subs s j i)]
            [(cond
               (= tok "nil") nil (= tok "true") true (= tok "false") false
               (numeric-token? tok) (try (Double/parseDouble tok) (catch #?(:clj Exception :cljs :default) _ tok))
               :else (let [as-long (try (Long/parseLong tok) (catch #?(:clj Exception :cljs :default) _ ::nan))]
                       (if (not= as-long ::nan) as-long tok)))
             i])))))
(defn- read-form [^String s n i]
  (let [i (skip-ws s n i)]
    (if (>= i n) [eof i]
        (let [c (.charAt s i)]
          (cond
            (= c \[) (read-vec s n (inc i)) (= c \{) (read-map s n (inc i))
            (= c \") (read-str s n (inc i)) (= c \:) (read-kw s n i)
            :else (read-atom s n i))))))

(defn read-all [text]
  (let [s (str text), n (count s)]
    (loop [i 0, forms []]
      (let [[v i] (read-form s n i)]
        (if (= v eof) (or (first (filter vector? forms)) (first forms) [])
            (recur i (conj forms v)))))))

#?(:clj (defn read-file [path] (read-all (slurp (str path)))))
(def load-edn read-file)   ; python name alias

(defn ordered-items
  "Items of an analyze ordered-map in first-touch (Python defaultdict) order. Mirrors
  analyze/ordered-items so `ip-edn/ordered-items` — referenced by analyze / kotoba / transact
  — resolves to identical behaviour (the clj-port placed the fn in analyze but several callers
  reference it via the ip-edn alias). Keyed on the EXACT meta the analyze loader attaches
  (:ipaddress.methods.analyze/order); no require on analyze, so no load cycle."
  [m]
  (if-let [order (:ipaddress.methods.analyze/order (meta m))]
    (map (fn [k] [k (get m k)]) order)
    (seq m)))

;; ── classifier (port of ip_edn.classify) ────────────────────────────────────
(def ^:private buckets
  [[":rir/id" "rirs"] [":asn/id" "asns"] [":iprange/id" "ranges"]
   [":ip/id" "ips"] [":net.announce/id" "announces"] [":net.member/id" "members"]
   [":geo/id" "geos"] [":rdns/id" "rdns"] [":whois/id" "whois"]])
(def ^:private keyed #{"rirs" "asns" "ranges" "ips"})

(defn classify [rows]
  (let [init (reduce (fn [m [_ name]] (assoc m name (if (keyed name) {} []))) {} buckets)]
    (reduce
     (fn [out r]
       (if-not (map? r)
         out
         (if-let [[k name] (some (fn [[k name]] (when (contains? r k) [k name])) buckets)]
           (if (keyed name)
             (update out name assoc (get r k) r)
             (update out name conj r))
           out)))
     init rows)))

;; ── EDN serializer (port of edn_str / edn_val / to_edn) ─────────────────────
(defn edn-str [s]
  (str \" (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \"))

(defn edn-val [x]
  (cond
    (boolean? x) (if x "true" "false")
    (number? x) (str x)
    (sequential? x) (str "[" (str/join " " (map edn-val x)) "]")
    (string? x) (if (str/starts-with? x ":") x (edn-str x))
    :else (edn-str (str x))))

(defn to-edn [recs header-lines]
  (let [body (map (fn [r]
                    (str " {" (str/join " " (map (fn [[k v]] (str k " " (edn-val v))) r)) "}"))
                  recs)
        lines (concat header-lines ["["] body ["]"])]
    (str (str/join "\n" lines) "\n")))

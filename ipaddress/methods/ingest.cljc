(ns ipaddress.methods.ingest
  "ingest.py — ipaddress ACTIVE number-resource collector → kotoba EAVT.
  1:1 Clojure port of `methods/ingest.py` (ADR-2605301400 §T2).

  1次ソース collector. Actively pulls PUBLIC Internet number-resource registries and
  normalizes them into the ip-network kotoba vocabulary, then dedup-merges with the curated
  seed (seed wins on id). Live network pull (--live) is GATE-G7 (IPADDRESS_OPERATOR_GATE).

  House style: the PARSERS are pure and portable (parse-delegated-stats, slug, key*); all
  host/network/file I/O (fetch, collect-rdns, the seed→merged main pipeline) is behind
  #?(:clj …). The Python __main__ CLI / argv dispatch is NOT ported (no tests cover it) —
  noted here in the docstring. There is no clojure.test suite for this module."
  (:require [clojure.string :as str]
            [ipaddress.methods.ip-edn :as ip-edn]))

(def rir-id
  {"apnic" "rir.apnic" "ripe" "rir.ripe" "arin" "rir.arin"
   "lacnic" "rir.lacnic" "afrinic" "rir.afrinic"})

;; Public delegated-stats endpoints (collection source-of-record; G7 live-gated).
(def rir-stats
  {"apnic"   "https://ftp.apnic.net/stats/apnic/delegated-apnic-latest"
   "ripe"    "https://ftp.ripe.net/pub/stats/ripencc/delegated-ripencc-latest"
   "arin"    "https://ftp.arin.net/pub/stats/arin/delegated-arin-extended-latest"
   "lacnic"  "https://ftp.lacnic.net/pub/stats/lacnic/delegated-lacnic-latest"
   "afrinic" "https://ftp.afrinic.net/pub/stats/afrinic/delegated-afrinic-latest"})

(defn slug
  "Port of _slug: replace ./: / with -, strip leading/trailing -."
  [s]
  (-> (str s)
      (str/replace "." "-")
      (str/replace ":" "-")
      (str/replace "/" "-")
      (str/replace #"^-+" "")
      (str/replace #"-+$" "")))

;; ── minimal IPv4 address arithmetic + summarize_address_range port ───────────────
(defn- ipv4->int [s]
  (let [octs (map #(Long/parseLong %) (str/split s #"\."))]
    (when (or (not= 4 (count octs)) (some #(or (neg? %) (> % 255)) octs))
      (throw (ex-info (str "bad IPv4 address: " s) {:addr s})))
    (reduce (fn [acc o] (+ (* acc 256) o)) 0 octs)))

(defn- int->ipv4 [n]
  (str/join "." [(bit-and (bit-shift-right n 24) 0xff)
                 (bit-and (bit-shift-right n 16) 0xff)
                 (bit-and (bit-shift-right n 8) 0xff)
                 (bit-and n 0xff)]))

(defn- count-rhs-zero-bits
  "ipaddress._count_righthand_zero_bits(number, bits)."
  [number bits]
  (if (zero? number)
    bits
    (min bits (loop [n number, c 0] (if (bit-test n 0) c (recur (bit-shift-right n 1) (inc c)))))))

(defn summarize-address-range-v4
  "Port of ipaddress.summarize_address_range(first, last) for IPv4 → seq of {:cidr :num}.
  Yields the minimal set of CIDR nets that exactly cover [first..last]."
  [first-int last-int]
  (loop [first* first-int, out []]
    (if (> first* last-int)
      out
      (let [nbits (min (count-rhs-zero-bits first* 32)
                       (let [d (inc (- last-int first*))]
                         ;; (last - first + 1).bit_length() - 1
                         (dec (.bitLength (biginteger d)))))
            prefix (- 32 nbits)
            num (long (Math/pow 2 nbits))
            cidr (str (int->ipv4 first*) "/" prefix)
            out (conj out {:cidr cidr :num num})
            nf (+ first* num)]
        (if (or (> nf (long (- (Math/pow 2 32) 1))) (> nf last-int))
          out
          (recur nf out))))))

(defn parse-delegated-stats
  "RIR delegated-stats → [asns ranges]. Port of parse_delegated_stats.
  Format per line: registry|cc|type|start|value|date|status[|ext...]"
  ([text registry sourcing] (parse-delegated-stats text registry sourcing 20000))
  ([text registry sourcing limit]
   (let [rir (get rir-id registry (str "rir." registry))]
     (loop [lines (str/split-lines text)
            asns [] ranges [] emitted 0]
       (if (or (empty? lines) (>= emitted limit))
         [asns ranges]
         (let [line (str/trim (first lines))
               rest-lines (rest lines)]
           (if (or (= "" line) (str/starts-with? line "#"))
             (recur rest-lines asns ranges emitted)
             (let [f (str/split line #"\|" -1)]
               (if (< (count f) 7)
                 (recur rest-lines asns ranges emitted)
                 (let [cc0 (nth f 1) typ (nth f 2) start (nth f 3)
                       value (nth f 4) date (nth f 5) status (nth f 6)]
                   (if (or (= status "summary") (not (contains? #{"asn" "ipv4" "ipv6"} typ)))
                     (recur rest-lines asns ranges emitted)
                     (let [cc (if (or (= "" cc0) (= "*" cc0)) "ZZ" cc0)
                           st (get {"allocated" ":allocated" "assigned" ":assigned"
                                    "reserved" ":reserved" "available" ":available"}
                                   status ":allocated")]
                       (cond
                         (>= emitted limit) [asns ranges]

                         (= typ "asn")
                         (let [res (try
                                     (let [n (Long/parseLong start)]
                                       [(conj asns {":asn/id" (str "asn." n) ":asn/number" n ":asn/country" cc
                                                    ":asn/rir" rir ":asn/hosting-class" ":unknown"
                                                    ":asn/sourcing" (str ":" sourcing)})
                                        ranges (inc emitted)])
                                     (catch #?(:clj Exception :cljs :default) _ nil))]
                           (if res
                             (recur rest-lines (nth res 0) (nth res 1) (nth res 2))
                             (recur rest-lines asns ranges emitted)))

                         (= typ "ipv4")
                         (let [res (try
                                     (let [first-int (ipv4->int start)
                                           last-int (- (+ first-int (Long/parseLong value)) 1)
                                           nets (summarize-address-range-v4 first-int last-int)]
                                       (loop [nets nets, ranges ranges, emitted emitted]
                                         (if (or (empty? nets) (>= emitted limit))
                                           [ranges emitted]
                                           (let [{:keys [cidr num]} (first nets)]
                                             (recur (rest nets)
                                                    (conj ranges {":iprange/id" (str "range.v4." (slug cidr))
                                                                  ":iprange/cidr" cidr ":iprange/version" 4
                                                                  ":iprange/country" cc ":iprange/rir" rir
                                                                  ":iprange/status" st ":iprange/alloc-date" date
                                                                  ":iprange/host-count" num
                                                                  ":iprange/sourcing" (str ":" sourcing)})
                                                    (inc emitted))))))
                                     (catch #?(:clj Exception :cljs :default) _ nil))]
                           (if res
                             (recur rest-lines asns (nth res 0) (nth res 1))
                             (recur rest-lines asns ranges emitted)))

                         (= typ "ipv6")
                         (let [cidr (str start "/" value)]
                           (recur rest-lines asns
                                  (conj ranges {":iprange/id" (str "range.v6." (slug cidr))
                                                ":iprange/cidr" cidr ":iprange/version" 6
                                                ":iprange/country" cc ":iprange/rir" rir
                                                ":iprange/status" st ":iprange/alloc-date" date
                                                ":iprange/host-count" 0 ":iprange/sourcing" (str ":" sourcing)})
                                  (inc emitted)))

                         :else (recur rest-lines asns ranges emitted))))))))))))))

(def id-keys
  [":rir/id" ":asn/id" ":iprange/id" ":ip/id" ":net.announce/id"
   ":net.member/id" ":geo/id" ":rdns/id" ":whois/id"])

(defn key*
  "Port of _key(rec): first present id-key value, else nil."
  [rec]
  (some (fn [k] (when (contains? rec k) (get rec k))) id-keys))

(defn dedup-merge
  "Port of the seed-wins-on-id dedup-merge: iterate (seed ++ bridged), keep first id seen."
  [seed-rows bridged]
  (loop [recs (concat seed-rows bridged), merged [], seen #{}]
    (if (empty? recs)
      merged
      (let [rec (first recs)]
        (if-not (map? rec)
          (recur (rest recs) merged seen)
          (let [k (key* rec)]
            (if (or (nil? k) (contains? seen k))
              (recur (rest recs) merged seen)
              (recur (rest recs) (conj merged rec) (conj seen k)))))))))

#?(:clj
   (defn fetch
     "Live HTTP GET of a public registry endpoint (G7). Behind #?(:clj …)."
     [url]
     (slurp url)))

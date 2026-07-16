(ns ipaddress.methods.analyze
  "analyze.py — ipaddress number-resource concentration analyzer (ADR-2605301400 §T2).
  1:1 Clojure port of `methods/analyze.py`.

  Reads a kotoba-EDN IP/ASN graph and emits, AGGREGATE-FIRST, RIR delegation coverage,
  ASN origin-prefix load, hosting-class address-space load, per-country address space, and
  an address-space Herfindahl index → an intel report + derived :ipnet/* datoms.

  CONSTITUTIONAL (ipaddress G2/G10): a number-resource RESILIENCE + accountability map,
  NEVER a target-list. No host is probed; no adherent is de-anonymised.

  House style: data maps stay string-keyed; ':…' keyword strings stay strings; pure fns;
  host/file I/O ONLY behind #?(:clj …). The Python __main__ CLI demo (main/argv) is omitted
  on purpose; the file-writing -main edge is provided behind #?(:clj …).

  defaultdict iteration order: Python defaultdicts iterate in first-touch order. We track
  first-touch order via the accumulators below so sorts tie exactly the Python order
  (sorted(..., key=-v) is a stable sort over that order)."
  (:require [clojure.string :as str]
            [ipaddress.methods.ip-edn :as ip-edn]))

;; ── ordered (first-touch-tracking) accumulator (mirror Python defaultdict order) ──
(defn- omap [] ^{::order []} {})

(defn- omap-update
  [m k f init]
  (let [had? (contains? m k)
        m' (update m k (fnil f init))]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn- omap-add [m k v] (omap-update m k #(+ % v) 0))

(defn ordered-items
  "Public: items of an analyze ordered-map in first-touch order (Python defaultdict order).
  Used by the sibling kotoba module's derived-datoms so its sort ties exactly the same order."
  [m]
  (if-let [order (::order (meta m))]
    (map (fn [k] [k (get m k)]) order)
    (seq m)))

(def ^:private omap-items ordered-items)

(defn- to-int
  "int(x or 0) — nil/false → 0, else parse/coerce. Mirrors `int(r.get(k, 0) or 0)`."
  [x]
  (cond
    (nil? x) 0
    (false? x) 0
    (integer? x) (long x)
    (number? x) (long x)
    (= x "") 0
    :else (try (Long/parseLong (str x)) (catch #?(:clj Exception :cljs :default) _ 0))))

;; round(x, 4) — Python round is banker's rounding (HALF_EVEN) over the exact double.
(defn- round4 [x]
  (-> (java.math.BigDecimal. (double x))
      (.setScale 4 java.math.RoundingMode/HALF_EVEN)
      (.doubleValue)))

(defn analyze
  "Port of analyze(b). b = classify(...) result. Returns a string-keyed result map."
  [b]
  (let [ranges    (get b "ranges")
        asns      (get b "asns")
        announces (get b "announces")
        ips       (get b "ips")
        geos      (get b "geos")
        rdns      (get b "rdns")
        whois     (get b "whois")
        members   (get b "members")

        ;; range → origin ASN (from announce edges)
        range-asn (reduce (fn [m e]
                            (assoc m (get e ":net.announce/range") (get e ":net.announce/asn")))
                          {}
                          announces)

        ;; iterate ranges in insertion order (array-map / ordered classify bucket)
        {:keys [rir-addr rir-ranges country-addr hosting-addr asn-addr v4 v6]}
        (reduce
         (fn [acc [rid r]]
           (let [hc  (to-int (get r ":iprange/host-count" 0))
                 rir (get r ":iprange/rir")
                 cc  (get r ":iprange/country" "ZZ")
                 acc (if rir
                       (-> acc
                           (update :rir-addr omap-add rir hc)
                           (update :rir-ranges omap-add rir 1))
                       acc)
                 acc (update acc :country-addr omap-add cc hc)
                 acc (if (= (get r ":iprange/version") 6)
                       (update acc :v6 inc)
                       (update acc :v4 inc))
                 asn (get range-asn rid)
                 acc (if asn
                       (let [hc-cls (get-in asns [asn ":asn/hosting-class"] ":unknown")]
                         (-> acc
                             (update :asn-addr omap-add asn hc)
                             (update :hosting-addr omap-add hc-cls hc)))
                       acc)]
             acc))
         {:rir-addr (omap) :rir-ranges (omap) :country-addr (omap)
          :hosting-addr (omap) :asn-addr (omap) :v4 0 :v6 0}
         (ip-edn/ordered-items ranges))

        ;; ASN origin-prefix load — sorted by -prefix-count (Python stable sort over dict order)
        asn-prefix (->> (ip-edn/ordered-items asns)
                        (map (fn [[aid a]]
                               [aid
                                (get a ":asn/name" aid)
                                (to-int (get a ":asn/prefix-count" 0))
                                (get a ":asn/hosting-class" ":unknown")
                                (get a ":asn/country" "ZZ")]))
                        ;; key=lambda r: -r[2]  → stable sort ascending on -prefix
                        (sort-by (fn [r] (- (nth r 2))))
                        vec)

        ;; address-space HHI across hosting classes
        tot-host (let [s (reduce + 0 (map second (omap-items hosting-addr)))] (if (zero? s) 1 s))
        space-hhi (round4 (reduce + 0.0 (map (fn [[_ v]] (let [s (/ (double v) tot-host)] (* s s)))
                                             (omap-items hosting-addr))))

        ;; prefix-load HHI across ASNs
        tot-pref (let [s (reduce + 0 (map #(nth % 2) asn-prefix))] (if (zero? s) 1 s))
        prefix-hhi (round4 (reduce + 0.0 (map (fn [r] (let [s (/ (double (nth r 2)) tot-pref)] (* s s)))
                                              asn-prefix)))]
    {"rir_addr" rir-addr "rir_ranges" rir-ranges "country_addr" country-addr
     "hosting_addr" hosting-addr "asn_addr" asn-addr "asn_prefix" asn-prefix
     "v4" v4 "v6" v6 "space_hhi" space-hhi "prefix_hhi" prefix-hhi
     "n_ips" (count ips) "n_geo" (count geos) "n_rdns" (count rdns) "n_whois" (count whois)
     "n_announce" (count announces) "n_member" (count members)}))

;; ── sorted helpers (sorted(d.items(), key=lambda kv: -kv[1]) — stable over dict order) ──
(defn- sorted-desc
  "Stable sort of ordered-map items by descending value (ties keep first-touch order)."
  [m]
  (sort-by (fn [[_ v]] (- v)) (omap-items m)))

(defn- lstrip-colon [s] (str/replace (str s) #"^:+" ""))

(defn- name* [rirs rid] (get-in rirs [rid ":rir/name"] rid))

;; thousands-separator format (Python f"{n:,}")
(defn- comma [n]
  (let [neg (neg? n)
        digits (str (Math/abs (long n)))
        grouped (->> (reverse digits)
                     (partition-all 3)
                     (map #(apply str (reverse %)))
                     reverse
                     (str/join ","))]
    (str (when neg "-") grouped)))

;; number → str matching Python str() for the HHI doubles (e.g. 0.0 → "0.0", 0.4567 → "0.4567")
(defn- pynum [x]
  (cond
    (integer? x) (str x)
    (and (number? x) (== x (Math/floor (double x))) (not (Double/isInfinite (double x))))
    (str (long x) ".0")
    :else (str x)))

(defn render-report
  "Port of render_report(b, a) — byte-identical markdown."
  [b a]
  (let [rirs (get b "rirs")
        asns (get b "asns")
        L (transient [])
        P (fn [s] (conj! L s))]
    (P "# ipaddress — world IP/ASN number-resource concentration report")
    (P "")
    (P (str "> ADR-2605301400 §T2 · **kotoba-native** (Datom log; NO RisingWave) · **aggregate-first** · "
            "number-resource RESILIENCE + accountability map (NOT a target-list). No host is probed; "
            "no adherent is de-anonymised. Sourcing `:representative` unless an operator-gated live RIR "
            "pull tagged it `:authoritative`."))
    (P "")
    (P (str "- RIRs: **" (count rirs) "**  ·  ASNs: **" (count asns) "**  ·  ranges: **"
            (+ (get a "v4") (get a "v6")) "** "
            "(v4 " (get a "v4") " / v6 " (get a "v6") ")  ·  observed IPs: **" (get a "n_ips") "**"))
    (P (str "- enrichment: geo **" (get a "n_geo") "** · rDNS **" (get a "n_rdns") "** · whois **"
            (get a "n_whois") "** · "
            "announce edges **" (get a "n_announce") "** · membership edges **" (get a "n_member") "**"))
    (P "")

    (P "## RIR delegation coverage")
    (P "")
    (P "Address space + range count delegated per Regional Internet Registry in the graph.")
    (P "")
    (P "| RIR | ranges | Σ IPv4-equiv addresses |")
    (P "|---|---:|---:|")
    (doseq [[rir addr] (sorted-desc (get a "rir_addr"))]
      (P (str "| " (name* rirs rir) " | " (get-in a ["rir_ranges" rir] 0) " | " (comma addr) " |")))
    (P "")

    (P "## ASN routing-authority load — announced-prefix concentration")
    (P "")
    (P (str "Declared announced-prefix count per ASN (origin-routing concentration). "
            "Prefix-load **HHI = " (pynum (get a "prefix_hhi")) "** (Σ share²; higher = routing authority piled "
            "into fewer AS operators). Routed to multi-homing / diversity, never to interdiction."))
    (P "")
    (P "| ASN | name | hosting-class | country | announced prefixes |")
    (P "|---|---|---|---|---:|")
    (doseq [[aid name pref cls cc] (take 15 (get a "asn_prefix"))]
      (P (str "| `" (lstrip-colon aid) "` | " name " | `" (lstrip-colon cls) "` | " cc " | " (comma pref) " |")))
    (P "")

    (P "## Hosting-class address-space load")
    (P "")
    (P (str "Σ routed address space by operator hosting-class (cloud/cdn/residential/transit/…). "
            "Address-space **HHI = " (pynum (get a "space_hhi")) "**. Surfaces how much of the observed routed "
            "space sits behind a few cloud/CDN operators — an accountability signal, aggregate-first."))
    (P "")
    (P "| hosting-class | Σ IPv4-equiv addresses |")
    (P "|---|---:|")
    (doseq [[cls addr] (sorted-desc (get a "hosting_addr"))]
      (P (str "| `" (lstrip-colon cls) "` | " (comma addr) " |")))
    (when (empty? (omap-items (get a "hosting_addr")))
      (P "| (no announce edges in graph) | |"))
    (P "")

    (P "## Per-country delegated address space")
    (P "")
    (P (str "Σ delegated address space per registrant country (geographic concentration of "
            "number resources). Routed to equitable allocation visibility, never a target-list."))
    (P "")
    (P "| country | Σ IPv4-equiv addresses |")
    (P "|---|---:|")
    (doseq [[cc addr] (take 15 (sorted-desc (get a "country_addr")))]
      (P (str "| `" cc "` | " (comma addr) " |")))
    (P "")

    (P "---")
    (P (str "*Generated by `ipaddress/methods/analyze.py`. HONEST: R0 bounded `:representative` seed of "
            "public number-resource records; host-counts from delegated-stats / seed; absence = \"not yet "
            "ingested\". Full RIR/RDAP universe ingest is `methods/ingest.py --live` (G7 operator-gated). "
            "kotoba Datom log is the canonical store (ADR-2605262130); the legacy RisingWave graph is retired.*"))
    (str (str/join "\n" (persistent! L)) "\n")))

(defn render-datoms
  "Port of render_datoms(b, a) — DERIVED :ipnet/* concentration datoms EDN."
  [b a]
  (let [rirs (get b "rirs")
        L (transient [])
        P (fn [s] (conj! L s))]
    (P ";; ipaddress — DERIVED number-resource concentration datoms (ADR-2605301400 §T2).")
    (P ";; :derived — recomputed from the graph; NOT re-ingested as :authoritative fact.")
    (P "[")
    (doseq [[rir addr] (sorted-desc (get a "rir_addr"))]
      (P (str " {:ipnet/rir-coverage " (ip-edn/edn-str (name* rirs rir)) " :ipnet/rir " (ip-edn/edn-str rir) " "
              ":ipnet/ranges " (get-in a ["rir_ranges" rir] 0) " :ipnet/addresses " addr " :ipnet/derived true}")))
    (doseq [[aid name pref cls _cc] (get a "asn_prefix")]
      (P (str " {:ipnet/asn-prefix-load " (ip-edn/edn-str aid) " :ipnet/asn-name " (ip-edn/edn-str name) " "
              ":ipnet/prefixes " pref " :ipnet/hosting-class " cls " :ipnet/derived true}")))
    (doseq [[cls addr] (sorted-desc (get a "hosting_addr"))]
      (P (str " {:ipnet/hosting-class-load " cls " :ipnet/addresses " addr " :ipnet/derived true}")))
    (doseq [[cc addr] (sorted-desc (get a "country_addr"))]
      (P (str " {:ipnet/country-load " (ip-edn/edn-str cc) " :ipnet/addresses " addr " :ipnet/derived true}")))
    (P (str " {:ipnet/space-hhi " (pynum (get a "space_hhi")) " :ipnet/prefix-hhi " (pynum (get a "prefix_hhi")) " "
            ":ipnet/v4-ranges " (get a "v4") " :ipnet/v6-ranges " (get a "v6") " :ipnet/derived true}"))
    (P "]")
    (str (str/join "\n" (persistent! L)) "\n")))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/intel-report.md + out/ip-concentration.kotoba.edn."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           default (let [merged (clojure.java.io/file here "data" "ip-network.merged.kotoba.edn")]
                     (if (.exists merged)
                       merged
                       (clojure.java.io/file here "data" "seed-ip-network.kotoba.edn")))
           graph (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                   (clojure.java.io/file (first argv))
                   default)
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           b (ip-edn/classify (ip-edn/load-edn graph))
           a (analyze b)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "intel-report.md") (render-report b a))
       (spit (clojure.java.io/file outdir "ip-concentration.kotoba.edn") (render-datoms b a))
       (println (str "ipaddress: " (count (get b "rirs")) " RIRs · " (count (get b "asns")) " ASNs · "
                     (+ (get a "v4") (get a "v6")) " ranges · prefix-HHI " (pynum (get a "prefix_hhi"))
                     " · space-HHI " (pynum (get a "space_hhi"))))
       0)))

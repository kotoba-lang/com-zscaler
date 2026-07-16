(ns matsurigoto.methods.standard
  "matsurigoto 政 — COFOG-based e-Government Service Standard loader / validator / coverage.
  1:1 Clojure port of `methods/standard.py` (ADR-2606062300, proposed).

  Reads data/cofog-standard.kotoba.edn (the universal, spec-derived service standard built
  on the UN COFOG function backbone) and:

    1. VALIDATES the standard (structural integrity + the three charter invariants):
         G1 no-server-authority  — every service has :server-held-authority false
         G2 spec-derived-only    — every service cites a non-empty official :spec-basis
         G3 authority-separation — every profile names a legitimate :operated-by + mode
       plus: every service's COFOG class exists in the backbone, references a known
       kotoba-wasm module, and is unique; and the COFOG backbone has 10 divisions.

    2. Emits a HONEST COVERAGE report (out/coverage.md): how much of the COFOG function
       space (10 divisions / 69 groups) + the named transactional domains the standard
       covers, separating :standard-draft / :planned / :reference-impl from executable
       (none yet — every module .solve() raises at R0).

  POSTURE: matsurigoto is the EXECUTION sibling of ooyake's observation atlas. It SUPPLIES
  the standard to governments; it never operates as a platform/operator master key (G1).
  But etzhayyim IS a government (Kingdom of God) — authority is BORNE via the Council, never
  disclaimed (G3).

  House style: Python ':…' keyword strings stay strings (all :egov.*/:cofog/*/:bind/* attrs);
  pure fns; file I/O only at the #?(:clj) edge. Closed-vocab gates raise via ex-info."
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [matsurigoto.methods.edn :as edn]))

;; ── named transactional domains the e-gov standard must cover (user request) ──
(def required-domains
  #{":taxation" ":civil-registry" ":corp-registry" ":identity-credential"})

;; ── universal service-level invariants (G1 no-operator-master-key + G2 spec-derived).
;; WHO governs (G3) is a per-deployment PROFILE concern, validated separately — etzhayyim
;; IS a government (Kingdom of God), so authority is BORNE, never disclaimed; it is just
;; never an operator master key.
(def required-invariants
  ;; vector-of-pairs to mirror Python dict iteration order in error messages
  [[":server-held-authority" false]  ; G1 — never a platform/operator master key (ADR-2605231525)
   [":spec-derived" true]])          ; G2 — official public specs only

;; G3 authority-bearing: every deployment names a legitimate governing authority + mode.
(def allowed-operated-by #{":etzhayyim-council" ":adopting-government"})
(def allowed-authority-mode #{":sovereign-governance" ":supplied-to-state"})

;; ── repr helpers — render values the way Python `repr()` / `!r` does for the error/report
;; strings that the byte-parity comparison depends on (set repr, str repr, None→nil-shape). ──

(defn- py-repr
  "Mirror Python repr() for the value shapes that appear in validation error strings:
   strings → 'single-quoted', None → None, True/False → True/False."
  [v]
  (cond
    (nil? v)      "None"
    (true? v)     "True"
    (false? v)    "False"
    (string? v)   (str "'" v "'")
    :else         (str v)))

(defn- py-set-repr
  "Mirror Python's repr() of a set of ':…' strings, e.g. {':a', ':b'}.
   Python set iteration order is hash-based; for the two fixed 2-element sets used in
   error messages we reproduce the observed CPython ordering."
  [s ordered]
  (str "{" (str/join ", " (map #(str "'" % "'") ordered)) "}"))

;; observed CPython iteration order of the two literal sets (matches set repr in errors)
(def ^:private operated-by-order [":etzhayyim-council" ":adopting-government"])
(def ^:private authority-mode-order [":sovereign-governance" ":supplied-to-state"])

;; ── loaders (file I/O at the #?(:clj) edge) ───────────────────────────────

(defn merge-profiles
  "Pure core of load_standard: merge a seq of external per-country profiles into
   :country-profiles, deduped by iso3 (inline list wins on collision)."
  [doc profiles]
  (let [inline (vec (get doc ":country-profiles" []))
        seen0  (set (map #(get % ":country-profile/iso3") inline))]
    (loop [inline inline
           seen seen0
           ps profiles]
      (if (empty? ps)
        (assoc doc ":country-profiles" inline)
        (let [p (first ps)
              iso (get p ":country-profile/iso3")]
          (if (contains? seen iso)
            (recur inline seen (rest ps))
            (recur (conj inline p) (conj seen iso) (rest ps))))))))

#?(:clj
   (defn load-profiles
     "Load every per-country profile from data/profiles/*.edn (one map per file), sorted by name."
     [directory]
     (let [dir (clojure.java.io/file directory)]
       (if (.exists dir)
         (->> (.listFiles dir)
              (filter #(str/ends-with? (.getName ^java.io.File %) ".edn"))
              (sort-by #(.getName ^java.io.File %))
              (map edn/load-edn)
              (filter map?)
              vec)
         []))))

#?(:clj
   (defn load-standard
     "Read + parse the standard EDN, then merge external per-country profiles. File I/O edge."
     ([] (let [here (-> *file* clojure.java.io/file .getParentFile .getParentFile)]
           (load-standard (clojure.java.io/file here "data" "cofog-standard.kotoba.edn")
                          (clojure.java.io/file here "data" "profiles"))))
     ([path profiles-dir]
      (let [doc (edn/load-edn path)]
        (when-not (map? doc)
          (throw (ex-info "standard root must be a map" {})))
        (merge-profiles doc (load-profiles profiles-dir))))))

(defn cofog-index [doc]
  (into {} (map (fn [row] [(get row ":cofog/code") row]) (get doc ":cofog" []))))

(defn module-index [doc]
  (into {} (map (fn [m] [(get m ":egov.module/id") m]) (get doc ":modules" []))))

;; ── validation ────────────────────────────────────────────────────────────

(defn- validate-profile
  "Port of _validate_profile: returns a vector of error strings for one profile."
  [p kind prefix service-ids]
  (let [name (get p (str prefix (if (= kind "polity") "id" "iso3")) "<no-id>")
        ob (get p (str prefix "operated-by"))
        am (get p (str prefix "authority-mode"))
        errs (transient [])]
    (when-not (contains? allowed-operated-by ob)
      (conj! errs (str kind " " name ": :operated-by " (py-repr ob)
                       " not in " (py-set-repr allowed-operated-by operated-by-order))))
    (when-not (contains? allowed-authority-mode am)
      (conj! errs (str kind " " name ": :authority-mode " (py-repr am)
                       " not in " (py-set-repr allowed-authority-mode authority-mode-order))))
    (when (and (= kind "polity")
               (not= [ob am] [":etzhayyim-council" ":sovereign-governance"]))
      (conj! errs (str "polity " name ": must be governed by :etzhayyim-council/:sovereign-governance")))
    (when (and (= kind "country")
               (not= [ob am] [":adopting-government" ":supplied-to-state"]))
      (conj! errs (str "country " name ": must be :adopting-government/:supplied-to-state")))
    (doseq [b (get p (str prefix "bindings") [])]
      (when-not (contains? service-ids (get b ":bind/service"))
        (conj! errs (str kind " " name ": binding to unknown service "
                         (py-repr (get b ":bind/service"))))))
    (persistent! errs)))

(defn validate
  "Return a vector of validation errors (empty = valid). 1:1 with validate()."
  [doc]
  (let [cofog (cofog-index doc)
        modules (module-index doc)
        services (get doc ":services" [])
        errors (transient [])
        seen-ids (volatile! #{})]
    (when (empty? services)
      (conj! errors "no :services in standard"))
    (doseq [s services]
      (let [sid (get s ":egov.service/id" "<no-id>")]
        (when (contains? @seen-ids sid)
          (conj! errors (str sid ": duplicate service id")))
        (vswap! seen-ids conj sid)
        ;; COFOG class must exist in the backbone
        (let [code (get s ":egov.service/cofog")]
          (when-not (contains? cofog code)
            (conj! errors (str sid ": COFOG class " (py-repr code) " not in backbone"))))
        ;; module must be a known kotoba-wasm module
        (let [mod (get s ":egov.service/module")]
          (when-not (contains? modules mod)
            (conj! errors (str sid ": unknown module " (py-repr mod)))))
        ;; G2 spec-derived-only: non-empty official spec basis
        (let [specs (or (get s ":egov.service/spec-basis") [])]
          (when (empty? specs)
            (conj! errors (str sid ": G2 violation — empty :spec-basis (spec-derived-only)"))))
        ;; G1 + G3: the structural invariants, exact values
        (let [inv (or (get s ":egov.service/invariants") {})]
          (doseq [[k want] required-invariants]
            (when (not= (get inv k) want)
              (conj! errors (str sid ": invariant " k " must be " (py-repr want)
                                 ", got " (py-repr (get inv k)))))))))
    ;; COFOG backbone sanity: 10 divisions present
    (let [divisions (filter #(= (get % ":cofog/level") ":division") (get doc ":cofog" []))]
      (when (not= (count divisions) 10)
        (conj! errors (str "COFOG backbone must have 10 divisions, found " (count divisions)))))
    ;; G3 authority-bearing: every profile names a legitimate governing authority + mode.
    (let [service-ids @seen-ids
          errs (persistent! errors)
          errs (into errs (mapcat #(validate-profile % "polity" ":polity-profile/" service-ids)
                                  (get doc ":polity-profiles" [])))
          errs (into errs (mapcat #(validate-profile % "country" ":country-profile/" service-ids)
                                  (get doc ":country-profiles" [])))]
      (vec errs))))

;; ── coverage ──────────────────────────────────────────────────────────────

(defn- inc-count
  "by_X[key] = by_X.get(key, 0) + 1, with a default key of '?' (matches Python .get(.,'?'))."
  [m s attr]
  (let [k (get s attr "?")]
    (assoc m k (inc (get m k 0)))))

(defn coverage
  "Compute honest coverage figures. 1:1 with coverage()."
  [doc]
  (let [cofog (get doc ":cofog" [])
        divisions (filter #(= (get % ":cofog/level") ":division") cofog)
        groups (filter #(= (get % ":cofog/level") ":group") cofog)
        services (get doc ":services" [])
        div-of (fn [code] (first (str/split code #"\." 2)))
        covered-divs (set (map #(div-of (get % ":egov.service/cofog")) services))
        covered-groups (set (map #(get % ":egov.service/cofog") services))
        by-domain (reduce #(inc-count %1 %2 ":egov.service/domain") {} services)
        by-module (reduce #(inc-count %1 %2 ":egov.service/module") {} services)
        by-maturity (reduce #(inc-count %1 %2 ":egov.service/maturity") {} services)
        polity-cov (mapv (fn [p]
                           {"id" (get p ":polity-profile/id")
                            "name" (get p ":polity-profile/name")
                            "operated_by" (get p ":polity-profile/operated-by")
                            "authority_mode" (get p ":polity-profile/authority-mode")
                            "bound" (count (get p ":polity-profile/bindings" []))})
                         (get doc ":polity-profiles" []))
        profiles (get doc ":country-profiles" [])
        localization (reduce (fn [loc p]
                               (reduce (fn [loc b]
                                         (let [sid (get b ":bind/service")]
                                           (assoc loc sid (inc (get loc sid 0)))))
                                       loc (get p ":country-profile/bindings" [])))
                             {} profiles)
        profile-cov (mapv (fn [p]
                            {"iso3" (get p ":country-profile/iso3")
                             "name" (get p ":country-profile/name")
                             "operated_by" (get p ":country-profile/operated-by")
                             "sourcing" (get p ":country-profile/sourcing")
                             "bound" (count (get p ":country-profile/bindings" []))})
                          profiles)]
    {"divisions_total" (count divisions)
     "divisions_covered" (count covered-divs)
     "groups_total" (count groups)
     "groups_covered" (count covered-groups)
     "services_total" (count services)
     "by_domain" by-domain
     "by_module" by-module
     "by_maturity" by-maturity
     "required_domains_covered" (vec (sort (set/intersection required-domains (set (keys by-domain)))))
     "required_domains_missing" (vec (sort (set/difference required-domains (set (keys by-domain)))))
     "executable_services" (get by-maturity ":executable" 0)
     "polities" polity-cov
     "profiles" profile-cov
     "countries" (count profile-cov)
     "localization" localization}))

;; ── report rendering (matches render_report's f-strings, byte-for-byte) ─────

(defn- join-or-dash
  "', '.join(xs) or fallback — Python truthiness: empty seq → fallback."
  [xs fallback]
  (let [s (str/join ", " xs)]
    (if (empty? s) fallback s)))

(defn render-report
  "Render the coverage markdown (1:1 with render_report). \\n-joined lines."
  [doc cov errors]
  (let [std (get doc ":standard" {})
        L (transient [])]
    (conj! L (str "# " (get std ":standard/title-en" "e-gov standard") " — coverage"))
    (conj! L "")
    (conj! L (str "- standard: `" (get std ":standard/id") "` v" (get std ":standard/version")))
    (conj! L (str "- backbone: " (get std ":standard/backbone")))
    (conj! L (str "- validation: " (if (empty? errors) "✅ PASS" (str "❌ " (count errors) " error(s)"))))
    (conj! L "")
    (conj! L "## COFOG function-space coverage (honest)")
    (conj! L "")
    (conj! L (str "- divisions covered: **" (get cov "divisions_covered") "/" (get cov "divisions_total") "**"))
    (conj! L (str "- groups covered: **" (get cov "groups_covered") "/" (get cov "groups_total") "**"))
    (conj! L (str "- standardized services: **" (get cov "services_total") "**"))
    (conj! L (str "- executable (module .solve runs): **" (get cov "executable_services") "** "
                  "(R0 — all modules raise; deployment Council+operator gated)"))
    (conj! L "")
    (conj! L "## Named transactional domains (user request)")
    (conj! L "")
    (conj! L (str "- covered: " (join-or-dash (get cov "required_domains_covered") "—")))
    (conj! L (str "- missing: " (join-or-dash (get cov "required_domains_missing") "— (all covered)")))
    (conj! L "")
    (conj! L "## Services by domain")
    (conj! L "")
    (doseq [k (sort (keys (get cov "by_domain")))]
      (conj! L (str "- " k ": " (get (get cov "by_domain") k))))
    (conj! L "")
    (conj! L "## Services by maturity")
    (conj! L "")
    (doseq [k (sort (keys (get cov "by_maturity")))]
      (conj! L (str "- " k ": " (get (get cov "by_maturity") k))))
    (conj! L "")
    (conj! L "## Polity profiles (principal A — the Kingdom's own 統治機構)")
    (conj! L "")
    (if (seq (get cov "polities"))
      (doseq [p (get cov "polities")]
        (conj! L (str "- " (get p "name") ": " (get p "bound") " organs bound "
                      "[" (get p "operated_by") " / " (get p "authority_mode") "]")))
      (conj! L "- none yet"))
    (conj! L "")
    (conj! L (str "## Country profiles (principal B — " (get cov "countries") " nation-state adopters)"))
    (conj! L "")
    (if (seq (get cov "profiles"))
      (doseq [p (get cov "profiles")]
        (conj! L (str "- " (get p "iso3") " (" (get p "name") "): " (get p "bound") " services bound "
                      "[" (get p "operated_by") " / sourcing " (get p "sourcing") "]")))
      (conj! L "- none yet"))
    (conj! L "")
    (conj! L "## Per-service localization (各国調整 — how many countries localize each service)")
    (conj! L "")
    (let [services (into {} (map (fn [s] [(get s ":egov.service/id") s]) (get doc ":services" [])))
          loc (get cov "localization")
          ;; sorted(services, key=lambda x: (-localization.get(x,0), x))
          sids (sort-by (fn [x] [(- (get loc x 0)) x]) (keys services))]
      (doseq [sid sids]
        (let [n (get loc sid 0)
              ja (get (get services sid) ":egov.service/ja" "")]
          (conj! L (str "- `" sid "` (" ja "): **" n "** / " (get cov "countries") " countries")))))
    (conj! L "")
    (when (seq errors)
      (conj! L "## Validation errors")
      (conj! L "")
      (doseq [e errors]
        (conj! L (str "- ❌ " e)))
      (conj! L ""))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: validate + write out/coverage.md (file I/O at the edge). Mirrors main()."
     [& argv]
     (let [here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           args (vec argv)
           [args outdir] (if (some #{"--out"} args)
                           (let [i (.indexOf args "--out")]
                             [(into (subvec args 0 i) (subvec args (+ i 2)))
                              (clojure.java.io/file (nth args (inc i)))])
                           [args (clojure.java.io/file here "methods" "out")])
           path (if (seq args)
                  (clojure.java.io/file (first args))
                  (clojure.java.io/file here "data" "cofog-standard.kotoba.edn"))
           profiles-dir (clojure.java.io/file here "data" "profiles")
           doc (load-standard path profiles-dir)
           errors (validate doc)
           cov (coverage doc)
           report (render-report doc cov errors)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "coverage.md") report)
       (println report)
       (println (str "\n[written] " (clojure.java.io/file outdir "coverage.md")))
       (if (seq errors) 1 0))))

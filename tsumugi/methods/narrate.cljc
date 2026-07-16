(ns tsumugi.methods.narrate
  "tsumugi 紡ぎ — Murakumo-only power-intel narration (ADR-2606092000 + 2605215000).
  Clojure port of methods/narrate.py — the PURE core (G6 host-assert + prompt/digest/Datom
  builders). The urllib LiteLLM `infer` network leg + the file-writing `run`/`main` are omitted
  (network/IO edge); `infer` keeps the G6 assert + the dry-run branch (gate=false → no network).

  G6 / ADR-2605215000: religious-corp inference is Murakumo-fleet-only — `assert-murakumo`
  refuses any non-fleet host (external OpenAI/Anthropic-direct/RunPod/Vertex/Bedrock prohibited)
  even in dry-run, so a misconfig is caught early. S1 aggregate-only · mirror · published=false (G7)."
  (:require [clojure.string :as str]))

(def MURAKUMO-BASE-URL "http://127.0.0.1:4000")
(def MURAKUMO-MODEL "gemma3:4b")           ; Maxwell (Gemma 4 E4B) = target weight

;; the ONLY hosts a religious-corp inference call may reach (loopback LiteLLM + EVO-X2 LAN);
;; extendable via MURAKUMO_ALLOWED_HOSTS env, but NEVER an external provider.
(def FLEET-HOSTS
  (into #{"127.0.0.1" "localhost" "::1" "192.168.1.70"}
        (->> (str/split (or #?(:clj (System/getenv "MURAKUMO_ALLOWED_HOSTS") :default nil) "") #",")
             (map str/trim)
             (remove str/blank?))))

(def SYSTEM-PROMPT
  (str "You are tsumugi, a terse power-dynamics intel narrator for a religious non-profit. "
       "You are given AGGREGATE, edge-primary readouts of (A) 産官学報 cross-sector concentration "
       "by locality and (B) declared ideology/faction camps. Narrate in at most 4 sentences. "
       "HARD RULES: mirror-not-target (this is an openness/resilience map, never a target-list); "
       "non-adjudicating (state concentration/alignment as structural fact — NEVER 'corruption', "
       "'collusion', 'extremist', or any verdict); aggregate-only (never name or imply a private "
       "individual — only institutions and public seats appear); plural (note bridges/pluralism). "
       "No preamble, no recommendation to act against anyone."))

(defn- url-host
  "Mirror urlparse(base_url).hostname.lower() for the cases at hand (scheme://host[:port]/…,
  incl. bracketed IPv6). No scheme → \"\" (urlparse treats a bare host:port as scheme:path)."
  [u]
  (let [m (re-find #"^[a-zA-Z][a-zA-Z0-9+.\-]*://(\[[^\]]*\]|[^/:?#]*)" (str u))
        h (some-> m second (str/replace "[" "") (str/replace "]" ""))]
    (str/lower-case (or h ""))))

(defn assert-murakumo
  "G6 — refuse any inference host that is not the Murakumo fleet."
  [base-url]
  (let [host (url-host base-url)]
    (when-not (contains? FLEET-HOSTS host)
      (throw (ex-info (str "G6 / ADR-2605215000 breach: inference host " (pr-str host)
                           " is not the Murakumo fleet " (vec (sort FLEET-HOSTS))
                           ". Religious-corp inference is Murakumo-only — external "
                           "providers (OpenAI/Anthropic-direct/RunPod/Vertex/Bedrock) are prohibited.")
                      {:gate "G6" :host host})))))

(defn build-prompt
  "Compose the Charter-safe USER prompt from aggregate readouts only (no per-person data)."
  [scale-result banner-result]
  (let [top-loc (take 5 (get scale-result "localities"))
        top-ck (take 6 (get scale-result "collective_kinds" []))
        top-brokers (take 5 (get scale-result "brokers"))
        camps (take 5 (get banner-result "camps"))
        bridges (take 5 (get banner-result "bridges"))
        vertical (take 5 (get scale-result "vertical" []))
        lines (concat
               ["(A) 産官学報 concentration — top localities:"]
               (map (fn [x] (str "  - " (get x "locality") ": sectors "
                                 (str/join " " (get x "sectors"))
                                 " (diversity " (get x "sector_diversity")
                                 ", concentration " (get x "concentration") ")")) top-loc)
               (when (seq top-ck) ["(A) granularity (粒度) — incident load by collective-kind:"])
               (map (fn [x] (str "  - " (get x "ja") ": " (get x "node_count")
                                 " nodes, load " (get x "load"))) top-ck)
               ["(A) top cross-sector brokers (seat/org ids):"]
               (map (fn [x] (str "  - " (get x "id") " (" (get x "sector") " → "
                                 (str/join " " (get x "bridges_to")) ", span " (get x "span") ")")) top-brokers)
               (when (seq vertical) ["(A) vertically-integrated orgs (跨-scale 縦の集中):"])
               (map (fn [x] (str "  - " (get x "label") ": spans " (get x "scale_span")
                                 " scales (" (str/join " " (get x "scales")) ") across "
                                 (get x "locality_span") " localities (load " (get x "load") ")")) vertical)
               ["(B) declared camps by reach:"]
               (map (fn [c] (str "  - " (get c "label") " (kind " (get c "kind")
                                 ", reach " (get c "reach") ", " (get c "member_count") " members)")) camps)
               ["(B) bridges (entities flying ≥2 banners — pluralism):"]
               (map (fn [b] (str "  - " (get b "label") ": " (str/join " · " (get b "banners")))) bridges))]
    (str/join "\n" lines)))

(defn edn-str [s]
  (str "\"" (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))

(defn build-digest
  "Fuse scale (A) + banner (B) into ONE kotoba-EDN intel digest — the canonical, machine-readable
  summary the Murakumo fleet / kotoba Datom log consumes (S1 aggregate; mirror; published=false)."
  [scale-result banner-result]
  (let [L (concat
           [";; tsumugi power-intel digest — GENERATED (ADR-2606092000); aggregate readouts only."
            ";; consumed by the Murakumo-only narration (G6) + the kotoba Datom log. No hand-edit."
            "[{:digest/kind :tsumugi-power-intel"
            " :digest/scale-top-localities ["]
           (map (fn [x] (str "   {:locality " (edn-str (get x "locality"))
                             " :diversity " (get x "sector_diversity")
                             " :concentration " (get x "concentration") "}"))
                (take 5 (get scale-result "localities")))
           [" ]" " :digest/vertical ["]
           (map (fn [x] (str "   {:org " (edn-str (get x "label"))
                             " :scale-span " (get x "scale_span")
                             " :locality-span " (get x "locality_span")
                             " :load " (get x "load") "}"))
                (take 5 (get scale-result "vertical" [])))
           [" ]" " :digest/collective-kinds ["]
           (map (fn [x] (str "   {:kind " (get x "kind") " :nodes " (get x "node_count")
                             " :load " (get x "load") "}"))
                (get scale-result "collective_kinds" []))
           [" ]" " :digest/banner-camps ["]
           (map (fn [c] (str "   {:banner " (edn-str (get c "label"))
                             " :reach " (get c "reach") " :members " (get c "member_count") "}"))
                (take 6 (get banner-result "camps")))
           [" ]" " :digest/bridges ["]
           (map (fn [b] (str "   {:ent " (edn-str (get b "label")) " :span " (get b "span") "}"))
                (take 5 (get banner-result "bridges")))
           [" ]" " :digest/published false}]"])]
    (str (str/join "\n" L) "\n")))

(defn build-datoms
  "Emit the intel as a kotoba Datom transaction (EAVT entity-maps) — the append-only CANONICAL-STATE
  shape the substrate ingests (ADR-2605312345). Aggregate-only (S1); mirror; published=false (G7)."
  [scale-result banner-result]
  (let [L (concat
           [";; tsumugi power-intel Datoms — GENERATED (ADR-2606092000); append-only assertions for"
            ";; the kotoba Datom log (ADR-2605312345). Aggregate-only (S1), mirror, published=false."
            "[;; ── scale clusters (per-locality cross-sector concentration readout) ──"]
           (map (fn [x] (str " {:db/id \"tsumugi.cluster/" (get x "locality") "\" "
                             ":cluster/locality " (edn-str (get x "locality")) " "
                             ":cluster/sector-diversity " (get x "sector_diversity") " "
                             ":cluster/concentration " (get x "concentration") " "
                             ":tsumugi/mirror true :tsumugi/published false}"))
                (take 8 (get scale-result "localities")))
           [" ;; ── vertically-integrated organizations (cross-scale 縦の集中) ──"]
           (map (fn [x] (str " {:db/id \"tsumugi.vertical/" (get x "root") "\" "
                             ":vertical/org " (edn-str (get x "label")) " "
                             ":vertical/scale-span " (get x "scale_span") " "
                             ":vertical/locality-span " (get x "locality_span") " "
                             ":vertical/load " (get x "load") " :tsumugi/mirror true :tsumugi/published false}"))
                (take 5 (get scale-result "vertical" [])))
           [" ;; ── declared 旗 camps (edge-primary reach; non-adjudicating) ──"]
           (map (fn [c] (str " {:db/id \"tsumugi.camp/" (get c "banner") "\" "
                             ":camp/banner " (edn-str (get c "label")) " "
                             ":camp/reach " (get c "reach") " :camp/members " (get c "member_count") " "
                             ":tsumugi/non-adjudicating true :tsumugi/published false}"))
                (take 8 (get banner-result "camps")))
           ["]"])]
    (str (str/join "\n" L) "\n")))

(defn infer
  "The `infer` node — pure G6 assert + dry-run branch. Returns [text status].
  status ∈ {\"dry-run\" \"ok\" \"unreachable\"}. The real LiteLLM call (gate=true) is the omitted
  network leg; G6 ALWAYS asserts the host (even dry-run) so a misconfig is caught early."
  [_prompt {:keys [gate base-url] :or {gate false base-url MURAKUMO-BASE-URL}}]
  (assert-murakumo base-url)
  (if-not gate
    [nil "dry-run"]
    ;; network leg omitted in the cljc port (urllib → LiteLLM); operator-gated on the fleet.
    (throw (ex-info "infer network leg omitted in cljc port (run on the Murakumo fleet via the py twin)"
                    {:gate "G7-network-leg"}))))

#?(:clj
   (defn -main
     "CLI entry: mirrors narrate.main/run — fuse analyze-scale + analyze-banner into an aggregate
     intel digest/datoms, build the Murakumo prompt, and write the G7 dry-run artifact. The live
     Murakumo (G6/LiteLLM) network leg is operator-gated and omitted (infer returns dry-run unless
     --live / TSUMUGI_MURAKUMO_GATE=1). ADR-2606261200 cljc-native operator leg."
     [& argv]
     (let [gate  (or (some #{"--live"} (vec argv)) (= (System/getenv "TSUMUGI_MURAKUMO_GATE") "1"))
           here  (let [f  (when (and *file* (not (str/blank? *file*))) (clojure.java.io/file *file*))
                       pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                   (if (and pp (.isDirectory (clojure.java.io/file pp "data"))) pp (clojure.java.io/file "20-actors" "tsumugi")))
           out   (clojure.java.io/file here "out")
           s-load (requiring-resolve 'tsumugi.methods.analyze-scale/load-graph)
           s-an   (requiring-resolve 'tsumugi.methods.analyze-scale/analyze)
           b-load (requiring-resolve 'tsumugi.methods.analyze-banner/load-file*)
           b-an   (requiring-resolve 'tsumugi.methods.analyze-banner/analyze)
           [s-nodes s-ties] (s-load (str (clojure.java.io/file here "data" "seed-scale-power.kotoba.edn")))
           scale  (s-an s-nodes s-ties)
           {:keys [banners ents flies]} (b-load (str (clojure.java.io/file here "data" "seed-banner.kotoba.edn")))
           banner (b-an banners ents flies)
           prompt (build-prompt scale banner)
           [text status] (infer prompt {:gate gate})]
       (.mkdirs out)
       (spit (clojure.java.io/file out "intel-digest.kotoba.edn") (build-digest scale banner))
       (spit (clojure.java.io/file out "intel-datoms.kotoba.edn") (build-datoms scale banner))
       (spit (clojure.java.io/file out "narration.dryrun.md")
             (str "<!-- tsumugi Murakumo narration — model=" MURAKUMO-MODEL " via " MURAKUMO-BASE-URL
                  " (G6 Murakumo-only); status=" status "; published=false (G7) -->\n\n"
                  "## SYSTEM\n" SYSTEM-PROMPT "\n\n## USER (aggregate intel)\n" prompt "\n"
                  (if text (str "\n## MURAKUMO OUTPUT\n" text "\n") "")))
       (when text (spit (clojure.java.io/file out "narration.md") (str text "\n")))
       (println (str "[tsumugi/narrate] status=" status " · model=" MURAKUMO-MODEL
                     " · published=false → " (.getPath (clojure.java.io/file out "narration.dryrun.md")))))))

(ns aburi.methods.ingest
  "aburi 炙り — ingest: the member's OWN privacy exports → tracker-exposure graph datoms.
  ADR-2606161630.

  Pure stdlib port of ingest.py (no network I/O, no side-effects at load time).
  Constitutional gates:
    G1 own-data-only — input is the MEMBER'S OWN export. No third-party PII.
    G2/G8 guard() RAISES on credential-shaped keys and raw-identifier values
            (PAN/UUID/email).  graph_to_datoms projects ONLY the NODE_ATTRS/EDGE_ATTRS
            allowlist, so no credential/raw-id attr can reach the substrate even by accident.
    G3 domain→collector mappings are DISCLOSED facts (public catalogues), never verdicts.
    G5 every tx carries the intake content CID; re-ingest of the same export is a no-op (dedup).
    G7 local-only: no network I/O in this namespace.

  Clojure keyword strings (':ns/name') are kept as strings (NOT Clojure keywords)
  throughout — for byte-parity with the Python implementation's CID preimage."
  (:require [clojure.string :as str]
            [aburi.methods.datom-emit :as de]
            #?(:clj [clojure.edn :as edn])))

;; ─── G2/G8 guard ─────────────────────────────────────────────────────────────

(def ^:private forbidden-key-tokens
  ["password" "passwd" "secret" "otp" "cvv" "credential" "token" "bearer"
   "pin" "session" "cookie" "idfa" "gaid" "aaid" "imei" "imsi" "udid"
   "idfv" "advertising-id" "advertising_id" "device-id" "device_id" "serial"
   "email" "e-mail" "phone" "msisdn" "ssn" "passport"])

;; 13–19 consecutive digits (spaces/dashes allowed) → a PAN / long raw-identifier shape.
(def ^:private pan-re
  #"(?:\d[ -]?){13,19}")

;; an advertising-id (UUID) shape e.g. 38400000-8cf0-11bd-b23e-10b96e40000d
(def ^:private adid-re
  #"(?i)\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b")

;; an email shape
(def ^:private email-re
  #"[^@\s]+@[^@\s]+\.[^@\s]+")

(defn- walk-node
  "Recursively yield [path role leaf] triples (depth-first)."
  [node path]
  (cond
    (map? node)
    (mapcat (fn [[k v]]
              (into [[path "key" k]]
                    (walk-node v (str path "/" k))))
            node)

    (sequential? node)
    (mapcat #(walk-node % path) node)

    :else
    [[path "value" node]]))

(defn guard
  "G2/G8 structural gate: refuse credential-shaped keys and raw-identifier values
  anywhere in the intake. Throws ex-info on violation."
  [doc]
  (doseq [[path role leaf] (walk-node doc "")]
    (let [s (str leaf)
          low (str/lower-case s)]
      (when (= role "key")
        (when (some #(str/includes? low %) forbidden-key-tokens)
          (throw (ex-info (str "G8: credential/raw-identifier key " (pr-str s)
                               " is unrepresentable in aburi")
                          {:aburi/g8-violation true :key s}))))
      (when (= role "value")
        ;; PAN / long-id check
        (when-let [m (re-find pan-re s)]
          (let [digits (str/replace m #"\D" "")]
            (when (>= (count digits) 13)
              (throw (ex-info (str "G8: PAN/long-id value at " (if (str/blank? path) "/" path)
                                   " is unrepresentable")
                              {:aburi/g8-violation true :path path})))))
        ;; ADID / UUID check
        (when (re-find adid-re s)
          (throw (ex-info (str "G8: advertising-id (UUID) value at "
                               (if (str/blank? path) "/" path)
                               " is unrepresentable")
                          {:aburi/g8-violation true :path path})))
        ;; email check
        (when (re-find email-re s)
          (throw (ex-info (str "G8: email value at "
                               (if (str/blank? path) "/" path)
                               " is unrepresentable")
                          {:aburi/g8-violation true :path path})))))))

;; ─── graph → datoms (G8: only the datom_emit allowlist is ever projected) ────

(defn load-graph
  "Split a seed-shape form list into {:nodes {id form} :edges [form]}."
  [forms]
  (reduce (fn [{:keys [nodes edges]} f]
            (cond
              (not (map? f)) {:nodes nodes :edges edges}
              (contains? f ":organism/id")
              {:nodes (assoc nodes (get f ":organism/id") f) :edges edges}
              (and (contains? f ":en/from") (contains? f ":en/to"))
              {:nodes nodes :edges (conj edges f)}
              :else {:nodes nodes :edges edges}))
          {:nodes {} :edges []}
          forms))

(defn graph-to-datoms
  "Project a tracker-exposure graph into append-only [:db/add e a v] datoms.
  ONLY de/node-attrs / de/edge-attrs allowlist is emitted — credential/raw-id
  attrs cannot pass (G8)."
  [nodes edges {:keys [intake-cid kind]}]
  (let [out (volatile! [])]
    (doseq [[nid n] nodes]
      (doseq [a de/node-attrs]
        (when-let [v (get n a)]
          (vswap! out conj [":db/add" nid a v])))
      (vswap! out conj [":db/add" nid ":aburi.intake/cid" intake-cid])
      (vswap! out conj [":db/add" nid ":aburi.intake/kind" kind]))
    (doseq [e edges]
      (let [eid (str "en." (get e ":en/from") "."
                     (str/replace (str (get e ":en/kind")) #"^:" "") "."
                     (get e ":en/to"))]
        (doseq [a de/edge-attrs]
          (when-let [v (get e a)]
            (vswap! out conj [":db/add" eid a v])))))
    @out))

;; ─── disclosed mapping tables (PUBLIC facts; extend per export — G3/G5) ───────

(def domain-collector
  {"googleadservices.com"  "ax.col.google-admanager"
   "doubleclick.net"       "ax.col.google-admanager"
   "googlesyndication.com" "ax.col.admob"
   "app-measurement.com"   "ax.col.firebase"
   "crashlytics.com"       "ax.col.firebase"
   "graph.facebook.com"    "ax.col.meta-audience"
   "facebook.com"          "ax.col.meta-audience"
   "applovin.com"          "ax.col.applovin"
   "applvn.com"            "ax.col.applovin"
   "unityads.unity3d.com"  "ax.col.unity-ads"
   "unity3d.com"           "ax.col.unity-ads"
   "supersonicads.com"     "ax.col.ironsource"
   "adsmoloco.com"         "ax.col.ironsource"
   "adsrvr.org"            "ax.col.the-trade-desk"
   "criteo.com"            "ax.col.criteo"
   "amazon-adsystem.com"   "ax.col.amazon-ads"
   "appsflyer.com"         "ax.col.appsflyer"
   "adjust.com"            "ax.col.adjust"
   "ads-twitter.com"       "ax.col.x-ads"
   "rlcdn.com"             "ax.col.liveramp"
   "pippio.com"            "ax.col.liveramp"})

(def permission-map
  {"ACCESS_FINE_LOCATION"                        ["ax.perm.precise-location" "ax.dt.precise-location"]
   "ACCESS_COARSE_LOCATION"                      ["ax.perm.coarse-location"  "ax.dt.coarse-location"]
   "READ_CONTACTS"                               ["ax.perm.contacts"         "ax.dt.contacts"]
   "AD_ID"                                       ["ax.perm.ad-id"            "ax.dt.device-id"]
   "com.google.android.gms.permission.AD_ID"     ["ax.perm.ad-id"            "ax.dt.device-id"]
   "RECORD_AUDIO"                                ["ax.perm.microphone"       "ax.dt.app-usage"]
   "READ_MEDIA_IMAGES"                           ["ax.perm.photos"           "ax.dt.photos"]})

(def name-collector
  {"admob"                  "ax.col.admob"
   "google admob"           "ax.col.admob"
   "google ad manager"      "ax.col.google-admanager"
   "doubleclick"            "ax.col.google-admanager"
   "ad manager"             "ax.col.google-admanager"
   "firebase"               "ax.col.firebase"
   "google analytics"       "ax.col.firebase"
   "meta"                   "ax.col.meta-audience"
   "facebook"               "ax.col.meta-audience"
   "audience network"       "ax.col.meta-audience"
   "meta audience network"  "ax.col.meta-audience"
   "applovin"               "ax.col.applovin"
   "unity"                  "ax.col.unity-ads"
   "unity ads"              "ax.col.unity-ads"
   "ironsource"             "ax.col.ironsource"
   "trade desk"             "ax.col.the-trade-desk"
   "the trade desk"         "ax.col.the-trade-desk"
   "criteo"                 "ax.col.criteo"
   "amazon"                 "ax.col.amazon-ads"
   "amazon advertising"     "ax.col.amazon-ads"
   "appsflyer"              "ax.col.appsflyer"
   "adjust"                 "ax.col.adjust"
   "x ads"                  "ax.col.x-ads"
   "twitter"                "ax.col.x-ads"
   "liveramp"               "ax.col.liveramp"})

(defn resolve-collector-name
  "Map a Play/disclosed collector display name to a catalogued collector node id.
  Exact match first, then substring containment, else nil. A name→collector mapping
  is a DISCLOSED fact (G3)."
  [name]
  (let [low (str/lower-case (str/trim (str name)))]
    (or (get name-collector low)
        (first (keep (fn [[alias cid]]
                       (when (or (str/includes? low alias)
                                 (str/includes? alias low))
                         cid))
                     name-collector)))))

(def category-map
  {"Location"            ["ax.perm.coarse-location"    "ax.dt.coarse-location"]
   "Precise Location"    ["ax.perm.precise-location"   "ax.dt.precise-location"]
   "Coarse Location"     ["ax.perm.coarse-location"    "ax.dt.coarse-location"]
   "Identifiers"         ["ax.perm.ad-id"              "ax.dt.device-id"]
   "Device or other IDs" ["ax.perm.ad-id"              "ax.dt.device-id"]
   "Contacts"            ["ax.perm.contacts"           "ax.dt.contacts"]
   "Contact Info"        ["ax.perm.contacts"           "ax.dt.contacts"]
   "Browsing History"    ["ax.perm.browsing-history"   "ax.dt.browsing"]
   "Search History"      ["ax.perm.browsing-history"   "ax.dt.browsing"]
   "Purchases"           ["ax.perm.purchase-history"   "ax.dt.purchases"]
   "Purchase History"    ["ax.perm.purchase-history"   "ax.dt.purchases"]
   "Usage Data"          ["ax.perm.app-usage"          "ax.dt.app-usage"]
   "App Activity"        ["ax.perm.app-usage"          "ax.dt.app-usage"]
   "Photos or Videos"    ["ax.perm.photos"             "ax.dt.photos"]
   "Health and Fitness"  ["ax.perm.health"             "ax.dt.health-fitness"]})

;; minimal ontology node templates so an ingested graph is self-contained + analyzable.
(def ^:private perm-labels
  {"ax.perm.ad-id"           ["Advertising ID (GAID / IDFA)"  ":ad-id"             ":sensitive"]
   "ax.perm.precise-location"["Precise location"              ":precise-location"  ":critical"]
   "ax.perm.coarse-location" ["Approximate location"          ":location"          ":sensitive"]
   "ax.perm.contacts"        ["Contacts / address book"       ":contacts"          ":sensitive"]
   "ax.perm.browsing-history"["Browsing / search history"     ":browsing-history"  ":sensitive"]
   "ax.perm.purchase-history"["Purchase history"              ":purchase-history"  ":moderate"]
   "ax.perm.app-usage"       ["App / device usage"            ":app-usage"         ":moderate"]
   "ax.perm.photos"          ["Photos / media"                ":photos"            ":sensitive"]
   "ax.perm.microphone"      ["Microphone"                    ":microphone"        ":sensitive"]
   "ax.perm.health"          ["Health / fitness data"         ":health"            ":critical"]})

(def ^:private dt-labels
  {"ax.dt.precise-location"  ["Precise location"              ":precise-location"  ":critical"]
   "ax.dt.coarse-location"   ["Coarse location"               ":coarse-location"   ":sensitive"]
   "ax.dt.device-id"         ["Device / advertising ID"       ":device-id"         ":sensitive"]
   "ax.dt.contacts"          ["Contacts"                      ":contacts"          ":sensitive"]
   "ax.dt.browsing"          ["Browsing / search"             ":browsing"          ":sensitive"]
   "ax.dt.purchases"         ["Purchases"                     ":purchases"         ":moderate"]
   "ax.dt.app-usage"         ["App usage / events"            ":app-usage"         ":moderate"]
   "ax.dt.photos"            ["Photos / media"                ":photos"            ":sensitive"]
   "ax.dt.health-fitness"    ["Health / fitness"              ":health-fitness"    ":critical"]})

(def ^:private col-labels
  {"ax.col.admob"            ["Google AdMob"                      ":ad-network"  "org.corp.alphabet"  ":exodus"]
   "ax.col.google-admanager" ["Google Ad Manager (DoubleClick)"    ":exchange"    "org.corp.alphabet"  ":sellers-json"]
   "ax.col.firebase"         ["Google Firebase Analytics"          ":analytics"   "org.corp.alphabet"  ":exodus"]
   "ax.col.meta-audience"    ["Meta Audience Network (FB SDK)"     ":ad-network"  "org.corp.meta"      ":exodus"]
   "ax.col.applovin"         ["AppLovin"                           ":ad-network"  "org.corp.applovin"  ":exodus"]
   "ax.col.unity-ads"        ["Unity Ads"                         ":ad-network"  "org.corp.unity"     ":exodus"]
   "ax.col.ironsource"       ["ironSource (mediation)"            ":ssp"         "org.corp.unity"     ":exodus"]
   "ax.col.the-trade-desk"   ["The Trade Desk"                    ":dsp"         "org.corp.ttd"       ":sellers-json"]
   "ax.col.criteo"           ["Criteo (retargeting)"              ":dsp"         "org.corp.criteo"    ":sellers-json"]
   "ax.col.amazon-ads"       ["Amazon Advertising"                ":ad-network"  "org.corp.amazon"    ":sellers-json"]
   "ax.col.appsflyer"        ["AppsFlyer (attribution)"           ":analytics"   "org.corp.appsflyer" ":exodus"]
   "ax.col.adjust"           ["Adjust (attribution)"              ":analytics"   "org.corp.adjust"    ":exodus"]
   "ax.col.x-ads"            ["X Ads"                             ":ad-network"  "org.corp.x-corp"    ":sellers-json"]
   "ax.col.liveramp"         ["LiveRamp (identity broker)"        ":data-broker" "org.corp.liveramp"  ":iab"]})

;; ─── _GraphBuilder equivalent ─────────────────────────────────────────────────
;; Mutable builder uses atoms to accumulate nodes/edges with dedup.

(defn- make-builder
  "Return a builder map with mutable :nodes and :edges atoms."
  []
  {:nodes (atom {})
   :edges (atom {})})

(defn- add-surface [b sid label kind operator]
  (swap! (:nodes b) update sid
         #(or % {":organism/id"      sid
                 ":organism/kind"    ":surface"
                 ":organism/label"   label
                 ":surface/kind"     kind
                 ":surface/operator" operator
                 ":organism/sourcing" ":representative"})))

(defn- add-perm [b pid]
  (let [[label kind sens] (get perm-labels pid [pid ":unknown" ":moderate"])]
    (swap! (:nodes b) update pid
           #(or % {":organism/id"          pid
                   ":organism/kind"        ":permission"
                   ":organism/label"       label
                   ":permission/kind"      kind
                   ":permission/sensitivity" sens
                   ":organism/sourcing"    ":representative"}))))

(defn- add-collector [b cid]
  (let [[label kind org cat sourcing]
        (if-let [[l k o c] (get col-labels cid)]
          [l k o c ":authoritative"]
          [cid ":tracker-sdk" "org.corp.unknown" ":exodus" ":representative"])]
    (swap! (:nodes b) update cid
           #(or % {":organism/id"       cid
                   ":organism/kind"     ":collector"
                   ":organism/label"    label
                   ":collector/kind"    kind
                   ":collector/org"     org
                   ":collector/catalog" cat
                   ":organism/sourcing" sourcing}))))

(defn- add-datatype [b did]
  (let [[label kind sens] (get dt-labels did [did ":app-usage" ":moderate"])]
    (swap! (:nodes b) update did
           #(or % {":organism/id"           did
                   ":organism/kind"         ":datatype"
                   ":organism/label"        label
                   ":datatype/kind"         kind
                   ":datatype/sensitivity"  sens
                   ":organism/sourcing"     ":representative"}))))

(defn- add-edge [b frm to kind load]
  (let [k [frm to kind]]
    (swap! (:edges b)
           (fn [m]
             (if (contains? m k)
               (update-in m [k ":en/load"] (fn [cur] (max (double cur) (double load))))
               (assoc m k {":en/from"      frm
                           ":en/to"        to
                           ":en/kind"      kind
                           ":en/load"      (double load)
                           ":en/sourcing"  ":representative"}))))))

(defn- builder-forms [b]
  (concat (vals @(:nodes b)) (vals @(:edges b))))

;; ─── per-format adapters ──────────────────────────────────────────────────────

(defn adapt-apple-app-privacy-report
  "iOS App Privacy Report.
  raw = {:apps [{:app str :accessed [cat…] :domains [dom…]}]}"
  [raw]
  (let [g (make-builder)
        sid "ax.surface.apple-appstore"]
    (add-surface g sid "Apple App Store / iOS" ":app-store" "org.corp.apple")
    (doseq [app (get raw "apps" [])]
      (doseq [cat (get app "accessed" [])]
        (when-let [[pid did] (get category-map cat)]
          (add-perm g pid)
          (add-datatype g did)
          (add-edge g sid pid ":grants" 0.7)
          (doseq [dom (get app "domains" [])]
            (when-let [cid (get domain-collector (-> dom str/lower-case (str/replace #"^\." "")))]
              (add-collector g cid)
              (add-edge g pid cid ":flows-to" 0.8)
              (add-edge g cid did ":collects" 0.8)))))
      ;; a contacted ad domain with no mapped category still evidences a flow (via ad-id)
      (doseq [dom (get app "domains" [])]
        (when-let [cid (get domain-collector (-> dom str/lower-case (str/replace #"^\." "")))]
          (when (empty? (get app "accessed" []))
            (add-perm g "ax.perm.ad-id")
            (add-datatype g "ax.dt.device-id")
            (add-collector g cid)
            (add-edge g sid "ax.perm.ad-id" ":grants" 0.6)
            (add-edge g "ax.perm.ad-id" cid ":flows-to" 0.7)
            (add-edge g cid "ax.dt.device-id" ":collects" 0.7)))))
    (builder-forms g)))

(defn adapt-play-data-safety
  "Google Play Data-safety.
  raw = {:apps [{:app str :shared [{:type cat :purpose str :collectors [name…]}]}]}"
  [raw]
  (let [g (make-builder)
        sid "ax.surface.google-android"]
    (add-surface g sid "Google / Android + Play" ":os" "org.corp.alphabet")
    (doseq [app (get raw "apps" [])]
      (doseq [share (get app "shared" [])]
        (let [cat (get share "type")]
          (when-let [[pid did] (get category-map cat)]
            (add-perm g pid)
            (add-datatype g did)
            (add-edge g sid pid ":grants" 0.7)
            (let [purpose (str/lower-case (str (get share "purpose" "")))
                  load (if (or (str/includes? purpose "advertis")
                               (str/includes? purpose "marketing"))
                         0.8 0.4)
                  cols (or (seq (get share "collectors"))
                           (when (str/includes? purpose "advertis") ["AppLovin"]))]
              (doseq [cn cols]
                (when-let [cid (resolve-collector-name cn)]
                  (add-collector g cid)
                  (add-edge g pid cid ":flows-to" load)
                  (add-edge g cid did ":collects" load))))))))
    (builder-forms g)))

(defn adapt-google-takeout-ads
  "Google Takeout Ads-settings.
  raw = {:adPersonalization bool :activityControls {:web bool :location bool :youtube bool}}
  (No advertiser names are ingested — they would be PII; only on/off structure.)"
  [raw]
  (let [g (make-builder)
        sid "ax.surface.google-search"]
    (add-surface g sid "Google Search" ":search" "org.corp.alphabet")
    (let [ac (get raw "activityControls" {})]
      (when (get ac "web")
        (add-perm g "ax.perm.browsing-history")
        (add-datatype g "ax.dt.browsing")
        (add-collector g "ax.col.google-admanager")
        (add-edge g sid "ax.perm.browsing-history" ":grants" 0.9)
        (add-edge g "ax.perm.browsing-history" "ax.col.google-admanager" ":flows-to" 0.9)
        (add-edge g "ax.col.google-admanager" "ax.dt.browsing" ":collects" 0.9))
      (when (get raw "adPersonalization")
        (add-perm g "ax.perm.ad-personalization")
        (add-datatype g "ax.dt.identifiers")
        (add-collector g "ax.col.google-admanager")
        (add-edge g sid "ax.perm.ad-personalization" ":grants" 0.8)
        (add-edge g "ax.perm.ad-personalization" "ax.col.google-admanager" ":flows-to" 0.8)
        (add-edge g "ax.col.google-admanager" "ax.dt.identifiers" ":collects" 0.7))
      (when (get ac "location")
        (add-perm g "ax.perm.precise-location")
        (add-datatype g "ax.dt.precise-location")
        (add-collector g "ax.col.admob")
        (add-edge g sid "ax.perm.precise-location" ":grants" 0.8)
        (add-edge g "ax.perm.precise-location" "ax.col.admob" ":flows-to" 0.7)
        (add-edge g "ax.col.admob" "ax.dt.precise-location" ":collects" 0.7)))
    (builder-forms g)))

(defn adapt-android-permission-dump
  "On-device permission dump.
  raw = {:apps [{:package str :grants [perm…]}]}"
  [raw]
  (let [g (make-builder)
        sid "ax.surface.free-mobile-app"]
    (add-surface g sid "On-device apps (Android)" ":mobile-app" "org.corp.various")
    (doseq [app (get raw "apps" [])]
      (doseq [perm (get app "grants" [])]
        (when-let [[pid did] (get permission-map perm)]
          (add-perm g pid)
          (add-datatype g did)
          (add-edge g sid pid ":grants" 0.8)
          ;; a permission that an ad-bearing app holds plausibly flows to AdMob/Meta
          (when (contains? #{"AD_ID" "com.google.android.gms.permission.AD_ID"} perm)
            (add-collector g "ax.col.admob")
            (add-collector g "ax.col.meta-audience")
            (add-edge g pid "ax.col.admob" ":flows-to" 0.7)
            (add-edge g pid "ax.col.meta-audience" ":flows-to" 0.6)
            (add-edge g "ax.col.admob" did ":collects" 0.7)
            (add-edge g "ax.col.meta-audience" did ":collects" 0.6)))))
    (builder-forms g)))

(def adapters
  {":apple-app-privacy-report" adapt-apple-app-privacy-report
   ":play-data-safety"         adapt-play-data-safety
   ":google-takeout-ads"       adapt-google-takeout-ads
   ":android-permission-dump"  adapt-android-permission-dump})

;; ─── export → datoms ──────────────────────────────────────────────────────────

(defn export-to-datoms
  "Adapt one export → guard → graph → append-only datoms (G8 allowlist).
  raw: parsed doc map; kind: ':play-data-safety' etc; intake-cid: content CID string."
  [raw kind intake-cid]
  (guard raw)                              ;; G2/G8 — raw values screened first
  (let [adapter (get adapters kind)]
    (when-not adapter
      (throw (ex-info (str "unknown export kind " (pr-str kind)
                           "; known: " (vec (sort (keys adapters))))
                      {:aburi/unknown-kind kind})))
    (let [forms (adapter raw)]
      (guard forms)                        ;; belt-and-suspenders: screen mapped forms too
      (let [{:keys [nodes edges]} (load-graph forms)]
        (graph-to-datoms nodes edges {:intake-cid intake-cid :kind kind})))))

;; ─── file I/O (clj-only; no IO at load time) ─────────────────────────────────

#?(:clj
   (do
     (defn- sha256-hex [^bytes bs]
       (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") bs)]
         (str/join (map #(let [h (Integer/toHexString (bit-and % 0xff))]
                           (if (= 1 (count h)) (str "0" h) h))
                        d))))

     (defn content-cid
       "Compute the content CID of a byte array (sha256 hex, prefixed 'b' for parity)."
       ^String [^bytes bs]
       (str "b" (sha256-hex bs)))

     (defn load-export
       "Read one local export file (.json / .edn) → [raw-doc content-cid].
       No network I/O."
       [path]
       (let [raw-bytes (java.nio.file.Files/readAllBytes
                        (if (instance? java.nio.file.Path path)
                          path
                          (.toPath (java.io.File. (str path)))))
             text (String. raw-bytes "UTF-8")
             doc (if (str/ends-with? (str path) ".json")
                   ;; use cheshire if available, fall back to clojure.data.json
                   ((requiring-resolve 'cheshire.core/parse-string) text false)
                   (edn/read-string text))]
         [doc (content-cid raw-bytes)]))))

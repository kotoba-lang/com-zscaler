(ns toritsugi.store
  "SSoT for the toritsugi (取次 = citizen government-procedure concierge) actor —
  the coded procedure registry + per-member concierge lifecycle, behind a `Store`
  protocol so the backend is a swap (MemStore default ‖ DatomicStore via
  langchain.db, itself swappable to real Datomic Local / kotoba-server XRPC).

  Domain = the citizen-side paperwork of walking a consenting member through their
  OWN government / municipal procedure. Entities:

    procedure — a coded government procedure (the LINE-like core): 窓口 / 所管 /
                onlineUrl / 必要書類 / 様式 / 手数料 / 法定処理期間 / 根拠法令
                (legal-basis) / provenance / channel / verificationStatus /
                lastVerified / freshnessWindowDays. Seed at
                registry/procedures.seed.json (all unverified-seed at R0).
    member    — the consenting member (申請者本人): DID, active consent-refs,
                adherent-SBT. G3/G4 binding.
    session   — one concierge run: member + procedure + consent-ref + lifecycle
                phase (init → matched → intaked → guided → drafted → submitted →
                tracked, + refused / hold).
    draft     — the assisted applicationDraft artifact (member reviews + owns;
                assist, NOT 作成代理 — G5). Body is an encrypted ref (G6).
    submission— the submissionRecord (member-self-submit default; agent-on-behalf
                is the gated R3 exception — G15).

  Charter (ADR-2605312030): PII NEVER lands here in plaintext — member PII /
  申請内容 / 結果 live ONLY in com.etzhayyim.encrypted.* DID-bound envelopes (G6,
  ADR-2605181100); the store keeps only encrypted refs + non-PII concierge state.
  Integers not floats (fees in JPY, statutory-days, dates as yyyymmdd ints); EAVT
  ground datoms are canonical; the append-only **ledger is the concierge
  genealogy** — immutable procedural provenance. The actor is 案内 + 伴走 + 本人提出
  支援 by default; 代行 is the gated exception (G15)."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [langchain.db :as d]))

(defprotocol Store
  (procedure      [s id])
  (all-procedures [s])
  (member         [s did])
  (session        [s id])
  (sessions-of    [s did]  "concierge sessions for a member")
  (draft-of       [s session-id])
  (submission-of  [s session-id])
  (ledger         [s])
  (record-datom!  [s record] "append a concierge ground fact to the SSoT")
  (append-ledger! [s fact]   "append one immutable concierge-genealogy fact")
  (seed!          [s data]   "bulk-seed entity collections (idempotent upsert)"))

;; ───────────────────────── demo data ─────────────────────────

(defn demo-data
  "Five coded procedures spanning the G14/G8 surface, two members (one consenting,
  one without active consent), and a pre-opened session. PII is kept as encrypted
  refs only (G6) — no plaintext ever enters the store.

    jp-juminhyo-utsushi — maintainer-verified, fresh, full provenance → clean.
    jp-tennyu-todoke    — maintainer-verified, fresh, in-person channel → clean.
    jp-unverified       — unverified-seed → G14 refuses at submit.
    jp-stale            — maintainer-verified but lastVerified far past the
                          freshness window → G14 refuses at submit (stale).
    jp-fabricated       — maintainer-verified but MISSING legal-basis + provenance
                          → G8 refuses (non-fabrication).

  alice carries an active consent for her own 住民票 procedure + an adherent SBT;
  bob has no active consent (G3 refusal path)."
  []
  {:procedures
    {"jp-juminhyo-utsushi"
     {:procedure-id "jp-juminhyo-utsushi"
      :title "住民票の写し交付請求"
      :jurisdiction "jpn" :authority "市区町村 (住民の属する自治体)"
      :channel-type "online"
      :required-docs ["本人確認書類" "マイナンバーカード"]
      :fee-jpy 300 :statutory-days 0
      :legal-basis "住民基本台帳法 §12"
      :provenance "https://www.soumu.go.jp/main_sosiki/jichi_gyousei/c-gyousei/zairyu/juuki.html"
      :verification-status "maintainer-verified"
      :last-verified 20260601 :freshness-window-days 180}
     "jp-tennyu-todoke"
     {:procedure-id "jp-tennyu-todoke"
      :title "転入届"
      :jurisdiction "jpn" :authority "転入先の市区町村"
      :channel-type "in-person"
      :required-docs ["転出証明書" "本人確認書類" "マイナンバーカード"]
      :fee-jpy 0 :statutory-days 0
      :legal-basis "住民基本台帳法 §22"
      :provenance "https://www.soumu.go.jp/main_sosiki/jichi_gyousei/c-gyousei/zairyu/index.html"
      :verification-status "maintainer-verified"
      :last-verified 20260601 :freshness-window-days 180}
     "jp-unverified"
     {:procedure-id "jp-unverified"
      :title "(seed) 未検証の手続き"
      :jurisdiction "jpn" :authority "市区町村"
      :channel-type "in-person"
      :required-docs ["本人確認書類"]
      :fee-jpy 0 :statutory-days 0
      :legal-basis "住民基本台帳法 §X"
      :provenance "https://example.gov/seed"
      :verification-status "unverified-seed"
      :last-verified 20260601 :freshness-window-days 180}
     "jp-stale"
     {:procedure-id "jp-stale"
      :title "(stale) 検証済だが鮮度Window切れ"
      :jurisdiction "jpn" :authority "市区町村"
      :channel-type "online"
      :required-docs ["本人確認書類"]
      :fee-jpy 200 :statutory-days 0
      :legal-basis "住民基本台帳法 §12"
      :provenance "https://example.gov/stale"
      :verification-status "maintainer-verified"
      :last-verified 20250101 :freshness-window-days 180}     ; ≫ 180 days stale @ 20260709
     "jp-fabricated"
     {:procedure-id "jp-fabricated"
      :title "(fabricated) 根拠法令 / provenance 欠落"
      :jurisdiction "jpn" :authority "—"
      :channel-type "online"
      :required-docs ["本人確認書類"]
      :fee-jpy 0 :statutory-days 0
      :legal-basis nil :provenance nil                        ; G8 refuse
      :verification-status "maintainer-verified"
      :last-verified 20260601 :freshness-window-days 180}}
    :members
    {"did:web:member.alice"
     {:member-did "did:web:member.alice"
      :consent-refs #{"consent-alice-juminhyo"}
      :adherent-sbt "sbt-alice-1"}
     "did:web:member.bob"
     {:member-did "did:web:member.bob"
      :consent-refs #{}                                        ; no active consent → G3 refuse
      :adherent-sbt "sbt-bob-1"}}
    :sessions {}
    :drafts {}
    :submissions {}
    :ledger []})

;; ───────────────────────── MemStore (default) ─────────────────────────

(defrecord MemStore [a]
  Store
  (procedure      [_ id] (get-in @a [:procedures id]))
  (all-procedures [_] (sort-by :procedure-id (vals (:procedures @a))))
  (member         [_ did] (get-in @a [:members did]))
  (session        [_ id] (get-in @a [:sessions id]))
  (sessions-of    [_ did] (->> (vals (get-in @a [:sessions] {}))
                               (filter #(= did (:member-did %)))
                               (sort-by :id)))
  (draft-of       [_ sid] (get-in @a [:drafts sid]))
  (submission-of  [_ sid] (get-in @a [:submissions sid]))
  (ledger         [_] (:ledger @a))
  (record-datom!  [s {:keys [kind id value]}]
    (case kind
      :procedure  (swap! a assoc-in [:procedures id] value)
      :member     (swap! a assoc-in [:members id] value)
      :session    (swap! a assoc-in [:sessions id] value)
      :draft      (swap! a assoc-in [:drafts id] value)
      :submission (swap! a assoc-in [:submissions id] value)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (seed! [s data] (swap! a merge (select-keys data
                                              [:procedures :members :sessions :drafts
                                               :submissions])) s))

(defn seed-db
  "Fresh MemStore seeded with demo-data."
  []
  (->MemStore (atom (demo-data))))

;; ───────────────────────── DatomicStore (langchain.db) ─────────────────────────

(def ^:private schema
  {:procedure/id {:db/unique :db.unique/identity}
   :member/id    {:db/unique :db.unique/identity}
   :session/id   {:db/unique :db.unique/identity}
   :ledger/seq   {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

;; The store talks to its backend ONLY through the langchain.db `:db-api` map
;; {:q :transact! :db :pull :entid}. langchain.db/api (in-process EAVT) and
;; langchain.kotoba-db/kotoba-api (kotoba-server XRPC, e.g. kotobase.net) both
;; implement it, so the same record runs on either by construction (ADR-0001).

(defn- q* [{:keys [api conn]} query & inputs]
  (apply (:q api) query ((:db api) conn) inputs))
(defn- pull* [{:keys [api conn]} pattern eid] ((:pull api) ((:db api) conn) pattern eid))
(defn- tx* [{:keys [api conn]} txd] ((:transact! api) conn txd))

(defrecord DatomicStore [api conn]
  Store
  (procedure [this id]
    (when-let [m (pull* this [:procedure/id :procedure/edn] [:procedure/id id])]
      (when (:procedure/id m) (dec* (:procedure/edn m)))))
  (all-procedures [this]
    (->> (q* this '[:find [?id ...] :where [?e :procedure/id ?id]])
         (map #(procedure this %)) (sort-by :procedure-id)))
  (member [this did]
    (when-let [m (pull* this [:member/id :member/edn] [:member/id did])]
      (when (:member/id m) (dec* (:member/edn m)))))
  (session [this id]
    (when-let [m (pull* this [:session/id :session/edn] [:session/id id])]
      (when (:session/id m) (dec* (:session/edn m)))))
  (sessions-of [this did]
    (->> (q* this '[:find [?v ...] :in $ ?did :where
                    [?e :session/id _] [?e :session/member ?did] [?e :session/edn ?v]] did)
         (mapv dec*) (sort-by :id)))
  (draft-of [this sid]
    (dec* (q* this '[:find ?p . :in $ ?sid :where
                     [?e :draft/session ?sid] [?e :draft/edn ?p]] sid)))
  (submission-of [this sid]
    (dec* (q* this '[:find ?p . :in $ ?sid :where
                     [?e :submission/session ?sid] [?e :submission/edn ?p]] sid)))
  (ledger [this]
    (->> (q* this '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]])
         (sort-by first) (mapv (comp dec* second))))
  (record-datom! [s {:keys [kind id value]}]
    (case kind
      :procedure  (tx* s [{:procedure/id id :procedure/edn (enc value)}])
      :member     (tx* s [{:member/id id :member/edn (enc value)}])
      :session    (tx* s [{:session/id id :session/member (:member-did value)
                           :session/edn (enc value)}])
      :draft      (tx* s [{:draft/session id :draft/edn (enc value)}])
      :submission (tx* s [{:submission/session id :submission/edn (enc value)}])
      nil)
    s)
  (append-ledger! [s fact]
    (tx* s [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}]) fact)
  (seed! [s data]
    (doseq [[id p] (:procedures data)] (record-datom! s {:kind :procedure  :id id :value p}))
    (doseq [[id m] (:members data)]    (record-datom! s {:kind :member     :id id :value m}))
    (doseq [[id ss] (:sessions data)]  (record-datom! s {:kind :session    :id id :value ss}))
    s))

(defn datomic-store
  "DatomicStore on the in-process langchain.db EAVT backend (default Datomic-shaped
  store; verifiable offline). For the kotoba-server pod (kotobase.net), bind the
  same record to langchain.kotoba-db/kotoba-api — same record, different :db-api
  (see ADR-2605312030 / docs)."
  ([] (datomic-store nil))
  ([data] (let [s (->DatomicStore d/api (d/create-conn schema))]
            (when data (seed! s data)) s)))

(defn datomic-seed-db [] (datomic-store (demo-data)))

;; ───────────────────────── ledger formatting ─────────────────────────

(defn ledger-line [{:keys [op procedure session disposition basis]}]
  (str/join " · " [(name (or disposition :record)) (str "op=" op)
                   (str "procedure=" procedure) (str "session=" session)
                   (str "basis=" (pr-str basis))]))

;; ───────────────────────── concierge ground facts ─────────────────────────

(defn open-session
  "Build a fresh concierge session ground fact (member + procedure + consent +
  mode). Pure; the caller records it via record-datom!."
  [session-id member-did procedure-id consent-ref mode]
  {:id session-id :member-did member-did :procedure procedure-id
   :consent-ref consent-ref :mode mode :phase "init"})

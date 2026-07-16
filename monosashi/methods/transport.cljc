(ns monosashi.methods.transport
  "monosashi 物差し — REAL atproto transport. ADR-2606271800.

  Turns a monosashi social post into an `app.bsky.feed.post` record and writes it to the etzhayyim
  PDS via `com.atproto.server.createSession` → `com.atproto.repo.createRecord`. This is the
  operator/member leg (no-server-key): the credential is supplied at call time (a member app-
  password or an access JWT); monosashi holds no key. With no credential it RETURNS a structured
  :blocked result naming exactly what is missing — it never fabricates a publish.

  Use as the `transport` fn of monosashi.methods.social/emit:
    (social/emit post (transport/pds-transport {:pds-base ... :identifier ... :app-password ...}))"
  (:require [clojure.string :as str]
            [babashka.http-client :as http]
            [cheshire.core :as json]))

(def default-pds "https://pds.etzhayyim.com")
(def feed-collection "app.bsky.feed.post")
(def post-max-graphemes 300)

(defn compact-text
  "Compact the (long, disclaimer-led) monosashi body into an ≤300-char app.bsky.feed.post text.
  Keeps the one-line skill distribution + the non-prediction/non-reward disclaimer tail."
  [post]
  (let [body  (get post ":post/body" "")
        line  (->> (str/split-lines body)
                   (remove str/blank?)
                   (some (fn [l] (when (str/includes? l "p10=") l))))
        text  (str "物差し｜予測アクター評価｜" (or line "") " #etzhayyim #monosashi")]
    (if (> (count text) post-max-graphemes)
      (str (subs text 0 (- post-max-graphemes 1)) "…")
      text)))

(defn to-feed-record
  "monosashi post → app.bsky.feed.post record. `created-at` is supplied (no wall clock)."
  [post created-at]
  {"$type" feed-collection
   "text" (compact-text post)
   "createdAt" created-at
   "langs" ["ja"]})

(defn create-session
  "com.atproto.server.createSession. Returns {:did :accessJwt :refreshJwt} or throws."
  [pds-base identifier app-password]
  (let [r (http/post (str pds-base "/xrpc/com.atproto.server.createSession")
                     {:headers {"content-type" "application/json"}
                      :body (json/generate-string {:identifier identifier :password app-password})
                      :throw false})
        b (json/parse-string (:body r) true)]
    (if (= 200 (:status r))
      {:did (:did b) :accessJwt (:accessJwt b) :refreshJwt (:refreshJwt b)}
      (throw (ex-info "createSession failed" {:status (:status r) :error (:error b) :message (:message b)})))))

(defn create-record!
  "com.atproto.repo.createRecord with a bearer access JWT. Returns {:uri :cid} or throws."
  [pds-base access-jwt repo record]
  (let [r (http/post (str pds-base "/xrpc/com.atproto.repo.createRecord")
                     {:headers {"content-type" "application/json"
                                "authorization" (str "Bearer " access-jwt)}
                      :body (json/generate-string {:repo repo :collection feed-collection :record record})
                      :throw false})
        b (json/parse-string (:body r) true)]
    (if (= 200 (:status r))
      {:uri (:uri b) :cid (:cid b)}
      (throw (ex-info "createRecord failed" {:status (:status r) :error (:error b) :message (:message b)})))))

(defn pds-transport
  "Build a transport fn (post -> relay-result) for social/emit. `cfg`:
     :pds-base (default https://pds.etzhayyim.com)
     :repo (the actor DID the record is written to, e.g. did:web:etzhayyim.com:actor:monosashi)
     :created-at (ISO-8601, required for a real write — deterministic, caller-supplied)
     and EITHER :access-jwt  OR  (:identifier + :app-password)   ← the member/operator credential.
  With no credential, returns a :blocked map naming the missing leg (never fabricates a write)."
  [{:keys [pds-base repo created-at access-jwt identifier app-password]
    :or {pds-base default-pds}}]
  (fn [post]
    (let [record (to-feed-record post created-at)]
      (cond
        (str/blank? (str repo))
        {:status :blocked :need :repo-did :note "supply :repo (the actor DID to write to)"}

        (str/blank? (str created-at))
        {:status :blocked :need :created-at :note "supply :created-at (deterministic, no wall clock)"}

        (some? access-jwt)
        (create-record! pds-base access-jwt repo record)

        (and (not (str/blank? (str identifier))) (not (str/blank? (str app-password))))
        (let [{:keys [did accessJwt]} (create-session pds-base identifier app-password)]
          (create-record! pds-base accessJwt (or repo did) record))

        :else
        {:status :blocked
         :need :member-credential
         :pds pds-base
         :repo repo
         :record record
         :note (str "no-server-key: supply a member :access-jwt, or :identifier + :app-password "
                    "(app-password from 1Password). monosashi holds no key. The PDS also requires "
                    "the actor account to exist (createAccount needs an operator invite code).")}))))

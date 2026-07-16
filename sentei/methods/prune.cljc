(ns sentei.methods.prune
  "剪定 (sentei) post-hoc pruning engine — 1:1 Clojure port of methods/prune.py
  (ADR-2606072000). The Council is the PRUNER, not the censor: a branch grows on
  the append-only Datom log first, and sentei prunes the overgrown/charter-
  violating ones AFTER they manifest.

  The constitutional model is STRUCTURAL — violations are unrepresentable:
    G1 no-prior-restraint — `prune` RAISES on an unmanifested branch.
    G2 append-only        — a prune APPENDS a datom; `apply-log` folds to state.
    G3 growth-unstoppable — prunes target a NAMED branch; no halt/delete action.
    G4 Transparent Force  — a prune carries no verdict/guilt value.
    G5 no-server-key      — server-held-key is always false; the server can't sign.
    G6 reversible         — every prune has the inverse `regraft`.
    G7 care-telos         — a prune MUST cite a Charter basis; no verdict token.

  Pure + deterministic (`at` is a parameter); event maps use string keys, mirroring
  the Python dicts. sha256 digest lives at the #?(:clj) edge."
  (:require [clojure.string :as str]))

;; D5 pruning vocabulary. delete / prior-restraint / halt-organism / verdict are ABSENT by design.
(def PRUNE-ACTIONS #{"quarantine" "retract" "rollback" "revoke"})
(def INVERSE-ACTION "regraft")
;; Tokens that would make a prune a punitive verdict — unrepresentable (G4/G7).
(def ^:private verdict-tokens ["guilt" "crime" "verdict" "punish" "違法" "有罪" "犯罪" "制裁" "処罰"])
;; Council levels: a contested / invariant-adjacent prune needs Lv7+; ordinary Lv6+.
(def COUNCIL-MIN 6)
(def COUNCIL-INVARIANT 7)

(defn- digest
  "sha256 of (join \"|\" parts), first 24 hex chars — parity with prune.py `_digest`."
  [& parts]
  #?(:clj
     (let [md (java.security.MessageDigest/getInstance "SHA-256")
           b  (.digest md (.getBytes ^String (str/join "|" parts) "UTF-8"))]
       (subs (apply str (map #(format "%02x" %) b)) 0 24))
     :cljs
     (throw (ex-info "sentei.digest: sha256 not yet wired for cljs (clj/bb edge only)" {}))))

(defn assert-no-verdict
  "G7: a prune is grooming, not punishment — refuse any guilt/verdict token in its basis."
  [basis]
  (let [low (str/lower-case (str basis))]
    (doseq [tok verdict-tokens]
      (when (str/includes? low tok)
        (throw (ex-info (str "G7 violation: verdict token '" tok "' in a prune basis (剪定 is care, not 制裁)")
                        {:token tok}))))))

(defn- server-did? [did]
  (or (= did "server") (str/starts-with? (str did) "did:web:etzhayyim.com#server")))

(defn prune
  "Prune a MANIFESTED branch. Returns an append-only prune event (a map; never deletes).

  Raises (structural invariants): G1 (unmanifested), G3 (unknown action),
  G5 (server signer), G7 (verdict token / missing basis), Council (level below floor),
  and a contested-vote that did not carry."
  [branch action basis signer-did
   {:keys [at council-level invariant-adjacent contested-vote]
    :or   {invariant-adjacent false}}]
  (when-not (get branch "manifested")
    (throw (ex-info (str "G1 no-prior-restraint: cannot prune an unmanifested branch — the organism's "
                         "growth is never pre-blocked; a branch must grow before it can be pruned") {})))
  (when-not (contains? PRUNE-ACTIONS action)
    (throw (ex-info (str "G3: '" action "' is not a prune action "
                         "(delete/halt/prior-restraint are unrepresentable)") {:action action})))
  (when (server-did? signer-did)
    (throw (ex-info "G5 no-server-key: a prune must be Council/member-signed; the server cannot sign" {})))
  (when (or (nil? basis) (str/blank? basis))
    (throw (ex-info "G7 care-telos: a prune MUST cite a Charter basis (why this branch is overgrown)" {})))
  (assert-no-verdict basis)
  (let [floor (if invariant-adjacent COUNCIL-INVARIANT COUNCIL-MIN)]
    (when (< council-level floor)
      (throw (ex-info (str "Transparent-Force: prune needs Council Lv" floor "+, got Lv" council-level) {})))
    (when contested-vote
      (let [yes (get contested-vote "yes" 0), no (get contested-vote "no" 0)]
        (when (<= yes no)
          (throw (ex-info (str "contested prune did not carry the vote (" yes " yes / " no " no)") {})))))
    (let [branch-id (get branch "id")
          prune-id  (str "prune:" branch-id ":" action ":" (digest branch-id action at))]
      (array-map
       "id" prune-id
       "kind" "prune"
       "branchId" branch-id
       "action" action
       "basis" basis
       "signerDid" signer-did
       "serverHeldKey" false
       "councilLevel" council-level
       "invariantAdjacent" invariant-adjacent
       "at" at
       "reversible" true
       "nonAdjudicating" true))))

(defn regraft
  "G6: the inverse of a prune — heal a mistaken cut (append-only, like ake revert)."
  [prune-datom signer-did {:keys [at basis] :or {basis "restoration"}}]
  (when-not (= (get prune-datom "kind") "prune")
    (throw (ex-info "regraft target must be a prune datom" {})))
  (assert-no-verdict basis)
  (when (server-did? signer-did)
    (throw (ex-info "G5 no-server-key: a regraft must be Council/member-signed" {})))
  (let [bid (get prune-datom "branchId")]
    (array-map
     "id" (str "regraft:" (get prune-datom "id") ":" (digest bid at))
     "kind" "regraft"
     "branchId" bid
     "prunesId" (get prune-datom "id")
     "basis" basis
     "signerDid" signer-did
     "serverHeldKey" false
     "at" at)))

(defn apply-log
  "Fold the append-only prune/regraft log into the CURRENT pruned-state of each branch:
  {branchId state} where state ∈ {\"live\" | <prune action>}. With `as-of` (ISO date)
  only events at/before that point are folded (非終末論 time-travel). A regraft restores
  the branch to \"live\"; the pruned event stays in history."
  ([events] (apply-log events nil))
  ([events as-of]
   (let [visible? (fn [ev]
                    (or (nil? as-of)
                        (<= (compare (str (get ev "at" "")) (str as-of "T23:59:59.999Z")) 0)))]
     (reduce
      (fn [state ev]
        (let [bid (get ev "branchId")]
          (cond
            (nil? bid) state
            (= (get ev "kind") "prune")   (assoc state bid (get ev "action"))
            (= (get ev "kind") "regraft") (assoc state bid "live")
            :else state)))
      {}
      (sort-by #(str (get % "at" "")) (filter visible? events))))))

(defn- json-val
  "Minimal json.dumps(v, ensure_ascii=False) for the scalar value types a prune/regraft
  event holds (string/bool/long/nil)."
  [v]
  (cond
    (nil? v)     "null"
    (true? v)    "true"
    (false? v)   "false"
    (string? v)  (str \" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \")
    :else        (str v)))

(defn to-datoms
  "Serialize a prune/regraft event to kotoba EAVT datoms (:sentei.prune/* | :sentei.regraft/*)."
  [event]
  (let [e  (get event "id")
        ns (if (= (get event "kind") "prune") ":sentei.prune/" ":sentei.regraft/")]
    (->> (dissoc event "id")
         (map (fn [[k v]] {"e" e "a" (str ns k) "v_edn" (json-val v) "added" true}))
         vec)))

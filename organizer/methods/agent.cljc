(ns organizer.methods.agent
  "organizer — kotoba-native auto-organize file commons. 1:1 port of py/agent.py. Content-addressed,
  vault-isolated items. Structural invariants: content-addressed dedup (G4 — itemId from the blake3
  of content), vault-isolation (G3 — an item belongs to exactly one vault; cross-vault read refused),
  no-mining (G2 — classification is owner-facing category/labels, no profile/ad field), no-server-key
  (G6 — only a member signature finalizes an upload). Classification is Murakumo-only when the rule
  layer is unsure (G7); the `from kotoba import datalog, llm` host binding is unused here, so the
  _murakumo_category fallback is the omitted leg (llm None → deterministic 'unknown')."
  (:require [clojure.string :as str]))

;; ── content addressing + dedup (G4) ───────────────────────────────────────────
(defn content-item-id
  "Content-addressed item id. Identical content → identical id → dedup (G4)."
  [blake3-hex]
  (str "cid." (subs blake3-hex 0 16)))

(defn ingest-item
  "Ingest an upload. If content with the same blake3 already exists IN THIS VAULT, return the
  existing item flagged deduped (G4). Otherwise stage a new, unsigned item (member finalizes via
  authorize-upload, G6). Blob is referenced as an encrypted envelope (G5)."
  [vault-did blake3-hex blob-ref filename content-type size-bytes posted-by existing-items]
  (if-let [dup (some (fn [it] (when (and (= (get it "vaultDid") vault-did)
                                         (= (get it "blake3") blake3-hex)) it))
                     existing-items)]
    {"state" "deduped" "item" dup "deduped" true}
    {"state" "staged" "deduped" false
     "item" {"itemId" (content-item-id blake3-hex)
             "vaultDid" vault-did "blake3" blake3-hex
             "blobRef" blob-ref                ; encrypted envelope ref (G5)
             "filename" filename "contentType" content-type
             "sizeBytes" (long size-bytes) "postedBy" posted-by
             "postedSig" nil}}))               ; G6: unsigned until member authorizes

(defn authorize-upload
  "Finalize a staged upload. ONLY a member-origin signature finalizes (G6 no-server-key)."
  [staged signature]
  (cond
    (not= (get staged "state") "staged")
    (merge staged {"refused" true "reason" "upload is not in :staged state"})
    (not= (get signature "origin") "member")
    (merge staged {"refused" true
                   "reason" "only a member passkey/wallet signature finalizes upload (G6 no-server-key)"})
    :else
    {"state" "stored" "item" (assoc (get staged "item") "postedSig" (get signature "ref"))}))

;; ── classification (G2 owner-only, G7 Murakumo fallback) ──────────────────────
(def ^:private TYPE-CATEGORY
  {"application/pdf" "document" "text/plain" "document"
   "image/jpeg" "image" "image/png" "image"
   "video/mp4" "media" "audio/mpeg" "media" "application/zip" "archive"})
(def ^:private EXT-CATEGORY
  {"pdf" "document" "txt" "document" "doc" "document" "docx" "document"
   "jpg" "image" "jpeg" "image" "png" "image" "heic" "image"
   "mp4" "media" "mov" "media" "mp3" "media"
   "zip" "archive" "tar" "archive" "gz" "archive"})

(defn classify
  "Classify an item for the OWNER's organization (G2). Rule layer first (content-type, then
  extension); Murakumo only when the rule layer is unsure (G7, omitted → 'unknown'). Returns
  category/labels/source scoped to the item's vault (G3) — NEVER a profile or ad signal."
  [item]
  (let [ct (str/lower-case (or (get item "contentType") ""))
        fname (get item "filename" "")
        ext (str/lower-case (let [i (str/last-index-of fname ".")] (if i (subs fname (inc i)) fname)))
        category (or (get TYPE-CATEGORY ct) (get EXT-CATEGORY ext) "unknown")  ; llm leg omitted
        fnl (str/lower-case fname)
        labels (cond-> [category]
                 (or (str/includes? fnl "receipt") (str/includes? fnl "invoice")) (conj "receipt"))]
    {"itemId" (get item "itemId")
     "vaultDid" (get item "vaultDid")          ; G3: classification stays in the item's vault
     "category" category "labels" labels
     "confidence" 1.0 "source" "rule"}))

;; ── auto-organize rules → collection ──────────────────────────────────────────
(defn apply-rules
  "Match the first organize-rule whose condition fits the classification and return its collection
  assignment. Returns nil if no rule fits (no forced bucketing)."
  [classification rules]
  (let [cat (get classification "category")
        labels (set (get classification "labels" []))]
    (some (fn [r]
            (let [cnd (get r "condition" {})]
              (when (and (or (not (contains? cnd "category")) (= (get cnd "category") cat))
                         (or (not (contains? cnd "label")) (contains? labels (get cnd "label"))))
                {"itemId" (get classification "itemId")
                 "vaultDid" (get classification "vaultDid")
                 "collection" (get r "collection")
                 "ruleMatched" (get r "id" "")})))
          (sort-by #(- (long (get % "priority" 0))) rules))))

;; ── vault isolation (G3) ──────────────────────────────────────────────────────
(defn read-item
  "Read an item only if the requester owns its vault (G3 own-data-only). Cross-vault read refused."
  [item requester-vault-did]
  (if (not= (get item "vaultDid") requester-vault-did)
    {"state" "refused" "reason" "cross-vault read refused — own-data-only (G3)"}
    {"state" "ok" "item" item}))

;; ── collection membership (vault-isolated, G3; idempotent) ────────────────────
(defn add-to-collection
  "Add an item to a collection. Refuses if item and collection are in different vaults (G3).
  Idempotent: adding a member twice does not duplicate it."
  [collection item]
  (if (not= (get collection "vaultDid") (get item "vaultDid"))
    {"state" "refused" "reason" "item and collection are in different vaults (G3)"}
    (let [members (vec (get collection "members" []))
          members (if (some #{(get item "itemId")} members) members (conj members (get item "itemId")))]
      {"state" "ok" "collection" (assoc collection "members" members)})))

(defn remove-from-collection
  "Remove an item from a collection (idempotent — removing a non-member is a no-op)."
  [collection item-id]
  {"state" "ok" "collection" (assoc collection "members"
                                    (filterv #(not= % item-id) (get collection "members" [])))})

(defn auto-organize
  "Batch auto-organize: classify each item (G2 owner-only), match the vault's organize rules, and
  assign it to the matching collection IN THE SAME VAULT (G3). Items with no matching rule are
  skipped (no forced bucketing)."
  [items collections]
  (let [by-vault (reduce (fn [m c] (update m (get c "vaultDid") (fnil conj []) c)) {} collections)]
    (vec (keep (fn [item]
                 (let [cls (classify item)]
                   (some (fn [c]
                           (apply-rules cls (mapv #(assoc % "collection" (get c "collectionId"))
                                                  (get c "autoRules" []))))
                         (get by-vault (get item "vaultDid") []))))
               items))))

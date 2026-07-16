(ns utsushie.methods.render-plan
  "utsushie 写し絵 — offline render-PLAN builder (R0, charter-gated).
  Clojure port of methods/render_plan.py (1:1). ADR-2606161536 §D2.

  Pure and offline: turns a kawaraban :article into a deterministic *plan* for a short
  narrated video. NEVER calls a model and NEVER renders — `render` raises at R0 because
  live render is G8-gated (Council Lv6+ + operator) and must run Murakumo-fleet only
  (U5 = G6, ADR-2605215000).

  The plan is bounded by the lexicon's structural gates (lex/video.edn, U1–U6):
    U1 (G1) no verdict        — no truth-rating; narration is attributive.
    U2 (G4) script ≤ excerpt  — narration is headline + the ≤280-char excerpt; never the body.
    U3 (G9) anti-deepfake     — no named-person photoreal likeness, no voice clone.
    U4 (G2) no engagement-edit — recency / 面-fit only.
    U5 (G6) Murakumo-only     — external-GPU render unrepresentable; render() R0-gated.
    U6 (G7) member-signed     — publish carries no server-held key.

  Pure stdlib (no deps)."
  (:require [clojure.string :as str]))

(def EXCERPT-MAX 280)                          ; = kawaraban G4 bound (lex/video.edn)
(def ALLOWED-KINDS ["mirror" "actor-event"])   ; G11: 'original' is not a member

(defn- refuse
  "Raise the charter-refusal equivalent of the Python CharterRefusal (a ValueError subclass)."
  [msg]
  (throw (ex-info msg {:charter-refusal true})))

(defn- s-get
  "article.get(k) or '' then .strip()."
  [article k]
  (str/trim (str (or (get article k) ""))))

(defn build-plan
  "Build a deterministic, charter-bounded video plan from a kawaraban :article. Raises a
  charter-refusal on any gate violation. Never renders."
  [article & {:keys [langs]}]
  (let [kind (get article "kind")]
    (when-not (some #(= % kind) ALLOWED-KINDS)
      (refuse (str "G11/U-kind: kind must be one of [\"mirror\" \"actor-event\"] (got "
                   (pr-str kind) "); utsushie narrates a mirror/actor-event, it is never "
                   "an :original source")))
    (let [headline (s-get article "headline")]
      (when (str/blank? headline)
        (refuse "missing headline — nothing to narrate"))
      (let [excerpt (s-get article "excerpt")]
        (when (> (count excerpt) EXCERPT-MAX)
          ;; U2 (= G4): a script longer than the bounded fair-use excerpt = full-text narration.
          (refuse (str "U2/G4: narration source exceeds the " EXCERPT-MAX "-char fair-use bound ("
                       (count excerpt) " chars) — the full body is never narrated")))
        ;; U3 (= G9) anti-deepfake: no real-person depiction / voice.
        (when (or (get article "depictsPerson") (get article "depicts_person"))
          (refuse "U3/G9 anti-deepfake: no photoreal likeness of a named real person"))
        (when (or (get article "voiceClone") (get article "voice_clone"))
          (refuse "U3/G9 anti-deepfake: no cloned voice of a named real person"))
        (when (and (= kind "mirror") (str/blank? (s-get article "url")))
          (refuse "G4: a 'mirror' video must carry a canonical link-out (url)"))
        (let [script (-> (if (str/blank? excerpt) headline (str headline "。" excerpt))
                         (#(subs % 0 (min EXCERPT-MAX (count %)))))
              langs* (if (seq langs) (vec langs) [(or (get article "lang") "ja")])]
          {"videoId" (str "utsushie-" (get article "articleId" "unknown"))
           "sourceArticleId" (get article "articleId")
           "kind" kind
           "section" (get article "section")
           "headline" headline
           "narrationScript" script        ; bounded; one script per target language (i18n D3)
           "langs" langs*
           "linkUrl" (get article "url")
           "outlet" (get article "outlet")
           "blobMime" "video/mp4"
           ;; gate witnesses — all const-false in lex/video.edn; echoed so callers can audit
           "gates" {"verdict" false
                    "fullTextNarration" false
                    "depictsPerson" false
                    "voiceClone" false
                    "engagementOptimized" false
                    "externalGpuRender" false
                    "serverHeldKey" false}
           "narrator" "synthetic-neutral"})))))   ; U3: never a named person's voice

(defn render
  "R0 guard — live video render is G8-gated and Murakumo-fleet only (U5 = G6)."
  [_plan]
  (throw (ex-info (str "utsushie.render is R0-gated: live video render requires Council Lv6+ "
                       "+ operator (G8) and must run Murakumo-fleet only (U5 = G6, "
                       "ADR-2605215000). Use build-plan for offline planning.")
                  {:r0-gated true})))

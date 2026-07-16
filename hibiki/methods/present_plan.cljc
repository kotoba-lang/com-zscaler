(ns hibiki.methods.present-plan
  "hibiki 響 — offline presentation-PLAN builder (R0, charter-gated).

  The ossekai-PROPOSAL sibling of utsushie 写し絵 (which does kawaraban :article → video).
  hibiki turns an ossekai 御節介 :proposal into a deterministic *plan* for a short narrated
  moving-image-with-SFX presentation whose 説得力 (persuasive power) is CLARITY + RESONANCE,
  never compliance-engineering.

  The constitutional novelty: a naive 'persuasion' actor is a dark-pattern factory and is
  charter-forbidden (ossekai is aggregate-first, anti-engagement, anti-addictive). hibiki is
  structurally pinned to the caring side of the same 御節介 knife-edge ossekai walks — it may
  make a proposal *land clearly and resonate on its merits*, but it may NOT manufacture
  urgency, optimize watch-time, or weaponize sound. The pin is dual: lex/presentation.edn
  (const-false fields) AND this builder (refuse on violation).

  Pure and offline: NEVER calls a model, NEVER renders. `render` raises at R0 because live
  render/TTS is G8-gated (Council Lv6+ + operator) and Murakumo-fleet only (H5 = G6,
  ADR-2605215000). Render + publish are CARRIED by ossekai (member-signed, aggregate-first,
  consent/mute honored), never by hibiki (H1 = G1 PROPOSE-not-act).

  Pure stdlib (no deps)."
  (:require [clojure.string :as str]))

(def EXCERPT-MAX 280)                                        ; H2 = G4 fair-use bound
(def ALLOWED-AUDIENCE #{"aggregate" "targeted"})            ; H7 aggregate-first (default = aggregate)
(def ALLOWED-MUSIC #{"calm" "neutral" "hopeful-sober"})    ; H8 — no ominous/euphoric/tense
(def ALLOWED-SFX-PURPOSE #{:scene-transition :emphasis :ambient-bed}) ; H4/H8 allowlist
(def FORBIDDEN-SFX-PURPOSE #{:fear :urgency :hype :startle :dopamine}) ; unrepresentable

(defn- refuse [msg] (throw (ex-info msg {:charter-refusal true})))

(defn- s-get [m k] (str/trim (str (or (get m k) ""))))

(defn- truthy? [m & ks] (some #(get m %) ks))

(defn- bound [s] (subs s 0 (min EXCERPT-MAX (count s))))

(defn build-plan
  "Build a deterministic, charter-bounded presentation plan from an ossekai :proposal.
  Raises a charter-refusal on any gate violation. Never renders, never publishes.

  proposal keys: \"proposalId\" \"finding\" \"why\" \"action\" \"severity\"
                 \"aggregate\"(bool, default true) \"linkUrl\" \"lang\""
  [proposal & {:keys [langs]}]
  ;; H1 (= G1): a proposal carrying a truth-verdict is not hibiki's to narrate.
  (when (truthy? proposal "verdict")
    (refuse "H1/G1: hibiki narrates 'ossekai proposes X because Y' — it never carries a truth-verdict"))
  ;; H3 (= G9) anti-deepfake.
  (when (truthy? proposal "depictsPerson" "depicts_person")
    (refuse "H3/G9 anti-deepfake: no photoreal likeness of a named real person"))
  (when (truthy? proposal "voiceClone" "voice_clone")
    (refuse "H3/G9 anti-deepfake: no cloned voice of a named real person; neutral synthetic narrator only"))
  ;; H4 (= G2) + H8 (§1.15): the 説得力 knife-edge — clarity/resonance, never manipulation.
  (when (truthy? proposal "engagementOptimized" "engagement_optimized")
    (refuse "H4/G2: no watch/dwell/conversion-optimized edit — 説得力 is clarity+resonance, not compliance-engineering"))
  (when (truthy? proposal "fakeUrgency" "fake_urgency")
    (refuse "H4/H8: manufactured urgency (countdown/scarcity/'act now') is unrepresentable — non-eschatological (§1.15)"))
  (when (truthy? proposal "manipulativeAudio" "manipulative_audio")
    (refuse "H4/H8: SFX/music may set tone but may not manufacture fear/euphoria — startle-stings unrepresentable"))
  ;; H5 (= G6): no external GPU / commercial TTS.
  (when (truthy? proposal "externalGpuRender" "external_gpu_render")
    (refuse "H5/G6: render/TTS is Murakumo-fleet only — external-GPU / commercial-TTS unrepresentable (ADR-2605215000)"))
  (let [finding (s-get proposal "finding")
        action  (s-get proposal "action")]
    (when (str/blank? finding) (refuse "missing finding — nothing to present"))
    (when (str/blank? action)  (refuse "missing action — an ossekai proposal must carry a proposed action"))
    (let [why     (s-get proposal "why")
          severity (let [s (s-get proposal "severity")] (if (str/blank? s) "warn" s))
          ;; H7 aggregate-first: aggregate is the DEFAULT; targeted requires explicit opt-out.
          aggregate? (if (contains? proposal "aggregate") (boolean (get proposal "aggregate")) true)
          audience (if aggregate? "aggregate" "targeted")
          _ (when-not (ALLOWED-AUDIENCE audience) (refuse (str "H7: audience must be aggregate|targeted (got " audience ")")))
          headline (bound (if (str/blank? why) finding (str finding "。" why)))
          ;; H2 (= G4): narration = bounded finding excerpt + the proposed action; nothing more.
          script   (bound (str finding "。" action))
          langs*   (if (seq langs) (vec langs) [(or (get proposal "lang") "ja")])
          ;; 動画 — deterministic storyboard; the LAST scene is ALWAYS the consent/opt-out card.
          storyboard [{"scene" "context"  "role" "neutral-title-card"   "text" headline}
                      {"scene" "finding"  "role" "what-ossekai-observed" "text" finding}
                      {"scene" "stakes"   "role" "why-it-matters"        "text" (if (str/blank? why) "(no stated why)" why)}
                      {"scene" "proposed" "role" "the-御節介-proposal"     "text" action}
                      {"scene" "consent"  "role" "opt-out-and-source"     "text" "あなたの選択です — 詳細・停止はこちら / This is your choice — details & opt-out"}]
          ;; 効果音 — allowlisted purposes only; loudness-normalized; no manipulation.
          sfx [{"purpose" "scene-transition" "loudnessLufs" -23}
               {"purpose" "emphasis" "loudnessLufs" -23 "at" "proposed"}
               {"purpose" "ambient-bed" "loudnessLufs" -30}]
          ;; severity tints the music mood but NEVER unlocks fear/urgency (sober emphasis only).
          music (case severity
                  ("critical" "structural") "hopeful-sober"
                  "calm")]
      ;; defence-in-depth: assert the SFX allowlist on the way out.
      (doseq [c sfx]
        (let [p (keyword (get c "purpose"))]
          (when (FORBIDDEN-SFX-PURPOSE p)
            (refuse (str "H4/H8: SFX purpose " p " is unrepresentable")))
          (when-not (ALLOWED-SFX-PURPOSE p)
            (refuse (str "H4/H8: SFX purpose " p " is not on the allowlist")))))
      (when-not (ALLOWED-MUSIC music) (refuse (str "H8: music mood " music " not allowed")))
      {"presentationId"  (str "hibiki-" (get proposal "proposalId" "unknown"))
       "sourceProposalId" (get proposal "proposalId")
       "audience"        audience
       "headline"        headline
       "narrationScript" script
       "storyboard"      storyboard
       "sfxCues"         sfx
       "musicBed"        music
       "narrator"        "synthetic-neutral"          ; H3
       "langs"           langs*
       "linkUrl"         (get proposal "linkUrl")
       "blobMime"        "video/mp4"
       ;; gate witnesses — all const-false in lex/presentation.edn; echoed for caller audit.
       "gates" {"verdict" false "fullProposalNarration" false
                "depictsPerson" false "voiceClone" false
                "engagementOptimized" false "fakeUrgency" false "manipulativeAudio" false
                "externalGpuRender" false "serverHeldKey" false}})))

(defn render
  "R0 guard — live render/TTS is G8-gated and Murakumo-fleet only (H5 = G6). Reuses utsushie's
  Murakumo render leg at R1; publish is carried by ossekai (member-signed, aggregate-first)."
  [_plan]
  (throw (ex-info (str "hibiki.render is R0-gated: live render/TTS requires Council Lv6+ + operator "
                       "(G8) and must run Murakumo-fleet only (H5 = G6, ADR-2605215000). Publish is "
                       "carried by ossekai (member-signed, H6 = G7). Use build-plan for offline planning.")
                  {:r0-gated true})))

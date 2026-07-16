#!/usr/bin/env bb
;; tsuchifumi 土踏み — self-contained visualization generator (clj-native, pure stdlib).
(ns tsuchifumi.methods.viz
  "viz.cljc — tsuchifumi 土踏み visualization generator (ADR-2606212000).

  Renders a SELF-CONTAINED HTML (vanilla canvas, no deps, no network) from the REAL
  method output (none of the data is hand-copied — it is computed by sysdyn/analyze/
  risk at generation time). Two panels:

    1. System-dynamics scenarios — the burden-stock trajectory for :neglect /
       :baseline / :relief with p10–p90 uncertainty BANDS (distribution-only, G6).
       Visually shows the relief dividend: institutionalizing earthing/greenspace
       access bends the (hypothesized) burden curve down, under disclosed parameters.
    2. Risk leverage points — Meadows leverage-priority bars + the per-region
       relief-gap, the no-regret civic worklist.

  The HTML carries the same epistemic-honesty banner as every other surface (G2/G6):
  the burden is a HYPOTHESIZED model variable, not an asserted clinical quantity.
  Pattern sibling of tatara's self-contained canvas globes."
  (:require [clojure.string :as str]
            [tsuchifumi.methods.sysdyn :as sd]
            [tsuchifumi.methods.analyze :as an]
            [tsuchifumi.methods.risk :as risk]
            [tsuchifumi.methods.tsuchifumi-edn :as te]))

;; ── tiny JSON encoder (maps with string keys, numbers, strings, vectors, kw) ──
(defn ->json [v]
  (cond
    (nil? v) "null"
    (boolean? v) (if v "true" "false")
    (keyword? v) (->json (name v))
    (integer? v) (str v)
    (number? v) (str (double v))
    (string? v) (str \" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")
                            (str/replace "\n" "\\n")) \")
    (map? v) (str "{" (str/join "," (map (fn [[k val]] (str (->json (if (keyword? k) (name k) (str k)))
                                                            ":" (->json val))) v)) "}")
    (sequential? v) (str "[" (str/join "," (map ->json v)) "]")
    :else (->json (str v))))

(defn build-data
  "Compute the real visualization payload from the seed-derived inputs."
  [regions evidence drivers]
  (let [scen (sd/run-scenarios)
        ssum (sd/summary scen)
        assessment (an/assess regions evidence)
        risk-a (risk/assess drivers)
        scen->bands (fn [k] (get-in scen [k "result" "bands"]))]
    {"scenarios" {"neglect"  {"label" "neglect"  "bands" (scen->bands :neglect)}
                  "baseline" {"label" "baseline" "bands" (scen->bands :baseline)}
                  "relief"   {"label" "relief"   "bands" (scen->bands :relief)}}
     "summary" ssum
     "leverage" (mapv (fn [r] {"name" (get r "name")
                               "priority" (get r "leverage_priority")
                               "band" (str (get r "leverage_band"))
                               "level" (get r "meadows_level")})
                      (get risk-a "leverage_points"))
     "relief_gap" (get assessment "relief_gap")
     "tally" (into {} (map (fn [[k v]] [(name k) v]) (get assessment "tally")))}))

(declare slurp-js)

(defn render-html [data]
  (str "<!doctype html>\n<html lang=\"ja\"><head><meta charset=\"utf-8\">\n"
   "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
   "<title>tsuchifumi 土踏み — earthing-EMF system dynamics + risk</title>\n"
   "<style>\n"
   "  body{font-family:system-ui,'Hiragino Sans',sans-serif;margin:0;background:#0c1116;color:#e6edf3}\n"
   "  header{padding:18px 22px;border-bottom:1px solid #21262d}\n"
   "  h1{font-size:18px;margin:0 0 4px} .sub{color:#8b949e;font-size:13px;line-height:1.5}\n"
   "  .banner{background:#1c2128;border:1px solid #30363d;border-left:3px solid #d29922;"
   "padding:10px 14px;margin:14px 22px;border-radius:6px;font-size:12.5px;color:#d6cfa6}\n"
   "  .grid{display:grid;grid-template-columns:1fr;gap:18px;padding:8px 22px 28px}\n"
   "  .card{background:#11161c;border:1px solid #21262d;border-radius:8px;padding:14px}\n"
   "  .card h2{font-size:14px;margin:0 0 10px;color:#c9d1d9}\n"
   "  canvas{width:100%;height:auto;display:block}\n"
   "  .legend{display:flex;gap:16px;flex-wrap:wrap;font-size:12px;margin-top:8px;color:#8b949e}\n"
   "  .legend i{display:inline-block;width:12px;height:12px;border-radius:2px;margin-right:5px;vertical-align:-1px}\n"
   "  footer{color:#6e7681;font-size:11.5px;padding:0 22px 24px;line-height:1.5}\n"
   "</style></head><body>\n"
   "<header><h1>tsuchifumi 土踏み — earthing under-institutionalization × ambient-EMF</h1>\n"
   "<div class=\"sub\">system-dynamics scenarios (distribution-only) + Meadows leverage points + institutional relief-gap. "
   "ADR-2606212000 · all data :synthetic, computed at generation time.</div></header>\n"
   "<div class=\"banner\">⚠ <b>Epistemic honesty (G2/G6):</b> the &quot;burden&quot; is a <b>HYPOTHESIZED model variable</b>, "
   "not an asserted clinical quantity. Non-thermal EMF harm and earthing-therapy benefit are scientifically <b>未確立 (contested)</b>. "
   "What IS established — and what this actor acts on — is the no-regret value of greenspace / outdoor time / soil contact. "
   "OBSERVATORY + MODEL + NUDGE only: non-diagnostic, non-therapeutic, sells nothing.</div>\n"
   "<div class=\"grid\">\n"
   "  <div class=\"card\"><h2>1. System dynamics — bioelectric burden (hypothesized), 30y, p10–p90 bands</h2>\n"
   "    <canvas id=\"sd\" width=\"900\" height=\"380\"></canvas>\n"
   "    <div class=\"legend\"><span><i style=\"background:#f85149\"></i>neglect</span>"
   "<span><i style=\"background:#d29922\"></i>baseline</span>"
   "<span><i style=\"background:#3fb950\"></i>relief (institutionalize access)</span></div></div>\n"
   "  <div class=\"card\"><h2>2. Meadows leverage points — where to intervene first (no-regret)</h2>\n"
   "    <canvas id=\"lv\" width=\"900\" height=\"300\"></canvas></div>\n"
   "  <div class=\"card\"><h2>3. Institutional relief-gap by region (population-weighted)</h2>\n"
   "    <canvas id=\"rg\" width=\"900\" height=\"300\"></canvas></div>\n"
   "</div>\n"
   "<footer>verdict tally: " (->json (get data "tally")) " · relief dividend (neglect−relief, p50) = "
   (get-in data ["summary" "relief_dividend_p50"]) " · "
   "Routed to ossekai (御節介) as transparent, consent-bound, no-fear nudges — never tsuchifumi acting on a person.</footer>\n"
   "<script>\nconst D=" (->json data) ";\n"
   (slurp-js)
   "</script>\n</body></html>\n"))

;; the canvas-drawing JS (kept separate for readability)
(defn slurp-js []
  (str
   "function ctx(id){const c=document.getElementById(id);const x=c.getContext('2d');"
   "x.clearRect(0,0,c.width,c.height);return [c,x];}\n"
   "// panel 1: system dynamics bands\n"
   "(function(){const [c,x]=ctx('sd');const W=c.width,H=c.height,pad=46;\n"
   " const scs=[['neglect','#f85149'],['baseline','#d29922'],['relief','#3fb950']];\n"
   " let maxB=0;scs.forEach(([k])=>{D.scenarios[k].bands.forEach(b=>{if(b.p90>maxB)maxB=b.p90;});});\n"
   " maxB=Math.max(maxB,0.5);const n=D.scenarios.neglect.bands.length;\n"
   " const X=t=>pad+(W-pad-12)*(t/(n-1));const Y=v=>H-pad-(H-pad-16)*(v/maxB);\n"
   " x.strokeStyle='#30363d';x.fillStyle='#8b949e';x.font='11px system-ui';x.lineWidth=1;\n"
   " for(let g=0;g<=4;g++){const v=maxB*g/4,y=Y(v);x.beginPath();x.moveTo(pad,y);x.lineTo(W-12,y);x.stroke();"
   "x.fillText(v.toFixed(2),6,y+3);}\n"
   " x.fillText('year →',W-60,H-pad+24);x.fillText('burden(hyp)',6,16);\n"
   " for(let t=0;t<n;t+=5){x.fillText(t,X(t)-4,H-pad+18);}\n"
   " scs.forEach(([k,col])=>{const B=D.scenarios[k].bands;\n"
   "  x.beginPath();B.forEach((b,t)=>{const px=X(t),py=Y(b.p90);t?x.lineTo(px,py):x.moveTo(px,py);});\n"
   "  for(let t=n-1;t>=0;t--){x.lineTo(X(t),Y(B[t].p10));}x.closePath();\n"
   "  x.fillStyle=col+'22';x.fill();\n"
   "  x.beginPath();B.forEach((b,t)=>{const px=X(t),py=Y(b.p50);t?x.lineTo(px,py):x.moveTo(px,py);});\n"
   "  x.strokeStyle=col;x.lineWidth=2;x.stroke();});\n"
   "})();\n"
   "// panel 2: leverage bars\n"
   "(function(){const [c,x]=ctx('lv');const W=c.width,H=c.height,pad=210;\n"
   " const L=D.leverage;const bh=(H-30)/L.length;let mx=0;L.forEach(r=>{if(r.priority>mx)mx=r.priority;});mx=Math.max(mx,0.1);\n"
   " const band={'paradigm-goal':'#a371f7','structure-rules':'#3fb950','feedback':'#d29922','parameter':'#8b949e'};\n"
   " x.font='12px system-ui';\n"
   " L.forEach((r,i)=>{const y=12+i*bh;const w=(W-pad-20)*(r.priority/mx);\n"
   "  x.fillStyle=band[r.band]||'#58a6ff';x.fillRect(pad,y,w,bh-8);\n"
   "  x.fillStyle='#e6edf3';x.fillText(r.name.slice(0,22),6,y+bh*0.5);\n"
   "  x.fillStyle='#8b949e';x.fillText('L'+r.level+' '+r.priority.toFixed(3),pad+w+6,y+bh*0.5);});\n"
   "})();\n"
   "// panel 3: relief gap\n"
   "(function(){const [c,x]=ctx('rg');const W=c.width,H=c.height,pad=210;\n"
   " const G=D.relief_gap;const bh=(H-30)/G.length;let mx=0;G.forEach(r=>{if(r.gap>mx)mx=r.gap;});mx=Math.max(mx,0.1);\n"
   " x.font='12px system-ui';\n"
   " G.forEach((r,i)=>{const y=12+i*bh;const w=(W-pad-20)*(r.gap/mx);\n"
   "  x.fillStyle='#2f81f7';x.fillRect(pad,y,w,bh-8);\n"
   "  x.fillStyle='#e6edf3';x.fillText((r.name||'').slice(0,22),6,y+bh*0.5);\n"
   "  x.fillStyle='#8b949e';x.fillText('gap '+r.gap.toFixed(3)+' (deficit '+r.earthing_deficit.toFixed(2)+')',pad+w+6,y+bh*0.5);});\n"
   "})();\n"))

;; ── CLI (bb) — write the HTML from the real seed-derived data ────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tsuchifumi/kotoba/seed.edn")
           out  (or (second args) "20-actors/tsuchifumi/viz/sysdyn-risk.html")
           rows (te/reconstitute-rows (clojure.edn/read-string (slurp seed)))
           regions (vec (filter #(= (:type %) :region) rows))
           evidence (vec (filter #(= (:type %) :evidence) rows))
           drivers (vec (filter #(= (:type %) :driver) rows))
           data (build-data regions evidence drivers)
           html (render-html data)]
       (clojure.java.io/make-parents out)
       (spit out html)
       (println (str "wrote " out " (" (count html) " bytes); scenarios="
                     (keys (get data "scenarios")) " leverage=" (count (get data "leverage"))
                     " relief-gap=" (count (get data "relief_gap")))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))

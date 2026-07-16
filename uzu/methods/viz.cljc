#!/usr/bin/env bb
;; uzu 渦 — visualization: render the measured energy field + organism life to one canvas.
(ns uzu.methods.viz
  "viz.cljc — uzu 渦 energy-field visualization generator (ADR-2606211500).

  Generates a SELF-CONTAINED HTML canvas from the measured field (measure.cljc) and the
  lived organisms (metabolism.cljc) — nothing is hand-copied; the markup embeds the data
  as JSON and draws it. The view makes the design legible:
    • four lanes, one per unit class, each flow a bar sized by log magnitude and labelled
      with its NATIVE unit — the lanes are kept VISUALLY SEPARATE because the units are
      incommensurable (the honest 'do not equate information and energy' boundary);
    • circulation arrows between flows = the open dissipative loop (energy→economy→
      information→meaning→behaviour→energy); cross-class couplings are dashed;
    • per-class totals (never cross-summed) + physical dissipation (waste heat);
    • organism energy trajectories = the abstract agent living inside the measured field,
      surviving or dying by the fit between its meaning (C) and the world.

  Pure string generation; :clj does the file write. No network, no-server-key."
  (:require [uzu.methods.measure :as measure]
            [uzu.methods.metabolism :as metab]
            [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

;; ── minimal JSON encoder (self-contained) ─────────────────────────────────────
(defn- jesc [s]
  (str/escape (str s) {\" "\\\"" \\ "\\\\" \newline "\\n" \return "\\r" \tab "\\t"}))

(defn json [v]
  (cond
    (nil? v) "null"
    (boolean? v) (if v "true" "false")
    (keyword? v) (str \" (jesc (subs (str v) 1)) \")
    (integer? v) (str v)
    (number? v) (str (double v))
    (string? v) (str \" (jesc v) \")
    (map? v) (str "{" (str/join "," (map (fn [[k val]]
                                            ;; munge hyphen→underscore so JS dot-access works
                                            (str (json (str/replace (name k) "-" "_")) ":" (json val))) v)) "}")
    (sequential? v) (str "[" (str/join "," (map json v)) "]")
    :else (str \" (jesc v) \")))

;; ── payload assembly ──────────────────────────────────────────────────────────
(defn life->series
  "Energy trajectory for one lived organism: born-energy followed by per-beat energy."
  [s]
  {:id (:id s)
   :alive (:alive? s)
   :prefs (:prefs s)
   :series (vec (cons (/ (Math/round (* 1000.0 (:born-energy s))) 1000.0)
                      (map :energy (:history s))))
   :death (let [i (count (take-while :alive? (:history s)))]
            (if (:alive? s) -1 i))})

(defn payload
  "Build the JSON-able view payload from lived organisms + measured flows/edges."
  [lives flows edges]
  (let [fld (measure/field {:flows flows :edges edges})]
    {:flows (:visual fld)
     :edges (mapv #(select-keys % [:from :to :coupling :cross-class]) edges)
     :totals (:totals fld)
     :dissipation (:dissipation fld)
     :closed (:closed? fld)
     :classes (vec (keys measure/unit-classes))
     :units measure/unit-classes
     :lives (mapv life->series lives)}))

;; ── HTML (canvas + data-driven renderer) ──────────────────────────────────────
(defn html [pl]
  (str "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">
<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
<title>uzu 渦 — energy field</title>
<style>
 :root{--bg:#0a0e14;--fg:#cdd6e4;--dim:#5b6878;--phys:#ff9f43;--econ:#26de81;--info:#4b9fff;--exp:#c56cf0;--dead:#ff5e5e}
 html,body{margin:0;background:var(--bg);color:var(--fg);font:13px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif}
 #wrap{max-width:1240px;margin:0 auto;padding:18px}
 h1{font-size:18px;margin:0 0 2px} .sub{color:var(--dim);font-size:12px;margin:0 0 12px}
 canvas{width:100%;height:auto;background:#070a0f;border:1px solid #18202c;border-radius:8px}
 .legend{display:flex;gap:16px;flex-wrap:wrap;margin:10px 0;color:var(--dim)}
 .legend b{font-weight:600}
 .note{color:var(--dim);font-size:12px;margin-top:8px;border-left:2px solid #18202c;padding-left:10px}
 code{color:#8fb7ff}
</style></head><body><div id=\"wrap\">
<h1>uzu 渦 — measured energy field of an open, coupled, dissipative system</h1>
<p class=\"sub\">Four flow classes in four incommensurable units — <b>never summed across classes</b>. Arrows = circulation; an organism lives inside the field, surviving by the fit of its <i>meaning</i> to the world.</p>
<canvas id=\"c\" width=\"1200\" height=\"800\"></canvas>
<div class=\"legend\">
 <span><b style=\"color:var(--phys)\">■</b> physical (W)</span>
 <span><b style=\"color:var(--econ)\">■</b> economic (USD/yr)</span>
 <span><b style=\"color:var(--info)\">■</b> informational (bit/s)</span>
 <span><b style=\"color:var(--exp)\">■</b> experiential (index)</span>
 <span>— solid = within-class · - - dashed = cross-class coupling</span>
</div>
<p class=\"note\">Cross-class bar lengths use a <b>reference-only</b> log conversion (energy intensity of money, J/bit) for visual layout — <b>not</b> a claim that the units are identical. The <code>experiential</code> lane has <b>no</b> physical conversion by design: meaning is subject-dependent and converting it to joules is the philosophy soup. ADR-2606211500.</p>
<script>
const D = " (json pl) ";
const C = document.getElementById('c'), X = C.getContext('2d');
const COL = {physical:'#ff9f43',economic:'#26de81',informational:'#4b9fff',experiential:'#c56cf0'};
const W=1200,H=800, FY0=70, FY1=520;          // field band
const lanes = ['physical','economic','informational','experiential'];
const laneX = c => 150 + lanes.indexOf(c)*((W-300)/ (lanes.length-1));
// place each flow as a node, stacked within its lane
const byClass = {}; lanes.forEach(c=>byClass[c]=[]);
D.flows.forEach(f=>byClass[f.class]&&byClass[f.class].push(f));
const pos = {};
lanes.forEach(c=>{const fs=byClass[c]; fs.forEach((f,i)=>{
  const y = FY0+40 + i*((FY1-FY0-60)/Math.max(1,fs.length)); pos[f.id]={x:laneX(c),y,f}; });});
// magnitude → bar half-length (log)
function barLen(f){const v=f.visual||{};
  if(v.axis==='experiential') return 30+ (v.index||0)*70;
  const l=v.log10_W; return l==null?20: Math.max(8,(l+10)*9);}   // shift so negatives show
function draw(){
  X.clearRect(0,0,W,H); X.lineWidth=1;
  // lane headers
  X.textAlign='center'; X.font='12px sans-serif';
  lanes.forEach(c=>{X.fillStyle=COL[c]; X.fillText(c+'  ('+ (D.units[c]?D.units[c].unit:'') +')', laneX(c), FY0); });
  // circulation edges
  D.edges.forEach(e=>{const a=pos[e.from],b=pos[e.to]; if(!a||!b)return;
    X.strokeStyle = e.cross_class? 'rgba(140,160,190,.40)':'rgba(90,104,120,.55)';
    X.setLineDash(e.cross_class?[5,4]:[]);
    X.beginPath(); X.moveTo(a.x,a.y);
    const mx=(a.x+b.x)/2, my=(a.y+b.y)/2 - 30;
    X.quadraticCurveTo(mx,my,b.x,b.y); X.stroke();
    // arrowhead
    const ang=Math.atan2(b.y-my,b.x-mx); X.setLineDash([]);
    X.beginPath(); X.moveTo(b.x,b.y);
    X.lineTo(b.x-8*Math.cos(ang-.4), b.y-8*Math.sin(ang-.4));
    X.lineTo(b.x-8*Math.cos(ang+.4), b.y-8*Math.sin(ang+.4));
    X.closePath(); X.fillStyle='rgba(140,160,190,.5)'; X.fill(); });
  X.setLineDash([]);
  // flow bars + labels
  D.flows.forEach(f=>{const p=pos[f.id], L=barLen(f);
    X.fillStyle=COL[f.class]; X.globalAlpha=.85;
    X.fillRect(p.x-L/2, p.y-7, L, 14); X.globalAlpha=1;
    X.fillStyle='#cdd6e4'; X.textAlign='center'; X.font='11px sans-serif';
    X.fillText(f.label, p.x, p.y-12);
    X.fillStyle='#5b6878'; X.font='10px monospace';
    X.fillText(f.magnitude.toExponential(2)+' '+(f.unit||''), p.x, p.y+22);
    if(f.visual&&f.visual.reference_only){X.fillStyle='#3a4658';X.fillText('(ref-only)',p.x,p.y+33);} });
  // per-class totals panel
  X.textAlign='left'; let ty=FY0;
  X.fillStyle='#8fb7ff'; X.font='12px sans-serif'; X.fillText('per-class totals (never cross-summed):', 20, ty); ty+=18;
  X.font='11px monospace';
  for(const c in D.totals){const t=D.totals[c]; X.fillStyle=COL[c];
    X.fillText(c+'  Σ='+t.total.toExponential(2)+' '+t.unit, 20, ty); ty+=15;}
  // organism trajectories
  const TY0=560, TY1=770, TX0=80, TX1=W-40;
  X.strokeStyle='#18202c'; X.beginPath(); X.moveTo(TX0,TY1); X.lineTo(TX1,TY1); X.moveTo(TX0,TY0); X.lineTo(TX0,TY1); X.stroke();
  X.fillStyle='#8fb7ff'; X.font='12px sans-serif'; X.textAlign='left';
  X.fillText('organism energy trajectory (conserved ledger) — survival = fit of meaning C to world', TX0, TY0-8);
  let emax=1; D.lives.forEach(l=>l.series.forEach(v=>emax=Math.max(emax,v)));
  const oc=['#26de81','#ff5e5e','#c56cf0','#4b9fff'];
  D.lives.forEach((l,k)=>{const n=l.series.length;
    X.strokeStyle=oc[k%oc.length]; X.lineWidth=2; X.beginPath();
    l.series.forEach((v,i)=>{const x=TX0+(TX1-TX0)*i/Math.max(1,n-1);
      const y=TY1-(TY1-TY0)*Math.max(0,v)/emax; i?X.lineTo(x,y):X.moveTo(x,y);});
    X.stroke(); X.lineWidth=1;
    // label + death marker
    const lx=TX1-150, ly=TY0+14+k*16; X.fillStyle=oc[k%oc.length];
    X.fillText(l.id+'  '+(l.alive?'alive':'DIED@'+l.death)+'  C{n:'+l.prefs.nutrient+',t:'+l.prefs.threat+'}', lx, ly);
    if(l.death>=0){const x=TX0+(TX1-TX0)*l.death/Math.max(1,n-1);
      X.fillStyle='#ff5e5e'; X.beginPath(); X.arc(x,TY1-2,4,0,7); X.fill();}});
  // zero line
  X.strokeStyle='#2a3340'; X.setLineDash([3,3]); X.beginPath(); X.moveTo(TX0,TY1); X.lineTo(TX1,TY1); X.stroke(); X.setLineDash([]);
}
draw();
</script></div></body></html>"))

#?(:clj
   (defn render-file!
     "Render the energy-field HTML to `out-path` from lived organisms + flows/edges."
     [lives flows edges out-path]
     (let [f (io/file out-path)]
       (when-let [p (.getParentFile f)] (.mkdirs p))
       (spit f (html (payload lives flows edges)))
       out-path)))

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/uzu/kotoba/seed.edn")
           out-path (or (second args) "20-actors/uzu/out/energy-field.html")
           rows (edn/read-string (slurp seed-path))
           {:keys [tape organisms flows edges]}
           {:tape (->> rows (filter #(= (:type %) :world-step)) (sort-by :step) vec)
            :organisms (vec (filter #(= (:type %) :organism) rows))
            :flows (vec (filter #(= (:type %) :flow) rows))
            :edges (vec (filter #(= (:type %) :circulation) rows))}
           lives (mapv #(metab/live % tape) organisms)
           out (render-file! lives flows edges out-path)]
       (println (str "wrote " out " (" (count flows) " flows, " (count edges) " edges, "
                     (count lives) " organisms)")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))

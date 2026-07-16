(ns tatara.viz.build-viz
  "tatara 鑪 — viz data builder (ADR-2606171800). Reads the tatara plant graph (and the
  watari craft graph for the integrated overlay), computes the aggregate analysis, and emits
  three SELF-CONTAINED canvas-globe HTML files — every coordinate DERIVED from a seed, none
  hand-copied (honest, regenerable):

    tatara/viz/plant-globe.htm          — (C) world manufacturing plants, by sector
    tatara/viz/world-supply-globe.htm   — (A) plants + live craft + chokepoint composition
    watari/viz/craft-globe.htm          — (B) live moving-craft positions (watari had NO viz)

  Each globe is an orthographic canvas sphere (drag to rotate / auto-spin / click a point),
  data inlined as a JSON constant. Aggregate-first RESILIENCE map, NEVER a target-list (G2).

  Run:  bb -cp 20-actors -e \"(require 'tatara.viz.build-viz)(tatara.viz.build-viz/-main)\""
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            #?(:clj [clojure.edn :as edn])
            [tatara.methods.analyze :as az]
            [tatara.methods.compose :as compose]))

;; ── tiny JSON encoder (deterministic; numbers/strings/bools/maps/vecs/keywords) ──
(defn- jstr [s] (str "\"" (-> (str s)
                              (str/replace "\\" "\\\\")
                              (str/replace "\"" "\\\"")
                              (str/replace "\n" "\\n")) "\""))
(defn- json [v]
  (cond
    (nil? v)     "null"
    (string? v)  (jstr v)
    (keyword? v) (jstr (name v))
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (number? v)  (str (double v))
    (map? v)     (str "{" (str/join "," (map (fn [[k vv]] (str (jstr (name k)) ":" (json vv)))
                                             v)) "}")
    (sequential? v) (str "[" (str/join "," (map json v)) "]")
    :else (jstr (str v))))

;; ── sector / kind / chokepoint palettes ─────────────────────────────────────────
(def sector-colors
  {:semiconductor "#62d0ff" :automotive "#ffd166" :battery "#7ee787" :steel "#c792ea"
   :chemicals "#f78c6b" :electronics "#ff6b6b" :aerospace "#4dd0e1" :shipbuilding "#f48fb1"
   :pharma "#a3e635"})
(def choke-colors
  {"malacca" "#ffd166" "luzon-strait" "#ff6b6b" "suez-red-sea" "#f78c6b"
   "gibraltar" "#7ee787" "panama" "#62d0ff" "hormuz" "#ff9f1c" "taiwan-strait" "#f48fb1"})

;; ── seed → viz primitives ───────────────────────────────────────────────────────
(defn- plant-points [plants]
  (mapv (fn [p]
          {:lat (:plant/lat p) :lon (:plant/lon p)
           :col (get sector-colors (:plant/sector p) "#9ad")
           :w (:plant/headcount-est p)
           :nm (:plant/name p) :cc (:plant/country p)
           :kind "plant"
           :info [(str "operator: " (:plant/operator p))
                  (str "sector: " (name (:plant/sector p)))
                  (str "employment (aggregate): " (:plant/headcount-est p))
                  (str "capacity: " (:plant/capacity-value p) " " (name (:plant/capacity-unit p)))
                  (:plant/capacity-note p)]})
        plants))

(defn- hub-points [hubs]
  (mapv (fn [h]
          {:lat (:hub/lat h) :lon (:hub/lon h) :col "#8fa9c8" :w 0
           :nm (:hub/name h) :cc (:hub/country h) :kind "hub"
           :info [(str "kind: " (name (:hub/kind h)))
                  (str "throughput: " (:hub/throughput-value h) " " (name (:hub/throughput-unit h)))]})
        hubs))

(defn- flow-arcs [plant-by-id hub-by-id flows]
  (vec (keep (fn [f]
               (let [a (plant-by-id (:flow/from f))
                     b (or (hub-by-id (:flow/to f)) (plant-by-id (:flow/to f)))
                     cps (:flow/via f)]
                 (when (and a b)
                   {:from [(:plant/lat a) (:plant/lon a)]
                    :to [(or (:hub/lat b) (:plant/lat b)) (or (:hub/lon b) (:plant/lon b))]
                    :col (if (seq cps) (get choke-colors (name (first cps)) "#5a708f") "#3b4d6b")})))
             flows)))

(defn- choke-bars [a]
  (mapv (fn [cp]
          {:name (name cp) :value (count (get-in a [:choke-plants cp]))
           :col (get choke-colors (name cp) "#9ad")})
        (az/chokes-by-load a)))

(defn- choke-points
  "Plot each first-class chokepoint at its coordinates, sized by export-dependence count."
  [a]
  (vec (keep (fn [cp]
               (when-let [c (get-in a [:choke-coords cp])]
                 (let [n (count (get-in a [:choke-plants cp]))]
                   {:lat (:lat c) :lon (:lon c)
                    :col (get choke-colors (name cp) "#9ad")
                    :w (* n n) :kind "chokepoint"
                    :nm (str "⚓ " (:name c)) :cc (name cp)
                    :info [(str "shared chokepoint keyword: :" (name cp))
                           (str "plants export-dependent: " n)
                           "composes with watari (live craft) + watatsuna (cable)"]})))
             (keys (:choke-coords a)))))

;; ── watari craft latest positions (real keywords; clojure.edn) ──────────────────
#?(:clj
   (defn- watari-craft-points [watari-seed]
     (when (.exists (io/file watari-seed))
       (let [rows (edn/read-string (slurp watari-seed))
             craft (into {} (keep (fn [r] (when (:craft/id r) [(:craft/id r) r])) rows))
             fixes (filter :craft.fix/id rows)
             latest (reduce (fn [m fx]
                              (let [c (:craft.fix/craft fx)
                                    cur (get m c)]
                                (if (or (nil? cur)
                                        (pos? (compare (:craft.fix/observed-at fx "")
                                                       (:craft.fix/observed-at cur ""))))
                                  (assoc m c fx) m)))
                            {} fixes)]
         (mapv (fn [[cid fx]]
                 (let [cm (get craft cid {})
                       kind (:craft/kind cm)]
                   {:lat (:craft.fix/lat fx) :lon (:craft.fix/lon fx)
                    :col (if (= kind :aircraft) "#ff9f6b" "#5ad1a8")
                    :w 1 :kind (name kind)
                    :nm (or (:craft/name cm) (:craft/callsign cm) cid)
                    :cc (:craft/flag cm "")
                    :info [(str "kind: " (name kind))
                           (str "lane: " (:craft.fix/lane fx ""))
                           (str "as-of: " (:craft.fix/observed-at fx ""))]}))
               latest)))))

;; The 動 (watari) + 静-infra (watatsuna) readers + the composition live in tatara.methods.compose
;; (the canonical SSoT); this viz only ADDS the geographic markers + the bar styling.

#?(:clj
   (defn- watatsuna-station-points
     "Cable landing stations as small markers (the third overlay on the world-supply globe)."
     [watatsuna-seed]
     (if-not (.exists (io/file watatsuna-seed))
       []
       (mapv (fn [s]
               {:lat (:station/lat s) :lon (:station/lon s) :col "#7fb1c9" :w 0 :kind "station"
                :nm (str "⌁ " (:station/name s)) :cc (:station/country s "")
                :info [(str "cable landing station (watatsuna)")
                       (str "chokepoint(s): " (clojure.string/join ", " (map name (:station/chokepoint s))))]})
             (filter :station/id (edn/read-string (slurp watatsuna-seed)))))))

(defn- composition-bars
  "Style the canonical compose/choke-composition (静 plants · 動 craft · 静-infra cable) as bars.
  :value = plants (primary bar); :sub = craft; :sub2 = cable stations."
  [a craft-transit cable-load]
  (mapv (fn [{:keys [chokepoint plants craft cable]}]
          {:name (name chokepoint) :value plants :sub craft :sub2 cable
           :col (get choke-colors (name chokepoint) "#9ad")})
        (compose/choke-composition a craft-transit cable-load)))

;; ── HTML template (orthographic canvas globe) ───────────────────────────────────
(defn- html [{:keys [title ja subtitle source data legend bars-title]}]
  (str "<!DOCTYPE html>
<!-- " title " " ja " — GENERATED by tatara/viz/build_viz.cljc. Self-contained canvas
     orthographic globe (drag to rotate / auto-spin / click). Aggregate-first RESILIENCE
     map (G2): where to ADD redundancy — NOT a target-list. ADR-2606171800. -->
<html lang=\"en\"><head><meta charset=\"utf-8\">
<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
<title>" title " " ja "</title>
<style>
 :root{--bg:#060a12;--panel:#0e1626;--ink:#cdd9ee;--dim:#7388a8;--line:#1d2a40;--accent:#62d0ff;}
 *{box-sizing:border-box;}html,body{margin:0;height:100%;background:var(--bg);color:var(--ink);
   font:13px/1.45 ui-monospace,\"SF Mono\",Menlo,monospace;}
 #wrap{display:flex;height:100vh;}#stage{flex:1;position:relative;min-width:0;}
 canvas{display:block;width:100%;height:100%;cursor:grab;}canvas.drag{cursor:grabbing;}
 #side{width:340px;background:var(--panel);border-left:1px solid var(--line);overflow-y:auto;padding:14px 16px;}
 h1{font-size:15px;margin:0 0 2px;letter-spacing:.04em;}h1 .ja{color:var(--accent);}
 .sub{color:var(--dim);font-size:11px;margin:0 0 12px;}
 h2{font-size:11px;text-transform:uppercase;letter-spacing:.12em;color:var(--dim);margin:18px 0 7px;
    border-bottom:1px solid var(--line);padding-bottom:4px;}
 .stat{display:flex;justify-content:space-between;padding:1px 0;}.stat b{color:#fff;font-weight:600;}
 .bar{margin:5px 0;}.bar .lab{display:flex;justify-content:space-between;font-size:11px;}
 .bar .track{height:7px;background:#101a2c;border-radius:4px;overflow:hidden;margin-top:2px;}
 .bar .fill{height:100%;border-radius:4px;}
 #info{min-height:84px;background:#0a121f;border:1px solid var(--line);border-radius:6px;padding:9px 11px;
   margin-top:6px;font-size:12px;}#info .nm{color:#fff;font-weight:600;}#info .cc{color:var(--accent);}
 #info ul{margin:6px 0 0;padding-left:16px;color:var(--dim);}
 .foot{color:var(--dim);font-size:10px;margin-top:16px;border-top:1px solid var(--line);padding-top:9px;}
 .warn{color:#ffcf6b;}
 #legend span{display:inline-flex;align-items:center;gap:5px;margin:2px 8px 2px 0;font-size:11px;}
 #legend i{width:9px;height:9px;border-radius:50%;display:inline-block;}
 #hint{position:absolute;left:12px;bottom:10px;color:var(--dim);font-size:11px;}
</style></head><body>
<div id=\"wrap\"><div id=\"stage\"><canvas id=\"c\"></canvas>
 <div id=\"hint\">drag to rotate · auto-spins when idle · click a point</div></div>
<div id=\"side\">
 <h1>" title " <span class=\"ja\">" ja "</span></h1>
 <p class=\"sub\">" subtitle "<br><span class=\"warn\">resilience map, NOT a target-list</span>
   &middot; source: <code>" source "</code></p>
 <h2>Overview</h2><div id=\"stats\"></div>
 <h2>" bars-title "</h2><div id=\"bars\"></div>
 <h2>Selected</h2><div id=\"info\">click a point &rarr; kotoba object data</div>
 <h2>Legend</h2><div id=\"legend\"></div>
 <p class=\"foot\">HONEST: self-contained canvas globe; bounded <code>:representative</code> seed;
   coordinates rounded to city/campus scale. Worker figures are DISCLOSED AGGREGATE facility
   employment (a SIZE), never per-worker data (G4). Live ingest G7-gated. ADR-2606171800.</p>
</div></div>
<script>
const DATA=" (json data) ";
const LEGEND=" (json legend) ";
const D2R=Math.PI/180;
function v3(lat,lon){const a=lat*D2R,b=lon*D2R,c=Math.cos(a);return[c*Math.cos(b),Math.sin(a),c*Math.sin(b)];}
function rotY(v,a){const s=Math.sin(a),c=Math.cos(a);return[v[0]*c+v[2]*s,v[1],-v[0]*s+v[2]*c];}
function rotX(v,a){const s=Math.sin(a),c=Math.cos(a);return[v[0],v[1]*c-v[2]*s,v[1]*s+v[2]*c];}
function dot(a,b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
function norm(v){const m=Math.hypot(v[0],v[1],v[2])||1;return[v[0]/m,v[1]/m,v[2]/m];}
function slerp(a,b,t){let o=Math.acos(Math.max(-1,Math.min(1,dot(a,b))));if(o<1e-4)return a;
 const s=Math.sin(o),k1=Math.sin((1-t)*o)/s,k2=Math.sin(t*o)/s;
 return norm([a[0]*k1+b[0]*k2,a[1]*k1+b[1]*k2,a[2]*k1+b[2]*k2]);}
const cv=document.getElementById('c'),ctx=cv.getContext('2d');
let W=0,H=0,R=0,cx=0,cy=0,DPR=Math.max(1,window.devicePixelRatio||1);
let ry=2.0,rx=-0.30;
function resize(){const r=cv.getBoundingClientRect();W=r.width;H=r.height;
 cv.width=W*DPR;cv.height=H*DPR;ctx.setTransform(DPR,0,0,DPR,0,0);R=Math.min(W,H)*0.42;cx=W/2;cy=H/2;}
window.addEventListener('resize',resize);
function project(lat,lon){let v=rotX(rotY(v3(lat,lon),ry),rx);return{x:cx+R*v[0],y:cy-R*v[1],front:v[2]>0};}
const maxW=Math.max(1,...DATA.points.map(p=>p.w||0));
const rOf=p=>p.kind==='chokepoint'?(4+1.6*Math.sqrt(p.w||1)):(p.kind==='station')?2.6:(p.kind==='hub')?3.2:(3+11*Math.sqrt((p.w||1)/maxW));
function draw(){
 ctx.clearRect(0,0,W,H);
 const g=ctx.createRadialGradient(cx-R*0.3,cy-R*0.3,R*0.2,cx,cy,R);
 g.addColorStop(0,'#0e2036');g.addColorStop(1,'#08111d');
 ctx.beginPath();ctx.arc(cx,cy,R,0,7);ctx.fillStyle=g;ctx.fill();
 ctx.lineWidth=1;ctx.strokeStyle='#1b2c4d';ctx.stroke();
 ctx.strokeStyle='#142442';ctx.lineWidth=1;
 for(let lon=-150;lon<=180;lon+=30){let p=false;ctx.beginPath();
  for(let lat=-90;lat<=90;lat+=5){const q=project(lat,lon);
   if(q.front){p?ctx.lineTo(q.x,q.y):ctx.moveTo(q.x,q.y);p=true;}else p=false;}ctx.stroke();}
 for(let lat=-60;lat<=60;lat+=30){let p=false;ctx.beginPath();
  for(let lon=-180;lon<=180;lon+=5){const q=project(lat,lon);
   if(q.front){p?ctx.lineTo(q.x,q.y):ctx.moveTo(q.x,q.y);p=true;}else p=false;}ctx.stroke();}
 (DATA.arcs||[]).forEach(sg=>{const a=norm(v3(sg.from[0],sg.from[1])),b=norm(v3(sg.to[0],sg.to[1]));
  ctx.strokeStyle=sg.col;ctx.lineWidth=1.3;ctx.globalAlpha=0.6;let st=false;ctx.beginPath();
  for(let i=0;i<=28;i++){const m=slerp(a,b,i/28);let v=rotX(rotY(m,ry),rx);
   if(v[2]>0){const x=cx+R*v[0],y=cy-R*v[1];st?ctx.lineTo(x,y):ctx.moveTo(x,y);st=true;}else st=false;}
  ctx.stroke();ctx.globalAlpha=1;});
 const pts=DATA.points.map((p,i)=>({p,i,q:project(p.lat,p.lon)})).filter(o=>o.q.front)
   .sort((u,v)=>u.q.y-v.q.y);
 pts.forEach(({p,q})=>{const r=rOf(p);
  if(p.kind==='chokepoint'){ctx.save();ctx.translate(q.x,q.y);ctx.rotate(Math.PI/4);
   ctx.globalAlpha=(hover&&hover!==p)?0.5:0.9;ctx.fillStyle=p.col;ctx.fillRect(-r,-r,2*r,2*r);
   ctx.globalAlpha=1;ctx.lineWidth=(hover===p)?2:1.4;ctx.strokeStyle=(hover===p)?'#fff':'#04121d';
   ctx.strokeRect(-r,-r,2*r,2*r);ctx.restore();return;}
  ctx.beginPath();ctx.arc(q.x,q.y,r,0,7);ctx.fillStyle=p.col;
  ctx.globalAlpha=(hover&&hover!==p)?0.55:0.95;ctx.fill();ctx.globalAlpha=1;
  ctx.lineWidth=(hover===p)?2:1;ctx.strokeStyle=(hover===p)?'#fff':(p.kind==='hub'?'#cfe':'#04121d');ctx.stroke();});
}
let hover=null;
function pick(mx,my){let best=null,bd=1e9;DATA.points.forEach(p=>{const q=project(p.lat,p.lon);
 if(!q.front)return;const d=Math.hypot(q.x-mx,q.y-my);
 if(d<Math.max(7,rOf(p))+3&&d<bd){bd=d;best=p;}});return best;}
let dragging=false,lx=0,ly=0,spin=true;
cv.addEventListener('mousedown',e=>{dragging=true;spin=false;lx=e.clientX;ly=e.clientY;cv.classList.add('drag');});
window.addEventListener('mouseup',()=>{dragging=false;cv.classList.remove('drag');setTimeout(()=>spin=true,1500);});
window.addEventListener('mousemove',e=>{const r=cv.getBoundingClientRect();
 if(dragging){ry+=(e.clientX-lx)*0.006;rx=Math.max(-1.4,Math.min(1.4,rx-(e.clientY-ly)*0.006));lx=e.clientX;ly=e.clientY;}
 else{const h=pick(e.clientX-r.left,e.clientY-r.top);if(h!==hover)hover=h;}});
cv.addEventListener('click',e=>{const r=cv.getBoundingClientRect();
 const p=pick(e.clientX-r.left,e.clientY-r.top);if(p)showInfo(p);});
function showInfo(p){document.getElementById('info').innerHTML=
 '<div class=\"nm\">'+p.nm+' <span class=\"cc\">'+(p.cc||'')+'</span></div>'+
 '<ul>'+(p.info||[]).filter(Boolean).map(x=>'<li>'+x+'</li>').join('')+'</ul>';}
const S=document.getElementById('stats');
S.innerHTML=DATA.stats.map(s=>'<div class=\"stat\"><span>'+s[0]+'</span><b>'+s[1]+'</b></div>').join('');
const maxBar=Math.max(1,...DATA.bars.map(b=>b.value));
const maxSub=Math.max(1,...DATA.bars.map(b=>b.sub||0));
const maxSub2=Math.max(1,...DATA.bars.map(b=>b.sub2||0));
const mkbar=(v,mx,col)=>'<div class=\"track\" style=\"height:4px;margin-top:2px;background:#0a1422\"><div class=\"fill\" style=\"width:'+(100*v/mx)+'%;background:'+col+'\"></div></div>';
document.getElementById('bars').innerHTML=DATA.bars.map(b=>{
 let lab=''+b.value;
 if(b.sub!=null)lab=b.value+'p · '+b.sub+'c'+(b.sub2!=null?' · '+b.sub2+'s':'');
 const sub=(b.sub!=null)?mkbar(b.sub,maxSub,'#5ad1a8'):'';
 const sub2=(b.sub2!=null)?mkbar(b.sub2,maxSub2,'#7fb1c9'):'';
 return '<div class=\"bar\"><div class=\"lab\"><span>'+b.name+'</span><span>'+lab+'</span></div>'+
  '<div class=\"track\"><div class=\"fill\" style=\"width:'+(100*b.value/maxBar)+'%;background:'+b.col+'\"></div></div>'+sub+sub2+'</div>';
}).join('');
document.getElementById('legend').innerHTML=LEGEND.map(l=>
 '<span><i style=\"background:'+l[1]+'\"></i>'+l[0]+'</span>').join('');
resize();(function loop(){if(spin&&!dragging)ry+=0.0016;draw();requestAnimationFrame(loop);})();
</script></body></html>\n"))

#?(:clj
   (defn -main [& _]
     (let [cand (let [f (clojure.java.io/file *file*)]
                  (-> f .getAbsoluteFile .getParentFile .getParentFile .getParentFile)) ; 20-actors/
           root (if (.exists (io/file cand "tatara" "data" "seed-plant-graph.kotoba.edn"))
                  cand
                  (io/file "20-actors"))
           tatara-seed (io/file root "tatara" "data" "seed-plant-graph.kotoba.edn")
           watari-seed (io/file root "watari" "data" "seed-craft-graph.kotoba.edn")
           watatsuna-seed (io/file root "watatsuna" "data" "cable-graph.merged.kotoba.edn")
           rows (az/load-edn tatara-seed)
           g (az/classify rows)
           a (az/analyze g)
           plant-by-id (into {} (map (juxt :plant/id identity) (:plants g)))
           hub-by-id (into {} (map (juxt :hub/id identity) (:hubs g)))
           pp (plant-points (:plants g))
           hp (hub-points (:hubs g))
           arcs (flow-arcs plant-by-id hub-by-id (:flows g))
           bars (choke-bars a)
           cps (choke-points a)
           sector-legend (mapv (fn [[s c]] [(name s) c])
                               (sort-by (comp name first) sector-colors))
           craft (or (watari-craft-points watari-seed) [])
           craft-transit (compose/watari-choke-transit watari-seed)
           cable-load (compose/watatsuna-choke-load watatsuna-seed)
           stations (watatsuna-station-points watatsuna-seed)
           comp-bars (composition-bars a craft-transit cable-load)

           ;; (C) plant globe
           plant-html
           (html {:title "tatara" :ja "鑪"
                  :subtitle "world manufacturing plants &amp; logistics flows, by sector"
                  :source "seed-plant-graph.kotoba.edn"
                  :bars-title "Chokepoint export-dependence"
                  :legend (conj sector-legend ["logistics hub" "#8fa9c8"] ["◆ chokepoint" "#ffcf6b"])
                  :data {:points (into (into pp hp) cps) :arcs arcs :bars bars
                         :stats [["plants" (:n-plants a)] ["logistics hubs" (:n-hubs a)]
                                 ["export flows" (:n-flows a)]
                                 ["aggregate employment" (:global-headcount a)]]}})

           ;; (A) integrated world supply globe — plants + live craft + composition
           world-html
           (html {:title "world supply" :ja "世界製造・物流"
                  :subtitle "plants (tatara) + live craft (watari) + cable stations (watatsuna) composed per chokepoint"
                  :source "tatara + watari + watatsuna seeds"
                  :bars-title "Chokepoint composition — plants (静) · craft (動) · cable (静 infra)"
                  :legend [["manufacturing plant" "#62d0ff"] ["vessel (live)" "#5ad1a8"]
                           ["aircraft (live)" "#ff9f6b"] ["logistics hub" "#8fa9c8"]
                           ["⌁ cable station" "#7fb1c9"] ["◆ chokepoint" "#ffcf6b"]]
                  :data {:points (into (into (into (into pp hp) cps) stations) craft) :arcs arcs :bars comp-bars
                         :stats [["plants" (:n-plants a)] ["live craft (watari)" (count craft)]
                                 ["export flows" (:n-flows a)]
                                 ["aggregate employment" (:global-headcount a)]]}})

           ;; (B) watari craft globe
           craft-bars (->> craft (group-by :kind)
                           (map (fn [[k v]] {:name k :value (count v)
                                             :col (if (= k "aircraft") "#ff9f6b" "#5ad1a8")}))
                           (sort-by (comp - :value)) vec)
           craft-html
           (html {:title "watari" :ja "渡り"
                  :subtitle "live moving-craft positions (latest as-of fix per craft)"
                  :source "seed-craft-graph.kotoba.edn"
                  :bars-title "Live craft by kind"
                  :legend [["vessel" "#5ad1a8"] ["aircraft" "#ff9f6b"]]
                  :data {:points craft :arcs [] :bars craft-bars
                         :stats [["craft (latest fix)" (count craft)]
                                 ["vessels" (count (filter #(= "vessel" (:kind %)) craft))]
                                 ["aircraft" (count (filter #(= "aircraft" (:kind %)) craft))]]}})

           out-tatara (io/file root "tatara" "viz")
           out-watari (io/file root "watari" "viz")]
       (.mkdirs out-tatara) (.mkdirs out-watari)
       (spit (io/file out-tatara "plant-globe.htm") plant-html)
       (spit (io/file out-tatara "world-supply-globe.htm") world-html)
       (spit (io/file out-watari "craft-globe.htm") craft-html)
       (println (str "tatara viz: wrote plant-globe.htm + world-supply-globe.htm ("
                     (:n-plants a) " plants, " (count craft) " live craft); "
                     "watari viz: wrote craft-globe.htm"))
       0)))

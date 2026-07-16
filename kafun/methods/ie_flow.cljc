#!/usr/bin/env bb
;; kafun 花粉 — ie-flow embedding + energy-flow VISUALIZATION (the SoS leg).
(ns kafun.methods.ie-flow
  "ie_flow.cljc — kafun 花粉 embeds the information-energy flow lifecycle
  (etzhayyim.ie-flow, ADR-2606211200) and renders how the actor CHANGES the
  energy flow of the system it sits in. ADR-2606212030.

  The ie-flow framing (energy-order protocol): an actor is a dissipative
  structure that draws free energy from society and is judged by how much
  LOW-ENTROPY STRUCTURE (order) it returns (η = exported ÷ consumed, the 共生
  axis). kafun's substrate is the 花粉 burden: 散在 (scattered) pollen-source
  pressure across many cedar/cypress stands = high-entropy disorder imposed on
  humans. kafun's GATE is a RECTIFIER (整流): it folds that scattered burden
  flow onto OUTCOMES — concentrating realised restoration value onto the
  highest-burden, viable, consented stands (:reforest-priority) and cleanly
  routing the rest to NAMED sinks (await 無花粉苗木 L1-1 / await-consent /
  protected-selective / refuse / monitor). order-index = the entropy DROP from
  the burden (VOLUME) distribution to the realised-value distribution = how much
  scattered pollen-disorder kafun rectified into prioritized restoration order.

  As a SYSTEM OF SYSTEMS, kafun's verdict sinks become INPUTS to downstream
  actors — sanae (planting robotics + 無花粉苗木) / inochi (biosphere
  restoration) / musubi (consent) / mitate+iyashi (allergic-rhinitis care). The
  visualization makes the whole transfer legible: 散在 burden → kafun整流 →
  prioritized routes → downstream actors.

  This namespace is PURE measurement + a self-contained HTML renderer; it embeds
  the SHARED ie-flow metrics (not a fork). Assessment-only — kafun never cuts or
  plants; the flow it moves is INFORMATION-energy (a prioritized map), cheaply
  (agent-efficiency ≫ 1: a 利得, not a 課金される魔法陣)."
  (:require [kafun.methods.kafun-edn :as ke]
            [kafun.methods.remediate :as rem]
            [etzhayyim.ie-flow.metrics :as iem]
            [etzhayyim.ie-flow.gate-adapter :as ga]
            [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])))

;; ── value model: how much ORDER each verdict route exports per unit burden ───
;; reforest-priority exports the most (full restoration, scaled by viability);
;; refuse exports PROTECTIVE order (rejecting a clearcut/carbon-positive path is
;; itself order — disorder prevented); monitor the least. Pure, deterministic.

(def ^:private value-scale 100.0)
(def ^:private assess-cost 2.0)     ;; kafun's per-stand compute (cheap — it only assesses)

(defn- route-factor
  "Fraction of a stand's burden that this verdict rectifies into realised RESTORATION
  order THIS cycle. reforest-priority delivers full restoration (scaled by viability);
  protected-selective starts gradual restoration; await-* are routed-but-pending (the
  burden is assigned to a named bottleneck, but ~no restoration moves yet); refuse
  exports 0 RESTORATION (it is PROTECTIVE — disorder prevented, shown structurally as a
  red sink, not restoration-energy); monitor observes. The concentration of realised
  value onto the few actionable stands IS the order kafun adds (high order-index)."
  [verdict viability]
  (case verdict
    :reforest-priority    (double viability)   ;; full restoration, scaled by how viably it replants
    :protected-selective  0.25                 ;; gradual/selective restoration begins
    :await-sapling-supply 0.05                 ;; routed to the L1-1 無花粉苗木 bottleneck (pending supply)
    :await-consent        0.05                 ;; routed, pending landowner/community consent
    :refuse               0.0                  ;; PROTECTIVE only — exports no restoration-energy this cycle
    :monitor              0.02                 ;; observe only
    0.02))

(def downstream
  "SoS routing: a verdict sink → the downstream actor(s) it feeds (the system of systems)."
  {:reforest-priority    ["sanae" "inochi"]
   :await-sapling-supply ["sanae"]
   :await-consent        ["musubi"]
   :protected-selective  ["inochi"]
   :refuse               ["kamado"]
   :monitor              ["kafun"]})

(def ^:private actor-label
  {"sanae" "sanae 早苗 (植林ロボ/苗木)" "inochi" "inochi 命 (生態系復元)"
   "musubi" "musubi 結 (同意)" "kamado" "kamado 竈 (炭素転換)"
   "kafun" "kafun 花粉 (再観測)" "mitate" "mitate/iyashi (花粉症ケア)"})

;; ── kafun assessment → measured ie-flow EVENTS ───────────────────────────────

(defn config
  "The shared gate-adapter config for kafun's remediation assessment (the domain model; the
  shared helper does the event/metric/record plumbing). Rows are the assess rows ENRICHED with
  the stand's :reforest-viability — the value model scales :reforest-priority by viability.
  source = the stand, route = the verdict, volume = pollen-burden, value = burden·route-factor·scale."
  [stands]
  (let [by-id (into {} (map (juxt :id identity) stands))
        rows (mapv (fn [r] (assoc r "viability"
                                  (double (or (:reforest-viability (get by-id (get r "id"))) 0))))
                   (get (rem/assess stands) "stands"))]
    {:actor "kafun" :id-prefix "kafun-" :source-kind "stand"
     :rows rows
     :route-key "verdict"
     :volume-fn #(double (get % "pollen_burden"))
     :value-fn #(* (double (get % "pollen_burden"))
                   (double (route-factor (get % "verdict") (double (get % "viability"))))
                   value-scale)}))

(defn flow-events
  "Project the kafun assessment into ie-flow EVENT maps via the SHARED gate-adapter. One event
  per stand: source = the stand (a 花粉源), target = the verdict route, volume = pollen-burden
  (the scattered input flow), value = burden·route-factor·scale (the order kafun rectifies onto
  an outcome), cost = flat assessment compute, risk = 0 (assessment-only, no actuation)."
  [stands]
  (ga/flow-events (config stands)))

(defn flow-state
  "Fold kafun's measured events through the SHARED ie-flow metrics → the order
  calculus (net-gain / order-index / agent-efficiency / parasitic?)."
  [stands]
  (ga/flow-state (config stands)))

#?(:clj
   (defn record-flow!
     "Record kafun's measured ie-flow EVENTS to the shared per-actor ie-flow ledger
     (80-data/ie-flow/kafun/flow.kotoba.edn) via the SHARED gate-adapter — so kafun's SoS
     scoreboard entry is produced by the heartbeat/tool, not only adapter-on-demand. The flow
     ledger is gitignored (generated). Deterministic (caller supplies tx-id + as-of) →
     resume-safe. No-server-key. Returns {:flow-log <path> :events <n> :order-index <oi>}."
     ([stands] (record-flow! stands {}))
     ([stands opts] (ga/record-flow! (config stands) opts))))

;; ── viz model (the SSoT the HTML renders; columns = the SoS pipeline) ────────

(def ^:private verdict-color
  {"reforest-priority" "#2f9e44" "await-sapling-supply" "#1098ad"
   "await-consent" "#f08c00" "protected-selective" "#1971c2"
   "refuse" "#e03131" "monitor" "#868e96"})

(def ^:private species-color
  {:sugi "#c0682f" :hinoki "#b5892f" :mixed-conifer "#5c8a3a" :broadleaf "#4a9e6f"})

(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn viz-model
  "Build the energy-flow viz model from the assessment: 4 columns (花粉源 stands →
  kafun 整流 → verdict routes → downstream SoS actors) + the order metrics + the
  before/after entropy of the rectification."
  [stands]
  (let [assessment (rem/assess stands)
        rows (get assessment "stands")
        events (flow-events stands)
        st (iem/flow-state events)
        by-id (into {} (map (juxt :id identity) stands))
        ;; before = burden (volume) distribution; after = realised-value distribution
        h-before (iem/entropy (iem/normalize (map :volume events)))
        h-after  (iem/entropy (iem/normalize (map :value events)))
        ;; col 1 — stands (sources), height ∝ burden
        stand-nodes (vec (for [r rows
                               :let [s (get by-id (get r "id"))]]
                           {:id (str "stand:" (get r "id"))
                            :label (:name s)
                            :mag (round3 (get r "pollen_burden"))
                            :sub (str (name (:species s)) " · burden " (round3 (get r "pollen_burden")))
                            :color (species-color (:species s) "#888")
                            :verdict (name (get r "verdict"))}))
        ;; col 3 — verdict routes, height ∝ Σ burden routed there
        route-tally (->> rows
                         (group-by #(name (get % "verdict")))
                         (map (fn [[v rs]]
                                [v {:burden (reduce + 0.0 (map #(double (get % "pollen_burden")) rs))
                                    :value  (reduce + 0.0 (map (fn [r]
                                                                 (let [s (get by-id (get r "id"))
                                                                       f (route-factor (get r "verdict")
                                                                                        (double (or (:reforest-viability s) 0)))]
                                                                   (* (double (get r "pollen_burden")) f value-scale)))
                                                               rs))
                                    :count (count rs)}]))
                         (into {}))
        route-order ["reforest-priority" "await-sapling-supply" "await-consent"
                     "protected-selective" "refuse" "monitor"]
        route-nodes (vec (for [v route-order
                               :let [t (get route-tally v)]
                               :when t]
                           {:id (str "route:" v)
                            :label v
                            :mag (round3 (:burden t))
                            :sub (str (:count t) " stand · order " (round3 (:value t)))
                            :color (verdict-color v "#888")
                            :verdict v}))
        ;; col 4 — downstream SoS actors
        actor-ids (->> route-order
                       (mapcat #(get downstream (keyword %)))
                       distinct vec)
        actor-burden (reduce (fn [acc v]
                               (let [b (get-in route-tally [v :burden] 0.0)
                                     as (get downstream (keyword v))
                                     share (/ b (max 1 (count as)))]
                                 (reduce (fn [a aid] (update a aid (fnil + 0.0) share)) acc as)))
                             {} route-order)
        actor-nodes (vec (for [aid actor-ids]
                           {:id (str "actor:" aid)
                            :label (actor-label aid aid)
                            :mag (round3 (get actor-burden aid 0.0))
                            :sub "downstream actor"
                            :color "#3b5bdb"
                            :verdict "sos"}))
        ;; links: stand→kafun (colored by verdict), kafun→route, route→actor
        gate-id "gate:kafun"
        links (vec
               (concat
                (for [r rows :let [v (name (get r "verdict"))]]
                  {:from (str "stand:" (get r "id")) :to gate-id
                   :value (round3 (get r "pollen_burden")) :verdict v})
                (for [v route-order :let [t (get route-tally v)] :when t]
                  {:from gate-id :to (str "route:" v)
                   :value (round3 (:burden t)) :verdict v})
                (for [v route-order
                      :let [t (get route-tally v)] :when t
                      :let [as (get downstream (keyword v))
                            share (/ (:burden t) (max 1 (count as)))]
                      aid as]
                  {:from (str "route:" v) :to (str "actor:" aid)
                   :value (round3 share) :verdict v})))]
    {:actor "kafun"
     :glyph "花"
     :title "kafun 花粉 — how the actor changes the energy flow (ie · system of systems)"
     :subtitle "散在する花粉負荷 (disorder) → kafun 整流 (rectifier) → 優先順位化された復元 (order) → 下流アクター"
     :metrics {:throughput (round3 (:throughput st))
               :net-gain (round3 (:net-gain st))
               :order-index (round3 (:order-index st))
               :order-exported (round3 (:total-value st))
               :actionable-now (round3 (/ (get-in route-tally ["reforest-priority" :burden] 0.0)
                                          (max 1.0e-9 (:throughput st))))
               :eta (round3 (/ (:total-value st) (max 1.0e-9 (:total-cost st))))
               :h-before (round3 h-before)
               :h-after (round3 h-after)
               :total-value (round3 (:total-value st))
               :total-cost (round3 (:total-cost st))
               :parasitic (:parasitic? st)
               :flows-n (:flows-n st)}
     :gate {:id gate-id :label (str "kafun 花 整流 · order-index " (round3 (:order-index st)))}
     :columns [{:id "sources" :label "花粉源 stands · 散在 burden (disorder)" :nodes stand-nodes}
               {:id "gate" :label "kafun 整流 (rectifier)" :nodes [{:id gate-id :label "花" :mag (round3 (:throughput st)) :sub "情報-エネルギー整流" :color "#111" :verdict "gate"}]}
               {:id "routes" :label "remediation verdict (rectified order)" :nodes route-nodes}
               {:id "sos" :label "下流アクター (system of systems)" :nodes actor-nodes}]
     :links links}))

;; ── self-contained HTML renderer (canvas Sankey; model inlined) ──────────────

(defn- json-escape [s]
  (-> (str s) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")))

(defn- ->json [v]
  (cond
    (map? v) (str "{" (str/join "," (for [[k val] v]
                                      (str "\"" (name k) "\":" (->json val)))) "}")
    (sequential? v) (str "[" (str/join "," (map ->json v)) "]")
    (keyword? v) (str "\"" (name v) "\"")
    (string? v) (str "\"" (json-escape v) "\"")
    (boolean? v) (str v)
    (number? v) (str v)
    (nil? v) "null"
    :else (str "\"" (json-escape v) "\"")))

(defn render-html [model]
  (str "<!doctype html><html lang=\"ja\"><head><meta charset=\"utf-8\">
<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
<title>kafun 花粉 — energy-flow (ie SoS)</title>
<style>
  :root{--bg:#0d1117;--fg:#e6edf3;--mut:#8b949e;--card:#161b22;--line:#30363d}
  *{box-sizing:border-box} html,body{margin:0;background:var(--bg);color:var(--fg);
    font-family:-apple-system,'Hiragino Sans','Noto Sans JP',system-ui,sans-serif}
  .wrap{max-width:1280px;margin:0 auto;padding:20px}
  h1{font-size:18px;margin:0 0 2px} .sub{color:var(--mut);font-size:13px;margin:0 0 14px}
  .metrics{display:flex;flex-wrap:wrap;gap:10px;margin:0 0 16px}
  .m{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:9px 13px;min-width:120px}
  .m .k{color:var(--mut);font-size:11px;letter-spacing:.02em} .m .v{font-size:19px;font-weight:650;margin-top:2px}
  .m .v.good{color:#3fb950} .m .v.warn{color:#d29922}
  canvas{width:100%;height:auto;background:var(--card);border:1px solid var(--line);border-radius:12px}
  .legend{display:flex;flex-wrap:wrap;gap:12px;margin:12px 0;font-size:12px;color:var(--mut)}
  .legend i{display:inline-block;width:11px;height:11px;border-radius:3px;margin-right:5px;vertical-align:-1px}
  .note{color:var(--mut);font-size:12px;line-height:1.6;margin-top:12px;border-top:1px solid var(--line);padding-top:12px}
  code{color:#79c0ff}
</style></head><body><div class=\"wrap\">
<h1>" (json-escape (:title model)) "</h1>
<p class=\"sub\">" (json-escape (:subtitle model)) "</p>
<div class=\"metrics\" id=\"metrics\"></div>
<canvas id=\"c\" width=\"1240\" height=\"640\"></canvas>
<div class=\"legend\" id=\"legend\"></div>
<div class=\"note\">
<b>整流 (rectification)</b>: order-index = 1 − H(after)/H(before) — kafun が散在した花粉負荷
(H<sub>before</sub>=<span id=\"hb\"></span>) を実現した復元価値の分布 (H<sub>after</sub>=<span id=\"ha\"></span>)
へ集中させた度合い。<b>η = 輸出 ÷ 消費</b> = kafun が安価な assessment で生む order の倍率 (共生軸; ≫1 = 利得、課金される魔法陣ではない)。
kafun は伐採も植林もしない — 動かすのは<b>情報-エネルギー</b> (優先順位化された地図) であり、物理的伐採は landowner+operator/Council。
verdict sink は下流アクター (sanae/inochi/musubi/kamado) の入力になる = <b>system of systems</b>。
</div>
</div>
<script>
const M=" (->json model) ";
const C={'reforest-priority':'#2f9e44','await-sapling-supply':'#1098ad','await-consent':'#f08c00','protected-selective':'#1971c2','refuse':'#e03131','monitor':'#868e96','sos':'#3b5bdb','gate':'#888'};
// metrics cards
const mm=M.metrics, mk=[['throughput',mm.throughput,'総花粉負荷 (flow)'],['order-index',mm['order-index'],'整流度 1−H_a/H_b ↑'],['net-gain',mm['net-gain'],'value−cost (Φ)'],['η export÷consume',mm.eta,'共生軸 ≫1'],['order exported',mm['order-exported'],'復元 order 輸出'],['actionable now',(mm['actionable-now']*100).toFixed(0)+'%','即実行 reforest 比'],['H before→after',mm['h-before']+' → '+mm['h-after'],'entropy 整流']];
const elM=document.getElementById('metrics');
for(const [k,v,s] of mk){const d=document.createElement('div');d.className='m';
  let cls=(k==='order-index'||k==='η export÷consume')&&parseFloat(v)>0?'good':'';
  if(k==='net-gain')cls=parseFloat(v)>0?'good':'warn';
  d.innerHTML='<div class=\"k\">'+k+'</div><div class=\"v '+cls+'\">'+v+'</div><div class=\"k\">'+s+'</div>';elM.appendChild(d);}
document.getElementById('hb').textContent=mm['h-before'];document.getElementById('ha').textContent=mm['h-after'];
// legend
const L=document.getElementById('legend');
for(const [k,c] of Object.entries(C)){if(k==='gate')continue;const s=document.createElement('span');s.innerHTML='<i style=\"background:'+c+'\"></i>'+k;L.appendChild(s);}
// layout
const cv=document.getElementById('c'),x=cv.getContext('2d');
const W=cv.width,H=cv.height,PAD=70,colX=[110,430,720,1080];
const cols=M.columns;
function place(col,xi){const ns=col.nodes,n=ns.length;
  const tot=ns.reduce((a,b)=>a+Math.max(b.mag,0.02),0)||1;
  const usable=H-2*PAD-(n-1)*8;let y=PAD;const pos={};
  for(const nd of ns){const h=Math.max(10,usable*Math.max(nd.mag,0.02)/tot);
    pos[nd.id]={x:colX[xi],y:y,h:h,node:nd};y+=h+8;}return pos;}
let P={};cols.forEach((c,i)=>{Object.assign(P,place(c,i));});
// draw ribbons first
function ribbon(a,b,w,color){if(!a||!b)return;
  const x0=a.x+46,x1=b.x-46,y0=a.cy,y1=b.cy,mx=(x0+x1)/2;
  x.beginPath();x.moveTo(x0,y0-w/2);
  x.bezierCurveTo(mx,y0-w/2,mx,y1-w/2,x1,y1-w/2);
  x.lineTo(x1,y1+w/2);
  x.bezierCurveTo(mx,y1+w/2,mx,y0+w/2,x0,y0+w/2);
  x.closePath();x.fillStyle=color;x.globalAlpha=.32;x.fill();x.globalAlpha=1;}
// assign cy and accumulate offsets for nicer stacking
for(const id in P){const p=P[id];p.cy=p.y+p.h/2;}
const maxV=Math.max(...M.links.map(l=>l.value),0.02);
for(const l of M.links){const a=P[l.from],b=P[l.to];if(!a||!b)continue;
  ribbon(a,b,Math.max(2,40*l.value/maxV),C[l.verdict]||'#888');}
// draw nodes
x.textBaseline='middle';
for(const id in P){const p=P[id],nd=p.node;
  x.fillStyle=nd.color||'#888';x.strokeStyle='#0d1117';x.lineWidth=1;
  roundRect(p.x-46,p.y,92,p.h,6);x.fill();
  // label
  x.fillStyle='#e6edf3';x.font='11px system-ui';
  const lab=(nd.label||'').slice(0,16);
  x.textAlign='center';x.fillText(lab,p.x,p.cy-(p.h>26?6:0));
  if(p.h>26&&nd.sub){x.fillStyle='#aeb6c0';x.font='9px system-ui';x.fillText((nd.sub||'').slice(0,22),p.x,p.cy+8);}}
// column headers
x.textAlign='center';x.font='12px system-ui';x.fillStyle='#8b949e';
cols.forEach((c,i)=>{x.fillText(c.label.slice(0,30),colX[i],28);});
function roundRect(rx,ry,rw,rh,r){x.beginPath();x.moveTo(rx+r,ry);
  x.arcTo(rx+rw,ry,rx+rw,ry+rh,r);x.arcTo(rx+rw,ry+rh,rx,ry+rh,r);
  x.arcTo(rx,ry+rh,rx,ry,r);x.arcTo(rx,ry,rx+rw,ry,r);x.closePath();}
</script></body></html>"))

;; ── CLI (bb): measure + write the viz, optionally record! to the ie-flow ledger ──

#?(:clj
   (defn -main [& args]
     (let [flags (set (filter #(str/starts-with? % "--") args))
           pos  (vec (remove #(str/starts-with? % "--") args))
           seed (or (first pos) "20-actors/kafun/kotoba/seed.edn")
           out  (or (second pos) "20-actors/kafun/viz/energy-flow.html")
           ;; kafun-edn/stands, not a raw read+filter: seed.edn is Datomic/
           ;; Datascript tx-data (Phase 4 EDN datomize) — kafun-edn/classify is
           ;; where the tx-data -> bare-row reconstitution lives.
           stands (ke/stands seed)
           st (flow-state stands)
           model (viz-model stands)]
       (clojure.java.io/make-parents out)
       (spit out (render-html model))
       (println (iem/summary-line st))
       (println (str "η(export÷consume)=" (get-in model [:metrics :eta])
                     " parasitic?=" (:parasitic? st)
                     " H " (get-in model [:metrics :h-before]) "→" (get-in model [:metrics :h-after])))
       (println (str "wrote " out))
       (when (contains? flags "--record")
         (let [r (record-flow! stands {:tx-id "kafun-ie-flow" :as-of "beat"})]
           (println (str "recorded " (:events r) " ie-flow events → " (:flow-log r))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))

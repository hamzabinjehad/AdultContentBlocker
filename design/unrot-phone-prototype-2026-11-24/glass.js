const NS='http://www.w3.org/2000/svg', BX=52.8, BY=70, INK='none';
const AT=[0,5,15,60,180,480,1440], NAMES=['Seed','Sprout','Two leaves','Young plant','Bud','Flower','Tree'];
const lerp=(a,b,t)=>a+(b-a)*Math.max(0,Math.min(1,t));
const tab=(g,v)=>{const x=Math.max(0,Math.min(v.length-1,g-1)),i=Math.min(v.length-2,Math.floor(x));return lerp(v[i],v[i+1],x-i)};
const el=(n,a,p)=>{const e=document.createElementNS(NS,n);for(const k in a)e.setAttribute(k,a[k]);if(p)p.appendChild(e);return e};
function leaf(parent,x,y,len,angle,cls,dew){if(len<.6)return;const w=len*.42;
 // A leaf in two tones split along the midrib, like a botanical print: light side up, shade side down.
 const g=el('g',{transform:`translate(${x} ${y}) rotate(${angle})`},parent);const f=el('g',{class:cls},g);
 el('path',{d:`M0 0 C${len*.22} ${-w*1.1} ${len*.74} ${-w*.86} ${len} 0 C${len*.74} ${w*.78} ${len*.22} ${w*.86} 0 0Z`,fill:`url(#${CUR})`},f);
 el('path',{d:`M0 0 C${len*.22} ${w*.86} ${len*.74} ${w*.78} ${len} 0 Q${len*.5} ${w*.06} 0 0Z`,fill:'rgba(20,60,20,.32)'},f);
 el('path',{d:`M${len*.04} 0 Q${len*.5} ${w*.04} ${len*.94} 0`,stroke:'rgba(235,255,205,.55)','stroke-width':.35,fill:'none','stroke-linecap':'round'},f);
 if(dew)el('circle',{cx:len*.6,cy:-w*.28,r:len*.07,fill:'rgba(220,240,255,.85)'},f)}


function drawBell(svg){svg.textContent='';const id='b'+(drawBell.n=(drawBell.n||0)+1),d=el('defs',{},svg);
 const lin=(nm,x2,y2,stops)=>{const g=el('linearGradient',{id:id+nm,x1:0,y1:0,x2,y2},d);stops.forEach(([o,c])=>el('stop',{offset:o,'stop-color':c},g))};
 lin('g',1,0,[[0,'rgba(255,255,255,.16)'],[.35,'rgba(255,255,255,.03)'],[.8,'rgba(255,255,255,.05)'],[1,'rgba(255,255,255,.14)']]);
 lin('r',0,1,[[0,'#f4d27a'],[1,'#a26c1c']]);lin('s',0,1,[[0,'#8a8275'],[1,'#3f3a33']]);lin('k',1,1,[[0,'#ffe7a3'],[1,'#b07a22']]);
 const rg=el('radialGradient',{id:id+'w',cx:.5,cy:.62,r:.5},d);el('stop',{offset:0,'stop-color':'rgba(255,214,120,.22)'},rg);el('stop',{offset:1,'stop-color':'rgba(255,214,120,0)'},rg);
 el('ellipse',{cx:52,cy:85.5,rx:30,ry:3,fill:'rgba(0,0,0,.5)'},svg);
 el('path',{d:'M24 73.5 H80 L77.5 82 Q52 86.5 26.5 82Z',fill:`url(#${id}s)`},svg);
 el('ellipse',{cx:52,cy:73.6,rx:28,ry:2.2,fill:'#9c9487'},svg);
 el('path',{d:'M30 70 V44 C30 26 40 16 52 16 C64 16 74 26 74 44 V70Z',fill:`url(#${id}w)`},svg);
 el('path',{d:'M31 70.5 C35 65.5 69 65.5 73 70.5Z',fill:'#3a2515'},svg);
 el('path',{d:'M33 68.6 C40 66.4 64 66.4 71 68.6',stroke:'#5b3b20','stroke-width':.8,fill:'none','stroke-linecap':'round'},svg);
 [[40,68.4,.7],[63,68,.6],[47,69.3,.5],[68,69.4,.45]].forEach(([cx,cy,r])=>el('circle',{cx,cy,r,fill:'#6a4a2c'},svg));
 return {front:(t=svg)=>{svg=t;
  el('path',{d:'M30 70 V44 C30 26 40 16 52 16 C64 16 74 26 74 44 V70',fill:`url(#${id}g)`,stroke:'rgba(255,240,210,.5)','stroke-width':.55},svg);
  el('path',{d:'M34.8 62 V45 C34.8 34 38.5 26 44.5 21',stroke:'rgba(255,255,255,.38)','stroke-width':1.5,fill:'none','stroke-linecap':'round'},svg);
  el('path',{d:'M69.6 50 V58',stroke:'rgba(255,255,255,.22)','stroke-width':1,fill:'none','stroke-linecap':'round'},svg);
  el('rect',{x:28.5,y:69.4,width:47,height:3.4,rx:1.7,fill:`url(#${id}r)`},svg);
  el('rect',{x:30,y:69.8,width:44,height:.7,rx:.35,fill:'rgba(255,245,210,.55)'},svg);
  el('ellipse',{cx:52,cy:16.2,rx:3.6,ry:1.3,fill:`url(#${id}r)`},svg);
  el('circle',{cx:52,cy:12.6,r:2.6,fill:`url(#${id}k)`},svg);el('circle',{cx:51.2,cy:11.8,r:.8,fill:'rgba(255,255,255,.7)'},svg)}}}

let CUR='';function drawPlant(svg,g,sleep,mood){svg.textContent='';
 const defs=el('defs',{},svg),gid='lg'+(drawPlant.n=(drawPlant.n||0)+1),lg=el('linearGradient',{id:gid,x1:0,y1:0,x2:.4,y2:1},defs);
 const dry=mood==='dry';el('stop',{offset:0,'stop-color':dry?'#c9a86a':mood==='droop'?'#b5cf6a':'#a6dd62'},lg);el('stop',{offset:1,'stop-color':dry?'#7a5a2e':mood==='droop'?'#6f8a38':'#4a9238'},lg);
 CUR=gid;if(g<.02)return;const small=(g<1?Math.max(.3,g):1)*1.25;g=Math.max(1,g);
 const outer=el('g',{transform:`translate(${BX} ${BY}) scale(${small}) translate(${-BX} ${-BY})`},svg);
 const sway=el('g',{class:'sway'},outer);
 const h=tab(g,[7,12,19,25,29.5,31]),tip=[BX,BY-h],c=[BX+2.2,BY-h*.55];
 const on=t=>{const a=1-t;return[a*a*BX+2*a*t*c[0]+t*t*tip[0],a*a*BY+2*a*t*c[1]+t*t*tip[1]]};
 const sw=tab(g,[1.3,1.6,2,2.3,2.5,2.6]),d=`M${BX} ${BY} Q${c[0]} ${c[1]} ${tip[0]} ${tip[1]}`;
 el('path',{d,stroke:INK,'stroke-width':sw+1.3,fill:'none','stroke-linecap':'round'},sway);
 el('path',{d,stroke:'#4f8f36','stroke-width':sw*.8,fill:'none','stroke-linecap':'round'},sway);el('path',{d,stroke:'rgba(200,240,160,.45)','stroke-width':sw*.25,fill:'none','stroke-linecap':'round',transform:'translate(-.3 0)'},sway);
 const fold=sleep?22:mood==='droop'?-28:mood==='dry'?-40:0;
 [[.42,1.9,5.2,34],[.66,2.6,5.8,28],[.25,3.3,4.6,40],[.82,4.1,5,24]].forEach(([t,from,size,tilt],i)=>{
  const o=Math.max(0,Math.min(1,(g-from)/.8));if(!o)return;const [x,y]=on(t),len=size*o,lift=i%2?8:0;
  leaf(sway,x,y,len,-180+tilt+lift+fold,'flutL');leaf(sway,x,y,len*.94,-tilt-lift-fold,'flutR')});
 const top=tab(g,[6.2,8.4,8.8,7.6,7,7]),bud=g>=3.8,[lx,ly]=bud?on(.93):tip;
 leaf(sway,lx,ly,top,-150+fold,'flutL');leaf(sway,lx,ly,top*1.04,-32-fold,'flutR',g>=1.4&&!sleep);
 if(g>=5.95){sway.textContent='';const k=.68,P=(dx,dy)=>[BX+dx*k,BY+dy*k];
  const [t1x,t1y]=P(0,-24),[l1x,l1y]=P(-1.6,-10),[l2x,l2y]=P(-1,-18),[r1x,r1y]=P(1,-18),[r2x,r2y]=P(1.8,-10);
  el('path',{d:`M${BX-2.2*k} ${BY} C${l1x} ${l1y} ${l2x} ${l2y} ${t1x} ${t1y} C${r1x} ${r1y} ${r2x} ${r2y} ${BX+2.6*k} ${BY}Z`,fill:'#8a5a2b',stroke:INK,'stroke-width':.9},sway);
  [[0,-38,13],[-10,-30,10],[10,-31,10.5],[-6,-42,9],[7,-43,9],[0,-27,9]].forEach(([dx,dy,r])=>{const [x,y]=P(dx,dy);el('circle',{cx:x,cy:y,r:r*k,fill:`url(#${CUR})`,stroke:INK,'stroke-width':.9},sway)});
  [[-4,-36],[6,-34],[1,-45],[-9,-29],[9,-27]].forEach(([dx,dy])=>{const [x,y]=P(dx,dy);el('circle',{cx:x,cy:y,r:1.1,fill:'#ffdf8a',stroke:INK,'stroke-width':.3},sway)});
  return}
 if(g>=5){const r=tab(g,[0,0,0,0,3.6,5.6]),open=Math.min(1,(g-5)/.7);
  for(let i=0;i<6;i++){const len=r*lerp(.8,1.15,open),w=len*.62*lerp(.35,1,open);
   el('path',{d:`M0 0 C${len*.3} ${-w} ${len*.95} ${-w*.8} ${len} 0 C${len*.95} ${w*.8} ${len*.3} ${w} 0 0Z`,fill:'#f2c14e',stroke:INK,'stroke-width':.7,transform:`translate(${tip[0]} ${tip[1]}) rotate(${i*60-90})`},sway)}
  el('circle',{cx:tip[0],cy:tip[1],r:r*.42,fill:'#e0892b',stroke:INK,'stroke-width':.7},sway)}
 else if(bud){const r=lerp(1.4,3.4,(g-3.8)/1.2);el('ellipse',{cx:tip[0],cy:tip[1]-r*.8,rx:r*1.05,ry:r*1.3,fill:'#f39a54',stroke:INK,'stroke-width':.8},sway)}}

const minutes=g=>{if(g>=6)return 1440;const i=Math.floor(g);return lerp(AT[i],AT[i+1],g-i)};
const fmt=m=>{m=Math.round(m);return{d:Math.floor(m/1440),h:Math.floor(m%1440/60),mm:m%60}};

const CRK='M60 36 L55 30 L52 24 M60 36 L66 31 L70 27 M60 36 L63 43 L61 50 L64 57 M60 36 L54 40 L48 39 L43 44 M60 36 L67 38 L73 42 M55 30 L51 32',
 BRK='M60 36 L58 48 L50 56 M60 36 L72 50';
/** One glass in a 0..100 square: bell, plant, front glass and the state's cracks. */
function drawGlass(svg,g,state){svg.textContent='';const b=drawBell(svg);
 const mood=state==='cracked'?'droop':state==='broken'?'dry':null;
 const p=el('svg',{x:0,y:0,width:100,height:100,viewBox:'0 0 100 100',class:'gplant'},svg);
 drawPlant(p,state==='broken'?Math.min(g,1.6):g,state==='night',mood);b.front();
 if(state==='night'){el('circle',{cx:40,cy:30,r:3.2,fill:'#e9eeff'},svg);el('circle',{cx:41.6,cy:28.8,r:3,fill:'rgba(14,16,40,.95)'},svg)}
 if(state==='cracked'||state==='gold'||state==='broken'){const gold=state==='gold',c=el('g',{class:'crk'+(gold?' gold':'')},svg);
  const d=state==='broken'?CRK+' '+BRK:CRK;
  el('path',{d,fill:'none',stroke:gold?'#7a4a08':'rgba(20,14,8,.7)','stroke-width':gold?.8:.6,'stroke-linecap':'round','stroke-linejoin':'round'},c);
  if(gold){el('path',{d,fill:'none',stroke:'rgba(255,210,90,.35)','stroke-width':1.4,'stroke-linecap':'round'},c);el('path',{d,class:'seam',fill:'none',stroke:'#ffd25a','stroke-width':.45,'stroke-linecap':'round','stroke-dasharray':'4 1.2'},c)}
  else el('path',{d,fill:'none',stroke:'rgba(255,255,255,.85)','stroke-width':.3,'stroke-linecap':'round'},c);
  if(state==='broken')['M30 74 l5 -3 l2 4z','M66 75 l6 -2 l-1 4z','M44 76 l3 -4 l3 3z','M58 77 l4 -3 l1 3z'].forEach(d=>el('path',{d,fill:'rgba(220,230,240,.55)',stroke:'rgba(255,255,255,.8)','stroke-width':.2},c))}}

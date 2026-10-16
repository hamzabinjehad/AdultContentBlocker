(() => {
  if (globalThis.HisnVisualEffects) return;
  let sequence = 0;
  const namespace = 'http://www.w3.org/2000/svg';
  function apply(node, regions, {personRegion='full',personEffect='solid'} = {}) {
    // Moving faces need frame-by-frame tracking. Until that is verified, keep
    // video protection whole-media rather than place a stale face mask.
    if (node.tagName !== 'IMG') personRegion = 'full';
    if (/(\.gif([?#]|$)|^data:image\/gif)/i.test(node.currentSrc||node.src||'')) personRegion='full';
    const before=node.style.getPropertyValue('filter'),priority=node.style.getPropertyPriority('filter');
    const previous=getComputedStyle(node).filter;
    const combine=effect=>previous && previous!=='none' ? `${previous} ${effect}` : effect;
    const restore=value=>{if(node.style.getPropertyValue('filter')===value){if(before)node.style.setProperty('filter',before,priority);else node.style.removeProperty('filter');}};
    if (personRegion === 'full') {
      if (personEffect !== 'blur') return null;
      const value=combine('blur(24px)');
      node.style.setProperty('filter',value,'important');
      return {valid:()=>node.style.getPropertyValue('filter')===value,dispose(){restore(value);}};
    }
    if (!Array.isArray(regions) || !regions.length || regions.length > 10) return null;
    function geometry() {
      const style=getComputedStyle(node);
      if (['paddingTop','paddingBottom','paddingLeft','paddingRight','borderTopWidth','borderBottomWidth','borderLeftWidth','borderRightWidth'].some(key=>parseFloat(style[key])>0)) return null;
      const w=node.clientWidth,h=node.clientHeight,nw=node.naturalWidth,nh=node.naturalHeight;
      if (!(w>0&&h>0&&nw>0&&nh>0)) return null;
      let width=w,height=h,x=0,y=0;
      if(style.objectFit!=='fill') {
        if(!['contain','cover'].includes(style.objectFit)) return null;
        // Only explicit percentage positions are mapped; never guess calc(),
        // pixel offsets or edge-offset syntax.
        const parts=style.objectPosition.trim().split(/\s+/);
        if(parts.length!==2||!parts.every(p=>/^-?\d+(?:\.\d+)?%$/.test(p)))return null;
        const scale=(style.objectFit==='contain'?Math.min:Math.max)(w/nw,h/nh);
        width=nw*scale;height=nh*scale;
        x=(w-width)*parseFloat(parts[0])/100;y=(h-height)*parseFloat(parts[1])/100;
      }
      return {w,h,width,height,x,y,key:JSON.stringify([w,h,nw,nh,style.objectFit,style.objectPosition])};
    }
    const layout=geometry();
    if(!layout)return null;
    const validGeometry=()=>geometry()?.key===layout.key;
    const boxes=[];
    for (const r of regions) {
      if (!r || ![r.x,r.y,r.width,r.height].every(Number.isFinite)
        || r.x<0 || r.y<0 || r.width<=0 || r.height<=0 || r.x+r.width>1.001 || r.y+r.height>1.001) return null;
      const x=Math.max(0,layout.x+Math.max(0,r.x-r.width*.12)*layout.width);
      const y=Math.max(0,layout.y+Math.max(0,r.y-r.height*.12)*layout.height);
      const right=Math.min(layout.w,layout.x+Math.min(1,r.x+r.width*1.12)*layout.width);
      const bottom=Math.min(layout.h,layout.y+Math.min(1,r.y+r.height*1.12)*layout.height);
      if(right<=x||bottom<=y)return null;
      boxes.push({x:x/layout.w,y:y/layout.h,width:(right-x)/layout.w,height:(bottom-y)/layout.h});
    }
    const svg=document.createElementNS(namespace,'svg'),defs=document.createElementNS(namespace,'defs');
    svg.setAttribute('aria-hidden','true');svg.setAttribute('width','0');svg.setAttribute('height','0');
    svg.style.cssText='position:absolute;pointer-events:none;overflow:hidden';
    const filter=document.createElementNS(namespace,'filter'),id=`hisn_face_${Date.now()}_${++sequence}`;
    for (const [key,value] of Object.entries({id,filterUnits:'objectBoundingBox',primitiveUnits:'objectBoundingBox',x:0,y:0,width:1,height:1})) filter.setAttribute(key,String(value));
    function primitive(name,attrs){const el=document.createElementNS(namespace,name);for(const [k,v] of Object.entries(attrs))el.setAttribute(k,String(v));filter.append(el);return el;}
    boxes.forEach((box,i)=>primitive('feFlood',{...box,'flood-color':'black',result:`face_${i}`}));
    const mask=primitive('feMerge',{result:'mask'});
    boxes.forEach((_,i)=>{const item=document.createElementNS(namespace,'feMergeNode');item.setAttribute('in',`face_${i}`);mask.append(item);});
    primitive('feComposite',{in:'SourceGraphic',in2:'mask',operator:'out',result:'outside'});
    let protectedResult='mask';
    if(personEffect==='blur'){
      primitive('feGaussianBlur',{in:'SourceGraphic',stdDeviation:.07,result:'blurred'});
      primitive('feComposite',{in:'blurred',in2:'mask',operator:'in',result:'covered'});protectedResult='covered';
    }
    const merge=primitive('feMerge',{});
    for(const value of ['outside',protectedResult]){const item=document.createElementNS(namespace,'feMergeNode');item.setAttribute('in',value);merge.append(item);}
    defs.append(filter);svg.append(defs);(document.body||document.documentElement).append(svg);
    const value=combine(`url("${location.href.split('#')[0].replaceAll('"','%22')}#${id}")`);
    node.style.setProperty('filter',value,'important');
    return {valid:()=>svg.isConnected&&node.style.getPropertyValue('filter')===value&&validGeometry(),dispose(){restore(value);svg.remove();}};
  }
  globalThis.HisnVisualEffects={apply};
})();

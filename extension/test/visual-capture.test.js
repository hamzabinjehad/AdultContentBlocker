// Readiness probes do not classify pixels or certify a pending image reload.
let checks=0;
const check=(ok,label)=>{checks++;if(!ok)throw Error(label)};
delete globalThis.HisnVisualCapture;
load('../visual/capture.js');
const image={complete:true,naturalWidth:32,naturalHeight:32};
function fixture({denied=false,missingContext=false}={}){
  const canvases=[],draws=[],buffers=[];
  const doc={createElement(){
    const canvas={width:0,height:0,getContext(){return missingContext?null:{
      drawImage(...args){draws.push(args)},
      getImageData(_x,_y,w,h){if(denied)throw Error('SecurityError');const data=new Uint8ClampedArray(w*h*4).fill(127);buffers.push(data);return{data}}
    }}};canvases.push(canvas);return canvas;
  }};return{doc,canvases,draws,buffers};
}
{
  const h=fixture();
  check(HisnVisualCapture.capture({...image,complete:false},h.doc).reason==='media-not-ready'
    && HisnVisualCapture.probe({...image,complete:false},h.doc).reason==='media-not-ready'
    && h.canvases.length===0, 'an incomplete same-node reload cannot submit retained older pixels or even probe them');
  check(HisnVisualCapture.probe({...image,naturalWidth:0},h.doc).reason==='media-not-ready',
    'a missing decoded image has no invented readable state');
}
{
  const h=fixture(), result=HisnVisualCapture.probe(image,h.doc);
  check(result.state==='readable' && !('rgba' in result), 'a readable readiness probe is not a model capture or a clean content verdict');
  check(h.draws[0][0]===image && h.draws[0].slice(1).join(',')==='0,0,1,1',
    'readiness uses only one pixel of the actual image node');
  check(h.buffers[0].every(v=>v===0) && h.canvases[0].width===0 && h.canvases[0].height===0,
    'readiness buffers and temporary canvas are released');
}
for(const [options,reason] of [[{denied:true},'inaccessible-pixels'],[{missingContext:true},'canvas-unavailable']]){
  const h=fixture(options), result=HisnVisualCapture.probe(image,h.doc);
  check(result.state==='unknown' && result.reason===reason && h.canvases[0].width===0,
    'an unavailable or tainted readiness probe stays unknown and releases the canvas');
}
{
  const h=fixture(), result=HisnVisualCapture.capture(image,h.doc);
  check(result.width===32 && result.height===32 && atob(result.rgba).length===32*32*4,
    'actual inspection still captures the full bounded decoded image, not the readiness pixel');
  check(h.buffers[0].every(v=>v===0) && h.canvases[0].width===0,
    'full capture still clears temporary pixel buffers');
}
print(`  ${checks}/${checks} visual capture readiness checks passed`);

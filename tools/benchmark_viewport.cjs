/* Measure the real coarse + detailed worker pipeline, including HTTP and composition. */
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const preview=path.resolve(__dirname,'../../WorldGen Editor Preview');
global.WgeMask=require(path.join(preview,'mask-core.js'));
const {create}=require(path.join(preview,'render-core.js'));
const document=JSON.parse(fs.readFileSync(process.argv[2]||path.resolve(__dirname,'../common/src/main/resources/assets/worldgen_editor/presets/default.json'),'utf8').replace(/^\uFEFF/,''));
if(process.argv[4])(document.config||document)._benchmark_run=process.argv[4];
const config=JSON.stringify(document);
const seed=process.argv[3]||'874321098';
const forceHeights=process.env.WGE_BENCH_FORCE_HEIGHTS==='1';
const endpoint=process.env.WGE_BENCH_URL||'http://127.0.0.1:8765';
const cssWidth=Number(process.env.WGE_BENCH_WIDTH||850),cssHeight=Number(process.env.WGE_BENCH_HEIGHT||720);
const quality=Number(process.env.WGE_BENCH_QUALITY||.65),blocksPerPixel=Number(process.env.WGE_BENCH_BPP||6.27);
if(![cssWidth,cssHeight,quality,blocksPerPixel].every(n=>Number.isFinite(n)&&n>0))throw new Error('Invalid viewport benchmark dimensions');
const realFetch=global.fetch,requests=[];
const start=performance.now();let firstTileMs=null,firstFrameMs=null;
global.fetch=async(url,options)=>{
  const begun=performance.now();
  if(url==='/api/tile'&&forceHeights)options={...options,body:JSON.stringify({...JSON.parse(options.body),include_heights:true})};
  const response=await realFetch(endpoint+url,options);
  const value=await response.json();
  if(url==='/api/tile'){
    firstTileMs??=performance.now()-start;
    requests.push({wall_ms:performance.now()-begun,java_ms:value.elapsed_ms,skipped_heights:value.heights?.filter(y=>y===-32768).length||0});
  }
  return {ok:response.ok,json:async()=>value};
};
(async()=>{
  const renderer=create(),message={id:1,configText:config,seedText:seed,source:'biomes',backendAvailable:true,
    heightMode:'surface',quality,width:Math.floor(cssWidth*quality),height:Math.floor(cssHeight*quality),cssWidth,cssHeight,
    view:{x:-13.11,z:113.11,blocksPerPixel}};
  const frame=()=>{firstFrameMs??=performance.now()-start;};
  const coarse=await renderer.render({...message,samplingQuality:32/Math.max(cssWidth,cssHeight)},()=>true,{onProgress:frame});
  frame();const coarseMs=performance.now()-start;
  const fine=await renderer.render(message,()=>true,{preview:coarse,onProgress:frame});
  const fullMs=performance.now()-start;
  const beforeCached=requests.length,cachedStart=performance.now();
  await renderer.render(message,()=>true);
  const wall=requests.map(r=>r.wall_ms).sort((a,b)=>a-b);
  const percentile=q=>wall[Math.min(wall.length-1,Math.floor(wall.length*q))];
  console.log(JSON.stringify({seed,force_heights:forceHeights,viewport:`${cssWidth}x${cssHeight}, quality ${quality}, ${blocksPerPixel} blocks/pixel`,
    first_tile_ms:firstTileMs,first_visible_frame_ms:firstFrameMs,coarse_complete_ms:coarseMs,
    full_view_ms:fullMs,cached_view_ms:performance.now()-cachedStart,cached_requests:requests.length-beforeCached,
    tile_requests:beforeCached,tile_wall_p50_ms:percentile(.5),tile_wall_p95_ms:percentile(.95),tile_wall_max_ms:wall.at(-1),
    java_max_ms:Math.max(...requests.map(r=>r.java_ms||0)),
    skipped_height_samples:requests.reduce((sum,r)=>sum+r.skipped_heights,0),
    biomes_sha256:crypto.createHash('sha256').update(Buffer.from(fine.biomesBuffer)).update(JSON.stringify(fine.palette)).digest('hex'),
    biome_height_sha256:crypto.createHash('sha256').update(Buffer.from(fine.biomesBuffer)).update(Buffer.from(fine.heightsBuffer)).update(JSON.stringify(fine.palette)).digest('hex')},null,2));
})().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{global.fetch=realFetch;});

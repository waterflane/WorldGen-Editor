/* Measure real API latency and cancellation, including session preparation. */
const fs=require('node:fs'),path=require('node:path');
const base=(process.env.WGE_PREVIEW_URL||'http://127.0.0.1:8875')+'/api/',profile='supported-combined',client='modded-benchmark-'+Date.now();
async function post(route,body){const started=performance.now(),response=await fetch(base+route,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...body,profile})}),value=await response.json();return {status:response.status,value,wall_ms:performance.now()-started};}
(async()=>{
 const started=performance.now(),catalog=await(await fetch(base+'catalog?profile='+profile)).json(),ready=performance.now()-started;
 const config=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../common/src/test/resources/natural-coast-repro.json'),'utf8')).config;config.name=client;
 const session=await post('session',{seed:'3',generation_version:5,coast_revision:3,expected_resources:catalog.resources,config,client});if(session.status!==200)throw new Error(JSON.stringify(session.value));
 const query={session:session.value.session,client,request:1,x:-256,z:-256,size:8,step:64,height_mode:'surface',include_heights:true};
 const first=await post('tile',query),repeat=await post('tile',query);if(first.status!==200||repeat.status!==200||JSON.stringify(first.value.biomes)!==JSON.stringify(repeat.value.biomes)||JSON.stringify(first.value.heights)!==JSON.stringify(repeat.value.heights))throw new Error('Tile cache mismatch');
 const dense=await post('tile',{...query,request:2,x:99,z:-20,size:16,step:1});if(dense.status!==200)throw new Error(JSON.stringify(dense.value));
 const pending=post('tile',{...query,request:3,x:328,z:-168,size:32,step:1});await new Promise(r=>setTimeout(r,20));const cancelStart=performance.now();
 await post('cancel',{session:session.value.session,client,request:4});const old=await pending,cancel=performance.now()-cancelStart;
 if(old.status!==200&&!old.value.cancelled)throw new Error(JSON.stringify(old));
 const following=await post('tile',{...query,request:5,size:1,x:328,z:-168});if(following.status!==200)throw new Error('Cancelled work poisoned the next tile');
 const info=await(await fetch(base+'profiles/runtime')).json();if(info.heap_limit_mib!==2048)throw new Error('Unexpected modded heap');
 const result={created_utc:new Date().toISOString(),profile,resources:catalog.resources,profile_ready_ms:ready,session_ms:session.wall_ms,first_coarse_8x8_ms:first.wall_ms,cached_8x8_ms:repeat.wall_ms,dense_16x16_ms:dense.wall_ms,cancellation_ms:cancel,cancelled:!!old.value.cancelled,followup_after_cancel:true,runtime:info};
 fs.writeFileSync(path.resolve(__dirname,'../build/modded-preview-performance.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
})().catch(e=>{console.error(e);process.exitCode=1});

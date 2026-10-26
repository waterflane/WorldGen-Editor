/* Local HTTP/worker checks; never reads or changes user saves. */
const fs=require('node:fs'),path=require('node:path');
const base='http://127.0.0.1:8765/api/',client='coast-performance-'+Date.now();
async function post(route,body){const start=performance.now(),response=await fetch(base+route,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});return {status:response.status,value:await response.json(),wall_ms:performance.now()-start};}
(async()=>{
 const info=await (await fetch(base+'info')).json();if(!info.capabilities.includes('coast_profile_v1'))throw new Error('Prepare the updated Java preview first');
 const config={name:'Coast benchmark '+client,enabled:true,entries:[{name:'Performance',x:0,z:0,radius:220,roughness:.12,coast:{beach_width_blocks:32,shallow_ocean_width_blocks:192}},{type:'ocean',x:0,z:0,radius:65,roughness:0}]};
 const session=await post('session',{seed:'2',generation_version:4,config,client});if(session.status!==200)throw new Error(JSON.stringify(session.value));
 const query={session:session.value.session,client,request:1,x:-256,z:-256,size:16,step:32,height_mode:'surface',include_heights:false};
 const first=await post('tile',query),repeat=await post('tile',{...query,request:2});
 if(first.status!==200||JSON.stringify(first.value.palette)!==JSON.stringify(repeat.value.palette)||JSON.stringify(first.value.biomes)!==JSON.stringify(repeat.value.biomes))throw new Error('Tile cache changed the result');
 const changed=structuredClone(config);changed.entries[0].coast.shallow_ocean_width_blocks=256;
 const next=await post('session',{seed:'2',generation_version:4,config:changed,client});if(next.value.session===session.value.session)throw new Error('Coast width change reused the old session');
 const large={name:'Coast cancellation '+client,enabled:true,entries:[{x:0,z:0,radius:5000,roughness:.18,coast:{beach_width_blocks:16,shallow_ocean_width_blocks:4096}}]};
 const slow=await post('session',{seed:'2',generation_version:4,config:large,client});
 const pending=post('tile',{...query,session:slow.value.session,x:8800,z:0,step:1,size:32});
 await new Promise(resolve=>setTimeout(resolve,20));const cancelStart=performance.now();
 await post('cancel',{session:slow.value.session,client,request:2});const cancelled=await pending,cancel_ms=performance.now()-cancelStart;
 if(cancelled.status!==200&&!cancelled.value.cancelled)throw new Error('Unexpected cancellation response');
 if(cancelled.value.cancelled&&cancel_ms>100)throw new Error('Java coast cancellation exceeded 100 ms: '+cancel_ms);
 const followup=await post('tile',{...query,session:slow.value.session,x:8800,z:0,step:1,size:1,request:3});if(followup.status!==200)throw new Error('Cancellation poisoned the next tile');
 const core=require(path.resolve(__dirname,'../../WorldGen Editor Preview/mask-core.js')),compiled=core.parseConfig(large,2n);let active=true;
 const jsStart=performance.now();await core.sampleMaskAsync(large,compiled,8800,0,{},()=>active,async()=>{active=false;await new Promise(setImmediate);});const jsCancel=performance.now()-jsStart;
 if(jsCancel>100)throw new Error('Worker coast cancellation exceeded 100 ms');
 const result={date:'2026-10-26',profile:'Minecraft + WGE',session_ms:session.wall_ms,first_coarse_tile_ms:first.wall_ms,repeat_tile_ms:repeat.wall_ms,java_cancel_ms:cancel_ms,java_cancelled:!!cancelled.value.cancelled,worker_cancel_ms:jsCancel,changed_width_new_session:true,followup_after_cancel:true,heap_limit_mib:512,heap_used_mib:(await (await fetch(base+'info')).json()).heap_used_mib};
 fs.writeFileSync(path.resolve(__dirname,'../build/coast-performance.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
})().catch(error=>{console.error(error);process.exitCode=1});

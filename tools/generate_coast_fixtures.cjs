const fs=require('node:fs'),path=require('node:path');
const preview=path.resolve(__dirname,'../../WorldGen Editor Preview'),core=require(path.join(preview,'mask-core.js')),{Model}=require(path.join(preview,'editor-model.js'));
(async()=>{
 const cases=[],add=(name,config)=>{
  config=structuredClone({...config,enabled:true,generation_version:4});const compiled=core.parseConfig(config,2n),points=[];
  for(let z=-560.75;z<=560;z+=79.25)for(let x=-560.25;x<=560;x+=73.5)points.push([x,z,core.sampleMask(config,compiled,x,z)]);
  for(const [x,z]of [[0,0],[144,0],[160,0],[200,0],[280,0],[320,0],[-256,-256],[4000,0],[470,0]])points.push([x,z,core.sampleMask(config,compiled,x,z)]);
  cases.push({name,seed:'2',config,points});
 };
 const profile=(b,s)=>({beach_width_blocks:b,shallow_ocean_width_blocks:s});
 for(const width of [0,16,128,4096])add(`circle-${width}`,{entries:[{x:0,z:0,radius:160,roughness:0,coast:profile(width,width)}]});
 add('noisy-rotated-negative',{entries:[{x:-256,z:-257,radius_x:220,radius_z:130,rotation:37,roughness:.4,coast:profile(32,128)}]});
 add('merged-islands',{entries:[{x:-80,z:0,radius:160,coast:profile(16,128)},{x:80,z:0,radius:160,coast:profile(64,256)}]});
 add('archipelago-cutout',{entries:[{name:'Group',type:'archipelago',x:0,z:0,radius:500,count:4,min_radius:80,max_radius:120,coast:profile(16,128)},{type:'ocean',x:0,z:0,radius:100}]});
 const m=new Model({entries:[]},'2');
 for(const [tool,radius]of [['land',160],['ocean',48]]){m.begin({tool,radius,roughness:.35});await m.point(-256,-256);m.end();}
 await m.selectPaint(-336,-256);const index=await m.convertPaint();m.changeEntry(index,{coast:profile(32,128)});add('outlined-hole',m.preset());
 m.changeEntry(index,{x:256,z:128,radius_x:300,radius_z:180,rotation:37,temperature:'warm'});add('transformed-outline',m.preset());
 const copy=m.duplicateEntry(index);m.changeEntry(copy,{x:320,z:96,coast:profile(16,256),temperature:'cold'});add('overlapping-outlines',m.preset());
 add('overlapping-shelves',{entries:[{x:0,z:0,radius:160,roughness:0,coast:profile(16,512)},{x:600,z:0,radius:80,roughness:0,coast:profile(16,16)}]});
 const inherited=JSON.parse(fs.readFileSync(path.resolve(__dirname,'../common/src/test/resources/island-outlines.json'),'utf8'))[1].config;
 inherited.entries[0].temperature='warm';inherited.entries[1].coast=profile(32,128);add('outline-over-warm-base',inherited);
 delete inherited.entries[1].coast;inherited.entries[0].coast=profile(16,128);add('legacy-outline-over-profiled-base',inherited);
 fs.writeFileSync(path.resolve(__dirname,'../common/src/test/resources/coast-profiles.json'),JSON.stringify(cases)+'\n');
 console.log(`${cases.length} cases / ${cases.reduce((n,c)=>n+c.points.length,0)} coast samples`);
})().catch(e=>{console.error(e);process.exitCode=1});

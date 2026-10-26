/* Cross-language fixtures: retain original painted values, then transform/overlap objects. */
const path=require('node:path'),fs=require('node:fs');
const preview=path.resolve(__dirname,'../../WorldGen Editor Preview');
const {Model}=require(path.join(preview,'editor-model.js')),core=require(path.join(preview,'mask-core.js'));
async function stroke(model,tool,x,z,radius,roughness=.35){model.begin({tool,radius,roughness});await model.point(x,z);model.end();}
(async()=>{
 const model=new Model({enabled:true,entries:[{name:'Base',x:-257,z:-257,radius:180,roughness:.2,temperature:'standard'}]},'2'),cases=[];
 await stroke(model,'land',-257,-257,160);await stroke(model,'ocean',-257,-257,32,0);await stroke(model,'land',800,800,64);
 const add=name=>{const config=model.preset(),compiled=core.parseConfig(config,2n),points=[];
  for(let x=-641.25;x<=1100;x+=101.5)for(let z=-639.75;z<=1100;z+=99.25)points.push([x,z,core.sampleMask(config,compiled,x,z)]);
  for(const [x,z]of [[-257,-257],[-545,-419],[-417,-257],[-96,-257],[0,0],[512,-128],[800,800]])points.push([x,z,core.sampleMask(config,compiled,x,z)]);
  cases.push({name,seed:'2',config,points});};
 add('original-paint');await model.selectPaint(-337,-257);const index=await model.convertPaint();add('converted-exact');
 model.changeEntry(index,{x:512,z:-128,radius_x:330,radius_z:150,rotation:37,temperature:'warm'});add('transformed');
 const other=model.duplicateEntry(index);model.changeEntry(other,{x:560,z:-80,rotation:-23,temperature:'cold'});add('overlapping-outlines');
 const output=path.resolve(__dirname,'../common/src/test/resources/island-outlines.json');fs.writeFileSync(output,JSON.stringify(cases)+'\n');
 console.log(`${cases.length} cases, ${cases.reduce((n,c)=>n+c.points.length,0)} shared outline samples`);
})().catch(error=>{console.error(error);process.exitCode=1});

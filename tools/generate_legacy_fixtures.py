"""Regenerate golden masks from the last pre-fix commit, without building that checkout."""
import json, pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[1]
cache=pathlib.Path.home()/'.gradle/caches/modules-2/files-2.1'
jdk=pathlib.Path.home()/'.jdks/corretto-22.0.2/bin'
cp=';'.join(str(next(p for p in (cache/sub).rglob('*.jar') if 'sources' not in p.name and 'javadoc' not in p.name)) for sub in ['com.google.code.gson/gson/2.10.1','org.slf4j/slf4j-api/2.0.9'])
scratch=root/'build/legacy-fixtures';scratch.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(dir=scratch) as td:
 t=pathlib.Path(td).resolve();assert t.is_relative_to(scratch.resolve())
 files=[]
 for name in subprocess.check_output(['git','ls-tree','-r','--name-only','c792da7','common/src/main/java'],cwd=root,text=True).splitlines():
  if '/config/' in name or name.endswith('/IslandMask.java'):
   dest=t/pathlib.Path(name).name;dest.write_bytes(subprocess.check_output(['git','show','c792da7:'+name],cwd=root));files.append(str(dest))
 stub=t/'Worldgen_editor.java';stub.write_text('package org.wodichka.worldgen_editor;public class Worldgen_editor{public static final String MOD_ID="worldgen_editor";}');files.append(str(stub))
 helper=t/'Golden.java';helper.write_text('''package org.wodichka.worldgen_editor.config;
import com.google.gson.*;import org.wodichka.worldgen_editor.world.*;import java.nio.file.*;import java.util.*;
public class Golden{public static void main(String[]a)throws Exception{var output=new ArrayList<Object>();for(String n:List.of("default","archipelago","small_island")){var raw=JsonParser.parseString(Files.readString(Path.of(a[0],n+".json")));for(long seed:new long[]{0L,12345L,Long.MIN_VALUE}){var mask=new IslandMask(IslandConfigLoader.parse(raw),seed);var points=new ArrayList<Object>();var random=new Random(117);int radius=n.equals("small_island")?500:2200;for(int i=0;i<160;i++){int x=random.nextInt(radius*2)-radius,z=random.nextInt(radius*2)-radius;points.add(List.of(x,z,mask.sample(x,z)));}output.add(Map.of("preset",n,"config",raw,"seed",Long.toString(seed),"points",points));}}System.out.print(new Gson().toJson(output));}}''');files.append(str(helper))
 subprocess.run([str(jdk/'javac.exe'),'-cp',cp,'-d',td,*files],check=True,cwd=root)
 result=subprocess.run([str(jdk/'java.exe'),'-cp',cp+';'+td,'org.wodichka.worldgen_editor.config.Golden',str(root/'common/src/main/resources/assets/worldgen_editor/presets')],check=True,capture_output=True,cwd=root)
 out=root/'common/src/test/resources/legacy-mask.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(result.stdout)
 print('Golden samples:',sum(len(v['points']) for v in json.loads(result.stdout)))

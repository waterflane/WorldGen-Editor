"""Exercise the folder importer with a disposable instance, never a user installation."""
from pathlib import Path
import hashlib, json, os, shutil, time, urllib.error, urllib.request, uuid, zipfile

root=Path(__file__).resolve().parents[1]
base=os.environ.get('WGE_PREVIEW_URL','http://127.0.0.1:8875')
source=root/'build'/('import-fixture-'+str(uuid.uuid4()))
(source/'mods').mkdir(parents=True)
lock=json.loads((root/'tools/compatibility-lock.json').read_text(encoding='utf-8'))
for artifact in lock:
    if artifact['project'] in ('biomes-o-plenty','glitchcore','terrablender'):
        shutil.copyfile(root/'neoforge/build/compatibility-libs'/artifact['filename'],source/'mods'/artifact['filename'])
release=root/'release/worldgen-editor-neoforge-0.4.0.jar'
if release.exists(): shutil.copyfile(release,source/'mods'/release.name)
(source/'config').mkdir();(source/'config/worldgen-sentinel.json').write_text('{"sentinel":42}',encoding='utf-8')
(source/'saves').mkdir();(source/'saves/DONT_COPY').write_text('not a user save',encoding='utf-8')
(source/'accounts.json').write_text('do not import',encoding='utf-8');(source/'options.txt').write_text('do not import',encoding='utf-8')
(source/'modrinth.index.json').write_text(json.dumps({'dependencies':{'minecraft':'1.21.1','neoforge':'21.1.230'}}),encoding='utf-8')
with zipfile.ZipFile(source/'mods/client-fixture.jar','w') as jar:
    jar.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\nclientSideOnly=true\n[[mods]]\nmodId="client_fixture"\nversion="1.0.0"\n')
pack=source/'datapacks/marker';(pack/'data/import_fixture/worldgen/biome').mkdir(parents=True)
(pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'Importer verification'}}),encoding='utf-8')
classpath=(root/'preview-engine/build/runtime/java.args').read_text(encoding='utf-8').splitlines()[2].strip('"').split(';')
for file in classpath:
    if not file.endswith('.jar'): continue
    with zipfile.ZipFile(file) as jar:
        if 'data/minecraft/worldgen/biome/plains.json' in jar.namelist():
            (pack/'data/import_fixture/worldgen/biome/marker.json').write_bytes(jar.read('data/minecraft/worldgen/biome/plains.json'));break
else: raise AssertionError('Prepared vanilla biome fixture is missing')

def hashes(): return {str(p.relative_to(source)):hashlib.sha256(p.read_bytes()).hexdigest() for p in source.rglob('*') if p.is_file()}
def api(path,body=None):
    request=urllib.request.Request(base+'/api/'+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=240) as response:return json.load(response)
def operation(body,cancel=False):
    job=api('profiles/prepare',body)['job']
    if cancel:api('profiles/cancel',{'job':job})
    deadline=time.monotonic()+900
    while time.monotonic()<deadline:
        state=api('profiles/status?id='+job)
        if state['state'] in ('ready','failed','cancelled'):return state
        time.sleep(.5)
    raise TimeoutError('Import did not complete')

for attempt in range(80):
    try:api('info');break
    except urllib.error.URLError:time.sleep(.25)
else:raise RuntimeError('Local preview did not start')
before=hashes();start=time.monotonic();ready=operation({'path':str(source),'heap_mib':2048})
assert ready['state']=='ready',ready
assert hashes()==before,'Importer modified its source'
profile=ready['profile'];profile_id=profile['id'];manifest_file=root/'build/preview-profiles'/profile_id/'profile.json'
manifest=json.loads(manifest_file.read_text(encoding='utf-8'));snapshot=Path(manifest['snapshot'])
assert (snapshot/'config/worldgen-sentinel.json').read_text(encoding='utf-8')=='{"sentinel":42}'
assert not any((snapshot/name).exists() for name in ('saves','accounts.json','options.txt'))
assert any(m.get('id')=='client_fixture' for m in profile['excluded'])
assert not (snapshot/'mods/client-fixture.jar').exists()
assert any(b['id']=='import_fixture:marker' for b in ready['catalog']['biomes']),'Root datapack was not loaded'
assert ready['catalog']['accuracy']=='unverified'
api('profiles/cancel',{'job':ready['job']});assert api('profiles/status?id='+ready['job'])['state']=='ready','Completed import was cancelled retroactively'
old_manifest=manifest_file.read_bytes()
with zipfile.ZipFile(source/'mods/fabric-fixture.jar','w') as jar:jar.writestr('fabric.mod.json','{"id":"fabric_fixture","version":"1"}')
before_failure=hashes();failed=operation({'profile':profile_id,'heap_mib':2048})
assert failed['state']=='failed' and 'Fabric' in failed['error'],failed
assert manifest_file.read_bytes()==old_manifest,'Failed reload replaced the profile'
assert hashes()==before_failure,'Failed reload modified its source'
cancelled=operation({'profile':profile_id,'heap_mib':2048},cancel=True)
assert cancelled['state']=='cancelled',cancelled
assert manifest_file.read_bytes()==old_manifest,'Cancelled reload replaced the profile'
catalog=api('catalog?profile='+profile_id)
assert catalog['resources']==ready['catalog']['resources'],'Restoring the original profile changed its fingerprint'
old_fingerprint=catalog['resources']
(source/'mods/fabric-fixture.jar').unlink()
(source/'config/worldgen-sentinel.json').write_text('{"sentinel":43}',encoding='utf-8')
changed_source=hashes();updated=operation({'profile':profile_id,'heap_mib':2048})
assert updated['state']=='ready',updated
assert updated['catalog']['resources']!=old_fingerprint,'Config update reused the previous fingerprint'
assert hashes()==changed_source,'Successful reload modified the original instance'
try:
    api('session',{'profile':profile_id,'expected_resources':old_fingerprint,'seed':'2','generation_version':5,'config':{'enabled':True,'entries':[]}})
    raise AssertionError('Obsolete resources were accepted')
except urllib.error.HTTPError as error:
    assert error.code==400 and 'resources changed' in json.load(error)['error']
result={'profile':profile_id,'source_unchanged':True,'excluded_client_mod':True,'datapack_loaded':True,'private_files_not_copied':True,
        'failed_reload_preserved_profile':True,'cancelled_reload_preserved_profile':True,'restored_fingerprint':True,'changed_config_new_fingerprint':True,'stale_resources_rejected':True,'completed_import_cannot_be_cancelled':True,'import_seconds':round(time.monotonic()-start,2)}
(root/'build/modded-import-verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result),flush=True)

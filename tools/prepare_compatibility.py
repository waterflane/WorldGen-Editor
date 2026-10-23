"""Install only the locked test artifacts; verify author hashes, including embedded libraries."""
from pathlib import Path
import hashlib,json,subprocess,zipfile

root=Path(__file__).resolve().parents[1]
output=root/'neoforge/build/compatibility-libs'
output.mkdir(parents=True,exist_ok=True)
lock=json.loads((root/'tools/compatibility-lock.json').read_text(encoding='utf-8-sig'))
for item in lock:
    target=output/item['filename']
    if not target.exists():
        if 'embedded_in' in item:
            parent=next(entry for entry in lock if entry['project']==item['embedded_in'])
            with zipfile.ZipFile(output/parent['filename']) as archive:target.write_bytes(archive.read(item['entry']))
        else:
            path=str(target).replace("'","''")
            subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command',
                f"$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing -Uri '{item['url']}' -OutFile '{path}'"],
                check=True,creationflags=subprocess.CREATE_NO_WINDOW)
    if hashlib.sha512(target.read_bytes()).hexdigest()!=item['sha512']:raise RuntimeError('Checksum mismatch: '+item['filename'])
    print(item['project']+' '+item['version'],flush=True)

# Loom strips nested jars from remapped mods. Give the unchanged JSON5 classes a
# test-only GAMELIBRARY manifest so NeoForge loads their complete module together.
with zipfile.ZipFile(output/'json5-java-3.0.0.jar') as source,zipfile.ZipFile(output/'json5-java-3.0.0-gamelibrary.jar','w') as target:
    for entry in source.infolist():
        if entry.filename!='META-INF/MANIFEST.MF':target.writestr(entry,source.read(entry.filename))
    target.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\r\nFMLModType: GAMELIBRARY\r\nAutomatic-Module-Name: wge.compat.json5\r\n\r\n')

"""Download the optional verification-only TerraBlender artifact from the author's Modrinth release."""
from pathlib import Path
import hashlib, os, subprocess, urllib.error, urllib.request
root=Path(__file__).resolve().parents[1]
url='https://cdn.modrinth.com/data/kkmrDlKT/versions/6e8GCrLb/TerraBlender-neoforge-1.21.1-4.1.0.8.jar'
expected='9d4b2a1be5139c0fb2fad92ed21805b17d9e83b6ea48e637e018bb14063c1823a206390755dbfe8d025c20fd62ac11cdd84db53ddb956dabaeda01bff57bac50'
out=root/'neoforge/build/verification-libs/TerraBlender-neoforge-1.21.1-4.1.0.8.jar'
if out.exists(): data=out.read_bytes()
else:
 request=urllib.request.Request(url,headers={'User-Agent':'WorldGenEditor-CompatibilityTest/0.3.0'})
 try:
  with urllib.request.urlopen(request,timeout=60) as response:data=response.read()
 except urllib.error.URLError:
  if os.name!='nt':raise
  # Windows PowerShell uses the system certificate/proxy store; keep TLS validation enabled.
  out.parent.mkdir(parents=True,exist_ok=True)
  temporary=out.with_suffix('.download')
  target=str(temporary).replace("'","''")
  try:
   subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command',
    f"$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing -Uri '{url}' -OutFile '{target}'"],
    check=True,creationflags=subprocess.CREATE_NO_WINDOW)
   data=temporary.read_bytes()
  finally:temporary.unlink(missing_ok=True)
if hashlib.sha512(data).hexdigest()!=expected:raise RuntimeError('TerraBlender artifact checksum mismatch')
out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
print(out)

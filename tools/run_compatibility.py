"""Bounded real-world verification. A hung mod startup cannot stall the remaining matrix."""
from pathlib import Path
import json,os,subprocess,sys,time

root=Path(__file__).resolve().parents[1]
profiles=sys.argv[1:] or ['vanilla','painted','disabled','terrablender','bop','terralith','tectonic','terratonic','bop_terralith','bop_tectonic','combined']
allowed={'vanilla','painted','disabled','terrablender','bop','terralith','tectonic','terratonic','bop_terralith','bop_tectonic','combined'}
if any(p not in allowed for p in profiles):raise ValueError('Unknown profile')
report=root/'tools/compatibility-results.json'
results=json.loads(report.read_text()) if report.exists() else {}
for profile in profiles:
    print('START '+profile,flush=True)
    log=root/'build'/('compat-'+profile+'.log')
    result=root/'neoforge/build/compatibility-runs'/(profile+'-v3')/'compatibility-result.json'
    start=time.time()
    with log.open('w',encoding='utf-8') as output:
        process=subprocess.Popen([str(root/'gradlew.bat'),':neoforge:runVerification','-PcompatProfile='+profile,'--offline','--no-daemon'],
            cwd=root,stdout=output,stderr=subprocess.STDOUT,env=os.environ,creationflags=subprocess.CREATE_NO_WINDOW)
        try:code=process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True,creationflags=subprocess.CREATE_NO_WINDOW)
            code=-1
    passed=code==0 and result.exists() and result.stat().st_mtime>=start
    row={'status':'passed' if passed else 'failed','exit_code':code,'elapsed_seconds':round(time.time()-start,2),'log':str(log.relative_to(root))}
    if passed:row['checks']=json.loads(result.read_text())
    else:
        text=log.read_text(encoding='utf-8',errors='replace')
        row['errors']=[line for line in text.splitlines() if any(s in line for s in ['Caused by:','ERROR','failed at','Failed to','error:'])][-12:]
        if 'Compilation failed;' in text:row['status']='build_failed'
    results[profile]=row
    report.write_text(json.dumps(results,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print('RESULT '+profile+' '+row['status'],flush=True)
    if row['status']=='build_failed':break

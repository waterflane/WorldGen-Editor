"""Compare isolated NeoForge worlds against the loaded preview profiles, without user saves."""
from pathlib import Path
import json, os, subprocess, sys, time, urllib.request

root = Path(__file__).resolve().parents[1]
base = os.environ.get('WGE_PREVIEW_URL', 'http://127.0.0.1:8875')
report_path = root / 'tools/modded-preview-verification.json'
report = json.loads(report_path.read_text(encoding='utf-8')) if report_path.exists() else {'schema': 1, 'profiles': {}}
profiles = sys.argv[1:] or ['terrablender', 'bop', 'terralith', 'tectonic', 'terratonic', 'bop_terralith', 'bop_tectonic', 'combined']
allowed = {'terrablender', 'bop', 'terralith', 'tectonic', 'terratonic', 'bop_terralith', 'bop_tectonic', 'combined'}
if not set(profiles) <= allowed:
    raise ValueError('Unknown test profile')

def api(path, body=None):
    request = urllib.request.Request(base + '/api/' + path, data=None if body is None else json.dumps(body).encode(), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=240) as response:
        return json.load(response)

for name in profiles:
    profile_id = 'supported-' + name
    listed = api('profiles/list')['profiles']
    profile = next(p for p in listed if p['id'] == profile_id)
    if profile['status'] in ('not prepared', 'outdated'):
        job = api('profiles/prepare', {'profile': profile_id, 'heap_mib': 2048})['job']
        deadline = time.monotonic() + 900
        while True:
            state = api('profiles/status?id=' + job)
            if state['state'] == 'ready':
                break
            if state['state'] in ('failed', 'cancelled'):
                raise RuntimeError(state.get('error', state['state']))
            if time.monotonic() >= deadline:
                api('profiles/cancel', {'job': job})
                raise TimeoutError('Profile preparation timed out')
            time.sleep(1)
    catalog = api('catalog?profile=' + profile_id)
    entry = {'resources': catalog['resources'], 'status': 'testing', 'cases': []}
    report['profiles'][profile_id] = entry
    cases = [('base', 2)]
    if name == 'combined':
        cases += [('coast', 2), ('coast', 3)]
    for map_name, seed in cases:
        run = 'matrix-' + str(time.time_ns())
        log = root / 'build' / f'modded-matrix-{name}-{map_name}-{seed}.log'
        command = [str(root / 'gradlew.bat'), ':neoforge:runVerification', '-PcompatProfile=' + name, '-PmoddedPreviewUrl=' + base,
                   '-PcompatMap=' + map_name, '-PcompatSeed=' + str(seed), '-PcompatRun=' + run, '--offline', '--no-daemon']
        print(f'START {name} {map_name} seed={seed}', flush=True)
        started = time.monotonic()
        with log.open('w', encoding='utf-8') as output:
            process = subprocess.Popen(command, cwd=root, env=os.environ, stdout=output, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
            try:
                code = process.wait(timeout=360)
            except subprocess.TimeoutExpired:
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW)
                code = -1
        result = root / 'neoforge/build/modded-preview-verification' / f'{name}-standard-{map_name}-{run}' / 'modded-preview-result.json'
        if code or not result.exists():
            entry['status'] = 'failed'
            entry['error_log'] = str(log.relative_to(root))
            report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
            raise RuntimeError(f'{name} {map_name} verification failed; see {log}')
        case = json.loads(result.read_text(encoding='utf-8'))
        if case['resources'] != entry['resources'] or case['mismatches']:
            raise AssertionError('Runtime changed during comparison')
        case['elapsed_seconds'] = round(time.monotonic() - started, 2)
        case['map'] = map_name
        if name == 'combined' and map_name == 'coast' and seed == 3:
            # A broken global preset must not replace the pinned world configuration.
            (result.parent / 'config/worldgen_editor/presets/default.json').write_text('{invalid', encoding='utf-8')
            with log.with_suffix('.reopen.log').open('w', encoding='utf-8') as output:
                reopened = subprocess.run(command + ['-PmoddedReopen=true'], cwd=root, env=os.environ,
                                          stdout=output, stderr=subprocess.STDOUT, timeout=360,
                                          creationflags=subprocess.CREATE_NO_WINDOW)
            restored = json.loads(result.read_text(encoding='utf-8'))
            if reopened.returncode or not restored.get('reopened_world') or restored['resources'] != case['resources'] or restored['mismatches']:
                raise AssertionError('Reopened world did not preserve the pinned generation context')
            case['reopened_world'] = True
        entry['cases'].append(case)
        print(f'PASS {name} {map_name}: {case["biome_samples"]} biomes, {case["base_height_samples"]} heights, {case["category_context_samples"]} category samples', flush=True)
    entry['status'] = 'verified'
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

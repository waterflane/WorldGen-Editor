"""Compare surface and fixed-height throughput of a running preview on the same 16 tiles."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import json
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]

def post(path, body):
    request = urllib.request.Request('http://127.0.0.1:8765/api/' + path,
                                    json.dumps(body).encode(), {'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)

config = json.loads((ROOT / 'common/src/main/resources/assets/worldgen_editor/presets/default.json').read_text())
client = str(uuid.uuid4())

def tile(x, z, mode='surface'):
    return post('tile', dict(session=session, client=client, request=1, x=x, z=z,
                            size=8, step=32, height_mode=mode, y=64))

coords = [(-512 + x * 256, -512 + z * 256) for z in range(4) for x in range(4)]
report = {}
for mode in ['surface', 'slice']:
    # Ignored metadata forces independent cold caches without changing the generated world.
    snapshot = dict(config, _benchmark_run=client, _benchmark_mode=mode)
    session = post('session', dict(seed='987654321', config=snapshot, generation_version=2, client=client))['session']
    for i in range(4):
        tile(9000 + i * 256, 9000, mode)
    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=4) as pool:
        values = list(pool.map(lambda point: tile(*point, mode), coords))
    report[mode + '_16_tiles_ms'] = round((time.perf_counter() - start) * 1000, 2)
    if mode == 'surface':
        points = [{key: value[key] for key in ['palette', 'biomes', 'heights']} for value in values]
        report['points_sha256'] = hashlib.sha256(json.dumps(points, sort_keys=True).encode()).hexdigest()
print(json.dumps(report, indent=2))

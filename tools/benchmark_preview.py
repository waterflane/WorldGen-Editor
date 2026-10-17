"""Profile a running loopback preview; no world files are created or changed."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import json
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:8765/api/'


def post(path, body):
    request = urllib.request.Request(BASE + path, json.dumps(body).encode(),
                                     {'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def measured(path, body):
    start = time.perf_counter()
    status, result = post(path, body)
    return round((time.perf_counter() - start) * 1000, 2), status, result


config = json.loads((ROOT / 'common/src/main/resources/assets/worldgen_editor/presets/default.json').read_text())
client = str(uuid.uuid4())
session_ms, status, session = measured('session', {'seed': '12345', 'config': config,
                                                'generation_version': 2, 'client': client})
assert status == 200, session
for version in [1.5, 3, '2']:
    status, error = post('session', {'seed': '12345', 'config': config, 'generation_version': version})
    assert status == 400 and 'generation_version' in error['error'], error
query = {'session': session['session'], 'client': client, 'request': session['request_base'] + 1,
         'x': 9000, 'z': 9000, 'size': 8, 'step': 256, 'height_mode': 'surface'}
first_ms, status, tile = measured('tile', query)
assert status == 200, tile
repeat_ms, status, _ = measured('tile', query)
assert status == 200
dense_ms, status, dense = measured('tile', dict(query, x=520, z=-28, size=32, step=1))
assert status == 200, dense

# Cancellation is sent by a second HTTP request while a previously unseen tile is running.
with ThreadPoolExecutor(max_workers=1) as pool:
    old = pool.submit(measured, 'tile', dict(query, x=12000, z=12000, size=32, step=64))
    time.sleep(.02)
    start = time.perf_counter()
    status, _ = post('cancel', dict(query, request=query['request'] + 1))
    assert status == 200
    _, old_status, old_response = old.result(timeout=5)
    cancel_ms = round((time.perf_counter() - start) * 1000, 2)
    assert old_status == 409 and old_response.get('cancelled'), old_response

with urllib.request.urlopen(BASE + 'info') as response:
    info = json.load(response)
report = dict(session_ms=session_ms, coarse_8x8_ms=first_ms, cached_8x8_ms=repeat_ms,
              dense_32x32_ms=dense_ms, cancellation_ms=cancel_ms, info=info)
print(json.dumps(report, indent=2))

#!/usr/bin/env python3
# benefit4j 压测脚本: runtime 查询接口 (getUserAssets) QPS/P99/RT
# 用法: python3 bench.py <vu> <duration_sec>
import urllib.request, urllib.parse, json, hmac, hashlib, base64, time, uuid, threading, concurrent.futures, sys, statistics

BASE = "http://localhost:9200"
PLATFORM_SECRET = "fvUQ54yPgrMg2v2X"

def form_post(url, data):
    req = urllib.request.Request(url, data=urllib.parse.urlencode(data).encode(), method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    with urllib.request.urlopen(req) as r: return json.loads(r.read())

def json_post(url, body, token, headers=None):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", "Bearer " + token)
    for k,v in (headers or {}).items(): req.add_header(k, v)
    with urllib.request.urlopen(req) as r: return json.loads(r.read())

def signed_get(path, appt, appid, secret, userid):
    ts = str(int(time.time()*1000)); nonce = uuid.uuid4().hex.upper()
    bodymd5 = hashlib.md5(b"").hexdigest()
    sts = f"GET\n{path}\n{ts}\n{nonce}\n{bodymd5}"
    sig = base64.b64encode(hmac.new(secret.encode(), sts.encode(), hashlib.sha256).digest()).decode()
    url = BASE + path
    req = urllib.request.Request(url, method="GET")
    req.add_header("Authorization", "Bearer " + appt)
    req.add_header("X-Access-Key", appid)
    req.add_header("X-Timestamp", ts); req.add_header("X-Nonce", nonce)
    req.add_header("X-Signature", sig)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req) as r:
            r.read(); return time.perf_counter()-t0, 200
    except urllib.error.HTTPError as e:
        return time.perf_counter()-t0, e.code
    except Exception:
        return time.perf_counter()-t0, 0

# === setup ===
print("=== 数据准备 ===")
pt = form_post(BASE+"/benefit/api/v1/auth/token",
    {"client_id":"PLATFORM","client_secret":PLATFORM_SECRET,"grant_type":"client_credentials"})["data"]["access_token"]
app = json_post(BASE+"/benefit/api/v1/platform/applications", {"name":"bench-app"}, pt)["data"]
appid, secret = app["id"], app["app_secret"]
appt = form_post(BASE+"/benefit/api/v1/auth/token",
    {"client_id":appid,"client_secret":secret,"grant_type":"client_credentials"})["data"]["access_token"]
# 压测查询 getUserAssets: 即使 bench-u1 无 subscribe (查空), 仍走 DB 查询路径, 测 RT/QPS
# (建 item/set/subscribe 链路复杂, 查询 RT 主要看 DB 索引 + 签名校验, 空查询有代表性)
print(f"app={appid}")
USERID = "bench-u1"
ASSETS_PATH = f"/benefit/api/v1/runtime/users/{USERID}/assets"

# === 压测 ===
vu = int(sys.argv[1]) if len(sys.argv)>1 else 20
duration = int(sys.argv[2]) if len(sys.argv)>2 else 10
print(f"\n=== 压测 getUserAssets: {vu} VU × {duration}s ===")
lats = []; codes = {}; lock = threading.Lock()
stop = time.time() + duration
def worker():
    while time.time() < stop:
        rt, code = signed_get(ASSETS_PATH, appt, appid, secret, USERID)
        with lock:
            lats.append(rt); codes[code] = codes.get(code, 0) + 1
with concurrent.futures.ThreadPoolExecutor(max_workers=vu) as ex:
    futures = [ex.submit(worker) for _ in range(vu)]
    concurrent.futures.wait(futures)

# === 统计 ===
lats.sort()
n = len(lats)
qps = n / duration
print(f"\n=== 结果 ===")
print(f"总请求: {n}  QPS: {qps:.1f}")
print(f"状态码: {codes}")
if lats:
    print(f"RT  avg={statistics.mean(lats)*1000:.1f}ms  P50={lats[n//2]*1000:.1f}ms  "
          f"P95={lats[int(n*0.95)]*1000:.1f}ms  P99={lats[int(n*0.99)]*1000:.1f}ms  max={lats[-1]*1000:.1f}ms")

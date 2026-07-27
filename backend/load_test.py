import time
import concurrent.futures
import requests
import statistics
import os
import sys

BASE_URL = "http://127.0.0.1:5000"
NUM_VUSERS = 100
DURATION_SECONDS = 60

# Test endpoints to simulate realistic traffic
ENDPOINTS = [
    ("GET", "/api/restaurants", None, None),
    ("GET", "/api/lounges", None, None),
    ("GET", "/api/shopping", None, None),
    ("GET", "/api/lost-items", None, None),
    ("GET", "/api/guides/terminal_1", None, None),
]

def make_request(session, endpoint_tuple):
    method, path, body, headers = endpoint_tuple
    url = BASE_URL + path
    start = time.perf_counter()
    try:
        if method == "GET":
            res = session.get(url, headers=headers, timeout=10)
        elif method == "POST":
            res = session.post(url, json=body, headers=headers, timeout=10)
        latency_ms = (time.perf_counter() - start) * 1000.0
        return res.status_code, latency_ms, True
    except Exception as e:
        latency_ms = (time.perf_counter() - start) * 1000.0
        return 0, latency_ms, False

def vuser_worker(user_id, stop_time, stats):
    session = requests.Session()
    local_latencies = []
    local_statuses = {}
    local_successes = 0
    local_failures = 0
    
    idx = 0
    while time.time() < stop_time:
        ep = ENDPOINTS[idx % len(ENDPOINTS)]
        status, latency, success = make_request(session, ep)
        local_latencies.append(latency)
        local_statuses[status] = local_statuses.get(status, 0) + 1
        if success and status in (200, 201, 400, 401, 403, 404):
            local_successes += 1
        else:
            local_failures += 1
        idx += 1

    stats.append({
        "user_id": user_id,
        "latencies": local_latencies,
        "statuses": local_statuses,
        "successes": local_successes,
        "failures": local_failures
    })

def run_load_test():
    print(f"==================================================")
    print(f"[START] STARTING BASELINE LOAD TEST")
    print(f"• Virtual Users (VUsers): {NUM_VUSERS}")
    print(f"• Duration: {DURATION_SECONDS} seconds")
    print(f"• Target API: {BASE_URL}")
    print(f"==================================================\n")

    # Warmup request to verify backend availability
    try:
        resp = requests.get(BASE_URL + "/api/restaurants", timeout=5)
        print(f"[HEALTH CHECK] Target API is responsive. Initial status: {resp.status_code}\n")
    except Exception as e:
        print(f"[ERROR] Target API at {BASE_URL} is unreachable: {e}")
        sys.exit(1)

    start_time = time.time()
    stop_time = start_time + DURATION_SECONDS
    stats = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=NUM_VUSERS) as executor:
        futures = [
            executor.submit(vuser_worker, i, stop_time, stats)
            for i in range(NUM_VUSERS)
        ]
        concurrent.futures.wait(futures)

    actual_duration = time.time() - start_time
    
    # Aggregate results
    all_latencies = []
    total_successes = 0
    total_failures = 0
    status_summary = {}

    for s in stats:
        all_latencies.extend(s["latencies"])
        total_successes += s["successes"]
        total_failures += s["failures"]
        for st, count in s["statuses"].items():
            status_summary[st] = status_summary.get(st, 0) + count

    total_requests = len(all_latencies)
    rps = total_requests / actual_duration if actual_duration > 0 else 0

    avg_latency = statistics.mean(all_latencies) if all_latencies else 0
    min_latency = min(all_latencies) if all_latencies else 0
    max_latency = max(all_latencies) if all_latencies else 0
    median_latency = statistics.median(all_latencies) if all_latencies else 0
    
    sorted_lat = sorted(all_latencies)
    p90 = sorted_lat[int(len(sorted_lat) * 0.90)] if sorted_lat else 0
    p95 = sorted_lat[int(len(sorted_lat) * 0.95)] if sorted_lat else 0

    print("==================================================")
    print("BASELINE LOAD TEST RESULTS SUMMARY")
    print("==================================================")
    print(f"• Total Duration:         {actual_duration:.2f} seconds")
    print(f"• Total Requests Sent:    {total_requests:,}")
    print(f"• Requests Per Second:    {rps:.2f} req/sec")
    print(f"• Successful Requests:    {total_successes:,}")
    print(f"• Failed Requests:        {total_failures:,}")
    print("--------------------------------------------------")
    print("RESPONSE TIME STATS (Latency):")
    print(f"• Min Response Time:      {min_latency:.2f} ms")
    print(f"• Average Response Time:  {avg_latency:.2f} ms")
    print(f"• Median Response Time:   {median_latency:.2f} ms")
    print(f"• 90th Percentile (P90):  {p90:.2f} ms")
    print(f"• 95th Percentile (P95):  {p95:.2f} ms")
    print(f"• Max Response Time:      {max_latency:.2f} ms")
    print("--------------------------------------------------")
    print("STATUS CODE BREAKDOWN:")
    for code, count in sorted(status_summary.items()):
        print(f"  - HTTP {code}: {count:,} requests ({count/total_requests*100:.1f}%)")
    print("==================================================")


if __name__ == "__main__":
    run_load_test()

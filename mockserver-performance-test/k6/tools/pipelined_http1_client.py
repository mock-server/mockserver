#!/usr/bin/env python3
"""HTTP/1.1 pipelining client for the in-order pipelining A/B in perf-test-run.sh.

k6 never pipelines (each VU waits for a response before its next request), so it
cannot reach the code path http1PipeliningInOrder changes. This stdlib-only client
writes DEPTH requests to one keep-alive connection in a single send and then reads
the DEPTH responses back, over CONNECTIONS connections in separate processes.

  probe  seeds a slow (delayed) and a fast expectation, pipelines slow-then-fast on
         one connection and reports which body came back first. With the flag on the
         slow one must come first; with it off the fast one must. This proves the
         server under test really runs the configuration its arm claims.
  load   seeds a static expectation and drives pipelined batches for WARMUP + DURATION
         seconds. Latency is per batch: from the send to the last response read.

Both modes write one JSON object to --out (or stdout). load writes the regression.js
behaviour shape, {"proto": P, "behaviours": {"pipelined_<P>": {...}}}, so the run
script assembles its arms exactly as it does the k6 arms. Exit status is non-zero
only when nothing could be measured.
"""
import argparse
import http.client
import json
import math
import multiprocessing
import queue
import socket
import sys
import threading
import time

MIN_TAIL_SAMPLES = 30
PROBE_SLOW_PATH = "/pipelining-probe/slow"
PROBE_FAST_PATH = "/pipelining-probe/fast"
LOAD_PATH = "/pipelining-load"


def percentile(values, p):
    if not values:
        return None
    ordered = sorted(values)
    k = min(len(ordered) - 1, int(math.ceil(p / 100.0 * len(ordered)) - 1))
    return ordered[max(0, k)]


def build_batch(host, path, depth):
    one = ("GET %s HTTP/1.1\r\nHost: %s\r\n\r\n" % (path, host)).encode("ascii")
    return one * depth


class ResponseParser:
    """Incremental parser for a stream of HTTP/1.1 responses (Content-Length or chunked)."""

    def __init__(self):
        self.buf = b""

    def feed(self, data):
        self.buf += data

    def next_response(self):
        """Return (status, body, close) for the next complete response, or None."""
        head_end = self.buf.find(b"\r\n\r\n")
        if head_end < 0:
            return None
        head = self.buf[:head_end].decode("iso-8859-1")
        lines = head.split("\r\n")
        parts = lines[0].split(" ", 2)
        if len(parts) < 2 or not parts[0].startswith("HTTP/1."):
            raise ValueError("malformed status line: %r" % lines[0])
        status = int(parts[1])
        headers = {}
        for line in lines[1:]:
            name, _, value = line.partition(":")
            headers[name.strip().lower()] = value.strip()
        close = headers.get("connection", "").lower() == "close"
        pos = head_end + 4
        if "chunked" in headers.get("transfer-encoding", "").lower():
            body = b""
            while True:
                line_end = self.buf.find(b"\r\n", pos)
                if line_end < 0:
                    return None
                size = int(self.buf[pos:line_end].split(b";")[0], 16)
                pos = line_end + 2
                if size == 0:
                    trailer_end = self.buf.find(b"\r\n\r\n", pos - 2)
                    if trailer_end < 0:
                        return None
                    pos = trailer_end + 4
                    break
                if len(self.buf) < pos + size + 2:
                    return None
                body += self.buf[pos:pos + size]
                pos += size + 2
        else:
            length = int(headers.get("content-length", "0"))
            if len(self.buf) < pos + length:
                return None
            body = self.buf[pos:pos + length]
            pos += length
        self.buf = self.buf[pos:]
        return status, body, close


def read_responses(sock, parser, count):
    out = []
    while len(out) < count:
        response = parser.next_response()
        if response is not None:
            out.append(response)
            continue
        data = sock.recv(65536)
        if not data:
            raise ConnectionError("connection closed after %d of %d responses" % (len(out), count))
        parser.feed(data)
    return out


def seed(host, port, expectations, timeout=10):
    conn = http.client.HTTPConnection(host, port, timeout=timeout)
    try:
        conn.request("PUT", "/mockserver/expectation", body=json.dumps(expectations),
                     headers={"Content-Type": "application/json"})
        response = conn.getresponse()
        response.read()
        if response.status != 201:
            raise RuntimeError("seeding returned HTTP %d" % response.status)
    finally:
        conn.close()


def probe(host, port, delay_ms, timeout):
    seed(host, port, [
        {"httpRequest": {"method": "GET", "path": PROBE_SLOW_PATH},
         "httpResponse": {"statusCode": 200, "body": "slow",
                          "delay": {"timeUnit": "MILLISECONDS", "value": delay_ms}},
         "times": {"unlimited": True}},
        {"httpRequest": {"method": "GET", "path": PROBE_FAST_PATH},
         "httpResponse": {"statusCode": 200, "body": "fast"}, "times": {"unlimited": True}},
    ])
    sock = socket.create_connection((host, port), timeout=timeout)
    try:
        sock.sendall(build_batch(host, PROBE_SLOW_PATH, 1) + build_batch(host, PROBE_FAST_PATH, 1))
        responses = read_responses(sock, ResponseParser(), 2)
    finally:
        sock.close()
    bodies = [body.decode("utf-8", "replace") for _, body, _ in responses]
    return {"mode": "probe", "ok": sorted(bodies) == ["fast", "slow"],
            "first_body": bodies[0], "second_body": bodies[1],
            "in_order": bodies == ["slow", "fast"], "delay_ms": delay_ms}


def load_worker(args):
    host, port, depth, start_at, warmup_s, duration_s, timeout = args
    if start_at is None:
        start_at = time.time()
    batch = build_batch(host, LOAD_PATH, depth)
    measure_from = start_at + warmup_s
    measure_to = measure_from + duration_s
    latencies_ms, responses, errors, non_2xx, reconnects = [], 0, 0, 0, 0
    sock, parser = None, None
    while time.time() < measure_to:
        try:
            if sock is None:
                sock = socket.create_connection((host, port), timeout=timeout)
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                parser = ResponseParser()
            sent_wall = time.time()
            t0 = time.perf_counter()
            sock.sendall(batch)
            got = read_responses(sock, parser, depth)
            elapsed_ms = (time.perf_counter() - t0) * 1000.0
            done_wall = time.time()
            if sent_wall >= measure_from and done_wall <= measure_to:
                latencies_ms.append(elapsed_ms)
                responses += depth
                non_2xx += sum(1 for status, _, _ in got if not 200 <= status < 300)
            if any(close for _, _, close in got):
                sock.close()
                sock, reconnects = None, reconnects + 1
        except (OSError, ValueError):
            if time.time() >= measure_from:
                errors += 1
            if sock is not None:
                sock.close()
            sock, reconnects = None, reconnects + 1
            time.sleep(0.05)
    if sock is not None:
        sock.close()
    return {"latencies_ms": latencies_ms, "responses": responses, "errors": errors,
            "non_2xx": non_2xx, "reconnects": reconnects}


def _barrier_worker(args, barrier, results):
    try:
        barrier.wait(60)
    except threading.BrokenBarrierError:
        return
    results.put(load_worker(args))


def run_workers(args, connections, deadline_s):
    """One process per connection, released together by a barrier once all have started (a slow
    process start must not eat the window), each starting its own clock at release. A worker that
    dies or overruns raises RuntimeError instead of blocking forever."""
    ctx = multiprocessing.get_context()
    barrier = ctx.Barrier(connections + 1)
    results = ctx.Queue()
    procs = [ctx.Process(target=_barrier_worker, args=(args, barrier, results), daemon=True)
             for _ in range(connections)]
    for proc in procs:
        proc.start()
    try:
        barrier.wait(60)
        return [results.get(timeout=deadline_s) for _ in procs]
    except (threading.BrokenBarrierError, queue.Empty):
        raise RuntimeError("a load worker did not start or did not report within %ss" % deadline_s)
    finally:
        for proc in procs:
            proc.join(timeout=5)
            if proc.is_alive():
                proc.terminate()


def load(host, port, proto, connections, depth, warmup_s, duration_s, timeout):
    seed(host, port, [{"httpRequest": {"method": "GET", "path": LOAD_PATH},
                       "httpResponse": {"statusCode": 200, "body": "ok"},
                       "times": {"unlimited": True}}])
    parts = run_workers((host, port, depth, None, warmup_s, duration_s, timeout), connections,
                        warmup_s + duration_s + timeout + 30)
    latencies = [v for part in parts for v in part["latencies_ms"]]
    responses = sum(part["responses"] for part in parts)
    failed_batches = sum(part["errors"] for part in parts)
    non_2xx = sum(part["non_2xx"] for part in parts)
    attempted = responses + failed_batches * depth
    enough = len(latencies) >= MIN_TAIL_SAMPLES

    def pct(p):
        value = percentile(latencies, p) if enough else None
        return None if value is None else round(value, 3)

    behaviour = {
        "p50_ms": pct(50), "p95_ms": pct(95), "p99_ms": pct(99),
        "sample_count": len(latencies),
        "throughput_rps": round(responses / duration_s, 3) if duration_s > 0 else 0,
        "error_rate": round((failed_batches * depth + non_2xx) / attempted, 5) if attempted else 1.0,
        "connections": connections, "depth": depth,
        "reconnects": sum(part["reconnects"] for part in parts),
        "measured_window_s": duration_s, "latency_unit": "batch",
    }
    return {"mode": "load", "proto": proto, "behaviours": {"pipelined_%s" % proto: behaviour}}


def parse_target(target):
    host, _, port = target.rpartition(":")
    if not host or not port.isdigit():
        raise argparse.ArgumentTypeError("target must be host:port, got %r" % target)
    return host, int(port)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="mode", required=True)
    for name in ("probe", "load"):
        p = sub.add_parser(name)
        p.add_argument("--target", type=parse_target, required=True, help="host:port")
        p.add_argument("--out", help="write the JSON result here instead of stdout")
        p.add_argument("--timeout", type=float, default=10.0)
    sub.choices["probe"].add_argument("--delay-ms", type=int, default=1000)
    lp = sub.choices["load"]
    lp.add_argument("--proto", required=True)
    lp.add_argument("--connections", type=int, default=8)
    lp.add_argument("--depth", type=int, default=16)
    lp.add_argument("--warmup-s", type=float, default=10.0)
    lp.add_argument("--duration-s", type=float, default=30.0)
    args = parser.parse_args(argv)
    host, port = args.target
    try:
        if args.mode == "probe":
            result = probe(host, port, args.delay_ms, args.timeout)
            status = 0 if result["ok"] else 1
        else:
            result = load(host, port, args.proto, args.connections, args.depth,
                          args.warmup_s, args.duration_s, args.timeout)
            status = 0 if next(iter(result["behaviours"].values()))["sample_count"] > 0 else 1
    except (OSError, RuntimeError, ValueError) as e:
        result, status = {"mode": args.mode, "ok": False, "error": str(e)}, 1
    text = json.dumps(result, indent=2)
    if args.out:
        with open(args.out, "w") as f:
            f.write(text + "\n")
    else:
        print(text)
    return status


if __name__ == "__main__":
    sys.exit(main())

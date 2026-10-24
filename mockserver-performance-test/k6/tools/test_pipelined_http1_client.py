#!/usr/bin/env python3
"""Unit tests for pipelined_http1_client.py, against an in-process stub server.

Run: python3 -m unittest discover -s mockserver-performance-test/k6/tools -p 'test_pipelined_http1_client.py'
"""
import json
import os
import socket
import socketserver
import sys
import tempfile
import threading
import time
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pipelined_http1_client as client  # noqa: E402


def response_bytes(body, extra=""):
    data = body.encode("ascii")
    return ("HTTP/1.1 200 OK\r\nContent-Length: %d\r\n%s\r\n" % (len(data), extra)).encode("ascii") + data


class StubHandler(socketserver.BaseRequestHandler):
    """Answers pipelined requests. in_order=False writes a delayed response after the
    requests read with it, as MockServer does with http1PipeliningInOrder=false."""

    def handle(self):
        buf = b""
        while True:
            try:
                data = self.request.recv(65536)
            except OSError:
                return
            if not data:
                return
            buf += data
            ready = []
            while True:
                end = buf.find(b"\r\n\r\n")
                if end < 0:
                    break
                head = buf[:end].decode("ascii")
                length = 0
                for line in head.split("\r\n")[1:]:
                    name, _, value = line.partition(":")
                    if name.strip().lower() == "content-length":
                        length = int(value.strip())
                if len(buf) < end + 4 + length:
                    break
                ready.append(head.split(" ")[:2])
                buf = buf[end + 4 + length:]
            deferred = []
            for method, path in ready:
                if method == "PUT":
                    self.request.sendall(b"HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n")
                elif path == client.PROBE_SLOW_PATH:
                    if self.server.in_order:
                        time.sleep(0.05)
                        self.request.sendall(response_bytes("slow"))
                    else:
                        deferred.append(response_bytes("slow"))
                elif path == client.PROBE_FAST_PATH:
                    self.request.sendall(response_bytes("fast"))
                else:
                    self.request.sendall(response_bytes("ok"))
            for pending in deferred:
                time.sleep(0.05)
                self.request.sendall(pending)


class StubServer(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, in_order):
        super().__init__(("127.0.0.1", 0), StubHandler)
        self.in_order = in_order


def temp_json_path():
    fd, path = tempfile.mkstemp(suffix=".json")
    os.close(fd)
    return path


def start_stub(in_order=True):
    server = StubServer(in_order)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


class PercentileTest(unittest.TestCase):
    def test_nearest_rank(self):
        values = list(range(1, 101))
        self.assertEqual(client.percentile(values, 50), 50)
        self.assertEqual(client.percentile(values, 95), 95)
        self.assertEqual(client.percentile(values, 99), 99)
        self.assertEqual(client.percentile([7], 99), 7)
        self.assertIsNone(client.percentile([], 50))


class ResponseParserTest(unittest.TestCase):
    def test_content_length_responses_split_across_reads(self):
        stream = response_bytes("one") + response_bytes("two", "Connection: close\r\n")
        parser = client.ResponseParser()
        parser.feed(stream[:10])
        self.assertIsNone(parser.next_response())
        parser.feed(stream[10:])
        self.assertEqual(parser.next_response(), (200, b"one", False))
        self.assertEqual(parser.next_response(), (200, b"two", True))
        self.assertIsNone(parser.next_response())

    def test_chunked_response(self):
        parser = client.ResponseParser()
        parser.feed(b"HTTP/1.1 404 Not Found\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n")
        parser.feed(response_bytes("next"))
        self.assertEqual(parser.next_response(), (404, b"abcde", False))
        self.assertEqual(parser.next_response(), (200, b"next", False))

    def test_incomplete_chunked_response_waits(self):
        parser = client.ResponseParser()
        parser.feed(b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nab")
        self.assertIsNone(parser.next_response())

    def test_malformed_status_line_raises(self):
        parser = client.ResponseParser()
        parser.feed(b"garbage\r\n\r\n")
        with self.assertRaises(ValueError):
            parser.next_response()

    def test_batch_is_depth_requests(self):
        batch = client.build_batch("h", "/p", 3)
        self.assertEqual(batch.count(b"GET /p HTTP/1.1\r\nHost: h\r\n\r\n"), 3)


class ProbeTest(unittest.TestCase):
    def test_in_order_server(self):
        server = start_stub(in_order=True)
        try:
            result = client.probe("127.0.0.1", server.server_address[1], 50, 5)
        finally:
            server.shutdown()
            server.server_close()
        self.assertTrue(result["ok"])
        self.assertTrue(result["in_order"])
        self.assertEqual(result["first_body"], "slow")

    def test_out_of_order_server(self):
        server = start_stub(in_order=False)
        try:
            result = client.probe("127.0.0.1", server.server_address[1], 50, 5)
        finally:
            server.shutdown()
            server.server_close()
        self.assertTrue(result["ok"])
        self.assertFalse(result["in_order"])
        self.assertEqual(result["first_body"], "fast")


class LoadTest(unittest.TestCase):
    def test_load_writes_the_behaviour_shape(self):
        server = start_stub()
        out = temp_json_path()
        try:
            status = client.main(["load", "--target", "127.0.0.1:%d" % server.server_address[1],
                                  "--proto", "test", "--connections", "2", "--depth", "4",
                                  "--warmup-s", "0.2", "--duration-s", "0.5", "--out", out])
            with open(out) as f:
                result = json.load(f)
        finally:
            server.shutdown()
            server.server_close()
            os.unlink(out)
        self.assertEqual(status, 0)
        behaviour = result["behaviours"]["pipelined_test"]
        self.assertGreater(behaviour["sample_count"], 0)
        self.assertEqual(behaviour["error_rate"], 0)
        self.assertGreater(behaviour["throughput_rps"], 0)
        self.assertEqual(behaviour["depth"], 4)
        for key in ("p50_ms", "p95_ms", "p99_ms"):
            self.assertIn(key, behaviour)

    def test_unreachable_target_exits_non_zero(self):
        sock = socket.socket()
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
        sock.close()
        out = temp_json_path()
        try:
            status = client.main(["probe", "--target", "127.0.0.1:%d" % port, "--timeout", "1", "--out", out])
            with open(out) as f:
                result = json.load(f)
        finally:
            os.unlink(out)
        self.assertEqual(status, 1)
        self.assertFalse(result["ok"])


if __name__ == "__main__":
    unittest.main()

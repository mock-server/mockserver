#!/usr/bin/env python3
"""Fast engine of the SUT receive-queue sampler in lib/perf-sut-recvq.sh (performance programme item 44).

One long-lived process samples the Recv-Q of every TCP socket in the SUT's network namespace at a fixed
rate and appends one row per sample to main-sut-recvq.csv, in the shell engine's format. It never forks
per sample for the proc source, which is what kept the shell engine near one sample a second.
Pure stdlib, Python 3.9+. Report-only: any failure ends the sampler and is recorded, never raised.

  perf-sut-recvq.py sample proc <proc_root> <pid>    one "estab,nonzero,sum,max,listen" line, or exit 1
  perf-sut-recvq.py sample ss <pid>                  the same from `nsenter -t <pid> -n ss -tanH`
  perf-sut-recvq.py run <dir> <proc|ss> <pid> <proc_root> <interval_s> <max_samples> <max_bytes> <parent_pid>

Tests: .buildkite/scripts/test/perf-sut-recvq-test.sh
"""
import json
import os
import signal
import subprocess
import sys
import time

CSV = "main-sut-recvq.csv"
ESTABLISHED, LISTEN = b"01", b"0A"


def parse_proc(data):
    """(estab, nonzero, sum, max, listen) over one /proc/<pid>/net/tcp{,6} table, or None if no row parsed.

    Field 4 is the state, field 5 tx_queue:rx_queue in hex; a listener's rx_queue is its accept queue.
    A row whose rx_queue is not hex is skipped, never read as 0.
    """
    e = nz = total = mx = lq = 0
    seen = False
    for line in data.split(b"\n"):
        f = line.split(None, 5)
        if len(f) < 5 or not f[0].endswith(b":") or not f[0][:-1].isdigit():
            continue
        rq = f[4].partition(b":")[2]
        if rq == b"00000000":
            v = 0
        else:
            try:
                v = int(rq, 16)
            except ValueError:
                continue
        seen = True
        if f[3] == ESTABLISHED:
            e += 1
            if v > 0:
                nz += 1
                total += v
                if v > mx:
                    mx = v
        elif f[3] == LISTEN:
            lq += v
    return (e, nz, total, mx, lq) if seen else None


def parse_ss(text):
    """The same columns from `ss -tanH` (state, Recv-Q, Send-Q, local, peer), or None if no row."""
    e = nz = total = mx = lq = 0
    seen = False
    for line in text.splitlines():
        f = line.split()
        if len(f) < 5:
            continue
        seen = True
        try:
            v = int(f[1])
        except ValueError:
            v = 0
        if f[0] == "ESTAB":
            e += 1
            if v > 0:
                nz += 1
                total += v
                if v > mx:
                    mx = v
        elif f[0] == "LISTEN":
            lq += v
    return (e, nz, total, mx, lq) if seen else None


def _read_all(path):
    fd = os.open(path, os.O_RDONLY)
    try:
        chunks = []
        while True:
            b = os.read(fd, 1 << 20)
            if not b:
                return b"".join(chunks)
            chunks.append(b)
    finally:
        os.close(fd)


def sample_proc(root, pid):
    rows = []
    for name in ("tcp", "tcp6"):
        try:
            data = _read_all(os.path.join(root, str(pid), "net", name))
        except OSError:
            continue
        r = parse_proc(data)
        if r is not None:
            rows.append(r)
    if not rows:
        return None
    return (sum(r[0] for r in rows), sum(r[1] for r in rows), sum(r[2] for r in rows),
            max(r[3] for r in rows), sum(r[4] for r in rows))


def sample_ss(pid):
    try:
        p = subprocess.run(["nsenter", "-t", str(pid), "-n", "ss", "-tanH"], stdin=subprocess.DEVNULL,
                           stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=5, check=False)
    except (OSError, subprocess.SubprocessError):
        return None
    if p.returncode != 0:
        return None
    return parse_ss(p.stdout.decode("ascii", "replace"))


def _parent_alive(pid):
    if pid <= 0:
        return True
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def _write_atomic(path, text):
    tmp = path + ".tmp"
    with open(tmp, "w") as f:
        f.write(text)
    os.replace(tmp, path)


def _pct(sorted_values, q):
    return sorted_values[min(len(sorted_values) - 1, int(q * len(sorted_values)))]


def run(out_dir, source, pid, root, interval, max_samples, max_bytes, parent):
    """Samples at fixed-rate ticks until stopped, the parent is gone, max_samples or max_bytes.

    A read longer than the interval skips the ticks it overran (counted as overruns) rather than
    sampling back to back. On exit: .recvq-stats.json, .recvq-count, then .recvq-end with the reason.
    """
    stop = []
    signal.signal(signal.SIGTERM, lambda *_: stop.append(1))
    csv_path = os.path.join(out_dir, CSV)
    attempts = failed = overruns = 0
    reads = []
    reason = "unknown"
    wall0, cpu0 = time.monotonic(), os.times()
    try:
        size = os.path.getsize(csv_path)
        fd = os.open(csv_path, os.O_WRONLY | os.O_APPEND)
        try:
            tick = 0
            while True:
                if not _parent_alive(parent):
                    reason = "parent_gone"
                    break
                if attempts >= max_samples:
                    reason = "max_samples"
                    break
                ts = time.time()
                r0 = time.perf_counter()
                row = sample_ss(pid) if source == "ss" else sample_proc(root, pid)
                ms = (time.perf_counter() - r0) * 1000
                attempts += 1
                if row is None:
                    failed += 1
                else:
                    line = ("%.3f,%s,%d,%d,%d,%d,%d,%.1f\n" % ((ts, source) + row + (ms,))).encode()
                    os.write(fd, line)
                    size += len(line)
                    reads.append(ms)
                if size >= max_bytes:
                    reason = "max_bytes"
                    break
                if stop:
                    reason = "stopped"
                    break
                tick += 1
                now = time.monotonic()
                due = wall0 + tick * interval
                if now > due:
                    missed = int((now - due) / interval) + 1
                    overruns += missed
                    tick += missed
                    due = wall0 + tick * interval
                while not stop and now < due:
                    time.sleep(min(due - now, 0.2))
                    now = time.monotonic()
                if stop:
                    reason = "stopped"
                    break
        finally:
            os.close(fd)
    except Exception as ex:  # report-only: record why, never raise into the harness
        reason = "error: %s" % type(ex).__name__
    wall = time.monotonic() - wall0
    cpu1 = os.times()
    cpu = (cpu1.user - cpu0.user) + (cpu1.system - cpu0.system) \
        + (cpu1.children_user - cpu0.children_user) + (cpu1.children_system - cpu0.children_system)
    reads.sort()
    stats = {
        "engine": "python", "attempts": attempts, "rows": len(reads), "failed_reads": failed,
        "overruns": overruns, "wall_s": round(wall, 3), "cpu_s": round(cpu, 3),
        "cpu_frac": round(cpu / wall, 4) if wall > 0 else None,
        "rows_per_s": round(len(reads) / wall, 2) if wall > 0 else None,
        "read_ms": ({"p50": round(_pct(reads, 0.5), 1), "p99": round(_pct(reads, 0.99), 1),
                     "max": round(reads[-1], 1)} if reads else None),
    }
    try:
        _write_atomic(os.path.join(out_dir, ".recvq-stats.json"), json.dumps(stats, separators=(",", ":")))
        _write_atomic(os.path.join(out_dir, ".recvq-count"), "%d\n" % attempts)
        _write_atomic(os.path.join(out_dir, ".recvq-end"), reason + "\n")
    except OSError:
        pass
    return 0


def _usage():
    sys.stderr.write(__doc__)
    return 2


def main(argv):
    if len(argv) >= 4 and argv[0] == "sample" and argv[1] == "proc":
        row = sample_proc(argv[2], argv[3])
    elif len(argv) >= 3 and argv[0] == "sample" and argv[1] == "ss":
        row = sample_ss(argv[2])
    elif len(argv) == 9 and argv[0] == "run" and argv[2] in ("proc", "ss"):
        try:
            interval, max_samples, max_bytes, parent = float(argv[5]), int(argv[6]), int(argv[7]), int(argv[8])
        except ValueError:
            return _usage()
        if not interval > 0:
            return _usage()
        return run(argv[1], argv[2], argv[3], argv[4], interval, max_samples, max_bytes, parent)
    else:
        return _usage()
    if row is None:
        return 1
    print("%d,%d,%d,%d,%d" % row)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

#!/usr/bin/env python3
"""Attribute the multi-k6 arm's latency-tail spikes (performance programme item 44).

Given a work bundle (serving-rw-multik6-work/, or its .tgz), print every tail spike: the k6
process(es) it hit, whether it overlaps that process's own Go GC mark phase, and the softirq,
softnet and TCP counters of the SUT's cpus at that second. Pure stdlib, Python 3.9+.

    rw-tail-attribution.py <bundle dir or .tgz> [--min-rate 64000]

Series come from main-k6-timeseries.csv when the bundle has it; older bundles fall back to each
k6 log's progress line (in-flight VUs only). Docs: docs/code/performance-measurement.md,
"Tail attribution files". Tests: .buildkite/scripts/test/perf-tail-instrument-test.sh
"""
import argparse
import csv
import json
import os
import re
import statistics
import sys
import tarfile
import tempfile
from math import comb

GC_PAD_S = 0.3  # a sample this close to a mark phase counts as inside it
GC_RE = re.compile(r'^gc (\d+) @([\d.]+)s \d+%: ([\d.]+)\+([\d.]+)\+([\d.]+) ms clock')
PROGRESS_RE = re.compile(r'running \((\d+)m([\d.]+)s\), (\d+)/(\d+) VUs, (\d+) complete')


def num(v):
    try:
        return float(v) if v not in (None, '') else None
    except ValueError:
        return None


def within(base, path):
    return path == base or path.startswith(base + os.sep)


def vetted_members(t, dest):
    """The archive's members, or exit if any is not a plain file or dir, repeats a name, or lands outside dest."""
    base = os.path.realpath(dest)
    seen = set()
    for m in t.getmembers():
        name = m.name
        if os.path.isabs(name) or '..' in name.replace('\\', '/').split('/'):
            sys.exit('unsafe path in bundle archive: %r' % name)
        target = os.path.realpath(os.path.join(base, name))
        if not within(base, target):
            sys.exit('unsafe path in bundle archive: %r' % name)
        if not (m.isfile() or m.isdir()):
            sys.exit('not a plain file or directory in bundle archive: %r' % name)
        if target in seen:
            sys.exit('duplicate name in bundle archive: %r' % name)
        seen.add(target)
    return t.getmembers()


def find_bundle(path, tmp):
    if os.path.isfile(path):
        with tarfile.open(path) as t:
            members = vetted_members(t, tmp)
            if hasattr(tarfile, 'data_filter'):
                try:
                    t.extractall(tmp, members=members, filter='data')
                except tarfile.FilterError as e:
                    sys.exit('unsafe member in bundle archive: %s' % e)
            else:
                t.extractall(tmp, members=members)
        path = tmp
    for root, _, files in os.walk(path):
        if 'main-meta.json' in files:
            return root
    sys.exit('no main-meta.json under %s: not a multi-k6 work bundle' % path)


def read_csv(path):
    if not os.path.isfile(path):
        return []
    with open(path, newline='') as f:
        return list(csv.DictReader(f))


def ladder(d, meta):
    """(step_s, gap_s): from main-meta.json, else from the result's window and the ladder span."""
    if 'step_s' in meta and 'gap_s' in meta:
        return meta['step_s'], meta['gap_s']
    step = None
    for name in ('result.json', 'sweep.json'):
        p = os.path.join(d, name)
        if os.path.isfile(p):
            doc = json.load(open(p))
            lw = (doc.get('sweep') or doc).get('latency_window') or {}
            if 'settle_s' in lw and 'measured_s' in lw:
                step = lw['settle_s'] + lw['measured_s']
                break
    if step is None:
        sys.exit('cannot tell the rung step: no step_s in main-meta.json and no latency_window')
    rungs = len(meta['agg_rates'])
    span = meta['ladder_end_s'] - meta['start_at_s'] - step
    return step, (span / (rungs - 1) - step) if rungs > 1 else 0


def gc_marks(d, meta):
    """{proc: [(mark_start, mark_end)]} from main-k6-gc.csv, else from the k6 logs' gctrace lines."""
    marks = {}
    rows = read_csv(os.path.join(d, 'main-k6-gc.csv'))
    if rows:
        for r in rows:
            s = num(r['start_epoch_s']) + num(r['stw_sweep_ms']) / 1000
            marks.setdefault(r['proc'], []).append((s, s + num(r['mark_ms']) / 1000))
        return marks, 'main-k6-gc.csv'
    for i in range(meta['n']):
        started = (meta.get('container_started_epoch_s') or [None] * meta['n'])[i]
        log = os.path.join(d, 'main-p%d.log' % i)
        if started is None or not os.path.isfile(log):
            continue
        for line in open(log, errors='replace'):
            m = GC_RE.match(line)
            if m:
                s = started + float(m.group(2)) + float(m.group(3)) / 1000
                marks.setdefault('main-p%d' % i, []).append((s, s + float(m.group(4)) / 1000))
    return marks, 'gctrace lines in main-p*.log'


def series(d, meta):
    """{proc: [(t0, t1, vus, over5, reqs)]}: per-second rows, or progress-line points (t0 == t1)."""
    out = {}
    rows = read_csv(os.path.join(d, 'main-k6-timeseries.csv'))
    if rows:
        for r in rows:
            t = int(r['ts'])
            out.setdefault(r['proc'], []).append((t, t + 1, num(r['vus']), num(r['over_5ms']), num(r['reqs'])))
        return out, 'main-k6-timeseries.csv'
    # Older bundles: k6's progress line; its clock starts at k6 start, anchored by the first completions.
    pp0 = meta.get('per_process_rates', [None])[0] or meta['agg_rates'][0] / meta['n']
    for i in range(meta['n']):
        log = os.path.join(d, 'main-p%d.log' % i)
        if not os.path.isfile(log):
            continue
        prog = []
        for line in open(log, errors='replace'):
            m = PROGRESS_RE.search(line)
            if m:
                prog.append((int(m.group(1)) * 60 + float(m.group(2)), int(m.group(3)), int(m.group(5))))
        first = [p for p in prog if p[2] > 0]
        if not first:
            continue
        off = first[0][0] - first[0][2] / pp0 - meta['start_at_s']
        out['main-p%d' % i] = [(t - off, t - off, v, None, None) for t, v, _ in prog]
    return out, 'progress lines in main-p*.log (in-flight VUs only)'


def host(d):
    """({(ts, role): row}, {(ts, netns): row}, status)."""
    cpu, tcp = {}, {}
    for r in read_csv(os.path.join(d, 'host-kernel-cpu.csv')):
        agg = cpu.setdefault((int(r['ts']), r['role']), {'n': 0})
        agg['n'] += 1
        for k in ('soft_pct', 'sys_pct', 'net_rx', 'net_tx', 'softnet_dropped', 'softnet_squeezed'):
            v = num(r[k])
            if v is not None:
                agg[k] = agg.get(k, 0) + v
    for r in read_csv(os.path.join(d, 'host-kernel-tcp.csv')):
        tcp[(int(r['ts']), r['netns'])] = r
    p = os.path.join(d, 'host-kernel-status.json')
    status = json.load(open(p)) if os.path.isfile(p) else None
    return cpu, tcp, status


def overlaps(t0, t1, marks, pad=GC_PAD_S):
    return sum(max(0.0, min(t1 + pad, b) - max(t0 - pad, a)) for a, b in marks if b >= t0 - pad and a <= t1 + pad)


def fmt(v, unit=''):
    return 'n/a' if v is None else ('%.1f%s' % (v, unit) if isinstance(v, float) and not v.is_integer() else '%d%s' % (v, unit))


def kernel_at(cpu, tcp, t, role, netns):
    c = cpu.get((t, role))
    parts = []
    if c:
        n = c['n']
        parts.append('%s soft %s sys %s NET_RX %s softnet drop %s squeeze %s' % (
            role, fmt(c['soft_pct'] / n if 'soft_pct' in c else None, '%'), fmt(c['sys_pct'] / n if 'sys_pct' in c else None, '%'),
            fmt(c.get('net_rx')), fmt(c.get('softnet_dropped')), fmt(c.get('softnet_squeezed'))))
    else:
        parts.append('%s: no host sample' % role)
    tr = tcp.get((t, netns))
    if tr:
        parts.append('%s retrans %s listen-drop %s backlog-drop %s' % (
            netns, tr.get('RetransSegs') or 'n/a', tr.get('ListenDrops') or 'n/a', tr.get('TCPBacklogDrop') or 'n/a'))
    return '; '.join(parts)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('bundle')
    ap.add_argument('--min-rate', type=float, default=0, help='only rungs at or above this aggregate offered rate')
    ap.add_argument('--min-vus', type=float, default=50, help='a VU spike is at least this many in-flight VUs ...')
    ap.add_argument('--vus-factor', type=float, default=5, help='... and this many times the rung median')
    ap.add_argument('--min-over5', type=float, default=20, help='an over-5 ms spike is at least this many requests ...')
    ap.add_argument('--over5-share', type=float, default=0.05, help='... and this share of the second\'s requests')
    a = ap.parse_args()
    with tempfile.TemporaryDirectory() as tmp:
        d = find_bundle(a.bundle, tmp)
        meta = json.load(open(os.path.join(d, 'main-meta.json')))
        step, gap = ladder(d, meta)
        ser, ser_src = series(d, meta)
        marks, gc_src = gc_marks(d, meta)
        cpu, tcp, hstatus = host(d)
        sut_roles = sorted({r for (_, r) in cpu if r == 'sut'})
        print('bundle %s: %d processes, series from %s, GC marks from %s (%d cycles)' % (
            d, meta['n'], ser_src, gc_src, sum(len(v) for v in marks.values())))
        if not cpu:
            why = 'no host-kernel-cpu.csv rows'
            if hstatus:
                why = hstatus.get('reason') or '; '.join(
                    '%s: %s' % (k, v['reason']) for k, v in (hstatus.get('sources') or {}).items() if not v.get('available')) or why
            print('host kernel counters unavailable: %s' % why)
        procs = sorted(ser)
        spikes, base = [], {'n': 0, 'own': 0, 'other': 0}
        spike_secs, calm_secs = set(), set()
        for ri, rate in enumerate(meta['agg_rates']):
            if rate < a.min_rate:
                continue
            st = meta['start_at_s'] + ri * (step + gap)
            line = []
            for p in procs:
                w = [s for s in ser[p] if st + 1 <= s[0] < st + step]  # skip the rung-onset second
                vus = [s[2] for s in w if s[2] is not None]
                med = statistics.median(vus) if vus else 0
                for t0, t1, v, o5, rq in w:
                    own = overlaps(t0, t1, marks.get(p, []))
                    oth = any(overlaps(t0, t1, marks.get(q, [])) for q in procs if q != p)
                    base['n'] += 1
                    base['own'] += own > 0
                    base['other'] += oth
                    why = []
                    if v is not None and v >= max(a.min_vus, a.vus_factor * med):
                        why.append('vus')
                    if o5 is not None and o5 >= max(a.min_over5, a.over5_share * (rq or 0)):
                        why.append('over5')
                    if not why:
                        calm_secs.add(int(t0))
                        continue
                    spikes.append(dict(rate=rate, proc=p, t0=t0, t1=t1, at=t0 - st, vus=v, o5=o5, reqs=rq, own=own, other=oth, why=why))
                    spike_secs.add(int(t0))
        for s in spikes:
            s['sim'] = sorted({o['proc'] for o in spikes if o['proc'] != s['proc'] and abs(o['t0'] - s['t0']) <= 1})
        for rate in sorted({s['rate'] for s in spikes}):
            print('--- rung %s' % fmt(rate))
            for s in sorted((s for s in spikes if s['rate'] == rate), key=lambda s: (s['t0'], s['proc'])):
                hit = s['proc'] + ('' if not s['sim'] else ' (+%s)' % ','.join(s['sim']))
                gc = 'OWN-GC %d ms' % round(s['own'] * 1000) if s['own'] else 'no own GC'
                print('  %s @+%.1fs [%s] vus %s over5 %s of %s | %s%s | %s' % (
                    hit, s['at'], '+'.join(s['why']), fmt(s['vus']), fmt(s['o5']), fmt(s['reqs']), gc,
                    ' | other-process GC' if s['other'] else '',
                    kernel_at(cpu, tcp, int(s['t0']), 'sut', 'sut') if cpu else 'host n/a'))
        n, own = len(spikes), sum(1 for s in spikes if s['own'])
        p = base['own'] / base['n'] if base['n'] else 0
        tail = sum(comb(n, x) * p ** x * (1 - p) ** (n - x) for x in range(own, n + 1)) if n else 1
        print('--- summary')
        print('spikes %d over %s; per process %s' % (n, 'rungs >= %s' % fmt(a.min_rate) if a.min_rate else 'every rung',
                                                      ', '.join('%s %d' % (q, sum(1 for s in spikes if s['proc'] == q)) for q in procs) or 'none'))
        print('own-GC aligned %d/%d (base rate %.3f of samples inside own GC +-%.1fs, P(X>=%d)=%.4f); '
              'other-process GC %d/%d (base %.3f); simultaneous with another process %d/%d' % (
                  own, n, p, GC_PAD_S, own, tail, sum(1 for s in spikes if s['other']), n,
                  base['other'] / base['n'] if base['n'] else 0, sum(1 for s in spikes if s['sim']), n))
        if cpu and sut_roles:
            def mean(secs, key):
                vals = [cpu[(t, 'sut')][key] / cpu[(t, 'sut')]['n'] for t in secs if (t, 'sut') in cpu and key in cpu[(t, 'sut')]]
                return statistics.mean(vals) if vals else None

            def total(secs, key):
                vals = [cpu[(t, 'sut')][key] for t in secs if (t, 'sut') in cpu and key in cpu[(t, 'sut')]]
                return sum(vals) if vals else None
            calm = calm_secs - spike_secs
            print('SUT cpus: spike seconds soft %s sys %s softnet drop %s squeeze %s; other seconds of the same rungs soft %s sys %s drop %s squeeze %s' % (
                fmt(mean(spike_secs, 'soft_pct'), '%'), fmt(mean(spike_secs, 'sys_pct'), '%'),
                fmt(total(spike_secs, 'softnet_dropped')), fmt(total(spike_secs, 'softnet_squeezed')),
                fmt(mean(calm, 'soft_pct'), '%'), fmt(mean(calm, 'sys_pct'), '%'),
                fmt(total(calm, 'softnet_dropped')), fmt(total(calm, 'softnet_squeezed'))))


if __name__ == '__main__':
    main()

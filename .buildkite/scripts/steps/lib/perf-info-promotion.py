#!/usr/bin/env python3
"""Per-rung young-GC promotion for perf-test-info-investigation.sh PART=promotion.

Applies the entry-lifetime rule that set WARN's /20 retention divisor (docs/code/memory-management.md,
"Retention and young-GC promotion") to one cell: an -Xlog:gc* log, the sweep.js result and the 1 s
event-log metric samples. Per rung, over [rung start + settle, rung start + step]:
  promoted_mb_s       ZGC: the young "Promoted:" row at Relocate End; G1: old-region growth in
                      young-only pauses (Mixed pauses also evacuate old regions, so they are counted
                      but not included). A young event whose figure the log does not yield
                      fails the rung ("promotion unparsed"); it never reads as 0
  young_interval_s    window / young collections
  lifetime_s          mean retained entries / eviction rate; ratio = lifetime_s / young_interval_s
  ring_wait_s         mean ring occupancy / eviction rate (time an entry waits before it is retained)
Pure stdlib, Python 3.9+.

  perf-info-promotion.py <gc.log> <sweep.json> <samples.csv> <settle_s> <step_s> <budget_bytes>

Prints one JSON object. Tests: .buildkite/scripts/test/perf-info-investigation-test.sh
"""
import csv
import json
import re
import sys
from datetime import datetime

MB = 1048576.0
TS = re.compile(r'^\[(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d+[+-]\d{4})\]')
SIZE = r'(\d+)M'
Z_YOUNG_END = re.compile(r'GC\((\d+)\) ([yY]): Young Generation ' + SIZE + r'\(\d+%\)->' + SIZE + r'\(\d+%\)')
Z_PROMOTED = re.compile(r'GC\((\d+)\) ([yY]):\s+Promoted:.*?' + SIZE + r' \(\d+%\)\s*$')
Z_ALLOCATED = re.compile(r'GC\((\d+)\) ([yY]):\s+Allocated:.*?' + SIZE + r' \(\d+%\)\s*$')
Z_MAJOR_END = re.compile(r'GC\(\d+\) Major Collection \(.*\) ' + SIZE + r'\(\d+%\)->')
Z_STALL = re.compile(r'Allocation Stall \(')
G_REGION = re.compile(r'Heap Region Size: (\d+)([KMG])')
G_EDEN = re.compile(r'GC\((\d+)\) Eden regions: (\d+)->')
G_OLD = re.compile(r'GC\((\d+)\) Old regions: (\d+)->(\d+)')
G_YOUNG_END = re.compile(r'GC\((\d+)\) Pause Young \(([^)]*)\) .*->')
G_FULL_END = re.compile(r'GC\(\d+\) Pause Full .*->')
G_MARK_END = re.compile(r'GC\(\d+\) Concurrent Mark Cycle \d')


def ts_ms(line):
    m = TS.match(line)
    if not m:
        return None
    return datetime.strptime(m.group(1), '%Y-%m-%dT%H:%M:%S.%f%z').timestamp() * 1000.0


def parse_gc(path):
    """-> (collector, events): each event {t, kind: young|major|stall, promoted_mb, alloc_mb, mixed}."""
    collector, events = None, []
    z = {}  # (gc id, generation letter) -> partial young record
    g = {}  # gc id -> partial G1 pause record
    region_mb, prev_young_after = None, None
    with open(path, encoding='utf-8', errors='replace') as f:
        for line in f:
            t = ts_ms(line)
            if t is None:
                continue
            if 'Initializing The Z Garbage Collector' in line:
                collector = 'ZGC'
            elif 'Using G1' in line:
                collector = 'G1'
            m = G_REGION.search(line)
            if m:
                region_mb = int(m.group(1)) * {'K': 1 / 1024.0, 'M': 1.0, 'G': 1024.0}[m.group(2)]
                continue
            if Z_STALL.search(line):
                events.append({'t': t, 'kind': 'stall'})
                continue
            for rx, key in ((Z_PROMOTED, 'promoted_mb'), (Z_ALLOCATED, 'during_mb')):
                m = rx.search(line)
                if m:
                    z.setdefault((m.group(1), m.group(2).lower()), {})[key] = float(m.group(3))
            m = Z_YOUNG_END.search(line)
            if m:
                rec = z.pop((m.group(1), m.group(2).lower()), {})
                before, after = float(m.group(3)), float(m.group(4))
                alloc = None
                if 'during_mb' in rec:
                    alloc = max((before - prev_young_after if prev_young_after is not None else 0.0) + rec['during_mb'], 0.0)
                prev_young_after = after
                events.append({'t': t, 'kind': 'young', 'promoted_mb': rec.get('promoted_mb'), 'alloc_mb': alloc, 'mixed': False})
                continue
            if Z_MAJOR_END.search(line) or G_FULL_END.search(line) or G_MARK_END.search(line):
                events.append({'t': t, 'kind': 'major'})
                continue
            m = G_EDEN.search(line)
            if m:
                g.setdefault(m.group(1), {})['eden'] = int(m.group(2))
            m = G_OLD.search(line)
            if m:
                g.setdefault(m.group(1), {})['old'] = (int(m.group(2)), int(m.group(3)))
            m = G_YOUNG_END.search(line)
            if m:
                rec = g.pop(m.group(1), {})
                mixed = m.group(2) == 'Mixed'
                old, eden = rec.get('old'), rec.get('eden')
                promoted = None
                if region_mb is not None and old is not None and not mixed:
                    promoted = max(old[1] - old[0], 0) * region_mb
                alloc = eden * region_mb if region_mb is not None and eden is not None else None
                events.append({'t': t, 'kind': 'young', 'promoted_mb': promoted, 'alloc_mb': alloc, 'mixed': mixed})
    return collector, events


def read_samples(path):
    rows = []
    with open(path, encoding='utf-8') as f:
        for r in csv.DictReader(f):
            try:
                rows.append({k: float(v) for k, v in r.items() if v not in ('', None)})
            except ValueError:
                continue
    return [r for r in rows if 'ts_ms' in r]


def mean(xs):
    return sum(xs) / len(xs) if xs else None


def rnd(v, dp=3):
    if v is None:
        return None
    r = round(v, dp)
    return int(r) if r == int(r) else r


def rung(point, events, samples, settle_s, step_s, budget):
    start = point.get('start_epoch_ms')
    out = {'offered_rps': point.get('offered_rps'), 'achieved_rps': point.get('achieved_rps'),
           'p50_ms': point.get('p50_ms'), 'p99_ms': point.get('p99_ms'),
           'dropped_iterations': point.get('dropped_iterations'), 'error_rate': point.get('error_rate')}
    if start is None:
        out['status'] = 'rung did not start'
        return out
    lo, hi = start + settle_s * 1000.0, start + step_s * 1000.0
    window_s = (hi - lo) / 1000.0
    ev = [e for e in events if lo <= e['t'] < hi]
    young = [e for e in ev if e['kind'] == 'young']
    # A young event whose figure the log did not yield makes the window's sum unknown, never zero.
    unmixed = [e for e in young if not e.get('mixed')]
    promoted = [e['promoted_mb'] for e in unmixed] if unmixed and all(e.get('promoted_mb') is not None for e in unmixed) else None
    allocs = [e['alloc_mb'] for e in young] if young and all(e.get('alloc_mb') is not None for e in young) else None
    out.update({'window_s': rnd(window_s, 1), 'young_collections': len(young),
                'mixed_pauses': sum(1 for e in young if e.get('mixed')),
                'major_collections': sum(1 for e in ev if e['kind'] == 'major'),
                'allocation_stalls': sum(1 for e in ev if e['kind'] == 'stall'),
                'promoted_mb_s': rnd(sum(promoted) / window_s) if promoted is not None else None,
                'alloc_mb_s': rnd(sum(allocs) / window_s, 1) if allocs else None,
                'young_interval_s': rnd(window_s / len(young)) if young else None,
                'alloc_mb_per_young': rnd(mean(allocs), 1) if allocs else None})
    s = [r for r in samples if lo <= r['ts_ms'] < hi]
    out['samples'] = len(s)
    if len(s) < 2 or not young:
        out['status'] = 'fewer than two metric samples' if len(s) < 2 else 'no young collection in the window'
        return out
    if promoted is None or allocs is None:
        out['status'] = 'promotion unparsed' if promoted is None else 'allocation unparsed'
        return out
    dt = (s[-1]['ts_ms'] - s[0]['ts_ms']) / 1000.0
    evict = (s[-1].get('evicted', 0) - s[0].get('evicted', 0)) / dt if dt > 0 else 0.0
    retained = mean([r.get('retained_entries', 0.0) for r in s])
    ring = mean([r.get('ring_occupancy', 0.0) for r in s])
    lifetime = retained / evict if evict > 0 else None
    ring_wait = ring / evict if evict > 0 else None
    interval = window_s / len(young)
    out.update({'eviction_rate': rnd(evict, 1), 'retained_entries': rnd(retained, 0),
                'retained_fraction_of_budget': rnd(mean([r.get('retained_bytes', 0.0) for r in s]) / budget) if budget else None,
                'ring_occupancy': rnd(ring, 0), 'in_flight_mb': rnd(mean([r.get('in_flight_bytes', 0.0) for r in s]) / MB, 1),
                'dropped_ring_full': rnd(s[-1].get('dropped_ring_full', 0) - s[0].get('dropped_ring_full', 0), 0),
                'dropped_in_flight_bytes': rnd(s[-1].get('dropped_in_flight_bytes', 0) - s[0].get('dropped_in_flight_bytes', 0), 0),
                'lifetime_s': rnd(lifetime), 'ring_wait_s': rnd(ring_wait),
                'lifetime_over_young_interval': rnd(lifetime / interval) if lifetime is not None else None,
                'ring_wait_over_young_interval': rnd(ring_wait / interval) if ring_wait is not None else None,
                'lifetime_alloc_over_budget': rnd(lifetime * sum(allocs) / window_s * MB / budget)
                if lifetime is not None and allocs and budget else None})
    out['status'] = 'measured' if lifetime is not None else 'no eviction in the window'
    return out


def main(argv):
    if len(argv) != 7:
        print(__doc__, file=sys.stderr)
        return 2
    gc_log, sweep_path, samples_path = argv[1], argv[2], argv[3]
    settle_s, step_s, budget = float(argv[4]), float(argv[5]), float(argv[6])
    collector, events = parse_gc(gc_log)
    with open(sweep_path, encoding='utf-8') as f:
        sweep = json.load(f)
    samples = read_samples(samples_path)
    rungs = [rung(p, events, samples, settle_s, step_s, budget) for p in sweep.get('points') or []]
    healthy = [r['offered_rps'] for r in rungs if r.get('p50_ms') is not None and r['p50_ms'] <= 1.0]
    print(json.dumps({'collector': collector, 'young_collections': sum(1 for e in events if e['kind'] == 'young'),
                      'rungs': rungs, 'highest_rung_p50_le_1ms': max(healthy) if healthy else None,
                      'max_promoted_mb_s': max([r['promoted_mb_s'] for r in rungs if r.get('promoted_mb_s') is not None], default=None),
                      'max_lifetime_over_young_interval': max([r['lifetime_over_young_interval'] for r in rungs
                                                               if r.get('lifetime_over_young_interval') is not None], default=None)},
                     separators=(',', ':')))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))

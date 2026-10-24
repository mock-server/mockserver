#!/usr/bin/env python3
"""Regenerate p95-history-<from>-<to>.json and the historical upper-mode shares.

Input: a directory of perf-result.json artifacts named <build>.json, fetched per build with
  bk artifacts list <N> -p mockserver-performance-test        (find perf-result.json's id)
  bk artifacts download <ID> --build <N> -p mockserver-performance-test

It rebuilds the windows the way perf-test-compare.sh does: the persisted history (master, valid,
baseline-eligible, perf queue, fresh image) in timestamp order, the prior 10 runs, filtered to the
head's instance type plus its k6 arm set and main event-log method (or, for info_* metrics, its
INFO event-log method), and a window needs 5 runs. Usage:
  generate-history.py <artifact-dir> [--from 597] > p95-history.json
The shares (stderr) are the upper-mode fraction of every persisted run, split at the largest ratio
between neighbouring sorted values with
at least a tenth of the runs on each side; copy them into the budget's bimodal.metrics.
"""
import glob
import json
import os
import sys

METRICS = {  # fixture key -> (arm, under the INFO arm?)
    "match_http.p95_ms": ("match_http", False),
    "template_http.p95_ms": ("template_http", False),
    "info_match_http.p95_ms": ("match_http", True),
    "info_match_https_h2.p95_ms": ("match_https_h2", True),
}
WINDOW, MIN_BASELINE = 10, 5


def persisted(r):
    return (r.get("branch") == "master" and (r.get("validity") or {}).get("valid") is True
            and r.get("baseline_eligible", True) is not False
            and (r.get("agent") or {}).get("queue", "perf") == "perf"
            and not (r.get("config") or {}).get("image_stale"))


def info_measured(r):
    return (r.get("info_log_level_arm") or {}).get("measured") is True


def value(r, arm, info):
    beh = ((r.get("info_log_level_arm") or {}).get("behaviours") if info else r.get("behaviours")) or {}
    return (beh.get(arm) or {}).get("p95_ms")


def fingerprint(r, info):
    method = lambda c: ((c or {}).get("event_log_budget") or {}).get("method", "fixed-268435456")
    instance = (r.get("agent") or {}).get("instance_type")
    if info:
        return (instance, method((r.get("info_log_level_arm") or {}).get("config")))
    return (instance, tuple(sorted((r.get("behaviours") or {}).keys())), method(r.get("config")))


def upper_share(values):
    s = sorted(v for v in values if v > 0)
    edge = max(1, len(s) // 10)  # a mode is at least a tenth of the runs, so one outlier cannot be one
    gaps = [(s[k + 1] / s[k], k) for k in range(edge - 1, len(s) - edge)]
    _, k = max(gaps, key=lambda g: g[0])
    return (len(s) - k - 1) / len(s)


def main():
    args = sys.argv[1:]
    start = int(args[args.index("--from") + 1]) if "--from" in args else 597
    runs = []
    for f in glob.glob(os.path.join(args[0], "*.json")):
        r = json.load(open(f))
        r["_build"] = int(os.path.basename(f)[:-5])
        runs.append(r)
    runs.sort(key=lambda r: r.get("timestamp_utc", ""))
    history = [r for r in runs if persisted(r)]
    out = {"_comment": [
        "Real p95 history from mockserver-performance-test: for each persisted run, its head value and",
        "the comparable baseline window the compare step used (prior persisted runs on the same instance",
        "type, k6 arm set and event-log budget). Written by generate-history.py; do not hand-edit."],
        "metrics": {}}
    for key, (arm, info) in METRICS.items():
        rows = []
        for i, head in enumerate(history):
            v = value(head, arm, info)
            if head["_build"] < start or v is None or (info and not info_measured(head)):
                continue
            prior = [r for r in history[max(0, i - WINDOW):i] if fingerprint(r, info) == fingerprint(head, info)
                     and (not info or info_measured(r))]
            window = [x for x in (value(r, arm, info) for r in prior) if x is not None]
            if len(window) >= MIN_BASELINE:
                rows.append({"build": head["_build"], "head": v, "baseline": window})
        out["metrics"][key] = rows
        every = [x for x in (value(r, arm, info) for r in history if not info or info_measured(r)) if x is not None]
        print(f"{key}: historical upper-mode share {upper_share(every):.2f} over {len(every)} runs", file=sys.stderr)
    lines = ["{", '  "_comment": ' + json.dumps(out["_comment"]) + ",", '  "metrics": {']
    keys = list(out["metrics"])
    for i, key in enumerate(keys):
        rows = out["metrics"][key]
        lines.append("    " + json.dumps(key) + ": [")
        lines += ["      " + json.dumps(w, separators=(", ", ": ")) + ("," if j < len(rows) - 1 else "") for j, w in enumerate(rows)]
        lines.append("    ]" + ("," if i < len(keys) - 1 else ""))
    print("\n".join(lines + ["  }", "}"]))


if __name__ == "__main__":
    main()

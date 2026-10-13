# perf-rw-cross-check.jq: the rw-multi-k6 cross-check (performance programme item 31).
# Input {live, pub, bytime, bytag, bytagcut, main, xcost, tol}, built by rw-multi-k6-sweep.sh
# (live: true) from its work files; a fixture or an offline re-derivation feeds the same object.
# Output: the cross_check object (same_requests gates rw_cross_check_same_requests).
#
# by_time cannot hold exactly the published requests: its cut is the first push at or after the
# settle boundary, so it drops up to two push intervals of steady requests. bytagcut is by_tag
# (the published requests) from the same cut on, so by_time must match it within the histogram
# tolerances; the two differ only by settle requests still in flight at the cut.
#
# cross_run judges a rung only when each of the N (> 1) processes offers at least
# tol.cross_run.min_per_process_rps: below that rate latency and cost per request still fall
# with each process's own rate, so the arms differ by generator layout, not run-to-run noise.
# Lower rungs are reported with their figures and ok:null; agrees is null when none is judged.
def num: type == "number";
.tol as $tol | $tol.abs_ms as $abs | (.live == true) as $live
| def within($a; $b; $t): ($a|num) and ($b|num) and ((($a - $b)|fabs) <= ([($t * ([$a, $b]|max)), $abs]|max));
  def ratio($a; $o): if ($a|num) and ($o|num) and $o > 0 then $a / $o else null end;
  def r3: if . == null then null else (. * 1000 | round) / 1000 end;
  def cmp($x; $y; $t5; $t9; $t99): {
      p50:{published:$x.p50_ms, remote_write:$y.p50_ms, ok:within($x.p50_ms; $y.p50_ms; $t5)},
      p95:{published:$x.p95_ms, remote_write:$y.p95_ms, ok:within($x.p95_ms; $y.p95_ms; $t9)},
      p99:{published:$x.p99_ms, remote_write:$y.p99_ms, ok:within($x.p99_ms; $y.p99_ms; $t99)}};
  # Requests the time cut moved: excluded (d > 0) up to 2 push intervals at the offered rate, or
  # added (d < 0, settle requests completing after the cut) up to the in-flight pool.
  def window($x; $t):
    ($x.measured_sample_count) as $n | ($t.measured_sample_count) as $m
    | (if ($n|num) and ($m|num) then $n - $m else null end) as $d
    | ([ ($t.per_process // [])[] | .pool | select(num) ] | add // 0) as $pool
    | ((($x.offered_rps // 0) * 2 * $tol.push_s * (1 + $tol.window_tol)) | floor) as $max
    | {excluded:$d, of:$n,
       fraction:(if $d != null and ($n|num) and $n > 0 then (($d / $n) * 100000 | round) / 100000 else null end),
       max_excluded:$max, max_added:$pool, settle_cut_ok:$t.settle_cut_ok,
       ok:($d != null and $n > 0 and $d <= $max and $d >= -$pool and $t.settle_cut_ok == true)};
  def quantiles: [["p50", 0.5, $tol.p50], ["p95", 0.95, $tol.p95], ["p99", 0.99, $tol.p99]];
  # by_time against by_tag at the cut ($c). The count may differ only by settle requests still in
  # flight at the cut (0..the VU pool). Offline, a run that predates bytagcut is held to the
  # published quantiles at the same tolerances; a live run without it fails.
  def tcmp($x; $t; $c; $w):
    ($c.measured_sample_count|num) as $hasc
    | (if $hasc then $c elif $live then null else $x end) as $ref
    | (if $hasc and ($t.measured_sample_count|num) then $t.measured_sample_count - $c.measured_sample_count else null end) as $extra
    | {reference:(if $hasc then "by_tag_at_cut" elif $live then null else "published" end),
       counts:{by_time:$t.measured_sample_count, by_tag_at_cut:$c.measured_sample_count, settle_after_cut:$extra,
               max_settle_after_cut:$w.max_added,
               ok:(if $hasc then ($extra != null and $extra >= 0 and $extra <= $w.max_added) else $ref != null end)}}
    | reduce quantiles[] as [$name, $q, $tq] (.;
        ($t[$name + "_ms"]) as $b | (if $ref == null then null else $ref[$name + "_ms"] end) as $r
        | .[$name] = {published:$x[$name + "_ms"], by_tag_at_cut:$c[$name + "_ms"], remote_write:$b,
                      ok:within($r; $b; $tq)});
  (.pub.points // []) as $P
  | (.bytime.points // []) as $T
  | (.bytag.points // []) as $G
  | (.bytagcut.points // []) as $C
  | [ range(0; $P|length) as $k
      | ($P[$k]) as $x | ($T[$k] // {}) as $t | ($G[$k] // {}) as $g | ($C[$k] // {}) as $c
      | window($x; $t) as $w
      | {offered_rps:$x.offered_rps,
         counts:{published:$x.sample_count, prometheus:$t.sample_count,
                 ok:(($x.sample_count|num) and $x.sample_count == $t.sample_count)},
         measured_counts:{published:$x.measured_sample_count, by_tag:$g.measured_sample_count, by_time:$t.measured_sample_count},
         window:$w,
         by_time:tcmp($x; $t; $c; $w),
         by_tag:cmp($x; $g; $tol.p50; $tol.p95; $tol.p99)}
      | .by_time += {ok:(.by_time.counts.ok and .by_time.p50.ok and .by_time.p95.ok and .by_time.p99.ok)}
      | .by_tag += {same_count:(($x.measured_sample_count|num) and $x.measured_sample_count == $g.measured_sample_count)}
      | .by_tag += {ok:(.by_tag.same_count and .by_tag.p50.ok and .by_tag.p95.ok and .by_tag.p99.ok)}
      | . + {ok:(.counts.ok and .by_tag.ok and .window.ok and .by_time.ok)} ] as $same
  | ($tol.cross_run.min_per_process_rps // 0) as $minpp
  | [ (.main.points // [])[] as $m
      | ([ $P[] | select(.offered_rps == $m.nominal_agg_offered_rps) ] | first) as $x
      | (($m.per_process // []) | length) as $np
      | (if $np > 0 and ($m.offered_rps|num) then $m.offered_rps / $np else null end) as $pp
      | if $x == null then
          {offered_rps:$m.nominal_agg_offered_rps, main_offered_rps:$m.offered_rps, status:"no counterpart", ok:null}
        else
          ratio($x.achieved_rps; $x.offered_rps) as $rs | ratio($m.achieved_rps; $m.offered_rps) as $rm
          | {offered_rps:$x.offered_rps, main_offered_rps:$m.offered_rps, per_process_rps:($pp|r3),
             status:(if $rs == null or $rm == null then "incomplete" else "compared" end),
             achieved_ratio:{single:($rs|r3), multi:($rm|r3),
                             ok:($rs != null and $rm != null and (($rs - $rm)|fabs) <= $tol.cross_run.achieved_ratio)},
             latency:cmp($x; $m; $tol.cross_run.p50; $tol.cross_run.p95; $tol.cross_run.p99)}
          | . + {ok:(.achieved_ratio.ok and .latency.p50.ok and .latency.p95.ok and .latency.p99.ok)}
          | if $np > 1 and $pp != null and $pp < $minpp then . + {status:"below per-process floor", ok:null} else . end
        end ] as $run
  | ([ $run[] | select(.status == "compared" or .status == "incomplete") ]) as $cmp
  | {attempted:true,
     note:"same_requests: one k6 in the published summary mode ALSO remote-writing. by_tag (the Prometheus merge of exactly its steady requests) must match its summary within the histogram tolerances; by_time (the time cut under test) may move up to 2 push intervals of requests (window) and must match by_tag from the same cut on within the same tolerances, holding at most the VU pool more requests (settle requests in flight at the cut). published vs by_tag_at_cut is what the late cut itself moved (report-only). cross_run: the N-process rungs vs that single-process run at the same aggregate offered rate (separate runs, so run-to-run noise), judged only where each of the N processes offers at least min_per_process_rps; below it the arms sit in different load regimes, so those rungs are reported, not judged, and agrees is null when no rung is judged; rungs above the cross-check cap have no counterpart.",
     tolerances:{same_requests:{p50:$tol.p50, p95:$tol.p95, p99:$tol.p99, abs_ms:$abs,
                                by_time_reference:"by_tag from the same settle cut (by_tag_at_cut); offline, a run without it uses the published quantiles"},
                 cross_run:($tol.cross_run + {abs_ms:$abs})},
     same_requests:{rungs:$same, equivalent:(($same|length) > 0 and all($same[]; .ok)),
                    accounting_ok:($T as $bp | ($bp|length) > 0 and all($bp[]; .accounting_ok)),
                    failed:[ $same[] | select(.ok|not) | . as $r
                             | "\($r.offered_rps): " + ([ (if $r.counts.ok then empty else "counts \($r.counts.published) vs \($r.counts.prometheus)" end),
                                  (if $r.by_tag.ok then empty else "by_tag (counts \($r.measured_counts.published) vs \($r.measured_counts.by_tag); " + ([ quantiles[] as [$n, $q, $tq] | $r.by_tag[$n] | select(.ok|not) | "\($n) \(.published) vs \(.remote_write)" ] | join(", ")) + ")" end),
                                  (if $r.window.ok then empty else "window (excluded \($r.window.excluded) of \($r.window.of), allowed -\($r.window.max_added)..\($r.window.max_excluded), settle_cut_ok \($r.window.settle_cut_ok))" end),
                                  (if $r.by_time.ok then empty else "by_time vs \($r.by_time.reference // "no by_tag at the cut") (" + ([ (if $r.by_time.counts.ok then empty else "counts \($r.by_time.counts.by_time) vs \($r.by_time.counts.by_tag_at_cut), allowed 0..\($r.by_time.counts.max_settle_after_cut) more" end), (quantiles[] as [$n, $q, $tq] | $r.by_time[$n] | select(.ok|not) | "\($n) \(.remote_write) vs " + (if $r.by_time.reference == "published" then "\(.published)" else "\(.by_tag_at_cut) (published \(.published))" end)) ] | join(", ")) + ")" end) ] | join("; ")) ]},
     cross_run:{rungs:$run, agrees:(if ($cmp|length) == 0 then null else all($cmp[]; .ok) end),
                compared:([ $run[] | select(.status == "compared") ] | length),
                below_floor:([ $run[] | select(.status == "below per-process floor") ] | length),
                no_counterpart:([ $run[] | select(.status == "no counterpart") ] | length)},
     single_process_cpu_us_per_request:((.xcost // [])[0].cpu_us_per_request // null)}
  | . + {equivalent:(.same_requests.equivalent and .same_requests.accounting_ok)}

#!/usr/bin/env bash
#
# One-time CROSS-VERSION per-connection memory comparison — the measurement that
# actually answers the 8.0.0 changelog's warning that the HTTP/2-multiplex migration changed per-connection
# memory. It is NOT part of the recurring perf run: it needs two published server images (a pre-8.0.0 and an
# 8.0.0+), so it is opt-in and manual/one-time.
#
# Method: drive the SAME external h2c client (Http2ConnectionMemoryBenchmark `hold` mode) against each image
# in turn, holding the client side constant, and diff the server CONTAINER'S RSS (docker stats) per
# established connection. Because the client is identical across images, the difference in per-connection RSS
# is attributable to the server-side change. RSS (not heap) is used because it is the one measure available
# uniformly for any published image, and it captures heap + Netty direct buffers + native — the whole
# per-connection footprint.
#
# Isolation of connection cost from event-log cost: the requests are bodyless GETs whose matched response is
# held open by a long delay (nothing large is retained), and the log is cleared before sampling. Establishment
# is proven per shape by the client opening C DISTINCT sockets (C*S in-flight streams is impossible on fewer
# than ceil(C*S/100) connections given MAX_CONCURRENT_STREAMS=100). The h2 stream path is WARMED before the
# idle baseline so first-traffic JVM warm-up is not mis-charged to the measured connections.
#
#   H2_MEM_COMPARE_IMAGES="mockserver/mockserver:7.6.0 mockserver/mockserver:mockserver-8.0.0" \
#     ./run-h2-connection-memory-compare.sh
#
# Env: H2_MEM_COMPARE_IMAGES (required, space/comma-separated), H2_MEM_COMPARE_SHAPES (default "1x1 10x10
# 100x10"), H2_MEM_COMPARE_MEM (container memory cap, default 512m), H2_MEM_COMPARE_REPEATS (default 3).
#
# Requires: docker; the benchmark compiled (target/classes + target/classpath.txt — run `mvn compile
# dependency:build-classpath -Dmdep.outputFile=target/classpath.txt` first); python3; curl; jq-free.
set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CP="$DIR/target/classes:$(cat "$DIR/target/classpath.txt")"
MEM_LIMIT="${H2_MEM_COMPARE_MEM:-512m}"
SHAPES="${H2_MEM_COMPARE_SHAPES:-1x1 10x10 100x10}"
REPEATS="${H2_MEM_COMPARE_REPEATS:-3}"
IMAGES_RAW="${H2_MEM_COMPARE_IMAGES:?set H2_MEM_COMPARE_IMAGES to two or more image tags}"
IMAGES="${IMAGES_RAW//,/ }"
RES="$(mktemp)"

mib_to_bytes() {
  local v="${1%%/*}"; v="${v// /}"
  python3 - "$v" <<'PY'
import sys,re
m=re.match(r'([0-9.]+)([KMG]i?B)',sys.argv[1])
val=float(m.group(1)); unit=m.group(2)
mult={'KiB':1024,'MiB':1024**2,'GiB':1024**3,'B':1,'KB':1000,'MB':1000**2,'GB':1000**3}[unit]
print(int(val*mult))
PY
}

median() { python3 -c "import sys,statistics; xs=[int(x) for x in sys.argv[1:]]; print(int(statistics.median(xs)))" "$@"; }
spread_pct() { python3 -c "import sys; xs=[int(x) for x in sys.argv[1:]]; import statistics; m=statistics.median(xs); print('%.1f'%(0 if m==0 else 100.0*(max(xs)-min(xs))/m))" "$@"; }

sample_rss_min() { # cid nsamples -> min bytes
  local cid="$1" n="$2" min="" raw b i
  for ((i=0;i<n;i++)); do
    raw=$(docker stats --no-stream --format '{{.MemUsage}}' "$cid" 2>/dev/null)
    [ -z "$raw" ] && { sleep 0.6; continue; }
    b=$(mib_to_bytes "$raw")
    if [ -z "$min" ] || [ "$b" -lt "$min" ]; then min="$b"; fi
    sleep 0.6
  done
  echo "${min:-0}"
}

run_once() { # image shape -> per_conn bytes (echoed)
  local img="$1" shape="$2" C S cid hp code logf jpid idle loaded per_conn
  C="${shape%%x*}"; S="${shape##*x}"
  cid=$(docker run -d -m "$MEM_LIMIT" -p 0:1080 "$img" -serverPort 1080 2>/dev/null)
  hp=$(docker port "$cid" 1080/tcp | head -1 | sed 's/.*://')
  for _ in $(seq 1 30); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "http://127.0.0.1:$hp/mockserver/status" 2>/dev/null)
    [ "$code" = "200" ] && break; sleep 0.5
  done
  for _ in $(seq 1 40); do curl -s -o /dev/null -X PUT "http://127.0.0.1:$hp/mockserver/status"; done
  # Warm the HTTP/2 stream path so its one-time class-load/JIT/buffer-pool cost is not charged to the
  # measured connections (without this the small shapes are swamped by first-h2-traffic warm-up).
  H2_MEM_CONFIRM_TIMEOUT_S=4 java -cp "$CP" org.mockserver.benchmark.Http2ConnectionMemoryBenchmark \
    hold 127.0.0.1 "$hp" 10 10 1500 >/dev/null 2>&1
  sleep 3
  idle=$(sample_rss_min "$cid" 5)
  logf=$(mktemp)
  java -cp "$CP" org.mockserver.benchmark.Http2ConnectionMemoryBenchmark hold 127.0.0.1 "$hp" "$C" "$S" 12000 >"$logf" 2>&1 &
  jpid=$!
  for _ in $(seq 1 80); do grep -q "^HELD" "$logf" && break; sleep 0.25; done
  loaded=$(sample_rss_min "$cid" 6)
  wait "$jpid"
  if ! grep -q "^HELD" "$logf"; then echo "ESTABLISH-FAILED" >&2; cat "$logf" >&2; fi
  per_conn=$(( (loaded - idle) / C ))
  rm -f "$logf"
  docker rm -f "$cid" >/dev/null 2>&1
  echo "$per_conn"
}

echo "=== cross-version per-connection RSS (mem=$MEM_LIMIT, repeats=$REPEATS) ==="
echo "images: $IMAGES"
for img in $IMAGES; do
  for shape in $SHAPES; do
    samples=()
    for ((r=0;r<REPEATS;r++)); do samples+=("$(run_once "$img" "$shape")"); done
    med=$(median "${samples[@]}"); sp=$(spread_pct "${samples[@]}")
    printf "%-40s %-8s median=%12d bytes/conn  spread=%5s%%  samples=[%s]\n" "$img" "$shape" "$med" "$sp" "${samples[*]}"
    echo "$img $shape $med" >> "$RES"
  done
done

echo ""
echo "=== summary: per-connection RSS bytes by shape ==="
set -- $IMAGES
printf "%-8s" "shape"; for img in $IMAGES; do printf " %26s" "$img"; done; echo
for shape in $SHAPES; do
  printf "%-8s" "$shape"
  for img in $IMAGES; do
    v=$(awk -v i="$img" -v s="$shape" '$1==i && $2==s {print $3}' "$RES")
    printf " %26d" "${v:-0}"
  done
  echo
done
rm -f "$RES"

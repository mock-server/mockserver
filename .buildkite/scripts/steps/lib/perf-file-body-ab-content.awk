# The FILE-body A/B's response body: exactly `bytes` bytes of JSON, {"marker":"fb-ab-<label>","pad":"xxx..."}.
# mockserver-performance-test/k6/file_body_ab.js builds the same string for the inline arm (fbContent)
# and its setup refuses to measure unless the two are byte-identical. Change both together.
# Usage: awk -v label=1kb -v bytes=1024 -f perf-file-body-ab-content.awk > fb-ab-1kb.json
BEGIN {
  head = "{\"marker\":\"fb-ab-" label "\",\"pad\":\""
  tail = "\"}"
  pad = bytes - length(head) - length(tail)
  if (label == "" || bytes !~ /^[0-9]+$/ || pad < 0) { print "perf-file-body-ab-content.awk: bad label or bytes" > "/dev/stderr"; exit 2 }
  printf "%s", head
  for (i = 0; i < pad; i++) printf "x"
  printf "%s", tail
}

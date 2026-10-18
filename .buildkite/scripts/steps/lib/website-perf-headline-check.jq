# website-perf-headline-check.jq: does the hand-written headline text agree with the published figure?
# Inputs (from website-perf-headline-lint.sh): $fig [the page data] or [] when unreadable; $places TSV
# "kind<TAB>file:line<TAB>text"; $expected, $adjacent the rung pair perf-website-publish.sh names.
# Output: an array of "file:line: problem" strings, [] when the text agrees. "about N" states the
# headline and must be the ceiling; other rates, latencies and core counts must be published figures.

def commas: if . < 1000 then tostring else ((. / 1000 | floor) | commas) + "," + ((. % 1000) | tostring | ("00" + .)[-3:]) end;
def isnum: type == "number";
def whole: isnum and . == floor and . > 0;
def near($v; $h): ($v | isnum) and ((. - $v) | fabs) <= $h + 0.000001;
def decimals: if test("\\.") then split(".")[1] | length else 0 end;
def units: "req/s|req/sec|requests/sec|requests per second|requests a second|rps";
def rate_re: "(?<claim>(?:\\babout|\\bapproximately|~|\\bceiling of(?:\\s+about)?)\\s*)?(?<![\\d.,])(?<num>\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?(?!\\d)|\\d+(?:\\.\\d+)?\\s?[kK]\\b|\\d{4,}(?=\\s?(?i:" + units + ")\\b))";
def lat_re: "(?<![\\d.,])(?<num>\\d+(?:\\.\\d+)?)\\s?ms\\b";
# The count and up to three words before it, so "twenty-one" and "a hundred and two" are read whole.
def tally_re: "(?<![\\w.,-])(?<n>(?:[A-Za-z]+[ -]){0,3}(?:[A-Za-z]+|[0-9][0-9,]*))\\s+(?i:like-for-like\\s+runs?)\\b";
def count_words: {zero:0, one:1, two:2, three:3, four:4, five:5, six:6, seven:7, eight:8, nine:9, ten:10,
  eleven:11, twelve:12, thirteen:13, fourteen:14, fifteen:15, sixteen:16, seventeen:17, eighteen:18, nineteen:19,
  twenty:20, thirty:30, forty:40, fifty:50, sixty:60, seventy:70, eighty:80, ninety:90};
def numberish: type == "string" and (. as $w | (count_words | has($w))
  or test("^(?:[0-9][0-9,]*|[a-z]+teen|(?:twen|thir|fou?r|fif|six|seven|eigh|nine?)ty|hundreds?|thousands?|millions?|dozens?|score)$"));
# A tally match -> {w: the count as written, v: its value, or null when it reads as a number it cannot parse};
# empty when the word before "like-for-like" is not a number at all ("many", "the").
def tally_of: ascii_downcase as $n | [ $n | splits("[ -]+") ] as $t | ($t | length) as $len
  | $t[$len - 1] as $u | (if $len > 1 then $t[$len - 2] else "" end) as $p
  | (if ($u | test("^[0-9][0-9,]*$")) then {w: $u, v: ($u | gsub(","; "") | tonumber), i: ($len - 1)}
     elif (count_words[$u] // 0) >= 1 and count_words[$u] <= 9 and (count_words[$p] // 0) >= 20
       then {w: "\($p)-\($u)", v: (count_words[$p] + count_words[$u]), i: ($len - 2)}
     elif count_words[$u] != null then {w: $u, v: count_words[$u], i: ($len - 1)}
     elif ($u | numberish) then {w: $n, v: null, i: 0}
     else empty end)
  | (if .i >= 1 then $t[.i - 1] else "" end) as $b1 | (if .i >= 2 then $t[.i - 2] else "" end) as $b2
  | if .v != null and (($b1 | numberish) or ($b1 == "and" and ($b2 | numberish))) then {w: $n, v: null} else {w, v} end;
def words: {one:1, two:2, three:3, four:4, five:5, six:6, seven:7, eight:8, nine:9, ten:10, eleven:11, twelve:12,
            sixteen:16, twenty:20, "twenty-four":24, "thirty-two":32, "forty-eight":48, "sixty-four":64};
def core_re($digits): "(?i)\\b(?<n>" + (if $digits then "[0-9]+|" else "" end)
  + "twenty-four|thirty-two|forty-eight|sixty-four|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|sixteen|twenty)[- ](?:CPU |vCPU |physical )?cores?\\b";
# A rate token -> {v: value, h: half its written precision}: "144,000" is +/-500, "143,700" +/-50, "143.7k" +/-50.
def rate_of: gsub("\\s"; "") as $t
  | if ($t | test("[kK]$")) then ($t[:-1]) as $n | {v: (($n | tonumber) * 1000), h: (0.5 * pow(10; 3 - ($n | decimals)))}
    else ($t | gsub(","; "")) as $n
      | if ($n | decimals) > 0 then {v: ($n | tonumber), h: (0.5 * pow(10; 0 - ($n | decimals)))}
        else {v: ($n | tonumber), h: (0.5 * pow(10; [($n | capture("(?<z>0*)$").z | length), 3] | min))} end
    end;
def norm: gsub("&nbsp;"; " ") | gsub("\u00a0"; " ");

($fig | if length == 1 then .[0] else null end) as $F
| [ $places | split("\n")[] | select(length > 0) | split("\t") | {kind: .[0], where: .[1], text: ((.[2:] | join(" ")) | norm)} ] as $P
| [ $P[] | select(.kind == "description" or .kind == "llms" or (.kind == "faq" and (.text | test("\\b(?:" + units + ")\\b"; "i")))) ] as $H
| ($F | if type == "object" then (.headline // null) else null end) as $h
| [
    (if $F == null then "perf_figures.json: not one JSON document, so there is no published figure to check against"
     elif ($F | type) != "object" then "perf_figures.json: is \($F | type), not an object"
     elif ($h | type) != "object" then "perf_figures.json: has no headline object"
     else
       (if ($h.healthy_ceiling_rps | whole) | not then "perf_figures.json: headline.healthy_ceiling_rps is \($h.healthy_ceiling_rps | tojson), not a whole number of req/s" else empty end),
       (if ($h.healthy_ceiling_rps_display | type) != "string" then "perf_figures.json: headline.healthy_ceiling_rps_display is \($h.healthy_ceiling_rps_display | tojson), not a string"
        elif ($h.healthy_ceiling_rps | whole) and $h.healthy_ceiling_rps_display != ($h.healthy_ceiling_rps | commas)
        then "perf_figures.json: headline.healthy_ceiling_rps_display \($h.healthy_ceiling_rps_display | tojson) is not headline.healthy_ceiling_rps \($h.healthy_ceiling_rps)"
        else empty end),
       (if ($h.server_cores | whole) | not then "perf_figures.json: headline.server_cores is \($h.server_cores | tojson), not a whole number of cores" else empty end)
     end)
  ] as $data
| if ($data | length) > 0 then $data else
  $h.healthy_ceiling_rps as $hc
  | ($expected | tonumber? // null) as $ex | ($adjacent | tonumber? // null) as $ad
  | ([ $P[] | select(.kind == "body") | .text | scan("\\bhc == (\\d+)") | .[0] | tonumber ] | unique) as $gate
  | ([ $ex, $ad ] | map(select(. != null)) | unique) as $pair
  | ($F.headline_rule != null and ($pair | length) == 2 and $gate == $pair and ($pair | index($hc)) != null) as $alt_ok
  | ([ $hc ] + (if $alt_ok then $pair else [] end)
     + [ $h.sub_ms_p95_through_rps | select(isnum) ]) as $exact
  | ([ $h.healthy_ceiling_achieved_rps | select(isnum) ]) as $rounded
  | ([ $h.healthy_ceiling_p50_ms, $h.healthy_ceiling_p95_ms, $h.healthy_ceiling_p99_ms, $h.healthy_ceiling_p99_whole_rung_ms, $h.p99_max_ms ]
     | map(select(isnum))) as $lat
  | ("headline \($h.healthy_ceiling_rps_display)"
     + (if $alt_ok then ", the other rung the qualifying runs read \($pair - [$hc] | .[0] | commas)" else "" end)
     + (if ($h.sub_ms_p95_through_rps | isnum) then ", sub-millisecond p95 through \($h.sub_ms_p95_through_rps | commas)" else "" end)
     + (if ($rounded | length) > 0 then ", achieved \($rounded[0])" else "" end)) as $allowed
  | [
      ($P[] | select(.kind == "unreadable") | "\(.where): \(.text)"),
      (if ([ $P[] | select(.kind == "description") ] | length) != 1
       then "performance.html: the front matter has \([ $P[] | select(.kind == "description") ] | length) description lines, not one" else empty end),
      (if ([ $H[] | select(.kind == "faq") ] | length) == 0 then "performance.html: no schema_faq answer states a rate, so the FAQ headline cannot be checked" else empty end),
      (if ([ $P[] | select(.kind == "llms") ] | length) == 0 then "llms.txt: no line links mock_server/performance.html, so its headline cannot be checked" else empty end),
      (if ([ $pair[] ] | length) != 2 then "perf-website-publish.sh: RW_EXPECTED_CEILING_RPS and RW_ADJACENT_CEILING_RPS do not name two different rungs (read \($expected | tojson), \($adjacent | tojson))" else empty end),
      (if ($gate | length) > 0 and ($pair | length) == 2 and $gate != $pair
       then ([ $P[] | select(.kind == "body" and (.text | test("\\bhc == \\d"))) | .where ][0]) + ": the run-tally gate names \($gate | map(commas) | join(" and ")) but perf-website-publish.sh names \($pair | map(commas) | join(" and "))" else empty end),
      ($H[] | . as $p
        | [ .text | capture(rate_re; "g") ] as $rates
        | (if ([ $rates[] | select(.claim != null) ] | length) == 0
           then "\(.where): states no headline (\"about \($h.healthy_ceiling_rps_display) requests/sec\")" else empty end),
          ($rates[] | (.num | rate_of) as $r
           | if .claim != null then
               (if $r.v != $hc then "\($p.where): states the headline as \(.num | gsub("\\s"; "")); perf_figures.json has \($h.healthy_ceiling_rps_display)" else empty end)
             elif ([ $exact[] | select(. == $r.v) ] | length) > 0 or ([ $rounded[] as $v | $r.v | select(near($v; $r.h)) ] | length) > 0 then empty
             else "\($p.where): \(.num | gsub("\\s"; "")) is not a published figure (\($allowed))" end),
          ($p.text | capture(lat_re; "g") | .num as $n | ($n | tonumber) as $v | (0.5 * pow(10; 0 - ($n | decimals))) as $hh
           | if ([ $lat[] as $l | $v | select(near($l; $hh)) ] | length) > 0 then empty
             else "\($p.where): \($n) ms is not a published latency (headline p50, p95, p99, whole-rung p99 or the p99 bound: \($lat | map(tostring) | join(", ")) ms)" end),
          ($p.text | capture(core_re(true); "g") | .n as $n | ($n | ascii_downcase) as $w
           | ((words | .[$w]) // ($w | tonumber? // null)) as $c
           | if $c == $h.server_cores then empty else "\($p.where): states \($n) cores; perf_figures.json headline.server_cores is \($h.server_cores)" end)
      ),
      ($P[] | select(.kind == "body") | . as $p | .text | capture(core_re(false); "g") | .n as $n
        | if (words | .[$n | ascii_downcase]) == $h.server_cores then empty else "\($p.where): states \($n) cores; perf_figures.json headline.server_cores is \($h.server_cores)" end),
      ($H[] | select(.text | test("(?i)like-for-like\\s+runs?\\b"))
        | select([ .text | capture(tally_re; "g") | .n | tally_of ] | length == 0)
        | "\(.where): mentions like-for-like runs without a count right before them, so the run tally cannot be checked"),
      ([ ($H[], ($P[] | select(.kind == "body"))) | . as $p | .text | capture(tally_re; "g") | .n | tally_of | . + {where: $p.where} ] as $t
       | ($t[] | select(.v == null)
          | "\(.where): the run tally \"\(.w)\" is not a count the check can read; write the count alone, as a numeral or in words up to ninety-nine, with no other number word beside it"),
         ([ $t[] | select(.v != null) ] as $ok
          | if ($ok | map(.v) | unique | length) > 1
            then "the run tally disagrees between places: " + ($ok | map("\(.where) says \(.w)") | join(", ")) else empty end))
    ]
  end

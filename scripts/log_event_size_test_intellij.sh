#!/usr/bin/env bash

curl -v -s -X PUT "http://localhost:1080/mockserver/reset"

MEM=$(jstat -gc "$(ps aux | grep java | grep 1080 | awk '{ print $2 }')" | awk '{ if (NR > 1) print $3","$4","$6","$8","$10 }')
printf "counter,logs,requests,expectations,S0U,S1U,EU,OU,MU\n"
printf "counter,logs,requests,expectations,S0U,S1U,EU,OU,MU\n" > memory_with_expectation.csv
for counter in $(seq 1 1 5000); do
  curl -s -X PUT 'http://localhost:1080/mockserver/expectation' -d "{
                        'httpRequest' : {
                        },
                        'httpResponse' : {
                          'body': 'some body'
                        }
                      }" >/dev/null
  curl -s -X PUT 'http://localhost:1080/some/path' >/dev/null

  COUNT_REQUESTS=$(curl -s -X PUT "http://localhost:1080/mockserver/retrieve?type=REQUESTS" | grep "method" | wc -l)
  COUNT_LOGS=$(curl -s -X PUT "http://localhost:1080/mockserver/retrieve?type=logs" | grep 2022 | wc -l)
  EXPECTATIONS=$(curl -s -X PUT "http://localhost:1080/mockserver/retrieve?type=active_expectations" | grep "times" | wc -l)
  if [[ "$counter" == *00 ]]; then
    MEM=$(jstat -gc "$(ps aux | grep java | grep 1080 | awk '{ print $2 }')" | awk '{ if (NR > 1) print $3","$4","$6","$8","$10 }')
  fi
  printf "%d,%d,%d,%d,%s\n" "$counter" "$COUNT_LOGS" "$COUNT_REQUESTS" "$EXPECTATIONS" "$MEM"
  printf "%d,%d,%d,%d,%s\n" "$counter" "$COUNT_LOGS" "$COUNT_REQUESTS" "$EXPECTATIONS" "$MEM" >> memory_with_expectation.csv
done

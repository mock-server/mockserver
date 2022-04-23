#!/usr/bin/env bash

docker stop mockserver > /dev/null 2>&1 || true
docker rm mockserver > /dev/null 2>&1 || true
docker pull mockserver/mockserver:snapshot
docker run --rm --name mockserver --memory=500m -p 1080:1080 -e MOCKSERVER_LOG_LEVEL=WARN --entrypoint "java" mockserver/mockserver:snapshot "-Xmx64M" "-Dfile.encoding=UTF-8" "-cp" "/mockserver-netty-jar-with-dependencies.jar:/libs/*" "org.mockserver.cli.Main" &
sleep 3
curl -v -s -X PUT "http://localhost:1080/mockserver/reset"

MEM=$(docker stats mockserver --no-stream --format "\tcpu: {{.CPUPerc}}  mem: {{.MemUsage}}")
printf "counter,logs,requests,expectations,KiB\n"
printf "counter,logs,requests,expectations,KiB\n" > memory_with_expectation.csv
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
    MEM=$(docker stats mockserver --no-stream --format "\tcpu: {{.CPUPerc}}  mem: {{.MemUsage}}")
  fi
  printf "%d,%d,%d,%d,%s\n" "$counter" "$COUNT_LOGS" "$COUNT_REQUESTS" "$EXPECTATIONS" "$MEM"
  printf "%d,%d,%d,%d,%s\n" "$counter" "$COUNT_LOGS" "$COUNT_REQUESTS" "$EXPECTATIONS" "$MEM" >> memory_with_expectation.csv
done

cleanup() {
  docker stop mockserver || true
  docker rm mockserver || true
}

trap cleanup EXIT

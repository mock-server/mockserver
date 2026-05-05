# mockserver-performance-test
Performance Test Suite for MockServer http://mock-server.com

Current MockServer results are (all figures are in **milliseconds**):

|   req/s  |   reqs | Avg | Min | Max | Median | 50% | 66% | 75% | 80% | 90% | 95% | 98% |99% | 100%
:----------|:-------|:----|:----|:----|:-------|:----|:----|:----|:----|:----|:----|:----|:---|:----
|   50     |   2476 |   2 |   1 |  12 |      2 |   2 |   3 |   3 |   3 |   3 |   4 |   5 |  5 |   12
|   99     |   4891 |   4 |   0 |  18 |      3 |   3 |   4 |   5 |   5 |   6 |   7 |   8 |  9 |   18
|  496     |  22271 |   7 |   0 |  65 |      6 |   6 |   7 |   9 |  10 |  12 |  15 |  19 | 22 |   65	
|  995     | 106830 |  20 |   0 | 236 |      6 |   6 |  13 |  19 |  25 |  57 |  97 | 140 |160 |  240
| 1243     | 135671 | 113 |   0 | 434 |    110 | 110 | 140 | 160 | 170 | 210 | 250 | 290 |300 |  430

These were recorded with 4 expectations in MockServer where the request matched the 3rd expectation.

In summary, MockServer can easily handle **50 TPS with a p99 of 5ms** and can scale up to **1250 TPS with a p99 of 300ms**.

# Notes:

Can run from the command line as follows:

locust --loglevel=DEBUG --headless --only-summary -u 600 -r 15 -t 180 --host=http://127.0.0.1:1080

# Apache Benchmark

```bash
java -Xmx500m -Dmockserver.logLevel=WARN \
-Dmockserver.disableSystemOut=true \
-jar ~/.m2/repository/org/mock-server/mockserver-netty/5.11.1-SNAPSHOT/mockserver-netty-5.11.1-SNAPSHOT-jar-with-dependencies.jar \
-serverPort 1080 &
```

Apache Benchmark doesn't open a separate connection for each user so produces much faster performances

# create expectations

ab -u expectations.json -T application/json -k -n 100 -c 10 http://127.0.0.1:1080/mockserver/expectation

### RELEASE 5.11.0

```bash
This is ApacheBench, Version 2.3 <$Revision: 1843412 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 127.0.0.1 (be patient).....done


Server Software:        
Server Hostname:        127.0.0.1
Server Port:            1080

Document Path:          /mockserver/expectation
Document Length:        1291 bytes

Concurrency Level:      10
Time taken for tests:   0.200 seconds
Complete requests:      100
Failed requests:        0
Keep-Alive requests:    100
Total transferred:      142500 bytes
Total body sent:        109100
HTML transferred:       129100 bytes
Requests per second:    500.55 [#/sec] (mean)
Time per request:       19.978 [ms] (mean)
Time per request:       1.998 [ms] (mean, across all concurrent requests)
Transfer rate:          696.56 [Kbytes/sec] received
                        533.30 kb/s sent
                        1229.86 kb/s total

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.1      0       1
Processing:    12   18   4.0     18      28
Waiting:       12   18   4.0     18      28
Total:         12   18   4.1     18      28

Percentage of the requests served within a certain time (ms)
  50%     18
  66%     19
  75%     20
  80%     23
  90%     25
  95%     27
  98%     28
  99%     28
 100%     28 (longest request)
```

### SNAPSHOT 5.11.1

```bash
ab -u expectations.json -T application/json -k -n 100 -c 10 http://127.0.0.1:1080/mockserver/expectation
This is ApacheBench, Version 2.3 <$Revision: 1843412 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 127.0.0.1 (be patient).....done


Server Software:        
Server Hostname:        127.0.0.1
Server Port:            1080

Document Path:          /mockserver/expectation
Document Length:        1291 bytes

Concurrency Level:      10
Time taken for tests:   0.197 seconds
Complete requests:      100
Failed requests:        0
Keep-Alive requests:    100
Total transferred:      143400 bytes
Total body sent:        109100
HTML transferred:       129100 bytes
Requests per second:    507.75 [#/sec] (mean)
Time per request:       19.695 [ms] (mean)
Time per request:       1.969 [ms] (mean, across all concurrent requests)
Transfer rate:          711.05 [Kbytes/sec] received
                        540.97 kb/s sent
                        1252.02 kb/s total

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.1      0       0
Processing:    13   18   1.6     18      23
Waiting:       12   18   1.7     18      23
Total:         13   18   1.7     18      23

Percentage of the requests served within a certain time (ms)
  50%     18
  66%     19
  75%     19
  80%     19
  90%     21
  95%     21
  98%     23
  99%     23
 100%     23 (longest request)
```

# request matcher

ab -k -n 100000 -c 10 http://127.0.0.1:1080/simple

### RELEASE 5.11.0

```bash
ab -k -n 100000 -c 10 http://127.0.0.1:1080/simple
This is ApacheBench, Version 2.3 <$Revision: 1843412 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 127.0.0.1 (be patient)
Completed 10000 requests
Completed 20000 requests
Completed 30000 requests
Completed 40000 requests
Completed 50000 requests
Completed 60000 requests
Completed 70000 requests
Completed 80000 requests
Completed 90000 requests
Completed 100000 requests
Finished 100000 requests


Server Software:        
Server Hostname:        127.0.0.1
Server Port:            1080

Document Path:          /simple
Document Length:        20 bytes

Concurrency Level:      10
Time taken for tests:   10.105 seconds
Complete requests:      100000
Failed requests:        0
Keep-Alive requests:    100000
Total transferred:      8300000 bytes
HTML transferred:       2000000 bytes
Requests per second:    9895.85 [#/sec] (mean)
Time per request:       1.011 [ms] (mean)
Time per request:       0.101 [ms] (mean, across all concurrent requests)
Transfer rate:          802.11 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.0      0       1
Processing:     0    1   1.1      1      23
Waiting:        0    1   1.1      1      23
Total:          0    1   1.1      1      23

Percentage of the requests served within a certain time (ms)
  50%      1
  66%      1
  75%      1
  80%      1
  90%      1
  95%      1
  98%      1
  99%      2
 100%     23 (longest request)
```

### SNAPSHOT 5.11.1

```bash
ab -k -n 100000 -c 10 http://127.0.0.1:1080/simple
This is ApacheBench, Version 2.3 <$Revision: 1843412 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 127.0.0.1 (be patient)
Completed 10000 requests
Completed 20000 requests
Completed 30000 requests
Completed 40000 requests
Completed 50000 requests
Completed 60000 requests
Completed 70000 requests
Completed 80000 requests
Completed 90000 requests
Completed 100000 requests
Finished 100000 requests


Server Software:        
Server Hostname:        127.0.0.1
Server Port:            1080

Document Path:          /simple
Document Length:        8 bytes

Concurrency Level:      10
Time taken for tests:   1.319 seconds
Complete requests:      100000
Failed requests:        0
Keep-Alive requests:    100000
Total transferred:      11100000 bytes
HTML transferred:       800000 bytes
Requests per second:    75797.43 [#/sec] (mean)
Time per request:       0.132 [ms] (mean)
Time per request:       0.013 [ms] (mean, across all concurrent requests)
Transfer rate:          8216.32 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.0      0       1
Processing:     0    0   0.1      0       4
Waiting:        0    0   0.1      0       4
Total:          0    0   0.1      0       4

Percentage of the requests served within a certain time (ms)
  50%      0
  66%      0
  75%      0
  80%      0
  90%      0
  95%      0
  98%      0
  99%      0
 100%      4 (longest request)
```

```bash
ab -k -n 100000 -c 100 http://127.0.0.1:1080/simple
This is ApacheBench, Version 2.3 <$Revision: 1843412 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 127.0.0.1 (be patient)
Completed 10000 requests
Completed 20000 requests
Completed 30000 requests
Completed 40000 requests
Completed 50000 requests
Completed 60000 requests
Completed 70000 requests
Completed 80000 requests
Completed 90000 requests
Completed 100000 requests
Finished 100000 requests


Server Software:        
Server Hostname:        127.0.0.1
Server Port:            1080

Document Path:          /simple
Document Length:        8 bytes

Concurrency Level:      100
Time taken for tests:   1.077 seconds
Complete requests:      100000
Failed requests:        0
Keep-Alive requests:    100000
Total transferred:      11100000 bytes
HTML transferred:       800000 bytes
Requests per second:    92864.82 [#/sec] (mean)
Time per request:       1.077 [ms] (mean)
Time per request:       0.011 [ms] (mean, across all concurrent requests)
Transfer rate:          10066.40 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.1      0       6
Processing:     0    1   0.4      1       6
Waiting:        0    1   0.4      1       6
Total:          0    1   0.4      1       8

Percentage of the requests served within a certain time (ms)
  50%      1
  66%      1
  75%      1
  80%      1
  90%      1
  95%      2
  98%      2
  99%      2
 100%      8 (longest request)
```

```bash
ab -k -n 100000 -c 250 http://127.0.0.1:1080/simple
This is ApacheBench, Version 2.3 <$Revision: 1843412 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 127.0.0.1 (be patient)
Completed 10000 requests
Completed 20000 requests
Completed 30000 requests
Completed 40000 requests
Completed 50000 requests
Completed 60000 requests
Completed 70000 requests
Completed 80000 requests
Completed 90000 requests
Completed 100000 requests
Finished 100000 requests


Server Software:        
Server Hostname:        127.0.0.1
Server Port:            1080

Document Path:          /simple
Document Length:        8 bytes

Concurrency Level:      250
Time taken for tests:   0.991 seconds
Complete requests:      100000
Failed requests:        0
Keep-Alive requests:    100000
Total transferred:      11100000 bytes
HTML transferred:       800000 bytes
Requests per second:    100876.72 [#/sec] (mean)
Time per request:       2.478 [ms] (mean)
Time per request:       0.010 [ms] (mean, across all concurrent requests)
Transfer rate:          10934.88 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.6      0      17
Processing:     0    2   0.8      2      17
Waiting:        0    2   0.8      2       6
Total:          0    2   1.0      2      21

Percentage of the requests served within a certain time (ms)
  50%      2
  66%      3
  75%      3
  80%      3
  90%      4
  95%      4
  98%      4
  99%      5
 100%     21 (longest request)
```

```bash
ab -k -n 1000000 -c 500 http://127.0.0.1:1080/simple
This is ApacheBench, Version 2.3 <$Revision: 1843412 $>
Copyright 1996 Adam Twiss, Zeus Technology Ltd, http://www.zeustech.net/
Licensed to The Apache Software Foundation, http://www.apache.org/

Benchmarking 127.0.0.1 (be patient)
Completed 100000 requests
Completed 200000 requests
Completed 300000 requests
Completed 400000 requests
Completed 500000 requests
Completed 600000 requests
Completed 700000 requests
Completed 800000 requests
Completed 900000 requests
Completed 1000000 requests
Finished 1000000 requests


Server Software:        
Server Hostname:        127.0.0.1
Server Port:            1080

Document Path:          /simple
Document Length:        8 bytes

Concurrency Level:      500
Time taken for tests:   10.610 seconds
Complete requests:      1000000
Failed requests:        0
Keep-Alive requests:    1000000
Total transferred:      111000000 bytes
HTML transferred:       8000000 bytes
Requests per second:    94250.15 [#/sec] (mean)
Time per request:       5.305 [ms] (mean)
Time per request:       0.011 [ms] (mean, across all concurrent requests)
Transfer rate:          10216.57 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    0   0.9      0      62
Processing:     0    5   2.5      5      74
Waiting:        0    5   2.5      5      74
Total:          0    5   2.6      5      74

Percentage of the requests served within a certain time (ms)
  50%      5
  66%      5
  75%      6
  80%      6
  90%      7
  95%      7
  98%      8
  99%      9
 100%     74 (longest request)
```

# AB Complex Expectation Test

java -Xmx500m -Dmockserver.logLevel=WARN \
-Dmockserver.disableSystemOut=true \
-jar ~/.m2/repository/org/mock-server/mockserver-netty/5.11.1-SNAPSHOT/mockserver-netty-5.11.1-SNAPSHOT-jar-with-dependencies.jar \
-serverPort 1080 &

curl -v -k -X PUT https://localhost:1080/mockserver/expectation -H "Content-Type: application/json; charset=utf-8" --data '{
    "httpRequest": {
        "method": "GET",
        "path": "/simple",
        "headers": {
            "one": ["one"],
            "two": ["two"],
            "three": ["three"],
            "four": ["four"],
            "five": ["five"]
        },
        "queryStringParameters": {
            "one": ["one"],
            "two": ["two"],
            "three": ["three"],
            "four": ["four"],
            "five": ["five"]
        }
    },
    "httpResponse": {
        "body": {
            "type": "STRING",
            "string": "سلام",
            "contentType": "text/plain; charset=utf-8"
         }
    },
    "times": {
        "unlimited": true
    }
}'

ab -k -n 100 -c 10 -H "one:one" -H "two:two" -H "three:three" -H "four:four" -H "five:five" http://127.0.0.1:1080/simple?one=one&two=two&three=three&four=four&five=five


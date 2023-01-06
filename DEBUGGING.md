### Debugging

It can be helpful to view packet capture to debug any issues with MockServer.

The [netshoot](https://github.com/nicolaka/netshoot) container has a helpful set of network debugging tools.

The following command can obtain a packet capture and the key log used to debug it:

```shell
CAPTURE_DIRECTORY=/tmp/mockserver_capture
mkdir -p "${CAPTURE_DIRECTORY}"
DATE_TIME=$(date +%H%M_%m_%d_%Y)
CAPTURE_FILE="/tmp/mockserver_capture/capture_loopback_8443_${DATE_TIME}.pcap"; echo "CAPTURE_FILE: ${CAPTURE_FILE}"
KEY_LOG_FILE="/tmp/mockserver_capture/key_log_loopback_8443_${DATE_TIME}"; echo "KEY_LOG_FILE: ${KEY_LOG_FILE}"
sudo tcpdump -i lo0 -nn -w "${CAPTURE_FILE}" 'port 8443' &
docker run -v /tmp/mockserver_capture:/tmp/mockserver_capture -e SSLKEYLOGFILE="${KEY_LOG_FILE}" nicolaka/netshoot curl -v -k -X PUT https://host.docker.internal:8443/mockserver/status https://host.docker.internal:8443/mockserver/status https://host.docker.internal:8443/mockserver/status
sleep 3
ps -ef | grep "sudo tcpdump" | grep -v grep | awk '{ print $2 }' | xargs sudo kill
ls -lrt "${CAPTURE_DIRECTORY}"
open ${CAPTURE_FILE}
```

### HTTP2

For HTTP2 errors or connections being close "early" you can determine the issue by capturing the packets and key log then look at the clien'ts GOAWAY frame which may have an Error (such as PROTOCOL_ERROR) and Additional Debug Data.

The Additional Debug Data can be futher understood (for curl, nghttp and envoy clients) by looking at the nghttp c code, for example here: https://github.com/nghttp2/nghttp2/blob/master/lib/nghttp2_session.c#L4220
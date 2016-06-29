package org.mockserver.model;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.mockserver.client.netty.NettyHttpClient;
import org.mockserver.logging.LogFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * author Valeriy Mironichev
 */
public class HttpWebHookRequest extends ObjectWithReflectiveEqualsHashCodeToString {

    private final Logger logger = LoggerFactory.getLogger(HttpWebHookRequest.class);
    private LogFormatter logFormatter = new LogFormatter(logger);
    private static NettyHttpClient httpClient = new NettyHttpClient();
    private static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private String host;
    private int port;
    private String path;
    private String payload;
    private long executionDelay;
    private TimeUnit executionDelayTimeUnit;

    public HttpWebHookRequest() {
    }

    public String getHost() {
        return host;
    }

    public HttpWebHookRequest setHost(String host) {
        this.host = host;
        return this;
    }

    public int getPort() {
        return port;
    }

    public HttpWebHookRequest setPort(int port) {
        this.port = port;
        return this;
    }

    public String getPath() {
        return path;
    }

    public HttpWebHookRequest setPath(String path) {
        this.path = path;
        return this;
    }

    public String getPayload() {
        return payload;
    }

    public HttpWebHookRequest setPayload(String payload) {
        this.payload = payload;
        return this;
    }

    public long getExecutionDelay() {
        return executionDelay;
    }

    public HttpWebHookRequest setExecutionDelay(long executionDelay) {
        this.executionDelay = executionDelay;
        return this;
    }

    public TimeUnit getExecutionDelayTimeUnit() {
        return executionDelayTimeUnit == null ? TimeUnit.MILLISECONDS : executionDelayTimeUnit;
    }

    public HttpWebHookRequest setExecutionDelayTimeUnit(TimeUnit executionDelayTimeUnit) {
        this.executionDelayTimeUnit = executionDelayTimeUnit;
        return this;
    }

    public void submit() {
        logFormatter.infoLog("Scheduling web hook for execution {}", this);
        executorService.schedule(webHookRequest(), getExecutionDelay(), getExecutionDelayTimeUnit());
    }

    private Runnable webHookRequest() {
        return new Runnable() {
            @Override
            public void run() {
                HttpRequest httpRequest = new HttpRequest()
                        .withBody(payload)
                        .withHeader("Content-type", "application/json")
                        .withMethod("POST")
                        .withPath(path == null ? "/" : path);
                OutboundHttpRequest outbondHttpRequest = new OutboundHttpRequest(host, port, null, httpRequest);
                HttpResponse response = httpClient.sendRequest(outbondHttpRequest);
                logFormatter.infoLog("Web hook executed, response recevied: {}" + System.getProperty("line.separator") + " for request:{}", response, httpRequest);
                System.out.println("received response: " + response.getBodyAsString());
            }
        };
    }
}

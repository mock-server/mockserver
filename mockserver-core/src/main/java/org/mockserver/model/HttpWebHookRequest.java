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
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
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

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public long getExecutionDelay() {
        return executionDelay;
    }

    public void setExecutionDelay(long executionDelay) {
        this.executionDelay = executionDelay;
    }

    public TimeUnit getExecutionDelayTimeUnit() {
        return executionDelayTimeUnit;
    }

    public void setExecutionDelayTimeUnit(TimeUnit executionDelayTimeUnit) {
        this.executionDelayTimeUnit = executionDelayTimeUnit;
    }

    public void submit() {
        logFormatter.infoLog("Scheduling web hook for execution {}", this);
        executorService.schedule(new Runnable() {
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
        }, executionDelay, executionDelayTimeUnit);
    }
}

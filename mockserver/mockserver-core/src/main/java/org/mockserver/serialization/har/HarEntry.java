package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarEntry {

    @JsonProperty("startedDateTime")
    private String startedDateTime;

    @JsonProperty("time")
    private long time;

    @JsonProperty("request")
    private HarRequest request;

    @JsonProperty("response")
    private HarResponse response;

    @JsonProperty("cache")
    private HarCache cache = new HarCache();

    @JsonProperty("timings")
    private HarTimings timings = new HarTimings();

    @JsonProperty("serverIPAddress")
    private String serverIPAddress;

    @JsonProperty("connection")
    private String connection;

    public String getStartedDateTime() {
        return startedDateTime;
    }

    public HarEntry withStartedDateTime(String startedDateTime) {
        this.startedDateTime = startedDateTime;
        return this;
    }

    public long getTime() {
        return time;
    }

    public HarEntry withTime(long time) {
        this.time = time;
        return this;
    }

    public HarRequest getRequest() {
        return request;
    }

    public HarEntry withRequest(HarRequest request) {
        this.request = request;
        return this;
    }

    public HarResponse getResponse() {
        return response;
    }

    public HarEntry withResponse(HarResponse response) {
        this.response = response;
        return this;
    }

    public HarCache getCache() {
        return cache;
    }

    public HarEntry withCache(HarCache cache) {
        this.cache = cache;
        return this;
    }

    public HarTimings getTimings() {
        return timings;
    }

    public HarEntry withTimings(HarTimings timings) {
        this.timings = timings;
        return this;
    }

    public String getServerIPAddress() {
        return serverIPAddress;
    }

    public HarEntry withServerIPAddress(String serverIPAddress) {
        this.serverIPAddress = serverIPAddress;
        return this;
    }

    public String getConnection() {
        return connection;
    }

    public HarEntry withConnection(String connection) {
        this.connection = connection;
        return this;
    }
}

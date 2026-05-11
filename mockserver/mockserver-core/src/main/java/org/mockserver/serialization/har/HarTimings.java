package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarTimings {

    @JsonProperty("send")
    private long send;

    @JsonProperty("wait")
    private long wait;

    @JsonProperty("receive")
    private long receive;

    @JsonProperty("blocked")
    private long blocked = -1;

    @JsonProperty("dns")
    private long dns = -1;

    @JsonProperty("connect")
    private long connect = -1;

    @JsonProperty("ssl")
    private long ssl = -1;

    public long getSend() {
        return send;
    }

    public HarTimings withSend(long send) {
        this.send = send;
        return this;
    }

    public long getWait() {
        return wait;
    }

    public HarTimings withWait(long wait) {
        this.wait = wait;
        return this;
    }

    public long getReceive() {
        return receive;
    }

    public HarTimings withReceive(long receive) {
        this.receive = receive;
        return this;
    }

    public long getBlocked() {
        return blocked;
    }

    public HarTimings withBlocked(long blocked) {
        this.blocked = blocked;
        return this;
    }

    public long getDns() {
        return dns;
    }

    public HarTimings withDns(long dns) {
        this.dns = dns;
        return this;
    }

    public long getConnect() {
        return connect;
    }

    public HarTimings withConnect(long connect) {
        this.connect = connect;
        return this;
    }

    public long getSsl() {
        return ssl;
    }

    public HarTimings withSsl(long ssl) {
        this.ssl = ssl;
        return this;
    }
}

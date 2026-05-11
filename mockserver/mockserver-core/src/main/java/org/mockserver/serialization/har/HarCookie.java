package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarCookie {

    @JsonProperty("name")
    private String name;

    @JsonProperty("value")
    private String value;

    @JsonProperty("path")
    private String path;

    @JsonProperty("domain")
    private String domain;

    @JsonProperty("expires")
    private String expires;

    @JsonProperty("httpOnly")
    private Boolean httpOnly;

    @JsonProperty("secure")
    private Boolean secure;

    public HarCookie() {
    }

    public HarCookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public HarCookie withName(String name) {
        this.name = name;
        return this;
    }

    public String getValue() {
        return value;
    }

    public HarCookie withValue(String value) {
        this.value = value;
        return this;
    }

    public String getPath() {
        return path;
    }

    public HarCookie withPath(String path) {
        this.path = path;
        return this;
    }

    public String getDomain() {
        return domain;
    }

    public HarCookie withDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getExpires() {
        return expires;
    }

    public HarCookie withExpires(String expires) {
        this.expires = expires;
        return this;
    }

    public Boolean getHttpOnly() {
        return httpOnly;
    }

    public HarCookie withHttpOnly(Boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public Boolean getSecure() {
        return secure;
    }

    public HarCookie withSecure(Boolean secure) {
        this.secure = secure;
        return this;
    }
}

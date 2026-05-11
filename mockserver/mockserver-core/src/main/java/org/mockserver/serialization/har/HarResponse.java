package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarResponse {

    @JsonProperty("status")
    private int status;

    @JsonProperty("statusText")
    private String statusText;

    @JsonProperty("httpVersion")
    private String httpVersion;

    @JsonProperty("cookies")
    private List<HarCookie> cookies = Collections.emptyList();

    @JsonProperty("headers")
    private List<HarNameValuePair> headers = Collections.emptyList();

    @JsonProperty("content")
    private HarContent content = new HarContent();

    @JsonProperty("redirectURL")
    private String redirectURL = "";

    @JsonProperty("headersSize")
    private long headersSize = -1;

    @JsonProperty("bodySize")
    private long bodySize = -1;

    public int getStatus() {
        return status;
    }

    public HarResponse withStatus(int status) {
        this.status = status;
        return this;
    }

    public String getStatusText() {
        return statusText;
    }

    public HarResponse withStatusText(String statusText) {
        this.statusText = statusText;
        return this;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public HarResponse withHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    public List<HarCookie> getCookies() {
        return cookies;
    }

    public HarResponse withCookies(List<HarCookie> cookies) {
        this.cookies = cookies;
        return this;
    }

    public List<HarNameValuePair> getHeaders() {
        return headers;
    }

    public HarResponse withHeaders(List<HarNameValuePair> headers) {
        this.headers = headers;
        return this;
    }

    public HarContent getContent() {
        return content;
    }

    public HarResponse withContent(HarContent content) {
        this.content = content;
        return this;
    }

    public String getRedirectURL() {
        return redirectURL;
    }

    public HarResponse withRedirectURL(String redirectURL) {
        this.redirectURL = redirectURL;
        return this;
    }

    public long getHeadersSize() {
        return headersSize;
    }

    public HarResponse withHeadersSize(long headersSize) {
        this.headersSize = headersSize;
        return this;
    }

    public long getBodySize() {
        return bodySize;
    }

    public HarResponse withBodySize(long bodySize) {
        this.bodySize = bodySize;
        return this;
    }
}

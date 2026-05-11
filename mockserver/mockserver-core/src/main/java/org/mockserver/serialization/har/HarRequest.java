package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarRequest {

    @JsonProperty("method")
    private String method;

    @JsonProperty("url")
    private String url;

    @JsonProperty("httpVersion")
    private String httpVersion;

    @JsonProperty("cookies")
    private List<HarCookie> cookies = Collections.emptyList();

    @JsonProperty("headers")
    private List<HarNameValuePair> headers = Collections.emptyList();

    @JsonProperty("queryString")
    private List<HarNameValuePair> queryString = Collections.emptyList();

    @JsonProperty("postData")
    private HarPostData postData;

    @JsonProperty("headersSize")
    private long headersSize = -1;

    @JsonProperty("bodySize")
    private long bodySize = -1;

    public String getMethod() {
        return method;
    }

    public HarRequest withMethod(String method) {
        this.method = method;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public HarRequest withUrl(String url) {
        this.url = url;
        return this;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public HarRequest withHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    public List<HarCookie> getCookies() {
        return cookies;
    }

    public HarRequest withCookies(List<HarCookie> cookies) {
        this.cookies = cookies;
        return this;
    }

    public List<HarNameValuePair> getHeaders() {
        return headers;
    }

    public HarRequest withHeaders(List<HarNameValuePair> headers) {
        this.headers = headers;
        return this;
    }

    public List<HarNameValuePair> getQueryString() {
        return queryString;
    }

    public HarRequest withQueryString(List<HarNameValuePair> queryString) {
        this.queryString = queryString;
        return this;
    }

    public HarPostData getPostData() {
        return postData;
    }

    public HarRequest withPostData(HarPostData postData) {
        this.postData = postData;
        return this;
    }

    public long getHeadersSize() {
        return headersSize;
    }

    public HarRequest withHeadersSize(long headersSize) {
        this.headersSize = headersSize;
        return this;
    }

    public long getBodySize() {
        return bodySize;
    }

    public HarRequest withBodySize(long bodySize) {
        this.bodySize = bodySize;
        return this;
    }
}

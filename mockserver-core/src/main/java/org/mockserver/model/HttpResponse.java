package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;

/**
 * @author jamesdbloom
 */
public class HttpResponse extends Action {
    private Integer statusCode = 200;
    private Body body = new StringBody("");
    private Map<String, Header> headers = new LinkedHashMap<String, Header>();
    private Map<String, Cookie> cookies = new LinkedHashMap<String, Cookie>();
    private Delay delay;
    private ConnectionOptions connectionOptions;

    public HttpResponse() {
    }

    public static HttpResponse response() {
        return new HttpResponse();
    }

    public static HttpResponse response(String body) {
        return new HttpResponse().withBody(body);
    }

    public static HttpResponse notFoundResponse() {
        return new HttpResponse().withStatusCode(404);
    }

    /**
     * The status code to return, such as 200, 404, the status code specified
     * here will result in the default status message for this status code for
     * example for 200 the status message "OK" is used
     *
     * @param statusCode an integer such as 200 or 404
     */
    public HttpResponse withStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    /**
     * Set response body to return as a simple UTF-8 string response body
     *
     * @param body a UTF-8 string
     */
    public HttpResponse withBody(String body) {
        if (body != null) {
            this.body = new StringBody(body);
        }
        return this;
    }

    /**
     * Set response body to return as binary such as a pdf or image
     *
     * @param body a byte array
     */
    public HttpResponse withBody(byte[] body) {
        this.body = new BinaryBody(body);
        return this;
    }

    /**
     * Set the body to return for example:
     * <p/>
     * string body:
     * - exact("<html><head/><body><div>a simple string body</div></body></html>");
     * <p/>
     * or
     * <p/>
     * - new StringBody("<html><head/><body><div>a simple string body</div></body></html>")
     * <p/>
     * binary body:
     * - binary(IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("example.pdf"), 1024));
     * <p/>
     * or
     * <p/>
     * - new BinaryBody(IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("example.pdf"), 1024));
     *
     * @param body an instance of one of the Body subclasses including StringBody or BinaryBody
     */
    public HttpResponse withBody(Body body) {
        this.body = body;
        return this;
    }

    public Body getBody() {
        return body;
    }

    @JsonIgnore
    public String getBodyAsString() {
        if (body != null) {
            return body.toString();
        } else {
            return null;
        }
    }

    /**
     * The headers to return as a list of Header objects
     *
     * @param headers a list of Header objects
     */
    public HttpResponse withHeaders(List<Header> headers) {
        this.headers.clear();
        for (Header header : headers) {
            withHeader(header);
        }
        return this;
    }

    /**
     * The headers to return as a varargs of Header objects
     *
     * @param headers a varargs of Header objects
     */
    public HttpResponse withHeaders(Header... headers) {
        if (headers != null) {
            withHeaders(Arrays.asList(headers));
        }
        return this;
    }

    /**
     * Add a header to return as a Header object, if a header with
     * the same name already exists this will NOT be modified but
     * two headers will exist
     *
     * @param header a Header objects
     */
    public HttpResponse withHeader(Header header) {
        if (this.headers.containsKey(header.getName())) {
            this.headers.get(header.getName()).addValues(header.getValues());
        } else {
            this.headers.put(header.getName(), header);
        }
        return this;
    }

    /**
     * Add a header to return as a Header object, if a header with
     * the same name already exists this will NOT be modified but
     * two headers will exist
     *
     * @param name the header name
     * @param values the header values which can be a varags of strings or regular expressions
     */
    public HttpResponse withHeader(String name, String... values) {
        if (this.headers.containsKey(name)) {
            this.headers.get(name).addValues(values);
        } else {
            this.headers.put(name, header(name, values));
        }
        return this;
    }

    /**
     * Update header to return as a Header object, if a header with
     * the same name already exists it will be modified
     *
     * @param header a Header objects
     */
    public HttpResponse updateHeader(Header header) {
        this.headers.put(header.getName(), header);
        return this;
    }

    /**
     * Update header to return as a Header object, if a header with
     * the same name already exists it will be modified
     *
     * @param name the header name
     * @param values the header values which can be a varags of strings or regular expressions
     */
    public HttpResponse updateHeader(String name, String... values) {
        this.headers.put(name, header(name, values));
        return this;
    }

    public List<Header> getHeaders() {
        return new ArrayList<Header>(headers.values());
    }

    public List<String> getHeader(String name) {
        List<String> headerValues = new ArrayList<String>();
        if (headers.containsKey(name)) {
            headerValues.addAll(headers.get(name).getValues());
        }
        return headerValues;
    }

    /**
     * The cookies to return as Set-Cookie headers as a list of Cookie objects
     *
     * @param cookies a list of Cookie objects
     */
    public HttpResponse withCookies(List<Cookie> cookies) {
        this.cookies.clear();
        for (Cookie cookie : cookies) {
            withCookie(cookie);
        }
        return this;
    }

    /**
     * The cookies to return as Set-Cookie headers as a varargs of Cookie objects
     *
     * @param cookies a varargs of Cookie objects
     */
    public HttpResponse withCookies(Cookie... cookies) {
        if (cookies != null) {
            withCookies(Arrays.asList(cookies));
        }
        return this;
    }

    /**
     * Add cookie to return as Set-Cookie header
     *
     * @param cookie a Cookie object
     */
    public HttpResponse withCookie(Cookie cookie) {
        this.cookies.put(cookie.getName(), cookie);
        return this;
    }

    /**
     * Add cookie to return as Set-Cookie header
     *
     * @param name the cookies name
     * @param value the cookies value which can be a string or regular expression
     */
    public HttpResponse withCookie(String name, String value) {
        this.cookies.put(name, cookie(name, value));
        return this;
    }

    public List<Cookie> getCookies() {
        return new ArrayList<Cookie>(cookies.values());
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param delay a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     */
    public HttpResponse withDelay(Delay delay) {
        this.delay = delay;
        return this;
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param timeUnit a the time unit, for example TimeUnit.SECONDS
     * @param value a the number of time units to delay the response
     */
    public HttpResponse withDelay(TimeUnit timeUnit, long value) {
        this.delay = new Delay(timeUnit, value);
        return this;
    }

    public Delay getDelay() {
        return delay;
    }

    /**
     * The connection options for override the default connection behaviour, this allows full control of "hop-by-hop" headers such a "Connection" or "Content-Length"
     *
     * @param connectionOptions the connection options for override the default connection behaviour
     */
    public HttpResponse withConnectionOptions(ConnectionOptions connectionOptions) {
        this.connectionOptions = connectionOptions;
        return this;
    }

    public ConnectionOptions getConnectionOptions() {
        return connectionOptions;
    }

    @JsonIgnore
    public HttpResponse applyDelay() {
        if (delay != null) {
            delay.applyDelay();
        }
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.RESPONSE;
    }
}


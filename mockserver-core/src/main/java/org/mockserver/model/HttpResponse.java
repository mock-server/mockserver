package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HttpResponse extends Action {
    private Integer statusCode;
    private BodyWithContentType body;
    private Map<NottableString, Header> headers = new LinkedHashMap<NottableString, Header>();
    private Map<NottableString, Cookie> cookies = new LinkedHashMap<NottableString, Cookie>();
    private Delay delay;
    private ConnectionOptions connectionOptions;

    /**
     * Static builder to create a response.
     */
    public static HttpResponse response() {
        return new HttpResponse();
    }

    /**
     * Static builder to create a response with a 200 status code and the string response body.
     *
     * @param body a string
     */
    public static HttpResponse response(String body) {
        return new HttpResponse().withStatusCode(200).withBody(body);
    }

    /**
     * Static builder to create a not found response.
     */
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
     * Set response body to return as a string response body. The character set will be determined by the Content-Type header
     * on the response. To force the character set, use {@link #withBody(String, Charset)}.
     *
     * @param body a string
     */
    public HttpResponse withBody(String body) {
        if (body != null) {
            this.body = new StringBody(body);
        }
        return this;
    }

    /**
     * Set response body to return a string response body with the specified encoding. <b>Note:</b> The character set of the
     * response will be forced to the specified charset, even if the Content-Type header specifies otherwise.
     *
     * @param body    a string
     * @param charset character set the string will be encoded in
     */
    public HttpResponse withBody(String body, Charset charset) {
        if (body != null) {
            this.body = new StringBody(body, charset);
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
    public HttpResponse withBody(BodyWithContentType body) {
        this.body = body;
        return this;
    }

    public BodyWithContentType getBody() {
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
            this.headers.get(header.getName()).addNottableValues(header.getValues());
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
     * @param name   the header name
     * @param values the header values which can be a varags of strings or regular expressions
     */
    public HttpResponse withHeader(String name, String... values) {
        if (this.headers.containsKey(string(name))) {
            this.headers.get(string(name)).addValues(values);
        } else {
            this.headers.put(string(name), header(name, values));
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
     * @param name   the header name
     * @param values the header values which can be a varags of strings or regular expressions
     */
    public HttpResponse updateHeader(String name, String... values) {
        this.headers.put(string(name), header(name, values));
        return this;
    }

    public List<Header> getHeaders() {
        return new ArrayList<Header>(headers.values());
    }

    public List<String> getHeader(String name) {
        List<String> headerValues = new ArrayList<String>();
        for (NottableString headerName : headers.keySet()) {
            if (headerName.equalsIgnoreCase(string(name))) {
                for (NottableString headerValue : headers.get(headerName).getValues()) {
                    headerValues.add(headerValue.getValue());
                }
            }
        }
        return headerValues;
    }

    public String getFirstHeader(String name) {
        List<String> headerValues = getHeader(name);
        if (headerValues.size() > 0) {
            return headerValues.get(0);
        } else {
            return "";
        }
    }

    public boolean containsHeader(String name, String value) {
        for (NottableString headerName : headers.keySet()) {
            if (headerName.equalsIgnoreCase(string(name))) {
                for (NottableString headerValue : headers.get(headerName).getValues()) {
                    if (headerValue.equalsIgnoreCase(value)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
     * @param name  the cookies name
     * @param value the cookies value which can be a string or regular expression
     */
    public HttpResponse withCookie(String name, String value) {
        this.cookies.put(string(name), cookie(name, value));
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
     * @param value    a the number of time units to delay the response
     */
    public HttpResponse withDelay(TimeUnit timeUnit, long value) {
        this.delay = new Delay(timeUnit, value);
        return this;
    }

    public Delay getDelay() {
        return delay;
    }

    /**
     * The connection options for override the default connection behaviour, this allows full control of headers such
     * as "Connection" or "Content-Length" or controlling whether the socket is closed after the response has been sent
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

    public HttpResponse clone() {
        return response()
                .withStatusCode(getStatusCode())
                .withBody(getBody())
                .withHeaders(getHeaders())
                .withCookies(getCookies())
                .withDelay(getDelay())
                .withConnectionOptions(getConnectionOptions());
    }
}


package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.net.MediaType;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.HttpStatusCode.NOT_FOUND_404;
import static org.mockserver.model.HttpStatusCode.OK_200;

/**
 * @author jamesdbloom
 */
public class HttpResponse extends Action<HttpResponse> {
    private Integer statusCode;
    private String reasonPhrase;
    private BodyWithContentType body;
    private Headers headers = new Headers();
    private Cookies cookies = new Cookies();
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
        return new HttpResponse().withStatusCode(OK_200.code()).withReasonPhrase(OK_200.reasonPhrase()).withBody(body);
    }

    /**
     * Static builder to create a not found response.
     */
    public static HttpResponse notFoundResponse() {
        return new HttpResponse().withStatusCode(NOT_FOUND_404.code()).withReasonPhrase(NOT_FOUND_404.reasonPhrase());
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
     * The reason phrase to return, if no reason code is returned this will
     * be defaulted to the standard reason phrase for the statusCode,
     * i.e. for a statusCode of 200 the standard reason phrase is "OK"
     *
     * @param reasonPhrase an string such as "Not Found" or "OK"
     */
    public HttpResponse withReasonPhrase(String reasonPhrase) {
        this.reasonPhrase = reasonPhrase;
        return this;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
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
     * Set response body to return a string response body with the specified encoding. <b>Note:</b> The character set of the
     * response will be forced to the specified charset, even if the Content-Type header specifies otherwise.
     *
     * @param body        a string
     * @param contentType media type, if charset is included this will be used for encoding string
     */
    public HttpResponse withBody(String body, MediaType contentType) {
        if (body != null) {
            this.body = new StringBody(body, contentType);
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

    public Headers getHeaders() {
        return this.headers;
    }

    public HttpResponse withHeaders(Headers headers) {
        this.headers = headers;
        return this;
    }

    /**
     * The headers to return as a list of Header objects
     *
     * @param headers a list of Header objects
     */
    public HttpResponse withHeaders(List<Header> headers) {
        this.headers.withEntries(headers);
        return this;
    }

    /**
     * The headers to return as a varargs of Header objects
     *
     * @param headers varargs of Header objects
     */
    public HttpResponse withHeaders(Header... headers) {
        this.headers.withEntries(headers);
        return this;
    }

    /**
     * Add a header to return as a Header object, if a header with
     * the same name already exists this will NOT be modified but
     * two headers will exist
     *
     * @param header a Header object
     */
    public HttpResponse withHeader(Header header) {
        this.headers.withEntry(header);
        return this;
    }

    /**
     * Add a header to return as a Header object, if a header with
     * the same name already exists this will NOT be modified but
     * two headers will exist
     *
     * @param name   the header name
     * @param values the header values
     */
    public HttpResponse withHeader(String name, String... values) {
        this.headers.withEntry(name, values);
        return this;
    }

    /**
     * Update header to return as a Header object, if a header with
     * the same name already exists it will be modified
     *
     * @param header a Header object
     */
    public HttpResponse replaceHeader(Header header) {
        this.headers.replaceEntry(header);
        return this;
    }

    /**
     * Update header to return as a Header object, if a header with
     * the same name already exists it will be modified
     *
     * @param name   the header name
     * @param values the header values
     */
    public HttpResponse replaceHeader(String name, String... values) {
        this.headers.replaceEntry(name, values);
        return this;
    }

    public List<Header> getHeaderList() {
        return this.headers.getEntries();
    }

    public List<String> getHeader(String name) {
        return this.headers.getValues(name);
    }

    public String getFirstHeader(String name) {
        return this.headers.getFirstValue(name);
    }

    /**
     * Returns true if a header with the specified name has been added
     *
     * @param name the header name
     * @return true if a header has been added with that name otherwise false
     */
    public boolean containsHeader(String name) {
        return this.headers.containsEntry(name);
    }

    public HttpResponse removeHeader(String name) {
        headers.remove(name);
        return this;
    }

    public HttpResponse removeHeader(NottableString name) {
        headers.remove(name);
        return this;
    }

    public Cookies getCookies() {
        return this.cookies;
    }

    public HttpResponse withCookies(Cookies cookies) {
        this.cookies = cookies;
        return this;
    }

    /**
     * Returns true if a header with the specified name has been added
     *
     * @param name  the header name
     * @param value the header value
     * @return true if a header has been added with that name otherwise false
     */
    public boolean containsHeader(String name, String value) {
        return this.headers.containsEntry(name, value);
    }

    /**
     * The cookies to return as Set-Cookie headers as a list of Cookie objects
     *
     * @param cookies a list of Cookie objects
     */
    public HttpResponse withCookies(List<Cookie> cookies) {
        this.cookies.withEntries(cookies);
        return this;
    }

    /**
     * The cookies to return as Set-Cookie headers as a varargs of Cookie objects
     *
     * @param cookies a varargs of Cookie objects
     */
    public HttpResponse withCookies(Cookie... cookies) {
        this.cookies.withEntries(cookies);
        return this;
    }

    /**
     * Add cookie to return as Set-Cookie header
     *
     * @param cookie a Cookie object
     */
    public HttpResponse withCookie(Cookie cookie) {
        this.cookies.withEntry(cookie);
        return this;
    }

    /**
     * Add cookie to return as Set-Cookie header
     *
     * @param name  the cookies name
     * @param value the cookies value
     */
    public HttpResponse withCookie(String name, String value) {
        this.cookies.withEntry(name, value);
        return this;
    }

    /**
     * Adds one cookie to match on or to not match on using the NottableString, each NottableString can either be a positive matching value,
     * such as string("match"), or a value to not match on, such as not("do not match"), the string values passed to the NottableString
     * can be a plain string or a regex (for more details of the supported regex syntax see
     * http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param name  the cookies name
     * @param value the cookies value
     */
    public HttpResponse withCookie(NottableString name, NottableString value) {
        this.cookies.withEntry(name, value);
        return this;
    }

    public List<Cookie> getCookieList() {
        return this.cookies.getEntries();
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

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.RESPONSE;
    }

    public HttpResponse clone() {
        return response()
            .withStatusCode(statusCode)
            .withReasonPhrase(reasonPhrase)
            .withBody(body)
            .withHeaders(headers.clone())
            .withCookies(cookies.clone())
            .withDelay(getDelay())
            .withConnectionOptions(connectionOptions);
    }
}

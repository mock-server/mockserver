package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HttpRequest extends Not implements HttpObject<HttpRequest, Body> {
    private NottableString method = string("");
    private NottableString path = string("");
    private Parameters queryStringParameters;
    private Body body = null;
    private Headers headers;
    private Cookies cookies;
    private Boolean keepAlive = null;
    private Boolean secure = null;

    public static HttpRequest request() {
        return new HttpRequest();
    }

    public static HttpRequest request(String path) {
        return new HttpRequest().withPath(path);
    }

    public Boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Match on whether the request was made using an HTTP persistent connection, also called HTTP keep-alive, or HTTP connection reuse
     *
     * @param isKeepAlive true if the request was made with an HTTP persistent connection
     */
    public HttpRequest withKeepAlive(Boolean isKeepAlive) {
        this.keepAlive = isKeepAlive;
        return this;
    }

    public Boolean isSecure() {
        return secure;
    }

    /**
     * Match on whether the request was made over SSL (i.e. HTTPS)
     *
     * @param isSsl true if the request was made with SSL
     */
    public HttpRequest withSecure(Boolean isSsl) {
        this.secure = isSsl;
        return this;
    }

    /**
     * The HTTP method to match on such as "GET" or "POST"
     *
     * @param method the HTTP method such as "GET" or "POST"
     */
    public HttpRequest withMethod(String method) {
        return withMethod(string(method));
    }

    /**
     * The HTTP method all method except a specific value using the "not" operator,
     * for example this allows operations such as not("GET")
     *
     * @param method the HTTP method to not match on not("GET") or not("POST")
     */
    public HttpRequest withMethod(NottableString method) {
        this.method = method;
        return this;
    }

    public NottableString getMethod() {
        return method;
    }

    public String getMethod(String defaultValue) {
        if (isBlank(method.getValue())) {
            return defaultValue;
        } else {
            return method.getValue();
        }
    }


    /**
     * The path to match on such as "/some_mocked_path" any servlet context path is ignored for matching and should not be specified here
     * regex values are also supported such as ".*_path", see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html
     * for full details of the supported regex syntax
     *
     * @param path the path such as "/some_mocked_path" or a regex
     */
    public HttpRequest withPath(String path) {
        withPath(string(path));
        return this;
    }

    /**
     * The path to not match on for example not("/some_mocked_path") with match any path not equal to "/some_mocked_path",
     * the servlet context path is ignored for matching and should not be specified here
     * regex values are also supported such as not(".*_path"), see
     * http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html for full details of the supported regex syntax
     *
     * @param path the path to not match on such as not("/some_mocked_path") or not(".*_path")
     */
    public HttpRequest withPath(NottableString path) {
        this.path = path;
        return this;
    }

    public NottableString getPath() {
        return path;
    }

    public boolean matches(String method, String... paths) {
        boolean matches = false;
        for (String path : paths) {
            matches = this.method.getValue().equals(method) && this.path.getValue().equals(path);
            if (matches) {
                break;
            }
        }
        return matches;
    }

    public Parameters getQueryStringParameters() {
        return this.queryStringParameters;
    }

    private Parameters getOrCreateQueryStringParameters() {
        if (this.queryStringParameters == null) {
            this.queryStringParameters = new Parameters();
        }
        return this.queryStringParameters;
    }

    public HttpRequest withQueryStringParameters(Parameters parameters) {
        if (parameters == null || parameters.isEmpty()) {
            this.queryStringParameters = null;
        } else {
            this.queryStringParameters = parameters;
        }
        return this;
    }

    /**
     * The query string parameters to match on as a list of Parameter objects where the values or keys of each parameter can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameters the list of Parameter objects where the values or keys of each parameter can be either a string or a regex
     */
    public HttpRequest withQueryStringParameters(List<Parameter> parameters) {
        getOrCreateQueryStringParameters().withEntries(parameters);
        return this;
    }

    /**
     * The query string parameters to match on as a varags Parameter objects where the values or keys of each parameter can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameters the varags Parameter objects where the values or keys of each parameter can be either a string or a regex
     */
    public HttpRequest withQueryStringParameters(Parameter... parameters) {
        getOrCreateQueryStringParameters().withEntries(parameters);
        return this;
    }

    /**
     * The query string parameters to match on as a Map<String, List<String>> where the values or keys of each parameter can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameters the Map<String, List<String>> object where the values or keys of each parameter can be either a string or a regex
     */
    public HttpRequest withQueryStringParameters(Map<String, List<String>> parameters) {
        getOrCreateQueryStringParameters().withEntries(parameters);
        return this;
    }

    /**
     * Adds one query string parameter to match on as a Parameter object where the parameter values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameter the Parameter object which can have a values list of strings or regular expressions
     */
    public HttpRequest withQueryStringParameter(Parameter parameter) {
        getOrCreateQueryStringParameters().withEntry(parameter);
        return this;
    }

    /**
     * Adds one query string parameter to match which can specified using plain strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param name   the parameter name
     * @param values the parameter values which can be a varags of strings or regular expressions
     */
    public HttpRequest withQueryStringParameter(String name, String... values) {
        getOrCreateQueryStringParameters().withEntry(name, values);
        return this;
    }

    /**
     * Adds one query string parameter to match on or to not match on using the NottableString, each NottableString can either be a positive matching
     * value, such as string("match"), or a value to not match on, such as not("do not match"), the string values passed to the NottableString
     * can also be a plain string or a regex (for more details of the supported regex syntax
     * see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param name   the parameter name as a NottableString
     * @param values the parameter values which can be a varags of NottableStrings
     */
    public HttpRequest withQueryStringParameter(NottableString name, NottableString... values) {
        getOrCreateQueryStringParameters().withEntry(name, values);
        return this;
    }

    public List<Parameter> getQueryStringParameterList() {
        if (this.queryStringParameters != null) {
            return this.queryStringParameters.getEntries();
        } else {
            return Collections.emptyList();
        }
    }

    public boolean hasQueryStringParameter(String name, String value) {
        if (this.queryStringParameters != null) {
            return this.queryStringParameters.containsEntry(name, value);
        } else {
            return false;
        }
    }

    public boolean hasQueryStringParameter(NottableString name, NottableString value) {
        if (this.queryStringParameters != null) {
            return this.queryStringParameters.containsEntry(name, value);
        } else {
            return false;
        }
    }

    public String getFirstQueryStringParameter(String name) {
        if (this.queryStringParameters != null) {
            return this.queryStringParameters.getFirstValue(name);
        } else {
            return "";
        }
    }

    /**
     * The exact string body to match on such as "this is an exact string body"
     *
     * @param body the body on such as "this is an exact string body"
     */
    public HttpRequest withBody(String body) {
        this.body = new StringBody(body);
        return this;
    }

    /**
     * The exact string body to match on such as "this is an exact string body"
     *
     * @param body    the body on such as "this is an exact string body"
     * @param charset character set the string will be encoded in
     */
    public HttpRequest withBody(String body, Charset charset) {
        if (body != null) {
            this.body = new StringBody(body, charset);
        }
        return this;
    }

    /**
     * The body to match on as binary data such as a pdf or image
     *
     * @param body a byte array
     */
    public HttpRequest withBody(byte[] body) {
        this.body = new BinaryBody(body);
        return this;
    }

    /**
     * The body match rules on such as using one of the Body subclasses as follows:
     * <p>
     * exact string match:
     * - exact("this is an exact string body");
     * <p>
     * or
     * <p>
     * - new StringBody("this is an exact string body")
     * <p>
     * regular expression match:
     * - regex("username[a-z]{4}");
     * <p>
     * or
     * <p>
     * - new RegexBody("username[a-z]{4}");
     * <p>
     * json match:
     * - json("{username: 'foo', password: 'bar'}");
     * <p>
     * or
     * <p>
     * - json("{username: 'foo', password: 'bar'}", MatchType.STRICT);
     * <p>
     * or
     * <p>
     * - new JsonBody("{username: 'foo', password: 'bar'}");
     * <p>
     * json schema match:
     * - jsonSchema("{type: 'object', properties: { 'username': { 'type': 'string' }, 'password': { 'type': 'string' } }, 'required': ['username', 'password']}");
     * <p>
     * or
     * <p>
     * - jsonSchemaFromResource("org/mockserver/model/loginSchema.json");
     * <p>
     * or
     * <p>
     * - new JsonSchemaBody("{type: 'object', properties: { 'username': { 'type': 'string' }, 'password': { 'type': 'string' } }, 'required': ['username', 'password']}");
     * <p>
     * xpath match:
     * - xpath("/element[key = 'some_key' and value = 'some_value']");
     * <p>
     * or
     * <p>
     * - new XPathBody("/element[key = 'some_key' and value = 'some_value']");
     * <p>
     * body parameter match:
     * - params(
     * param("name_one", "value_one_one", "value_one_two")
     * param("name_two", "value_two")
     * );
     * <p>
     * or
     * <p>
     * - new ParameterBody(
     * new Parameter("name_one", "value_one_one", "value_one_two")
     * new Parameter("name_two", "value_two")
     * );
     * <p>
     * binary match:
     * - binary(IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("example.pdf"), 1024));
     * <p>
     * or
     * <p>
     * - new BinaryBody(IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("example.pdf"), 1024));
     * <p>
     * for more details of the supported regular expression syntax see <a href="http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html">http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html</a>
     * for more details of the supported json syntax see <a href="http://jsonassert.skyscreamer.org">http://jsonassert.skyscreamer.org</a>
     * for more details of the supported json schema syntax see <a href="http://json-schema.org/">http://json-schema.org/</a>
     * for more detail of XPath syntax see <a href="http://saxon.sourceforge.net/saxon6.5.3/expressions.html">http://saxon.sourceforge.net/saxon6.5.3/expressions.html</a>
     *
     * @param body an instance of one of the Body subclasses including StringBody, ParameterBody or BinaryBody
     */
    public HttpRequest withBody(Body body) {
        this.body = body;
        return this;
    }

    public Body getBody() {
        return body;
    }

    @JsonIgnore
    public byte[] getBodyAsRawBytes() {
        return this.body != null ? this.body.getRawBytes() : new byte[0];
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

    private Headers getOrCreateHeaders() {
        if (this.headers == null) {
            this.headers = new Headers();
        }
        return this.headers;
    }

    public HttpRequest withHeaders(Headers headers) {
        if (headers == null || headers.isEmpty()) {
            this.headers = null;
        } else {
            this.headers = headers;
        }
        return this;
    }

    /**
     * The headers to match on as a list of Header objects where the values or keys of each header can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param headers the list of Header objects where the values or keys of each header can be either a string or a regex
     */
    public HttpRequest withHeaders(List<Header> headers) {
        getOrCreateHeaders().withEntries(headers);
        return this;
    }

    /**
     * The headers to match on as a varags of Header objects where the values or keys of each header can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param headers the varags of Header objects where the values or keys of each header can be either a string or a regex
     */
    public HttpRequest withHeaders(Header... headers) {
        getOrCreateHeaders().withEntries(headers);
        return this;
    }

    /**
     * Adds one header to match on as a Header object where the header values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param header the Header object which can have a values list of strings or regular expressions
     */
    public HttpRequest withHeader(Header header) {
        getOrCreateHeaders().withEntry(header);
        return this;
    }

    /**
     * Adds one header to match which can specified using plain strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param name   the header name
     * @param values the header values which can be a varags of strings or regular expressions
     */
    public HttpRequest withHeader(String name, String... values) {
        getOrCreateHeaders().withEntry(header(name, values));
        return this;
    }

    /**
     * Adds one header to match on or to not match on using the NottableString, each NottableString can either be a positive matching value,
     * such as string("match"), or a value to not match on, such as not("do not match"), the string values passed to the NottableString
     * can also be a plain string or a regex (for more details of the supported regex syntax
     * see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param name   the header name as a NottableString
     * @param values the header values which can be a varags of NottableStrings
     */
    public HttpRequest withHeader(NottableString name, NottableString... values) {
        getOrCreateHeaders().withEntry(header(name, values));
        return this;
    }

    /**
     * Adds one header to match on as a Header object where the header values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param header the Header object which can have a values list of strings or regular expressions
     */
    public HttpRequest replaceHeader(Header header) {
        getOrCreateHeaders().replaceEntry(header);
        return this;
    }

    public List<Header> getHeaderList() {
        if (this.headers != null) {
            return this.headers.getEntries();
        } else {
            return Collections.emptyList();
        }
    }

    public List<String> getHeader(String name) {
        if (this.headers != null) {
            return this.headers.getValues(name);
        } else {
            return Collections.emptyList();
        }
    }

    public String getFirstHeader(String name) {
        if (this.headers != null) {
            return this.headers.getFirstValue(name);
        } else {
            return "";
        }
    }

    /**
     * Returns true if a header with the specified name has been added
     *
     * @param name the header name
     * @return true if a header has been added with that name otherwise false
     */
    public boolean containsHeader(String name) {
        if (this.headers != null) {
            return this.headers.containsEntry(name);
        } else {
            return false;
        }
    }

    public HttpRequest removeHeader(String name) {
        if (this.headers != null) {
            headers.remove(name);
        }
        return this;
    }

    public HttpRequest removeHeader(NottableString name) {
        if (this.headers != null) {
            headers.remove(name);
        }
        return this;
    }

    public Cookies getCookies() {
        return this.cookies;
    }

    private Cookies getOrCreateCookies() {
        if (this.cookies == null) {
            this.cookies = new Cookies();
        }
        return this.cookies;
    }

    public HttpRequest withCookies(Cookies cookies) {
        if (cookies == null || cookies.isEmpty()) {
            this.cookies = null;
        } else {
            this.cookies = cookies;
        }
        return this;
    }

    /**
     * The cookies to match on as a list of Cookie objects where the values or keys of each cookie can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param cookies a list of Cookie objects
     */
    public HttpRequest withCookies(List<Cookie> cookies) {
        getOrCreateCookies().withEntries(cookies);
        return this;
    }

    /**
     * The cookies to match on as a varags Cookie objects where the values or keys of each cookie can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param cookies a varargs of Cookie objects
     */
    public HttpRequest withCookies(Cookie... cookies) {
        getOrCreateCookies().withEntries(cookies);
        return this;
    }

    /**
     * Adds one cookie to match on as a Cookie object where the cookie values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param cookie a Cookie object
     */
    public HttpRequest withCookie(Cookie cookie) {
        getOrCreateCookies().withEntry(cookie);
        return this;
    }

    /**
     * Adds one cookie to match on, which can specified using either plain strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param name  the cookies name
     * @param value the cookies value
     */
    public HttpRequest withCookie(String name, String value) {
        getOrCreateCookies().withEntry(name, value);
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
    public HttpRequest withCookie(NottableString name, NottableString value) {
        getOrCreateCookies().withEntry(name, value);
        return this;
    }

    public List<Cookie> getCookieList() {
        if (this.cookies != null) {
            return this.cookies.getEntries();
        } else {
            return Collections.emptyList();
        }
    }

    public InetSocketAddress socketAddressFromHostHeader() {
        if (isNotBlank(getFirstHeader(HOST.toString()))) {
            boolean isSsl = isSecure() != null && isSecure();
            String[] hostHeaderParts = getFirstHeader(HOST.toString()).split(":");
            return new InetSocketAddress(hostHeaderParts[0], hostHeaderParts.length > 1 ? Integer.parseInt(hostHeaderParts[1]) : isSsl ? 443 : 80);
        } else {
            throw new IllegalArgumentException("Host header must be provided to determine remote socket address, the request does not include the \"Host\" header:" + NEW_LINE + this);
        }
    }

    public HttpRequest clone() {
        return not(request(), not)
            .withMethod(method)
            .withPath(path)
            .withQueryStringParameters(queryStringParameters != null ? queryStringParameters.clone() : null)
            .withBody(body)
            .withHeaders(headers != null ? headers.clone() : null)
            .withCookies(cookies != null ? cookies.clone() : null)
            .withKeepAlive(keepAlive)
            .withSecure(secure);
    }

    public HttpRequest update(HttpRequest replaceRequest) {
        if (replaceRequest.getMethod() != null && isNotBlank(replaceRequest.getMethod().getValue())) {
            withMethod(replaceRequest.getMethod());
        }
        if (replaceRequest.getPath() != null && isNotBlank(replaceRequest.getPath().getValue())) {
            withPath(replaceRequest.getPath());
        }
        for (Header header : replaceRequest.getHeaderList()) {
            getHeaders().replaceEntry(header);
        }
        for (Cookie cookie : replaceRequest.getCookieList()) {
            withCookie(cookie);
        }
        for (Parameter parameter : replaceRequest.getQueryStringParameterList()) {
            getQueryStringParameters().replaceEntry(parameter);
        }
        if (replaceRequest.getBody() != null) {
            withBody(replaceRequest.getBody());
        }
        if (replaceRequest.isSecure() != null) {
            withSecure(replaceRequest.isSecure());
        }
        if (replaceRequest.isKeepAlive() != null) {
            withKeepAlive(replaceRequest.isKeepAlive());
        }
        return this;
    }
}

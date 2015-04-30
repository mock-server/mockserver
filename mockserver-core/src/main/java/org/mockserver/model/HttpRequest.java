package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;

import java.util.*;

import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;

/**
 * @author jamesdbloom
 */
public class HttpRequest extends Not {
    NottableString method = string("");
    NottableString path = string("");
    Map<String, Parameter> queryStringParameters = new LinkedHashMap<String, Parameter>();
    Body body = null;
    Map<String, Header> headers = new LinkedHashMap<String, Header>();
    Map<String, Cookie> cookies = new LinkedHashMap<String, Cookie>();
    boolean isKeepAlive = false;
    boolean secure;

    public static HttpRequest request() {
        return new HttpRequest();
    }

    public static HttpRequest request(String path) {
        return new HttpRequest().withPath(path);
    }

    @JsonIgnore
    public boolean isKeepAlive() {
        return isKeepAlive;
    }

    public HttpRequest setKeepAlive(boolean isKeepAlive) {
        this.isKeepAlive = isKeepAlive;
        return this;
    }

    @JsonIgnore
    public boolean isSecure() {
        return secure;
    }

    public HttpRequest setSecure(boolean secure) {
        this.secure = secure;
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
        if (Strings.isNullOrEmpty(method.getValue())) {
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

    public boolean matches(String method, String path) {
        return this.method.getValue().equals(method) && this.path.getValue().equals(path);
    }

    /**
     * The query string parameters to match on as a list of Parameter objects where the values or keys of each parameter can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameters the list of Parameter objects where the values or keys of each parameter can be either a string or a regex
     */
    public HttpRequest withQueryStringParameters(List<Parameter> parameters) {
        this.queryStringParameters.clear();
        for (Parameter parameter : parameters) {
            withQueryStringParameter(parameter);
        }
        return this;
    }

    /**
     * The query string parameters to match on as a varags Parameter objects where the values or keys of each parameter can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameters the varags Parameter objects where the values or keys of each parameter can be either a string or a regex
     */
    public HttpRequest withQueryStringParameters(Parameter... parameters) {
        return withQueryStringParameters(Arrays.asList(parameters));
    }

    /**
     * The query string parameters to match on as a Map<String, List<String>> where the values or keys of each parameter can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameters the Map<String, List<String>> object where the values or keys of each parameter can be either a string or a regex
     */
    public HttpRequest withQueryStringParameters(Map<String, List<String>> parameters) {
        this.queryStringParameters.clear();
        for (String name : parameters.keySet()) {
            for (String value : parameters.get(name)) {
                withQueryStringParameter(new Parameter(name, value));
            }
        }
        return this;
    }

    /**
     * Adds one query string parameter to match on as a Parameter object where the parameter values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameter the Parameter object which can have a values list of strings or regular expressions
     */
    public HttpRequest withQueryStringParameter(Parameter parameter) {
        if (this.queryStringParameters.containsKey(parameter.getName())) {
            this.queryStringParameters.get(parameter.getName()).addValues(parameter.getValues());
        } else {
            this.queryStringParameters.put(parameter.getName(), parameter);
        }
        return this;
    }

    /**
     * Adds one query string parameter to match on as a Parameter object where the parameter values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param name the parameter name
     * @param values the parameter values which can be a varags of strings or regular expressions
     */
    public HttpRequest withQueryStringParameter(String name, String... values) {
        if (this.queryStringParameters.containsKey(name)) {
            this.queryStringParameters.get(name).addValues(values);
        } else {
            this.queryStringParameters.put(name, param(name, values));
        }
        return this;
    }

    public List<Parameter> getQueryStringParameters() {
        return new ArrayList<Parameter>(queryStringParameters.values());
    }

    public boolean hasQueryStringParameter(String name, String expectedValue) {
        if (Strings.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("Name must not be null or empty");
        }
        if (expectedValue == null) {
            throw new IllegalArgumentException("Expected value must not be null");
        }
        Parameter parameter = queryStringParameters.get(name);
        if (parameter != null) {
            for (String actualValue : parameter.getValues()) {
                if (expectedValue.equals(actualValue)) {
                    return true;
                }
            }
        }
        return false;
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
     *
     * exact string match:
     *   - exact("this is an exact string body");
     *
     *   or
     *
     *   - new StringBody("this is an exact string body")
     *
     * regular expression match:
     *   - regex("username[a-z]{4}");
     *
     *   or
     *
     *   - new RegexBody("username[a-z]{4}");
     *
     * json match:
     *   - json("{username: 'foo', password: 'bar'}");
     *
     *   or
     *
     *   - json("{username: 'foo', password: 'bar'}", MatchType.STRICT);
     *
     *   or
     *
     *   - new JsonBody("{username: 'foo', password: 'bar'}");
     *
     * json schema match:
     *   - jsonSchema("{type: 'object', properties: { 'username': { 'type': 'string' }, 'password': { 'type': 'string' } }, 'required': ['username', 'password']}");
     *
     *   or
     *
     *   - jsonSchemaFromResource("org/mockserver/model/loginSchema.json");
     *
     *   or
     *
     *   - new JsonSchemaBody("{type: 'object', properties: { 'username': { 'type': 'string' }, 'password': { 'type': 'string' } }, 'required': ['username', 'password']}");
     *
     * xpath match:
     *   - xpath("/element[key = 'some_key' and value = 'some_value']");
     *
     *   or
     *
     *   - new XPathBody("/element[key = 'some_key' and value = 'some_value']");
     *
     * body parameter match:
     *   - params(
     *             param("name_one", "value_one_one", "value_one_two")
     *             param("name_two", "value_two")
     *     );
     *
     *   or
     *
     *   - new ParameterBody(
     *             new Parameter("name_one", "value_one_one", "value_one_two")
     *             new Parameter("name_two", "value_two")
     *     );
     *
     * binary match:
     *   - binary(IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("example.pdf"), 1024));
     *
     *   or
     *
     *   - new BinaryBody(IOUtils.readFully(getClass().getClassLoader().getResourceAsStream("example.pdf"), 1024));
     *
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
        return this.body != null ? new String(this.body.getRawBytes(), Charsets.UTF_8) : "";
    }

    /**
     * The headers to match on as a list of Header objects where the values or keys of each header can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param headers the list of Header objects where the values or keys of each header can be either a string or a regex
     */
    public HttpRequest withHeaders(List<Header> headers) {
        this.headers.clear();
        for (Header header : headers) {
            withHeader(header);
        }
        return this;
    }

    /**
     * The headers to match on as a varags of Header objects where the values or keys of each header can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param headers the varags of Header objects where the values or keys of each header can be either a string or a regex
     */
    public HttpRequest withHeaders(Header... headers) {
        if (headers != null) {
            withHeaders(Arrays.asList(headers));
        }
        return this;
    }

    /**
     * Adds one header to match on as a Header object where the header values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param header the Header object which can have a values list of strings or regular expressions
     */
    public HttpRequest withHeader(Header header) {
        if (this.headers.containsKey(header.getName())) {
            this.headers.get(header.getName()).addValues(header.getValues());
        } else {
            this.headers.put(header.getName(), header);
        }
        return this;
    }

    /**
     * Adds one header to match on as a Header object where the header values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param name the header name
     * @param values the header values which can be a varags of strings or regular expressions
     */
    public HttpRequest withHeader(String name, String... values) {
        if (this.headers.containsKey(name)) {
            this.headers.get(name).addValues(values);
        } else {
            this.headers.put(name, header(name, values));
        }
        return this;
    }

    /**
     * Adds one header to match on as a Header object where the header values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param header the Header object which can have a values list of strings or regular expressions
     */
    public HttpRequest replaceHeader(Header header) {
        for (String key : new HashSet<String>(this.headers.keySet())) {
            if (header.getName().equalsIgnoreCase(key)) {
                this.headers.remove(key);
            }
        }
        this.headers.put(header.getName(), header);
        return this;
    }

    public List<Header> getHeaders() {
        return new ArrayList<Header>(headers.values());
    }

    public String getFirstHeader(String name) {
        String firstHeadValue = "";
        if (headers.containsKey(name) || headers.containsKey(name.toLowerCase())) {
            Header header = headers.get(name);
            if (header == null) {
                header = headers.get(name.toLowerCase());
            }
            if (!header.getValues().isEmpty() && !Strings.isNullOrEmpty(header.getValues().get(0))) {
                firstHeadValue = header.getValues().get(0);
            }
        }
        return firstHeadValue;
    }

    /**
     * Returns true if a header with the specified name has been added
     *
     * @param name the Header name
     * @return true if a header has been added with that name otherwise false
     */
    public boolean containsHeader(String name) {
        return headers.containsKey(name);
    }

    /**
     * The cookies to match on as a list of Cookie objects where the values or keys of each cookie can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param cookies the list of Cookie objects where the values or keys of each cookie can be either a string or a regex
     */
    public HttpRequest withCookies(List<Cookie> cookies) {
        this.cookies.clear();
        for (Cookie cookie : cookies) {
            withCookie(cookie);
        }
        return this;
    }

    /**
     * The cookies to match on as a varags Cookie objects where the values or keys of each cookie can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param cookies the varags Cookie objects where the values or keys of each cookie can be either a string or a regex
     */
    public HttpRequest withCookies(Cookie... cookies) {
        if (cookies != null) {
            withCookies(Arrays.asList(cookies));
        }
        return this;
    }

    /**
     * Adds one cookie to match on as a Cookie object where the cookie values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param cookie the Cookie object which can have a values list of strings or regular expressions
     */
    public HttpRequest withCookie(Cookie cookie) {
        this.cookies.put(cookie.getName(), cookie);
        return this;
    }

    /**
     * Adds one cookie to match on as a Cookie object where the cookie values list can be a list of strings or regular expressions
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/6/docs/api/java/util/regex/Pattern.html)
     *
     * @param name the cookies name
     * @param value the cookies value which can be a string or regular expression
     */
    public HttpRequest withCookie(String name, String value) {
        this.cookies.put(name, cookie(name, value));
        return this;
    }

    public List<Cookie> getCookies() {
        return new ArrayList<Cookie>(cookies.values());
    }
}

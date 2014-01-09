package org.mockserver.model;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class HttpRequest extends EqualsHashCodeToString {
    private String method = "";
    private String url = "";
    private String path = "";
    private String queryString = "";
    private List<Parameter> parameters = new ArrayList<Parameter>();
    private String body = "";
    private List<Header> headers = new ArrayList<Header>();
    private List<Cookie> cookies = new ArrayList<Cookie>();

    public HttpRequest() {
    }

    public static HttpRequest request() {
        return new HttpRequest();
    }

    /**
     * The HTTP method to match on such as "GET" or "POST"
     *
     * @param method the HTTP method such as "GET" or "POST"
     */
    public HttpRequest withMethod(String method) {
        this.method = method;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public String getURL() {
        return url;
    }

    /**
     * The URL to match on such as "http://localhost:9999/some_mocked_path" regex values are also supported
     * such as ".*some_mocked_path", see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
     * for full details of the supported regex syntax
     *
     * @param url the full URL such as "http://localhost:9999/some_mocked_path" or a regex
     */
    public HttpRequest withURL(String url) {
        this.url = url;
        return this;
    }

    public String getPath() {
        return path;
    }

    /**
     * The path to match on such as "/some_mocked_path" any servlet context path is ignored for matching and should not be specified here
     * regex values are also supported such as ".*_path", see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
     * for full details of the supported regex syntax
     *
     * @param path the path such as "/some_mocked_path" or a regex
     */
    public HttpRequest withPath(String path) {
        this.path = path;
        return this;
    }

    public String getQueryString() {
        return queryString;
    }

    /**
     * The query string - this method supports an exact string match such as "someParameter=someValue&someOtherParameter=someOtherValue" or a regex match,
     * see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html for full details of the supported regex syntax
     *
     * @param queryString the query string such as "someParameter=someValue&someOtherParameter=someOtherValue" or a regex
     */
    public HttpRequest withQueryString(String queryString) {
        this.queryString = queryString;
        return this;
    }

    /**
     * The parameters (in the query string or body) to match on as a list of Parameter objects where the values of each parameter can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameters the list of Parameter objects where the values of each parameter can be either a string or a regex
     */
    public HttpRequest withParameters(List<Parameter> parameters) {
        this.parameters = parameters;
        return this;
    }

    /**
     * The parameters (in the query string or body) to match on as a varags Parameter objects where the values of each parameter can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)
     *
     * @param parameters the varags Parameter objects where the values of each parameter can be either a string or a regex
     */
    public HttpRequest withParameters(Parameter... parameters) {
        this.parameters = Arrays.asList(parameters);
        return this;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public String getBody() {
        return body;
    }

    /**
     * The body to match on such as "{username: 'foo', password: 'bar'}" or a regex (for more details of the supported regex syntax see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)
     * or an XPath expression which returns one or more values or evaluates to true (for more detail of XPath syntax see http://saxon.sourceforge.net/saxon6.5.3/expressions.html)
     *
     * @param body the body on such as "{username: 'foo', password: 'bar'}" or a regex such as "username[a-z]{4}" or an XPath such as "/element[key = 'some_key' and value = 'some_value']"
     */
    public HttpRequest withBody(String body) {
        this.body = body;
        return this;
    }

    /**
     * The headers to match on as a list of Header objects where the values of each header can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)
     *
     * @param headers the list of Header objects where the values of each header can be either a string or a regex
     */
    public HttpRequest withHeaders(List<Header> headers) {
        this.headers = headers;
        return this;
    }

    /**
     * The headers to match on as a varags of Header objects where the values of each header can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)
     *
     * @param headers the varags of Header objects where the values of each header can be either a string or a regex
     */
    public HttpRequest withHeaders(Header... headers) {
        this.headers = Arrays.asList(headers);
        return this;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    /**
     * The cookies to match on as a list of Cookie objects where the values of each cookie can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)
     *
     * @param cookies the list of Cookie objects where the values of each cookie can be either a string or a regex
     */
    public HttpRequest withCookies(List<Cookie> cookies) {
        this.cookies = cookies;
        return this;
    }

    /**
     * The cookies to match on as a varags Cookie objects where the values of each cookie can be either a string or a regex
     * (for more details of the supported regex syntax see http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)
     *
     * @param cookies the varags Cookie objects where the values of each cookie can be either a string or a regex
     */
    public HttpRequest withCookies(Cookie... cookies) {
        this.cookies = Arrays.asList(cookies);
        return this;
    }

    public List<Cookie> getCookies() {
        return cookies;
    }

    public int getPort() {
        URL url = null;
        try {
            url = new URL(this.url);
        } catch (MalformedURLException murle) {
            logger.debug("MalformedURLException parsing uri [" + this.url + "]", murle);
        }
        if (url != null && url.getPort() != -1) {
            return url.getPort();
        } else {
            if (this.url.startsWith("https")) {
                return 443;
            } else {
                return 80;
            }
        }
    }
}

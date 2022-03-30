package org.mockserver.model;

import java.util.List;
import java.util.Objects;

public class HttpRequestModifier extends ObjectWithJsonToString {

    private int hashCode;
    private PathModifier path;
    private QueryParametersModifier queryStringParameters;
    private HeadersModifier headers;
    private CookiesModifier cookies;

    public static HttpRequestModifier requestModifier() {
        return new HttpRequestModifier();
    }

    public PathModifier getPath() {
        return path;
    }

    public HttpRequestModifier withPath(PathModifier path) {
        this.path = path;
        this.hashCode = 0;
        return this;
    }

    /**
     * <p>
     * The regex and substitution values to use to modify matching substrings, if multiple matches are found they will all be modified with the substitution
     * for full details of supported regex syntax see: http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html
     * </p>
     * <p>
     * The substitution can specify matching groups using $ followed by the group number for example $1
     * </p>
     * <p>
     * If a null or empty substitution string is provided the regex pattern will be used to remove any substring matching the regex
     * </p>
     * <p>
     * For example:
     * </p>
     * <pre>
     * regex: ^/(.+)/(.+)$
     * substitution: /prefix/$1/infix/$2/postfix
     * then: /some/path &#61;&gt; /prefix/some/infix/path/postfix
     * or: /some/longer/path &#61;&gt; /prefix/some/infix/longer/path/postfix
     * </pre>
     *
     * @param regex        regex value to match on
     * @param substitution the value to substitute for the regex
     */
    public HttpRequestModifier withPath(String regex, String substitution) {
        this.path = new PathModifier()
            .withRegex(regex)
            .withSubstitution(substitution);
        this.hashCode = 0;
        return this;
    }

    public QueryParametersModifier getQueryStringParameters() {
        return queryStringParameters;
    }

    public HttpRequestModifier withQueryStringParameters(QueryParametersModifier queryStringParameters) {
        this.queryStringParameters = queryStringParameters;
        this.hashCode = 0;
        return this;
    }

    public HttpRequestModifier withQueryStringParameters(Parameters add, Parameters replace, List<String> remove) {
        this.queryStringParameters = new QueryParametersModifier()
            .withAdd(add)
            .withReplace(replace)
            .withRemove(remove);
        this.hashCode = 0;
        return this;
    }

    public HttpRequestModifier withQueryStringParameters(List<Parameter> add, List<Parameter> replace, List<String> remove) {
        this.queryStringParameters = new QueryParametersModifier()
            .withAdd(new Parameters(add))
            .withReplace(new Parameters(replace))
            .withRemove(remove);
        this.hashCode = 0;
        return this;
    }

    public HeadersModifier getHeaders() {
        return headers;
    }

    public HttpRequestModifier withHeaders(HeadersModifier headers) {
        this.headers = headers;
        this.hashCode = 0;
        return this;
    }

    public HttpRequestModifier withHeaders(List<Header> add, List<Header> replace, List<String> remove) {
        this.headers = new HeadersModifier()
            .withAdd(new Headers(add))
            .withReplace(new Headers(replace))
            .withRemove(remove);
        this.hashCode = 0;
        return this;
    }

    public CookiesModifier getCookies() {
        return cookies;
    }

    public HttpRequestModifier withCookies(CookiesModifier cookies) {
        this.cookies = cookies;
        this.hashCode = 0;
        return this;
    }

    public HttpRequestModifier withCookies(List<Cookie> add, List<Cookie> replace, List<String> remove) {
        this.cookies = new CookiesModifier()
            .withAdd(new Cookies(add))
            .withReplace(new Cookies(replace))
            .withRemove(remove);
        this.hashCode = 0;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        HttpRequestModifier that = (HttpRequestModifier) o;
        return Objects.equals(path, that.path) &&
            Objects.equals(queryStringParameters, that.queryStringParameters) &&
            Objects.equals(headers, that.headers) &&
            Objects.equals(cookies, that.cookies);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(path, queryStringParameters, headers, cookies);
        }
        return hashCode;
    }

}

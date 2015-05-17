package org.mockserver.mockserver;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import org.mockserver.url.URLParser;

import java.util.List;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class MappedRequest {

    private final HttpMethod method;
    private final String path;
    private final String content;
    private final Map<String, List<String>> parameters;

    public MappedRequest(FullHttpRequest request) {
        this.method = request.getMethod();
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
        this.path = URLParser.returnPath(queryStringDecoder.path());
        this.parameters = queryStringDecoder.parameters();
        this.content = (request.content() != null ? request.content().toString(CharsetUtil.UTF_8) : "");
    }

    public boolean matches(HttpMethod method, String path) {
        return this.method.equals(method) && this.path.equals(path);
    }

    public HttpMethod method() {
        return method;
    }

    public String content() {
        return content;
    }

    public Map<String, List<String>> parameters() {
        return parameters;
    }
}

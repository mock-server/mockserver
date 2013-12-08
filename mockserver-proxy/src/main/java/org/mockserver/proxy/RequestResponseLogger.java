package org.mockserver.proxy;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestResponseLogger {

    private final List<Pattern> patterns = Collections.synchronizedList(new ArrayList<Pattern>());
    private final Map<String, HttpRequest> requestLog = new ConcurrentHashMap<String, HttpRequest>();

    public void log(String urlRegex) {
        patterns.add(Pattern.compile(urlRegex));
    }

    public void stopLogging(String urlRegex) {
        for (Pattern pattern : new ArrayList<Pattern>(patterns)) {
            if (pattern.pattern().equals(urlRegex)) {
                patterns.remove(pattern);
            }
        }
    }

    public List<HttpRequest> clearLog(String urlRegex) {
        List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
        Pattern urlPattern = Pattern.compile(urlRegex);
        for (String requestUrl : requestLog.keySet()) {
            if (urlPattern.matcher(requestUrl).matches()) {
                requestLog.remove(requestUrl);
            }
        }
        return httpRequests;
    }

    public List<HttpRequest> getRequests(String urlRegex, Map<String, List<String>> queryParameters) {
        List<HttpRequest> httpRequests = new ArrayList<HttpRequest>();
        Pattern urlPattern = Pattern.compile(urlRegex);
        for (String requestUrl : requestLog.keySet()) {
            if (urlPattern.matcher(requestUrl).matches()) {
                if (queryParameters != null) {
                    HttpRequest httpRequest = requestLog.get(requestUrl);
                    QueryStringDecoder queryStringDecoder = new QueryStringDecoder(httpRequest.getUri());
                    if (isSubMap(queryStringDecoder.getParameters(), queryParameters)) {
                        httpRequests.add(requestLog.get(requestUrl));
                    }
                }
            }
        }
        return httpRequests;
    }

    private <K, V> boolean isSubMap(Map<K, V> mapToTest, Map<K, V> subMap) {
        for (K referenceKey : subMap.keySet()) {
            if (!subMap.get(referenceKey).equals(mapToTest.get(referenceKey))) {
                return false;
            }
        }
        return true;
    }

    void logRequestAndResponse(HttpRequest httpRequest) {
        if (patterns.size() > 0) {
            final String host = httpRequest.getHeader("Host");
            final String url = "http://" + host + httpRequest.getUri();
            for (Pattern pattern : patterns) {
                if (shouldLog(pattern, url)) {
                    requestLog.put(url, httpRequest);
                }
            }
        }
    }

    private boolean shouldLog(Pattern pattern, String url) {
        Matcher matcher = pattern.matcher(url);
        return matcher.matches();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (String url : requestLog.keySet()) {
            stringBuilder.append("\n\n");
            stringBuilder.append(url);
            stringBuilder.append("\nREQUEST: \n");
            stringBuilder.append(requestLog.get(url));
        }
        return stringBuilder.toString();
    }
}

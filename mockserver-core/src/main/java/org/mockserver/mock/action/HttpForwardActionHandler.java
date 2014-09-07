package org.mockserver.mock.action;

import io.netty.handler.codec.http.HttpHeaders;
import org.apache.http.client.utils.URIBuilder;
import org.mockserver.client.http.ApacheHttpClient;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.mockserver.proxy.filters.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

import static org.mockserver.model.Header.header;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandler {
    private final Filters filters;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ApacheHttpClient apacheHttpClient = new ApacheHttpClient(true);

    public HttpForwardActionHandler(Filters filters) {
        this.filters = filters;
    }

    public HttpResponse handle(HttpForward httpForward, HttpRequest httpRequest) {
        updateURLAndHost(httpRequest, httpForward);
        return sendRequest(filters.applyFilters(httpRequest));
    }

    private void updateURLAndHost(HttpRequest httpRequest, HttpForward httpForward) {
        try {
            URIBuilder uriBuilder = new URIBuilder(httpRequest.getURL());
            uriBuilder.setPath(httpRequest.getPath().startsWith("/") ? httpRequest.getPath() : "/" + httpRequest.getPath());
            uriBuilder.setHost(httpForward.getHost());
            uriBuilder.setPort(httpForward.getPort());
            uriBuilder.setScheme(httpForward.getScheme().name().toLowerCase());
            for (Parameter parameter : httpRequest.getQueryStringParameters()) {
                for (String value : parameter.getValues()) {
                    uriBuilder.addParameter(parameter.getName(), value);
                }
            }
            httpRequest.withURL(uriBuilder.toString());
            httpRequest.replaceHeader(header(HttpHeaders.Names.HOST, String.format("%s:%d", uriBuilder.getHost(), uriBuilder.getPort())));
        } catch (URISyntaxException e) {
            logger.warn("URISyntaxException for url " + httpRequest.getURL(), e);
        }
    }

    private HttpResponse sendRequest(HttpRequest httpRequest) {
        if (httpRequest != null) {
            return filters.applyFilters(httpRequest, apacheHttpClient.sendRequest(httpRequest, false));
        } else {
            return null;
        }
    }
}

package org.mockserver.mock.action;

import com.google.common.base.Strings;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringEncoder;
import org.apache.commons.io.Charsets;
import org.mockserver.client.http.NettyHttpClient;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

import static org.mockserver.model.Header.header;

/**
 * @author jamesdbloom
 */
public class HttpForwardActionHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // http client
    private NettyHttpClient httpClient = new NettyHttpClient();

    public HttpResponse handle(HttpForward httpForward, HttpRequest httpRequest) {
        updateURLAndHost(httpRequest, httpForward);
        return sendRequest(httpRequest);
    }

    private void updateURLAndHost(HttpRequest httpRequest, HttpForward httpForward) {
        try {
            URI originalUri = new URI(httpRequest.getURL());
            QueryStringEncoder queryStringEncoder = new QueryStringEncoder("");
            for (Parameter parameter : httpRequest.getQueryStringParameters()) {
                for (String value : parameter.getValues()) {
                    queryStringEncoder.addParam(parameter.getName(), value);
                }
            }
            URI updatedUri = new URI(
                    httpForward.getScheme().name().toLowerCase(),
                    originalUri.getUserInfo(),
                    httpForward.getHost(),
                    httpForward.getPort(),
                    httpRequest.getPath().startsWith("/") ? httpRequest.getPath() : "/" + httpRequest.getPath(),
                    (!Strings.isNullOrEmpty(queryStringEncoder.toString()) ? queryStringEncoder.toString() : null),
                    null
            );

            httpRequest.withURL(updatedUri.toString());
            httpRequest.replaceHeader(header(HttpHeaders.Names.HOST, String.format("%s:%d", updatedUri.getHost(), updatedUri.getPort())));
        } catch (URISyntaxException e) {
            logger.warn("URISyntaxException for url " + httpRequest.getURL(), e);
        }
    }

    private HttpResponse sendRequest(HttpRequest httpRequest) {
        if (httpRequest != null) {
            try {
                return httpClient.sendRequest(httpRequest);
            } catch (Exception e) {
                logger.error("Exception forwarding request " + httpRequest, e);
            }
        }
        return null;
    }
}

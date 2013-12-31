package org.mockserver.proxy.filters;

import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author jamesdbloom
 */
public class HopByHopHeaderFilter implements ProxyRequestFilter {

    public HttpRequest onRequest(HttpRequest httpRequest) {
        List<String> headersToRemove = Arrays.asList(
                "proxy-connection",
                "connection",
                "keep-alive",
                "transfer-encoding",
                "te",
                "trailer",
                "proxy-authorization",
                "proxy-authenticate",
                "upgrade"
        );
        List<Header> filteredHeaders = new ArrayList<>();
        for (Header header : httpRequest.getHeaders()) {
            if (!headersToRemove.contains(header.getName().toLowerCase(Locale.ENGLISH))) {
                filteredHeaders.add(header);
            }
        }
        return httpRequest.withHeaders(filteredHeaders);
    }
}

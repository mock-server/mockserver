package org.mockserver.filters;

import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author jamesdbloom
 */
public class HopByHopHeaderFilter implements RequestFilter {

    public HttpRequest onRequest(HttpRequest httpRequest) {
        if (httpRequest != null) {
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
            List<Header> filteredHeaders = new ArrayList<Header>();
            for (Header header : httpRequest.getHeaders()) {
                if (!headersToRemove.contains(header.getName().getValue().toLowerCase(Locale.ENGLISH))) {
                    filteredHeaders.add(header);
                }
            }
            return httpRequest.clone().withHeaders(filteredHeaders);
        } else {
            return null;
        }
    }
}

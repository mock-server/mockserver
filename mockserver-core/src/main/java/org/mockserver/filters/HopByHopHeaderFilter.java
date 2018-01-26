package org.mockserver.filters;

import org.mockserver.model.Header;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author jamesdbloom
 */
public class HopByHopHeaderFilter {

    public HttpRequest onRequest(HttpRequest request) {
        if (request != null) {
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
            Headers headers = new Headers();
            for (Header header : request.getHeaderList()) {
                if (!headersToRemove.contains(header.getName().getValue().toLowerCase(Locale.ENGLISH))) {
                    headers.withEntry(header);
                }
            }
            HttpRequest clonedRequest = request.clone();
            if (!headers.isEmpty()) {
                clonedRequest.withHeaders(headers);
            }
            return clonedRequest;
        } else {
            return null;
        }
    }
}

package org.mockserver.servlet.responsewriter;

import org.mockserver.cors.CORSHeaders;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.MockServerResponseToHttpServletResponseEncoder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;

import javax.servlet.http.HttpServletResponse;

import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAPI;
import static org.mockserver.configuration.ConfigurationProperties.enableCORSForAllResponses;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class ServletResponseWriter extends ResponseWriter {
    private static final CORSHeaders CORS_HEADERS = new CORSHeaders();
    private final HttpServletResponse httpServletResponse;
    private MockServerResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder;

    public ServletResponseWriter(MockServerLogger mockServerLogger, HttpServletResponse httpServletResponse) {
        this.httpServletResponse = httpServletResponse;
        this.mockServerResponseToHttpServletResponseEncoder = new MockServerResponseToHttpServletResponseEncoder(mockServerLogger);
    }

    @Override
    public void sendResponse(HttpRequest request, HttpResponse response) {
        mockServerResponseToHttpServletResponseEncoder.mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
    }

}

package org.mockserver.servlet.responsewriter;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.MockServerHttpResponseToHttpServletResponseEncoder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;

import javax.servlet.http.HttpServletResponse;

/**
 * @author jamesdbloom
 */
public class ServletResponseWriter extends ResponseWriter {
    private final HttpServletResponse httpServletResponse;
    @SuppressWarnings("FieldMayBeFinal")
    private MockServerHttpResponseToHttpServletResponseEncoder mockServerResponseToHttpServletResponseEncoder;

    public ServletResponseWriter(Configuration configuration, MockServerLogger mockServerLogger, HttpServletResponse httpServletResponse) {
        super(configuration);
        this.httpServletResponse = httpServletResponse;
        this.mockServerResponseToHttpServletResponseEncoder = new MockServerHttpResponseToHttpServletResponseEncoder(mockServerLogger);
    }

    @Override
    public void sendResponse(HttpRequest request, HttpResponse response) {
        mockServerResponseToHttpServletResponseEncoder.mapMockServerResponseToHttpServletResponse(response, httpServletResponse);
    }

}

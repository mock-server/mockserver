package org.mockserver.mock.action;

import com.google.common.util.concurrent.SettableFuture;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;

import static org.mockserver.scheduler.Scheduler.submit;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequestActionHandler extends HttpForwardAction {

    public HttpOverrideForwardedRequestActionHandler(MockServerLogger logFormatter) {
        super(logFormatter);
    }

    public SettableFuture<HttpResponse> handle(final HttpOverrideForwardedRequest httpOverrideForwardedRequest, final HttpRequest request) {
        return sendRequest(request.update(httpOverrideForwardedRequest.getHttpRequest()), null);
    }

}

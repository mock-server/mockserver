package org.mockserver.mock.action;

import java.util.List;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpWebHook;
import org.mockserver.model.HttpWebHookConfig;
import org.mockserver.model.HttpWebHookRequest;

/**
 * author Valeriy Mironichev
 */
public class HttpWebHookActionHandler extends HttpResponseActionHandler {

    public HttpResponse handle(HttpWebHook httpWebHook, HttpRequest httpRequest) {

        HttpWebHookConfig callbackConfig = httpWebHook.getHttpWebHookConfig();
        if (callbackConfig != null) {
            List<HttpWebHookRequest> requests = callbackConfig.getEndpoints();
            for (HttpWebHookRequest request : requests) {
                request.submit();
            }
        }
        return httpWebHook.getHttpResponse();
    }
}

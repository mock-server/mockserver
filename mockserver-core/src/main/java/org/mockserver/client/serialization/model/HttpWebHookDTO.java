package org.mockserver.client.serialization.model;

import org.mockserver.model.HttpWebHookConfig;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpWebHook;

/**
 * author Valeriy Mironichev
 */
public class HttpWebHookDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    private HttpWebHookConfig httpWebHookConfig;

    public HttpWebHookDTO(HttpWebHook httpWebHook) {
        if (httpWebHook != null) {
            httpWebHookConfig = httpWebHook.getHttpWebHookConfig();
        }
    }

    public HttpWebHookDTO() {
    }

    public HttpWebHook buildObject(HttpResponse httpResponse) {
        return new HttpWebHook().withHttpWebHookConfig(httpWebHookConfig).withHttpResponse(httpResponse);
    }

    public HttpWebHookConfig getHttpWebHookConfig() {
        return httpWebHookConfig;
    }

    public void setHttpWebHookConfig(HttpWebHookConfig httpWebHookConfig) {
        this.httpWebHookConfig = httpWebHookConfig;
    }

}

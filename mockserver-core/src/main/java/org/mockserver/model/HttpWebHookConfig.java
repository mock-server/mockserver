package org.mockserver.model;

import java.util.List;

/**
 * author Valeriy Mironichev
 */
public class HttpWebHookConfig {
    private List<HttpWebHookRequest> endpoints;

    public HttpWebHookConfig() {
    }

    public List<HttpWebHookRequest> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<HttpWebHookRequest> endpoints) {
        this.endpoints = endpoints;
    }

}

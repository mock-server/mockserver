package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ProxyPassMappingTest {

    @Test
    public void shouldParseHttpsTargetUri() {
        ProxyPassMapping mapping = ProxyPassMapping.proxyPass("/api/", "https://backend:8443/services/");
        assertThat(mapping.getTargetHost(), is("backend"));
        assertThat(mapping.getTargetPort(), is(8443));
        assertThat(mapping.getTargetPath(), is("/services"));
        assertThat(mapping.isTargetSecure(), is(true));
    }

    @Test
    public void shouldParseHttpTargetUri() {
        ProxyPassMapping mapping = ProxyPassMapping.proxyPass("/auth/", "http://auth-server:9090/");
        assertThat(mapping.getTargetHost(), is("auth-server"));
        assertThat(mapping.getTargetPort(), is(9090));
        assertThat(mapping.getTargetPath(), is(""));
        assertThat(mapping.isTargetSecure(), is(false));
    }

    @Test
    public void shouldDefaultToPort443ForHttps() {
        ProxyPassMapping mapping = ProxyPassMapping.proxyPass("/api/", "https://backend/services/");
        assertThat(mapping.getTargetPort(), is(443));
        assertThat(mapping.isTargetSecure(), is(true));
    }

    @Test
    public void shouldDefaultToPort80ForHttp() {
        ProxyPassMapping mapping = ProxyPassMapping.proxyPass("/api/", "http://backend/services/");
        assertThat(mapping.getTargetPort(), is(80));
        assertThat(mapping.isTargetSecure(), is(false));
    }

    @Test
    public void shouldPreserveHost() {
        ProxyPassMapping mapping = ProxyPassMapping.proxyPass("/api/", "https://backend:8443/services/")
            .withPreserveHost(true);
        assertThat(mapping.isPreserveHost(), is(true));
    }

    @Test
    public void shouldStripTrailingSlashFromTargetPath() {
        ProxyPassMapping mapping = ProxyPassMapping.proxyPass("/api/", "https://backend:8443/services/");
        assertThat(mapping.getTargetPath(), is("/services"));
    }

    @Test
    public void shouldHandleTargetUriWithoutPath() {
        ProxyPassMapping mapping = ProxyPassMapping.proxyPass("/api/", "https://backend:8443");
        assertThat(mapping.getTargetPath(), is(""));
    }

}

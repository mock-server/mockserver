package org.mockserver.proxyconfiguration;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;

public class ProxyConfigurationTest {

    @Before
    public void clearProperties() {
        System.clearProperty("mockserver.forwardHttpProxy");
        System.clearProperty("mockserver.forwardHttpsProxy");
        System.clearProperty("mockserver.forwardSocksProxy");
        System.clearProperty("mockserver.forwardProxyAuthenticationUsername");
        System.clearProperty("mockserver.forwardProxyAuthenticationPassword");
    }

    @Test
    public void shouldConfigureForwardHttpProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardHttpProxy());
        forwardHttpProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, forwardHttpProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpProxy"));
        assertThat(proxyConfiguration(), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.HTTP, proxyAddress, null, null))));
    }

    @Test
    public void shouldConfigureForwardHttpsProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardHttpsProxy());
        forwardHttpsProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertThat(proxyConfiguration(), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.HTTPS, proxyAddress, null, null))));
    }

    @Test
    public void shouldConfigureForwardHttpAndHttpsProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardHttpProxy());
        forwardHttpProxy(proxyAddress);
        assertNull(forwardHttpsProxy());
        forwardHttpsProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, forwardHttpProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpProxy"));
        assertEquals("/" + proxyAddress, forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertThat(proxyConfiguration(), equalTo(ImmutableList.of(
            proxyConfiguration(ProxyConfiguration.Type.HTTP, proxyAddress, null, null),
            proxyConfiguration(ProxyConfiguration.Type.HTTPS, proxyAddress, null, null)
        )));
    }

    @Test
    public void shouldConfigureForwardHttpsProxyWithAuthentication() {
        // given
        String proxyAddress = "127.0.0.1:1090";
        String userName = "userName";
        String password = "password";

        // when
        assertNull(forwardHttpsProxy());
        forwardHttpsProxy(proxyAddress);
        assertNull(forwardProxyAuthenticationUsername());
        forwardProxyAuthenticationUsername(userName);
        assertNull(forwardProxyAuthenticationPassword());
        forwardProxyAuthenticationPassword(password);

        // then
        assertEquals("/" + proxyAddress, forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertEquals(userName, forwardProxyAuthenticationUsername());
        assertEquals(userName, System.getProperty("mockserver.forwardProxyAuthenticationUsername"));
        assertEquals(password, forwardProxyAuthenticationPassword());
        assertEquals(password, System.getProperty("mockserver.forwardProxyAuthenticationPassword"));
        assertThat(proxyConfiguration(), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.HTTPS, proxyAddress, userName, password))));
    }

    @Test
    public void shouldConfigureForwardSocksProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardSocksProxy());
        forwardSocksProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        assertThat(proxyConfiguration(), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.SOCKS5, proxyAddress, null, null))));
    }

    @Test
    public void shouldConfigureForwardSocksProxyWithAuthentication() {
        // given
        String proxyAddress = "127.0.0.1:1090";
        String userName = "userName";
        String password = "password";

        // when
        assertNull(forwardSocksProxy());
        forwardSocksProxy(proxyAddress);
        assertNull(forwardProxyAuthenticationUsername());
        forwardProxyAuthenticationUsername(userName);
        assertNull(forwardProxyAuthenticationPassword());
        forwardProxyAuthenticationPassword(password);

        // then
        assertEquals("/" + proxyAddress, forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        assertEquals(userName, forwardProxyAuthenticationUsername());
        assertEquals(userName, System.getProperty("mockserver.forwardProxyAuthenticationUsername"));
        assertEquals(password, forwardProxyAuthenticationPassword());
        assertEquals(password, System.getProperty("mockserver.forwardProxyAuthenticationPassword"));
        assertThat(proxyConfiguration(), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.SOCKS5, proxyAddress, userName, password))));
    }

    @Test
    public void shouldNotAllowConfigurationOfForwardHttpProxyAndSocksProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardHttpProxy());
        forwardHttpProxy(proxyAddress);
        assertNull(forwardSocksProxy());
        forwardSocksProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, forwardHttpProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpProxy"));
        assertEquals("/" + proxyAddress, forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, ProxyConfiguration::proxyConfiguration);
        assertThat(illegalArgumentException.getMessage(), equalTo("Invalid proxy configuration it is not possible to configure HTTP or HTTPS proxy at the same time as a SOCKS proxy, please choose either HTTP(S) proxy OR a SOCKS proxy"));
    }

    @Test
    public void shouldNotAllowConfigurationOfForwardHttpsProxyAndSocksProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardHttpsProxy());
        forwardHttpsProxy(proxyAddress);
        assertNull(forwardSocksProxy());
        forwardSocksProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertEquals("/" + proxyAddress, forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, ProxyConfiguration::proxyConfiguration);
        assertThat(illegalArgumentException.getMessage(), equalTo("Invalid proxy configuration it is not possible to configure HTTP or HTTPS proxy at the same time as a SOCKS proxy, please choose either HTTP(S) proxy OR a SOCKS proxy"));
    }

    @Test
    public void shouldNotAllowConfigurationOfForwardHttpAndHttpsProxyAndSocksProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(forwardHttpProxy());
        forwardHttpProxy(proxyAddress);
        assertNull(forwardHttpsProxy());
        forwardHttpsProxy(proxyAddress);
        assertNull(forwardSocksProxy());
        forwardSocksProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, forwardHttpProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpProxy"));
        assertEquals("/" + proxyAddress, forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertEquals("/" + proxyAddress, forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, ProxyConfiguration::proxyConfiguration);
        assertThat(illegalArgumentException.getMessage(), equalTo("Invalid proxy configuration it is not possible to configure HTTP or HTTPS proxy at the same time as a SOCKS proxy, please choose either HTTP(S) proxy OR a SOCKS proxy"));
    }

}
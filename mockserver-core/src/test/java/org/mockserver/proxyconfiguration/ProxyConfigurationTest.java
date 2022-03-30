package org.mockserver.proxyconfiguration;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;

import java.net.InetSocketAddress;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.proxyconfiguration.ProxyConfiguration.proxyConfiguration;

public class ProxyConfigurationTest {

    private InetSocketAddress originalForwardHttpProxy;
    private InetSocketAddress originalForwardHttpsProxy;
    private InetSocketAddress originalForwardSocksProxy;
    private String originalForwardProxyAuthenticationUsername;
    private String originalForwardProxyAuthenticationPassword;

    @Before
    public void recordOriginalPropertyValues() {
        originalForwardHttpProxy = ConfigurationProperties.forwardHttpProxy();
        originalForwardHttpsProxy = ConfigurationProperties.forwardHttpsProxy();
        originalForwardSocksProxy = ConfigurationProperties.forwardSocksProxy();
        originalForwardProxyAuthenticationUsername = ConfigurationProperties.forwardProxyAuthenticationUsername();
        originalForwardProxyAuthenticationPassword = ConfigurationProperties.forwardProxyAuthenticationPassword();
    }

    @After
    public void restoreOriginalPropertyValues() {
        ConfigurationProperties.forwardHttpProxy(originalForwardHttpProxy != null ? originalForwardHttpProxy.toString() : "");
        ConfigurationProperties.forwardHttpsProxy(originalForwardHttpsProxy != null ? originalForwardHttpsProxy.toString() : "");
        ConfigurationProperties.forwardSocksProxy(originalForwardSocksProxy != null ? originalForwardSocksProxy.toString() : "");
        ConfigurationProperties.forwardProxyAuthenticationUsername(originalForwardProxyAuthenticationUsername);
        ConfigurationProperties.forwardProxyAuthenticationPassword(originalForwardProxyAuthenticationPassword);
    }

    @Test
    public void shouldConfigureForwardHttpProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(ConfigurationProperties.forwardHttpProxy());
        ConfigurationProperties.forwardHttpProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardHttpProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpProxy"));
        assertThat(proxyConfiguration(configuration()), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.HTTP, proxyAddress, "", ""))));
    }

    @Test
    public void shouldConfigureForwardHttpsProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(ConfigurationProperties.forwardHttpsProxy());
        ConfigurationProperties.forwardHttpsProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertThat(proxyConfiguration(configuration()), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.HTTPS, proxyAddress, "", ""))));
    }

    @Test
    public void shouldConfigureForwardHttpAndHttpsProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(ConfigurationProperties.forwardHttpProxy());
        ConfigurationProperties.forwardHttpProxy(proxyAddress);
        assertNull(ConfigurationProperties.forwardHttpsProxy());
        ConfigurationProperties.forwardHttpsProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardHttpProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpProxy"));
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertThat(proxyConfiguration(configuration()), equalTo(ImmutableList.of(
                proxyConfiguration(ProxyConfiguration.Type.HTTP, proxyAddress, "", ""),
                proxyConfiguration(ProxyConfiguration.Type.HTTPS, proxyAddress, "", "")
        )));
    }

    @Test
    public void shouldConfigureForwardHttpsProxyWithAuthentication() {
        // given
        String proxyAddress = "127.0.0.1:1090";
        String userName = "userName";
        String password = "password";

        // when
        assertNull(ConfigurationProperties.forwardHttpsProxy());
        ConfigurationProperties.forwardHttpsProxy(proxyAddress);
        assertThat(ConfigurationProperties.forwardProxyAuthenticationUsername(), equalTo(""));
        ConfigurationProperties.forwardProxyAuthenticationUsername(userName);
        assertThat(ConfigurationProperties.forwardProxyAuthenticationPassword(), equalTo(""));
        ConfigurationProperties.forwardProxyAuthenticationPassword(password);

        // then
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertEquals(userName, ConfigurationProperties.forwardProxyAuthenticationUsername());
        assertEquals(userName, System.getProperty("mockserver.forwardProxyAuthenticationUsername"));
        assertEquals(password, ConfigurationProperties.forwardProxyAuthenticationPassword());
        assertEquals(password, System.getProperty("mockserver.forwardProxyAuthenticationPassword"));
        assertThat(proxyConfiguration(configuration()), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.HTTPS, proxyAddress, userName, password))));
    }

    @Test
    public void shouldConfigureForwardSocksProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(ConfigurationProperties.forwardSocksProxy());
        ConfigurationProperties.forwardSocksProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        assertThat(proxyConfiguration(configuration()), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.SOCKS5, proxyAddress, "", ""))));
    }

    @Test
    public void shouldConfigureForwardSocksProxyWithAuthentication() {
        // given
        String proxyAddress = "127.0.0.1:1090";
        String userName = "userName";
        String password = "password";

        // when
        assertNull(ConfigurationProperties.forwardSocksProxy());
        ConfigurationProperties.forwardSocksProxy(proxyAddress);
        assertThat(ConfigurationProperties.forwardProxyAuthenticationUsername(), equalTo(""));
        ConfigurationProperties.forwardProxyAuthenticationUsername(userName);
        assertThat(ConfigurationProperties.forwardProxyAuthenticationPassword(), equalTo(""));
        ConfigurationProperties.forwardProxyAuthenticationPassword(password);

        // then
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        assertEquals(userName, ConfigurationProperties.forwardProxyAuthenticationUsername());
        assertEquals(userName, System.getProperty("mockserver.forwardProxyAuthenticationUsername"));
        assertEquals(password, ConfigurationProperties.forwardProxyAuthenticationPassword());
        assertEquals(password, System.getProperty("mockserver.forwardProxyAuthenticationPassword"));
        assertThat(proxyConfiguration(configuration()), equalTo(ImmutableList.of(proxyConfiguration(ProxyConfiguration.Type.SOCKS5, proxyAddress, userName, password))));
    }

    @Test
    public void shouldNotAllowConfigurationOfForwardHttpProxyAndSocksProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(ConfigurationProperties.forwardHttpProxy());
        ConfigurationProperties.forwardHttpProxy(proxyAddress);
        assertNull(ConfigurationProperties.forwardSocksProxy());
        ConfigurationProperties.forwardSocksProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardHttpProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpProxy"));
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> proxyConfiguration(configuration()));
        assertThat(illegalArgumentException.getMessage(), equalTo("Invalid proxy configuration it is not possible to configure HTTP or HTTPS proxy at the same time as a SOCKS proxy, please choose either HTTP(S) proxy OR a SOCKS proxy"));
    }

    @Test
    public void shouldNotAllowConfigurationOfForwardHttpsProxyAndSocksProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(ConfigurationProperties.forwardHttpsProxy());
        ConfigurationProperties.forwardHttpsProxy(proxyAddress);
        assertNull(ConfigurationProperties.forwardSocksProxy());
        ConfigurationProperties.forwardSocksProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> proxyConfiguration(configuration()));
        assertThat(illegalArgumentException.getMessage(), equalTo("Invalid proxy configuration it is not possible to configure HTTP or HTTPS proxy at the same time as a SOCKS proxy, please choose either HTTP(S) proxy OR a SOCKS proxy"));
    }

    @Test
    public void shouldNotAllowConfigurationOfForwardHttpAndHttpsProxyAndSocksProxy() {
        // given
        String proxyAddress = "127.0.0.1:1090";

        // when
        assertNull(ConfigurationProperties.forwardHttpProxy());
        ConfigurationProperties.forwardHttpProxy(proxyAddress);
        assertNull(ConfigurationProperties.forwardHttpsProxy());
        ConfigurationProperties.forwardHttpsProxy(proxyAddress);
        assertNull(ConfigurationProperties.forwardSocksProxy());
        ConfigurationProperties.forwardSocksProxy(proxyAddress);

        // then
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardHttpProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpProxy"));
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardHttpsProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardHttpsProxy"));
        assertEquals("/" + proxyAddress, ConfigurationProperties.forwardSocksProxy().toString());
        assertEquals(proxyAddress, System.getProperty("mockserver.forwardSocksProxy"));
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> proxyConfiguration(configuration()));
        assertThat(illegalArgumentException.getMessage(), equalTo("Invalid proxy configuration it is not possible to configure HTTP or HTTPS proxy at the same time as a SOCKS proxy, please choose either HTTP(S) proxy OR a SOCKS proxy"));
    }

}
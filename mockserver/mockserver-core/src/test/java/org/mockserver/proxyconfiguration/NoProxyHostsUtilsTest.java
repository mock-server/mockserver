package org.mockserver.proxyconfiguration;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class NoProxyHostsUtilsTest {

    @Test
    public void shouldMatchExactHostname() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("example.com", "example.com"), is(true));
    }

    @Test
    public void shouldNotMatchDifferentHostname() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("other.com", "example.com"), is(false));
    }

    @Test
    public void shouldMatchCaseInsensitive() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("EXAMPLE.COM", "example.com"), is(true));
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("example.com", "EXAMPLE.COM"), is(true));
    }

    @Test
    public void shouldMatchWildcardPrefix() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("api.internal.corp", "*.internal.corp"), is(true));
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("deep.api.internal.corp", "*.internal.corp"), is(true));
    }

    @Test
    public void shouldMatchWildcardForParentDomain() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("internal.corp", "*.internal.corp"), is(true));
    }

    @Test
    public void shouldNotMatchWildcardForDifferentDomain() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("example.com", "*.internal.corp"), is(false));
    }

    @Test
    public void shouldStripPortBeforeMatching() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("example.com:8080", "example.com"), is(true));
    }

    @Test
    public void shouldHandleIPv6WithBrackets() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("[::1]:8080", "::1"), is(true));
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("[::1]", "::1"), is(true));
    }

    @Test
    public void shouldHandleIPv6WithoutBrackets() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("::1", "::1"), is(true));
    }

    @Test
    public void shouldHandleIPv4() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("192.168.1.1", "192.168.1.1"), is(true));
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("192.168.1.1:8080", "192.168.1.1"), is(true));
    }

    @Test
    public void shouldMatchMultiplePatterns() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("example.com", "other.com,example.com,test.com"), is(true));
    }

    @Test
    public void shouldHandleWhitespaceInPatternList() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("example.com", " example.com , other.com "), is(true));
    }

    @Test
    public void shouldReturnFalseForBlankInputs() {
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("", "example.com"), is(false));
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("example.com", ""), is(false));
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList(null, "example.com"), is(false));
        assertThat(NoProxyHostsUtils.isHostOnNoProxyList("example.com", null), is(false));
    }

    @Test
    public void shouldExtractHostFromIPv6WithBracketsAndPort() {
        assertThat(NoProxyHostsUtils.extractHost("[::1]:8080"), is("::1"));
    }

    @Test
    public void shouldExtractHostFromIPv6WithBracketsOnly() {
        assertThat(NoProxyHostsUtils.extractHost("[::1]"), is("::1"));
    }

    @Test
    public void shouldExtractHostFromIPv6WithoutBrackets() {
        assertThat(NoProxyHostsUtils.extractHost("::1"), is("::1"));
    }

    @Test
    public void shouldExtractHostFromHostnameWithPort() {
        assertThat(NoProxyHostsUtils.extractHost("example.com:8080"), is("example.com"));
    }

    @Test
    public void shouldExtractHostFromHostnameWithoutPort() {
        assertThat(NoProxyHostsUtils.extractHost("example.com"), is("example.com"));
    }

}

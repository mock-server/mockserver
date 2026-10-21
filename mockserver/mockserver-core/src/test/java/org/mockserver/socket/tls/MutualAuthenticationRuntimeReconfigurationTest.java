package org.mockserver.socket.tls;

import io.netty.handler.ssl.SslContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Asserts that requiring mTLS at runtime actually reaches the server SSL context.
 *
 * <p>{@link NettySslContextFactory#createServerSslContext()} caches the built context and used to rebuild
 * it only when {@code Configuration.rebuildServerTLSContext()} was set. Neither
 * {@code Configuration.tlsMutualAuthenticationRequired(Boolean)} nor the static
 * {@code ConfigurationProperties.tlsMutualAuthenticationRequired(boolean)} set that flag — unlike the
 * subject-alternative-name setters, which do. So enabling mTLS on a running instance was accepted and
 * silently ignored: the context stayed pinned at {@code ClientAuth.OPTIONAL} with
 * {@code InsecureTrustManagerFactory}, and certificateless clients kept connecting.
 *
 * <p>These tests assert the OBSERVABLE consequence — that the cached context is replaced — for each
 * configuration route. Asserting the resulting {@code ClientAuth} directly is not possible through
 * Netty's public {@link SslContext} API, so context identity is the available proxy; combined with the
 * construction code reading {@code tlsMutualAuthenticationRequired()} at build time, a rebuilt context
 * necessarily carries the new setting.
 */
public class MutualAuthenticationRuntimeReconfigurationTest {

    private MockServerLogger mockServerLogger;

    // these tests are about rebuilding the context, not about which CA it trusts
    private static Configuration bundledCaConfiguration() {
        return Configuration.configuration().tlsMutualAuthenticationAllowBundledCa(true);
    }

    @Before
    public void setUp() {
        mockServerLogger = new MockServerLogger();
        ConfigurationProperties.tlsMutualAuthenticationRequired(false);
    }

    @After
    public void tearDown() {
        ConfigurationProperties.tlsMutualAuthenticationRequired(false);
    }

    @Test
    public void shouldRebuildServerSslContextWhenMutualAuthenticationRequiredSetOnConfiguration() {
        Configuration configuration = bundledCaConfiguration();
        NettySslContextFactory sslContextFactory = new NettySslContextFactory(configuration, mockServerLogger, true);

        SslContext before = sslContextFactory.createServerSslContext();
        assertThat("cached context should be reused when nothing changed",
            sslContextFactory.createServerSslContext(), is(sameInstance(before)));

        configuration.tlsMutualAuthenticationRequired(true);

        assertThat("requiring mTLS at runtime must rebuild the server SSL context, not reuse the "
                + "context built with ClientAuth.OPTIONAL and InsecureTrustManagerFactory",
            sslContextFactory.createServerSslContext(), is(not(sameInstance(before))));
    }

    @Test
    public void shouldRebuildServerSslContextWhenMutualAuthenticationRequiredSetViaSystemProperty() {
        // the static route has no Configuration instance on which to raise rebuildServerTLSContext, so it
        // is covered by the client-authentication signature check inside NettySslContextFactory
        Configuration configuration = bundledCaConfiguration();
        NettySslContextFactory sslContextFactory = new NettySslContextFactory(configuration, mockServerLogger, true);

        SslContext before = sslContextFactory.createServerSslContext();

        ConfigurationProperties.tlsMutualAuthenticationRequired(true);

        assertThat("requiring mTLS via the system-property route must also rebuild the server SSL context",
            sslContextFactory.createServerSslContext(), is(not(sameInstance(before))));
    }

    @Test
    public void shouldRebuildServerSslContextWhenMutualAuthenticationDisabledAgain() {
        Configuration configuration = bundledCaConfiguration().tlsMutualAuthenticationRequired(true);
        NettySslContextFactory sslContextFactory = new NettySslContextFactory(configuration, mockServerLogger, true);

        SslContext before = sslContextFactory.createServerSslContext();
        configuration.tlsMutualAuthenticationRequired(false);

        assertThat("relaxing mTLS at runtime must also take effect",
            sslContextFactory.createServerSslContext(), is(not(sameInstance(before))));
    }

    @Test
    public void shouldRebuildEvenWhenPreventCertificateDynamicUpdateIsSet() {
        // preventCertificateDynamicUpdate suppresses certificate REGENERATION on a domain-list change; it
        // must not be able to suppress a tightening of client-authentication policy
        Configuration configuration = bundledCaConfiguration().preventCertificateDynamicUpdate(true);
        NettySslContextFactory sslContextFactory = new NettySslContextFactory(configuration, mockServerLogger, true);

        SslContext before = sslContextFactory.createServerSslContext();
        configuration.tlsMutualAuthenticationRequired(true);

        assertThat("preventCertificateDynamicUpdate must not suppress an mTLS policy change",
            sslContextFactory.createServerSslContext(), is(not(sameInstance(before))));
    }

    @Test
    public void shouldNotRebuildWhenNothingRelevantChanged() {
        Configuration configuration = bundledCaConfiguration();
        NettySslContextFactory sslContextFactory = new NettySslContextFactory(configuration, mockServerLogger, true);

        SslContext before = sslContextFactory.createServerSslContext();
        // an unrelated setting must not force an expensive context rebuild on the hot path
        configuration.maxExpectations(4321);

        assertThat(sslContextFactory.createServerSslContext(), is(sameInstance(before)));
    }
}

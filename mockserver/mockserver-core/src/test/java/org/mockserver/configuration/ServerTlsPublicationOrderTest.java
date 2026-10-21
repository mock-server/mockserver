package org.mockserver.configuration;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.ConfigurationDTO;
import org.mockserver.socket.tls.NettySslContextFactory;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * An update must publish the server TLS snapshot BEFORE it advances the TLS generation. In the other
 * order, a handshake between the two steps sees the new generation with the old snapshot, revalidates
 * the cached context under the new generation, and keeps it after the snapshot changes.
 */
public class ServerTlsPublicationOrderTest {

    @Test
    public void shouldNotKeepAContextRevalidatedBetweenPublicationAndGenerationAdvance() {
        AtomicBoolean armed = new AtomicBoolean();
        AtomicBoolean handshakeRan = new AtomicBoolean();
        AtomicReference<Supplier<SslContext>> handshake = new AtomicReference<>();
        // about publication ordering, not which CA mTLS trusts, so the bundled CA is allowed
        Configuration configuration = new Configuration() {
            @Override
            void serverTlsSettingsPublished() {
                if (armed.compareAndSet(true, false)) {
                    handshake.get().get();
                    handshakeRan.set(true);
                }
            }
        }.tlsMutualAuthenticationAllowBundledCa(true).tlsMutualAuthenticationRequired(false);
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        handshake.set(factory::createServerSslContext);
        assertThat(requiresClientCertificate(factory.createServerSslContext()), is(false));

        armed.set(true);
        new ConfigurationDTO().setTlsMutualAuthenticationRequired(true).applyTo(configuration);

        assertThat("the seam must run during publication", handshakeRan.get(), is(true));
        assertThat(requiresClientCertificate(factory.createServerSslContext()), is(true));
    }

    private static boolean requiresClientCertificate(SslContext sslContext) {
        SSLEngine engine = sslContext.newEngine(ByteBufAllocator.DEFAULT);
        try {
            return engine.getNeedClientAuth();
        } finally {
            ReferenceCountUtil.release(engine);
        }
    }
}

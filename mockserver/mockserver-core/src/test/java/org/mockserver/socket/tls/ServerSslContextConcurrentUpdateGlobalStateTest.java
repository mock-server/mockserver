package org.mockserver.socket.tls;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.ConfigurationDTO;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

/**
 * The cached server {@link SslContext} must always match the signature it is cached under, even when a
 * configuration update lands while the context is being built.
 *
 * <p>The build used to read its inputs live and record the signature after building, so a {@code PUT}
 * that changed {@code tlsMutualAuthenticationRequired} mid-build was recorded as an input of a context
 * built without it, and that stale context was served until some other input changed.
 *
 * <p>Sequential lane: pausing the build goes through the static
 * {@link NettySslContextFactory#sslServerContextBuilderCustomizer}.
 */
public class ServerSslContextConcurrentUpdateGlobalStateTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private UnaryOperator<SslContextBuilder> originalCustomizer;

    @Before
    public void captureCustomizer() {
        originalCustomizer = NettySslContextFactory.sslServerContextBuilderCustomizer;
    }

    @After
    public void restoreCustomizer() {
        NettySslContextFactory.sslServerContextBuilderCustomizer = originalCustomizer;
    }

    @Test
    public void shouldRebuildWhenMutualAuthenticationIsRequiredWhileTheContextIsBeingBuilt() throws Exception {
        Configuration configuration = Configuration.configuration().tlsMutualAuthenticationAllowBundledCa(true).tlsMutualAuthenticationRequired(false);
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), true);

        SslContext builtDuringUpdate = buildWhile(factory, () -> new ConfigurationDTO()
            .setTlsMutualAuthenticationRequired(true)
            .setTlsMutualAuthenticationCertificateChain(ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE)
            .applyTo(configuration));

        assertThat("the in-flight build uses the configuration it started from", requiresClientCertificate(builtDuringUpdate), is(false));
        assertThat("the next handshake must get a context that requires a client certificate",
            requiresClientCertificate(factory.createServerSslContext()), is(true));
    }

    @Test
    public void shouldRebuildWhenMutualAuthenticationIsDroppedWhileTheContextIsBeingBuilt() throws Exception {
        Configuration configuration = Configuration.configuration().tlsMutualAuthenticationAllowBundledCa(true).tlsMutualAuthenticationRequired(true);
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), true);

        SslContext builtDuringUpdate = buildWhile(factory, () -> new ConfigurationDTO()
            .setTlsMutualAuthenticationRequired(false)
            .applyTo(configuration));

        assertThat(requiresClientCertificate(builtDuringUpdate), is(true));
        assertThat(requiresClientCertificate(factory.createServerSslContext()), is(false));
    }

    @Test
    public void shouldKeepTheContextWhenTheBuildOnlyDerivedItsOwnCertificatePaths() throws Exception {
        Configuration configuration = Configuration.configuration()
            .dynamicallyCreateCertificateAuthorityCertificate(true)
            .directoryToSaveDynamicSSLCertificate(temporaryFolder.getRoot().getAbsolutePath());
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), true);

        SslContext first = factory.createServerSslContext();

        // the build wrote the dynamic CA paths into the configuration; that alone must not force a rebuild
        assertThat(configuration.certificateAuthorityCertificate().startsWith(temporaryFolder.getRoot().getAbsolutePath()), is(true));
        assertThat(factory.createServerSslContext(), is(sameInstance(first)));
    }

    @Test
    public void shouldRebuildWhenAFixedCertificateIsConfiguredWhileAGeneratedOneIsBeingBuilt() throws Exception {
        // a valid fixed leaf pair, signed by the default CA: generate one and keep the files it saves
        Configuration generator = Configuration.configuration()
            .preventCertificateDynamicUpdate(true)
            .directoryToSaveDynamicSSLCertificate(temporaryFolder.getRoot().getAbsolutePath());
        new NettySslContextFactory(generator, new MockServerLogger(), true).createServerSslContext();
        String fixedPrivateKeyPath = generator.privateKeyPath();
        String fixedCertificatePath = generator.x509CertificatePath();

        Configuration configuration = Configuration.configuration().privateKeyPath("").x509CertificatePath("");
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), true);

        SslContext builtDuringUpdate = buildWhile(factory, () -> new ConfigurationDTO()
            .setPrivateKeyPath(fixedPrivateKeyPath)
            .setX509CertificatePath(fixedCertificatePath)
            .applyTo(configuration));

        // the build generated a leaf; the leaf paths it wrote are only trusted when no update landed meanwhile
        assertThat(factory.createServerSslContext(), is(not(sameInstance(builtDuringUpdate))));
    }

    @Test
    public void shouldRebuildWhenTheCachedContextWasRevalidatedDuringAnUpdate() throws Exception {
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        // applyTo writes tlsMutualAuthenticationRequired, then later transparentProxyEnabled
        Configuration configuration = new Configuration() {
            @Override
            public Configuration transparentProxyEnabled(Boolean transparentProxyEnabled) {
                paused.countDown();
                try {
                    resume.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return super.transparentProxyEnabled(transparentProxyEnabled);
            }
        }.tlsMutualAuthenticationAllowBundledCa(true).tlsMutualAuthenticationRequired(false);
        NettySslContextFactory factory = new NettySslContextFactory(configuration, new MockServerLogger(), true);
        SslContext before = factory.createServerSslContext();

        Thread put = new Thread(() -> new ConfigurationDTO()
            .setTlsMutualAuthenticationRequired(true)
            .setTransparentProxyEnabled(false)
            .applyTo(configuration), "configuration-put");
        put.start();
        try {
            assertThat("the PUT never reached the pause point", paused.await(10, TimeUnit.SECONDS), is(true));
            // the mTLS write moved the generation but the snapshot is unchanged, so the cached context is revalidated
            assertThat(factory.createServerSslContext(), is(sameInstance(before)));
        } finally {
            resume.countDown();
            put.join(TimeUnit.SECONDS.toMillis(10));
        }

        assertThat("publishing the update must invalidate the revalidated context",
            requiresClientCertificate(factory.createServerSslContext()), is(true));
    }

    /**
     * Starts a context build, runs {@code update} while the build is paused after its inputs are read,
     * and returns the context that build produced.
     */
    private SslContext buildWhile(NettySslContextFactory factory, Runnable update) throws Exception {
        CountDownLatch inBuild = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean armed = new AtomicBoolean(true);
        NettySslContextFactory.sslServerContextBuilderCustomizer = builder -> {
            if (armed.compareAndSet(true, false)) {
                inBuild.countDown();
                try {
                    resume.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return builder;
        };
        AtomicReference<SslContext> built = new AtomicReference<>();
        Thread build = new Thread(() -> built.set(factory.createServerSslContext()), "server-ssl-context-build");
        build.start();
        try {
            assertThat("the build never reached the pause point", inBuild.await(10, TimeUnit.SECONDS), is(true));
            update.run();
        } finally {
            resume.countDown();
            build.join(TimeUnit.SECONDS.toMillis(30));
        }
        assertThat("the paused build did not finish", built.get() != null, is(true));
        return built.get();
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

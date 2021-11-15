package org.mockserver.socket.tls;

import org.junit.Test;
import org.junit.AfterClass;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockserver.configuration.Configuration.configuration;

public class CustomKeyAndCertificateFactorySupplierTest {

    @Test
    public void shouldReturnCustomFactory() {
        Function<MockServerLogger, KeyAndCertificateFactory> originalCustomKeyAndCertificateFactorySupplier = KeyAndCertificateFactoryFactory.getCustomKeyAndCertificateFactorySupplier();

        // given
        Configuration configuration = configuration();
        MockServerLogger mockServerLogger = new MockServerLogger();
        KeyAndCertificateFactory factoryInstance = new KeyAndCertificateFactory() {
            @Override
            public void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate() {
            }

            @Override
            public void buildAndSavePrivateKeyAndX509Certificate() {
            }

            @Override
            public boolean certificateNotYetCreated() {
                return false;
            }

            @Override
            public PrivateKey privateKey() {
                return null;
            }

            @Override
            public X509Certificate x509Certificate() {
                return null;
            }

            @Override
            public X509Certificate certificateAuthorityX509Certificate() {
                return null;
            }
        };

        try {
            // when
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(logger -> factoryInstance);

            // then
            assertThat(KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory(configuration, mockServerLogger), equalTo(factoryInstance));
        } finally {
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(originalCustomKeyAndCertificateFactorySupplier);
        }
    }

    @Test
    public void setServerModifier_shouldBeCalled() {
        final AtomicBoolean customizerCallFlag = new AtomicBoolean(false);
        NettySslContextFactory.sslServerContextBuilderCustomizer = builder -> {
            customizerCallFlag.set(true);
            return builder;
        };

        new NettySslContextFactory(new Configuration(), mock(MockServerLogger.class), true)
            .createServerSslContext();

        assertTrue(customizerCallFlag.get());
    }

    @Test
    public void setClientModifier_shouldBeCalled() {
        final AtomicBoolean customizerCallFlag = new AtomicBoolean(false);
        NettySslContextFactory.sslClientContextBuilderCustomizer = builder -> {
            customizerCallFlag.set(true);
            return builder;
        };

        new NettySslContextFactory(new Configuration(), mock(MockServerLogger.class), false)
            .createClientSslContext(false);

        assertTrue(customizerCallFlag.get());
    }

    @AfterClass
    public static void resetSupplier() {
        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(null);
    }
}

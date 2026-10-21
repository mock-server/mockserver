package org.mockserver.socket.tls;

import org.junit.AfterClass;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.ConfigurationDTO;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.configuration.ConfigurationProperties.DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE;

public class CustomKeyAndCertificateFactorySupplierTest {

    @Test
    public void shouldReturnCustomFactory() {
        KeyAndCertificateFactorySupplier originalCustomKeyAndCertificateFactorySupplier
            = KeyAndCertificateFactoryFactory.getCustomKeyAndCertificateFactorySupplier();

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

            @Override
            public List<X509Certificate> certificateChain() {
                return null;
            }
        };

        try {
            // when
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier((logger, isServer, config) -> factoryInstance);

            // then
            assertThat(KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory(configuration, mockServerLogger), equalTo(factoryInstance));
        } finally {
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(originalCustomKeyAndCertificateFactorySupplier);
        }
    }

    @Test
    public void shouldDecideDataPlaneMutualTlsTrustFromTheCustomFactoryCertificateAuthority() {
        KeyAndCertificateFactorySupplier originalCustomKeyAndCertificateFactorySupplier
            = KeyAndCertificateFactoryFactory.getCustomKeyAndCertificateFactorySupplier();
        // mTLS on, no chain, and certificateAuthorityCertificate left at the bundled default
        Configuration configuration = configuration()
            .tlsMutualAuthenticationRequired(true)
            .tlsMutualAuthenticationCertificateChain("")
            .tlsMutualAuthenticationAllowBundledCa(false)
            .dynamicallyCreateCertificateAuthorityCertificate(false)
            .certificateAuthorityCertificate(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE)
            .proactivelyInitialiseTLS(false);
        try {
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier((logger, isServer, config) -> factoryWithCertificateAuthority("org/mockserver/authentication/mtls/ca.pem"));
            DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(configuration, new MockServerLogger());
            DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setTlsMutualAuthenticationRequired(true), configuration, new MockServerLogger());

            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier((logger, isServer, config) -> factoryWithCertificateAuthority(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE));
            assertThrows(DataPlaneMutualTlsConfigurationException.class, () -> DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(configuration, new MockServerLogger()));
        } finally {
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(originalCustomKeyAndCertificateFactorySupplier);
        }
    }

    @Test
    public void shouldNotBuildTheCustomFactoryToCheckDataPlaneMutualTlsWhenItIsOff() {
        KeyAndCertificateFactorySupplier originalCustomKeyAndCertificateFactorySupplier
            = KeyAndCertificateFactoryFactory.getCustomKeyAndCertificateFactorySupplier();
        Configuration configuration = configuration()
            .tlsMutualAuthenticationRequired(false)
            .tlsMutualAuthenticationCertificateChain("")
            .proactivelyInitialiseTLS(false);
        AtomicInteger built = new AtomicInteger();
        try {
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier((logger, isServer, config) -> {
                built.incrementAndGet();
                return factoryWithCertificateAuthority(DEFAULT_CERTIFICATE_AUTHORITY_X509_CERTIFICATE);
            });

            DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(configuration, new MockServerLogger());
            DataPlaneMutualTlsTrust.requireUsableDataPlaneMutualTls(new ConfigurationDTO().setMaxExpectations(10), configuration, new MockServerLogger());

            assertThat(built.get(), equalTo(0));
        } finally {
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(originalCustomKeyAndCertificateFactorySupplier);
        }
    }

    private static KeyAndCertificateFactory factoryWithCertificateAuthority(String certificateAuthority) {
        KeyAndCertificateFactory factory = mock(KeyAndCertificateFactory.class);
        when(factory.certificateAuthorityX509Certificate()).thenReturn(PEMToFile.x509FromPEMFile(certificateAuthority));
        return factory;
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
            .createClientSslContext(false, false);

        assertTrue(customizerCallFlag.get());
    }

    @AfterClass
    public static void resetSupplier() {
        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(null);
    }
}

package org.mockserver.socket.tls;

import java.util.function.BiFunction;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockserver.configuration.Configuration.configuration;

public class CustomKeyAndCertificateFactorySupplierTest {

    @Test
    public void shouldReturnCustomFactory() {
        BiFunction<MockServerLogger, Boolean, KeyAndCertificateFactory> originalCustomKeyAndCertificateFactorySupplier = KeyAndCertificateFactoryFactory.getCustomKeyAndCertificateFactorySupplier();

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
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier((logger, isServer) -> factoryInstance);

            // then
            assertThat(KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory(configuration, mockServerLogger), equalTo(factoryInstance));
        } finally {
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(originalCustomKeyAndCertificateFactorySupplier);
        }
    }

}

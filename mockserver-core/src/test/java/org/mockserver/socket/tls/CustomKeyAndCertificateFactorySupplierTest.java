package org.mockserver.socket.tls;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class CustomKeyAndCertificateFactorySupplierTest {

    @Test
    public void shouldReturnCustomFactory() {
        Function<MockServerLogger, KeyAndCertificateFactory> originalCustomKeyAndCertificateFactorySupplier = KeyAndCertificateFactoryFactory.getCustomKeyAndCertificateFactorySupplier();

        // given
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
            assertThat(KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory(mockServerLogger), equalTo(factoryInstance));
        } finally {
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(originalCustomKeyAndCertificateFactorySupplier);
        }
    }

}

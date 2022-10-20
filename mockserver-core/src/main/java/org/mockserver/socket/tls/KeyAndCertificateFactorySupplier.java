package org.mockserver.socket.tls;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;

@FunctionalInterface
public interface KeyAndCertificateFactorySupplier {
    KeyAndCertificateFactory buildKeyAndCertificateFactory(
        MockServerLogger logger,
        boolean isServerInstance,
        Configuration configuration);
}

package org.mockserver.socket.tls;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import org.junit.AfterClass;
import org.junit.Test;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

public class CustomKeyAndCertificateFactorySupplierTest {

    @Test
    public void setSupplier_shouldUseSupplier() {
        KeyAndCertificateFactory factoryInstance = new BCKeyAndCertificateFactory(null);
        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier((logger, isServer) -> factoryInstance);

        assertTrue("Should give exact instance",
            KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory(null)
                == factoryInstance);
    }

    @AfterClass
    public static void resetSupplier() {
        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(null);
    }
}

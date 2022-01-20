package org.mockserver.socket.tls;

import org.junit.AfterClass;
import org.junit.Test;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

import static junit.framework.TestCase.assertTrue;

public class CustomKeyAndCertificateFactorySupplierTest {

    @Test
    public void setSupplier_shouldUseSupplier() {
        KeyAndCertificateFactory factoryInstance = new BCKeyAndCertificateFactory(null);
        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(logger -> factoryInstance);

        assertTrue("Should give exact instance",
            KeyAndCertificateFactoryFactory.createKeyAndCertificateFactory(null)
                == factoryInstance);
    }

    @AfterClass
    public static void resetSupplier() {
        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(null);
    }
}

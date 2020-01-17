package org.mockserver.socket.tls;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * @author jamesdbloom
 */
public interface KeyAndCertificateFactory {

    String SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption";
    String KEY_GENERATION_ALGORITHM = "RSA";
    String SIGNING_ALGORITHM = "SHA256withRSA";
    /**
     * Generates an 2048 bit RSA key pair using SHA1PRNG for the Certificate Authority.
     */
    int ROOT_KEYSIZE = 2048;
    /**
     * Generates an 2048 bit RSA key pair using SHA1PRNG for the server
     * certificates.
     */
    int MOCK_KEYSIZE = 2048;
    /**
     * Current time minus 1 year, just in case software clock goes back due to
     * time synchronization
     */
    Date NOT_BEFORE = new Date(System.currentTimeMillis() - 86400000L * 365);
    /**
     * The maximum possible value in X.509 specification: 9999-12-31 23:59:59,
     * new Date(253402300799000L), but Apple iOS 8 fails with a certificate
     * expiration date grater than Mon, 24 Jan 6084 02:07:59 GMT (issue #6).
     * <p>
     * Hundred years in the future from starting the proxy should be enough.
     */
    Date NOT_AFTER = new Date(System.currentTimeMillis() + 86400000L * 365 * 100);

    void buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();

    void buildAndSavePrivateKeyAndX509Certificate();

    boolean certificateCreated();

    PrivateKey privateKey();

    X509Certificate x509Certificate();

    X509Certificate certificateAuthorityX509Certificate();

    static void main(String[] args) {
        new BCKeyAndCertificateFactory(new MockServerLogger()).buildAndSaveCertificateAuthorityPrivateKeyAndX509Certificate();
    }

}

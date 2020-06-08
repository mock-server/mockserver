package org.mockserver.socket.tls.jdk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.DAYS;

/**
 * @author jamesdbloom
 */
public class CertificateSigningRequest {

    // defaults
    public static final String DEFAULT_SIGNING_ALGORITHM = "SHA256withRSA";
    public static final String DEFAULT_KEY_GENERATION_ALGORITHM = "RSA";
    public static final int DEFAULT_KEY_PAIR_LENGTH = 2048;
    public static final int DEFAULT_VALIDITY = 90;
    /**
     * Generates an 2048 bit RSA key pair using SHA1PRNG for the Certificate Authority.
     */
    public static final int ROOT_KEY_SIZE = 2048;
    /**
     * Generates an 2048 bit RSA key pair using SHA1PRNG for the server certificates.
     */
    public static final int MOCK_KEY_SIZE = 2048;
    /**
     * Current time minus 1 year, just in case software clock goes back due to
     * time synchronization
     */
    public static final Date NOT_BEFORE = new Date(System.currentTimeMillis() - 86400000L * 5);
    /**
     * The maximum possible value in X.509 specification: 9999-12-31 23:59:59,
     * new Date(253402300799000L), but Apple iOS 8 fails with a certificate
     * expiration date grater than Mon, 24 Jan 6084 02:07:59 GMT (issue #6).
     * <p>
     * Hundred years in the future from starting the proxy should be enough.
     */
    public static final Date NOT_AFTER = new Date(System.currentTimeMillis() + 86400000L * 365);
    // distinguishing name
    public static final String ROOT_COMMON_NAME = "www.mockserver.com";
    public static final String ORGANISATION = "MockServer";
    public static final String LOCALITY = "London";
    public static final String STATE = "England";
    public static final String COUNTRY = "UK";
    public static final String CERTIFICATE_DOMAIN = "localhost";

    private String commonName;
    private int keyPairSize;
    private List<String> subjectAlternativeNames;
    private String signingAlgorithm = DEFAULT_SIGNING_ALGORITHM;
    private String keyPairAlgorithm = DEFAULT_KEY_GENERATION_ALGORITHM;
    private Long validityInMillis = DAYS.toMillis(DEFAULT_VALIDITY);

    public static String buildDistinguishedName(String commonName) {
        return format("C=%s, ST=%s, L=%s, O=%s, CN=%s", COUNTRY, STATE, LOCALITY, ORGANISATION, commonName);
    }

    public String getCommonName() {
        return commonName;
    }

    public CertificateSigningRequest setCommonName(String commonName) {
        this.commonName = commonName;
        return this;
    }

    public int getKeyPairSize() {
        return keyPairSize;
    }

    public CertificateSigningRequest setKeyPairSize(int keyPairSize) {
        this.keyPairSize = keyPairSize;
        return this;
    }

    public List<String> getSubjectAlternativeNames() {
        return subjectAlternativeNames;
    }

    public CertificateSigningRequest addSubjectAlternativeNames(String... subjectAlternativeNames) {
        if (this.subjectAlternativeNames == null) {
            this.subjectAlternativeNames = new ArrayList<>();
        }
        this.subjectAlternativeNames.addAll(Arrays.asList(subjectAlternativeNames));
        return this;
    }

    public String getSigningAlgorithm() {
        return signingAlgorithm;
    }

    public CertificateSigningRequest setSigningAlgorithm(String signingAlgorithm) {
        this.signingAlgorithm = signingAlgorithm;
        return this;
    }

    public String getKeyPairAlgorithm() {
        return keyPairAlgorithm;
    }

    public CertificateSigningRequest setKeyPairAlgorithm(String keyPairAlgorithm) {
        this.keyPairAlgorithm = keyPairAlgorithm;
        return this;
    }

    public Long getValidityInMillis() {
        return validityInMillis;
    }

    public CertificateSigningRequest setValidityInMillis(Long validityInMillis) {
        this.validityInMillis = validityInMillis;
        return this;
    }
}

package org.mockserver.socket.tls.jdk;

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
    // distinguishing name
    public static final String ROOT_COMMON_NAME = "www.mockserver.com";
    public static final String ORGANISATION = "MockServer";
    public static final String LOCALITY = "London";
    public static final String STATE = "England";
    public static final String COUNTRY = "UK";

    private String commonName;
    private int keyPairSize;
    private String[] subjectAlternativeNames;
    private String signingAlgorithm = DEFAULT_SIGNING_ALGORITHM;
    private String keyPairAlgorithm = DEFAULT_KEY_GENERATION_ALGORITHM;
    private Long validityInMillis = DAYS.toMillis(DEFAULT_VALIDITY);

    static String buildDistinguishedName(String commonName) {
        return format("CN=%s, O=%s, L=%s, ST=%s, C=%s", commonName, ORGANISATION, LOCALITY, STATE, COUNTRY);
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

    public String[] getSubjectAlternativeNames() {
        return subjectAlternativeNames;
    }

    public CertificateSigningRequest setSubjectAlternativeNames(String[] subjectAlternativeNames) {
        this.subjectAlternativeNames = subjectAlternativeNames;
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

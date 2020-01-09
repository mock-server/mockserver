package org.mockserver.socket.tls.jdk;

import java.util.List;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.DAYS;

public class CertificateSigningRequest {

    public static final String ROOT_COMMON_NAME = "www.mockserver.com";
    public static final String ORGANISATION = "MockServer";
    public static final String LOCALITY = "London";
    public static final String STATE = "England";
    public static final String COUNTRY = "UK";
    public static final String SHA_256_WITH_RSA = "SHA256withRSA";
    public static final String KEY_PAIR_ALGORITHM = "RSA";
    public static final int DEFAULT_KEY_PAIR_LENGTH = 2048;
    public static final int VALIDITY = 90;

    private String commonName;
    private int keyPairSize;
    private List<String> subjectAlternativeNames;
    private String signingAlgorithm = SHA_256_WITH_RSA;
    private String keyPairAlgorithm = KEY_PAIR_ALGORITHM;
    private Long validity = DAYS.toMillis(VALIDITY);

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

    public List<String> getSubjectAlternativeNames() {
        return subjectAlternativeNames;
    }

    public CertificateSigningRequest setSubjectAlternativeNames(List<String> subjectAlternativeNames) {
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

    public Long getValidity() {
        return validity;
    }

    public CertificateSigningRequest setValidity(Long validity) {
        this.validity = validity;
        return this;
    }
}

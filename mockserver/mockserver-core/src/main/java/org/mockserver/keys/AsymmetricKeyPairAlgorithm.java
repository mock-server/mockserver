package org.mockserver.keys;

public enum AsymmetricKeyPairAlgorithm {

    EC256_SHA256("EC", "ES256", "SHA256WITHECDSA", "secp256r1"),
    EC384_SHA384("EC", "ES384", "SHA384WITHECDSA", "secp384r1"),
    ECP512_SHA512("EC", "ES512", "SHA512WITHECDSA", "secp521r1"),
    RSA2048_SHA256("RSA", "RS256", "SHA256WithRSA", 2048),
    RSA3072_SHA384("RSA", "RS384", "SHA384WITHRSA", 3072),
    RSA4096_SHA512("RSA", "RS512", "SHA512WITHRSA", 4096);

    private final String algorithm;
    private String ecDomainParameters;
    private int keyLength;
    private final String jwtAlgorithm;
    private final String signingAlgorithm;

    AsymmetricKeyPairAlgorithm(String algorithm, String jwtAlgorithm, String signingAlgorithm, String ecDomainParameters) {
        this(algorithm, jwtAlgorithm, signingAlgorithm);
        this.ecDomainParameters = ecDomainParameters;
    }

    AsymmetricKeyPairAlgorithm(String algorithm, String jwtAlgorithm, String signingAlgorithm, int keyLength) {
        this(algorithm, jwtAlgorithm, signingAlgorithm);
        this.keyLength = keyLength;
    }

    AsymmetricKeyPairAlgorithm(String algorithm, String jwtAlgorithm, String signingAlgorithm) {
        this.algorithm = algorithm;
        this.jwtAlgorithm = jwtAlgorithm;
        this.signingAlgorithm = signingAlgorithm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getJwtAlgorithm() {
        return jwtAlgorithm;
    }

    public String getSigningAlgorithm() {
        return signingAlgorithm;
    }

    public String getECDomainParameters() {
        return ecDomainParameters;
    }

    public int getKeyLength() {
        return keyLength;
    }
}

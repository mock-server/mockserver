package org.mockserver.model;

import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;
import java.util.Objects;

public class X509Certificate extends ObjectWithJsonToString {

    private Certificate certificate;
    private byte[] certificateBytes;
    private String issuerDistinguishedName;
    private String subjectDistinguishedName;
    private String serialNumber;
    private String signatureAlgorithmName;
    private int hashCode;

    public static X509Certificate x509Certificate() {
        return new X509Certificate();
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public X509Certificate withCertificate(Certificate certificate) {
        try {
            this.certificate = certificate;
            this.certificateBytes = certificate.getEncoded();
        } catch (CertificateEncodingException cee) {
            throw new RuntimeException(cee.getMessage(), cee);
        }
        this.hashCode = 0;
        return this;
    }

    public String getIssuerDistinguishedName() {
        return issuerDistinguishedName;
    }

    public X509Certificate withIssuerDistinguishedName(String issuerDistinguishedName) {
        this.issuerDistinguishedName = issuerDistinguishedName;
        this.hashCode = 0;
        return this;
    }

    public String getSubjectDistinguishedName() {
        return subjectDistinguishedName;
    }

    public X509Certificate withSubjectDistinguishedName(String subjectDistinguishedName) {
        this.subjectDistinguishedName = subjectDistinguishedName;
        this.hashCode = 0;
        return this;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public X509Certificate withSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
        this.hashCode = 0;
        return this;
    }

    public String getSignatureAlgorithmName() {
        return signatureAlgorithmName;
    }

    public X509Certificate withSignatureAlgorithmName(String signatureAlgorithmName) {
        this.signatureAlgorithmName = signatureAlgorithmName;
        this.hashCode = 0;
        return this;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public X509Certificate clone() {
        return x509Certificate()
            .withCertificate(certificate)
            .withIssuerDistinguishedName(issuerDistinguishedName)
            .withSubjectDistinguishedName(subjectDistinguishedName)
            .withSerialNumber(serialNumber)
            .withSignatureAlgorithmName(signatureAlgorithmName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        X509Certificate that = (X509Certificate) o;
        return Arrays.equals(certificateBytes, that.certificateBytes) &&
            Objects.equals(issuerDistinguishedName, that.issuerDistinguishedName) &&
            Objects.equals(subjectDistinguishedName, that.subjectDistinguishedName) &&
            Objects.equals(serialNumber, that.serialNumber) &&
            Objects.equals(signatureAlgorithmName, that.signatureAlgorithmName);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(Arrays.hashCode(certificateBytes), issuerDistinguishedName, subjectDistinguishedName, serialNumber, signatureAlgorithmName, hashCode);
        }
        return hashCode;
    }
}

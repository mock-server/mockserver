package org.mockserver.test;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates certificate authorities and client certificates for mTLS tests at test time, so no fixture can expire.
 * Callers need BouncyCastle on their classpath (mockserver-core brings it).
 */
public final class TestCertificates {

    private static final AtomicLong SERIAL = new AtomicLong(System.currentTimeMillis());
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final int CLIENT_KEY_USAGE = KeyUsage.digitalSignature;
    private static final int CA_KEY_USAGE = KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature;

    private TestCertificates() {
    }

    public static final class Issued {
        public final X509Certificate certificate;
        public final KeyPair keyPair;

        private Issued(X509Certificate certificate, KeyPair keyPair) {
            this.certificate = certificate;
            this.keyPair = keyPair;
        }

        private X500Name subject() {
            return X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
        }
    }

    /**
     * An existing certificate authority, such as MockServer's bundled CA, to issue certificates from.
     */
    public static Issued issuer(X509Certificate certificate, PrivateKey privateKey) {
        return new Issued(certificate, new KeyPair(certificate.getPublicKey(), privateKey));
    }

    public static Issued certificateAuthority(String commonName) throws Exception {
        KeyPair keyPair = keyPair();
        X500Name subject = name(commonName);
        return new Issued(build(subject, keyPair, subject, keyPair, true, daysFromNow(-1), daysFromNow(30), CA_KEY_USAGE, null), keyPair);
    }

    /**
     * A self-signed certificate authority for an existing key pair, such as one re-issued for a known CA key.
     */
    public static Issued certificateAuthority(String commonName, KeyPair keyPair) throws Exception {
        X500Name subject = name(commonName);
        return new Issued(build(subject, keyPair, subject, keyPair, true, daysFromNow(-1), daysFromNow(30), CA_KEY_USAGE, null), keyPair);
    }

    public static Issued intermediate(String commonName, Issued issuer, Date notBefore, Date notAfter) throws Exception {
        KeyPair keyPair = keyPair();
        return new Issued(build(name(commonName), keyPair, issuer.subject(), issuer.keyPair, true, notBefore, notAfter, CA_KEY_USAGE, null), keyPair);
    }

    public static Issued clientCertificate(String commonName, Issued issuer) throws Exception {
        return clientCertificate(commonName, issuer, daysFromNow(-1), daysFromNow(30), KeyPurposeId.id_kp_clientAuth);
    }

    public static Issued clientCertificate(String commonName, Issued issuer, Date notBefore, Date notAfter, KeyPurposeId... extendedKeyUsage) throws Exception {
        KeyPair keyPair = keyPair();
        return new Issued(build(name(commonName), keyPair, issuer.subject(), issuer.keyPair, false, notBefore, notAfter, CLIENT_KEY_USAGE, extendedKeyUsage), keyPair);
    }

    /**
     * @param keyUsage a {@link KeyUsage} bit mask
     */
    public static Issued clientCertificateWithKeyUsage(String commonName, Issued issuer, int keyUsage) throws Exception {
        KeyPair keyPair = keyPair();
        return new Issued(build(name(commonName), keyPair, issuer.subject(), issuer.keyPair, false, daysFromNow(-1), daysFromNow(30), keyUsage, new KeyPurposeId[]{KeyPurposeId.id_kp_clientAuth}), keyPair);
    }

    public static Issued selfSignedClientCertificate(String commonName) throws Exception {
        KeyPair keyPair = keyPair();
        X500Name subject = name(commonName);
        return new Issued(build(subject, keyPair, subject, keyPair, false, daysFromNow(-1), daysFromNow(30), CLIENT_KEY_USAGE, new KeyPurposeId[]{KeyPurposeId.id_kp_clientAuth}), keyPair);
    }

    public static Date daysFromNow(int days) {
        return new Date(System.currentTimeMillis() + days * DAY);
    }

    public static String toPem(X509Certificate certificate) throws Exception {
        return "-----BEGIN CERTIFICATE-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(certificate.getEncoded())
            + "\n-----END CERTIFICATE-----\n";
    }

    private static X500Name name(String commonName) {
        return new X500Name("CN=" + commonName);
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static X509Certificate build(X500Name subject, KeyPair subjectKeyPair, X500Name issuer, KeyPair issuerKeyPair, boolean certificateAuthority, Date notBefore, Date notAfter, int keyUsage, KeyPurposeId[] extendedKeyUsage) throws Exception {
        JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(SERIAL.incrementAndGet()), notBefore, notAfter, subject, subjectKeyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(certificateAuthority));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsage));
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(subjectKeyPair.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(issuerKeyPair.getPublic()));
        if (extendedKeyUsage != null && extendedKeyUsage.length > 0) {
            builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(extendedKeyUsage));
        }
        String signatureAlgorithm = "EC".equals(issuerKeyPair.getPrivate().getAlgorithm()) ? "SHA256withECDSA" : "SHA256withRSA";
        return new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder(signatureAlgorithm).build(issuerKeyPair.getPrivate())));
    }
}

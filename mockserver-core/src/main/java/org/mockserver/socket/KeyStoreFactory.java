package org.mockserver.socket;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * @author jamesdbloom
 * @author ganskef
 */
public class KeyStoreFactory {

    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final String SIGNATURE_ALGORITHM = "SHA1WithRSAEncryption";

    private static final String KEYGEN_ALGORITHM = "RSA";

    /**
     * Generates an 2048 bit RSA key pair using SHA1PRNG for the Certificate
     * Authority.
     */
    private static final int ROOT_KEYSIZE = 2048;

    /**
     * Generates an 1024 bit RSA key pair using SHA1PRNG for the server
     * certificates. Thoughts: 2048 takes much longer time on older CPUs. And
     * for almost every client, 1024 is sufficient.
     */
    private static final int FAKE_KEYSIZE = 1024;

    /**
     * Current time minus 1 year, just in case software clock goes back due to
     * time synchronization
     */
    private static final Date NOT_BEFORE = new Date(System.currentTimeMillis() - 86400000L * 365);

    /** The maximum possible value in X.509 specification: 9999-12-31 23:59:59 */
    private static final Date NOT_AFTER = new Date(253402300799000L);

    /**
     * Create a random 2048 bit RSA key pair
     */
    public static KeyPair generateRSAKeyPair() throws Exception {
        return generateKeyPair(ROOT_KEYSIZE);
    }

    /**
     * Create a random 2048 bit RSA key pair with the given length
     */
    public static KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEYGEN_ALGORITHM, PROVIDER_NAME);
        generator.initialize(keySize, new SecureRandom());
        return generator.generateKeyPair();
    }

    /**
     * Create a certificate to use by a Certificate Authority, signed by a self
     * signed certificate.
     */
    public X509Certificate createCACert(PublicKey publicKey, PrivateKey privateKey) throws Exception {

        String issuer = "CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK";

        X500Name issuerName = new X500Name(issuer);
        BigInteger serial = BigInteger.valueOf(new Random().nextInt());
        X500Name subjectName = issuerName;
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName, serial, NOT_BEFORE, NOT_AFTER, subjectName, publicKey);

        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(publicKey));
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        KeyUsage usage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature | KeyUsage.keyEncipherment | KeyUsage.dataEncipherment | KeyUsage.cRLSign);
        builder.addExtension(Extension.keyUsage, false, usage);

        ASN1EncodableVector purposes = new ASN1EncodableVector();
        purposes.add(KeyPurposeId.id_kp_serverAuth);
        purposes.add(KeyPurposeId.id_kp_clientAuth);
        purposes.add(KeyPurposeId.anyExtendedKeyUsage);
        builder.addExtension(Extension.extendedKeyUsage, false, new DERSequence(purposes));

        X509Certificate cert = signCertificate(builder, privateKey);

        cert.checkValidity(new Date());

        cert.verify(publicKey);

        return cert;
    }

    /**
     * Create a server certificate for the given domain and subject alternative
     * names, signed by the given Certificate Authority.
     */
    public X509Certificate createClientCert(PublicKey publicKey, PrivateKey certificateAuthorityPrivateKey, PublicKey certificateAuthorityPublicKey, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

        // TODO(ganskef) the issuer should be taken from the CA certificate
        // X500Name issuer = new
        // X509CertificateHolder(cert.getEncoded()).getSubject();

        X500Name issuer = new X500Name("CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK");

        X500Name subject = new X500Name("CN=" + domain + ", O=MockServer, L=London, ST=England, C=UK");

        BigInteger serial = BigInteger.valueOf(new Random().nextInt());

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial, NOT_BEFORE, NOT_AFTER, subject, publicKey);

        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(publicKey));
        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

        List<ASN1Encodable> subjectAlternativeNames = new ArrayList<ASN1Encodable>();
        if (subjectAlternativeNameDomains != null) {
            for (String subjectAlternativeName : subjectAlternativeNameDomains) {
                subjectAlternativeNames.add(new GeneralName(GeneralName.dNSName, subjectAlternativeName));
            }
        }
        if (subjectAlternativeNameIps != null) {
            for (String subjectAlternativeName : subjectAlternativeNameIps) {
                subjectAlternativeNames.add(new GeneralName(GeneralName.iPAddress, subjectAlternativeName));
            }
        }
        if (subjectAlternativeNames.size() > 0) {
            DERSequence subjectAlternativeNamesExtension = new DERSequence(subjectAlternativeNames.toArray(new ASN1Encodable[subjectAlternativeNames.size()]));
            builder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension);
        }

        X509Certificate cert = signCertificate(builder, certificateAuthorityPrivateKey);

        cert.checkValidity(new Date());
        cert.verify(certificateAuthorityPublicKey);

        return cert;
    }

    /**
     * Create a KeyStore with a server certificate for the given domain and
     * subject alternative names.
     * 
     * TODO(ganskef) This method creates a new Certificate Authority every time.
     * It should be possible to persist it separately. Also it must be given as
     * a parameter.
     * 
     * TODO(ganskef) It should be possible to export the Certificate Authority
     * into a PEM (and P12 file for Windows)
     */
    KeyStore generateCertificate(String certificationAlias, String certificateAuthorityAlias, char[] keyStorePassword, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

        KeyPair keyPair = generateKeyPair(FAKE_KEYSIZE);
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        KeyPair caKeyPair = generateKeyPair(ROOT_KEYSIZE);
        PrivateKey caPrivateKey = caKeyPair.getPrivate();
        PublicKey caPublicKey = caKeyPair.getPublic();

        X509Certificate caCert = createCACert(caPublicKey, caPrivateKey);
        X509Certificate clientCert = createClientCert(publicKey, caPrivateKey, caPublicKey, domain, subjectAlternativeNameDomains, subjectAlternativeNameIps);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, keyStorePassword);

        keyStore.setKeyEntry(certificationAlias, privateKey, keyStorePassword, new X509Certificate[] { clientCert, caCert });

        return keyStore;
    }

    private static SubjectKeyIdentifier createSubjectKeyIdentifier(Key key) throws IOException {
        ASN1InputStream is = null;
        try {
            is = new ASN1InputStream(new ByteArrayInputStream(key.getEncoded()));
            ASN1Sequence seq = (ASN1Sequence) is.readObject();
            SubjectPublicKeyInfo info = new SubjectPublicKeyInfo(seq);
            return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private static X509Certificate signCertificate(X509v3CertificateBuilder certificateBuilder, PrivateKey signedWithPrivateKey) throws OperatorCreationException, CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(PROVIDER_NAME).build(signedWithPrivateKey);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider(PROVIDER_NAME).getCertificate(certificateBuilder.build(signer));
        return cert;
    }

}

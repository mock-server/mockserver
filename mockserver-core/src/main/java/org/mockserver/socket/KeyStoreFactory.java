package org.mockserver.socket;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * @author jamesdbloom
 */
public class KeyStoreFactory {

    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;
    private static final String SIGNATURE_ALGORITHM = "SHA1WithRSAEncryption";
    private static final String KEYGEN_ALGORITHM = "RSA";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Create a random 2048 bit RSA key pair
     */
    public static KeyPair generateRSAKeyPair() throws Exception {
        return generateRSAKeyPair(2048);
    }

    public static KeyPair generateRSAKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEYGEN_ALGORITHM, PROVIDER_NAME);
        generator.initialize(keySize, new SecureRandom());
        return generator.generateKeyPair();
    }

    /**
     * we generate the AC issuer's certificate
     */
    public X509Certificate createCACert(PublicKey publicKey, PrivateKey privateKey) throws Exception {

        String issuer = "CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK";

        X500Name issuerName = new X500Name(issuer);
        BigInteger serial = BigInteger.valueOf(new Random().nextInt());
        Date notBefore = new Date(System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 30));
        Date notAfter = new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30));
        X500Name subjectName = issuerName;
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName, serial, notBefore, notAfter, subjectName, publicKey);

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(PROVIDER_NAME).build(privateKey);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider(PROVIDER_NAME).getCertificate(builder.build(signer));

        cert.checkValidity(new Date());

        cert.verify(publicKey);

        return cert;
    }

    /**
     * we generate a certificate signed by our CA's intermediate certficate
     */
    public X509Certificate createClientCert(PublicKey publicKey, PrivateKey certificateAuthorityPrivateKey, PublicKey certificateAuthorityPublicKey, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

        X500Name issuer = new X500Name("CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK");

        X500Name subject = new X500Name("CN=" + domain + ", O=MockServer, L=London, ST=England, C=UK");

        X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(new Random().nextInt()), new Date(System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 30)), new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)), subject, publicKey);

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
            certGen.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension);
        }

        ContentSigner sigGen = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(PROVIDER_NAME).build(certificateAuthorityPrivateKey);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider(PROVIDER_NAME).getCertificate(certGen.build(sigGen));

        cert.checkValidity(new Date());

        cert.verify(certificateAuthorityPublicKey);

        return cert;
    }

    /**
     * Create KeyStore and add a self-signed X.509 Certificate
     */
    KeyStore generateCertificate(String certificationAlias, String certificateAuthorityAlias, char[] keyStorePassword, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

        Security.addProvider(new BouncyCastleProvider());

        KeyPair keyPair = generateRSAKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        KeyPair caKeyPair = generateRSAKeyPair();
        PrivateKey caPrivateKey = caKeyPair.getPrivate();
        PublicKey caPublicKey = caKeyPair.getPublic();

        X509Certificate caCert = createCACert(caPublicKey, caPrivateKey);
        X509Certificate clientCert = createClientCert(publicKey, caPrivateKey, caPublicKey, domain, subjectAlternativeNameDomains, subjectAlternativeNameIps);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, keyStorePassword);

        keyStore.setKeyEntry(certificationAlias, privateKey, keyStorePassword, new X509Certificate[] { clientCert, caCert });

        return keyStore;
    }
}

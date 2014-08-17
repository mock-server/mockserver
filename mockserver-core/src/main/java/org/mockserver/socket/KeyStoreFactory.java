package org.mockserver.socket;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("deprecation")
public class KeyStoreFactory {

    /**
     * Create a random 2048 bit RSA key pair
     */
    public static KeyPair generateRSAKeyPair() throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        kpGen.initialize(2048, new SecureRandom());
        return kpGen.generateKeyPair();
    }

    /**
     * we generate the AC issuer's certificate
     */
    public X509Certificate createCACert(PublicKey publicKey, PrivateKey privateKey) throws Exception {
        //
        // signers name
        //
        String issuer = "CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK";

        //
        // subjects name - the same as we are self signed.
        //
        String subject = "CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK";

        //
        // create the certificate - version 3
        //
        X509V3CertificateGenerator x509V1CertificateGenerator = new X509V3CertificateGenerator();
        x509V1CertificateGenerator.addExtension(X509Extensions.BasicConstraints, false, new BasicConstraints(true));
        x509V1CertificateGenerator.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        x509V1CertificateGenerator.setIssuerDN(new X509Principal(issuer));
        x509V1CertificateGenerator.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30));
        x509V1CertificateGenerator.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)));
        x509V1CertificateGenerator.setSubjectDN(new X509Principal(subject));
        x509V1CertificateGenerator.setPublicKey(publicKey);
        x509V1CertificateGenerator.setSignatureAlgorithm("SHA1WithRSAEncryption");

        X509Certificate cert = x509V1CertificateGenerator.generate(privateKey);

        cert.checkValidity(new Date());

        cert.verify(publicKey);

        return cert;
    }

    /**
     * we generate a certificate signed by our CA's intermediate certficate
     */
    public X509Certificate createClientCert(PublicKey publicKey, PrivateKey certificateAuthorityPrivateKey, PublicKey certificateAuthorityPublicKey, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {
        //
        // issuer
        //
        String issuer = "CN=www.mockserver.com, O=MockServer, L=London, ST=England, C=UK";

        //
        // create the certificate - version 3
        //
        X509V3CertificateGenerator x509V3CertificateGenerator = new X509V3CertificateGenerator();
        x509V3CertificateGenerator.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        x509V3CertificateGenerator.setIssuerDN(new X509Principal(issuer));
        x509V3CertificateGenerator.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30));
        x509V3CertificateGenerator.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)));
        x509V3CertificateGenerator.setSubjectDN(new X509Principal("CN=" + domain + ", O=MockServer, L=London, ST=England, C=UK"));
        x509V3CertificateGenerator.setPublicKey(publicKey);
        x509V3CertificateGenerator.setSignatureAlgorithm("SHA1WithRSAEncryption");

        //
        // add the extensions
        //
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
            x509V3CertificateGenerator.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension);
        }

        X509Certificate cert = x509V3CertificateGenerator.generate(certificateAuthorityPrivateKey);

        cert.checkValidity(new Date());

        cert.verify(certificateAuthorityPublicKey);

        return cert;
    }

    /**
     * Create KeyStore and add a self-signed X.509 Certificate
     */
    KeyStore generateCertificate(String certificationAlias, String certificateAuthorityAlias, char[] keyStorePassword, String domain, String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {

        Security.addProvider(new BouncyCastleProvider());

        //
        // personal keys
        //
        KeyPair keyPair = generateRSAKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        //
        // ca keys
        //
        KeyPair certificateAuthorityKeyPair = generateRSAKeyPair();
        PrivateKey certificateAuthorityPrivateKey = certificateAuthorityKeyPair.getPrivate();
        PublicKey certificateAuthorityPublicKey = certificateAuthorityKeyPair.getPublic();

        //
        // generate certificates
        //
        X509Certificate caCert = createCACert(certificateAuthorityPublicKey, certificateAuthorityPrivateKey);
        X509Certificate clientCert = createClientCert(publicKey, certificateAuthorityPrivateKey, certificateAuthorityPublicKey, domain, subjectAlternativeNameDomains, subjectAlternativeNameIps);

        // create new key store
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, keyStorePassword);

        // add certification
        keyStore.setKeyEntry(certificationAlias, privateKey, keyStorePassword, new X509Certificate[]{clientCert, caCert});

        return keyStore;
    }
}

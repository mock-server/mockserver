package org.mockserver.socket.tls.jdk;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;
import sun.security.util.DerValue;
import sun.security.x509.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.socket.tls.PEMToFile.*;
import static org.mockserver.socket.tls.jdk.CertificateSigningRequest.*;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"RedundantSuppression", "deprecation", "removal"})
public class X509Generator {

    private final MockServerLogger mockServerLogger;

    public X509Generator(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;
    }

    public X509AndPrivateKey generateRootX509AndPrivateKey(final CertificateSigningRequest csr) throws IOException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException {
        final KeyPair keyPair = generateKeyPair(csr.getKeyPairAlgorithm(), csr.getKeyPairSize());
        final X500Name subjectAndIssuer = new X500Name(buildDistinguishedName(csr.getCommonName()));
        X509CertInfo x509CertInfo = buildX509CertInfo(subjectAndIssuer, subjectAndIssuer, keyPair.getPublic(), csr);
        updateWithRootCertificateExtensions(x509CertInfo, keyPair.getPublic());
        return signX509KeyPair(keyPair.getPrivate(), keyPair, x509CertInfo, csr.getSigningAlgorithm());
    }

    public X509AndPrivateKey generateLeafX509AndPrivateKey(final CertificateSigningRequest csr, String issuerDistinguishingName, final String caPrivateKey, final X509Certificate caCertificate) throws IOException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, NoSuchProviderException, SignatureException, InvalidKeySpecException {
        final PrivateKey privateKey = KeyFactory
            .getInstance(csr.getKeyPairAlgorithm())
            .generatePrivate(keySpecFromPEM(caPrivateKey));
        final KeyPair keyPair = generateKeyPair(csr.getKeyPairAlgorithm(), csr.getKeyPairSize());
        final X500Name subject = new X500Name(buildDistinguishedName(csr.getCommonName()));
        final X500Name issuer = new X500Name(issuerDistinguishingName);
        X509CertInfo x509CertInfo = buildX509CertInfo(subject, issuer, keyPair.getPublic(), csr);
        updateWithCertificateExtensions(x509CertInfo, keyPair.getPublic(), caCertificate.getPublicKey(), csr.getSubjectAlternativeNames());
        X509AndPrivateKey x509AndPrivateKey = signX509KeyPair(privateKey, keyPair, x509CertInfo, csr.getSigningAlgorithm());

        // validate
        X509Certificate signedX509Certificate = x509FromPEM(x509AndPrivateKey.getCert());
        signedX509Certificate.checkValidity(new Date());
        signedX509Certificate.verify(caCertificate.getPublicKey());

        return x509AndPrivateKey;
    }

    private KeyPair generateKeyPair(final String algorithm, final int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator kpg;
        kpg = KeyPairGenerator.getInstance(algorithm);
        kpg.initialize(keySize);
        return kpg.genKeyPair();
    }

    private X509CertInfo buildX509CertInfo(final X500Name subject, final X500Name issuer, final PublicKey publicKey, final CertificateSigningRequest csr) throws IOException, NoSuchAlgorithmException, CertificateException {
        X509CertInfo x509CertInfo = new X509CertInfo();

        CertificateValidity interval = new CertificateValidity(
            NOT_BEFORE,
            NOT_AFTER
        );
        // replaced secure random with random in order to prevent entropy depletion
        BigInteger sn = new BigInteger(64, new Random());

        x509CertInfo.set(X509CertInfo.VALIDITY, interval);
        x509CertInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
        x509CertInfo.set(X509CertInfo.SUBJECT, subject);
        x509CertInfo.set(X509CertInfo.ISSUER, issuer);
        x509CertInfo.set(X509CertInfo.KEY, new CertificateX509Key(publicKey));
        x509CertInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));

        AlgorithmId algo = new AlgorithmId(AlgorithmId.get(csr.getSigningAlgorithm()).getOID());
        x509CertInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

        return x509CertInfo;
    }

    private void updateWithCertificateExtensions(final X509CertInfo x509CertInfo, final PublicKey publicKey, final PublicKey caPublicKey, final List<String> subjectAlternativeNames) throws IOException, CertificateException {
        CertificateExtensions certificateExtensions = new CertificateExtensions();

        GeneralNames generalNames = subjectAlternativeNames
            .stream()
            .filter(StringUtils::isNotBlank)
            .map(this::buildGeneralName)
            .filter(Objects::nonNull)
            .collect(
                Collector.of(
                    GeneralNames::new,
                    GeneralNames::add,
                    (generalNames1, generalNames2) -> null //do nothing
                )
            );

        if (!generalNames.isEmpty()) {
            certificateExtensions.set(SubjectAlternativeNameExtension.NAME, new SubjectAlternativeNameExtension(Boolean.FALSE, generalNames));
        }

        // See: https://tools.ietf.org/html/rfc5280#section-4.2.1.2
        certificateExtensions.set(SubjectKeyIdentifierExtension.NAME, new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));

        // See: https://tools.ietf.org/html/rfc5280#section-4.2.1.2
        certificateExtensions.set(AuthorityKeyIdentifierExtension.NAME, new AuthorityKeyIdentifierExtension(new KeyIdentifier(caPublicKey), null, null));

        // See: https://tools.ietf.org/html/rfc5280#section-4.2.1.1
        x509CertInfo.set(X509CertInfo.EXTENSIONS, certificateExtensions);
    }

    private void updateWithRootCertificateExtensions(final X509CertInfo x509CertInfo, final PublicKey publicKey) throws IOException, CertificateException {
        CertificateExtensions certificateExtensions = new CertificateExtensions();

        // See: https://tools.ietf.org/html/rfc5280#section-4.2.1.9
        certificateExtensions.set(
            BasicConstraintsExtension.NAME,
            new BasicConstraintsExtension(
                true, // is critical
                true, // is CA
                -1 // path length
            )
        );

        // See: https://tools.ietf.org/html/rfc5280#section-4.2.1.3
        boolean[] keyUsage = new boolean[9];
        keyUsage[5] = true; // keyCertSign

        certificateExtensions.set(KeyUsageExtension.NAME, new KeyUsageExtension(keyUsage));

        // See: https://tools.ietf.org/html/rfc5280#section-4.2.1.2
        certificateExtensions.set(SubjectKeyIdentifierExtension.NAME, new SubjectKeyIdentifierExtension(new KeyIdentifier(publicKey).getIdentifier()));

        x509CertInfo.set(X509CertInfo.EXTENSIONS, certificateExtensions);
    }

    @SuppressWarnings("UnstableApiUsage")
    private GeneralName buildGeneralName(final String subjectAlternativeName) {
        if (InetAddresses.isUriInetAddress(subjectAlternativeName)) {
            try {
                return new GeneralName(new IPAddressName(subjectAlternativeName));
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("unable to use ip address with the value \"" + subjectAlternativeName + "\" as Subject Alternative Name (SAN) for X509 as JDK does not support SANs with that format")
                        .setThrowable(throwable)
                );
            }
        } else if (InternetDomainName.isValid(subjectAlternativeName)) {
            try {
                return new GeneralName(new DNSName(subjectAlternativeName));
            } catch (Throwable ignore) {
                try {
                    return new GeneralName(new DNSName(new DerValue(DerValue.tag_IA5String, subjectAlternativeName)));
                } catch (Throwable throwable) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.WARN)
                            .setMessageFormat("unable to use domain name with the value \"" + subjectAlternativeName + "\" as Subject Alternative Name (SAN) for X509 as JDK does not support SANs with that format")
                            .setThrowable(throwable)
                    );
                }
            }
        } else if (subjectAlternativeName.contains("*")) {
            try {
                return new GeneralName(new DNSName(new DerValue(DerValue.tag_IA5String, subjectAlternativeName)));
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("unable to use domain name with the value \"" + subjectAlternativeName + "\" as Subject Alternative Name (SAN) for X509 as JDK does not support SANs with that format")
                        .setThrowable(throwable)
                );
            }
        }
        return null;
    }

    private X509AndPrivateKey signX509KeyPair(final PrivateKey privateKey, final KeyPair keyPair, X509CertInfo x509CertInfo, final String signatureAlgorithm) throws CertificateException, NoSuchAlgorithmException, IOException, InvalidKeyException, NoSuchProviderException, SignatureException {
        x509CertInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(AlgorithmId.get(signatureAlgorithm)));
        X509CertImpl cert = new X509CertImpl(x509CertInfo);
        cert.sign(privateKey, signatureAlgorithm);
        return new X509AndPrivateKey()
            .setPrivateKey(privateKeyToPEM(keyPair.getPrivate().getEncoded()))
            .setCert(certToPEM(cert.getEncoded()));
    }

}


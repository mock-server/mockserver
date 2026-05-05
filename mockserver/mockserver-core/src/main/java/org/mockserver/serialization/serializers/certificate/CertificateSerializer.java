package org.mockserver.serialization.serializers.certificate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
@SuppressWarnings("SameParameterValue")
public class CertificateSerializer extends StdSerializer<Certificate> {

    public CertificateSerializer() {
        super(Certificate.class);
    }

    @Override
    public void serialize(Certificate certificate, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (certificate instanceof X509Certificate) {
            X509Certificate x509Certificate = (X509Certificate) certificate;
            jgen.writeObjectField("version", x509Certificate.getVersion());
            if (x509Certificate.getSerialNumber() != null) {
                jgen.writeObjectField("serialNumber", x509Certificate.getSerialNumber().toString());
            }
            if (x509Certificate.getIssuerX500Principal() != null) {
                String issuerDN = x509Certificate.getIssuerX500Principal().getName(X500Principal.RFC2253);
                if (isNotBlank(issuerDN)) {
                    jgen.writeObjectField("issuerDN", issuerDN);
                }
            }
            try {
                if (x509Certificate.getIssuerAlternativeNames() != null && !x509Certificate.getIssuerAlternativeNames().isEmpty()) {
                    writeCollectionOfLists("issuerAlternativeNames", x509Certificate.getIssuerAlternativeNames(), jgen);
                }
            } catch (CertificateParsingException ignore) {

            }
            if (x509Certificate.getSubjectX500Principal() != null) {
                String subjectDN = x509Certificate.getSubjectX500Principal().getName(X500Principal.RFC2253);
                if (isNotBlank(subjectDN)) {
                    jgen.writeObjectField("subjectDN", subjectDN);
                }
            }
            try {
                if (x509Certificate.getSubjectAlternativeNames() != null && !x509Certificate.getSubjectAlternativeNames().isEmpty()) {
                    writeCollectionOfLists("subjectAlternativeNames", x509Certificate.getSubjectAlternativeNames(), jgen);
                }
            } catch (CertificateParsingException ignore) {

            }
            if (isNotBlank(x509Certificate.getSigAlgOID())) {
                jgen.writeObjectField("sigAlgOID", x509Certificate.getSigAlgOID());
            }
            if (isNotBlank(x509Certificate.getSigAlgName())) {
                jgen.writeObjectField("sigAlgName", x509Certificate.getSigAlgName());
            }
            if (isNotBlank(x509Certificate.getSigAlgName())) {
                jgen.writeObjectField("sigAlgName", x509Certificate.getSigAlgName());
            }
            try {
                if (x509Certificate.getExtendedKeyUsage() != null && !x509Certificate.getExtendedKeyUsage().isEmpty()) {
                    writeCollection("extendedKeyUsage", x509Certificate.getExtendedKeyUsage(), jgen);
                }
            } catch (CertificateParsingException ignore) {

            }
            if (x509Certificate.getNotBefore() != null) {
                jgen.writeObjectField("notBefore", x509Certificate.getNotBefore().toString());
            }
            if (x509Certificate.getNotAfter() != null) {
                jgen.writeObjectField("notAfter", x509Certificate.getNotAfter().toString());
            }
        }
        jgen.writeEndObject();
    }

    private void writeCollection(String name, Collection<?> collection, JsonGenerator jgen) throws IOException {
        jgen.writeArrayFieldStart(name);
        for (Object item : collection) {
            jgen.writeObject(item);
        }
        jgen.writeEndArray();
    }

    private void writeCollectionOfLists(String name, Collection<List<?>> collection, JsonGenerator jgen) throws IOException {
        jgen.writeArrayFieldStart(name);
        for (Object item : collection.stream().flatMap(objects -> objects.stream().filter(item -> item instanceof String)).collect(Collectors.toList())) {
            jgen.writeObject(item);
        }
        jgen.writeEndArray();
    }
}

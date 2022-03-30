package org.mockserver.serialization.serializers.certificate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.X509Certificate;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class X509CertificateSerializer extends StdSerializer<X509Certificate> {

    public X509CertificateSerializer() {
        super(X509Certificate.class);
    }

    @Override
    public void serialize(X509Certificate x509Certificate, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (isNotBlank(x509Certificate.getSerialNumber())) {
            jgen.writeObjectField("serialNumber", x509Certificate.getSerialNumber());
        }
        if (isNotBlank(x509Certificate.getIssuerDistinguishedName())) {
            jgen.writeObjectField("issuerDistinguishedName", x509Certificate.getIssuerDistinguishedName());
        }
        if (isNotBlank(x509Certificate.getSubjectDistinguishedName())) {
            jgen.writeObjectField("subjectDistinguishedName", x509Certificate.getSubjectDistinguishedName());
        }
        if (isNotBlank(x509Certificate.getSignatureAlgorithmName())) {
            jgen.writeObjectField("signatureAlgorithmName", x509Certificate.getSignatureAlgorithmName());
        }
        jgen.writeEndObject();
    }
}

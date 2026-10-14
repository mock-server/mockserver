package org.mockserver.serialization;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.DnsRecordClass;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.DnsRequestDefinition;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationSequence;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.Not.not;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;

/**
 * DNS and binary request definitions survive every serialiser a client uses to send a filter: a lost definition
 * becomes an empty clear body (clear everything) or a missing step in a verified sequence.
 */
public class NonHttpRequestDefinitionSerializationTest {

    private static final MockServerLogger LOGGER = new MockServerLogger(NonHttpRequestDefinitionSerializationTest.class);
    private static final DnsRequestDefinition DNS = not(dnsRequest("one.example.", DnsRecordType.AAAA).withDnsClass(DnsRecordClass.IN));
    private static final BinaryRequestDefinition BINARY = binaryRequest("payload".getBytes(StandardCharsets.UTF_8));

    private final RequestDefinitionSerializer requestDefinitionSerializer = new RequestDefinitionSerializer(LOGGER);

    @Test
    public void dnsDefinitionRoundTrips() {
        String json = requestDefinitionSerializer.serialize(DNS);

        assertThat(json, containsString("\"dnsName\" : \"one.example.\""));
        assertThat(requestDefinitionSerializer.deserialize(json), is(DNS));
    }

    @Test
    public void binaryDefinitionRoundTrips() {
        assertThat(requestDefinitionSerializer.deserialize(requestDefinitionSerializer.serialize(BINARY)), is(BINARY));
    }

    @Test
    public void definitionArrayKeepsDnsAndBinaryEntries() {
        RequestDefinition[] definitions = {request("/http"), DNS, BINARY};

        assertThat(requestDefinitionSerializer.deserializeArray(requestDefinitionSerializer.serialize(definitions)), is(definitions));
    }

    @Test
    public void unsupportedDefinitionFailsRatherThanSerialisingAsEmpty() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> requestDefinitionSerializer.serialize(new UnsupportedDefinition()));

        assertThat(failure.getMessage(), containsString("UnsupportedDefinition"));
    }

    @Test
    public void verificationKeepsDnsDefinition() {
        VerificationSerializer serializer = new VerificationSerializer(LOGGER);
        Verification verification = verification().withRequest(DNS).withTimes(atLeast(1));

        assertThat(serializer.deserialize(serializer.serialize(verification)), is(verification));
    }

    @Test
    public void verificationSequenceKeepsEveryStep() {
        VerificationSequenceSerializer serializer = new VerificationSequenceSerializer(LOGGER);
        VerificationSequence sequence = new VerificationSequence().withRequests(request("/first"), DNS, BINARY, request("/last"));

        assertThat(serializer.deserialize(serializer.serialize(sequence)), is(sequence));
    }

    private static class UnsupportedDefinition extends RequestDefinition {
        @Override
        public RequestDefinition shallowClone() {
            return this;
        }
    }
}

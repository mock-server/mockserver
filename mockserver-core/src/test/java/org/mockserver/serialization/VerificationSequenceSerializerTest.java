package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.StringBody;
import org.mockserver.serialization.model.VerificationSequenceDTO;
import org.mockserver.validator.jsonschema.JsonSchemaVerificationSequenceValidator;
import org.mockserver.verify.VerificationSequence;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.model.HttpRequest.request;

public class VerificationSequenceSerializerTest {

    private final HttpRequest requestOne =
        request()
            .withMethod("GET")
            .withPath("some_path_one")
            .withBody(new StringBody("some_body_one"))
            .withHeaders(new Header("header_name_two", "header_value_two"));
    private final HttpRequest requestTwo =
        request()
            .withMethod("GET")
            .withPath("some_path_two")
            .withBody(new StringBody("some_body_two"))
            .withHeaders(new Header("header_name_one", "header_value_one"));
    private final VerificationSequence fullVerificationSequence = new VerificationSequence().withRequests(requestOne);
    private final VerificationSequenceDTO fullVerificationSequenceDTO = new VerificationSequenceDTO(fullVerificationSequence);
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @Mock
    private JsonSchemaVerificationSequenceValidator verificationSequenceValidator;
    @InjectMocks
    private VerificationSequenceSerializer verificationSequenceSerializer;

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Before
    public void setupTestFixture() {
        verificationSequenceSerializer = spy(new VerificationSequenceSerializer(new MockServerLogger()));

        openMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        when(verificationSequenceValidator.isValid(eq("requestBytes"))).thenReturn("");
        when(objectMapper.readValue(eq("requestBytes"), same(VerificationSequenceDTO.class))).thenReturn(fullVerificationSequenceDTO);

        // when
        VerificationSequence verification = verificationSequenceSerializer.deserialize("requestBytes");

        // then
        assertEquals(fullVerificationSequence, verification);
    }

    @Test
    public void serialize() throws IOException {
        // when
        verificationSequenceSerializer.serialize(fullVerificationSequence);

        // then
        verify(objectWriter).writeValueAsString(fullVerificationSequenceDTO);
    }

    @Test
    public void serializeHandlesException() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing verificationSequence to JSON with value { }");
        // and
        when(objectWriter.writeValueAsString(any(VerificationSequenceDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        verificationSequenceSerializer.serialize(new VerificationSequence());
    }

}

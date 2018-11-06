package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.serialization.model.VerificationSequenceDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.verify.VerificationSequence;

import java.io.IOException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class VerificationSequenceSerializationErrorsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private VerificationSequenceSerializer verificationSequenceSerializer;


    @Before
    public void setupTestFixture() {
        verificationSequenceSerializer = spy(new VerificationSequenceSerializer(new MockServerLogger()));

        initMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing verificationSequence to JSON with value { }");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(VerificationSequenceDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        verificationSequenceSerializer.serialize(new VerificationSequence());
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("JsonParseException - Unrecognized token 'requestBytes': was expecting ('true', 'false' or 'null')");

        // when
        verificationSequenceSerializer.deserialize("requestBytes");
    }

}

package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.VerificationDTO;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.verify.Verification;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class VerificationSerializationErrorsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private VerificationSerializer verificationSerializer;


    @Before
    public void setupTestFixture() {
        verificationSerializer = spy(new VerificationSerializer(new MockServerLogger()));

        initMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileSerializingObject() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Exception while serializing verification to JSON with value {" + NEW_LINE +
                "  \"httpRequest\" : { }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"atLeast\" : 1" + NEW_LINE +
                "  }" + NEW_LINE +
                "}");
        // and
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(VerificationDTO.class))).thenThrow(new RuntimeException("TEST EXCEPTION"));

        // when
        verificationSerializer.serialize(new Verification());
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingObject() throws IOException {
        // given
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("JsonParseException - Unrecognized token 'requestBytes': was expecting ('true', 'false' or 'null')");

        // when
        verificationSerializer.deserialize("requestBytes");
    }

}

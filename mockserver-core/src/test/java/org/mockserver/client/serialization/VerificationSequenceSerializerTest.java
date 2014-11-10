package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.VerificationSequenceDTO;
import org.mockserver.model.*;
import org.mockserver.verify.VerificationSequence;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpRequest.request;

public class VerificationSequenceSerializerTest {

    private final HttpRequest requestOne =
            request()
                    .withMethod("GET")
                    .withURL("http://www.two.com")
                    .withPath("some_path_one")
                    .withBody(new StringBody("some_body_one", Body.Type.STRING))
                    .withHeaders(new Header("header_name_two", "header_value_two"));
    private final HttpRequest requestTwo =
            request()
                    .withMethod("GET")
                    .withURL("http://www.two.com")
                    .withPath("some_path_two")
                    .withBody(new StringBody("some_body_two", Body.Type.STRING))
                    .withHeaders(new Header("header_name_one", "header_value_one"));
    private final VerificationSequence fullVerificationSequence = new VerificationSequence().withRequests(requestOne);
    private final VerificationSequenceDTO fullVerificationSequenceDTO = new VerificationSequenceDTO(fullVerificationSequence);
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private VerificationSequenceSerializer verificationSequenceSerializer;

    @Before
    public void setupTestFixture() {
        verificationSequenceSerializer = spy(new VerificationSequenceSerializer());

        initMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(VerificationSequenceDTO.class))).thenReturn(fullVerificationSequenceDTO);

        // when
        VerificationSequence verification = verificationSequenceSerializer.deserialize("requestBytes");

        // then
        assertEquals(fullVerificationSequence, verification);
    }

    @Test
    public void deserializeHandleException() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(VerificationSequenceDTO.class))).thenThrow(new IOException("TEST EXCEPTION"));

        try {
            // when
            verificationSequenceSerializer.deserialize("requestBytes");
        } catch (Throwable t) {
            fail();
        }
    }

    @Test
    public void serialize() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        // when
        verificationSequenceSerializer.serialize(fullVerificationSequence);

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(fullVerificationSequenceDTO);
    }

    @Test(expected = RuntimeException.class)
    public void serializeHandlesException() throws IOException {
        // given
        VerificationSequence verification = mock(VerificationSequence.class);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(VerificationSequenceDTO.class))).thenThrow(IOException.class);

        // when
        verificationSequenceSerializer.serialize(verification);
    }
}
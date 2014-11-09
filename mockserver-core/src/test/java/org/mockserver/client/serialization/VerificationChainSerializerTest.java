package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.VerificationChainDTO;
import org.mockserver.model.*;
import org.mockserver.verify.VerificationChain;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.HttpRequest.request;

public class VerificationChainSerializerTest {

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
    private final VerificationChain fullVerificationChain = new VerificationChain().withRequests(requestOne);
    private final VerificationChainDTO fullVerificationChainDTO = new VerificationChainDTO(fullVerificationChain);
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private VerificationChainSerializer verificationChainSerializer;

    @Before
    public void setupTestFixture() {
        verificationChainSerializer = spy(new VerificationChainSerializer());

        initMocks(this);
    }

    @Test
    public void deserialize() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(VerificationChainDTO.class))).thenReturn(fullVerificationChainDTO);

        // when
        VerificationChain verification = verificationChainSerializer.deserialize("requestBytes");

        // then
        assertEquals(fullVerificationChain, verification);
    }

    @Test
    public void deserializeHandleException() throws IOException {
        // given
        when(objectMapper.readValue(eq("requestBytes"), same(VerificationChainDTO.class))).thenThrow(new IOException("TEST EXCEPTION"));

        try {
            // when
            verificationChainSerializer.deserialize("requestBytes");
        } catch (Throwable t) {
            fail();
        }
    }

    @Test
    public void serialize() throws IOException {
        // given
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        // when
        verificationChainSerializer.serialize(fullVerificationChain);

        // then
        verify(objectMapper).writerWithDefaultPrettyPrinter();
        verify(objectWriter).writeValueAsString(fullVerificationChainDTO);
    }

    @Test(expected = RuntimeException.class)
    public void serializeHandlesException() throws IOException {
        // given
        VerificationChain verification = mock(VerificationChain.class);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any(VerificationChainDTO.class))).thenThrow(IOException.class);

        // when
        verificationChainSerializer.serialize(verification);
    }
}
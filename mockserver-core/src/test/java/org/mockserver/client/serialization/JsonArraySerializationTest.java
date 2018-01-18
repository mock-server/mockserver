package org.mockserver.client.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.mock.Expectation;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonArraySerializationTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private JsonArraySerializer jsonArraySerializer;


    @Before
    public void setupTestFixture() {
        jsonArraySerializer = spy(new JsonArraySerializer());

        initMocks(this);
    }

    @Test
    public void shouldHandleExceptionWhileDeserializingArray() throws IOException {
        // given
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("java.io.IOException: TEST EXCEPTION");
        // and
        when(objectMapper.readTree(eq("requestBytes"))).thenThrow(new IOException("TEST EXCEPTION"));

        // when
        jsonArraySerializer.returnJSONObjects("requestBytes");
    }

    @Test
    public void shouldReturnArrayItems() throws IOException {
        // when
        assertThat(new JsonArraySerializer().returnJSONObjects("[{'foo':'bar'},{'foo':'bar'}]"), hasItems("{" + NEW_LINE + "  \"foo\" : \"bar\"" + NEW_LINE + "}", "{" + NEW_LINE + "  \"foo\" : \"bar\"" + NEW_LINE + "}"));
        assertThat(new JsonArraySerializer().returnJSONObjects("[{},{}]"), hasItems("{ }", "{ }"));
        assertThat(new JsonArraySerializer().returnJSONObjects("[{}]"), hasItems("{ }"));
        assertThat(new JsonArraySerializer().returnJSONObjects("[\"\"]"), hasItems("\"\""));
        assertThat(new JsonArraySerializer().returnJSONObjects("[\"\",\"\"]"), hasItems("\"\"", "\"\""));
        assertThat(new JsonArraySerializer().returnJSONObjects("[\"ab\",\"cd\"]"), hasItems("\"ab\"", "\"cd\""));
    }
}

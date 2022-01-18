package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class JsonArraySerializationTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @InjectMocks
    private JsonArraySerializer jsonArraySerializer;


    @Before
    public void setupTestFixture() {
        jsonArraySerializer = spy(new JsonArraySerializer());

        openMocks(this);
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
    public void shouldReturnArrayItems() {
        // when
        assertThat(new JsonArraySerializer().returnJSONObjects("[{'foo':'bar'},{'foo':'bar'}]"), hasItems("{" + NEW_LINE + "  \"foo\" : \"bar\"" + NEW_LINE + "}", "{" + NEW_LINE + "  \"foo\" : \"bar\"" + NEW_LINE + "}"));
        assertThat(new JsonArraySerializer().returnJSONObjects("[{},{}]"), hasItems("{ }", "{ }"));
        assertThat(new JsonArraySerializer().returnJSONObjects("[{}]"), hasItems("{ }"));
        assertThat(new JsonArraySerializer().returnJSONObjects("[\"\"]"), hasItems("\"\""));
        assertThat(new JsonArraySerializer().returnJSONObjects("[\"\",\"\"]"), hasItems("\"\"", "\"\""));
        assertThat(new JsonArraySerializer().returnJSONObjects("[\"ab\",\"cd\"]"), hasItems("\"ab\"", "\"cd\""));
    }
}

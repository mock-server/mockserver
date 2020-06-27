package org.mockserver.codec;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Parameter;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;

/**
 * See: https://swagger.io/docs/specification/serialization/
 */
public class FormParameterDecoderTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger(FormParameterDecoderTest.class);

    @Test
    public void shouldFormQueryParameters() {
        shouldParseParameters(
            "/users?one=5",
            param("one", "5"));
        shouldParseParameters(
            "/users?one=3&one=4&one=5",
            param("one", "3", "4", "5"));
        shouldParseParameters(
            "/users?one=3&one=4&one=5&two=1&two=2&three=1",
            param("one", "3", "4", "5"),
            param("two", "1", "2"),
            param("three", "1"));
        shouldParseParameters(
            "/users"
        );
    }

    @Test
    public void shouldFormBodyParameters() {
        shouldParseParameters(
            "one=5",
            param("one", "5"));
        shouldParseParameters(
            "one=3&one=4&one=5",
            param("one", "3", "4", "5"));
        shouldParseParameters(
            "one=3&one=4&one=5&two=1&two=2&three=1",
            param("one", "3", "4", "5"),
            param("two", "1", "2"),
            param("three", "1")
        );
        shouldParseParameters(
            ""
        );
    }

    @Test
    public void shouldSplitCommaDelimitedParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.FORM,
            Arrays.asList(
                string("2,3"),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.FORM,
            Arrays.asList(
                string("2,3", true),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.FORM,
            Arrays.asList(
                string("?2,3"),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.FORM,
            Arrays.asList(
                string("?!2,3"),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("?!2"),
                string("?!3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
    }

    @Test
    public void shouldNotSplitCommaDelimitedParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.FORM_EXPLODE,
            Arrays.asList(
                string("2,3"),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("2,3"),
                string("3,4,5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.FORM_EXPLODE,
            Arrays.asList(
                string("2,3", true),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("2,3", true),
                string("3,4,5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.FORM_EXPLODE,
            Arrays.asList(
                string("?2,3"),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3,4,5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.FORM_EXPLODE,
            Arrays.asList(
                string("?2,3", true),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("?2,3", true),
                string("3,4,5")
            )
        );
    }

    @Test
    public void shouldSplitPipeDelimitedParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED,
            Arrays.asList(
                string("2|3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED,
            Arrays.asList(
                string("2|3", true),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED,
            Arrays.asList(
                string("?2|3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED,
            Arrays.asList(
                string("?!2|3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("?!2"),
                string("?!3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
    }

    @Test
    public void shouldNotSplitPipeDelimitedParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("2|3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("2|3"),
                string("3|4|5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("!2|3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("!2|3"),
                string("3|4|5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("?2,3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3|4|5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("?!2|3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("?!2|3"),
                string("3|4|5")
            )
        );
    }

    @Test
    public void shouldSplitSpaceDelimitedWithPercentCharParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("2%203"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("2%203", true),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("?2%203"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("?!2%203"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("?!2"),
                string("?!3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
    }

    @Test
    public void shouldNotSplitSpaceDelimitedWithPercentCharParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("2%203"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("2%203"),
                string("3%204%205")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("!2%203"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("!2%203"),
                string("3%204%205")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("?2,3"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3%204%205")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("?!2%203"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("?!2%203"),
                string("3%204%205")
            )
        );
    }

    @Test
    public void shouldSplitSpaceDelimitedWithSpaceCharParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("2 3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("2 3", true),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("?2 3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("?!2 3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("?!2"),
                string("?!3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
    }

    @Test
    public void shouldNotSplitSpaceDelimitedWithSpaceCharParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("2 3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("2 3"),
                string("3 4 5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("!2 3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("!2 3"),
                string("3 4 5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("?2,3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3 4 5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("?!2 3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("?!2 3"),
                string("3 4 5")
            )
        );
    }

    @Test
    public void shouldSplitSpaceDelimitedWithPlusCharParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("2+3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("2+3", true),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("?2+3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.SPACE_DELIMITED,
            Arrays.asList(
                string("?!2+3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("?!2"),
                string("?!3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
    }

    @Test
    public void shouldNotSplitSpaceDelimitedWithPlusCharParameters() {
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("2+3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("2+3"),
                string("3+4+5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("!2+3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("!2+3"),
                string("3+4+5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("?2,3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3+4+5")
            )
        );
        assertThat(new FormParameterDecoder(mockServerLogger).splitOnDelimiter(Parameter.Style.PIPE_DELIMITED_EXPLODE,
            Arrays.asList(
                string("?!2+3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("?!2+3"),
                string("3+4+5")
            )
        );
    }

    private void shouldParseParameters(String path, Parameter... parameters) {
        List<Parameter> actual = new FormParameterDecoder(mockServerLogger).retrieveFormParameters(path, path.contains("?")).getEntries();
        if (parameters.length > 0) {
            assertThat(actual, containsInAnyOrder(parameters));
        } else {
            assertThat(actual, iterableWithSize(0));
        }
    }

}
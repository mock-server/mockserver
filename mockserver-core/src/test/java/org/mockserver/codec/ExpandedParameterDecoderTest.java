package org.mockserver.codec;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterStyle;

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
public class ExpandedParameterDecoderTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger(ExpandedParameterDecoderTest.class);

    @Test
    public void shouldParseQueryParameters() {
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
    public void shouldParseBodyParameters() {
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

    // MATRIX

    @Test
    public void shouldSplitMatrixParameters() {
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.MATRIX_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.MATRIX_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2;parameterName=3", true),
                string("3;parameterName=4;parameterName=5")
            )),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.MATRIX_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("?2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.MATRIX_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("?!2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
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
    public void shouldNotSplitMatrixParameters() {
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.MATRIX,
            "parameterName",
            Arrays.asList(
                string("2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )),
            containsInAnyOrder(
                string("2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.MATRIX,
            "parameterName",
            Arrays.asList(
                string("2;parameterName=3", true),
                string("3;parameterName=4;parameterName=5")
            )),
            containsInAnyOrder(
                string("2;parameterName=3", true),
                string("3;parameterName=4;parameterName=5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.MATRIX,
            "parameterName",
            Arrays.asList(
                string("?2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )),
            containsInAnyOrder(
                string("?2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.MATRIX,
            "parameterName",
            Arrays.asList(
                string("?!2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )),
            containsInAnyOrder(
                string("?!2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )
        );
    }

    // LABEL

    @Test
    public void shouldSplitLabelParameters() {
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.LABEL_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2.3"),
                string("3.4.5")
            )),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.LABEL_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2.3", true),
                string("3.4.5")
            )),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.LABEL_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("?2.3"),
                string("3.4.5")
            )),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.LABEL_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("?!2.3"),
                string("3.4.5")
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
    public void shouldNotSplitLabelParameters() {
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.LABEL,
            "parameterName",
            Arrays.asList(
                string("2.3"),
                string("3.4.5")
            )),
            containsInAnyOrder(
                string("2.3"),
                string("3.4.5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.LABEL,
            "parameterName",
            Arrays.asList(
                string("2.3", true),
                string("3.4.5")
            )),
            containsInAnyOrder(
                string("2.3", true),
                string("3.4.5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.LABEL,
            "parameterName",
            Arrays.asList(
                string("?2.3"),
                string("3.4.5")
            )),
            containsInAnyOrder(
                string("?2.3"),
                string("3.4.5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.LABEL,
            "parameterName",
            Arrays.asList(
                string("?!2.3"),
                string("3.4.5")
            )),
            containsInAnyOrder(
                string("?!2.3"),
                string("3.4.5")
            )
        );
    }

    // FORM

    @Test
    public void shouldSplitFormParameters() {
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.FORM,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.FORM,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.FORM,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.FORM,
            "parameterName",
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
    public void shouldNotSplitFormParameters() {
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.FORM_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2,3"),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("2,3"),
                string("3,4,5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.FORM_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2,3", true),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("2,3", true),
                string("3,4,5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.FORM_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("?2,3"),
                string("3,4,5")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3,4,5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.FORM_EXPLODED,
            "parameterName",
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

    // PIPE DELIMITED

    @Test
    public void shouldSplitPipeDelimitedParameters() {
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2|3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("2|3"),
                string("3|4|5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("!2|3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("!2|3"),
                string("3|4|5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("?2,3"),
                string("3|4|5")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3|4|5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED_EXPLODED,
            "parameterName",
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

    // SPACE DELIMITED

    @Test
    public void shouldSplitSpaceDelimitedWithPercentCharParameters() {
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2%203"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("2%203"),
                string("3%204%205")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("!2%203"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("!2%203"),
                string("3%204%205")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("?2,3"),
                string("3%204%205")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3%204%205")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED_EXPLODED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2 3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("2 3"),
                string("3 4 5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("!2 3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("!2 3"),
                string("3 4 5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("?2,3"),
                string("3 4 5")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3 4 5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED_EXPLODED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.SPACE_DELIMITED,
            "parameterName",
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
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("2+3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("2+3"),
                string("3+4+5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("!2+3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("!2+3"),
                string("3+4+5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED_EXPLODED,
            "parameterName",
            Arrays.asList(
                string("?2,3"),
                string("3+4+5")
            )),
            containsInAnyOrder(
                string("?2,3"),
                string("3+4+5")
            )
        );
        assertThat(new ExpandedParameterDecoder(mockServerLogger).splitOnDelimiter(
            ParameterStyle.PIPE_DELIMITED_EXPLODED,
            "parameterName",
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
        List<Parameter> actual = new ExpandedParameterDecoder(mockServerLogger).retrieveFormParameters(path, path.contains("?")).getEntries();
        if (parameters.length > 0) {
            assertThat(actual, containsInAnyOrder(parameters));
        } else {
            assertThat(actual, iterableWithSize(0));
        }
    }

}
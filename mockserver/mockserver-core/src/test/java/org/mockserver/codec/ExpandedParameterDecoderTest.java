package org.mockserver.codec;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterStyle;
import org.mockserver.model.Parameters;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;

/**
 * See: https://swagger.io/docs/specification/serialization/
 */
public class ExpandedParameterDecoderTest {

    private final Configuration configuration = configuration();
    private final MockServerLogger mockServerLogger = new MockServerLogger(ExpandedParameterDecoderTest.class);

    @Test
    public void shouldParseQueryParameters() {
        shouldParseParameters(
            "/users?one=5",
            param("one", "5")
        );
        shouldParseParameters(
            "/users?one=3&one=4&one=5",
            param("one", "3", "4", "5")
        );
        shouldParseParameters(
            "/users?one=3&one=4&one=5&two=1&two=2&three=1",
            param("one", "3", "4", "5"),
            param("two", "1", "2"),
            param("three", "1")
        );
        shouldParseParameters(
            "/users"
        );
    }

    @Test
    public void shouldParseQueryParametersSeparatedBySemicolon() {
        shouldParseParameters(
            "/users?one=5",
            param("one", "5")
        );
        shouldParseParameters(
            "/users?one=3;one=4;one=5",
            param("one", "3", "4", "5")
        );
        shouldParseParameters(
            "/users?one=3;one=4;one=5;two=1;two=2;three=1",
            param("one", "3", "4", "5"),
            param("two", "1", "2"),
            param("three", "1")
        );
        shouldParseParameters(
            "/users"
        );
    }

    @Test
    public void shouldNotParseQueryParametersSeparatedBySemicolon() {
        Configuration noSemicolonConfig = configuration().useSemicolonAsQueryParameterSeparator(false);
        MockServerLogger noSemicolonLogger = new MockServerLogger(ExpandedParameterDecoderTest.class);
        shouldParseParametersWithConfig(
            noSemicolonConfig, noSemicolonLogger,
            "/users?one=5",
            param("one", "5")
        );
        shouldParseParametersWithConfig(
            noSemicolonConfig, noSemicolonLogger,
            "/users?one=3;one=4;one=5",
            param("one", "3;one=4;one=5")
        );
        shouldParseParametersWithConfig(
            noSemicolonConfig, noSemicolonLogger,
            "/users?one=3;one=4;one=5;two=1;two=2;three=1",
            param("one", "3;one=4;one=5;two=1;two=2;three=1")
        );
        shouldParseParametersWithConfig(
            noSemicolonConfig, noSemicolonLogger,
            "/users"
        );
    }

    @Test
    public void shouldParseBodyParameters() {
        shouldParseParameters(
            "one=5",
            param("one", "5")
        );
        shouldParseParameters(
            "one=3&one=4&one=5",
            param("one", "3", "4", "5")
        );
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
    public void shouldParseBodyParametersWithSlash() {
        shouldParseParameters(
            "code=48e5392a3ea21fa73a4bc0a63823b2a13071e82907ec1f03fffb56269efcd7f6&" +
                "code_verifier=NzpeXIxUAwJyX7LJIDGTLx_07UWOLBYYPE-3f9WjyGY&" +
                "redirect_uri=com.calendly.app://oauth&" +
                "client_id=75b439951f843108a4e89e67179a7a529ae81f61b0aa66ade49ef3c038373c78&" +
                "grant_type=authorization_code",
            param("code", "48e5392a3ea21fa73a4bc0a63823b2a13071e82907ec1f03fffb56269efcd7f6"),
            param("code_verifier", "NzpeXIxUAwJyX7LJIDGTLx_07UWOLBYYPE-3f9WjyGY"),
            param("redirect_uri", "com.calendly.app://oauth"),
            param("client_id", "75b439951f843108a4e89e67179a7a529ae81f61b0aa66ade49ef3c038373c78"),
            param("grant_type", "authorization_code")
        );
    }

    // MATRIX

    @Test
    public void shouldSplitMatrixParameters() {
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.MATRIX_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2;parameterName=3"),
                    string("3;parameterName=4;parameterName=5")
                )
            ),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.MATRIX_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2;parameterName=3", true),
                    string("3;parameterName=4;parameterName=5")
                )
            ),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.MATRIX_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?2;parameterName=3"),
                    string("3;parameterName=4;parameterName=5")
                )
            ),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.MATRIX_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?!2;parameterName=3"),
                    string("3;parameterName=4;parameterName=5")
                )
            ),
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
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.MATRIX,
                "parameterName",
                Arrays.asList(
                    string("2;parameterName=3"),
                    string("3;parameterName=4;parameterName=5")
                )
            ),
            containsInAnyOrder(
                string("2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.MATRIX,
                "parameterName",
                Arrays.asList(
                    string("2;parameterName=3", true),
                    string("3;parameterName=4;parameterName=5")
                )
            ),
            containsInAnyOrder(
                string("2;parameterName=3", true),
                string("3;parameterName=4;parameterName=5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.MATRIX,
                "parameterName",
                Arrays.asList(
                    string("?2;parameterName=3"),
                    string("3;parameterName=4;parameterName=5")
                )
            ),
            containsInAnyOrder(
                string("?2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.MATRIX,
                "parameterName",
                Arrays.asList(
                    string("?!2;parameterName=3"),
                    string("3;parameterName=4;parameterName=5")
                )
            ),
            containsInAnyOrder(
                string("?!2;parameterName=3"),
                string("3;parameterName=4;parameterName=5")
            )
        );
    }

    // LABEL

    @Test
    public void shouldSplitLabelParameters() {
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.LABEL_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2.3"),
                    string("3.4.5")
                )
            ),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.LABEL_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2.3", true),
                    string("3.4.5")
                )
            ),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.LABEL_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?2.3"),
                    string("3.4.5")
                )
            ),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.LABEL_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?!2.3"),
                    string("3.4.5")
                )
            ),
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
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.LABEL,
                "parameterName",
                Arrays.asList(
                    string("2.3"),
                    string("3.4.5")
                )
            ),
            containsInAnyOrder(
                string("2.3"),
                string("3.4.5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.LABEL,
                "parameterName",
                Arrays.asList(
                    string("2.3", true),
                    string("3.4.5")
                )
            ),
            containsInAnyOrder(
                string("2.3", true),
                string("3.4.5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.LABEL,
                "parameterName",
                Arrays.asList(
                    string("?2.3"),
                    string("3.4.5")
                )
            ),
            containsInAnyOrder(
                string("?2.3"),
                string("3.4.5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.LABEL,
                "parameterName",
                Arrays.asList(
                    string("?!2.3"),
                    string("3.4.5")
                )
            ),
            containsInAnyOrder(
                string("?!2.3"),
                string("3.4.5")
            )
        );
    }

    // FORM

    @Test
    public void shouldSplitFormParameters() {
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.FORM,
                "parameterName",
                Arrays.asList(
                    string("2,3"),
                    string("3,4,5")
                )
            ),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.FORM,
                "parameterName",
                Arrays.asList(
                    string("2,3", true),
                    string("3,4,5")
                )
            ),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.FORM,
                "parameterName",
                Arrays.asList(
                    string("?2,3"),
                    string("3,4,5")
                )
            ),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.FORM,
                "parameterName",
                Arrays.asList(
                    string("?!2,3"),
                    string("3,4,5")
                )
            ),
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
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.FORM_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2,3"),
                    string("3,4,5")
                )
            ),
            containsInAnyOrder(
                string("2,3"),
                string("3,4,5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.FORM_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2,3", true),
                    string("3,4,5")
                )
            ),
            containsInAnyOrder(
                string("2,3", true),
                string("3,4,5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.FORM_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?2,3"),
                    string("3,4,5")
                )
            ),
            containsInAnyOrder(
                string("?2,3"),
                string("3,4,5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.FORM_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?2,3", true),
                    string("3,4,5")
                )
            ),
            containsInAnyOrder(
                string("?2,3", true),
                string("3,4,5")
            )
        );
    }

    // PIPE DELIMITED

    @Test
    public void shouldSplitPipeDelimitedParameters() {
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("2|3"),
                    string("3|4|5")
                )
            ),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("2|3", true),
                    string("3|4|5")
                )
            ),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("?2|3"),
                    string("3|4|5")
                )
            ),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("?!2|3"),
                    string("3|4|5")
                )
            ),
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
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2|3"),
                    string("3|4|5")
                )
            ),
            containsInAnyOrder(
                string("2|3"),
                string("3|4|5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("!2|3"),
                    string("3|4|5")
                )
            ),
            containsInAnyOrder(
                string("!2|3"),
                string("3|4|5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?2,3"),
                    string("3|4|5")
                )
            ),
            containsInAnyOrder(
                string("?2,3"),
                string("3|4|5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?!2|3"),
                    string("3|4|5")
                )
            ),
            containsInAnyOrder(
                string("?!2|3"),
                string("3|4|5")
            )
        );
    }

    // SPACE DELIMITED

    @Test
    public void shouldSplitSpaceDelimitedWithPercentCharParameters() {
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("2%203"),
                    string("3%204%205")
                )
            ),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("2%203", true),
                    string("3%204%205")
                )
            ),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("?2%203"),
                    string("3%204%205")
                )
            ),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("?!2%203"),
                    string("3%204%205")
                )
            ),
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
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2%203"),
                    string("3%204%205")
                )
            ),
            containsInAnyOrder(
                string("2%203"),
                string("3%204%205")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("!2%203"),
                    string("3%204%205")
                )
            ),
            containsInAnyOrder(
                string("!2%203"),
                string("3%204%205")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?2,3"),
                    string("3%204%205")
                )
            ),
            containsInAnyOrder(
                string("?2,3"),
                string("3%204%205")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?!2%203"),
                    string("3%204%205")
                )
            ),
            containsInAnyOrder(
                string("?!2%203"),
                string("3%204%205")
            )
        );
    }

    @Test
    public void shouldSplitSpaceDelimitedWithSpaceCharParameters() {
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("2 3"),
                    string("3 4 5")
                )
            ),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("2 3", true),
                    string("3 4 5")
                )
            ),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("?2 3"),
                    string("3 4 5")
                )
            ),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("?!2 3"),
                    string("3 4 5")
                )
            ),
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
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2 3"),
                    string("3 4 5")
                )
            ),
            containsInAnyOrder(
                string("2 3"),
                string("3 4 5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("!2 3"),
                    string("3 4 5")
                )
            ),
            containsInAnyOrder(
                string("!2 3"),
                string("3 4 5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?2,3"),
                    string("3 4 5")
                )
            ),
            containsInAnyOrder(
                string("?2,3"),
                string("3 4 5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?!2 3"),
                    string("3 4 5")
                )
            ),
            containsInAnyOrder(
                string("?!2 3"),
                string("3 4 5")
            )
        );
    }

    @Test
    public void shouldSplitSpaceDelimitedWithPlusCharParameters() {
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("2+3"),
                    string("3+4+5")
                )
            ),
            containsInAnyOrder(
                string("2"),
                string("3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("2+3", true),
                    string("3+4+5")
                )
            ),
            containsInAnyOrder(
                string("2", true),
                string("3", true),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("?2+3"),
                    string("3+4+5")
                )
            ),
            containsInAnyOrder(
                string("?2"),
                string("?3"),
                string("3"),
                string("4"),
                string("5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.SPACE_DELIMITED,
                "parameterName",
                Arrays.asList(
                    string("?!2+3"),
                    string("3+4+5")
                )
            ),
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
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("2+3"),
                    string("3+4+5")
                )
            ),
            containsInAnyOrder(
                string("2+3"),
                string("3+4+5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("!2+3"),
                    string("3+4+5")
                )
            ),
            containsInAnyOrder(
                string("!2+3"),
                string("3+4+5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?2,3"),
                    string("3+4+5")
                )
            ),
            containsInAnyOrder(
                string("?2,3"),
                string("3+4+5")
            )
        );
        assertThat(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(
                ParameterStyle.PIPE_DELIMITED_EXPLODED,
                "parameterName",
                Arrays.asList(
                    string("?!2+3"),
                    string("3+4+5")
                )
            ),
            containsInAnyOrder(
                string("?!2+3"),
                string("3+4+5")
            )
        );
    }

    private void shouldParseParameters(String path, Parameter... parameters) {
        shouldParseParametersWithConfig(configuration, mockServerLogger, path, parameters);
    }

    private void shouldParseParametersWithConfig(Configuration config, MockServerLogger logger, String path, Parameter... parameters) {
        List<Parameter> actual = new ExpandedParameterDecoder(config, logger).retrieveFormParameters(path, path.contains("?")).getEntries();
        if (parameters.length > 0) {
            assertThat(actual, containsInAnyOrder(parameters));
        } else {
            assertThat(actual, iterableWithSize(0));
        }
    }

    // BY PARAMETERS

    @Test
    public void shouldSplitByMatchParameters() {
        // given
        Parameters matcher = new Parameters(
            param(string("some_name"), string("1")).withStyle(ParameterStyle.SPACE_DELIMITED),
            param(string("some_other_name"), string("a")).withStyle(ParameterStyle.MATRIX_EXPLODED),
            param(string("some_other_name_two"), string("value")).withStyle(ParameterStyle.PIPE_DELIMITED)
        );
        Parameters matched = new Parameters(
            param(string("some_name"), string("1%202 3+4")),
            param(string("some_other_name"), string("a;some_other_name=b;some_other_name=c")),
            param(string("some_other_name_two"), string("one|two|three"))
        );

        // when
        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        // then
        assertThat(matched, is(new Parameters(
            param("some_name", "1", "2", "3", "4"),
            param("some_other_name", "a", "b", "c"),
            param("some_other_name_two", "one", "two", "three")
        )));
    }

    @Test
    public void shouldSplitByMatchParametersAndMatchByRegex() {
        // given
        Parameters matcher = new Parameters(
            param(string("some_name"), string("1")).withStyle(ParameterStyle.SPACE_DELIMITED),
            param(string("some_other_[a-z]{2}me"), string("a")).withStyle(ParameterStyle.FORM),
            param(string("some_other_name_.*"), string("value")).withStyle(ParameterStyle.PIPE_DELIMITED)
        );
        Parameters matched = new Parameters(
            param(string("some_name"), string("1%202 3+4")),
            param(string("some_other_name"), string("a,b,c")),
            param(string("some_other_name_two"), string("one|two|three"))
        );

        // when
        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        // then
        assertThat(matched, is(new Parameters(
            param("some_name", "1", "2", "3", "4"),
            param("some_other_name", "a", "b", "c"),
            param("some_other_name_two", "one", "two", "three")
        )));
    }

    @Test
    public void shouldPreserveDuplicateQueryParameterValues() {
        Parameters parameters = new ExpandedParameterDecoder(configuration, mockServerLogger)
            .retrieveQueryParameters("/?q=1&q=1&q=2", true);
        assertThat(parameters.getEntries(), containsInAnyOrder(
            param("q", "1", "1", "2")
        ));
    }

    @Test
    public void shouldPreserveEncodedQuestionMarkInQueryParameterValues() {
        Parameters parameters = new ExpandedParameterDecoder(configuration, mockServerLogger)
            .retrieveQueryParameters("/?redirect=http%3A%2F%2Fexample.com%3Ffoo%3Dbar", true);
        assertThat(parameters.getEntries(), containsInAnyOrder(
            param("redirect", "http://example.com?foo=bar")
        ));
    }

    @Test
    public void shouldPreserveEncodedExclamationMarkInQueryParameterValues() {
        Parameters parameters = new ExpandedParameterDecoder(configuration, mockServerLogger)
            .retrieveQueryParameters("/?msg=hello%21world", true);
        assertThat(parameters.getEntries(), containsInAnyOrder(
            param("msg", "hello!world")
        ));
    }

    @Test
    public void shouldPreserveDuplicateFormParameterValues() {
        List<Parameter> actual = new ExpandedParameterDecoder(configuration, mockServerLogger)
            .retrieveFormParameters("q=1&q=1&q=2", false).getEntries();
        assertThat(actual, containsInAnyOrder(
            param("q", "1", "1", "2")
        ));
    }

    // OBJECT RECONSTRUCTION

    @Test
    public void shouldReconstructObjectFromAlternatingValues() {
        ExpandedParameterDecoder decoder = new ExpandedParameterDecoder(configuration, mockServerLogger);
        String result = decoder.reconstructObjectFromAlternatingValues(
            Arrays.asList(string("R"), string("100"), string("G"), string("200"), string("B"), string("150"))
        );
        assertThat(result, is("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
    }

    @Test
    public void shouldReturnNullForOddNumberOfAlternatingValues() {
        ExpandedParameterDecoder decoder = new ExpandedParameterDecoder(configuration, mockServerLogger);
        String result = decoder.reconstructObjectFromAlternatingValues(
            Arrays.asList(string("R"), string("100"), string("G"))
        );
        assertThat(result, is((String) null));
    }

    @Test
    public void shouldReturnNullForEmptyAlternatingValues() {
        ExpandedParameterDecoder decoder = new ExpandedParameterDecoder(configuration, mockServerLogger);
        String result = decoder.reconstructObjectFromAlternatingValues(
            Collections.emptyList()
        );
        assertThat(result, is((String) null));
    }

    @Test
    public void shouldReturnNullForSingleAlternatingValue() {
        ExpandedParameterDecoder decoder = new ExpandedParameterDecoder(configuration, mockServerLogger);
        String result = decoder.reconstructObjectFromAlternatingValues(
            Collections.singletonList(string("R"))
        );
        assertThat(result, is((String) null));
    }

    @Test
    public void shouldReconstructExplodedObject() {
        ExpandedParameterDecoder decoder = new ExpandedParameterDecoder(configuration, mockServerLogger);
        String result = decoder.reconstructExplodedObject(
            Arrays.asList(string("R=100"), string("G=200"), string("B=150"))
        );
        assertThat(result, is("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
    }

    @Test
    public void shouldReturnNullForExplodedObjectWithoutEquals() {
        ExpandedParameterDecoder decoder = new ExpandedParameterDecoder(configuration, mockServerLogger);
        String result = decoder.reconstructExplodedObject(
            Arrays.asList(string("100"), string("200"), string("150"))
        );
        assertThat(result, is((String) null));
    }

    @Test
    public void shouldReturnNullForEmptyExplodedObject() {
        ExpandedParameterDecoder decoder = new ExpandedParameterDecoder(configuration, mockServerLogger);
        String result = decoder.reconstructExplodedObject(
            Collections.emptyList()
        );
        assertThat(result, is((String) null));
    }

    // SIMPLE STYLE OBJECT

    @Test
    public void shouldSplitSimpleNonExplodedObjectParameters() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.SIMPLE).withSchemaType("object"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R,100,G,200,B,150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched.getValues("color"), containsInAnyOrder("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
    }

    @Test
    public void shouldSplitSimpleExplodedObjectParameters() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.SIMPLE_EXPLODED).withSchemaType("object"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R=100,G=200,B=150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched.getValues("color"), containsInAnyOrder("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
    }

    // LABEL STYLE OBJECT

    @Test
    public void shouldSplitLabelNonExplodedObjectParameters() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.LABEL).withSchemaType("object"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R,100,G,200,B,150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched.getValues("color"), containsInAnyOrder("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
    }

    @Test
    public void shouldSplitLabelExplodedObjectParameters() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.LABEL_EXPLODED).withSchemaType("object"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R=100.G=200.B=150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched.getValues("color"), containsInAnyOrder("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
    }

    // MATRIX STYLE OBJECT

    @Test
    public void shouldSplitMatrixNonExplodedObjectParameters() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.MATRIX).withSchemaType("object"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R,100,G,200,B,150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched.getValues("color"), containsInAnyOrder("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
    }

    @Test
    public void shouldSplitMatrixExplodedObjectParameters() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.MATRIX_EXPLODED).withSchemaType("object"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R=100;color=G=200;color=B=150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched.getValues("color"), containsInAnyOrder("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
    }

    // FORM STYLE OBJECT

    @Test
    public void shouldSplitFormNonExplodedObjectParameters() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.FORM).withSchemaType("object"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R,100,G,200,B,150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched.getValues("color"), containsInAnyOrder("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
    }

    // DEEP OBJECT STYLE

    @Test
    public void shouldReconstructDeepObjectParameters() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.DEEP_OBJECT).withSchemaType("object"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color[R]"), string("100")),
            param(string("color[G]"), string("200")),
            param(string("color[B]"), string("150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched.getValues("color"), containsInAnyOrder("{\"R\":\"100\",\"G\":\"200\",\"B\":\"150\"}"));
        assertThat(matched.getValues("color[R]"), is(Collections.emptyList()));
        assertThat(matched.getValues("color[G]"), is(Collections.emptyList()));
        assertThat(matched.getValues("color[B]"), is(Collections.emptyList()));
    }

    @Test
    public void shouldNotReconstructDeepObjectParametersWithoutObjectSchemaType() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.DEEP_OBJECT), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color[R]"), string("100")),
            param(string("color[G]"), string("200"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched.getValues("color[R]"), containsInAnyOrder("100"));
        assertThat(matched.getValues("color[G]"), containsInAnyOrder("200"));
    }

    // ARRAY TYPE SHOULD NOT BE RECONSTRUCTED AS OBJECT

    @Test
    public void shouldNotReconstructArrayTypeAsObject() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.SIMPLE).withSchemaType("array"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R,100,G,200,B,150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched, is(new Parameters(
            param("color", "R", "100", "G", "200", "B", "150")
        )));
    }

    @Test
    public void shouldNotReconstructStringTypeAsObject() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.SIMPLE).withSchemaType("string"), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R,100,G,200,B,150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched, is(new Parameters(
            param("color", "R", "100", "G", "200", "B", "150")
        )));
    }

    @Test
    public void shouldNotReconstructWithoutSchemaType() {
        Parameters matcher = new Parameters(
            param(string("color").withStyle(ParameterStyle.SIMPLE), string("schema"))
        );
        Parameters matched = new Parameters(
            param(string("color"), string("R,100,G,200,B,150"))
        );

        new ExpandedParameterDecoder(configuration, mockServerLogger).splitParameters(matcher, matched);

        assertThat(matched, is(new Parameters(
            param("color", "R", "100", "G", "200", "B", "150")
        )));
    }

    // DEEP_OBJECT STYLE ON NOTTABLE STRING

    @Test
    public void shouldAllowDeepObjectStyleOnNottableString() {
        NottableString name = string("color").withStyle(ParameterStyle.DEEP_OBJECT);
        assertThat(name.getParameterStyle(), is(ParameterStyle.DEEP_OBJECT));
    }

    // SCHEMA TYPE ON NOTTABLE STRING

    @Test
    public void shouldSetAndGetSchemaType() {
        NottableString name = string("color").withSchemaType("object");
        assertThat(name.getSchemaType(), is("object"));
    }

    @Test
    public void shouldReturnNullSchemaTypeByDefault() {
        NottableString name = string("color");
        assertThat(name.getSchemaType(), is((String) null));
    }

}
package org.mockserver.matchers;

import org.junit.Test;

import java.util.*;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;

public class MatchDifferenceFormatterTest {

    @Test
    public void shouldFormatEmptyDifferences() {
        assertThat(MatchDifferenceFormatter.formatDifferences(null), is(""));
        assertThat(MatchDifferenceFormatter.formatDifferences(Collections.emptyMap()), is(""));
    }

    @Test
    public void shouldFormatSingleFieldDifference() {
        Map<MatchDifference.Field, List<String>> differences = new LinkedHashMap<>();
        differences.put(MatchDifference.Field.METHOD, Collections.singletonList("expected GET but was POST"));

        String result = MatchDifferenceFormatter.formatDifferences(differences);

        assertThat(result, containsString("closest match diff:"));
        assertThat(result, containsString("method:"));
        assertThat(result, containsString("expected GET but was POST"));
    }

    @Test
    public void shouldFormatMultipleFieldDifferences() {
        Map<MatchDifference.Field, List<String>> differences = new LinkedHashMap<>();
        differences.put(MatchDifference.Field.METHOD, Collections.singletonList("expected GET but was POST"));
        differences.put(MatchDifference.Field.PATH, Collections.singletonList("expected /api/users but was /api/user"));

        String result = MatchDifferenceFormatter.formatDifferences(differences);

        assertThat(result, containsString("method:"));
        assertThat(result, containsString("path:"));
        assertThat(result, containsString("expected GET but was POST"));
        assertThat(result, containsString("expected /api/users but was /api/user"));
    }

    @Test
    public void shouldFormatMultipleDifferencesPerField() {
        Map<MatchDifference.Field, List<String>> differences = new LinkedHashMap<>();
        differences.put(MatchDifference.Field.HEADERS, Arrays.asList("missing header Accept", "wrong Content-Type"));

        String result = MatchDifferenceFormatter.formatDifferences(differences);

        assertThat(result, containsString("headers:"));
        assertThat(result, containsString("missing header Accept"));
        assertThat(result, containsString("wrong Content-Type"));
    }

    @Test
    public void shouldTruncateLongDiffLines() {
        StringBuilder longLine = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longLine.append("x");
        }

        String result = MatchDifferenceFormatter.truncateDiffLine(longLine.toString());

        assertThat(result.length(), is(500 + "...[truncated]".length()));
        assertThat(result.endsWith("...[truncated]"), is(true));
    }

    @Test
    public void shouldNotTruncateShortDiffLines() {
        String shortLine = "expected GET but was POST";
        assertThat(MatchDifferenceFormatter.truncateDiffLine(shortLine), is(shortLine));
    }

    @Test
    public void shouldCollapseNewlinesInDiffLines() {
        String multiLine = "line one\nline two\nline three";
        String result = MatchDifferenceFormatter.truncateDiffLine(multiLine);
        assertThat(result, is("line one line two line three"));
    }

    @Test
    public void shouldOutputFieldsInDefinedOrder() {
        Map<MatchDifference.Field, List<String>> differences = new LinkedHashMap<>();
        differences.put(MatchDifference.Field.BODY, Collections.singletonList("body mismatch"));
        differences.put(MatchDifference.Field.METHOD, Collections.singletonList("method mismatch"));

        String result = MatchDifferenceFormatter.formatDifferences(differences);

        int methodPos = result.indexOf("method:");
        int bodyPos = result.indexOf("body:");
        assertThat("method should appear before body in output", methodPos < bodyPos, is(true));
    }
}

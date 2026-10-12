package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ProviderContractShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<String> compare(String real, String mock, boolean partial) throws Exception {
        return ProviderContractShape.compare("body", Collections.singletonList(MAPPER.readTree(real)),
                Collections.singletonList(MAPPER.readTree(mock)), partial)
            .stream().map(Object::toString).collect(Collectors.toList());
    }

    @Test
    public void shouldIgnoreValuesOtherThanDiscriminators() throws Exception {
        assertThat(compare(
            "{\"id\":\"chatcmpl-1\",\"created\":1,\"choices\":[{\"message\":{\"content\":\"hi\"}}]}",
            "{\"id\":\"chatcmpl-2\",\"created\":99,\"choices\":[{\"message\":{\"content\":\"something else\"}}]}", false), is(empty()));
    }

    @Test
    public void shouldReportOnlyTheTopmostMissingAndExtraPath() throws Exception {
        assertThat(compare(
            "{\"usage\":{\"details\":{\"cached\":1}},\"a\":1}",
            "{\"a\":1,\"extra\":{\"nested\":true}}", false), contains("MISSING body usage", "EXTRA body extra"));
    }

    @Test
    public void shouldReportATypeMismatchButTreatNullAsAnyType() throws Exception {
        assertThat(compare("{\"arguments\":\"{}\",\"refusal\":null}", "{\"arguments\":{},\"refusal\":\"no\"}", false),
            contains("TYPE body arguments (real [string], mock [object])"));
    }

    @Test
    public void shouldMergeArrayElementsAndCompareDiscriminatorValues() throws Exception {
        assertThat(compare(
            "{\"choices\":[{\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"finish_reason\":\"length\"}]}", false),
            contains("VALUE body choices[].finish_reason (real [stop], mock [length])"));
        assertThat("a partial example needs only a subset of the values",
            compare("{\"choices\":[{\"type\":\"text\"}]}", "{\"choices\":[{\"type\":\"text\"},{\"type\":\"tool_use\"}]}", true), is(empty()));
    }

    @Test
    public void shouldCompareStreamOrderWithRepeatsCollapsed() {
        List<ProviderContractShape.Event> real = ProviderContractShape.parseSse(
            "event: a\ndata: {\"type\":\"a\"}\n\n: comment\n\nevent: b\ndata: {\"type\":\"b\"}\n\nevent: b\ndata: {\"type\":\"b\"}\n\n");
        List<ProviderContractShape.Event> mock = Arrays.asList(
            new ProviderContractShape.Event("a", "{\"type\":\"a\"}"),
            new ProviderContractShape.Event("b", "{\"type\":\"b\"}"));
        assertThat(ProviderContractShape.compareStreams(real, mock, false), is(empty()));

        List<ProviderContractShape.Event> reordered = Arrays.asList(mock.get(1), mock.get(0));
        assertThat(ProviderContractShape.compareStreams(real, reordered, false).toString(), containsString("SEQUENCE stream events"));
    }

    @Test
    public void shouldAcceptAPartialExampleThatIsASubsequence() {
        List<ProviderContractShape.Event> real = ProviderContractShape.parseSse("data: {\"x\":1}\r\n\r\ndata: [DONE]\r\n\r\n");
        List<ProviderContractShape.Event> mock = Arrays.asList(
            new ProviderContractShape.Event("start", "{\"y\":1}"),
            new ProviderContractShape.Event(null, "{\"x\":2}"),
            new ProviderContractShape.Event(null, "[DONE]"));
        assertThat(ProviderContractShape.compareStreams(real, mock, true), is(empty()));
        assertThat(ProviderContractShape.compareStreams(real, mock, false).toString(), containsString("SEQUENCE"));
    }

    @Test
    public void shouldParseMultiLineDataAndDropOneLeadingSpace() {
        List<ProviderContractShape.Event> events = ProviderContractShape.parseSse("event:x\ndata:  a\ndata: b\n\n");
        assertThat(events.size(), is(1));
        assertThat(events.get(0).name, is("x"));
        assertThat(events.get(0).data, is(" a\nb"));
    }

    @Test
    public void shouldDescribeShapeByPath() throws Exception {
        JsonNode node = MAPPER.readTree("{\"a\":[{\"b\":1},{\"b\":\"x\",\"c\":null}]}");
        assertThat(ProviderContractShape.shape(node).toString(), is("{a=[array], a[]=[object], a[].b=[number, string], a[].c=[null]}"));
    }
}

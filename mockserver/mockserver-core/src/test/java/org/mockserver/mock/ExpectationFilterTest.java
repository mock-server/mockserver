package org.mockserver.mock;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.Test;
import org.mockserver.model.HttpRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static org.mockserver.mock.ExpectationFilter.expectationFilter;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ExpectationFilterTest {

    private static Expectation withMetadata(Map<String, ?> metadata) {
        return new Expectation(request().withPath("/some")).withMetadata(metadata).thenRespond(response());
    }

    private static Map<String, Object> metadata(Object... keysAndValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            metadata.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return metadata;
    }

    private static ExpectationFilter parse(String... namesAndValues) {
        HttpRequest request = request();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            request.withQueryStringParameter(namesAndValues[i], namesAndValues[i + 1]);
        }
        return ExpectationFilter.fromQueryStringParameters(request);
    }

    @Test
    public void emptyFilterSelectsEverythingAndDoesNotPage() {
        ExpectationFilter filter = parse();
        List<String> items = Arrays.asList("a", "b");

        assertThat(filter.hasSelection(), is(false));
        assertThat(filter.hasPaging(), is(false));
        assertThat(filter.matches(withMetadata(null)), is(true));
        assertThat(filter.page(items), sameInstance(items));
    }

    @Test
    public void metadataKeyAloneSelectsAnyValue() {
        ExpectationFilter filter = parse("metadataKey", "owner");

        assertThat(filter.matches(withMetadata(metadata("owner", "team-a"))), is(true));
        assertThat(filter.matches(withMetadata(metadata("owner", null))), is(true));
        assertThat(filter.matches(withMetadata(metadata("other", "team-a"))), is(false));
        assertThat(filter.matches(withMetadata(null)), is(false));
    }

    @Test
    public void stringValueIsComparedAsText() {
        ExpectationFilter filter = parse("metadataKey", "owner", "metadataValue", "team-a");

        assertThat(filter.matches(withMetadata(metadata("owner", "team-a"))), is(true));
        assertThat(filter.matches(withMetadata(metadata("owner", "team-b"))), is(false));
        assertThat(filter.matches(withMetadata(metadata("owner", "Team-A"))), is(false));
    }

    @Test
    public void quotedJsonStringMatchesTheString() {
        assertThat(parse("metadataKey", "owner", "metadataValue", "\"team-a\"").matches(withMetadata(metadata("owner", "team-a"))), is(true));
    }

    @Test
    public void nonStringValuesAreComparedAsJson() {
        assertThat(parse("metadataKey", "ticket", "metadataValue", "42").matches(withMetadata(metadata("ticket", 42))), is(true));
        assertThat(parse("metadataKey", "ticket", "metadataValue", "42").matches(withMetadata(metadata("ticket", 42L))), is(true));
        assertThat(parse("metadataKey", "ticket", "metadataValue", "42.0").matches(withMetadata(metadata("ticket", 42))), is(true));
        assertThat(parse("metadataKey", "ticket", "metadataValue", "43").matches(withMetadata(metadata("ticket", 42))), is(false));
        assertThat(parse("metadataKey", "ticket", "metadataValue", "42").matches(withMetadata(metadata("ticket", "42"))), is(true));
        assertThat(parse("metadataKey", "flaky", "metadataValue", "true").matches(withMetadata(metadata("flaky", true))), is(true));
        assertThat(parse("metadataKey", "flaky", "metadataValue", "false").matches(withMetadata(metadata("flaky", true))), is(false));
        assertThat(parse("metadataKey", "tags", "metadataValue", "[\"a\",\"b\"]").matches(withMetadata(metadata("tags", Arrays.asList("a", "b")))), is(true));
        assertThat(parse("metadataKey", "tags", "metadataValue", "[\"b\",\"a\"]").matches(withMetadata(metadata("tags", Arrays.asList("a", "b")))), is(false));
        assertThat(parse("metadataKey", "owner", "metadataValue", "{\"team\":\"a\"}").matches(withMetadata(metadata("owner", JsonNodeFactory.instance.objectNode().put("team", "a")))), is(true));
        assertThat(parse("metadataKey", "missing", "metadataValue", "null").matches(withMetadata(metadata("missing", null))), is(true));
        assertThat(parse("metadataKey", "ticket", "metadataValue", "not json").matches(withMetadata(metadata("ticket", 42))), is(false));
    }

    @Test
    public void jsonPathSelectsOnTheMetadataObject() {
        Expectation tagged = withMetadata(metadata("owner", "team-a", "tags", Arrays.asList("smoke", "checkout")));

        assertThat(parse("metadataJsonPath", "$[?(@.owner == 'team-a')]").matches(tagged), is(true));
        assertThat(parse("metadataJsonPath", "$[?(@.owner == 'team-b')]").matches(tagged), is(false));
        assertThat(parse("metadataJsonPath", "$.tags[?(@ == 'smoke')]").matches(tagged), is(true));
        assertThat(parse("metadataJsonPath", "$.tags[?(@ == 'nightly')]").matches(tagged), is(false));
        assertThat(parse("metadataJsonPath", "$.missing").matches(tagged), is(false));
        assertThat(parse("metadataJsonPath", "$.owner").matches(withMetadata(null)), is(false));
    }

    @Test
    public void allSelectionsMustHold() {
        ExpectationFilter filter = parse("metadataKey", "owner", "metadataValue", "team-a", "metadataJsonPath", "$.tags[?(@ == 'smoke')]");

        assertThat(filter.matches(withMetadata(metadata("owner", "team-a", "tags", Collections.singletonList("smoke")))), is(true));
        assertThat(filter.matches(withMetadata(metadata("owner", "team-a", "tags", Collections.singletonList("nightly")))), is(false));
        assertThat(filter.matches(withMetadata(metadata("owner", "team-b", "tags", Collections.singletonList("smoke")))), is(false));
    }

    @Test
    public void unmatchedSelectsOnlyExpectationsThatHaveNeverMatched() {
        Expectation unused = withMetadata(null);
        Expectation used = withMetadata(null);
        used.consumeMatch();
        ExpectationFilter filter = parse("unmatched", "true");

        assertThat(filter.matches(unused), is(true));
        assertThat(filter.matches(used), is(false));
        assertThat(parse("unmatched", "false").matches(used), is(true));
        assertThat(parse("unmatched", "false").hasSelection(), is(false));
    }

    @Test
    public void pagesInOrder() {
        List<Integer> items = Arrays.asList(0, 1, 2, 3, 4);

        assertThat(parse("limit", "2").page(items), contains(0, 1));
        assertThat(parse("offset", "3").page(items), contains(3, 4));
        assertThat(parse("offset", "1", "limit", "2").page(items), contains(1, 2));
        assertThat(parse("offset", "4", "limit", "10").page(items), contains(4));
        assertThat(parse("offset", "5").page(items), empty());
        assertThat(parse("offset", "99", "limit", "1").page(items), empty());
        assertThat(parse("limit", "0").page(items), empty());
        assertThat(parse("offset", "1", "limit", String.valueOf(Integer.MAX_VALUE)).page(items), contains(1, 2, 3, 4));
        assertThat(parse("limit", "2").page(null), nullValue());
    }

    @Test
    public void rejectsInvalidParameters() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("limit", "-1")).getMessage(), containsString("\"limit\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("offset", "abc")).getMessage(), containsString("\"offset\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("limit", "99999999999")).getMessage(), containsString("\"limit\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("unmatched", "yes")).getMessage(), containsString("\"unmatched\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("metadataValue", "x")).getMessage(), containsString("\"metadataKey\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("metadataJsonPath", "$[?(@.owner ==")).getMessage(), containsString("not a valid JSONPath"));
    }

    @Test
    public void rejectsPresentButBlankSelectionParameters() {
        for (String name : Arrays.asList("metadataKey", "metadataJsonPath")) {
            for (String blank : Arrays.asList("", " ")) {
                assertThat(name + "=\"" + blank + "\"", assertThrows(IllegalArgumentException.class, () -> parse(name, blank)).getMessage(), containsString("\"" + name + "\" must not be blank"));
            }
        }
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("unmatched", "")).getMessage(), containsString("\"unmatched\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("limit", "")).getMessage(), containsString("\"limit\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("offset", " ")).getMessage(), containsString("\"offset\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("metadataKey", "", "metadataValue", "x")).getMessage(), containsString("must not be blank"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("metadataValue", "")).getMessage(), containsString("\"metadataKey\""));
    }

    @Test
    public void unmatchedFalseIsReportedAsSelectingNothing() {
        assertThrows(IllegalArgumentException.class, () -> parse("unmatched", "false").requireComplete(true));
        assertThrows(IllegalArgumentException.class, () -> parse("unmatched", "false").requireComplete(false));
        assertThat(parse("unmatched", "true").requireComplete(true).hasSelection(), is(true));
        assertThat(parse("unmatched", "false", "metadataKey", "owner").requireComplete(true).hasSelection(), is(true));
        assertThat(parse().requireComplete(true).isFiltered(), is(false));
        assertThrows(IllegalArgumentException.class, () -> parse("limit", "1").requireComplete(true));
        assertThat(parse("limit", "1").requireComplete(false).hasPaging(), is(true));
    }

    @Test
    public void clientBuilderRejectsBlankOrInvalidSelections() {
        for (String blank : Arrays.asList(null, "", " ")) {
            assertThrows(IllegalArgumentException.class, () -> expectationFilter().withMetadata(blank, "x"));
            assertThrows(IllegalArgumentException.class, () -> expectationFilter().withMetadataKey(blank));
            assertThrows(IllegalArgumentException.class, () -> expectationFilter().withMetadataJsonPath(blank));
        }
        assertThrows(IllegalArgumentException.class, () -> expectationFilter().withMetadataJsonPath("$[?(@.owner =="));
    }

    @Test
    public void parameterNamesAreMatchedIgnoringCaseForPresenceAndValueAlike() {
        ExpectationFilter filter = parse("METADATAKEY", "owner", "MetadataValue", "team-a", "metadatajsonpath", "$.owner", "Unmatched", "TRUE", "LIMIT", "2", "Offset", "1");

        assertThat(filter.getMetadataKey(), is("owner"));
        assertThat(filter.getMetadataValue(), is("team-a"));
        assertThat(filter.getMetadataJsonPath(), is("$.owner"));
        assertThat(filter.isUnmatched(), is(true));
        assertThat(filter.getLimit(), is(2));
        assertThat(filter.getOffset(), is(1));
        assertThat(filter.matches(withMetadata(metadata("owner", "team-b"))), is(false));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("MetadataKey", "")).getMessage(), containsString("must not be blank"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("METADATAVALUE", "x")).getMessage(), containsString("requires"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("UNMATCHED", "")).getMessage(), containsString("\"unmatched\""));
        assertThrows(IllegalArgumentException.class, () -> parse("UnMatched", "false").requireComplete(true));
    }

    @Test
    public void refusesUnrecognisedOrRepeatedFilterParameters() {
        for (String name : Arrays.asList("metadata_key", "Metadata-Key", "metadataKeys", "metadata", "metadataJsonPathExpression", "unmatchedOnly", "UN_MATCHED")) {
            assertThat(name, assertThrows(IllegalArgumentException.class, () -> parse(name, "owner")).getMessage(), containsString("not a recognised expectation filter parameter"));
        }
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("metadataKey", "a", "MetadataKey", "b")).getMessage(), containsString("at most once"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> ExpectationFilter.fromQueryStringParameters(request().withQueryStringParameter("unmatched", "true", "false"))).getMessage(), containsString("at most once"));
        // parameters that are not filters are left alone
        assertThat(parse("type", "expectations", "namespace", "team", "format", "json").hasSelection(), is(false));
    }

    @Test
    public void notAndOptionalPrefixesAreSentLiterallyOnTheWire() {
        org.mockserver.mappers.MockServerHttpRequestToFullHttpRequest encoder = new org.mockserver.mappers.MockServerHttpRequestToFullHttpRequest(new org.mockserver.logging.MockServerLogger(), null);

        String valueUri = encoder.getURI(expectationFilter().withMetadata("owner", "!team-a").applyTo(request("/mockserver/clear")), null);
        String keyUri = encoder.getURI(expectationFilter().withMetadataKey("!owner").applyTo(request("/mockserver/clear")), null);
        String optionalUri = encoder.getURI(expectationFilter().withMetadata("?owner", "?").withMetadataJsonPath("$[?(@.owner == '!a')]").applyTo(request("/mockserver/clear")), null);

        assertThat(valueUri, is("/mockserver/clear?metadataKey=owner&metadataValue=%21team-a"));
        assertThat(keyUri, is("/mockserver/clear?metadataKey=%21owner"));
        Map<String, List<String>> decoded = new io.netty.handler.codec.http.QueryStringDecoder(optionalUri).parameters();
        assertThat(decoded.get("metadataKey"), contains("?owner"));
        assertThat(decoded.get("metadataValue"), contains("?"));
        assertThat(decoded.get("metadataJsonPath"), contains("$[?(@.owner == '!a')]"));
        // and the server, which decodes literally, reads back exactly what was set
        ExpectationFilter received = ExpectationFilter.fromQueryStringParameters(expectationFilter().withMetadata("!owner", "?!team-a").applyTo(request()));
        assertThat(received.getMetadataKey(), is("!owner"));
        assertThat(received.getMetadataValue(), is("?!team-a"));
    }

    @Test
    public void refusesInProcessNotOrOptionalFilterParameters() {
        // the String API parses "!" and "?" as matcher markers; such a parameter cannot be read literally
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("metadataKey", "owner", "metadataValue", "!team-a")).getMessage(), containsString("must be a literal value"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("metadataKey", "?owner")).getMessage(), containsString("must be a literal value"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> parse("!metadataKey", "owner")).getMessage(), containsString("must be a literal value"));
    }

    private static ExpectationFilter fromWire(String rawQuery) {
        org.mockserver.codec.ExpandedParameterDecoder decoder = new org.mockserver.codec.ExpandedParameterDecoder(org.mockserver.configuration.Configuration.configuration(), new org.mockserver.logging.MockServerLogger());
        HttpRequest received = request("/mockserver/clear");
        decoder.applyQueryParameters(received, "/mockserver/clear?" + rawQuery, true);
        return ExpectationFilter.fromControlPlaneRequest(received);
    }

    @Test
    public void malformedPercentEncodingAnywhereInTheQueryIsRefused() {
        for (String raw : Arrays.asList(
            "type=expectations&metadataKey=discount&metadataValue=100%",
            "metadataKey=owner&metadataValue=%ZZ",
            "metadataKey=owner&metadataValue=%Z",
            "metadataKey=owner&metadataValue=team-a&note=50%off",
            "type=expectations&note=%",
            "metadataKey=owner&metadataValue=%C3%28",
            "metadataKey=owner&metadataValue=%FF",
            "metadataKey=owner&metadataValue=%E2%82"
        )) {
            assertThat(raw, assertThrows(raw, IllegalArgumentException.class, () -> fromWire(raw)).getMessage(), containsString("cannot be decoded"));
        }
    }

    @Test
    public void wireQueriesAreDecodedLikeTheServerDecoder() {
        assertThat(fromWire("metadataKey=owner&metadataValue=a+b").getMetadataValue(), is("a b"));
        assertThat(fromWire("metadataKey=owner&metadataValue=a%2Bb").getMetadataValue(), is("a+b"));
        assertThat(fromWire("metadataKey=owner&metadataValue=100%25").getMetadataValue(), is("100%"));
        assertThat(fromWire("metadataKey=owner&metadataValue=caf%C3%A9").getMetadataValue(), is("café"));
        assertThat(fromWire("metadataKey=owner&metadataValue=a;b").getMetadataValue(), is("a;b"));
        assertThat(fromWire("%6DetadataKey=owner").getMetadataKey(), is("owner"));
        assertThat(fromWire("METADATAKEY=owner").getMetadataKey(), is("owner"));
        assertThat(fromWire("type=expectations&namespace=team").isFiltered(), is(false));
        assertThat(assertThrows(IllegalArgumentException.class, () -> fromWire("metadata%5Fkey=owner")).getMessage(), containsString("not a recognised"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> fromWire("type=expectations&namespace=").requireComplete(true)).getMessage(), containsString("\"namespace\" must not be blank"));
    }

    @Test
    public void aNullValueIsRefusedAndTheKeyOnlyFormIsExplicit() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> expectationFilter().withMetadata("owner", null)).getMessage(), containsString("withMetadataKey"));
        ExpectationFilter keyOnly = expectationFilter().withMetadata("owner", "team-a").withMetadataKey("ticket");
        assertThat(keyOnly.getMetadataKey(), is("ticket"));
        assertThat(keyOnly.getMetadataValue(), nullValue());
        assertThat(expectationFilter().withMetadata("owner", com.fasterxml.jackson.databind.node.NullNode.getInstance()).getMetadataValue(), is("null"));
        assertThat(expectationFilter().withMetadata("owner", com.fasterxml.jackson.databind.node.NullNode.getInstance()).matches(withMetadata(metadata("owner", null))), is(true));
        assertThat(expectationFilter().withMetadata("owner", com.fasterxml.jackson.databind.node.NullNode.getInstance()).matches(withMetadata(metadata("owner", "team-a"))), is(false));
    }

    @Test
    public void clientQueryParametersRoundTripToTheSameFilter() {
        ExpectationFilter sent = expectationFilter()
            .withMetadata("ticket", 42)
            .withMetadataJsonPath("$.tags[?(@ == 'smoke')]")
            .withUnmatched(true)
            .withOffset(5)
            .withLimit(10);

        ExpectationFilter received = ExpectationFilter.fromQueryStringParameters(sent.applyTo(request()));

        assertThat(received.getMetadataKey(), is("ticket"));
        assertThat(received.getMetadataValue(), is("42"));
        assertThat(received.getMetadataJsonPath(), is("$.tags[?(@ == 'smoke')]"));
        assertThat(received.isUnmatched(), is(true));
        assertThat(received.getOffset(), is(5));
        assertThat(received.getLimit(), is(10));
        assertThat(expectationFilter().withMetadata("owner", "team-a").getMetadataValue(), is("team-a"));
        assertThat(expectationFilter().withMetadata("tags", Arrays.asList("a", "b")).getMetadataValue(), is("[\"a\",\"b\"]"));
    }
}

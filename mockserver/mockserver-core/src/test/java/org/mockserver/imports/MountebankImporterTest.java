package org.mockserver.imports;

import org.junit.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.ResponseMode;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MountebankImporterTest {

    private final MountebankImporter importer = new MountebankImporter();

    // A replayable mb config with an http imposter (multiple stubs), a tcp imposter (skipped),
    // exercising equals/deepEquals/contains/matches/exists predicates, is/proxy/fault responses,
    // _behaviors wait/repeat, multi-response cycling, and inject (warned).
    private static final String MOUNTEBANK = """
        {
          "imposters": [
            {
              "port": 4545,
              "protocol": "http",
              "name": "orders api",
              "stubs": [
                {
                  "predicates": [
                    { "equals": { "method": "GET", "path": "/orders" } },
                    { "deepEquals": { "query": { "status": "open" } } },
                    { "matches": { "headers": { "Accept": "application/.*" } } }
                  ],
                  "responses": [
                    {
                      "is": {
                        "statusCode": 200,
                        "headers": { "Content-Type": "application/json" },
                        "body": { "orders": [1, 2] }
                      },
                      "_behaviors": { "wait": 300, "repeat": 2 }
                    }
                  ]
                },
                {
                  "predicates": [
                    { "contains": { "body": "urgent" } },
                    { "exists": { "headers": { "X-Api-Version": true } } }
                  ],
                  "responses": [
                    { "proxy": { "to": "http://downstream.example.com:9000", "mode": "proxyOnce" } }
                  ]
                },
                {
                  "predicates": [ { "equals": { "path": "/cycle" } } ],
                  "responses": [
                    { "is": { "statusCode": 200, "body": "first" } },
                    { "is": { "statusCode": 200, "body": "second" } }
                  ]
                },
                {
                  "predicates": [ { "equals": { "path": "/boom" } } ],
                  "responses": [ { "fault": "CONNECTION_RESET_BY_PEER" } ]
                },
                {
                  "predicates": [ { "equals": { "path": "/js" } } ],
                  "responses": [ { "inject": "function (request) { return { body: 'x' }; }" } ]
                }
              ]
            },
            {
              "port": 5555,
              "protocol": "tcp",
              "stubs": [ { "responses": [ { "is": { "data": "raw" } } ] } ]
            }
          ]
        }
        """;

    @Test
    public void skipsNonHttpImposterWithWarning() {
        ImportResult result = importer.importExpectations(MOUNTEBANK);
        assertTrue("expected a tcp-imposter skip warning",
            result.getWarnings().stream().anyMatch(w ->
                w.getConstruct().equals("imposter.protocol") && w.getDetail().contains("tcp")));
    }

    @Test
    public void mapsEqualsAndDeepEqualsAndMatchesPredicates() {
        Expectation orders = importer.importExpectations(MOUNTEBANK).getExpectations().get(0);
        HttpRequest request = (HttpRequest) orders.getHttpRequest();
        assertThat(request.getMethod().getValue(), is("GET"));
        assertThat(request.getPath().getValue(), is("/orders"));
        String json = request.toString();
        assertThat(json, containsString("status"));
        assertThat(json, containsString("open"));
        assertThat(json, containsString("application/.*"));
    }

    @Test
    public void mapsIsResponseWithWaitDelayAndDropsSingleResponseRepeat() {
        ImportResult result = importer.importExpectations(MOUNTEBANK);
        Expectation orders = result.getExpectations().get(0);
        HttpResponse response = orders.getHttpResponse();
        assertThat(response.getStatusCode(), is(200));
        assertThat(response.getBodyAsString(), containsString("orders"));
        assertThat(response.getDelay().getValue(), is(300L));
        // repeat:2 on a single-response stub must NOT become Times.exactly(2): Mountebank serves a
        // single response indefinitely, so mapping to a finite count would make the stub 404 after
        // 2 hits. The expectation stays unlimited and a warning documents the dropped repeat.
        assertTrue("single-response repeat must map to unlimited times", orders.getTimes().isUnlimited());
        assertTrue("expected a dropped single-response repeat warning",
            result.getWarnings().stream().anyMatch(w ->
                w.getConstruct().equals("response._behaviors.repeat")
                    && w.getDetail().contains("indefinitely")));
    }

    @Test
    public void mapsContainsBodyAndProxyResponse() {
        Expectation proxied = importer.importExpectations(MOUNTEBANK).getExpectations().get(1);
        HttpRequest request = (HttpRequest) proxied.getHttpRequest();
        assertThat(request.getBody().toString(), containsString("urgent"));
        HttpForward forward = proxied.getHttpForward();
        assertThat(forward, is(not(nullValue())));
        assertThat(forward.getHost(), is("downstream.example.com"));
        assertThat(forward.getPort(), is(9000));
    }

    @Test
    public void mapsMultipleIsResponsesToSequentialCycle() {
        Expectation cycle = importer.importExpectations(MOUNTEBANK).getExpectations().get(2);
        assertThat(cycle.getHttpResponses(), hasSize(2));
        assertThat(cycle.getResponseMode(), is(ResponseMode.SEQUENTIAL));
        assertThat(cycle.getHttpResponses().get(0).getBodyAsString(), is("first"));
        assertThat(cycle.getHttpResponses().get(1).getBodyAsString(), is("second"));
    }

    @Test
    public void mapsConnectionResetByPeerFaultToConnectionReset() {
        Expectation boom = importer.importExpectations(MOUNTEBANK).getExpectations().get(3);
        HttpError error = boom.getHttpError();
        assertThat(error, is(not(nullValue())));
        assertThat(error.getResetConnection(), is(true));
        assertThat(error.getDropConnection(), is(nullValue()));
    }

    @Test
    public void warnsAndPlaceholdsInjectResponse() {
        ImportResult result = importer.importExpectations(MOUNTEBANK);
        assertTrue("expected an inject warning",
            result.getWarnings().stream().anyMatch(w -> w.getConstruct().contains("inject")));
        // inject stub is the 5th mapped expectation (index 4)
        Expectation js = result.getExpectations().get(4);
        assertThat(js.getHttpResponse().getStatusCode(), is(501));
    }

    @Test
    public void mapsExistsHeaderPredicateToPresenceMatcher() {
        // 2nd stub has exists header X-Api-Version
        HttpRequest request = (HttpRequest) importer.importExpectations(MOUNTEBANK).getExpectations().get(1).getHttpRequest();
        assertThat(request.toString(), containsString("X-Api-Version"));
    }

    @Test
    public void producesFiveExpectationsFromHttpImposter() {
        // 5 http stubs mapped; tcp imposter skipped
        assertThat(importer.importExpectations(MOUNTEBANK).getExpectations(), hasSize(5));
    }

    @Test
    public void warnsThatExactPredicatesBecomeCaseSensitiveAndMakesRegexPredicatesCaseInsensitive() {
        // Mountebank matches case-insensitively by default. An exact equals predicate can't be made
        // case-insensitive in MockServer, so it must warn; a `matches` regex predicate is preserved
        // faithfully with a (?i) prefix.
        String imposter = """
            { "protocol": "http", "port": 7000, "stubs": [
              { "predicates": [
                  { "equals": { "path": "/Case" } },
                  { "matches": { "headers": { "X-Env": "PROD" } } } ],
                "responses": [ { "is": { "statusCode": 200 } } ] } ] }
            """;
        ImportResult result = importer.importExpectations(imposter);
        assertTrue("expected a case-sensitivity warning for the exact equals predicate",
            result.getWarnings().stream().anyMatch(w ->
                w.getConstruct().equals("predicate.equals")
                    && w.getDetail().contains("case-sensitively")));
        // the matches predicate carries a (?i) prefix so it stays case-insensitive
        assertThat(result.getExpectations().get(0).getHttpRequest().toString(), containsString("(?i)PROD"));
        // caseSensitive:true suppresses the warning (MockServer already matches case-sensitively)
        String sensitive = """
            { "protocol": "http", "port": 7001, "stubs": [
              { "predicates": [ { "equals": { "path": "/Case" }, "caseSensitive": true } ],
                "responses": [ { "is": { "statusCode": 200 } } ] } ] }
            """;
        assertTrue("caseSensitive:true must not raise a case-sensitivity warning",
            importer.importExpectations(sensitive).getWarnings().stream().noneMatch(w ->
                w.getConstruct().equals("predicate.equals")));
    }

    @Test
    public void warnsOnCompoundPredicate() {
        String imposter = """
            { "protocol": "http", "port": 6000, "stubs": [
              { "predicates": [ { "or": [ { "equals": { "path": "/a" } } ] } ],
                "responses": [ { "is": { "statusCode": 200 } } ] } ] }
            """;
        ImportResult result = importer.importExpectations(imposter);
        assertThat(result.getWarnings().size(), is(greaterThanOrEqualTo(1)));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.getConstruct().contains("predicate")));
    }

    @Test
    public void supportsSingleImposterForm() {
        String imposter = """
            { "protocol": "https", "port": 443, "stubs": [
              { "predicates": [ { "equals": { "path": "/x" } } ],
                "responses": [ { "is": { "statusCode": 200, "body": "ok" } } ] } ] }
            """;
        assertThat(importer.importExpectations(imposter).getExpectations(), hasSize(1));
    }

    @Test
    public void rejectsBlankAndInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> importer.importExpectations(""));
        assertThrows(IllegalArgumentException.class, () -> importer.importExpectations("{ \"foo\": 1 }"));
    }
}

package org.mockserver.client;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.ClientConfiguration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.ResponseMode;
import org.mockserver.model.CrossProtocolScenario;
import org.mockserver.model.CrossProtocolTrigger;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.ExpectationSerializer;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.ClientConfiguration.clientConfiguration;
import static org.mockserver.model.HttpResponse.response;

/**
 * Verifies the scenario-parity fluent setters on {@link ForwardChainExpectation} populate the
 * underlying {@link Expectation} and that it serializes with the contract JSON field names.
 *
 * @author jamesdbloom
 */
public class ForwardChainExpectationScenarioParityTest {

    private MockServerClient mockServerClient;
    private Expectation expectation;
    private ForwardChainExpectation forwardChainExpectation;

    @Before
    public void setupFixture() {
        mockServerClient = mock(MockServerClient.class);
        expectation = new Expectation(org.mockserver.model.HttpRequest.request().withPath("/some/path"));
        when(mockServerClient.upsert(expectation)).thenReturn(new Expectation[]{expectation});
        ClientConfiguration configuration = clientConfiguration();
        forwardChainExpectation = new ForwardChainExpectation(configuration, new MockServerLogger(), new MockServerEventBus(), mockServerClient, expectation);
    }

    @Test
    public void shouldSetMetadataIncludingJsonValues() {
        // when
        forwardChainExpectation
            .withMetadata("owner", "team-a")
            .withMetadataJson("tags", "[\"smoke\",{\"n\":2}]");

        // then
        assertThat(expectation.getMetadata().get("owner"), is("team-a"));
        assertThat(expectation.getMetadata().get("tags"), is(Arrays.asList("smoke", java.util.Collections.singletonMap("n", 2))));
        org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> forwardChainExpectation.withMetadataJson("tags", "[not json"));
    }

    @Test
    public void shouldSetHttpResponsesResponseModeWeightsAndSwitchAfter() {
        // given
        List<HttpResponse> responses = Arrays.asList(response().withBody("one"), response().withBody("two"));

        // when
        forwardChainExpectation
            .withHttpResponses(responses)
            .withResponseMode(ResponseMode.WEIGHTED)
            .withResponseWeights(Arrays.asList(3, 1))
            .withSwitchAfter(2);

        // then — populates the underlying expectation
        assertThat(expectation.getHttpResponses().size(), is(2));
        assertThat(expectation.getResponseMode(), is(ResponseMode.WEIGHTED));
        assertThat(expectation.getResponseWeights(), is(Arrays.asList(3, 1)));
        assertThat(expectation.getSwitchAfter(), is(2));
    }

    @Test
    public void shouldSetCrossProtocolScenarioSingularAndPlural() {
        // given
        CrossProtocolScenario scenario = CrossProtocolScenario.crossProtocolScenario()
            .withTrigger(CrossProtocolTrigger.DNS_QUERY)
            .withMatchPattern("api.example.com")
            .withScenarioName("Deploy")
            .withTargetState("DnsObserved");

        // when (singular)
        forwardChainExpectation.withCrossProtocolScenario(scenario);

        // then
        assertThat(expectation.getCrossProtocolScenarios().size(), is(1));
        assertThat(expectation.getCrossProtocolScenarios().get(0).getScenarioName(), is("Deploy"));

        // when (plural replaces)
        CrossProtocolScenario wsScenario = CrossProtocolScenario.onWebSocketConnect("Deploy", "Connected");
        forwardChainExpectation.withCrossProtocolScenarios(Arrays.asList(scenario, wsScenario));

        // then
        assertThat(expectation.getCrossProtocolScenarios().size(), is(2));
        assertThat(expectation.getCrossProtocolScenarios().get(1).getTrigger(), is(CrossProtocolTrigger.WEBSOCKET_CONNECT));
    }

    @Test
    public void shouldSerializeNewFieldsWithContractJsonFieldNames() {
        // given
        forwardChainExpectation
            .withScenarioName("Deploy")
            .withScenarioState("Idle")
            .withNewScenarioState("Deploying")
            .withHttpResponses(Arrays.asList(response().withBody("one"), response().withBody("two")))
            .withResponseMode(ResponseMode.WEIGHTED)
            .withResponseWeights(Arrays.asList(3, 1))
            .withSwitchAfter(2)
            .withCrossProtocolScenario(
                CrossProtocolScenario.crossProtocolScenario()
                    .withTrigger(CrossProtocolTrigger.GRPC_REQUEST)
                    .withMatchPattern("my.Service")
                    .withScenarioName("Deploy")
                    .withTargetState("GrpcSeen"));

        // when — serialize with the production serializer
        String json = new ExpectationSerializer(new MockServerLogger()).serialize(expectation);

        // then — exact contract field names are present
        assertThat(json, containsString("\"scenarioName\""));
        assertThat(json, containsString("\"scenarioState\""));
        assertThat(json, containsString("\"newScenarioState\""));
        assertThat(json, containsString("\"httpResponses\""));
        assertThat(json, containsString("\"responseMode\""));
        assertThat(json, containsString("WEIGHTED"));
        assertThat(json, containsString("\"responseWeights\""));
        assertThat(json, containsString("\"switchAfter\""));
        assertThat(json, containsString("\"crossProtocolScenarios\""));
        assertThat(json, containsString("\"trigger\""));
        assertThat(json, containsString("GRPC_REQUEST"));
        assertThat(json, containsString("\"matchPattern\""));
        assertThat(json, containsString("\"targetState\""));
    }
}

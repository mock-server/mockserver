package org.mockserver.serialization;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.AfterAction;
import org.mockserver.model.FailurePolicy;
import org.mockserver.model.HttpTemplate;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.Delay.seconds;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * Round-trips expectations carrying {@code beforeActions} / {@code afterActions} through
 * {@link ExpectationSerializer} to verify the DTO mapping and JSON schema accept the new
 * before-action controls (blocking / timeout / failurePolicy) and that absent fields stay absent
 * (backward compatibility).
 */
public class BeforeAndAfterActionsSerializationTest {

    private final ExpectationSerializer serializer = new ExpectationSerializer(new MockServerLogger());

    @Test
    public void shouldRoundTripBlockingBeforeActionWithTimeoutAndFailurePolicy() {
        // given
        Expectation expectation = new Expectation(request("/path"))
            .withBeforeActions(
                AfterAction.beforeAction()
                    .withHttpRequest(request("/hook"))
                    .withBlocking(true)
                    .withTimeout(seconds(2))
                    .withFailurePolicy(FailurePolicy.FAIL_FAST)
            )
            .thenRespond(response("ok"));

        // when
        String json = serializer.serialize(expectation);
        Expectation parsed = serializer.deserialize(json);

        // then
        List<AfterAction> beforeActions = parsed.getBeforeActions();
        assertThat(beforeActions, hasSize(1));
        AfterAction before = beforeActions.get(0);
        assertThat(before.getHttpRequest().getPath().getValue(), is("/hook"));
        assertThat(before.getBlocking(), is(true));
        assertThat(before.getTimeout(), is(seconds(2)));
        assertThat(before.getFailurePolicy(), is(FailurePolicy.FAIL_FAST));
        assertThat(parsed.getAfterActions(), is(nullValue()));
    }

    @Test
    public void shouldRoundTripBeforeAndAfterActionsTogether() {
        // given
        Expectation expectation = new Expectation(request("/path"))
            .withBeforeActions(AfterAction.beforeAction().withHttpRequest(request("/before")))
            .withAfterActions(AfterAction.afterAction().withHttpRequest(request("/after")))
            .thenRespond(response("ok"));

        // when
        Expectation parsed = serializer.deserialize(serializer.serialize(expectation));

        // then
        assertThat(parsed.getBeforeActions(), hasSize(1));
        assertThat(parsed.getBeforeActions().get(0).getHttpRequest().getPath().getValue(), is("/before"));
        assertThat(parsed.getAfterActions(), hasSize(1));
        assertThat(parsed.getAfterActions().get(0).getHttpRequest().getPath().getValue(), is("/after"));
    }

    @Test
    public void shouldOmitBeforeActionControlsWhenUnset() {
        // given a before-action with no blocking/timeout/failurePolicy controls
        Expectation expectation = new Expectation(request("/path"))
            .withBeforeActions(AfterAction.beforeAction().withHttpRequest(request("/hook")))
            .thenRespond(response("ok"));

        // when
        String json = serializer.serialize(expectation);
        Expectation parsed = serializer.deserialize(json);

        // then the optional controls survive as unset (defaults applied at dispatch time)
        AfterAction before = parsed.getBeforeActions().get(0);
        assertThat(before.getBlocking(), is(nullValue()));
        assertThat(before.getTimeout(), is(nullValue()));
        assertThat(before.getFailurePolicy(), is(nullValue()));
        assertThat(json, not(containsString("\"failurePolicy\"")));
    }

    @Test
    public void shouldRoundTripTemplatedAfterActionWebhook() {
        // given
        Expectation expectation = new Expectation(request("/path"))
            .withAfterActions(AfterAction.afterAction()
                .withHttpRequestTemplate(template(HttpTemplate.TemplateType.VELOCITY, "{ \"path\": \"/hook$!request.path\" }")))
            .thenRespond(response("ok"));

        // when
        String json = serializer.serialize(expectation);
        Expectation parsed = serializer.deserialize(json);

        // then
        assertThat(json, containsString("\"httpRequestTemplate\""));
        AfterAction after = parsed.getAfterActions().get(0);
        assertThat(after.getHttpRequest(), is(nullValue()));
        assertThat(after.getHttpRequestTemplate().getTemplateType(), is(HttpTemplate.TemplateType.VELOCITY));
        assertThat(after.getHttpRequestTemplate().getTemplate(), is("{ \"path\": \"/hook$!request.path\" }"));
        assertThat(parsed, is(expectation));
    }

    @Test
    public void shouldAcceptTemplatedWebhookJsonAndRejectOneWithBothRequestAndTemplate() {
        // given
        String templated = "{ \"httpRequest\": { \"path\": \"/path\" }, \"httpResponse\": { \"statusCode\": 200 }," +
            " \"afterActions\": [ { \"httpRequestTemplate\": { \"templateType\": \"MUSTACHE\", \"template\": \"{ \\\"path\\\": \\\"/hook\\\" }\" } } ] }";
        String both = "{ \"httpRequest\": { \"path\": \"/path\" }, \"httpResponse\": { \"statusCode\": 200 }," +
            " \"afterActions\": [ { \"httpRequest\": { \"path\": \"/hook\" }, \"httpRequestTemplate\": { \"templateType\": \"MUSTACHE\", \"template\": \"x\" } } ] }";

        // when
        Expectation parsed = serializer.deserialize(templated);

        // then
        assertThat(parsed.getAfterActions().get(0).getHttpRequestTemplate().getTemplateType(), is(HttpTemplate.TemplateType.MUSTACHE));
        IllegalArgumentException invalid = org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(both));
        assertThat(invalid.getMessage(), containsString("afterActions"));
    }
}

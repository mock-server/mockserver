package org.mockserver.serialization.java;

import org.mockserver.mock.Expectation;
import org.mockserver.model.Action;
import org.mockserver.model.AnyOfRequestDefinition;
import org.mockserver.model.CaptureRule;
import org.mockserver.model.ConditionalRequestDefinition;
import org.mockserver.model.CrossProtocolScenario;
import org.mockserver.model.ExpectationStep;
import org.mockserver.model.HttpChaosProfile;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import org.mockserver.model.OpenAPIDefinition;
import org.mockserver.model.RateLimit;
import org.mockserver.model.RequestDefinition;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class ExpectationToJavaSerializer implements ToJavaSerializer<Expectation> {

    public static final int INDENT_SIZE = 8;

    private final boolean writeIds;

    public ExpectationToJavaSerializer() {
        this(true);
    }

    /**
     * @param writeIds false to leave out every expectation's id, as for recorded expectations, which have none
     *                 until something asks for one; true writes an id that has been set
     */
    public ExpectationToJavaSerializer(boolean writeIds) {
        this.writeIds = writeIds;
    }

    public String serialize(List<Expectation> expectations) {
        StringWriter writer = new StringWriter();
        serialize(expectations, writer);
        return writer.toString();
    }

    /**
     * As {@link #serialize(List)}, writing the code for one expectation at a time to {@code writer}, and
     * each body as it is escaped.
     */
    public void serialize(List<Expectation> expectations, Writer writer) {
        JavaCode output = new JavaCode(writer);
        for (Expectation expectation : expectations) {
            write(0, expectation, output);
            output.append(NEW_LINE);
            output.append(NEW_LINE);
        }
    }

    @Override
    public String serialize(int numberOfSpacesToIndent, Expectation expectation) {
        return JavaCode.toString(output -> write(numberOfSpacesToIndent, expectation, output));
    }

    private void write(int numberOfSpacesToIndent, Expectation expectation, JavaCode output) {
        if (expectation != null) {
            // when(...) takes one terminal action, and has none for forward-validate, a rate limit or steps alone
            int actionCount = generatedActionCount(expectation);
            boolean upsert = actionCount > 1
                || expectation.getHttpForwardValidateAction() != null
                || expectation.getRateLimit() != null
                || actionCount == 0 && expectation.getSteps() != null;
            int indent = upsert ? numberOfSpacesToIndent + 1 : numberOfSpacesToIndent;
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("new MockServerClient(\"localhost\", 1080)");
            if (upsert) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(".upsert(");
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append("new org.mockserver.mock.Expectation(");
            } else {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".when(");
            }
            String respond = upsert ? ".thenRespond(" : ".respond(";
            String forward = upsert ? ".thenForward(" : ".forward(";
            String error = upsert ? ".thenError(" : ".error(";
            String respondWith = upsert ? ".thenRespondWith" : ".respondWith";
            writeRequestDefinition(indent + 1, expectation.getHttpRequest(), output);
            output.append(",");
            if (expectation.getTimes() != null) {
                output.append(new TimesToJavaSerializer().serialize(indent + 1, expectation.getTimes()));
            } else {
                appendNewLineAndIndent((indent + 1) * INDENT_SIZE, output).append("null");
            }
            output.append(",");
            if (expectation.getTimeToLive() != null) {
                output.append(new TimeToLiveToJavaSerializer().serialize(indent + 1, expectation.getTimeToLive()));
            } else {
                appendNewLineAndIndent((indent + 1) * INDENT_SIZE, output).append("null");
            }
            output.append(",");
            appendNewLineAndIndent((indent + 1) * INDENT_SIZE, output).append(expectation.getPriority());
            appendNewLineAndIndent(indent * INDENT_SIZE, output).append(")");
            if (writeIds && expectation.hasId()) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".withId(").append(FluentJavaBuilder.literal(expectation.getId())).append(")");
            }
            if (expectation.getPercentage() != null) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".withPercentage(").append(expectation.getPercentage()).append(")");
            }
            if (isNotBlank(expectation.getNamespace())) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".withNamespace(\"").appendEscaped(expectation.getNamespace()).append("\")");
            }
            if (expectation.getMetadata() != null) {
                for (Map.Entry<String, Object> entry : expectation.getMetadata().entrySet()) {
                    appendNewLineAndIndent(indent * INDENT_SIZE, output).append(metadataToJava(entry.getKey(), entry.getValue()));
                }
            }
            if (isNotBlank(expectation.getScenarioName())) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".withScenarioName(\"").appendEscaped(expectation.getScenarioName()).append("\")");
            }
            if (isNotBlank(expectation.getScenarioState())) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".withScenarioState(\"").appendEscaped(expectation.getScenarioState()).append("\")");
            }
            if (isNotBlank(expectation.getNewScenarioState())) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".withNewScenarioState(\"").appendEscaped(expectation.getNewScenarioState()).append("\")");
            }
            if (expectation.getResponseMode() != null) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".withResponseMode(org.mockserver.mock.ResponseMode.").append(expectation.getResponseMode().name()).append(")");
            }
            if (expectation.getResponseWeights() != null && !expectation.getResponseWeights().isEmpty()) {
                StringBuilder weights = new StringBuilder();
                List<Integer> responseWeights = expectation.getResponseWeights();
                for (int i = 0; i < responseWeights.size(); i++) {
                    if (i > 0) {
                        weights.append(", ");
                    }
                    weights.append(responseWeights.get(i));
                }
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".withResponseWeights(java.util.Arrays.asList(").append(weights).append("))");
            }
            if (expectation.getSwitchAfter() != null) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(".withSwitchAfter(").append(expectation.getSwitchAfter()).append(")");
            }
            if (expectation.getChaos() != null) {
                appendCall(indent, ".withChaos(", serializeChaos(indent + 1, expectation.getChaos()), output);
            }
            if (expectation.getRateLimit() != null) {
                appendCall(indent, ".withRateLimit(", serializeRateLimit(indent + 1, expectation.getRateLimit()), output);
            }
            appendEach(indent, ".withCapture(", expectation.getCapture(), ExpectationToJavaSerializer::serializeCaptureRule, ")", output);
            appendEach(indent, ".withCrossProtocolScenarios(java.util.Arrays.asList(", expectation.getCrossProtocolScenarios(), ExpectationToJavaSerializer::serializeCrossProtocolScenario, "))", output);
            appendEach(indent, ".withSteps(", expectation.getSteps(), ExpectationToJavaSerializer::serializeStep, ")", output);
            appendEach(indent, ".withBeforeActions(", expectation.getBeforeActions(), new AfterActionToJavaSerializer(), ")", output);
            appendEach(indent, ".withAfterActions(", expectation.getAfterActions(), new AfterActionToJavaSerializer(), ")", output);
            if (expectation.getHttpResponses() != null && !expectation.getHttpResponses().isEmpty()) {
                HttpResponseToJavaSerializer responseSerializer = new HttpResponseToJavaSerializer();
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append(respond).append("java.util.Arrays.asList(");
                List<HttpResponse> responses = expectation.getHttpResponses();
                for (int i = 0; i < responses.size(); i++) {
                    appendNewLineAndIndent((indent + 1) * INDENT_SIZE, output);
                    responseSerializer.write(indent + 2, responses.get(i), output);
                    if (i < responses.size() - 1) {
                        output.append(",");
                    }
                }
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append("))");
            } else if (expectation.getHttpResponse() != null) {
                appendAction(indent, respond, code -> new HttpResponseToJavaSerializer().write(indent + 1, expectation.getHttpResponse(), code), expectation.getHttpResponse(), output);
            }
            if (expectation.getHttpResponseTemplate() != null) {
                appendAction(indent, respond, new HttpTemplateToJavaSerializer().serialize(indent + 1, expectation.getHttpResponseTemplate()), expectation.getHttpResponseTemplate(), output);
            }
            if (expectation.getHttpResponseClassCallback() != null) {
                appendAction(indent, respond, new HttpClassCallbackToJavaSerializer().serialize(indent + 1, expectation.getHttpResponseClassCallback()), expectation.getHttpResponseClassCallback(), output);
            }
            if (expectation.getHttpResponseObjectCallback() != null) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append("/*NOT POSSIBLE TO GENERATE CODE FOR OBJECT CALLBACK*/");
            }
            if (expectation.getHttpForward() != null) {
                appendAction(indent, forward, new HttpForwardToJavaSerializer().serialize(indent + 1, expectation.getHttpForward()), expectation.getHttpForward(), output);
            }
            if (expectation.getHttpOverrideForwardedRequest() != null) {
                appendAction(indent, forward, new HttpOverrideForwardedRequestToJavaSerializer().serialize(indent + 1, expectation.getHttpOverrideForwardedRequest()), expectation.getHttpOverrideForwardedRequest(), output);
            }
            if (expectation.getHttpForwardTemplate() != null) {
                appendAction(indent, forward, new HttpTemplateToJavaSerializer().serialize(indent + 1, expectation.getHttpForwardTemplate()), expectation.getHttpForwardTemplate(), output);
            }
            if (expectation.getHttpForwardClassCallback() != null) {
                appendAction(indent, forward, new HttpClassCallbackToJavaSerializer().serialize(indent + 1, expectation.getHttpForwardClassCallback()), expectation.getHttpForwardClassCallback(), output);
            }
            if (expectation.getHttpForwardObjectCallback() != null) {
                appendNewLineAndIndent(indent * INDENT_SIZE, output).append("/*NOT POSSIBLE TO GENERATE CODE FOR OBJECT CALLBACK*/");
            }
            if (expectation.getHttpForwardValidateAction() != null) {
                appendAction(indent, ".thenForwardValidate(", new HttpForwardValidateActionToJavaSerializer().serialize(indent + 1, expectation.getHttpForwardValidateAction()), expectation.getHttpForwardValidateAction(), output);
            }
            if (expectation.getHttpForwardWithFallback() != null) {
                appendAction(indent, upsert ? ".thenForwardWithFallback(" : ".forwardWithFallback(", new HttpForwardWithFallbackToJavaSerializer().serialize(indent + 1, expectation.getHttpForwardWithFallback()), expectation.getHttpForwardWithFallback(), output);
            }
            if (expectation.getHttpError() != null) {
                appendAction(indent, error, new HttpErrorToJavaSerializer().serialize(indent + 1, expectation.getHttpError()), expectation.getHttpError(), output);
            }
            if (expectation.getHttpSseResponse() != null) {
                appendAction(indent, respondWith + "Sse(", new HttpSseResponseToJavaSerializer().serialize(indent + 1, expectation.getHttpSseResponse()), expectation.getHttpSseResponse(), output);
            }
            if (expectation.getHttpLlmResponse() != null) {
                appendAction(indent, respondWith + "Llm(", new HttpLlmResponseToJavaSerializer().serialize(indent + 1, expectation.getHttpLlmResponse()), expectation.getHttpLlmResponse(), output);
            }
            if (expectation.getHttpWebSocketResponse() != null) {
                appendAction(indent, respondWith + "WebSocket(", new HttpWebSocketResponseToJavaSerializer().serialize(indent + 1, expectation.getHttpWebSocketResponse()), expectation.getHttpWebSocketResponse(), output);
            }
            if (expectation.getGrpcStreamResponse() != null) {
                appendAction(indent, respondWith + "GrpcStream(", new GrpcStreamResponseToJavaSerializer().serialize(indent + 1, expectation.getGrpcStreamResponse()), expectation.getGrpcStreamResponse(), output);
            }
            if (expectation.getGrpcBidiResponse() != null) {
                appendAction(indent, respondWith + "GrpcBidi(", new GrpcBidiResponseToJavaSerializer().serialize(indent + 1, expectation.getGrpcBidiResponse()), expectation.getGrpcBidiResponse(), output);
            }
            if (expectation.getBinaryResponse() != null) {
                appendAction(indent, respondWith + "Binary(", new BinaryResponseToJavaSerializer().serialize(indent + 1, expectation.getBinaryResponse()), expectation.getBinaryResponse(), output);
            }
            if (expectation.getDnsResponse() != null) {
                appendAction(indent, respondWith + "Dns(", new DnsResponseToJavaSerializer().serialize(indent + 1, expectation.getDnsResponse()), expectation.getDnsResponse(), output);
            }
            if (upsert) {
                appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
            }
            output.append(";");
        }
    }

    /**
     * The number of action calls the generated code makes. With more than one, the code builds an {@link Expectation}
     * and upserts it, since {@code when(...)} takes a single terminal action.
     */
    private static int generatedActionCount(Expectation expectation) {
        int count = expectation.getHttpResponses() != null && !expectation.getHttpResponses().isEmpty() || expectation.getHttpResponse() != null ? 1 : 0;
        for (Action<?> action : Arrays.asList(
            expectation.getHttpResponseTemplate(),
            expectation.getHttpResponseClassCallback(),
            expectation.getHttpForward(),
            expectation.getHttpOverrideForwardedRequest(),
            expectation.getHttpForwardTemplate(),
            expectation.getHttpForwardClassCallback(),
            expectation.getHttpForwardValidateAction(),
            expectation.getHttpForwardWithFallback(),
            expectation.getHttpError(),
            expectation.getHttpSseResponse(),
            expectation.getHttpLlmResponse(),
            expectation.getHttpWebSocketResponse(),
            expectation.getGrpcStreamResponse(),
            expectation.getGrpcBidiResponse(),
            expectation.getBinaryResponse(),
            expectation.getDnsResponse()
        )) {
            if (action != null) {
                count++;
            }
        }
        return count;
    }

    private static String serializeChaos(int numberOfSpacesToIndent, HttpChaosProfile chaos) {
        return new FluentJavaBuilder(numberOfSpacesToIndent, "HttpChaosProfile.httpChaosProfile()")
            .with("withErrorStatus", chaos.getErrorStatus())
            .with("withRetryAfter", chaos.getRetryAfter())
            .with("withErrorProbability", chaos.getErrorProbability())
            .with("withDropConnectionProbability", chaos.getDropConnectionProbability())
            .withDelay("withLatency", chaos.getLatency())
            .with("withSeed", chaos.getSeed())
            .with("withSucceedFirst", chaos.getSucceedFirst())
            .with("withFailRequestCount", chaos.getFailRequestCount())
            .with("withOutageAfterMillis", chaos.getOutageAfterMillis())
            .with("withOutageDurationMillis", chaos.getOutageDurationMillis())
            .with("withTruncateBodyAtFraction", chaos.getTruncateBodyAtFraction())
            .with("withMalformedBody", chaos.getMalformedBody())
            .with("withSlowResponseChunkSize", chaos.getSlowResponseChunkSize())
            .withDelay("withSlowResponseChunkDelay", chaos.getSlowResponseChunkDelay())
            .with("withQuotaName", chaos.getQuotaName())
            .with("withQuotaLimit", chaos.getQuotaLimit())
            .with("withQuotaWindowMillis", chaos.getQuotaWindowMillis())
            .with("withQuotaErrorStatus", chaos.getQuotaErrorStatus())
            .with("withDegradationRampMillis", chaos.getDegradationRampMillis())
            .with("withGraphqlErrors", chaos.getGraphqlErrors())
            .with("withGraphqlErrorMessage", chaos.getGraphqlErrorMessage())
            .with("withGraphqlErrorCode", chaos.getGraphqlErrorCode())
            .with("withGraphqlNullifyData", chaos.getGraphqlNullifyData())
            .build();
    }

    private static String serializeRateLimit(int numberOfSpacesToIndent, RateLimit rateLimit) {
        return new FluentJavaBuilder(numberOfSpacesToIndent, "RateLimit.rateLimit()")
            .with("withName", rateLimit.getName())
            .with("withAlgorithm", rateLimit.getAlgorithm())
            .with("withLimit", rateLimit.getLimit())
            .with("withWindowMillis", rateLimit.getWindowMillis())
            .with("withBurst", rateLimit.getBurst())
            .with("withRefillPerSecond", rateLimit.getRefillPerSecond())
            .with("withErrorStatus", rateLimit.getErrorStatus())
            .with("withRetryAfter", rateLimit.getRetryAfter())
            .build();
    }

    private static String serializeCaptureRule(int numberOfSpacesToIndent, CaptureRule captureRule) {
        return new FluentJavaBuilder(numberOfSpacesToIndent, "CaptureRule.captureRule()")
            .with("withSource", captureRule.getSource())
            .with("withExpression", captureRule.getExpression())
            .with("withInto", captureRule.getInto())
            .build();
    }

    private static String serializeCrossProtocolScenario(int numberOfSpacesToIndent, CrossProtocolScenario scenario) {
        return new FluentJavaBuilder(numberOfSpacesToIndent, "CrossProtocolScenario.crossProtocolScenario()")
            .with("withTrigger", scenario.getTrigger())
            .with("withScenarioName", scenario.getScenarioName())
            .with("withTargetState", scenario.getTargetState())
            .with("withMatchPattern", scenario.getMatchPattern())
            .build();
    }

    private static String serializeStep(int numberOfSpacesToIndent, ExpectationStep step) {
        return new FluentJavaBuilder(numberOfSpacesToIndent, "ExpectationStep.step()")
            .withObject("withHttpRequest", step.getHttpRequest(), new HttpRequestToJavaSerializer())
            .withObject("withHttpClassCallback", step.getHttpClassCallback(), new HttpClassCallbackToJavaSerializer())
            .comment(step.getHttpObjectCallback() != null, "NOT POSSIBLE TO GENERATE CODE FOR OBJECT CALLBACK")
            .withObject("withHttpForward", step.getHttpForward(), new HttpForwardToJavaSerializer())
            .withObject("withHttpOverrideForwardedRequest", step.getHttpOverrideForwardedRequest(), new HttpOverrideForwardedRequestToJavaSerializer())
            .withObject("withHttpResponse", step.getHttpResponse(), new HttpResponseToJavaSerializer())
            .withObject("withHttpError", step.getHttpError(), new HttpErrorToJavaSerializer())
            .with("withResponder", step.getResponder())
            .withDelay("withDelay", step.getDelay())
            .with("withBlocking", step.getBlocking())
            .withDelay("withTimeout", step.getTimeout())
            .with("withFailurePolicy", step.getFailurePolicy())
            .build();
    }

    private <T extends ObjectWithReflectiveEqualsHashCodeToString> void appendEach(int numberOfSpacesToIndent, String open, List<T> values, ToJavaSerializer<T> serializer, String close, JavaCode output) {
        if (values != null && !values.isEmpty()) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(open);
            for (int i = 0; i < values.size(); i++) {
                output.append(i > 0 ? "," : "").append(serializer.serialize(numberOfSpacesToIndent + 1, values.get(i)));
            }
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(close);
        }
    }

    private void appendCall(int numberOfSpacesToIndent, String method, String serializedArgument, JavaCode output) {
        appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(method).append(serializedArgument);
        appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
    }

    private void appendAction(int numberOfSpacesToIndent, String method, String serializedAction, Action<?> action, JavaCode output) {
        appendAction(numberOfSpacesToIndent, method, code -> code.append(serializedAction), action, output);
    }

    private void appendAction(int numberOfSpacesToIndent, String method, Consumer<JavaCode> serializedAction, Action<?> action, JavaCode output) {
        appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(method);
        serializedAction.accept(output);
        if (action.isPrimary()) {
            appendNewLineAndIndent((numberOfSpacesToIndent + 2) * INDENT_SIZE, output).append(".withPrimary(true)");
        }
        appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append(")");
    }

    private void writeRequestDefinition(int indent, RequestDefinition requestDefinition, JavaCode output) {
        if (requestDefinition instanceof HttpRequest) {
            new HttpRequestToJavaSerializer().write(indent, (HttpRequest) requestDefinition, output);
        } else if (requestDefinition instanceof OpenAPIDefinition) {
            output.append(new OpenAPIMatcherToJavaSerializer().serialize(indent, (OpenAPIDefinition) requestDefinition));
        } else if (requestDefinition instanceof AnyOfRequestDefinition) {
            AnyOfRequestDefinition anyOf = (AnyOfRequestDefinition) requestDefinition;
            appendNewLineAndIndent(indent * INDENT_SIZE, output)
                .append(anyOf.isNot() ? "org.mockserver.model.Not.not(" : "")
                .append("org.mockserver.model.AnyOfRequestDefinition.requestAnyOf(");
            List<RequestDefinition> branches = anyOf.getAnyOf();
            for (int i = 0; i < branches.size(); i++) {
                writeBranch(indent + 1, branches.get(i), output);
                if (i < branches.size() - 1) {
                    output.append(",");
                }
            }
            appendNewLineAndIndent(indent * INDENT_SIZE, output).append(anyOf.isNot() ? "))" : ")");
        } else if (requestDefinition instanceof ConditionalRequestDefinition) {
            ConditionalRequestDefinition conditional = (ConditionalRequestDefinition) requestDefinition;
            List<RequestDefinition> branches = new ArrayList<>(Arrays.asList(conditional.getIf(), conditional.getThen(), conditional.getElse()));
            while (branches.size() > 1 && branches.get(branches.size() - 1) == null) {
                branches.remove(branches.size() - 1);
            }
            appendNewLineAndIndent(indent * INDENT_SIZE, output)
                .append(conditional.isNot() ? "org.mockserver.model.Not.not(" : "")
                .append("org.mockserver.model.ConditionalRequestDefinition.requestIf(");
            for (int i = 0; i < branches.size(); i++) {
                writeBranch(indent + 1, branches.get(i), output);
                if (i < branches.size() - 1) {
                    output.append(",");
                }
            }
            appendNewLineAndIndent(indent * INDENT_SIZE, output).append(conditional.isNot() ? "))" : ")");
        }
    }

    private void writeBranch(int indent, RequestDefinition branch, JavaCode output) {
        if (branch instanceof HttpRequest || branch instanceof OpenAPIDefinition || branch instanceof AnyOfRequestDefinition || branch instanceof ConditionalRequestDefinition) {
            writeRequestDefinition(indent, branch, output);
        } else {
            // an absent branch, or one with no Java form, keeps the argument list compilable
            appendNewLineAndIndent(indent * INDENT_SIZE, output).append("null");
        }
    }

    private JavaCode appendNewLineAndIndent(int numberOfSpacesToIndent, JavaCode output) {
        return output.newLineAndIndent(numberOfSpacesToIndent);
    }

    private static String metadataToJava(String key, Object value) {
        if (value == null || value instanceof String || value instanceof Integer || value instanceof Long || value instanceof Double || value instanceof Boolean) {
            return ".withMetadata(" + FluentJavaBuilder.literal(key) + ", " + (value == null ? "(Object) null" : FluentJavaBuilder.literal(value)) + ")";
        }
        try {
            String json = org.mockserver.serialization.ObjectMapperFactory.createObjectMapper().writeValueAsString(value);
            return ".withMetadataJson(" + FluentJavaBuilder.literal(key) + ", " + FluentJavaBuilder.literal(json) + ")";
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("metadata value for \"" + key + "\" is not serialisable as JSON", e);
        }
    }
}

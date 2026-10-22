package org.mockserver.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.configuration.Configuration;
import org.mockserver.fixture.SensitiveValueMatcher;
import org.mockserver.matchers.AnyOfRequestMatcher;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.LlmConversationMatcher;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.matchers.MismatchRemediation;
import org.mockserver.model.HttpLlmResponse;
import org.mockserver.model.HttpRequest;

import java.util.List;
import java.util.Map;

/**
 * Re-evaluates a stored request against the current expectations, on demand, for the explain endpoints. Read-only:
 * it consumes no Times, moves no scenario and writes no match events.
 */
final class RequestMatchExplainer {

    // approximate denominator: see the note in HttpState.debugMismatch
    static final int TOTAL_FIELDS = MatchDifference.Field.values().length;

    private RequestMatchExplainer() {
    }

    /**
     * Compares {@code request} with one expectation field by field: the per-expectation node that
     * {@code explainUnmatched} returns. Differences and hints quoting the request are scrubbed by {@code sensitiveValues}.
     */
    static ObjectNode compareFields(ObjectMapper objectMapper, HttpRequestMatcher matcher, Expectation expectation, HttpRequest request, SensitiveValueMatcher sensitiveValues) {
        HttpRequest clonedRequest = request.clone();
        // collectAllDifferences: callers rank by differingFieldCount, which is only meaningful if every
        // differing field is counted; fail-fast would leave every mismatched expectation on a count of 1
        MatchDifference matchDifference = new MatchDifference(true, clonedRequest)
            .suppressMatchResultLogging()
            .collectAllDifferences();
        boolean matches = matcher.matches(matchDifference, clonedRequest);

        ObjectNode result = expectationSummary(objectMapper, expectation);
        result.put("matches", matches);

        Map<MatchDifference.Field, List<String>> allDifferences = matchDifference.getAllDifferences();
        int failures = matches ? 0 : allDifferences.size();
        result.put("matchedFieldCount", TOTAL_FIELDS - failures);
        result.put("totalFieldCount", TOTAL_FIELDS);
        result.put("differingFieldCount", failures);

        if (!matches && !allDifferences.isEmpty()) {
            ObjectNode differences = objectMapper.createObjectNode();
            for (Map.Entry<MatchDifference.Field, List<String>> diffEntry : allDifferences.entrySet()) {
                ArrayNode fieldDiffs = differences.putArray(diffEntry.getKey().getName());
                for (String diff : diffEntry.getValue()) {
                    fieldDiffs.add(sensitiveValues.scrub(diff));
                }
            }
            result.set("differences", differences);

            Map<MatchDifference.Field, String> hints = MismatchRemediation.allHints(allDifferences);
            if (!hints.isEmpty()) {
                ObjectNode remediationNode = objectMapper.createObjectNode();
                for (Map.Entry<MatchDifference.Field, String> hintEntry : hints.entrySet()) {
                    remediationNode.put(hintEntry.getKey().getName(), sensitiveValues.scrub(hintEntry.getValue()));
                }
                result.set("remediation", remediationNode);
            }
        }
        if (!matches) {
            addAnyOfBranches(objectMapper, result, matcher, clonedRequest);
        }
        return result;
    }

    /**
     * For an anyOf request matcher that did not match, adds which branch came closest and how many fields each
     * branch differed on, so a user can see which alternative to fix. The top-level differences are the closest
     * branch's.
     */
    static void addAnyOfBranches(ObjectMapper objectMapper, ObjectNode result, HttpRequestMatcher matcher, HttpRequest request) {
        if (!(matcher instanceof AnyOfRequestMatcher anyOfMatcher)) {
            return;
        }
        List<AnyOfRequestMatcher.BranchComparison> comparisons = anyOfMatcher.compareBranches(request.clone(), true);
        if (comparisons.isEmpty()) {
            return;
        }
        ObjectNode anyOf = objectMapper.createObjectNode();
        anyOf.put("closestBranch", AnyOfRequestMatcher.closestBranch(comparisons));
        ArrayNode branches = anyOf.putArray("branches");
        for (int i = 0; i < comparisons.size(); i++) {
            AnyOfRequestMatcher.BranchComparison comparison = comparisons.get(i);
            ObjectNode branch = branches.addObject();
            branch.put("branch", i);
            branch.put("matches", comparison.matches());
            branch.put("differingFieldCount", comparison.getDifferences().size());
            ArrayNode fields = branch.putArray("differingFields");
            for (MatchDifference.Field field : comparison.getDifferences().keySet()) {
                fields.add(field.getName());
            }
        }
        result.set("anyOf", anyOf);
    }

    private static ObjectNode expectationSummary(ObjectMapper objectMapper, Expectation expectation) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("expectationId", expectation.getId());
        if (expectation.getHttpRequest() instanceof HttpRequest) {
            HttpRequest expectationRequest = (HttpRequest) expectation.getHttpRequest();
            summary.put("expectationPath", expectationRequest.getPath() != null ? expectationRequest.getPath().getValue() : "");
            summary.put("expectationMethod", expectationRequest.getMethod() != null ? expectationRequest.getMethod().getValue() : "");
        }
        return summary;
    }

    /**
     * Walks the expectations in the order the serving path evaluates them and applies the same gates
     * (namespace, active, request fields, LLM conversation, scenario state, percentage), recording why each
     * expectation ahead of the one that would serve the request now was passed over, and which later
     * expectations also match but are shadowed by it. At most {@code maxEvaluations} expectations are evaluated.
     */
    static ObjectNode explain(ObjectMapper objectMapper, RequestMatchers requestMatchers, Configuration configuration, HttpRequest request, SensitiveValueMatcher sensitiveValues, int maxEvaluations) {
        List<HttpRequestMatcher> matchers = requestMatchers.sortedRequestMatchers();
        String requestNamespace = requestMatchers.extractRequestNamespace(request);

        ArrayNode skipped = objectMapper.createArrayNode();
        ArrayNode shadowed = objectMapper.createArrayNode();
        ObjectNode matched = null;
        Expectation winner = null;
        String closestExpectationId = null;
        int closestFailures = Integer.MAX_VALUE;
        int evaluated = 0;
        boolean truncated = false;
        int position = 0;

        for (HttpRequestMatcher matcher : matchers) {
            Expectation expectation = matcher.getExpectation();
            position++;
            if (expectation == null) {
                continue;
            }
            if (evaluated >= maxEvaluations) {
                truncated = true;
                break;
            }
            evaluated++;

            ObjectNode node;
            String reason = null;
            String explanation = null;
            if (!RequestMatchers.matchesNamespace(expectation, requestNamespace)) {
                node = expectationSummary(objectMapper, expectation);
                reason = "NAMESPACE";
                explanation = "it belongs to namespace '" + expectation.getNamespace() + "' but the request is in "
                    + (requestNamespace == null ? "no namespace" : "namespace '" + requestNamespace + "'");
            } else if (!expectation.isActive()) {
                node = expectationSummary(objectMapper, expectation);
                reason = "INACTIVE";
                explanation = "it is no longer active: its Times are used up or its time to live has expired";
            } else {
                node = compareFields(objectMapper, matcher, expectation, request, sensitiveValues);
                if (!node.path("matches").asBoolean()) {
                    List<String> differingFields = fieldNames(node);
                    if (differingFields.isEmpty() && expectation.getHttpRequest() != null && expectation.getHttpRequest().isNot()) {
                        // a negated matcher fails exactly when every inner field matches, so it is the farthest
                        // candidate, not the closest
                        reason = "NOT_OPERATOR";
                        explanation = "the request matches every field of its request matcher, which is wrapped in not(...), so it excludes this request";
                    } else {
                        reason = "FIELDS_DIFFER";
                        explanation = differingFields.isEmpty()
                            ? "the request does not satisfy its request matcher"
                            : "the request differs on: " + String.join(", ", differingFields);
                        int failures = node.path("differingFieldCount").asInt();
                        if (winner == null && failures < closestFailures) {
                            closestFailures = failures;
                            closestExpectationId = expectation.getId();
                        }
                    }
                } else if (!conversationMatches(expectation, request, configuration)) {
                    reason = "CONVERSATION";
                    explanation = "the request matches but its LLM conversation does not match the expectation's conversation predicates";
                } else if (!scenarioStateMatches(requestMatchers, expectation, request)) {
                    reason = "SCENARIO_STATE";
                    String isolationKey = requestMatchers.extractIsolationKey(expectation, request);
                    explanation = "it requires scenario '" + expectation.getScenarioName() + "' to be in state '" + expectation.getScenarioState()
                        + "' but the scenario is now in state '" + requestMatchers.getScenarioManager().getState(expectation.getScenarioName(), isolationKey) + "'";
                } else if (expectation.getPercentage() != null && expectation.getPercentage() < 100) {
                    reason = "PERCENTAGE";
                    explanation = expectation.getPercentage() <= 0
                        ? "the request matches but its percentage is 0, so it is never served"
                        : "the request matches but it is served for only " + expectation.getPercentage() + "% of matching requests, so it may or may not have been chosen";
                }
            }
            node.put("position", position);
            node.put("priority", expectation.getPriority());

            if (winner == null) {
                if (reason == null) {
                    winner = expectation;
                    matched = node;
                } else {
                    node.put("reason", reason);
                    node.put("explanation", sensitiveValues.scrub(explanation));
                    skipped.add(node);
                }
            } else if (reason == null || "PERCENTAGE".equals(reason)) {
                node.put("reason", "LOWER_PRIORITY");
                node.put("explanation", "it also matches the request, but expectation '" + winner.getId() + "' is evaluated first because "
                    + (winner.getPriority() > expectation.getPriority()
                    ? "it has a higher priority (" + winner.getPriority() + " > " + expectation.getPriority() + ")"
                    : "it has the same priority and was created earlier"));
                shadowed.add(node);
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        if (matched != null) {
            result.set("matchedExpectation", matched);
        } else if (closestExpectationId != null) {
            result.put("closestExpectationId", closestExpectationId);
        }
        result.set("skippedExpectations", skipped);
        result.set("shadowedExpectations", shadowed);
        result.put("totalExpectations", matchers.size());
        result.put("evaluatedExpectations", evaluated);
        result.put("truncated", truncated);
        if (truncated) {
            result.put("maxExpectationsEvaluated", maxEvaluations);
        }
        return result;
    }

    private static List<String> fieldNames(ObjectNode comparison) {
        List<String> names = new java.util.ArrayList<>();
        comparison.path("differences").fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static boolean conversationMatches(Expectation expectation, HttpRequest request, Configuration configuration) {
        HttpLlmResponse llmResponse = expectation.getHttpLlmResponse();
        if (llmResponse == null) {
            return true;
        }
        LlmConversationMatcher conversationMatcher = llmResponse.getConversationMatcher();
        return conversationMatcher == null || !conversationMatcher.hasPredicates() || conversationMatcher.matches(request, configuration);
    }

    private static boolean scenarioStateMatches(RequestMatchers requestMatchers, Expectation expectation, HttpRequest request) {
        if (expectation.getScenarioName() == null || expectation.getScenarioState() == null) {
            return true;
        }
        String isolationKey = requestMatchers.extractIsolationKey(expectation, request);
        return requestMatchers.getScenarioManager().matchesState(expectation.getScenarioName(), isolationKey, expectation.getScenarioState());
    }
}

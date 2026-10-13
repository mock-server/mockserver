package org.mockserver.log;

import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.HttpResponseMatcher;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.LogEventRequestAndResponse;
import org.mockserver.model.NottableString;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.Disposition;
import org.mockserver.verify.VerificationTimes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;

/**
 * Builds the {@code verification summary:} block appended to a failed verification when
 * {@code detailedVerificationFailures} is on: the counts (or, for a sequence, the step that failed),
 * the closest non-matching recorded requests or responses, and how many arrived after the
 * verification started. The block is only ever appended after the existing message, which clients
 * parse by prefix, and it names only methods, paths, status codes and field names.
 */
final class VerificationFailureSummary {

    static final String HEADING = "verification summary:";
    static final int MAX_CLOSEST_REQUESTS = 3;
    static final int MAX_REQUESTS_EVALUATED = 500;
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    record RecordedRequest(RequestDefinition request, long arrivedAtMillis) {
    }

    record RecordedExchange(LogEventRequestAndResponse exchange, long arrivedAtMillis) {
    }

    private record Candidate(String description, List<String> differingFields, int recency) {
    }

    private VerificationFailureSummary() {
    }

    /**
     * @return the block to append, or "" when it would add nothing beyond the count already in the
     * message: no non-matching request to show, no request with the other disposition, nothing
     * arrived late, and the verification did not wait
     */
    static String build(HttpRequestMatcher verificationMatcher,
                        VerificationTimes times,
                        int matchedCount,
                        int remoteMatchedCount,
                        Disposition disposition,
                        int otherDispositionCount,
                        List<RecordedRequest> recorded,
                        long verificationStartedAtMillis,
                        boolean waited) {
        // a "found too often" failure is about the requests that matched, so near misses are noise there
        boolean foundTooOften = MockServerEventLog.foundTooOften(times, matchedCount);
        int evaluated = foundTooOften ? 0 : Math.min(recorded.size(), MAX_REQUESTS_EVALUATED);
        List<Candidate> closest = closest(recorded.size(), 0, evaluated,
            index -> requestDifferences(verificationMatcher, recorded.get(index).request()),
            index -> describe(recorded.get(index).request()));
        long arrivedAfterStart = recorded.stream().filter(r -> r.arrivedAtMillis() > verificationStartedAtMillis).count();
        if (closest.isEmpty() && otherDispositionCount == 0 && arrivedAfterStart == 0 && !waited) {
            return "";
        }
        StringBuilder builder = heading();
        builder.append("  expected ").append(times).append(" but matched ").append(plural(matchedCount, "time"));
        if (remoteMatchedCount > 0) {
            builder.append(" (including ").append(remoteMatchedCount).append(" on other cluster nodes)");
        }
        builder.append(" among ").append(plural(recorded.size(), "recorded request")).append(NEW_LINE);
        if (disposition != null && otherDispositionCount > 0) {
            String wanted = disposition == Disposition.FORWARDED ? "forwarded" : "mocked";
            String other = disposition == Disposition.FORWARDED ? "mocked" : "forwarded";
            builder.append("  ").append(plural(otherDispositionCount, "recorded request")).append(" matched but ")
                .append(otherDispositionCount == 1 ? "was " : "were ").append(other).append(", not ").append(wanted).append(NEW_LINE);
        }
        appendClosest(builder, "closest non-matching requests (fewest differing fields first):", closest);
        if (!foundTooOften && evaluated < recorded.size()) {
            builder.append("  closest requests ranked from the ").append(evaluated).append(" most recent of ")
                .append(recorded.size()).append(" recorded requests").append(NEW_LINE);
        }
        appendArrivals(builder, arrivedAfterStart, waited, "request");
        return builder.toString();
    }

    /**
     * Response-aware count verification: ranks the recorded responses (already narrowed by any request
     * matcher) that did not match, comparing the raw responses and describing the display copies.
     */
    static String buildForResponses(HttpResponseMatcher responseMatcher,
                                    VerificationTimes times,
                                    int matchedCount,
                                    List<RecordedExchange> recorded,
                                    List<LogEventRequestAndResponse> display,
                                    long verificationStartedAtMillis,
                                    boolean waited) {
        boolean foundTooOften = MockServerEventLog.foundTooOften(times, matchedCount);
        int evaluated = foundTooOften ? 0 : Math.min(recorded.size(), MAX_REQUESTS_EVALUATED);
        List<Candidate> closest = closest(recorded.size(), 0, evaluated,
            index -> responseDifferences(responseMatcher, recorded.get(index).exchange()),
            index -> describe(display.get(index)));
        long arrivedAfterStart = arrivedAfter(recorded, verificationStartedAtMillis);
        if (closest.isEmpty() && arrivedAfterStart == 0 && !waited) {
            return "";
        }
        StringBuilder builder = heading();
        builder.append("  expected ").append(times).append(" but matched ").append(plural(matchedCount, "time"))
            .append(" among ").append(plural(recorded.size(), "recorded response")).append(NEW_LINE);
        appendClosest(builder, "closest non-matching responses (fewest differing fields first):", closest);
        if (!foundTooOften && evaluated < recorded.size()) {
            builder.append("  closest responses ranked from the ").append(evaluated).append(" most recent of ")
                .append(recorded.size()).append(" recorded responses").append(NEW_LINE);
        }
        appendArrivals(builder, arrivedAfterStart, waited, "response");
        return builder.toString();
    }

    /**
     * Request sequence: names the step that failed and ranks the recorded requests it could have
     * matched (those after the previous step's match), and counts the earlier requests that matched it.
     *
     * @param stepMatcher  the failed step's matcher, or null when its near misses cannot be ranked
     * @param searchedFrom index of the first recorded request the failed step was checked against
     */
    static String buildForRequestSequence(int stepNumber,
                                          int stepCount,
                                          int previousStepNumber,
                                          HttpRequestMatcher stepMatcher,
                                          List<RecordedRequest> recorded,
                                          int searchedFrom,
                                          long verificationStartedAtMillis,
                                          boolean waited) {
        int size = recorded.size();
        // without a matcher nothing is checked, so there is no window to report
        int windowStart = stepMatcher == null ? 0 : Math.max(0, size - MAX_REQUESTS_EVALUATED);
        List<Candidate> closest = Collections.emptyList();
        int matchedBefore = 0;
        if (stepMatcher != null) {
            IntFunction<List<String>> differences = index -> requestDifferences(stepMatcher, recorded.get(index).request());
            closest = closest(size, searchedFrom, size - windowStart, differences, index -> describe(recorded.get(index).request()));
            matchedBefore = countMatchedBefore(windowStart, searchedFrom, index -> differences.apply(index) == null);
        }
        return sequenceBlock(stepNumber, stepCount, previousStepNumber, "request", size, searchedFrom, matchedBefore,
            closest, windowStart, arrivedAfter(recorded.stream().map(RecordedRequest::arrivedAtMillis), verificationStartedAtMillis), waited, true);
    }

    /**
     * Response-aware sequence: as {@link #buildForRequestSequence}, over recorded request-response pairs,
     * comparing the raw pairs and describing the display copies.
     *
     * @param rankable false when the step's request cannot be judged here (not an HttpRequest), so only
     *                 the step line is given: judging such a pair on its response alone would be wrong
     */
    static String buildForResponseSequence(int stepNumber,
                                           int stepCount,
                                           int previousStepNumber,
                                           boolean rankable,
                                           HttpRequestMatcher stepRequestMatcher,
                                           HttpResponseMatcher stepResponseMatcher,
                                           List<RecordedExchange> recorded,
                                           List<LogEventRequestAndResponse> display,
                                           int searchedFrom,
                                           long verificationStartedAtMillis,
                                           boolean waited) {
        int size = recorded.size();
        int windowStart = rankable ? Math.max(0, size - MAX_REQUESTS_EVALUATED) : 0;
        List<Candidate> closest = Collections.emptyList();
        int matchedBefore = 0;
        if (rankable) {
            IntFunction<List<String>> differences = index -> exchangeDifferences(stepRequestMatcher, stepResponseMatcher, recorded.get(index).exchange());
            closest = closest(size, searchedFrom, size - windowStart, differences, index -> describe(display.get(index)));
            matchedBefore = countMatchedBefore(windowStart, searchedFrom, index -> differences.apply(index) == null);
        }
        return sequenceBlock(stepNumber, stepCount, previousStepNumber, "response", size, searchedFrom, matchedBefore,
            closest, windowStart, arrivedAfter(recorded, verificationStartedAtMillis), waited, true);
    }

    /**
     * Expectation-id sequence: names the step that failed and counts the earlier requests for its
     * expectation, as {@link #buildForRequestSequence} does. Steps match by id, so there are no fields
     * to rank, and the requests carry no arrival time.
     */
    static String buildForExpectationIdSequence(int stepNumber,
                                                int stepCount,
                                                int previousStepNumber,
                                                int recordedCount,
                                                int searchedFrom,
                                                IntPredicate matchesStep) {
        int windowStart = Math.max(0, recordedCount - MAX_REQUESTS_EVALUATED);
        return sequenceBlock(stepNumber, stepCount, previousStepNumber, "request", recordedCount, searchedFrom,
            countMatchedBefore(windowStart, searchedFrom, matchesStep), Collections.emptyList(), windowStart, 0, false, false);
    }

    /**
     * Counts the items in the evaluation window before the previous step's match (at
     * {@code searchedFrom - 1}, which is itself excluded) that match the failed step.
     */
    private static int countMatchedBefore(int windowStart, int searchedFrom, IntPredicate matchesStep) {
        int matchedBefore = 0;
        for (int index = windowStart; index < searchedFrom - 1; index++) {
            if (matchesStep.test(index)) {
                matchedBefore++;
            }
        }
        return matchedBefore;
    }

    private static String sequenceBlock(int stepNumber, int stepCount, int previousStepNumber, String noun, int size, int searchedFrom,
                                        int matchedBefore, List<Candidate> closest, int windowStart, long arrivedAfterStart, boolean waited,
                                        boolean reportArrivals) {
        StringBuilder builder = heading();
        builder.append("  step ").append(stepNumber).append(" of ").append(stepCount).append(" not found in ");
        if (previousStepNumber > 0) {
            builder.append("the ").append(plural(size - searchedFrom, "recorded " + noun)).append(" after step ").append(previousStepNumber)
                .append(" matched recorded ").append(noun).append(" ").append(searchedFrom).append(" of ").append(size);
        } else {
            builder.append(plural(size, "recorded " + noun));
        }
        builder.append(NEW_LINE);
        if (matchedBefore > 0) {
            builder.append("  ").append(plural(matchedBefore, "recorded " + noun)).append(" matched step ").append(stepNumber)
                .append(" but came before step ").append(previousStepNumber).append("'s match").append(NEW_LINE);
        }
        appendClosest(builder, "closest non-matching " + noun + "s for step " + stepNumber + " (fewest differing fields first):", closest);
        if (windowStart > 0) {
            builder.append("  only the ").append(size - windowStart).append(" most recent of ").append(size)
                .append(" recorded ").append(noun).append("s were checked").append(NEW_LINE);
        }
        if (reportArrivals) {
            appendArrivals(builder, arrivedAfterStart, waited, noun);
        }
        return builder.toString();
    }

    private static StringBuilder heading() {
        return new StringBuilder().append(NEW_LINE).append(NEW_LINE).append(HEADING).append(NEW_LINE);
    }

    private static void appendClosest(StringBuilder builder, String title, List<Candidate> closest) {
        if (!closest.isEmpty()) {
            builder.append("  ").append(title).append(NEW_LINE);
            for (int i = 0; i < closest.size(); i++) {
                Candidate candidate = closest.get(i);
                builder.append("    ").append(i + 1).append(". ").append(candidate.description())
                    .append(" differs in: ").append(String.join(", ", candidate.differingFields())).append(NEW_LINE);
            }
        }
    }

    private static void appendArrivals(StringBuilder builder, long arrivedAfterStart, boolean waited, String noun) {
        if (arrivedAfterStart > 0 || waited) {
            builder.append("  ").append(plural(arrivedAfterStart, noun)).append(" arrived after verification started").append(NEW_LINE);
        }
    }

    private static long arrivedAfter(List<RecordedExchange> recorded, long verificationStartedAtMillis) {
        return arrivedAfter(recorded.stream().map(RecordedExchange::arrivedAtMillis), verificationStartedAtMillis);
    }

    private static long arrivedAfter(Stream<Long> arrivals, long verificationStartedAtMillis) {
        return arrivals.filter(arrivedAtMillis -> arrivedAtMillis > verificationStartedAtMillis).count();
    }

    /**
     * Ranks the most recent {@code evaluated} items at or after {@code fromIndex}; an item whose
     * differences are null (it matched) or empty (nothing recorded to show) is skipped.
     */
    private static List<Candidate> closest(int size, int fromIndex, int evaluated, IntFunction<List<String>> differingFields, IntFunction<String> describe) {
        List<Candidate> candidates = new ArrayList<>();
        int lowestIndex = Math.max(fromIndex, size - evaluated);
        for (int index = size - 1; index >= lowestIndex; index--) {
            List<String> fields = differingFields.apply(index);
            if (fields != null && !fields.isEmpty()) {
                candidates.add(new Candidate(describe.apply(index), fields, size - 1 - index));
            }
        }
        return candidates.stream()
            .sorted(Comparator.<Candidate>comparingInt(c -> c.differingFields().size()).thenComparingInt(Candidate::recency))
            .limit(MAX_CLOSEST_REQUESTS)
            .collect(Collectors.toList());
    }

    /**
     * @return null when the request matches, otherwise the names of the fields that differ
     * (empty when it is not an HttpRequest or no field difference was recorded)
     */
    private static List<String> requestDifferences(HttpRequestMatcher matcher, RequestDefinition requestDefinition) {
        if (!(requestDefinition instanceof HttpRequest)) {
            return Collections.emptyList();
        }
        MatchDifference difference = new MatchDifference(true, requestDefinition)
            .collectAllDifferences()
            .suppressMatchResultLogging();
        if (matcher.matches(difference, requestDefinition)) {
            return null;
        }
        return fieldNames(difference.getAllDifferences());
    }

    private static List<String> responseDifferences(HttpResponseMatcher matcher, LogEventRequestAndResponse exchange) {
        RequestDefinition diffRequest = exchange.getHttpRequest() != null ? exchange.getHttpRequest() : request();
        MatchDifference difference = new MatchDifference(true, diffRequest)
            .collectAllDifferences()
            .suppressMatchResultLogging();
        if (matcher.matches(difference, exchange.getHttpResponse())) {
            return null;
        }
        return responseFieldNames(difference.getAllDifferences());
    }

    private static List<String> exchangeDifferences(HttpRequestMatcher requestMatcher, HttpResponseMatcher responseMatcher, LogEventRequestAndResponse exchange) {
        List<String> requestFields = null;
        if (requestMatcher != null) {
            requestFields = exchange.getHttpRequest() == null ? Collections.emptyList() : requestDifferences(requestMatcher, exchange.getHttpRequest());
        }
        List<String> responseFields = responseMatcher == null ? null : responseDifferences(responseMatcher, exchange);
        if (requestFields == null && responseFields == null) {
            return null;
        }
        List<String> fields = new ArrayList<>();
        if (requestFields != null) {
            fields.addAll(requestFields);
        }
        if (responseFields != null) {
            fields.addAll(responseFields);
        }
        return fields;
    }

    private static List<String> fieldNames(Map<MatchDifference.Field, List<String>> differences) {
        List<String> fields = new ArrayList<>();
        for (MatchDifference.Field field : MatchDifference.Field.values()) {
            if (differences.containsKey(field)) {
                fields.add(field.getName());
            }
        }
        return fields;
    }

    /**
     * A response's status code and reason phrase are both recorded under OPERATION, named "status"
     * and listed first, as a response is read.
     */
    private static List<String> responseFieldNames(Map<MatchDifference.Field, List<String>> differences) {
        List<String> fields = new ArrayList<>();
        if (differences.containsKey(MatchDifference.Field.OPERATION)) {
            fields.add("response status");
        }
        for (MatchDifference.Field field : MatchDifference.Field.values()) {
            if (field != MatchDifference.Field.OPERATION && differences.containsKey(field)) {
                fields.add("response " + field.getName());
            }
        }
        return fields;
    }

    private static String describe(LogEventRequestAndResponse exchange) {
        HttpResponse response = exchange.getHttpResponse();
        String status = response == null || response.getStatusCode() == null ? "no status" : String.valueOf(response.getStatusCode());
        String request = exchange.getHttpRequest() == null ? "" : describe(exchange.getHttpRequest()) + " ";
        return request + "returned " + status;
    }

    private static String describe(RequestDefinition requestDefinition) {
        if (!(requestDefinition instanceof HttpRequest)) {
            return "(not an HTTP request)";
        }
        HttpRequest request = (HttpRequest) requestDefinition;
        String method = valueOf(request.getMethod());
        String path = valueOf(request.getPath());
        return bounded((method.isEmpty() ? "" : method + " ") + (path.isEmpty() ? "(no path)" : path));
    }

    private static String bounded(String description) {
        String singleLine = description.replace("\n", " ").replace("\r", "");
        return singleLine.length() > MAX_DESCRIPTION_LENGTH ? singleLine.substring(0, MAX_DESCRIPTION_LENGTH) + "...[truncated]" : singleLine;
    }

    private static String valueOf(NottableString nottableString) {
        return nottableString == null || nottableString.getValue() == null ? "" : nottableString.getValue();
    }

    private static String plural(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}

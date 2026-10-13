package org.mockserver.log;

import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;
import org.mockserver.model.RequestDefinition;
import org.mockserver.verify.VerificationTimes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * Builds the {@code verification summary:} block appended to a failed request-count verification
 * when {@code detailedVerificationFailures} is on: the expected and matched counts, the closest
 * non-matching recorded requests, and how many requests arrived after the verification started.
 * The block is only ever appended after the existing message, which clients parse by prefix.
 */
final class VerificationFailureSummary {

    static final String HEADING = "verification summary:";
    static final int MAX_CLOSEST_REQUESTS = 3;
    static final int MAX_REQUESTS_EVALUATED = 500;
    private static final int MAX_PATH_LENGTH = 200;

    record RecordedRequest(RequestDefinition request, long arrivedAtMillis) {
    }

    private record Candidate(HttpRequest request, List<String> differingFields, int recency) {
    }

    private VerificationFailureSummary() {
    }

    /**
     * @return the block to append, or "" when it would add nothing beyond the count already in the
     * message: no non-matching request to show, nothing arrived late, and the verification did not wait
     */
    static String build(HttpRequestMatcher verificationMatcher,
                        VerificationTimes times,
                        int matchedCount,
                        int remoteMatchedCount,
                        List<RecordedRequest> recorded,
                        long verificationStartedAtMillis,
                        boolean waited) {
        // a "found too often" failure is about the requests that matched, so near misses are noise there
        boolean foundTooOften = MockServerEventLog.foundTooOften(times, matchedCount);
        int evaluated = foundTooOften ? 0 : Math.min(recorded.size(), MAX_REQUESTS_EVALUATED);
        List<Candidate> closest = closestNonMatching(verificationMatcher, recorded, evaluated);
        long arrivedAfterStart = recorded.stream().filter(r -> r.arrivedAtMillis() > verificationStartedAtMillis).count();
        if (closest.isEmpty() && arrivedAfterStart == 0 && !waited) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE).append(NEW_LINE).append(HEADING).append(NEW_LINE);
        builder.append("  expected ").append(times).append(" but matched ").append(plural(matchedCount, "time"));
        if (remoteMatchedCount > 0) {
            builder.append(" (including ").append(remoteMatchedCount).append(" on other cluster nodes)");
        }
        builder.append(" among ").append(plural(recorded.size(), "recorded request")).append(NEW_LINE);
        if (!closest.isEmpty()) {
            builder.append("  closest non-matching requests (fewest differing fields first):").append(NEW_LINE);
            for (int i = 0; i < closest.size(); i++) {
                Candidate candidate = closest.get(i);
                builder.append("    ").append(i + 1).append(". ").append(describe(candidate.request()))
                    .append(" differs in: ").append(String.join(", ", candidate.differingFields())).append(NEW_LINE);
            }
        }
        if (!foundTooOften && evaluated < recorded.size()) {
            builder.append("  closest requests ranked from the ").append(evaluated).append(" most recent of ")
                .append(recorded.size()).append(" recorded requests").append(NEW_LINE);
        }
        if (arrivedAfterStart > 0 || waited) {
            builder.append("  ").append(plural(arrivedAfterStart, "request")).append(" arrived after verification started").append(NEW_LINE);
        }
        return builder.toString();
    }

    private static List<Candidate> closestNonMatching(HttpRequestMatcher verificationMatcher, List<RecordedRequest> recorded, int evaluated) {
        List<Candidate> candidates = new ArrayList<>();
        for (int recency = 0; recency < evaluated; recency++) {
            RequestDefinition requestDefinition = recorded.get(recorded.size() - 1 - recency).request();
            if (!(requestDefinition instanceof HttpRequest)) {
                continue;
            }
            HttpRequest request = (HttpRequest) requestDefinition;
            MatchDifference difference = new MatchDifference(true, request)
                .collectAllDifferences()
                .suppressMatchResultLogging();
            if (verificationMatcher.matches(difference, request)) {
                continue;
            }
            Map<MatchDifference.Field, List<String>> differences = difference.getAllDifferences();
            if (differences.isEmpty()) {
                continue;
            }
            List<String> fields = new ArrayList<>();
            for (MatchDifference.Field field : MatchDifference.Field.values()) {
                if (differences.containsKey(field)) {
                    fields.add(field.getName());
                }
            }
            candidates.add(new Candidate(request, fields, recency));
        }
        return candidates.stream()
            .sorted(Comparator.<Candidate>comparingInt(c -> c.differingFields().size()).thenComparingInt(Candidate::recency))
            .limit(MAX_CLOSEST_REQUESTS)
            .collect(Collectors.toList());
    }

    private static String describe(HttpRequest request) {
        String method = valueOf(request.getMethod());
        String path = valueOf(request.getPath());
        String description = (method.isEmpty() ? "" : method + " ") + (path.isEmpty() ? "(no path)" : path);
        String singleLine = description.replace("\n", " ").replace("\r", "");
        return singleLine.length() > MAX_PATH_LENGTH ? singleLine.substring(0, MAX_PATH_LENGTH) + "...[truncated]" : singleLine;
    }

    private static String valueOf(NottableString nottableString) {
        return nottableString == null || nottableString.getValue() == null ? "" : nottableString.getValue();
    }

    private static String plural(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}

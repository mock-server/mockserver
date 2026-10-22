package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.LlmConversationMatcher;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.Body;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.KeyAndValue;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.KeysAndValues;
import org.mockserver.model.KeysToMultiValues;
import org.mockserver.model.NottableMatcherString;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ExpectationSerializer;
import org.slf4j.event.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Advisory checks for expectations that can never match, or will not match the way they read. Lint never rejects or
 * changes an expectation. The per-expectation checks are cheap and run on every {@code PUT /mockserver/expectation};
 * the pairwise shadowing check runs only on demand ({@code PUT /mockserver/lint}, {@code mockserver lint}) and once per
 * initialisation file load, within a comparison budget, so a large expectation set reports truncation instead of
 * taking unbounded time.
 */
public final class ExpectationLinter {

    public static final String LINT_WARNINGS_HEADER = "x-mockserver-lint-warnings";
    static final int MAX_PAIR_COMPARISONS = 100_000;
    static final int MAX_REGEX_STEPS = 2_000_000;
    static final int MAX_ISSUES = 1_000;
    static final int MAX_LOGGED_ISSUES = 20;
    private static final int MAX_REGEX_STEPS_PER_MATCH = 100_000;
    private static final int MAX_PATTERN_LENGTH = 8_192;
    private static final int MAX_MESSAGE_LENGTH = 4_096;
    private static final String NOTE = "advisory only: lint never rejects or changes an expectation";

    private static final Pattern PATH_TEMPLATE = Pattern.compile("/\\{[A-Za-z_][\\w.\\-]*}(?=/|\\.|$)");
    // a ? that is not a regex group construct such as (?= or (?<name>, followed later by =
    private static final Pattern QUERY_STRING = Pattern.compile("(?<!\\()\\?[^?]*=");
    private static final Pattern HOST_AND_PORT = Pattern.compile("[^/]+:\\d+");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_\\-]+");
    private static final Set<String> KNOWN_METHODS = new LinkedHashSet<>(Arrays.asList(
        "GET", "HEAD", "POST", "PUT", "DELETE", "CONNECT", "OPTIONS", "TRACE", "PATCH", "QUERY", "PRI",
        "PROPFIND", "PROPPATCH", "MKCOL", "COPY", "MOVE", "LOCK", "UNLOCK", "LINK", "UNLINK", "PURGE",
        "SEARCH", "REPORT", "BIND", "UNBIND", "REBIND", "ACL", "LABEL", "MERGE", "UPDATE", "CHECKIN", "CHECKOUT",
        "UNCHECKOUT", "MKACTIVITY", "MKWORKSPACE", "MKCALENDAR", "MKREDIRECTREF", "UPDATEREDIRECTREF", "ORDERPATCH",
        "NOTIFY", "SUBSCRIBE", "UNSUBSCRIBE", "M-SEARCH", "BATCH", "FETCH"
    ));
    private static final Set<String> KNOWN_HEADER_NAMES = knownHeaderNames();

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public enum Rule {
        INVALID_EXPECTATION(Severity.ERROR),
        DUPLICATE_ID(Severity.WARNING),
        SHADOWED(Severity.WARNING),
        TIMES_ZERO(Severity.WARNING),
        TIME_TO_LIVE_ZERO(Severity.WARNING),
        PERCENTAGE_ZERO(Severity.WARNING),
        UNREACHABLE_RESPONSES(Severity.WARNING),
        PATH_MISSING_LEADING_SLASH(Severity.WARNING),
        PATH_INCLUDES_SCHEME_AND_HOST(Severity.WARNING),
        PATH_INCLUDES_QUERY_STRING(Severity.WARNING),
        PATH_GLOB_WILDCARD(Severity.WARNING),
        PATH_TEMPLATE_WITHOUT_PATH_PARAMETERS(Severity.WARNING),
        METHOD_TYPO(Severity.WARNING),
        HEADER_NAME_TYPO(Severity.WARNING),
        STRICT_JSON_BODY_WITH_ARRAYS(Severity.INFO);

        private final Severity severity;

        Rule(Severity severity) {
            this.severity = severity;
        }

        public Severity getSeverity() {
            return severity;
        }
    }

    static final class Subject {
        final Expectation expectation;
        final int order;
        final int servingPosition;
        final String reportedId;
        final String summary;

        Subject(Expectation expectation, int order, String reportedId, String summary) {
            this(expectation, order, order, reportedId, summary);
        }

        Subject(Expectation expectation, int order, int servingPosition, String reportedId, String summary) {
            this.expectation = expectation;
            this.order = order;
            this.servingPosition = servingPosition;
            this.reportedId = reportedId;
            this.summary = summary;
        }

        String describe() {
            return reportedId != null ? "'" + reportedId + "' (" + summary + ")" : summary;
        }
    }

    static final class Issue {
        final Rule rule;
        final String field;
        final String message;
        final Subject subject;
        final Subject related;

        Issue(Rule rule, String field, String message, Subject subject, Subject related) {
            this.rule = rule;
            this.field = field;
            this.message = message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) + "..." : message;
            this.subject = subject;
            this.related = related;
        }
    }

    static final class Report {
        final List<Issue> issues = new ArrayList<>();
        int expectationsChecked;
        int skipped;
        long pairComparisons;
        boolean shadowCheckTruncated;
        boolean issuesTruncated;
    }

    private ExpectationLinter() {
    }

    // ---- entry points ----

    /**
     * Lints expectations supplied as JSON (one expectation or an array), without storing them. Entries that are not
     * valid expectations are reported as {@code INVALID_EXPECTATION}; OpenAPI entries are skipped, not expanded, so
     * linting never fetches a specification.
     */
    public static ObjectNode lintJson(ObjectMapper objectMapper, ExpectationSerializer expectationSerializer, String json) throws java.io.IOException {
        JsonNode root = objectMapper.readTree(json);
        List<JsonNode> entries = new ArrayList<>();
        if (root != null && root.isArray()) {
            root.forEach(entries::add);
        } else if (root != null && root.isObject()) {
            entries.add(root);
        } else {
            throw new IllegalArgumentException("expected an expectation or an array of expectations");
        }
        return lintJsonEntries(objectMapper, expectationSerializer, entries);
    }

    /**
     * As {@link #lintJson}, for entries already parsed; each issue's {@code index} is the entry's position in {@code entries}.
     */
    public static ObjectNode lintJsonEntries(ObjectMapper objectMapper, ExpectationSerializer expectationSerializer, List<JsonNode> entries) {
        return lintEntries(objectMapper, expectationSerializer, entries, null);
    }

    /**
     * As {@link #lintJsonEntries}, for entries read from initialization files, where {@code fileOfEntry} gives the file
     * each entry came from, numbered in load order. As with an upsert, the last entry for an id is kept, within a file
     * and across files; {@code DUPLICATE_ID} says which file's load replaces each earlier entry.
     */
    public static ObjectNode lintInitializationFileEntries(ObjectMapper objectMapper, ExpectationSerializer expectationSerializer, List<JsonNode> entries, List<Integer> fileOfEntry) {
        return lintEntries(objectMapper, expectationSerializer, entries, fileOfEntry);
    }

    private static ObjectNode lintEntries(ObjectMapper objectMapper, ExpectationSerializer expectationSerializer, List<JsonNode> entries, List<Integer> fileOfEntry) {
        Report report = new Report();
        List<Subject> subjects = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            JsonNode entry = entries.get(index);
            String explicitId = entry.path("id").isTextual() ? entry.path("id").asText() : null;
            if (entry.has("specUrlOrPayload")) {
                report.skipped++;
                continue;
            }
            try {
                if (!entry.isObject()) {
                    throw new IllegalArgumentException("each entry must be an expectation object");
                }
                Expectation expectation = expectationSerializer.deserialize(entry.toString());
                subjects.add(new Subject(expectation, index, explicitId, summary(expectation)));
            } catch (IllegalArgumentException invalid) {
                String method = entry.path("httpRequest").path("method").asText("");
                String path = entry.path("httpRequest").path("path").asText("");
                Subject subject = new Subject(null, index, explicitId, (method + " " + path).trim());
                String reason = String.valueOf(invalid.getMessage());
                int errors = reason.indexOf("schema validation errors:");
                if (errors >= 0) {
                    reason = reason.substring(errors + "schema validation errors:".length()).trim();
                }
                addIssue(report, new Issue(Rule.INVALID_EXPECTATION, null, "not a valid expectation, so PUT /mockserver/expectation would reject it: " + reason, subject, null));
            }
        }
        subjects = removeReplacedDuplicates(report, subjects, fileOfEntry);
        subjects.sort(Comparator.comparingInt((Subject subject) -> -subject.expectation.getPriority()).thenComparingInt(subject -> subject.servingPosition));
        lint(report, subjects, true);
        return toJson(objectMapper, report, "request", "index", "openApiExpectationsSkipped");
    }

    /**
     * Lints the active stored expectations in the order the serving path evaluates them.
     */
    static ObjectNode lintStored(ObjectMapper objectMapper, RequestMatchers requestMatchers) {
        Report report = new Report();
        List<Subject> subjects = new ArrayList<>();
        int position = 0;
        for (HttpRequestMatcher matcher : requestMatchers.sortedRequestMatchers()) {
            Expectation expectation = matcher.getExpectation();
            if (expectation == null) {
                continue;
            }
            position++;
            if (!expectation.isActive()) {
                report.skipped++;
                continue;
            }
            subjects.add(new Subject(expectation, position, expectation.getId(), summary(expectation)));
        }
        lint(report, subjects, true);
        return toJson(objectMapper, report, "stored", "position", "inactiveExpectationsSkipped");
    }

    /**
     * The cheap per-expectation checks for expectations just created or updated, at warning severity or above.
     * Logs them and adds {@value #LINT_WARNINGS_HEADER} to {@code response}; never changes what was stored.
     */
    static HttpResponse adviseOnUpsert(MockServerLogger mockServerLogger, List<Expectation> upserted, HttpResponse response) {
        Report report = new Report();
        for (int index = 0; index < upserted.size(); index++) {
            Expectation expectation = upserted.get(index);
            lintOne(report, new Subject(expectation, index, expectation.getId(), summary(expectation)), false);
        }
        List<Issue> warnings = report.issues;
        if (warnings.isEmpty()) {
            return response;
        }
        if (mockServerLogger != null && mockServerLogger.isEnabledForInstance(Level.WARN)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.WARN)
                    .setLogLevel(Level.WARN)
                    .setMessageFormat("expectation lint found " + warnings.size() + " problem(s) in the expectations just created or updated, which were stored unchanged; PUT /mockserver/lint checks every stored expectation, including for shadowing:{}")
                    .setArguments(warningLines(warnings, ""))
            );
        }
        return response.withHeader(LINT_WARNINGS_HEADER, Integer.toString(warnings.size()));
    }

    /**
     * The per-expectation checks, at warning severity or above, and the budgeted shadowing check for the expectations
     * one initialization file has just loaded, compared in the order the file lists them at each priority. Logs one
     * WARN naming the file and never changes what was loaded. Ids in {@code generatedIds} were assigned by the loader,
     * not written in the file, so messages name those expectations by method and path instead. An entry whose id a later
     * entry repeats is reported as {@code DUPLICATE_ID} and left out of the other checks, as the load keeps only the last.
     * <p>
     * Returns a digest of the problems found, 0 when there are none. The WARN is skipped when the digest equals
     * {@code previousDigest}, so a reload that finds the same problems in the same file does not repeat them.
     */
    public static int adviseOnLoad(MockServerLogger mockServerLogger, String file, Expectation[] loaded, Set<String> generatedIds, int previousDigest) {
        if (mockServerLogger == null || !mockServerLogger.isEnabledForInstance(Level.WARN)) {
            return previousDigest;
        }
        if (loaded.length == 0) {
            return 0;
        }
        List<Subject> subjects = new ArrayList<>();
        for (int index = 0; index < loaded.length; index++) {
            Expectation expectation = loaded[index];
            String id = generatedIds.contains(expectation.getId()) ? null : expectation.getId();
            subjects.add(new Subject(expectation, index, id, summary(expectation)));
        }
        Report report = new Report();
        subjects = removeReplacedDuplicates(report, subjects, Collections.nCopies(loaded.length, 0));
        subjects.sort(Comparator.comparingInt((Subject subject) -> -subject.expectation.getPriority()).thenComparingInt(subject -> subject.servingPosition));
        lint(report, subjects, true, false);
        if (report.issues.isEmpty()) {
            return 0;
        }
        String truncated = report.shadowCheckTruncated ? "\n - the shadowing check stopped at its budget, so not every pair of expectations was compared" : "";
        StringBuilder allIssues = new StringBuilder(truncated);
        for (Issue issue : report.issues) {
            allIssues.append('\n').append(issue.rule.name()).append(' ').append(issue.subject.describe()).append(": ").append(issue.message);
        }
        int digest = allIssues.toString().hashCode();
        digest = digest == 0 ? 1 : digest;
        if (digest != previousDigest) {
            List<Issue> warnings = report.issues;
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.WARN)
                    .setLogLevel(Level.WARN)
                    .setMessageFormat("expectation lint found " + warnings.size() + " problem(s) in the expectations loaded from initialization file:{}which were loaded unchanged; PUT /mockserver/lint or mockserver lint <file> lists them in full:{}")
                    .setArguments(file, warningLines(warnings, truncated))
            );
        }
        return digest;
    }

    private static String warningLines(List<Issue> warnings, String suffix) {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < Math.min(warnings.size(), MAX_LOGGED_ISSUES); i++) {
            Issue issue = warnings.get(i);
            lines.append("\n - ").append(issue.rule.name()).append(" expectation ").append(issue.subject.describe()).append(": ").append(issue.message);
        }
        if (warnings.size() > MAX_LOGGED_ISSUES) {
            lines.append("\n - and ").append(warnings.size() - MAX_LOGGED_ISSUES).append(" more");
        }
        return lines.append(suffix).toString();
    }

    // ---- the checks ----

    static void lint(Report report, List<Subject> servingOrder, boolean checkShadowing) {
        lint(report, servingOrder, checkShadowing, true);
    }

    private static void lint(Report report, List<Subject> servingOrder, boolean checkShadowing, boolean includeInfo) {
        for (Subject subject : servingOrder) {
            report.expectationsChecked++;
            lintOne(report, subject, includeInfo);
        }
        if (checkShadowing) {
            findShadowed(report, servingOrder);
        }
        report.issues.sort(Comparator.comparingInt((Issue issue) -> issue.subject.order).thenComparing(issue -> issue.rule));
    }

    /**
     * Drops the entries a store would not keep: an upsert of each entry in turn and a file load both keep the last entry
     * for an id. {@code fileOfEntry} null means an upsert; otherwise it names each entry's file, for the message. The kept
     * entry is served from the first entry's place, as an update in place keeps the original's creation time.
     */
    private static List<Subject> removeReplacedDuplicates(Report report, List<Subject> subjects, List<Integer> fileOfEntry) {
        Map<String, Subject> firstById = new HashMap<>();
        Map<String, Subject> keptById = new HashMap<>();
        for (Subject subject : subjects) {
            if (subject.reportedId != null) {
                firstById.putIfAbsent(subject.reportedId, subject);
                keptById.put(subject.reportedId, subject);
            }
        }
        List<Subject> result = new ArrayList<>();
        for (Subject subject : subjects) {
            Subject kept = subject.reportedId != null ? keptById.get(subject.reportedId) : subject;
            if (kept != subject) {
                String message;
                if (fileOfEntry == null) {
                    message = "is replaced by a later entry with the same id (" + kept.summary + "); expectations are upserted by id, so only the last one is kept";
                } else if (sameFile(fileOfEntry, kept, subject)) {
                    message = "is replaced by a later entry in the same file with the same id (" + kept.summary + "); loading a file keeps only the last entry for each id";
                } else {
                    message = "is replaced by a later entry with the same id (" + kept.summary + "); a file loaded later replaces an earlier file's entries with the same id";
                }
                addIssue(report, new Issue(Rule.DUPLICATE_ID, "id", message, subject, kept));
            } else {
                Subject first = subject.reportedId != null ? firstById.get(subject.reportedId) : subject;
                result.add(new Subject(subject.expectation, subject.order, first.order, subject.reportedId, subject.summary));
            }
        }
        return result;
    }

    private static boolean sameFile(List<Integer> fileOfEntry, Subject one, Subject other) {
        return fileOfEntry.get(one.order).equals(fileOfEntry.get(other.order));
    }

    private static void lintOne(Report report, Subject subject, boolean includeInfo) {
        Expectation expectation = subject.expectation;
        Times times = expectation.getTimes();
        if (times != null && !times.isUnlimited() && times.getRemainingTimes() <= 0) {
            addIssue(report, new Issue(Rule.TIMES_ZERO, "times", "never matches: times allows " + times.getRemainingTimes() + " matches; remove times for unlimited matches, or set remainingTimes to at least 1", subject, null));
        }
        TimeToLive timeToLive = expectation.getTimeToLive();
        if (timeToLive != null && !timeToLive.isUnlimited() && timeToLive.getTimeToLive() != null && timeToLive.getTimeToLive() <= 0) {
            addIssue(report, new Issue(Rule.TIME_TO_LIVE_ZERO, "timeToLive", "never matches: its time to live is " + timeToLive.getTimeToLive() + ", so it expires as it is created", subject, null));
        }
        if (expectation.getPercentage() != null && expectation.getPercentage() <= 0) {
            addIssue(report, new Issue(Rule.PERCENTAGE_ZERO, "percentage", "never served: percentage is " + expectation.getPercentage() + ", so every matching request passes it over", subject, null));
        }
        checkUnreachableResponses(report, subject);
        if (expectation.getHttpRequest() instanceof HttpRequest && !expectation.getHttpRequest().isNot()) {
            HttpRequest request = (HttpRequest) expectation.getHttpRequest();
            checkPath(report, subject, request);
            checkMethod(report, subject, request.getMethod());
            checkHeaderNames(report, subject, request);
            if (includeInfo) {
                checkStrictJsonArrays(report, subject, request.getBody());
            }
        }
    }

    private static void checkUnreachableResponses(Report report, Subject subject) {
        Expectation expectation = subject.expectation;
        List<HttpResponse> responses = expectation.getHttpResponses();
        Times times = expectation.getTimes();
        if (responses == null || responses.size() < 2 || times == null || times.isUnlimited() || times.getRemainingTimes() <= 0 || expectation.getMatchCount() > 0) {
            return;
        }
        ResponseMode mode = expectation.getResponseMode() != null ? expectation.getResponseMode() : ResponseMode.SEQUENTIAL;
        int remaining = times.getRemainingTimes();
        int reachable;
        if (mode == ResponseMode.SEQUENTIAL) {
            reachable = Math.min(remaining, responses.size());
        } else if (mode == ResponseMode.SWITCH) {
            int threshold = expectation.getSwitchAfter() != null && expectation.getSwitchAfter() > 0 ? expectation.getSwitchAfter() : 1;
            reachable = Math.min((remaining - 1) / threshold + 1, responses.size());
        } else {
            return;
        }
        if (reachable < responses.size()) {
            addIssue(report, new Issue(Rule.UNREACHABLE_RESPONSES, "httpResponses", "only the first " + reachable + " of its " + responses.size()
                + " responses can be served: times allows " + remaining + " matches in " + mode + " mode, so responses " + (reachable + 1) + " to " + responses.size()
                + " are never reached (except by the " + Expectation.FORCE_RESPONSE_INDEX_HEADER + " header)", subject, null));
        }
    }

    private static void checkPath(Report report, Subject subject, HttpRequest request) {
        NottableString path = request.getPath();
        if (!isPlain(path) || path.getValue().isEmpty()) {
            return;
        }
        String value = path.getValue();
        String lower = value.toLowerCase(Locale.ROOT);
        boolean connect = request.getMethod() != null && "CONNECT".equalsIgnoreCase(request.getMethod().getValue());
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            addIssue(report, new Issue(Rule.PATH_INCLUDES_SCHEME_AND_HOST, "path", "never matches: the path is matched without the scheme and host, so write only the part from the first / (match the host with a Host header, or secure for https)", subject, null));
        } else if (!connect && Character.isLetterOrDigit(value.charAt(0)) && value.indexOf('|') < 0 && !HOST_AND_PORT.matcher(value).matches()) {
            addIssue(report, new Issue(Rule.PATH_MISSING_LEADING_SLASH, "path", "never matches: request paths always start with /, so use \"/" + value + "\"", subject, null));
        }
        if (QUERY_STRING.matcher(value).find()) {
            addIssue(report, new Issue(Rule.PATH_INCLUDES_QUERY_STRING, "path", "does not match as written: the query string is not part of the path, and ? is a regex quantifier here; move it to queryStringParameters", subject, null));
        }
        // a lone * is the asterisk-form request target of OPTIONS *, not a glob
        if (!"*".equals(value) && (value.startsWith("*") || value.contains("/*"))) {
            addIssue(report, new Issue(Rule.PATH_GLOB_WILDCARD, "path", "the path is a regular expression, not a glob: * repeats the character before it, so \"/*\" matches any number of slashes, including none, and nothing else; use .* to match any characters", subject, null));
        }
        if ((request.getPathParameters() == null || request.getPathParameters().isEmpty()) && PATH_TEMPLATE.matcher(value).find()) {
            addIssue(report, new Issue(Rule.PATH_TEMPLATE_WITHOUT_PATH_PARAMETERS, "path", "the path has a {name} template but no pathParameters, so the braces are matched as written; add pathParameters (for example \"name\": [\".*\"]) or use a regex such as [^/]+", subject, null));
        }
    }

    private static void checkMethod(Report report, Subject subject, NottableString method) {
        if (!isPlain(method) || !TOKEN.matcher(method.getValue()).matches()) {
            return;
        }
        String upper = method.getValue().toUpperCase(Locale.ROOT);
        if (KNOWN_METHODS.contains(upper) || upper.length() < 3) {
            return;
        }
        String suggestion = closest(upper, KNOWN_METHODS, 1);
        if (suggestion != null) {
            addIssue(report, new Issue(Rule.METHOD_TYPO, "method", "\"" + method.getValue() + "\" is not an HTTP method; did you mean " + suggestion + "?", subject, null));
        }
    }

    private static void checkHeaderNames(Report report, Subject subject, HttpRequest request) {
        if (request.getHeaders() == null || request.getHeaders().isEmpty()) {
            return;
        }
        for (KeyToMultiValue header : request.getHeaders().getEntries()) {
            NottableString name = header.getName();
            if (!isPlain(name) || !TOKEN.matcher(name.getValue()).matches()) {
                continue;
            }
            String suggestion = suggestHeaderName(name.getValue());
            if (suggestion != null) {
                addIssue(report, new Issue(Rule.HEADER_NAME_TYPO, "headers", "header \"" + name.getValue() + "\" is not a standard header name; did you mean " + suggestion + "?", subject, null));
            }
        }
    }

    static String suggestHeaderName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (KNOWN_HEADER_NAMES.contains(lower)) {
            return null;
        }
        // proxies such as nginx drop headers with underscores, and users often mean hyphens
        String hyphenated = lower.replace('_', '-');
        if (KNOWN_HEADER_NAMES.contains(hyphenated)) {
            return displayHeaderName(hyphenated);
        }
        if (lower.length() < 5) {
            return null;
        }
        // one edit only: adding or removing a prefix such as "X-" is two or more, so X-Authorization or
        // Request-Id (for X-Request-Id) is read as a custom header, never as a typo
        String closest = closest(hyphenated, KNOWN_HEADER_NAMES, 1);
        return closest != null ? displayHeaderName(closest) : null;
    }

    private static void checkStrictJsonArrays(Report report, Subject subject, Body<?> body) {
        if (!(body instanceof JsonBody) || body.isNot() || ((JsonBody) body).getMatchType() != MatchType.STRICT) {
            return;
        }
        try {
            if (containsArray(ObjectMapperHolder.MAPPER.readTree(((JsonBody) body).getValue()))) {
                addIssue(report, new Issue(Rule.STRICT_JSON_BODY_WITH_ARRAYS, "body", "STRICT matching needs every array in the same order with no extra items; if order or extra items should not matter use ONLY_MATCHING_FIELDS (the default)", subject, null));
            }
        } catch (Exception notJson) {
            // the JSON matcher reports an unparseable body; nothing to add here
        }
    }

    private static boolean containsArray(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isArray()) {
            return true;
        }
        for (JsonNode child : node) {
            if (containsArray(child)) {
                return true;
            }
        }
        return false;
    }

    // ---- shadowing ----

    /**
     * An expectation is shadowed when one evaluated before it always matches first: the earlier one has no gate that
     * can pass it over (unlimited times and time to live, no scenario state, no percentage, no LLM conversation) and
     * matches every request the later one matches. The comparison is deliberately conservative: only constraints it
     * can prove weaker count, so it misses some shadowing but never reports a reachable expectation.
     */
    private static void findShadowed(Report report, List<Subject> servingOrder) {
        Map<String, List<Integer>> byPath = new HashMap<>();
        for (int i = 0; i < servingOrder.size(); i++) {
            HttpRequest request = httpRequest(servingOrder.get(i));
            if (request != null && isPlain(request.getPath())) {
                byPath.computeIfAbsent(request.getPath().getValue(), key -> new ArrayList<>()).add(i);
            }
        }
        boolean[] shadowed = new boolean[servingOrder.size()];
        RegexBudget regexBudget = new RegexBudget(MAX_REGEX_STEPS);
        for (int i = 0; i < servingOrder.size(); i++) {
            Subject earlier = servingOrder.get(i);
            HttpRequest earlierRequest = httpRequest(earlier);
            // a shadowed expectation never serves, and whatever it covers its own shadower covers too
            if (shadowed[i] || earlierRequest == null || !alwaysServesWhenMatched(earlier.expectation)) {
                continue;
            }
            NottableString earlierPath = earlierRequest.getPath();
            boolean literalPath = isPlain(earlierPath) && !earlierPath.getValue().isEmpty() && !looksLikeRegex(earlierPath.getValue());
            Pattern pathPattern = literalPath ? null : compile(earlierPath);
            Pattern methodPattern = compile(earlierRequest.getMethod());
            List<Integer> candidates = literalPath ? byPath.getOrDefault(earlierPath.getValue(), Collections.emptyList()) : null;
            int first = candidates != null ? Collections.binarySearch(candidates, i) + 1 : i + 1;
            int count = candidates != null ? candidates.size() : servingOrder.size();
            for (int c = first; c < count; c++) {
                // every candidate visited counts, so the work is bounded however large the set is
                if (report.pairComparisons >= MAX_PAIR_COMPARISONS) {
                    report.shadowCheckTruncated = true;
                    return;
                }
                report.pairComparisons++;
                int j = candidates != null ? candidates.get(c) : c;
                Subject later = servingOrder.get(j);
                HttpRequest laterRequest = httpRequest(later);
                if (shadowed[j] || laterRequest == null || servedEarlier(laterRequest, earlierRequest)) {
                    continue;
                }
                if (Objects.equals(earlier.expectation.getNamespace(), later.expectation.getNamespace())
                    && covers(earlierRequest, laterRequest, methodPattern, pathPattern, regexBudget)) {
                    shadowed[j] = true;
                    int earlierPriority = earlier.expectation.getPriority();
                    int laterPriority = later.expectation.getPriority();
                    addIssue(report, new Issue(Rule.SHADOWED, null, "never matches: " + earlier.describe() + " is evaluated first "
                        + (earlierPriority > laterPriority ? "(priority " + earlierPriority + " > " + laterPriority + ")" : "(same priority, created earlier)")
                        + ", matches every request this one matches, and never runs out (unlimited times, no time to live, scenario state or percentage); give this one a higher priority or make the other more specific", later, earlier));
                }
            }
            if (regexBudget.exhausted) {
                report.shadowCheckTruncated = true;
            }
        }
    }

    // respondBeforeBody expectations are matched before the body arrives, ahead of every other expectation
    private static boolean servedEarlier(HttpRequest later, HttpRequest earlier) {
        return Boolean.TRUE.equals(later.getRespondBeforeBody()) && !Boolean.TRUE.equals(earlier.getRespondBeforeBody());
    }

    private static boolean alwaysServesWhenMatched(Expectation expectation) {
        Times times = expectation.getTimes();
        TimeToLive timeToLive = expectation.getTimeToLive();
        if (times != null && !times.isUnlimited() || timeToLive != null && !timeToLive.isUnlimited()) {
            return false;
        }
        if (expectation.getPercentage() != null && expectation.getPercentage() < 100) {
            return false;
        }
        if (expectation.getScenarioName() != null && expectation.getScenarioState() != null) {
            return false;
        }
        if (expectation.getHttpLlmResponse() != null) {
            LlmConversationMatcher conversation = expectation.getHttpLlmResponse().getConversationMatcher();
            return conversation == null || !conversation.hasPredicates();
        }
        return true;
    }

    private static boolean covers(HttpRequest earlier, HttpRequest later, Pattern methodPattern, Pattern pathPattern, RegexBudget regexBudget) {
        // a {name} path template is only comparable with the same template
        boolean pathParameters = notEmpty(earlier.getPathParameters()) || notEmpty(later.getPathParameters());
        if (pathParameters && !(notEmpty(earlier.getPathParameters()) && sameString(earlier.getPath(), later.getPath(), false) && multiValuesCover(earlier.getPathParameters(), later.getPathParameters(), false))) {
            return false;
        }
        return multiValuesCover(earlier.getQueryStringParameters(), later.getQueryStringParameters(), false)
            && multiValuesCover(earlier.getHeaders(), later.getHeaders(), true)
            && valuesCover(earlier.getCookies(), later.getCookies())
            && (earlier.getBody() == null || Objects.equals(earlier.getBody(), later.getBody()))
            && (earlier.isKeepAlive() == null || Objects.equals(earlier.isKeepAlive(), later.isKeepAlive()))
            && (earlier.isSecure() == null || Objects.equals(earlier.isSecure(), later.isSecure()))
            && (earlier.getProtocol() == null || Objects.equals(earlier.getProtocol(), later.getProtocol()))
            && (earlier.getClientCertificate() == null || Objects.equals(earlier.getClientCertificate(), later.getClientCertificate()))
            && (earlier.getJwt() == null || Objects.equals(earlier.getJwt(), later.getJwt()))
            && (earlier.getClientIp() == null || Objects.equals(earlier.getClientIp(), later.getClientIp()))
            && stringCovers(earlier.getMethod(), later.getMethod(), methodPattern, regexBudget)
            && (pathParameters || stringCovers(earlier.getPath(), later.getPath(), pathPattern, regexBudget));
    }

    private static boolean stringCovers(NottableString earlier, NottableString later, Pattern earlierPattern, RegexBudget regexBudget) {
        if (earlier == null || isPlain(earlier) && (earlier.getValue().isEmpty() || ".*".equals(earlier.getValue()))) {
            return true;
        }
        if (sameString(earlier, later, false)) {
            return true;
        }
        if (earlierPattern == null || !isPlain(later) || later.getValue().isEmpty() || looksLikeRegex(later.getValue())) {
            return false;
        }
        // the later value matches only itself, so the earlier one covers it when its pattern matches that text
        return regexBudget.matches(earlierPattern, later.getValue());
    }

    private static boolean multiValuesCover(KeysToMultiValues<? extends KeyToMultiValue, ?> earlier, KeysToMultiValues<? extends KeyToMultiValue, ?> later, boolean ignoreKeyCase) {
        if (!notEmpty(earlier)) {
            return true;
        }
        if (!notEmpty(later) || earlier.getKeyMatchStyle() != later.getKeyMatchStyle()) {
            return false;
        }
        for (KeyToMultiValue earlierEntry : earlier.getEntries()) {
            boolean found = false;
            for (KeyToMultiValue laterEntry : later.getEntries()) {
                if (sameString(earlierEntry.getName(), laterEntry.getName(), ignoreKeyCase) && sameStrings(earlierEntry.getValues(), laterEntry.getValues())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean valuesCover(KeysAndValues<? extends KeyAndValue, ?> earlier, KeysAndValues<? extends KeyAndValue, ?> later) {
        if (earlier == null || earlier.isEmpty()) {
            return true;
        }
        if (later == null || later.isEmpty()) {
            return false;
        }
        for (KeyAndValue earlierEntry : earlier.getEntries()) {
            boolean found = false;
            for (KeyAndValue laterEntry : later.getEntries()) {
                if (sameString(earlierEntry.getName(), laterEntry.getName(), false) && sameString(earlierEntry.getValue(), laterEntry.getValue(), false)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameStrings(List<NottableString> earlier, List<NottableString> later) {
        if (earlier.size() != later.size()) {
            return false;
        }
        for (NottableString value : earlier) {
            boolean found = false;
            for (NottableString other : later) {
                if (sameString(value, other, false)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    // NottableString.equals treats a schema as matching a value, so compare the parts directly
    private static boolean sameString(NottableString one, NottableString two, boolean ignoreCase) {
        if (one == null || two == null) {
            return one == two;
        }
        if (one.getClass() != two.getClass() || one.isNot() != two.isNot() || one.isOptional() != two.isOptional()) {
            return false;
        }
        return ignoreCase ? one.getValue().equalsIgnoreCase(two.getValue()) : Objects.equals(one.getValue(), two.getValue());
    }

    private static Pattern compile(NottableString value) {
        if (!isPlain(value) || value.getValue().isEmpty() || value.getValue().length() > MAX_PATTERN_LENGTH || !looksLikeRegex(value.getValue())) {
            return null;
        }
        try {
            return Pattern.compile(value.getValue(), Pattern.DOTALL);
        } catch (PatternSyntaxException invalid) {
            return null;
        }
    }

    /**
     * Bounds the work a user's regex can make the shadowing check do: each character read counts against a per-match
     * and a total budget, and a match that runs out counts as "does not cover".
     */
    static final class RegexBudget {
        private long remaining;
        boolean exhausted;

        RegexBudget(long total) {
            this.remaining = total;
        }

        boolean matches(Pattern pattern, String input) {
            if (remaining <= 0) {
                exhausted = true;
                return false;
            }
            BoundedCharSequence text = new BoundedCharSequence(input, Math.min(remaining, MAX_REGEX_STEPS_PER_MATCH));
            try {
                return pattern.matcher(text).matches();
            } catch (BudgetExceeded | StackOverflowError cannotProve) {
                // java.util.regex recurses per repetition, so a long input can overflow the stack before the budget ends
                return false;
            } finally {
                remaining -= text.steps[0];
                if (remaining <= 0) {
                    exhausted = true;
                }
            }
        }
    }

    private static final class BudgetExceeded extends RuntimeException {
        BudgetExceeded() {
            super(null, null, false, false);
        }
    }

    private static final class BoundedCharSequence implements CharSequence {
        private final String text;
        private final long limit;
        private final long[] steps;

        BoundedCharSequence(String text, long limit) {
            this(text, limit, new long[1]);
        }

        private BoundedCharSequence(String text, long limit, long[] steps) {
            this.text = text;
            this.limit = limit;
            this.steps = steps;
        }

        @Override
        public char charAt(int index) {
            if (++steps[0] > limit) {
                throw new BudgetExceeded();
            }
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new BoundedCharSequence(text.substring(start, end), limit, steps);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    // ---- helpers ----

    private static HttpRequest httpRequest(Subject subject) {
        if (subject.expectation != null && subject.expectation.getHttpRequest() instanceof HttpRequest && !subject.expectation.getHttpRequest().isNot()) {
            return (HttpRequest) subject.expectation.getHttpRequest();
        }
        return null;
    }

    private static boolean notEmpty(KeysToMultiValues<?, ?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean isPlain(NottableString value) {
        return value != null && value.getValue() != null && !(value instanceof NottableMatcherString) && !value.isNot() && !value.isOptional();
    }

    private static boolean looksLikeRegex(String value) {
        for (int i = 0; i < value.length(); i++) {
            if ("\\.[]{}()*+?^$|".indexOf(value.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static String closest(String value, Set<String> candidates, int maxDistance) {
        String best = null;
        int bestDistance = maxDistance + 1;
        for (String candidate : candidates) {
            if (Math.abs(candidate.length() - value.length()) > maxDistance) {
                continue;
            }
            int distance = editDistance(value, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    // optimal string alignment distance: insertions, deletions, substitutions and adjacent transpositions
    static int editDistance(String one, String two) {
        int[][] distance = new int[one.length() + 1][two.length() + 1];
        for (int i = 0; i <= one.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= two.length(); j++) {
            distance[0][j] = j;
        }
        for (int i = 1; i <= one.length(); i++) {
            for (int j = 1; j <= two.length(); j++) {
                int cost = one.charAt(i - 1) == two.charAt(j - 1) ? 0 : 1;
                distance[i][j] = Math.min(Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1), distance[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && one.charAt(i - 1) == two.charAt(j - 2) && one.charAt(i - 2) == two.charAt(j - 1)) {
                    distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + 1);
                }
            }
        }
        return distance[one.length()][two.length()];
    }

    private static String displayHeaderName(String lowerCaseName) {
        StringBuilder display = new StringBuilder(lowerCaseName.length());
        boolean upper = true;
        for (char character : lowerCaseName.toCharArray()) {
            display.append(upper ? Character.toUpperCase(character) : character);
            upper = character == '-';
        }
        return display.toString();
    }

    private static Set<String> knownHeaderNames() {
        Set<String> names = new HashSet<>(Arrays.asList(
            "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-port", "x-forwarded-proto",
            "x-request-id", "x-correlation-id", "x-api-key", "traceparent", "tracestate"
        ));
        for (Field field : HttpHeaderNames.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && CharSequence.class.isAssignableFrom(field.getType())) {
                try {
                    names.add(field.get(null).toString().toLowerCase(Locale.ROOT));
                } catch (IllegalAccessException ignored) {
                    // public static fields are always readable
                }
            }
        }
        return Collections.unmodifiableSet(names);
    }

    private static String summary(Expectation expectation) {
        if (expectation.getHttpRequest() instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) expectation.getHttpRequest();
            String method = request.getMethod() != null && isNotBlankValue(request.getMethod()) ? request.getMethod().toString() : "any method";
            String path = request.getPath() != null && isNotBlankValue(request.getPath()) ? request.getPath().toString() : "any path";
            return method + " " + path;
        }
        return expectation.getHttpRequest() != null ? expectation.getHttpRequest().getClass().getSimpleName() : "any request";
    }

    private static boolean isNotBlankValue(NottableString value) {
        return value.getValue() != null && !value.getValue().isEmpty();
    }

    private static void addIssue(Report report, Issue issue) {
        if (report.issues.size() >= MAX_ISSUES) {
            report.issuesTruncated = true;
        } else {
            report.issues.add(issue);
        }
    }

    private static ObjectNode toJson(ObjectMapper objectMapper, Report report, String source, String orderField, String skippedField) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("source", source);
        result.put("note", NOTE);
        result.put("expectationsChecked", report.expectationsChecked);
        result.put(skippedField, report.skipped);
        int errors = 0;
        int warnings = 0;
        int infos = 0;
        ArrayNode issues = objectMapper.createArrayNode();
        for (Issue issue : report.issues) {
            switch (issue.rule.getSeverity()) {
                case ERROR:
                    errors++;
                    break;
                case WARNING:
                    warnings++;
                    break;
                default:
                    infos++;
            }
            ObjectNode node = issues.addObject();
            node.put("rule", issue.rule.name());
            node.put("severity", issue.rule.getSeverity().name());
            if (issue.subject.reportedId != null) {
                node.put("expectationId", issue.subject.reportedId);
            }
            node.put(orderField, issue.subject.order);
            node.put("summary", issue.subject.summary);
            if (issue.field != null) {
                node.put("field", issue.field);
            }
            node.put("message", issue.message);
            if (issue.related != null) {
                if (issue.related.reportedId != null) {
                    node.put("relatedExpectationId", issue.related.reportedId);
                }
                node.put("related" + Character.toUpperCase(orderField.charAt(0)) + orderField.substring(1), issue.related.order);
                node.put("relatedSummary", issue.related.summary);
            }
        }
        result.put("issueCount", report.issues.size());
        result.put("errorCount", errors);
        result.put("warningCount", warnings);
        result.put("infoCount", infos);
        result.put("issuesTruncated", report.issuesTruncated);
        result.put("pairComparisons", report.pairComparisons);
        result.put("maxPairComparisons", MAX_PAIR_COMPARISONS);
        result.put("shadowCheckTruncated", report.shadowCheckTruncated);
        result.set("issues", issues);
        return result;
    }

    private static final class ObjectMapperHolder {
        private static final ObjectMapper MAPPER = org.mockserver.serialization.ObjectMapperFactory.createObjectMapper();
    }
}

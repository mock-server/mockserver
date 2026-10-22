package org.mockserver.matchers;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.AnyOfRequestDefinition;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Matcher for {@link AnyOfRequestDefinition}: a request matches when any branch matches it.
 * <p>
 * Each branch is compiled once, in {@link #apply}, to its own {@link HttpRequestMatcher} via {@link MatcherBuilder}, in
 * the same plane (control or data) as this matcher. A match walks the compiled array in order and stops at the first
 * branch that matches, passing the caller's context straight through, so it adds no allocation of its own.
 * </p>
 * <p>
 * Only a read-only diagnostic context (detailed differences with match logging suppressed, as the explain, closest
 * match and debug mismatch paths create) evaluates every branch in its own context and keeps the differences of the
 * closest branch, rather than the union of every branch's.
 * </p>
 * <p>
 * <strong>Concurrency:</strong> as for {@link ConditionalRequestMatcher}, the compiled branches live in an immutable
 * holder published through one {@code volatile} reference, which {@code matches()} reads once.
 * </p>
 */
public class AnyOfRequestMatcher extends AbstractHttpRequestMatcher {

    private static final HttpRequestMatcher[] NO_BRANCHES = new HttpRequestMatcher[0];

    private final MatcherBuilder matcherBuilder;
    private volatile Compiled compiled = Compiled.EMPTY;

    private static final class Compiled {

        private static final Compiled EMPTY = new Compiled(null, NO_BRANCHES, Collections.emptyList());

        private final AnyOfRequestDefinition anyOfRequestDefinition;
        private final HttpRequestMatcher[] branchMatchers;
        private final List<HttpRequest> httpRequests;

        private Compiled(AnyOfRequestDefinition anyOfRequestDefinition, HttpRequestMatcher[] branchMatchers, List<HttpRequest> httpRequests) {
            this.anyOfRequestDefinition = anyOfRequestDefinition;
            this.branchMatchers = branchMatchers;
            this.httpRequests = httpRequests;
        }
    }

    /**
     * How one branch compared with a request, for diagnostics.
     */
    public static final class BranchComparison {

        private final boolean matches;
        private final Map<MatchDifference.Field, List<String>> differences;

        private BranchComparison(boolean matches, Map<MatchDifference.Field, List<String>> differences) {
            this.matches = matches;
            this.differences = differences;
        }

        public boolean matches() {
            return matches;
        }

        public Map<MatchDifference.Field, List<String>> getDifferences() {
            return differences;
        }
    }

    protected AnyOfRequestMatcher(Configuration configuration, MockServerLogger mockServerLogger) {
        super(configuration, mockServerLogger);
        this.matcherBuilder = new MatcherBuilder(configuration, mockServerLogger);
    }

    @Override
    public List<HttpRequest> getHttpRequests() {
        return compiled.httpRequests;
    }

    @Override
    boolean apply(RequestDefinition requestDefinition) {
        AnyOfRequestDefinition anyOf = requestDefinition instanceof AnyOfRequestDefinition definition ? definition : null;
        Compiled current = this.compiled;
        if (current.anyOfRequestDefinition == null || !current.anyOfRequestDefinition.equals(anyOf)) {
            Compiled rebuilt;
            if (anyOf != null) {
                List<HttpRequestMatcher> branchMatchers = new ArrayList<>();
                List<HttpRequest> requests = new ArrayList<>();
                for (RequestDefinition branch : anyOf.getAnyOf()) {
                    if (branch != null) {
                        HttpRequestMatcher branchMatcher = buildBranchMatcher(branch);
                        branchMatchers.add(branchMatcher);
                        if (branchMatcher.getHttpRequests() != null) {
                            for (HttpRequest request : branchMatcher.getHttpRequests()) {
                                if (request != null) {
                                    requests.add(request);
                                }
                            }
                        }
                    }
                }
                rebuilt = new Compiled(anyOf, branchMatchers.toArray(NO_BRANCHES), Collections.unmodifiableList(requests));
            } else {
                rebuilt = new Compiled(null, NO_BRANCHES, Collections.emptyList());
            }
            this.compiled = rebuilt;
            return true;
        }
        return false;
    }

    private HttpRequestMatcher buildBranchMatcher(RequestDefinition branch) {
        if (controlPlaneMatcher) {
            return matcherBuilder.transformsToMatcher(branch);
        } else {
            // a throwaway expectation makes the branch a data-plane matcher, as ConditionalRequestMatcher does
            return matcherBuilder.transformsToMatcher(new Expectation(branch));
        }
    }

    @Override
    public boolean matches(MatchDifference context, RequestDefinition requestDefinition) {
        if (!isActive()) {
            return false;
        }
        final Compiled compiled = this.compiled;
        boolean requestNot = requestDefinition != null && requestDefinition.isNot();
        boolean anyOfNot = compiled.anyOfRequestDefinition != null && compiled.anyOfRequestDefinition.isNot();
        final HttpRequestMatcher[] branchMatchers = compiled.branchMatchers;
        boolean anyMatches = false;
        if (context != null && context.isDiagnosticOnly() && branchMatchers.length > 1) {
            anyMatches = matchesKeepingClosestBranch(branchMatchers, context, requestDefinition);
        } else {
            for (int i = 0; i < branchMatchers.length; i++) {
                if (branchMatchers[i].matches(context, requestDefinition)) {
                    anyMatches = true;
                    break;
                }
            }
        }
        return applyNotOperators(anyMatches, requestNot, anyOfNot, not);
    }

    private static boolean matchesKeepingClosestBranch(HttpRequestMatcher[] branchMatchers, MatchDifference context, RequestDefinition requestDefinition) {
        List<BranchComparison> comparisons = compareBranches(branchMatchers, requestDefinition, context.isCollectAllDifferences());
        int closest = closestBranch(comparisons);
        if (closest < 0) {
            return false;
        }
        BranchComparison comparison = comparisons.get(closest);
        if (!comparison.matches()) {
            context.addDifferences(comparison.getDifferences());
        }
        return comparison.matches();
    }

    /**
     * Diagnostics only: compares the request with every branch, each in its own context that records every
     * difference and writes no match log entries. Allocates, so it never runs while serving a request.
     */
    public List<BranchComparison> compareBranches(RequestDefinition requestDefinition, boolean collectAllDifferences) {
        return compareBranches(compiled.branchMatchers, requestDefinition, collectAllDifferences);
    }

    private static List<BranchComparison> compareBranches(HttpRequestMatcher[] branchMatchers, RequestDefinition requestDefinition, boolean collectAllDifferences) {
        List<BranchComparison> comparisons = new ArrayList<>(branchMatchers.length);
        for (HttpRequestMatcher branchMatcher : branchMatchers) {
            MatchDifference branchContext = new MatchDifference(true, requestDefinition).suppressMatchResultLogging();
            if (collectAllDifferences) {
                branchContext.collectAllDifferences();
            }
            boolean matches = branchMatcher.matches(branchContext, requestDefinition);
            comparisons.add(new BranchComparison(matches, matches ? Collections.emptyMap() : branchContext.getAllDifferences()));
        }
        return comparisons;
    }

    /**
     * The index of the first matching branch, else of the branch with the fewest differing fields (the earlier on a
     * tie), or -1 when there are no branches.
     */
    public static int closestBranch(List<BranchComparison> comparisons) {
        int closest = -1;
        int fewest = Integer.MAX_VALUE;
        for (int i = 0; i < comparisons.size(); i++) {
            BranchComparison comparison = comparisons.get(i);
            if (comparison.matches()) {
                return i;
            }
            int differing = comparison.getDifferences().size();
            if (differing < fewest) {
                fewest = differing;
                closest = i;
            }
        }
        return closest;
    }
}

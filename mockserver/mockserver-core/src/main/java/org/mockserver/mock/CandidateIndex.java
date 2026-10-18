package org.mockserver.mock;

import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.HttpRequestPropertiesMatcher;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableOptionalString;
import org.mockserver.model.NottableSchemaString;
import org.mockserver.model.NottableString;
import org.mockserver.model.Parameters;
import org.mockserver.model.RequestDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static org.mockserver.mock.SortableExpectationId.EXPECTATION_SORTABLE_PRIORITY_COMPARATOR;

/**
 * Candidate index for expectation matching above a size threshold.
 *
 * <p><b>Purpose.</b> {@code RequestMatchers.firstMatchingExpectation} scans every
 * registered expectation in global priority/insertion order and returns the first
 * match — pure O(n). For large expectation sets (1k–5k) this dominates the
 * request-serving hot path. This index narrows the scan to a small CANDIDATE set
 * for the request without changing which expectation is returned.
 *
 * <p><b>Hard guarantee — zero behavioural change.</b> The expectation returned for
 * any request is byte-for-byte identical to the full linear scan, including match
 * ORDER (priority + insertion order, first match wins). This holds because:
 * <ol>
 *   <li>An expectation is placed in a {@code (method, path)} BUCKET only when both
 *       its method and path are PLAIN LITERAL equality matchers (see
 *       {@link #bucketKeyFor(HttpRequestMatcher, boolean)}); such an expectation can
 *       match a request ONLY when the request's literal {@code (method, path)} equals
 *       the bucket key. Any other request provably fails the method/path criteria
 *       (which are AND-ed in {@code HttpRequestPropertiesMatcher}).</li>
 *   <li>Every expectation that is NOT safely bucketable (regex/notted/blank/optional/
 *       schema/path-parameter method or path, or a non-HTTP request definition) goes
 *       in the FALLTHROUGH list, which is checked on every request.</li>
 *   <li>The candidate set for a request is {@code bucket(requestKey) ∪ fallthrough}.
 *       Any expectation outside this set is in a different literal bucket and so
 *       cannot match the request. Therefore the first match among candidates,
 *       evaluated in the SAME GLOBAL sorted order, equals the first match of the
 *       full scan.</li>
 * </ol>
 *
 * <p><b>Global-order evaluation.</b> {@link #candidatesInGlobalOrder} returns the
 * candidate matchers sorted by exactly the comparator the backing
 * {@code CircularPriorityQueue} uses ({@link SortableExpectationId#EXPECTATION_SORTABLE_PRIORITY_COMPARATOR}
 * over each matcher's {@code expectation.getSortableId()}). This reproduces the
 * global total order, so a higher-priority fallthrough expectation still wins over
 * a lower-priority bucketed one.
 *
 * <p><b>Incremental (per-mutation) maintenance.</b> The index is maintained
 * INCREMENTALLY: {@link #onAdded}/{@link #onRemoved} update one bucket in O(1) as each
 * matcher enters or leaves the store. They are driven by a mutation listener the
 * {@code RequestMatchers} wires onto the backing {@code CircularPriorityQueue}, so every
 * structural mutation (add, remove, in-place update, priority re-key, overflow eviction,
 * reset) is reflected without a full rebuild. A read NEVER rebuilds — it reads a small
 * candidate set directly. This replaces the earlier generation-driven
 * rebuild-on-read-after-any-mutation design, which under continuous churn (the serving
 * path itself schedules lazy removal of {@code once()} / limited-{@code Times} matchers)
 * forced every request to rebuild the whole index AND the full sorted list — O(n) per
 * request and, being {@code synchronized}, serialising concurrent readers so it worsened
 * with cores. The incremental index is flat under churn and touches no shared lock on the
 * read path.
 *
 * <p><b>An in-place update that re-buckets is correct.</b> {@code RequestMatchers} performs
 * an in-place update as {@code removePriorityKey(matcher)} (the matcher still holds its OLD
 * method/path) then, after {@code matcher.update(newExpectation)}, {@code addPriorityKey(matcher)}
 * (now the NEW method/path). The listener maps these to {@link #onRemoved} (removes the id from
 * its OLD placement, looked up via the {@code locator} — never recomputed) then {@link #onAdded}
 * (places the id into its NEW bucket). The id is invariant across the update, so the move is exact
 * regardless of whether the bucket key changed.
 *
 * <p><b>Case mode.</b> Bucket keys are folded with {@code toLowerCase(ROOT)} when matching is
 * case-insensitive ({@code matchExactCase} off). The fold in force is fixed for the maintained
 * index and stored on the published {@link State}. A read that observes a different case mode
 * (a live {@code matchExactCase} change) triggers a one-off {@link #rebuild} from the
 * authoritative sorted snapshot under the index monitor — rare, and it never spans the scan.
 *
 * <p><b>Threading contract.</b> Mirrors {@code RequestMatchers}: control-plane mutations are
 * single-writer, and {@link #onAdded}/{@link #onRemoved}/{@link #rebuild} serialise on the index
 * monitor so a data-plane {@link #rebuild} can never race a writer. Reads
 * ({@link #candidatesInGlobalOrder}) run lock-free on data-plane threads over the published
 * {@link State} (a {@code volatile} reference to concurrent maps) and are eventually consistent —
 * a read concurrent with an in-flight mutation may not yet reflect it, exactly as the CPQ's
 * {@code toSortedList()} snapshot is.
 */
class CandidateIndex {

    private static final char METHOD_PATH_SEPARATOR = '\n';

    /**
     * Locator sentinel meaning "this id lives in the fallthrough, not a bucket". Contains no
     * {@link #METHOD_PATH_SEPARATOR}, which every real {@code method\npath} bucket key always
     * contains, so it can never collide with one.
     */
    // NOTE the escape. This sentinel is a NUL-prefixed string; it MUST be written as
    // \u0000 and never as a raw NUL byte in the source. A raw NUL is invisible to grep,
    // diff and code review -- it reads as absent -- so a corrupted or accidentally
    // duplicated sentinel could not be seen by any of them.
    private static final String FALLTHROUGH_KEY = "\u0000FALLTHROUGH";

    /**
     * Locator sentinel for the PATH-ONLY dimension: "this id's path is not a plain literal, so it
     * lives in the path fallthrough". Kept distinct from {@link #FALLTHROUGH_KEY} only so the two
     * dimensions' locators never alias; within each dimension the sentinel just means "not bucketed
     * in this dimension".
     * <p>
     * Note the collision guarantee here is WEAKER than {@link #FALLTHROUGH_KEY}'s and rests on
     * different facts, so do not read the two as equivalent. A composed (method, path) key always
     * contains a '\n' separator, which structurally cannot appear in the sentinel. A path key has
     * NO separator — {@link #composePathKey} returns the path itself — so the sentinel's safety is
     * instead:
     * <ul>
     *   <li>the path dimension is only ever READ in case-insensitive mode
     *       ({@link #clearCandidates} returns null under exact-case), and</li>
     *   <li>in that mode every bucket key is {@code toLowerCase(ROOT)}-folded, which cannot produce
     *       the uppercase letters in "PATH_FALLTHROUGH" — so no real key can equal the sentinel.</li>
     * </ul>
     * Under exact-case a literal path COULD in principle collide (it is stored unfolded), but the
     * dimension is never consulted then, and a later flip to case-insensitive triggers a full
     * rebuild. Hence no reachable defect — but the invariant is conditional, not structural, and a
     * future change that read the path buckets under exact-case would break it.
     */
    // Same escape discipline as FALLTHROUGH_KEY: write the NUL as \u0000, never a raw NUL byte.
    private static final String PATH_FALLTHROUGH_KEY = "\u0000PATH_FALLTHROUGH";

    /**
     * Published index state. The maps are concurrent so data-plane readers iterate them
     * lock-free while the single control-plane writer mutates them; the whole object is
     * swapped atomically (through the {@code volatile snapshot} reference) only on a case-mode
     * rebuild, so a read sees a wholly-consistent {@code (buckets, fallthrough, caseInsensitive)}
     * triple.
     */
    private static final class State {
        // bucketKey ("method\npath") -> (expectationId -> matcher). Inner maps are keyed by the
        // (unique, invariant) expectation id so an in-place update overwrites in place and a
        // bucket can legitimately hold several distinct expectations sharing one (method,path).
        final ConcurrentMap<String, ConcurrentMap<String, HttpRequestMatcher>> buckets = new ConcurrentHashMap<>();
        // expectationId -> matcher for every non-bucketable (fallthrough) expectation.
        final ConcurrentMap<String, HttpRequestMatcher> fallthrough = new ConcurrentHashMap<>();
        // expectationId -> where it is placed (a bucket key, or FALLTHROUGH_KEY). Lets onRemoved
        // find the exact placement in O(1) WITHOUT recomputing the bucket key, so removal is
        // robust even if the matcher's state changed between add and remove.
        final ConcurrentMap<String, String> locator = new ConcurrentHashMap<>();

        // ---- PATH-ONLY dimension (used ONLY by the control-plane clear fast path, never by the
        // data-plane read). An expectation is path-bucketed on its literal path ALONE, regardless
        // of whether its method is literal, so a path-only clear (no method, matches any method)
        // can narrow to the one path bucket plus the path fallthrough. This is a SEPARATE placement
        // from the (method,path) dimension above because their bucketable predicates differ: an
        // expectation with a literal path but a regex/blank/notted method is (method,path)-
        // fallthrough yet path-BUCKETED here.
        // pathKey (folded literal path) -> (expectationId -> matcher).
        final ConcurrentMap<String, ConcurrentMap<String, HttpRequestMatcher>> pathBuckets = new ConcurrentHashMap<>();
        // expectationId -> matcher for every expectation whose PATH is not a plain literal.
        final ConcurrentMap<String, HttpRequestMatcher> pathFallthrough = new ConcurrentHashMap<>();
        // expectationId -> its path placement (a path key, or PATH_FALLTHROUGH_KEY). Same O(1)-
        // removal role as {@code locator}, for the path dimension.
        final ConcurrentMap<String, String> pathLocator = new ConcurrentHashMap<>();

        final boolean caseInsensitive;

        State(boolean caseInsensitive) {
            this.caseInsensitive = caseInsensitive;
        }
    }

    private volatile State snapshot;

    CandidateIndex(boolean caseInsensitive) {
        this.snapshot = new State(caseInsensitive);
    }

    // ---- incremental maintenance (control-plane, single-writer; serialised for the rare rebuild) ----

    /**
     * Records that {@code matcher} has entered the store (or re-entered it after an in-place
     * update). Idempotent: any prior placement of the same id is removed first, so a re-add or a
     * missed {@link #onRemoved} can never leave a duplicate.
     */
    synchronized void onAdded(HttpRequestMatcher matcher) {
        String id = idOf(matcher);
        if (id == null) {
            return;
        }
        State state = snapshot;
        placeIntoState(state, id, matcher);
    }

    /**
     * Records that {@code matcher} has left the store. Uses the {@code locator} to remove the id
     * from its exact placement in O(1); a no-op if the id was never indexed.
     */
    synchronized void onRemoved(HttpRequestMatcher matcher) {
        String id = idOf(matcher);
        if (id == null) {
            return;
        }
        removeFromState(snapshot, id);
    }

    private static void placeIntoState(State state, String id, HttpRequestMatcher matcher) {
        // Drop any previous placement of this id first (handles re-add / re-bucket robustly).
        removeFromState(state, id);

        // (method, path) dimension — used by the data-plane read.
        String key = bucketKeyFor(matcher, state.caseInsensitive);
        if (key == null) {
            state.fallthrough.put(id, matcher);
            state.locator.put(id, FALLTHROUGH_KEY);
        } else {
            state.buckets.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(id, matcher);
            state.locator.put(id, key);
        }

        // Path-only dimension — used by the control-plane clear fast path. Placed independently:
        // an expectation with a literal path but a non-literal method is (method,path)-fallthrough
        // above yet path-bucketed here.
        String pathKey = pathKeyFor(matcher, state.caseInsensitive);
        if (pathKey == null) {
            state.pathFallthrough.put(id, matcher);
            state.pathLocator.put(id, PATH_FALLTHROUGH_KEY);
        } else {
            state.pathBuckets.computeIfAbsent(pathKey, k -> new ConcurrentHashMap<>()).put(id, matcher);
            state.pathLocator.put(id, pathKey);
        }
    }

    private static void removeFromState(State state, String id) {
        // (method, path) dimension.
        String key = state.locator.remove(id);
        if (key != null) {
            if (FALLTHROUGH_KEY.equals(key)) {
                state.fallthrough.remove(id);
            } else {
                ConcurrentMap<String, HttpRequestMatcher> bucket = state.buckets.get(key);
                if (bucket != null) {
                    bucket.remove(id);
                    // Reclaim an emptied bucket so a long churn of DISTINCT (method,path) keys
                    // (e.g. many one-time expectations) cannot leak empty maps. Single-writer, so
                    // the value-guarded remove only prevents dropping a bucket already replaced.
                    if (bucket.isEmpty()) {
                        state.buckets.remove(key, bucket);
                    }
                }
            }
        }

        // Path-only dimension (mirror of the above).
        String pathKey = state.pathLocator.remove(id);
        if (pathKey != null) {
            if (PATH_FALLTHROUGH_KEY.equals(pathKey)) {
                state.pathFallthrough.remove(id);
            } else {
                ConcurrentMap<String, HttpRequestMatcher> pathBucket = state.pathBuckets.get(pathKey);
                if (pathBucket != null) {
                    pathBucket.remove(id);
                    if (pathBucket.isEmpty()) {
                        state.pathBuckets.remove(pathKey, pathBucket);
                    }
                }
            }
        }
    }

    private static String idOf(HttpRequestMatcher matcher) {
        if (matcher == null || matcher.getExpectation() == null) {
            return null;
        }
        return matcher.getExpectation().getId();
    }

    /**
     * Returns the candidate matchers for the request in GLOBAL sorted order (the same
     * order {@code CircularPriorityQueue.toSortedList()} produces), so the first match
     * among them equals the first match of the full scan.
     *
     * <p>The index is maintained incrementally, so a read normally performs NO rebuild and
     * touches no shared lock. The only exception is a live {@code matchExactCase} change: if the
     * supplied case mode differs from the maintained one the index is rebuilt once from the
     * authoritative snapshot (rare — the config is effectively fixed at runtime).
     *
     * @param requestDefinition     the incoming request
     * @param caseInsensitiveNow    whether case-folding applies now (matchExactCase off)
     * @param authoritativeSnapshot supplier of the full sorted snapshot, used only for the
     *                              non-ASCII case-insensitive fallback and a case-mode rebuild
     *                              (never for a steady-state read)
     */
    List<HttpRequestMatcher> candidatesInGlobalOrder(
        RequestDefinition requestDefinition,
        boolean caseInsensitiveNow,
        Supplier<List<HttpRequestMatcher>> authoritativeSnapshot
    ) {
        State state = snapshot;
        if (state.caseInsensitive != caseInsensitiveNow) {
            // Pass the SUPPLIER, not its result. Evaluating it here would read the
            // authoritative list on this (unsynchronised) reader thread BEFORE rebuild
            // takes the monitor, so a concurrent onAdded could place a matcher into the
            // state we are about to replace and be silently lost — permanently, because
            // the incremental design has no generation re-check to self-heal it the way
            // the previous rebuild-on-generation design did.
            state = rebuild(authoritativeSnapshot, caseInsensitiveNow);
        }

        ConcurrentMap<String, HttpRequestMatcher> bucket = null;
        if (requestDefinition instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) requestDefinition;
            // Case-insensitive bucketing folds the key with toLowerCase(ROOT), which is NOT
            // equivalent to the matcher's char-by-char equalsIgnoreCase for non-ASCII characters
            // (e.g. Turkish dotted/dotless i U+0130/U+0131, long s U+017F): a non-ASCII request can
            // equalsIgnoreCase-match a bucketed pure-ASCII literal yet fold to a different bucket key
            // (U+0130 even changes length under toLowerCase). We therefore cannot narrow a non-ASCII
            // request in case-insensitive mode without risking a SILENT MISS of a bucketed
            // expectation — fall back to the full authoritative scan for this request (byte-for-byte
            // identical to the un-indexed path). Case-sensitive mode uses exact equality with no
            // fold, so a non-ASCII request can never exact-match a pure-ASCII bucketed literal and
            // narrowing stays sound.
            if (state.caseInsensitive && requestHasNonAsciiMethodOrPath(httpRequest)) {
                return authoritativeSnapshot.get();
            }
            String key = requestKeyFor(httpRequest, state.caseInsensitive);
            if (key != null) {
                bucket = state.buckets.get(key);
            }
        }
        // A non-HTTP request (DNS/binary/OpenAPI) cannot match any bucketed (literal HTTP
        // method+path) expectation, so its candidate set is the fallthrough only — which
        // is correct: all such expectations are themselves in the fallthrough.

        boolean hasFallthrough = !state.fallthrough.isEmpty();
        boolean hasBucket = bucket != null && !bucket.isEmpty();
        if (!hasFallthrough && !hasBucket) {
            // No candidate matches this request — a full miss allocates nothing.
            return java.util.Collections.emptyList();
        }

        // Common case (bucketable request, no fallthrough expectations): copy just the one small
        // bucket. Only when fallthrough expectations exist do we union the two, so the read path
        // allocates no more than the pre-existing "fallthrough copy + bucket addAll" did.
        List<HttpRequestMatcher> candidates;
        if (!hasFallthrough) {
            candidates = new ArrayList<>(bucket.values());
        } else {
            candidates = new ArrayList<>(state.fallthrough.values());
            if (hasBucket) {
                candidates.addAll(bucket.values());
            }
        }

        if (candidates.size() > 1) {
            candidates.sort((a, b) -> EXPECTATION_SORTABLE_PRIORITY_COMPARATOR.compare(
                sortableId(a), sortableId(b)
            ));
        }
        return candidates;
    }

    /**
     * Rebuilds the whole index from the authoritative sorted snapshot for a new case mode and
     * publishes it. Serialised on the index monitor against {@link #onAdded}/{@link #onRemoved}
     * so a data-plane rebuild can never lose a concurrent control-plane mutation. The
     * double-check avoids a redundant rebuild when two readers race the same mode flip. Holds no
     * lock that spans the matching scan.
     */
    private synchronized State rebuild(
        Supplier<List<HttpRequestMatcher>> authoritativeSnapshot,
        boolean caseInsensitive
    ) {
        State current = snapshot;
        if (current.caseInsensitive == caseInsensitive) {
            // Another reader already rebuilt for this case mode.
            return current;
        }
        // Read the authoritative list UNDER THIS MONITOR. onAdded/onRemoved serialise on
        // the same monitor, and the CPQ completes its skipList mutation and invalidates its
        // sorted snapshot before the listener callback, so any mutation whose callback finished
        // before we acquired the lock is already visible here, and any that has not yet
        // acquired it will apply to the fresh state after we publish. Capturing outside the
        // lock loses the former.
        List<HttpRequestMatcher> authoritative = authoritativeSnapshot.get();
        State fresh = new State(caseInsensitive);
        for (HttpRequestMatcher matcher : authoritative) {
            String id = idOf(matcher);
            if (id != null) {
                placeIntoState(fresh, id, matcher);
            }
        }
        this.snapshot = fresh;
        return fresh;
    }

    /**
     * Returns the narrowed CANDIDATE set for a control-plane {@code clear(RequestDefinition)} — the
     * only expectations whose request the clear matcher could possibly match — or {@code null} when
     * the clear shape cannot be soundly narrowed and the caller MUST fall back to scanning every
     * registered expectation.
     *
     * <p><b>Direction.</b> Unlike the data-plane read, the clear's request is the MATCHER and each
     * expectation's request is the SUBJECT ({@code clearMatcher.matches(expectation.request)}), a
     * control-plane reverse match. The narrowing is nonetheless sound for the same structural
     * reason: a candidate set that is a SUBSET of all expectations can only ever UNDER-remove, and
     * we only ever exclude an expectation when it provably cannot match the clear's literal path.
     *
     * <p><b>Soundness.</b> When the clear carries a pure-ASCII literal path, the reverse path match
     * reduces to literal (folded) equality against any other literal path (both sides are
     * regex-metacharacter-free, so as-regex each matches only itself), so an expectation in a
     * DIFFERENT literal-path bucket provably fails the clear's path criterion. Every expectation
     * whose path is NOT a plain literal lives in the {@code pathFallthrough} and is ALWAYS included
     * — that covers a regex-path expectation a literal clear still removes (bidirectional match),
     * and every notted/blank/schema/path-parameter path. Extra clear constraints (a literal method,
     * headers, query, body) only make it MORE selective and are still applied by the full reverse
     * match the caller runs on each returned candidate, so they never cause an under-removal.
     *
     * <p><b>Shapes.</b>
     * <ul>
     *   <li>literal path + literal method → the one {@code (method,path)} bucket ∪ {@code fallthrough}
     *       (the tighter set; a foreign method sits in a different (method,path) bucket);</li>
     *   <li>literal path + any non-literal method (absent/blank/regex/notted) → the one path bucket
     *       ∪ {@code pathFallthrough} (method imposes no bucket constraint);</li>
     *   <li>anything else (non-literal path, path parameters, a non-HTTP clear) → {@code null},
     *       forcing the full scan (e.g. a regex-path clear spans many literal buckets).</li>
     * </ul>
     * The clear's literal path/method come from {@link #literalValue}, which already rejects
     * non-ASCII, so no non-ASCII case-fold fallback is needed here (a non-ASCII expectation path is
     * itself in the path fallthrough and always included).
     *
     * <p><b>Case mode.</b> The control-plane clear matcher is ALWAYS case-insensitive, independent
     * of {@code matchExactCase} (see {@code HttpRequestPropertiesMatcher}, where
     * {@code caseSensitive = !controlPlaneMatcher && matchExactCase} is false for any clear filter).
     * So narrowing is only possible against a case-insensitively folded index, which exists only
     * when {@code matchExactCase} is OFF ({@code caseInsensitiveNow == true}); with exact-case ON we
     * return {@code null} (full scan) rather than under-remove against case-sensitive buckets. This
     * is the opposite of the data-plane read, whose index fold tracks {@code matchExactCase}.
     *
     * <p>Order is irrelevant — a clear removes EVERY match, not the first — so the union is returned
     * unsorted. A live {@code matchExactCase} flip to OFF is handled by a one-off {@link #rebuild}
     * to the case-insensitive fold under the monitor before the lookup.
     *
     * @param caseInsensitiveNow {@code !configuration.matchExactCase()} — true when the (case-
     *                           insensitive) clear can be served by the case-insensitively folded
     *                           index; false forces the full scan
     * @return the candidate matchers to run the full reverse match against, or {@code null} to mean
     * "cannot narrow — scan everything".
     */
    List<HttpRequestMatcher> clearCandidates(
        RequestDefinition clearRequest,
        boolean caseInsensitiveNow,
        Supplier<List<HttpRequestMatcher>> authoritativeSnapshot
    ) {
        if (!(clearRequest instanceof HttpRequest)) {
            return null;
        }
        HttpRequest httpRequest = (HttpRequest) clearRequest;

        // Path parameters on the CLEAR rewrite its path into a regex, so it is not a literal-path
        // clear and cannot be narrowed by a literal-path bucket lookup.
        Parameters pathParameters = httpRequest.getPathParameters();
        if (pathParameters != null && !pathParameters.isEmpty()) {
            return null;
        }

        // The control-plane clear matcher is ALWAYS case-insensitive, independent of matchExactCase
        // (HttpRequestPropertiesMatcher: caseSensitive = !controlPlaneMatcher && matchExactCase, so
        // false for any clear/verify/retrieve filter — administrative operations kept the historical
        // case-insensitive behaviour). We can therefore only narrow using a CASE-INSENSITIVELY
        // folded index. That index exists exactly when matchExactCase is OFF (caseInsensitiveNow ==
        // true); when exact-case is ON the maintained buckets are case-sensitive and cannot serve a
        // case-insensitive clear without under-removing (e.g. clear "/a2" must still remove "/A2"),
        // so fall back to the full scan. matchExactCase is off by default, so this fallback does not
        // cost the common case.
        if (!caseInsensitiveNow) {
            return null;
        }

        String literalPath = literalValue(httpRequest.getPath());
        if (literalPath == null) {
            // Blank/notted/regex/schema/non-ASCII path — cannot narrow (a regex clear legitimately
            // spans many literal buckets, and a non-ASCII path's case fold diverges from the
            // matcher's equalsIgnoreCase); scan everything.
            return null;
        }

        State state = snapshot;
        if (!state.caseInsensitive) {
            // matchExactCase was flipped OFF at runtime but no data-plane read has rebuilt the index
            // to the case-insensitive fold yet. Rebuild now (same discipline as
            // candidatesInGlobalOrder: pass the SUPPLIER, rebuild under the monitor, never capture
            // the authoritative list on this thread first) so the bucket keys are CI-folded.
            state = rebuild(authoritativeSnapshot, true);
        }

        // Fold with true unconditionally: the clear is case-insensitive and the bucket keys are now
        // guaranteed CI-folded (state.caseInsensitive == true).
        String literalMethod = literalValue(httpRequest.getMethod());
        if (literalMethod != null) {
            // (method, path) fast path — the tightest sound candidate set.
            String key = composeKey(literalMethod, literalPath, true);
            return union(state.fallthrough, state.buckets.get(key));
        }
        // Path-only fast path — the clear imposes no method bucket constraint.
        String pathKey = composePathKey(literalPath, true);
        return union(state.pathFallthrough, state.pathBuckets.get(pathKey));
    }

    /**
     * Concatenates an always-included fallthrough map's values with an optional bucket's values into
     * a fresh list. No sorting (clear order is irrelevant) and no dedup is needed: within a single
     * dimension an id is in EITHER the fallthrough OR exactly one bucket, never both.
     */
    private static List<HttpRequestMatcher> union(
        ConcurrentMap<String, HttpRequestMatcher> fallthrough,
        ConcurrentMap<String, HttpRequestMatcher> bucket
    ) {
        List<HttpRequestMatcher> candidates = new ArrayList<>(fallthrough.values());
        if (bucket != null) {
            candidates.addAll(bucket.values());
        }
        return candidates;
    }

    private static SortableExpectationId sortableId(HttpRequestMatcher matcher) {
        return matcher.getExpectation() != null
            ? matcher.getExpectation().getSortableId()
            : SortableExpectationId.NULL;
    }

    // ---- bucketable predicate ----

    /**
     * Returns the {@code (method, path)} bucket key for a matcher when — and only when
     * — the expectation matches exactly one literal {@code (method, path)} pair, or
     * {@code null} (meaning "fallthrough — checked on every request") otherwise.
     *
     * <p>EXTREMELY conservative: any doubt routes the expectation to the fallthrough.
     * An expectation is bucketable iff ALL hold:
     * <ul>
     *   <li>its request definition is a plain {@link HttpRequest} (not OpenAPI/DNS/binary);</li>
     *   <li>the matcher is an {@link HttpRequestPropertiesMatcher} (the only matcher whose
     *       method/path semantics this predicate reasons about);</li>
     *   <li>it declares NO path parameters (otherwise the matched path is rewritten to a
     *       {@code .*} regex by {@code PathParametersDecoder.normalisePathWithParametersForMatching});</li>
     *   <li>both method and path are plain literal values — non-null, not blank, not notted
     *       ({@code !}), not optional, not a schema/OpenAPI string, and containing no regex
     *       metacharacter and only ASCII characters (so the matcher's anchored-regex path is
     *       provably equivalent to a literal (case-insensitive) string equals).</li>
     * </ul>
     */
    private static String bucketKeyFor(HttpRequestMatcher matcher, boolean caseInsensitive) {
        if (!(matcher instanceof HttpRequestPropertiesMatcher)) {
            return null;
        }
        Expectation expectation = matcher.getExpectation();
        if (expectation == null) {
            return null;
        }
        RequestDefinition requestDefinition = expectation.getHttpRequest();
        if (!(requestDefinition instanceof HttpRequest)) {
            return null;
        }
        HttpRequest httpRequest = (HttpRequest) requestDefinition;

        // Path parameters rewrite the matched path into a regex — not bucketable.
        Parameters pathParameters = httpRequest.getPathParameters();
        if (pathParameters != null && !pathParameters.isEmpty()) {
            return null;
        }

        String method = literalValue(httpRequest.getMethod());
        if (method == null) {
            return null;
        }
        String path = literalValue(httpRequest.getPath());
        if (path == null) {
            return null;
        }
        return composeKey(method, path, caseInsensitive);
    }

    /**
     * Returns the folded literal-path key for a matcher when — and only when — the expectation's
     * PATH is a plain literal, or {@code null} ("path fallthrough — a path-only clear must check it
     * regardless") otherwise. Deliberately says NOTHING about the method: unlike
     * {@link #bucketKeyFor}, an expectation with a literal path is path-bucketable even when its
     * method is a regex/blank/notted matcher, because the path-only clear fast path applies no
     * method criterion of its own (a blank clear method matches any expectation method) and the
     * full reverse match still runs on each candidate.
     *
     * <p>Same conservative literal test as {@link #bucketKeyFor}'s path leg: a plain
     * {@link HttpRequest} {@link HttpRequestPropertiesMatcher}, no path parameters (they rewrite the
     * matched path into a {@code .*} regex), and a non-null, non-blank, non-notted, non-optional,
     * non-schema, pure-ASCII, regex-metacharacter-free path value.
     */
    private static String pathKeyFor(HttpRequestMatcher matcher, boolean caseInsensitive) {
        if (!(matcher instanceof HttpRequestPropertiesMatcher)) {
            return null;
        }
        Expectation expectation = matcher.getExpectation();
        if (expectation == null) {
            return null;
        }
        RequestDefinition requestDefinition = expectation.getHttpRequest();
        if (!(requestDefinition instanceof HttpRequest)) {
            return null;
        }
        HttpRequest httpRequest = (HttpRequest) requestDefinition;

        Parameters pathParameters = httpRequest.getPathParameters();
        if (pathParameters != null && !pathParameters.isEmpty()) {
            return null;
        }

        String path = literalValue(httpRequest.getPath());
        if (path == null) {
            return null;
        }
        return composePathKey(path, caseInsensitive);
    }

    /**
     * Returns the plain literal string value of a method/path matcher component, or
     * {@code null} when the component is anything other than a plain literal (blank,
     * notted, optional, schema, or containing a regex metacharacter / non-ASCII char).
     */
    private static String literalValue(NottableString nottableString) {
        if (nottableString == null) {
            return null;
        }
        // Schema (OpenAPI) and optional matchers are never plain literal equality.
        if (nottableString instanceof NottableSchemaString || nottableString instanceof NottableOptionalString) {
            return null;
        }
        if (nottableString.isOptional() || nottableString.isNot()) {
            return null;
        }
        if (nottableString.isBlank()) {
            // A blank matcher matches ANY value — must be checked on every request.
            return null;
        }
        String value = nottableString.getValue();
        if (value == null) {
            return null;
        }
        // Must be a pure-ASCII literal so the matcher's anchored-regex comparison is
        // provably equivalent to a (case-insensitive) literal equals. Mirrors the
        // RegexStringMatcher pure-ASCII-literal short-circuit.
        if (!isPureAsciiLiteral(value)) {
            return null;
        }
        return value;
    }

    private static String requestKeyFor(HttpRequest request, boolean caseInsensitive) {
        NottableString method = request.getMethod();
        NottableString path = request.getPath();
        if (method == null || path == null) {
            return null;
        }
        String methodValue = method.getValue();
        String pathValue = path.getValue();
        if (methodValue == null || pathValue == null) {
            return null;
        }
        return composeKey(methodValue, pathValue, caseInsensitive);
    }

    /**
     * True when the request's method or path value contains a non-ASCII character. Used to force
     * the full-scan fallback in case-insensitive mode, where toLowerCase(ROOT) bucket-key folding
     * diverges from the matcher's char-by-char equalsIgnoreCase (see {@link #candidatesInGlobalOrder}).
     */
    private static boolean requestHasNonAsciiMethodOrPath(HttpRequest request) {
        NottableString method = request.getMethod();
        NottableString path = request.getPath();
        return (method != null && !isPureAscii(method.getValue()))
            || (path != null && !isPureAscii(path.getValue()));
    }

    private static String composeKey(String method, String path, boolean caseInsensitive) {
        if (caseInsensitive) {
            method = method.toLowerCase(Locale.ROOT);
            path = path.toLowerCase(Locale.ROOT);
        }
        return method + METHOD_PATH_SEPARATOR + path;
    }

    /**
     * Folds a literal path into a path-dimension bucket key. Folds with {@code toLowerCase(ROOT)}
     * in case-insensitive mode so a case-insensitive literal clear matches a literal expectation
     * differing only in case — the same fold {@link #composeKey} applies to the path segment. No
     * separator is prefixed: every value here is a real path, and the path fallthrough is keyed by
     * the NUL-sentinel {@link #PATH_FALLTHROUGH_KEY}, never by a path value.
     */
    private static String composePathKey(String path, boolean caseInsensitive) {
        return caseInsensitive ? path.toLowerCase(Locale.ROOT) : path;
    }

    // ---- pure-ASCII-literal test (mirrors RegexStringMatcher) ----

    private static boolean isPureAsciiLiteral(String s) {
        return !looksLikeRegex(s) && isPureAscii(s);
    }

    private static boolean looksLikeRegex(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '\\':
                case '.':
                case '[':
                case ']':
                case '{':
                case '}':
                case '(':
                case ')':
                case '*':
                case '+':
                case '?':
                case '^':
                case '$':
                case '|':
                    return true;
                default:
                    // continue scanning
            }
        }
        return false;
    }

    private static boolean isPureAscii(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}

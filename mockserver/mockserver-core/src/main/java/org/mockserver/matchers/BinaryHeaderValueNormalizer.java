package org.mockserver.matchers;

import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.NottableOptionalString;
import org.mockserver.model.NottableMatcherString;
import org.mockserver.model.NottableString;

/**
 * Padding-insensitive comparison support for gRPC binary metadata header values.
 * <p>
 * <strong>The contract.</strong> The gRPC wire format requires the value of any metadata key whose
 * name ends {@code -bin} to be base64. MockServer treats a {@code -bin} value as <em>already
 * base64-encoded by the user</em> and passes it through unchanged in both directions — it never
 * encodes or decodes. Base64 is pure ASCII, so the value survives every transport site unaltered
 * (US-ASCII gRPC-Web trailers, CR/LF header sanitisation, HPACK/QPACK, JSON serialisation).
 * <p>
 * <strong>The one place pass-through is not enough: matching.</strong> grpc-java encodes outbound
 * binary metadata with {@code BASE64_ENCODING_OMIT_PADDING}
 * ({@code io.grpc.internal.TransportFrameUtil#toHttp2Headers}), so the value that actually arrives
 * on the wire has <em>no</em> {@code =} padding — {@code AQIDBA}, not {@code AQIDBA==}. A user who
 * writes the natural, padded form produced by {@code Base64.getEncoder()} in an expectation would
 * otherwise silently fail to match every real gRPC client. Both forms decode to identical bytes and
 * are therefore the same value, so the matcher compares them with the padding removed.
 * <p>
 * Normalisation is deliberately narrow:
 * <ul>
 *   <li>It applies only when the header <em>name</em> ends {@code -bin} (case-insensitively), so
 *       ordinary HTTP header matching is untouched.</li>
 *   <li>It applies only to header maps, not to query-string or path parameters.</li>
 *   <li>It strips trailing {@code =} only from a <em>structurally valid</em> padded base64 value —
 *       base64 alphabet characters, one or two {@code =}, and a total length that is a multiple of 4.
 *       A JSON-schema matcher is never touched. Note that {@code +} and {@code /} are base64
 *       alphabet characters as well as regex metacharacters, so a regex is left as written only when
 *       it fails that structural test; the length invariant is what makes the common metacharacter
 *       case ({@code A+=}) safe. See {@link #stripPaddingIfPaddedBase64}.</li>
 *   <li>Both the matcher side and the matched side are normalised through the same function, so the
 *       comparison stays symmetric and a padded expectation matches a padded actual value just as it
 *       did before.</li>
 * </ul>
 *
 * @see org.mockserver.collections.SubSetMatcher
 * @see org.mockserver.collections.NottableStringMultiMap
 */
public class BinaryHeaderValueNormalizer {

    /**
     * The gRPC binary metadata key suffix ({@code io.grpc.Metadata#BINARY_HEADER_SUFFIX}).
     */
    public static final String BINARY_HEADER_SUFFIX = "-bin";

    private static final char PADDING_CHAR = '=';

    private BinaryHeaderValueNormalizer() {
    }

    /**
     * Returns true when the given header name is a gRPC binary metadata key, i.e. it ends
     * {@code -bin}. Matched case-insensitively because HTTP/2 lower-cases header names on the wire
     * but an expectation may be written in any case.
     * <p>
     * A name of exactly {@code -bin} counts, matching {@code io.grpc.Metadata.Key}, which tests the
     * suffix without requiring a non-empty prefix. A name that merely <em>contains</em> the letters
     * (e.g. {@code x-cabin}) does not — the suffix must be preceded by the hyphen it includes.
     */
    public static boolean isBinaryHeaderName(NottableString name) {
        if (name == null) {
            return false;
        }
        String value = name.getValue();
        return value != null
            && value.length() >= BINARY_HEADER_SUFFIX.length()
            && value.regionMatches(true, value.length() - BINARY_HEADER_SUFFIX.length(), BINARY_HEADER_SUFFIX, 0, BINARY_HEADER_SUFFIX.length());
    }

    /**
     * Returns true when a value comparison for the entry pair should be normalised: at least one of
     * the two keys is a {@code -bin} key, and neither value is a JSON-schema matcher (a schema is
     * evaluated against the raw string and must not be rewritten).
     * <p>
     * Either key is enough because the matcher-side key may be a regular expression (e.g.
     * {@code x-.*}) that matches a literal {@code -bin} key on the request side. Triggering on the
     * matcher key alone would not make the comparison asymmetric — this gate governs both
     * normalisations, so both sides would simply stay raw — it would just silently miss the
     * regex-key case and leave a padded expectation failing to match. (Asymmetry <em>would</em>
     * arise from normalising at map-construction time instead, where each map only sees its own
     * keys; that is the reason this is decided at the comparison site.)
     */
    public static boolean shouldNormalize(NottableString matcherKey, NottableString matchedKey, NottableString matcherValue, NottableString matchedValue) {
        if (matcherValue instanceof NottableMatcherString || matchedValue instanceof NottableMatcherString) {
            return false;
        }
        return isBinaryHeaderName(matcherKey) || isBinaryHeaderName(matchedKey);
    }

    /**
     * Compares two {@code -bin} values with base64 padding removed from both, reporting any mismatch
     * against the values as <em>originally written</em>.
     * <p>
     * The normalised comparison runs with a {@code null} {@link MatchDifference} so it cannot record
     * a difference naming the padding-stripped forms: a user who wrote {@code AQIDBA==} and mistyped
     * the payload would otherwise be told the expected value was {@code AQIDBA}, sending them to look
     * for a padding bug that does not exist. On failure a single difference is added here from the
     * original values, noting that padding was ignored so the reader knows why two visibly different
     * spellings were still considered unequal.
     */
    public static boolean matchesIgnoringPadding(RegexStringMatcher regexStringMatcher, MockServerLogger mockServerLogger, MatchDifference context, NottableString matcherValue, NottableString matchedValue) {
        boolean matches = regexStringMatcher.matches(mockServerLogger, null, normalize(matcherValue), normalize(matchedValue));
        if (!matches && context != null) {
            context.addDifference(mockServerLogger,
                "string or regex match failed expected:{}found:{}(gRPC binary metadata: base64 padding ignored)",
                matcherValue, matchedValue);
        }
        return matches;
    }

    /**
     * Returns the value with base64 padding removed when it is entirely padded base64, otherwise the
     * value unchanged. The {@code not} and {@code optional} markers are preserved, so
     * {@code !AQIDBA==} normalises to {@code !AQIDBA} and keeps asserting inequality.
     */
    public static NottableString normalize(NottableString value) {
        if (value == null || value instanceof NottableMatcherString) {
            return value;
        }
        String raw = value.getValue();
        // fast path: the wire form produced by every real gRPC client is already unpadded, so the
        // overwhelmingly common case costs a single character comparison
        if (raw == null || raw.isEmpty() || raw.charAt(raw.length() - 1) != PADDING_CHAR) {
            return value;
        }
        String stripped = stripPaddingIfPaddedBase64(raw);
        if (stripped.equals(raw)) {
            return value;
        }
        if (value.isOptional()) {
            return NottableOptionalString.optional(stripped, value.isNot());
        }
        return NottableString.string(stripped, value.isNot());
    }

    /**
     * Strips trailing {@code =} characters only from a <em>structurally valid</em> padded base64
     * string: base64 alphabet characters, then exactly one or two {@code =}, with a total length that
     * is a multiple of 4. Anything else is returned untouched.
     * <p>
     * <strong>The length check is load-bearing, not belt-and-braces.</strong> {@code +} is both a
     * base64 alphabet character and a regex metacharacter, so without it the alphabet test alone
     * accepts {@code A+=} and rewrites it to the regex {@code A+} — silently broadening an
     * expectation from one literal value to "one or more A". Real base64 is always a multiple of 4
     * in total length ({@code AQIDBA==} → 8, {@code AQIDBAU=} → 8), so requiring that rejects
     * {@code A+=} (3), {@code AQID==} (6) and {@code a=} (2) while admitting every legitimate value.
     */
    private static String stripPaddingIfPaddedBase64(String raw) {
        int end = raw.length();
        int padding = 0;
        while (end > 0 && raw.charAt(end - 1) == PADDING_CHAR) {
            end--;
            padding++;
        }
        if (padding < 1 || padding > 2 || end == 0 || (end + padding) % 4 != 0) {
            return raw;
        }
        for (int i = 0; i < end; i++) {
            if (!isBase64AlphabetChar(raw.charAt(i))) {
                return raw;
            }
        }
        return raw.substring(0, end);
    }

    /**
     * The <em>standard</em> base64 alphabet only. Deliberately not the URL-safe alphabet: the gRPC
     * spec mandates standard base64 for {@code -bin} metadata and grpc-java decodes it with
     * {@code BaseEncoding.base64()}, which rejects {@code -} and {@code _}. Keeping the test tight
     * also keeps ordinary hyphenated text (e.g. {@code some-value=}) out of the stripping path.
     */
    private static boolean isBase64AlphabetChar(char c) {
        return (c >= 'A' && c <= 'Z')
            || (c >= 'a' && c <= 'z')
            || (c >= '0' && c <= '9')
            || c == '+' || c == '/';
    }
}

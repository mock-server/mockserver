package org.mockserver.mock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Build-time guard that keeps {@link HttpState#isControlPlanePathCandidate(String)} in step with the
 * routes it gates.
 *
 * <p><b>The hazard.</b> The gate is a hand-maintained allow-list consulted as a fast reject before
 * the two dispatch chains. Returning {@code true} for a non-route is harmless, but returning
 * {@code false} for a real control-plane route makes that route silently unreachable — a 404-shaped
 * failure with no exception and no log.
 *
 * <p><b>Why the corpus test is not enough.</b>
 * {@code HttpStateControlPlaneGateTest#everyControlPlanePathIsACandidate} asserts the gate accepts
 * every alias <em>listed in that test</em>. Both it and the gate are hand-written copies of the same
 * routing table, so a route added to {@code HttpState.handle} without touching either stays green in
 * both. This guard closes that gap by deriving the expected set from the routing source itself, so
 * the check is bound to the thing it guards rather than to a second copy of the answer.
 *
 * <p><b>Honest limitations — a text scanner, not a Java parser.</b> It recognises the two shapes the
 * routing chains actually use: the canonical two-form
 * {@code matches("METHOD", PATH_PREFIX + "/x", "/x")} call, and trailing-slash path literals that
 * denote {@code {name}}-style route prefixes. Comments are stripped before scanning, so prose
 * mentioning a path cannot make the prefix scan demand a gate entry for it. It therefore MISSES a route whose bare alias is built
 * at runtime, held in a variable or constant, or written in a shape not listed above. A prefixed-only
 * route (no bare alias) needs no entry and is intentionally not scanned, being covered by the gate's
 * {@code PATH_PREFIX} rule. The fail-closed minimums below mean a regex that stops matching breaks
 * the build rather than passing vacuously.
 */
public class ControlPlaneGateCoverageTest {

    /** Surefire runs with the module basedir ({@code mockserver-core}) as the working directory. */
    private static final Path MODULES_ROOT = Paths.get("..");

    private static final Path[] ROUTING_SOURCES = {
        MODULES_ROOT.resolve("mockserver-core/src/main/java/org/mockserver/mock/HttpState.java"),
        MODULES_ROOT.resolve("mockserver-netty/src/main/java/org/mockserver/netty/HttpRequestHandler.java")
    };

    /** A broken scan (wrong CWD, regex rot) must fail closed, not scan nothing and pass. */
    private static final int MIN_EXPECTED_TWO_FORM_SITES = 80;
    private static final int MIN_EXPECTED_PREFIX_LITERALS = 4;

    /** {@code matches("PUT", PATH_PREFIX + "/expectation", "/expectation")} — the bare form is group 2. */
    private static final Pattern TWO_FORM_ROUTE = Pattern.compile(
        "matches\\s*\\(\\s*\"[A-Z]+\"\\s*,\\s*PATH_PREFIX\\s*\\+\\s*\"(/[^\"]*)\"\\s*,\\s*\"(/[^\"]*)\"\\s*\\)");

    /** The same two-form call, capturing its method (group 1) and bare form (group 2). */
    private static final Pattern TWO_FORM_ROUTE_WITH_METHOD = Pattern.compile(
        "matches\\s*\\(\\s*\"([A-Z]+)\"\\s*,\\s*PATH_PREFIX\\s*\\+\\s*\"/[^\"]*\"\\s*,\\s*\"(/[^\"]*)\"\\s*\\)");

    /** Block and line comments, blanked before scanning so prose cannot create a false route. */
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    /** A path literal ending in {@code /} denotes a {@code {name}}-style route prefix. */
    private static final Pattern PREFIX_LITERAL = Pattern.compile("\"(/[A-Za-z0-9/]{2,}/)\"");

    @Test
    public void everyBareRouteAliasInTheRoutingSourcesIsAcceptedByTheGate() throws IOException {
        Set<String> bareAliases = new LinkedHashSet<>();
        int sites = 0;

        for (Path source : ROUTING_SOURCES) {
            assertTrue("expected routing source at " + source.toAbsolutePath()
                    + " (this guard must run with the mockserver-core module as the working directory)",
                Files.isRegularFile(source));
            Matcher matcher = TWO_FORM_ROUTE.matcher(readWithoutComments(source));
            while (matcher.find()) {
                sites++;
                assertEquals("route declares a prefixed form whose suffix differs from its bare form,"
                        + " so this guard cannot tell which the gate must accept: " + matcher.group(),
                    matcher.group(1), matcher.group(2));
                bareAliases.add(matcher.group(2));
            }
        }

        assertTrue("scanned only " + sites + " two-form route sites, expected at least "
                + MIN_EXPECTED_TWO_FORM_SITES + " — the scan is broken, failing closed rather than"
                + " passing vacuously", sites >= MIN_EXPECTED_TWO_FORM_SITES);

        for (String alias : bareAliases) {
            assertTrue("control-plane route \"" + alias + "\" is declared in the routing chain but"
                    + " HttpState.isControlPlanePathCandidate rejects its bare form, so the route is"
                    + " unreachable without the /mockserver prefix — add it to"
                    + " CONTROL_PLANE_BARE_ALIASES",
                HttpState.isControlPlanePathCandidate(alias));
            assertTrue("control-plane route \"" + alias + "\" is rejected in its prefixed form",
                HttpState.isControlPlanePathCandidate(HttpState.PATH_PREFIX + alias));
        }
    }

    @Test
    public void everyBareRouteMethodInTheRoutingSourcesIsAControlPlaneRouteForPreflights() throws IOException {
        int sites = 0;
        for (Path source : ROUTING_SOURCES) {
            Matcher matcher = TWO_FORM_ROUTE_WITH_METHOD.matcher(readWithoutComments(source));
            while (matcher.find()) {
                sites++;
                assertTrue("control-plane route " + matcher.group(1) + " " + matcher.group(2) + " is declared in the routing"
                        + " chain but HttpState.isControlPlaneRoute rejects it, so a CORS preflight for it would be decided by"
                        + " an expectation or the corsAllowOrigin policy — add the method to CONTROL_PLANE_BARE_ROUTE_METHODS",
                    HttpState.isControlPlaneRoute(matcher.group(1), matcher.group(2)));
            }
        }
        assertTrue("scanned only " + sites + " two-form route sites, failing closed", sites >= MIN_EXPECTED_TWO_FORM_SITES);
    }

    @Test
    public void namedRoutePrefixMethodsAreControlPlaneRoutesForPreflights() {
        String[][] routes = {
            {"GET", "/scenario"}, {"GET", "/scenario/checkout"}, {"PUT", "/scenario/checkout"},
            {"GET", "/loadScenario/name"}, {"GET", "/loadScenario/name/report"}, {"DELETE", "/loadScenario/name"},
            {"GET", "/chaosExperiment/profiles/name"}, {"PUT", "/chaosExperiment/profiles/name"}, {"DELETE", "/chaosExperiment/profiles/name"},
            {"POST", "/chaosExperiment/apply/name"},
            {"POST", "/mockserver/anything"}, {"OPTIONS", "/mockserver"}, {"GET", "/_mockserver_ui_websocket"},
        };
        for (String[] route : routes) {
            assertTrue(route[0] + " " + route[1], HttpState.isControlPlaneRoute(route[0], route[1]));
        }
        String[][] dataPlane = {
            {"POST", "/graphql"}, {"GET", "/status"}, {"POST", "/config"}, {"POST", "/scenario/checkout"},
            {"GET", "/api/orders"}, {"PUT", "/api/orders"},
        };
        for (String[] route : dataPlane) {
            assertTrue(route[0] + " " + route[1], !HttpState.isControlPlaneRoute(route[0], route[1]));
        }
    }

    @Test
    public void everyNameRoutePrefixInTheRoutingSourcesIsAcceptedByTheGate() throws IOException {
        Set<String> prefixes = new LinkedHashSet<>();
        for (Path source : ROUTING_SOURCES) {
            Matcher matcher = PREFIX_LITERAL.matcher(readWithoutComments(source));
            while (matcher.find()) {
                prefixes.add(matcher.group(1));
            }
        }

        assertTrue("found only " + prefixes.size() + " route-prefix literals, expected at least "
                + MIN_EXPECTED_PREFIX_LITERALS + " — the scan is broken, failing closed",
            prefixes.size() >= MIN_EXPECTED_PREFIX_LITERALS);

        for (String prefix : prefixes) {
            String probe = prefix + "guard-probe-name";
            assertTrue("{name}-style route prefix \"" + prefix + "\" is declared in the routing chain"
                    + " but HttpState.isControlPlanePathCandidate rejects \"" + probe + "\", so named"
                    + " routes under it are unreachable without the /mockserver prefix — add it to"
                    + " CONTROL_PLANE_BARE_PREFIXES",
                HttpState.isControlPlanePathCandidate(probe));
            assertTrue("prefixed form of route prefix \"" + prefix + "\" is rejected",
                HttpState.isControlPlanePathCandidate(HttpState.PATH_PREFIX + probe));
        }
    }

    /**
     * Source text with {@code //} and block comments blanked out. The prefix scan matches any
     * trailing-slash path literal, so prose quoting a path would otherwise make this guard demand a
     * gate entry for a route that does not exist — failing closed, but spuriously, and a guard that
     * cries wolf gets deleted. Newlines are preserved so reported positions stay meaningful.
     */
    private static String readWithoutComments(Path source) throws IOException {
        String content = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        return COMMENT.matcher(content).replaceAll(match -> blankKeepingNewlines(match.group()));
    }

    private static String blankKeepingNewlines(String comment) {
        StringBuilder blanked = new StringBuilder(comment.length());
        for (int i = 0; i < comment.length(); i++) {
            char character = comment.charAt(i);
            blanked.append(character == '\n' || character == '\r' ? character : ' ');
        }
        return blanked.toString();
    }
}

package org.mockserver.testing;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.mockserver.testing.EphemeralListenerBindGuardTest.MODULES_ROOT;
import static org.mockserver.testing.EphemeralListenerBindGuardTest.blank;
import static org.mockserver.testing.EphemeralListenerBindGuardTest.lineOf;
import static org.mockserver.testing.EphemeralListenerBindGuardTest.module;
import static org.mockserver.testing.EphemeralListenerBindGuardTest.testSources;

/**
 * Build-time guard: test code must not find a UDP port and leave MockServer to bind it as its HTTP/3 port.
 *
 * <h2>Why</h2>
 * <p>MockServer binds {@code http3Port} itself and cannot be asked for an ephemeral port, so a port a test found
 * can be taken before the server binds it, and the server then refuses to start.
 * {@code Http3TestServer.startWithHttp3} starts again on another port when that happens; a test that sets
 * {@code http3Port} itself fails as often as its port is taken. So in every module's test sources, and the main
 * sources of the test-support modules and the benchmarks, each {@code findFreeUdpPort}, each {@code http3Port(...)} given anything but
 * {@code 0} outside the arguments of a {@code startWithHttp3(...)} call, and each {@code mockserver.http3Port}
 * or {@code MOCKSERVER_HTTP3_PORT} outside a comment must be counted in {@link #ALLOWED} with a reason.
 *
 * <h2>Limits</h2>
 * <p>A textual check. Everything inside the arguments of a call named {@code startWithHttp3} is taken to be
 * started by the starter, whatever port it sets. A helper that takes a port and sets {@code http3Port} is counted
 * where it sets it, not where it is called. Two files of one name in a module share an {@link #ALLOWED} entry.
 * <p>Not seen: a port found another way and bound without {@code http3Port}
 * ({@code new Http3Server().start(port)}); a property or variable name built from parts; and an {@code http3Port}
 * read from a properties or JSON file.
 */
public class Http3PortFindThenBindGuardTest {

    private static final Pattern FOUND_UDP_PORT = Pattern.compile("\\bfindFreeUdpPort\\s*\\(|::\\s*findFreeUdpPort\\b");

    private static final Pattern HTTP3_PORT_CALL = Pattern.compile("\\bhttp3Port\\s*\\(");

    private static final Pattern HTTP3_PORT_NAME = Pattern.compile("mockserver\\.http3Port|MOCKSERVER_HTTP3_PORT");

    private static final String STARTER = "startWithHttp3";

    private static final Pattern STARTER_CALL = Pattern.compile("\\b" + STARTER + "\\s*\\(");

    // this file's patterns and self-tests name the property and the variable
    private static final String SELF = "mockserver-core/" + Http3PortFindThenBindGuardTest.class.getSimpleName() + ".java";

    /**
     * Found UDP ports and {@code http3Port}s set outside the starter that are meant, keyed by
     * {@code module/File.java}, with how many the file has.
     */
    private static final List<Allowed> ALLOWED = List.of(
        allowed("mockserver-core/TemplateHelpersTest.java", 1,
            "sets http3Port on a Configuration only the extension registry reads; no server starts, so nothing binds it"),
        allowed("mockserver-integration-testing/TestPortFactory.java", 1,
            "declares findFreeUdpPort"),
        allowed("mockserver-netty/Http3TestServer.java", 2,
            "the starter: takes its candidates from findFreeUdpPort and starts again when one is taken"),
        allowed("mockserver-netty/Http3TestServerTest.java", 1,
            "gives the starter its candidates after the held ports the tests put first"),
        allowed("mockserver-netty/Http3ServerIpv4PortConflictTest.java", 2,
            "probes a found port, and looks for one free on both stacks to start and restart a bare Http3Server on"),
        allowed("mockserver-netty/Http3StartFailureTest.java", 6,
            "asserts the refusal of a port another socket holds and of 70000; finds a port to hold on both stacks"),
        allowed("mockserver-netty/Http3LifecycleTest.java", 2,
            "asserts that start-up fails where the QUIC native is missing, so the port is never bound"),
        allowed("mockserver-netty/Http3NativeStartupIntegrationTest.java", 2,
            "passes the port to a forked jar: through the starter where HTTP/3 must start, found only where the native cannot load"),
        allowed("mockserver-netty/DnsLocalBoundIpTest.java", 1,
            "binds a found port as an explicit dnsPort, not http3Port, and tries another when one is taken"),
        allowed("mockserver-netty/MainTest.java", 2,
            "asserts the exit code for a port another socket holds and for 70000"),
        allowed("mockserver-netty/DnsPortTakenAfterProbeTest.java", 1,
            "finds a port free on both stacks as the dnsPort another socket then takes just after MockServer's probe"),
        allowed("mockserver-netty/Ipv4UdpLoopbackProbeTest.java", 2,
            "binds a bare listener on a found port, trying another if it is taken, and finds one nothing listens on"),
        allowed("mockserver-testcontainers/MockServerContainerConfigTest.java", 1,
            "asserts a container's environment; nothing binds the port on this host")
    );

    @Test
    public void shouldLeaveAFoundUdpPortToTheHttp3Starter() throws IOException {
        List<Path> sources = testSources();
        Set<String> modules = sources.stream().map(EphemeralListenerBindGuardTest::module).collect(Collectors.toCollection(TreeSet::new));
        assertThat("must scan the netty and test-support sources: " + modules, modules, hasItems("mockserver-netty", "mockserver-integration-testing", "mockserver-testcontainers"));
        assertThat("must scan a representative source tree", sources.size(), greaterThan(1000));

        Map<String, List<String>> offendersByFile = new TreeMap<>();
        int starterCalls = 0;
        for (Path source : sources) {
            String file = module(source) + "/" + source.getFileName();
            if (file.equals(SELF)) {
                continue;
            }
            String code = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            Matcher starterCall = STARTER_CALL.matcher(blank(code, true));
            while (starterCall.find()) {
                starterCalls++;
            }
            for (String offender : bareHttp3Ports(code)) {
                offendersByFile.computeIfAbsent(file, key -> new ArrayList<>()).add(MODULES_ROOT.relativize(source) + ":" + offender);
            }
        }
        assertThat("must find the HTTP/3 tests that start through Http3TestServer", starterCalls, greaterThan(20));

        List<String> problems = problems(offendersByFile, ALLOWED);
        assertThat("test code that finds a UDP port, or sets http3Port, outside Http3TestServer.startWithHttp3 - MockServer "
                + "binds http3Port itself, so a found port can be taken first and the server then refuses to start; start the "
                + "server with Http3TestServer.startWithHttp3, which starts again on another port, or add a reasoned entry to "
                + "ALLOWED:\n" + String.join("\n", problems),
            problems, is(empty()));
    }

    @Test
    public void shouldFlagAFoundUdpPort() {
        assertThat(bareHttp3Ports("int udpPort = TestPortFactory.findFreeUdpPort();"), contains("1 findFreeUdpPort("));
        assertThat("statically imported", bareHttp3Ports("import static org.mockserver.testing.socket.TestPortFactory.findFreeUdpPort;\n\nint udpPort = findFreeUdpPort ();"),
            contains("3 findFreeUdpPort ("));
        assertThat("as a method reference", bareHttp3Ports("IntSupplier candidates = TestPortFactory::findFreeUdpPort;"), contains("1 ::findFreeUdpPort"));
        assertThat("a helper of its own by the same name", bareHttp3Ports("private static int findFreeUdpPort() {\n return port(new DatagramSocket(0));\n}"), contains("1 findFreeUdpPort("));
        assertThat("inside the starter's arguments too", bareHttp3Ports("server = startWithHttp3(TestPortFactory::findFreeUdpPort, start, bound, stop);"), contains("1 ::findFreeUdpPort"));
    }

    @Test
    public void shouldFlagAnHttp3PortSetOutsideTheStarter() {
        assertThat(bareHttp3Ports("server = new MockServer(configuration().http3Port(udpPort), 0);"), contains("1 http3Port(udpPort)"));
        assertThat("a fixed port", bareHttp3Ports("a();\nConfigurationProperties.http3Port( 8443 );"), contains("2 http3Port( 8443 )"));
        assertThat("found and set in one statement", bareHttp3Ports("configuration().http3Port(TestPortFactory.findFreeUdpPort());"),
            contains("1 http3Port(TestPortFactory.findFreeUdpPort())", "1 findFreeUdpPort("));
        assertThat("an argument with brackets of its own", bareHttp3Ports("configuration.http3Port(port(holder, \")\")).logLevel(\"WARN\");"), contains("1 http3Port(port(holder, \")\"))"));
        assertThat("over several lines", bareHttp3Ports("configuration()\n .http3Port(\n  udpPort\n )\n .logLevel(\"WARN\");"), contains("2 http3Port( udpPort )"));
        assertThat("after a starter call has closed", bareHttp3Ports("server = startWithHttp3(configuration);\nconfiguration.http3Port(udpPort);"), contains("2 http3Port(udpPort)"));
        assertThat("inside another call", bareHttp3Ports("refused = refusedStart(() -> new MockServer(configuration().http3Port(udpPort), 0));"), contains("1 http3Port(udpPort)"));
        assertThat("in a method whose name only starts with the starter's", bareHttp3Ports("startWithHttp3Disabled(() -> configuration().http3Port(udpPort));"), contains("1 http3Port(udpPort)"));
        assertThat("in a method whose name only ends with the starter's", bareHttp3Ports("restartWithHttp3(() -> configuration().http3Port(udpPort));"), contains("1 http3Port(udpPort)"));
        assertThat("0 written another way", bareHttp3Ports("configuration().http3Port(NO_HTTP3);"), contains("1 http3Port(NO_HTTP3)"));
    }

    @Test
    public void shouldFlagAnHttp3PortPassedByName() {
        assertThat(bareHttp3Ports("command.add(\"-Dmockserver.http3Port=\" + udpPort);"), contains("1 mockserver.http3Port"));
        assertThat(bareHttp3Ports("a();\nb();\nSystem.setProperty(\"mockserver.http3Port\", \"8443\");"), contains("3 mockserver.http3Port"));
        assertThat(bareHttp3Ports("environment.put(\"MOCKSERVER_HTTP3_PORT\", port);"), contains("1 MOCKSERVER_HTTP3_PORT"));
        assertThat("in a text block", bareHttp3Ports("String properties = \"\"\"\n mockserver.http3Port=8443\n \"\"\";"), contains("2 mockserver.http3Port"));
        assertThat("as a constant", bareHttp3Ports("String HTTP3 = \"MOCKSERVER_HTTP3_PORT\";\nenvironment.put(MOCKSERVER_HTTP3_PORT, port);"),
            contains("1 MOCKSERVER_HTTP3_PORT", "2 MOCKSERVER_HTTP3_PORT"));
    }

    @Test
    public void shouldNotFlagAStartThroughTheStarterOrAnHttp3PortOfZero() {
        assertThat(bareHttp3Ports("server = startWithHttp3(udpPort -> new MockServer(configFactory.get().http3Port(udpPort).http3MaxIdleTimeout(30000L), 0));"), is(empty()));
        assertThat(bareHttp3Ports("server = Http3TestServer.startWithHttp3 (candidates(asked, taken), udpPort -> {\n try {\n  return start(configuration.http3Port(udpPort));\n"
            + " } catch (RuntimeException refused) {\n  refusals.add(refused);\n  throw refused;\n }\n}, MockServer::getHttp3Port, MockServer::stop);"), is(empty()));
        assertThat(bareHttp3Ports("server = startWithHttp3(configuration().http3MaxIdleTimeout(30000L));\nint http3Port = server.getHttp3Port();\nsend(http3Port, \"GET\");"), is(empty()));
        assertThat("HTTP/3 off", bareHttp3Ports("server = new MockServer(configuration().http3Port(0), 0);\nConfigurationProperties.http3Port( 0 );"), is(empty()));
        assertThat("read, not set", bareHttp3Ports("assertThat(configuration.http3Port(), is(server.getHttp3Port()));\nwithHttp3Port(8443);"), is(empty()));
        assertThat(bareHttp3Ports("// configuration().http3Port(findFreeUdpPort())\n/* -Dmockserver.http3Port=8443 */ String text = \"http3Port(8443) findFreeUdpPort()\";"), is(empty()));
        assertThat(bareHttp3Ports("assertThat(log, containsString(\"HTTP/3 is enabled (http3Port=\" + udpPort + \")\"));"), is(empty()));
        assertThat("only an import", bareHttp3Ports("import static org.mockserver.testing.socket.TestPortFactory.findFreeUdpPort;"), is(empty()));
    }

    @Test
    public void shouldHoldEachAllowedFileToItsExactCount() {
        List<Allowed> allowed = List.of(allowed("module/Listed.java", 2, "meant"));

        assertThat(problems(Map.of("module/Listed.java", List.of("a", "b")), allowed), is(empty()));
        assertThat("exceeded", problems(Map.of("module/Listed.java", List.of("a", "b", "c")), allowed),
            contains("ALLOWED expects 2 in module/Listed.java (meant) but found 3:\n  a\n  b\n  c"));
        assertThat("partly stale", problems(Map.of("module/Listed.java", List.of("a")), allowed),
            contains("ALLOWED expects 2 in module/Listed.java (meant) but found 1:\n  a"));
        assertThat("stale", problems(Map.of(), allowed), contains("ALLOWED expects 2 in module/Listed.java (meant) but found 0"));
        assertThat("not listed", problems(Map.of("module/Listed.java", List.of("a", "b"), "module/Other.java", List.of("c")), allowed), contains("c"));
        assertThat("the same file name in another module", problems(Map.of("other/Listed.java", List.of("a", "b")), allowed),
            contains(containsString("but found 0"), is("a"), is("b")));
    }

    /**
     * Each found UDP port, {@code http3Port} set outside the starter and {@code http3Port} passed by name in
     * {@code source}, in the order written, as {@code "<line> <matched text>"}.
     */
    static List<String> bareHttp3Ports(String source) {
        String code = blank(source, false);
        String structure = blank(source, true);
        Map<Integer, String> offenders = new TreeMap<>();
        Matcher found = FOUND_UDP_PORT.matcher(structure);
        while (found.find()) {
            offenders.put(found.start(), found.group());
        }
        Matcher call = HTTP3_PORT_CALL.matcher(structure);
        while (call.find()) {
            int close = closingBracket(structure, call.end());
            String argument = structure.substring(call.end(), close).trim();
            if (!argument.isEmpty() && !argument.equals("0") && !insideStarterCall(structure, call.start())) {
                offenders.put(call.start(), code.substring(call.start(), Math.min(close + 1, code.length())).replaceAll("\\s+", " "));
            }
        }
        Matcher name = HTTP3_PORT_NAME.matcher(code);
        while (name.find()) {
            offenders.put(name.start(), name.group());
        }
        return offenders.entrySet().stream().map(offender -> lineOf(code, offender.getKey()) + " " + offender.getValue()).collect(Collectors.toList());
    }

    /**
     * What {@code offendersByFile} has that {@code allowed} does not expect, and what {@code allowed} expects that
     * it does not have.
     */
    static List<String> problems(Map<String, List<String>> offendersByFile, List<Allowed> allowed) {
        Map<String, List<String>> notListed = new TreeMap<>(offendersByFile);
        List<String> problems = new ArrayList<>();
        for (Allowed entry : allowed) {
            List<String> found = notListed.remove(entry.file);
            int actual = found == null ? 0 : found.size();
            if (actual != entry.count) {
                problems.add("ALLOWED expects " + entry.count + " in " + entry.file + " (" + entry.reason + ") but found " + actual
                    + (found == null ? "" : ":\n  " + String.join("\n  ", found)));
            }
        }
        notListed.values().forEach(problems::addAll);
        return problems;
    }

    // the offset of the bracket closing the one opened just before from, or the end of the source
    private static int closingBracket(String structure, int from) {
        int depth = 0;
        for (int i = from; i < structure.length(); i++) {
            char c = structure.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return structure.length();
    }

    private static boolean insideStarterCall(String structure, int offset) {
        int closed = 0;
        for (int i = offset - 1; i >= 0; i--) {
            char c = structure.charAt(i);
            if (c == ')') {
                closed++;
            } else if (c == '(') {
                if (closed > 0) {
                    closed--;
                } else if (isStarterName(structure, i)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isStarterName(String structure, int openBracket) {
        int end = openBracket;
        while (end > 0 && Character.isWhitespace(structure.charAt(end - 1))) {
            end--;
        }
        int start = end - STARTER.length();
        return start >= 0 && structure.startsWith(STARTER, start) && (start == 0 || !Character.isJavaIdentifierPart(structure.charAt(start - 1)));
    }

    private static Allowed allowed(String file, int count, String reason) {
        return new Allowed(file, count, reason);
    }

    private static final class Allowed {
        private final String file;
        private final int count;
        private final String reason;

        private Allowed(String file, int count, String reason) {
            this.file = file;
            this.count = count;
            this.reason = reason;
        }
    }
}

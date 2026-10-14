package org.mockserver.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;

/**
 * {@code mockserver lint <file>...}: checks expectation files offline, with no server, and never changes them.
 */
public class MainLintCommandTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @BeforeClass
    public static void doNotExitTheTestJvm() {
        Main.exitOnNonZeroCode = false;
    }

    @AfterClass
    public static void restoreExit() {
        Main.exitOnNonZeroCode = true;
        Main.usageShown = false;
    }

    @Test
    public void shouldReportAShadowedExpectationAcrossTwoFilesAndExitZero() throws Exception {
        File first = file("first.json", "[{\"httpRequest\":{\"method\":\"GET\",\"path\":\"/users/.*\"},\"httpResponse\":{\"body\":\"all\"}}]");
        File second = file("second.json", "{\"id\":\"user-one\",\"httpRequest\":{\"method\":\"GET\",\"path\":\"/users/1\"},\"httpResponse\":{\"body\":\"one\"}}");

        Output output = lint(first.getAbsolutePath(), second.getAbsolutePath());

        assertThat(output.exitCode, is(0));
        assertThat(output.out, containsString(second.getAbsolutePath() + " [0] GET /users/1"));
        assertThat(output.out, containsString("WARNING SHADOWED: never matches: GET /users/.* is evaluated first"));
        assertThat(output.out, containsString("see " + first.getAbsolutePath() + " [0] GET /users/.*"));
        assertThat(output.out, containsString("checked 2 expectation(s) in 2 file(s): 0 error(s), 1 warning(s), 0 info"));
        assertThat("lint never changes the file", new String(Files.readAllBytes(second.toPath()), StandardCharsets.UTF_8), containsString("\"user-one\""));
    }

    @Test
    public void shouldExitOneOnAWarningOnlyWithStrict() throws Exception {
        File expectations = file("no-slash.json", "[{\"httpRequest\":{\"path\":\"users\"},\"httpResponse\":{}}]");

        assertThat(lint(expectations.getAbsolutePath()).exitCode, is(0));
        Output strict = lint("--strict", expectations.getAbsolutePath());
        assertThat(strict.exitCode, is(1));
        assertThat(strict.out, containsString("PATH_MISSING_LEADING_SLASH"));
    }

    @Test
    public void shouldExitOneOnAnInvalidExpectation() throws Exception {
        File expectations = file("invalid.json", "[{\"httpRequest\":{\"path\":\"/ok\"},\"httpResponse\":{}},{\"httpRequest\":{\"path\":\"/bad\",\"method\":42},\"httpResponse\":{}}]");

        Output output = lint(expectations.getAbsolutePath());

        assertThat(output.exitCode, is(1));
        assertThat(output.out, containsString(expectations.getAbsolutePath() + " [1]"));
        assertThat(output.out, containsString("ERROR INVALID_EXPECTATION"));
    }

    @Test
    public void shouldPrintTheReportAsJsonWithEachIssuesFile() throws Exception {
        File expectations = file("json.json", "[{\"httpRequest\":{\"path\":\"/api/*\"},\"httpResponse\":{}}]");

        Output output = lint("--json", expectations.getAbsolutePath());

        JsonNode report = ObjectMapperFactory.createObjectMapper().readTree(output.out);
        assertThat(output.exitCode, is(0));
        assertThat(report.path("issues").get(0).path("rule").asText(), is("PATH_GLOB_WILDCARD"));
        assertThat(report.path("issues").get(0).path("file").asText(), is(expectations.getAbsolutePath()));
        assertThat(report.path("issues").get(0).path("index").asInt(), is(0));
    }

    @Test
    public void shouldReportAMissingFileAsAnErrorWithoutTheRunUsage() throws Exception {
        Output output = lint(new File(tempFolder.getRoot(), "missing.json").getAbsolutePath());

        assertThat(output.exitCode, is(1));
        assertThat(output.err, containsString("ERROR:  could not read expectations from"));
        assertThat(output.out, not(containsString("-serverPort")));
    }

    @Test
    public void shouldNameADuplicateIdByItsFileAndIndexInThatFile() throws Exception {
        File first = file("dup-first.json", "[{\"httpRequest\":{\"path\":\"/a\"},\"httpResponse\":{}},{\"id\":\"dup\",\"httpRequest\":{\"path\":\"/b\"},\"httpResponse\":{}}]");
        File second = file("dup-second.json", "[{\"id\":\"dup\",\"httpRequest\":{\"path\":\"/c\"},\"httpResponse\":{}}]");

        Output output = lint(first.getAbsolutePath(), second.getAbsolutePath());

        assertThat(output.out, containsString(first.getAbsolutePath() + " [1] any method /b"));
        assertThat(output.out, containsString("WARNING DUPLICATE_ID: is replaced by a later entry with the same id (any method /c)"));
        assertThat(output.out, containsString("see " + second.getAbsolutePath() + " [0] any method /c"));
        assertThat("no index from the combined list leaks into the message", output.out, not(containsString("index 2")));
    }

    @Test
    public void shouldNotFailWhenARegexOverflowsTheStackDuringTheShadowingCheck() throws Throwable {
        // a regex that java.util.regex matches recursively, compared with a 12,001 character literal path
        File expectations = file("deep-regex.json", "[{\"id\":\"catch-all\",\"httpRequest\":{\"path\":\"/(a|b)*\"},\"httpResponse\":{}},"
            + "{\"id\":\"long\",\"httpRequest\":{\"path\":\"/" + new String(new char[6_000]).replace("\0", "ab") + "\"},\"httpResponse\":{}}]");
        Output[] output = new Output[1];
        Throwable[] escaped = new Throwable[1];

        Thread thread = new Thread(null, () -> {
            try {
                output[0] = lint(expectations.getAbsolutePath());
            } catch (Throwable throwable) {
                escaped[0] = throwable;
            }
        }, "small-stack", 512 * 1024);
        thread.start();
        thread.join(java.util.concurrent.TimeUnit.SECONDS.toMillis(120));

        assertThat("finished", thread.isAlive(), is(false));
        assertThat(String.valueOf(escaped[0]), escaped[0] == null, is(true));
        assertThat(output[0].err, output[0].exitCode, is(0));
        assertThat(output[0].out, containsString("checked 2 expectation(s) in 1 file(s): 0 error(s), 0 warning(s), 0 info"));
    }

    @Test
    public void shouldBeAKnownSubcommand() {
        assertThat(Main.preprocessArguments("lint", "./expectations.json")[0], is("lint"));
    }

    private File file(String name, String content) throws Exception {
        File file = tempFolder.newFile(name);
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static Output lint(String... arguments) throws Exception {
        PrintStream originalOut = Main.systemOut;
        PrintStream originalErr = Main.systemErr;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            Main.systemOut = new PrintStream(out, true, StandardCharsets.UTF_8.name());
            Main.systemErr = new PrintStream(err, true, StandardCharsets.UTF_8.name());
            String[] withCommand = new String[arguments.length + 1];
            withCommand[0] = "lint";
            System.arraycopy(arguments, 0, withCommand, 1, arguments.length);
            Main.main(withCommand);
            return new Output(Main.lastExitCode, out.toString(StandardCharsets.UTF_8.name()), err.toString(StandardCharsets.UTF_8.name()));
        } finally {
            Main.systemOut = originalOut;
            Main.systemErr = originalErr;
        }
    }

    private static final class Output {
        final int exitCode;
        final String out;
        final String err;

        Output(int exitCode, String out, String err) {
            this.exitCode = exitCode;
            this.out = out;
            this.err = err;
        }
    }
}

package org.mockserver.test;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

/**
 * The build's leak gate (check-netty-leaks in the parent pom) fails on any non-empty {@code leak-*.txt} in the report
 * directory. Each case runs {@link LeakOneBuffer} in a child JVM with the detector settings the build gives a module's
 * test forks ({@code mockserver.nettyLeakDetectorArgLine}), so a change to those settings that stops a leaked buffer
 * from being recorded fails here.
 */
public class FailOnLeakResourceLeakDetectorTest {

    private static final String BUILD_ARG_LINE_PROPERTY = "mockserver.nettyLeakDetectorArgLine";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test(timeout = 120_000)
    public void shouldWriteALeakedBufferWithItsAllocationSiteToTheFileTheGateFailsOn() throws Exception {
        File reportDir = runChildJvmWithDetector(LeakOneBuffer.class, "leak");

        List<File> leakFiles = nonEmptyLeakFiles(reportDir);
        assertThat("non-empty leak-*.txt files in " + reportDir, leakFiles, hasSize(1));
        String report = new String(Files.readAllBytes(leakFiles.get(0).toPath()), StandardCharsets.UTF_8);
        assertThat(report, containsString("LEAK: ByteBuf"));
        assertThat(report, containsString("Created at:"));
        assertThat(report, containsString(LeakOneBuffer.class.getName() + ".allocateAndDrop"));
    }

    @Test(timeout = 120_000)
    public void shouldWriteNothingTheGateFailsOnWhenEveryBufferIsReleased() throws Exception {
        File reportDir = runChildJvmWithDetector(LeakOneBuffer.class, "release");

        assertThat("non-empty leak-*.txt files in " + reportDir, nonEmptyLeakFiles(reportDir), is(empty()));
    }

    /**
     * The gate also fails when tests ran and no fork wrote an installed marker. A fork whose tests never allocate a
     * buffer must still write one, or a build that runs only such tests (a -DskipTests build still runs failsafe)
     * fails although its detector was installed.
     */
    @Test(timeout = 120_000)
    public void shouldInstallTheDetectorAtForkStartWhenTheForkAllocatesNoBuffer() throws Exception {
        runChildJvmWithDetector(StartForkWithoutBuffers.class, "start");
    }

    @Test(timeout = 120_000)
    public void shouldWriteNoMarkerAtForkStartWhenTheForkIsNotGivenTheDetector() throws Exception {
        ChildJvm child = runChildJvm(StartForkWithoutBuffers.class, "start-without-detector", NettyLeakDetectorForkStart.CUSTOM_DETECTOR_PROPERTY);

        assertThat("the child JVM wrote no installed marker, output:\n" + child.output, installedMarkers(child.reportDir), is(emptyArray()));
    }

    private File runChildJvmWithDetector(Class<?> mainClass, String mode) throws IOException, InterruptedException {
        ChildJvm child = runChildJvm(mainClass, mode, null);
        assertThat("the child JVM installed the detector, output:\n" + child.output, installedMarkers(child.reportDir), is(arrayWithSize(1)));
        return child.reportDir;
    }

    private ChildJvm runChildJvm(Class<?> mainClass, String mode, String omittedProperty) throws IOException, InterruptedException {
        String buildArgLine = System.getProperty(BUILD_ARG_LINE_PROPERTY, "").trim();
        if (buildArgLine.isEmpty()) {
            fail("system property " + BUILD_ARG_LINE_PROPERTY + " is not set: run this class through Maven, whose surefire configuration in mockserver-testing/pom.xml passes it");
        }
        File reportDir = temporaryFolder.newFolder("netty-leaks-" + mode);
        List<String> command = new ArrayList<>();
        command.add(new File(System.getProperty("java.home"), "bin/java").getPath());
        for (String argument : buildArgLine.split("\\s+")) {
            boolean omitted = omittedProperty != null && argument.startsWith("-D" + omittedProperty + "=");
            if (!omitted && !argument.startsWith("-D" + FailOnLeakResourceLeakDetector.LEAK_REPORT_DIR_PROPERTY + "=")) {
                command.add(argument);
            }
        }
        command.add("-D" + FailOnLeakResourceLeakDetector.LEAK_REPORT_DIR_PROPERTY + "=" + reportDir.getPath());
        command.add("-cp");
        command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(mainClass.getName());
        command.add(mode);
        File output = temporaryFolder.newFile("child-" + mode + ".log");
        Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output).start();
        if (!child.waitFor(60, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            fail("child JVM did not exit within 60 s: " + command);
        }
        String childOutput = new String(Files.readAllBytes(output.toPath()), StandardCharsets.UTF_8);
        assertThat("child JVM exit code, output:\n" + childOutput, child.exitValue(), is(0));
        return new ChildJvm(reportDir, childOutput);
    }

    private static String[] installedMarkers(File reportDir) {
        return reportDir.list((dir, name) -> name.startsWith(FailOnLeakResourceLeakDetector.INSTALLED_MARKER_PREFIX));
    }

    private static final class ChildJvm {
        private final File reportDir;
        private final String output;

        private ChildJvm(File reportDir, String output) {
            this.reportDir = reportDir;
            this.output = output;
        }
    }

    private static List<File> nonEmptyLeakFiles(File reportDir) {
        List<File> leakFiles = new ArrayList<>();
        File[] files = reportDir.listFiles((dir, name) -> name.startsWith("leak-") && name.endsWith(".txt"));
        if (files != null) {
            for (File file : files) {
                if (file.length() > 0) {
                    leakFiles.add(file);
                }
            }
        }
        return leakFiles;
    }
}

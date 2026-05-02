package org.mockserver.test;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PrintOutCurrentTestRunListener extends RunListener {

    private static final String MODE = System.getProperty("mockserver.testOutput", "verbose");
    private static final String NEW_LINE = System.getProperty("line.separator");
    private static final int DOTS_PER_LINE = 80;

    private static final Map<String, Long> START_TIMES = new ConcurrentHashMap<>();
    private static final Set<String> FAILED_TESTS = ConcurrentHashMap.newKeySet();

    private final AtomicInteger classPassCount = new AtomicInteger();
    private final AtomicInteger classFailCount = new AtomicInteger();
    private final AtomicInteger classIgnoreCount = new AtomicInteger();
    private final AtomicInteger dotCount = new AtomicInteger();
    private final List<String> classFailureMessages = Collections.synchronizedList(new ArrayList<>());
    private volatile String currentClassName = "";
    private volatile long classStartTime;

    @Override
    public void testStarted(Description description) {
        switchClassIfNeeded(description);
        START_TIMES.put(description.getMethodName(), System.currentTimeMillis());
        if ("verbose".equals(MODE)) {
            System.out.print("STARTED: " + description.getMethodName() + NEW_LINE);
        }
    }

    @Override
    public void testFinished(Description description) {
        if (FAILED_TESTS.contains(description.getMethodName())) {
            return;
        }
        Long startTime = START_TIMES.get(description.getMethodName());
        Long duration = startTime != null ? System.currentTimeMillis() - startTime : null;
        classPassCount.incrementAndGet();
        if ("verbose".equals(MODE)) {
            System.out.print("FINISHED: " + description.getMethodName() + (duration != null ? " duration: " + duration : "") + NEW_LINE);
        } else if ("quiet".equals(MODE)) {
            System.out.print(".");
            if (dotCount.incrementAndGet() % DOTS_PER_LINE == 0) {
                System.out.print(NEW_LINE);
            }
        }
    }

    @Override
    public void testFailure(Failure failure) {
        FAILED_TESTS.add(failure.getDescription().getMethodName());
        classFailCount.incrementAndGet();
        Long startTime = START_TIMES.get(failure.getDescription().getMethodName());
        Long duration = startTime != null ? System.currentTimeMillis() - startTime : null;
        if ("verbose".equals(MODE)) {
            System.out.print("FAILED: " + failure.getDescription().getMethodName() + NEW_LINE);
        } else if ("quiet".equals(MODE)) {
            System.out.print("F");
            if (dotCount.incrementAndGet() % DOTS_PER_LINE == 0) {
                System.out.print(NEW_LINE);
            }
            System.out.print(NEW_LINE + "  FAILED: " + failure.getDescription().getMethodName()
                + (duration != null ? " duration: " + duration : "") + NEW_LINE);
            if (failure.getMessage() != null) {
                System.out.print("  " + failure.getMessage() + NEW_LINE);
            }
            System.out.print(indent(failure.getTrace()));
        } else if ("summary".equals(MODE)) {
            String msg = "  FAILED: " + failure.getDescription().getMethodName()
                + (duration != null ? " duration: " + duration : "") + NEW_LINE;
            if (failure.getMessage() != null) {
                msg += "  " + failure.getMessage() + NEW_LINE;
            }
            msg += indent(failure.getTrace());
            classFailureMessages.add(msg);
        }
    }

    @Override
    public void testIgnored(Description description) {
        switchClassIfNeeded(description);
        classIgnoreCount.incrementAndGet();
        if ("verbose".equals(MODE)) {
            System.out.print("IGNORED: " + description.getMethodName() + NEW_LINE);
        } else if ("quiet".equals(MODE)) {
            System.out.print("S");
            if (dotCount.incrementAndGet() % DOTS_PER_LINE == 0) {
                System.out.print(NEW_LINE);
            }
        }
    }

    @Override
    public void testRunFinished(Result result) {
        if (!currentClassName.isEmpty()) {
            printClassSummary();
        }
    }

    private void switchClassIfNeeded(Description description) {
        String className = description.getClassName();
        if (!className.equals(currentClassName)) {
            if (!currentClassName.isEmpty()) {
                printClassSummary();
            }
            currentClassName = className;
            classStartTime = System.currentTimeMillis();
            classPassCount.set(0);
            classFailCount.set(0);
            classIgnoreCount.set(0);
            dotCount.set(0);
            classFailureMessages.clear();
            FAILED_TESTS.clear();
            if ("quiet".equals(MODE)) {
                System.out.print(NEW_LINE + simpleClassName(className) + ": ");
            }
        }
    }

    private void printClassSummary() {
        long elapsed = System.currentTimeMillis() - classStartTime;
        int passed = classPassCount.get();
        int failed = classFailCount.get();
        int ignored = classIgnoreCount.get();
        if ("quiet".equals(MODE)) {
            System.out.print(NEW_LINE + "  " + passed + " passed, " + failed + " failed"
                + (ignored > 0 ? ", " + ignored + " ignored" : "")
                + " (" + formatDuration(elapsed) + ")" + NEW_LINE);
        } else if ("summary".equals(MODE)) {
            String status = failed > 0 ? "FAIL" : "OK";
            System.out.print(status + " " + simpleClassName(currentClassName) + ": " + passed + " passed, " + failed + " failed"
                + (ignored > 0 ? ", " + ignored + " ignored" : "")
                + " (" + formatDuration(elapsed) + ")" + NEW_LINE);
            for (String msg : classFailureMessages) {
                System.out.print(msg);
            }
        }
    }

    private static String simpleClassName(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot >= 0 ? fqcn.substring(dot + 1) : fqcn;
    }

    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }
        return String.format("%.1fs", millis / 1000.0);
    }

    private static String indent(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            sb.append("    ").append(line).append(System.lineSeparator());
        }
        return sb.toString();
    }

}

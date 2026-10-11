package org.mockserver.testing.integration.mock;

import com.sun.management.UnixOperatingSystemMXBean;
import org.mockserver.echo.http.EchoServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.mockserver.testing.tls.SSLSocketFactory.sslSocketFactory;

/**
 * Keeps the echo servers that every mocking integration test class in a JVM shares answering.
 * Before a test class uses one, it is probed; one that does not answer is reported on stderr
 * (a thread dump and the open file-descriptor count, in full the first time) and replaced,
 * so a single wedged echo server cannot fail every later forwarding test in the fork.
 */
class SharedEchoServerGuard {

    static final String DIAGNOSTIC_HEADER = "=== SHARED ECHO SERVER NOT ANSWERING ===";
    static final int PROBE_TIMEOUT_MILLIS = 2000;
    private static final int PROBE_ATTEMPTS = 2;
    // the echo server's WebSocket handler answers an unsupported WebSocket version with 426 before the
    // echo handler sees the request, so the probe leaves the queued responses and last request alone
    private static final byte[] PROBE_REQUEST = (
        "GET /_mockserver_callback_websocket HTTP/1.1\r\n" +
            "Host: 127.0.0.1\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: c2hhcmVkLWVjaG8tcHJvYmU=\r\n" +
            "Sec-WebSocket-Version: 0\r\n" +
            "\r\n"
    ).getBytes(US_ASCII);
    private static final AtomicBoolean FULL_DIAGNOSTIC_PRINTED = new AtomicBoolean();

    private SharedEchoServerGuard() {
    }

    /**
     * @return {@code echoServer} if it answers, otherwise a new echo server; an unanswering server is left
     * running because a test class running concurrently may still be waiting on it
     */
    static EchoServer answeringOrReplaced(EchoServer echoServer, boolean secure) {
        if (echoServer == null) {
            return new EchoServer(secure);
        }
        Integer port = boundPort(echoServer);
        for (int attempt = 0; port != null && attempt < PROBE_ATTEMPTS; attempt++) {
            if (answers(port, secure)) {
                return echoServer;
            }
        }
        String server = (secure ? "secure" : "insecure") + " echo server";
        printDiagnostic(
            (port == null ? server + " has no bound port" : server + " on port " + port + " did not answer within " + PROBE_TIMEOUT_MILLIS + "ms (" + PROBE_ATTEMPTS + " attempts)") + "; replacing it",
            System.err
        );
        return new EchoServer(secure);
    }

    private static Integer boundPort(EchoServer echoServer) {
        try {
            return echoServer.getPort();
        } catch (RuntimeException bindFailed) {
            return null;
        }
    }

    static boolean answers(int port, boolean secure) {
        try (Socket plain = new Socket()) {
            plain.connect(new InetSocketAddress("127.0.0.1", port), PROBE_TIMEOUT_MILLIS);
            plain.setSoTimeout(PROBE_TIMEOUT_MILLIS);
            try (Socket socket = secure ? sslSocketFactory().wrapSocket(plain) : plain) {
                OutputStream output = socket.getOutputStream();
                output.write(PROBE_REQUEST);
                output.flush();
                return statusLine(socket.getInputStream()).startsWith("HTTP/1.1 426");
            }
        } catch (IOException | RuntimeException notAnswering) {
            return false;
        }
    }

    private static String statusLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != -1 && next != '\n' && line.size() < 256) {
            line.write(next);
        }
        return line.toString(US_ASCII);
    }

    static void printDiagnostic(String reason, PrintStream output) {
        StringBuilder diagnostic = new StringBuilder()
            .append(System.lineSeparator()).append(DIAGNOSTIC_HEADER).append(System.lineSeparator())
            .append(reason).append(System.lineSeparator())
            .append(openFileDescriptors()).append(System.lineSeparator());
        if (FULL_DIAGNOSTIC_PRINTED.compareAndSet(false, true)) {
            appendThreadDump(diagnostic);
        } else {
            diagnostic.append("thread dump: printed with the first ").append(DIAGNOSTIC_HEADER).append(System.lineSeparator());
        }
        output.print(diagnostic);
        output.flush();
    }

    static void resetForTest() {
        FULL_DIAGNOSTIC_PRINTED.set(false);
    }

    private static String openFileDescriptors() {
        OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (operatingSystem instanceof UnixOperatingSystemMXBean) {
            UnixOperatingSystemMXBean unix = (UnixOperatingSystemMXBean) operatingSystem;
            return "open file descriptors: " + unix.getOpenFileDescriptorCount() + " of " + unix.getMaxFileDescriptorCount();
        }
        return "open file descriptors: unavailable on this platform";
    }

    private static void appendThreadDump(StringBuilder diagnostic) {
        ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
        diagnostic.append("thread dump (").append(threads.length).append(" threads):").append(System.lineSeparator());
        for (ThreadInfo thread : threads) {
            diagnostic.append('"').append(thread.getThreadName()).append("\" #").append(thread.getThreadId())
                .append(' ').append(thread.getThreadState());
            if (thread.getLockName() != null) {
                diagnostic.append(" on ").append(thread.getLockName());
            }
            if (thread.getLockOwnerName() != null) {
                diagnostic.append(" owned by \"").append(thread.getLockOwnerName()).append('"');
            }
            diagnostic.append(System.lineSeparator());
            // ThreadInfo.toString() truncates stacks to eight frames, so print every frame
            for (StackTraceElement frame : thread.getStackTrace()) {
                diagnostic.append("\tat ").append(frame).append(System.lineSeparator());
            }
            for (LockInfo synchronizer : thread.getLockedSynchronizers()) {
                diagnostic.append("\tholds ").append(synchronizer).append(System.lineSeparator());
            }
            diagnostic.append(System.lineSeparator());
        }
    }
}

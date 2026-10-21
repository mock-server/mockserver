package org.mockserver.persistence;

import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Body;
import org.mockserver.model.FileBody;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Moves large recorded response bodies out of the expectations into files (WireMock's
 * {@code extractBodyCriteria}): each body larger than the threshold is written to the configured directory
 * and replaced by a {@link FileBody} that references it.
 * <p>
 * The directory only ever comes from configuration. Each file is named by the SHA-256 of its contents plus an
 * extension chosen from the content type, so no part of a recorded request or response reaches the file name
 * and the same body is written once: an existing regular file with that name and size is reused, any other
 * file or link with that name is replaced. A file is written to a temporary file in the directory (owner-only
 * permissions on POSIX) and then moved into place, which replaces, rather than follows, a symbolic link at the
 * target name. Files are never deleted. One extraction run references at most {@link #MAX_FILES_PER_RUN}
 * distinct files and {@link #MAX_BYTES_PER_RUN} bytes, and starts no new write once its time budget is spent;
 * bodies beyond any of these limits stay inline. The file I/O blocks, so callers run it off the event loop.
 */
public class RecordedBodyFileExtractor {

    static final int MAX_FILES_PER_RUN = 1000;
    static final long MAX_BYTES_PER_RUN = 256L * 1024 * 1024;
    private static final long MAX_TIME_BUDGET_MILLIS = java.util.concurrent.TimeUnit.DAYS.toMillis(1);

    private static final Set<Body.Type> EXTRACTABLE_TYPES = EnumSet.of(Body.Type.STRING, Body.Type.JSON, Body.Type.XML, Body.Type.BINARY);

    private final MockServerLogger mockServerLogger;
    private final String configuredDirectory;
    private final long thresholdBytes;
    private final int maxFiles;
    private final long maxBytes;
    private final long timeBudgetMillis;

    /**
     * @param mockServerLogger    logs what was written, and a body that could not be written, which is then left inline
     * @param configuredDirectory the directory to write bodies to, as configured; must not be blank
     * @param thresholdBytes      bodies with more bytes than this are extracted
     * @param timeBudgetMillis    no new file is written once a run has taken this long
     */
    public RecordedBodyFileExtractor(MockServerLogger mockServerLogger, String configuredDirectory, long thresholdBytes, long timeBudgetMillis) {
        this(mockServerLogger, configuredDirectory, thresholdBytes, MAX_FILES_PER_RUN, MAX_BYTES_PER_RUN, timeBudgetMillis);
    }

    RecordedBodyFileExtractor(MockServerLogger mockServerLogger, String configuredDirectory, long thresholdBytes, int maxFiles, long maxBytes, long timeBudgetMillis) {
        if (isBlank(configuredDirectory)) {
            throw new IllegalArgumentException("a recorded body files directory is required");
        }
        if (thresholdBytes < 0) {
            throw new IllegalArgumentException("the body extraction threshold must not be negative");
        }
        this.mockServerLogger = mockServerLogger;
        this.configuredDirectory = configuredDirectory;
        this.thresholdBytes = thresholdBytes;
        this.maxFiles = maxFiles;
        this.maxBytes = maxBytes;
        this.timeBudgetMillis = timeBudgetMillis;
    }

    private static final class Run {
        final long deadlineNanos;
        final Set<String> files = new HashSet<>();
        long bytes;
        int written;
        long bytesWritten;
        int keptInlineOverLimit;

        Run(long timeBudgetMillis) {
            this.deadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.min(Math.max(0, timeBudgetMillis), MAX_TIME_BUDGET_MILLIS));
        }
    }

    /**
     * Returns the expectations with each large response body, including each body of a response sequence,
     * replaced by a {@link FileBody}. An expectation with nothing to extract is returned as it is.
     */
    public List<Expectation> extract(List<Expectation> expectations) {
        List<Expectation> result = new ArrayList<>();
        if (expectations == null) {
            return result;
        }
        Run run = new Run(timeBudgetMillis);
        for (Expectation expectation : expectations) {
            result.add(extract(expectation, run));
        }
        if (!run.files.isEmpty() || run.keptInlineOverLimit > 0) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.INFO)
                    .setLogLevel(Level.INFO)
                    .setMessageFormat("recorded response bodies moved to files in{}files referenced:{}files written:{}bytes written:{}bodies kept inline over the per-retrieve limit:{}file limit:{}byte limit:{}time limit in milliseconds:{}")
                    .setArguments(configuredDirectory, run.files.size(), run.written, run.bytesWritten, run.keptInlineOverLimit, maxFiles, maxBytes, timeBudgetMillis)
            );
        }
        return result;
    }

    private Expectation extract(Expectation expectation, Run run) {
        if (expectation == null) {
            return null;
        }
        HttpResponse response = expectation.getHttpResponse();
        HttpResponse extractedResponse = extract(response, run);
        List<HttpResponse> responses = expectation.getHttpResponses();
        List<HttpResponse> extractedResponses = null;
        if (responses != null && !responses.isEmpty()) {
            extractedResponses = new ArrayList<>(responses.size());
            boolean changed = false;
            for (HttpResponse each : responses) {
                HttpResponse extractedEach = extract(each, run);
                changed |= extractedEach != each;
                extractedResponses.add(extractedEach);
            }
            if (!changed) {
                extractedResponses = null;
            }
        }
        if (extractedResponse == response && extractedResponses == null) {
            return expectation;
        }
        Expectation copy = expectation.cloneWith(expectation.getHttpRequest(), extractedResponse);
        if (extractedResponses != null) {
            copy.thenRespond(extractedResponses);
        }
        return copy;
    }

    private HttpResponse extract(HttpResponse response, Run run) {
        if (response == null || response.getBody() == null || !EXTRACTABLE_TYPES.contains(response.getBody().getType())) {
            return response;
        }
        Body<?> body = response.getBody();
        byte[] bytes = body.getRawBytes();
        if (bytes == null || bytes.length <= thresholdBytes) {
            return response;
        }
        String contentType = isNotBlank(body.getContentType()) ? body.getContentType() : response.getFirstHeader("content-type");
        String fileName = sha256Hex(bytes) + extension(contentType);
        if (!run.files.contains(fileName)) {
            if (run.files.size() >= maxFiles || run.bytes + bytes.length > maxBytes || System.nanoTime() - run.deadlineNanos >= 0) {
                run.keptInlineOverLimit++;
                return response;
            }
            try {
                if (write(fileName, bytes)) {
                    run.written++;
                    run.bytesWritten += bytes.length;
                }
            } catch (IOException | RuntimeException failure) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.WARN)
                        .setLogLevel(Level.WARN)
                        .setMessageFormat("could not write recorded response body to{}in{}so it is kept in the expectation")
                        .setArguments(fileName, configuredDirectory)
                        .setThrowable(failure)
                );
                return response;
            }
            run.files.add(fileName);
            run.bytes += bytes.length;
        }
        MediaType mediaType = null;
        if (isNotBlank(contentType)) {
            try {
                mediaType = MediaType.parse(contentType);
            } catch (RuntimeException unparseable) {
                mediaType = null;
            }
        }
        return response.clone().withBody(new FileBody(Paths.get(configuredDirectory, fileName).toString(), mediaType));
    }

    /**
     * @return whether the file was written, rather than an identical existing file reused
     */
    private boolean write(String fileName, byte[] bytes) throws IOException {
        Path directory = Files.createDirectories(Paths.get(configuredDirectory));
        Path target = directory.resolve(fileName);
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && Files.size(target) == bytes.length) {
            return false;
        }
        Path temporary = Files.createTempFile(directory, ".recorded-body-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }

    static String extension(String contentType) {
        if (contentType == null) {
            return ".bin";
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        int parameters = type.indexOf(';');
        if (parameters >= 0) {
            type = type.substring(0, parameters);
        }
        type = type.trim();
        if (type.endsWith("/json") || type.endsWith("+json")) {
            return ".json";
        }
        if (type.endsWith("/xml") || type.endsWith("+xml")) {
            return ".xml";
        }
        if (type.equals("text/html")) {
            return ".html";
        }
        if (type.startsWith("text/")) {
            return ".txt";
        }
        return ".bin";
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte each : digest) {
                hex.append(Character.forDigit((each >> 4) & 0xF, 16)).append(Character.forDigit(each & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}

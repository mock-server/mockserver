package org.mockserver.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class TestLogCaptureHandler extends Handler {

    private static final TestLogCaptureHandler INSTANCE = new TestLogCaptureHandler();
    private static final Map<Long, List<String>> THREAD_BUFFERS = new ConcurrentHashMap<>();

    public static TestLogCaptureHandler getInstance() {
        return INSTANCE;
    }

    public static void startCapture() {
        THREAD_BUFFERS.put(Thread.currentThread().getId(), new ArrayList<>());
    }

    public static void stopAndDiscard() {
        THREAD_BUFFERS.remove(Thread.currentThread().getId());
    }

    public static String stopAndDrain() {
        List<String> lines = THREAD_BUFFERS.remove(Thread.currentThread().getId());
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line);
        }
        return sb.toString();
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) {
            return;
        }
        List<String> buffer = THREAD_BUFFERS.get(Thread.currentThread().getId());
        if (buffer != null) {
            buffer.add(formatRecord(record));
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    private static String formatRecord(LogRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.getLevel())
            .append(" ")
            .append(record.getLoggerName() != null ? record.getLoggerName() : "")
            .append(" - ")
            .append(formatMessage(record))
            .append(System.lineSeparator());
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        return sb.toString();
    }

    private static String formatMessage(LogRecord record) {
        String message = record.getMessage();
        if (message == null) {
            return "";
        }
        Object[] params = record.getParameters();
        if (params != null && params.length > 0) {
            try {
                return MessageFormat.format(message, params);
            } catch (Exception e) {
                return message;
            }
        }
        return message;
    }

}

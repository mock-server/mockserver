package org.mockserver.memory;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.ui.MockServerLogListener;
import org.mockserver.ui.MockServerMatcherListener;
import org.mockserver.ui.MockServerMatcherNotifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.mockserver.character.Character.NEW_LINE;

public class MemoryMonitoring implements MockServerLogListener, MockServerMatcherListener {

    private static final AtomicInteger memoryUpdateFrequency = new AtomicInteger(0);
    private static final AtomicInteger mockServerLogSize = new AtomicInteger(0);
    private static final AtomicInteger requestMatchersSize = new AtomicInteger(0);
    private static final List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
    private static final String CSV_FILE = "memoryUsage_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv";

    static {
        if (ConfigurationProperties.outputMemoryUsageCsv()) {
            if (!new File(CSV_FILE).exists()) {
                String line = buildStatistics().stream().map(Pair::getKey).collect(Collectors.joining(","));
                writeLineToCsv(line);
            }
        }
    }

    private static void writeLineToCsv(String line) {
        try {
            FileOutputStream rawFileOutputStream = new FileOutputStream(CSV_FILE, true);
            rawFileOutputStream.write((line + NEW_LINE).getBytes(StandardCharsets.UTF_8));
            rawFileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public MemoryMonitoring() {
        this(null, null);
    }

    public MemoryMonitoring(MockServerEventLog mockServerLog, RequestMatchers requestMatchers) {
        if (mockServerLog != null) {
            mockServerLog.registerListener(this);
        }
        if (requestMatchers != null) {
            requestMatchers.registerListener(this);
        }
    }


    private static Summary getJVMMemory(MemoryType heap) {
        return new Summary(memoryPoolMXBeans.stream().filter(bean -> bean.getType() == heap).collect(Collectors.toList()));
    }

    public long remainingHeapKB() {
        Summary heap = getJVMMemory(MemoryType.HEAP);
        return (heap.getNet().getMax() - heap.getNet().getUsed()) / 1024L;
    }

    public void logMemoryMetrics() {
        if (ConfigurationProperties.outputMemoryUsageCsv()) {
            String line = buildStatistics().stream().map(Pair::getValue).map(String::valueOf).collect(Collectors.joining(","));
            writeLineToCsv(line);
        }
    }

    private static List<ImmutablePair<String, Object>> buildStatistics() {
        Summary heap = getJVMMemory(MemoryType.HEAP);
        Summary nonHeap = getJVMMemory(MemoryType.NON_HEAP);
        List<ImmutablePair<String, Object>> memoryStatistics = new ArrayList<>();
        memoryStatistics.add(ImmutablePair.of("eventLogSize", mockServerLogSize.get()));
        memoryStatistics.add(ImmutablePair.of("maxLogEntries", ConfigurationProperties.maxLogEntries()));
        memoryStatistics.add(ImmutablePair.of("expectationsSize", requestMatchersSize.get()));
        memoryStatistics.add(ImmutablePair.of("maxExpectations", ConfigurationProperties.maxExpectations()));
        memoryStatistics.add(ImmutablePair.of("heapInitialAllocation", heap.getNet().getInit()));
        memoryStatistics.add(ImmutablePair.of("heapUsed", heap.getNet().getUsed()));
        memoryStatistics.add(ImmutablePair.of("heapCommitted", heap.getNet().getCommitted()));
        memoryStatistics.add(ImmutablePair.of("heapMaxAllowed", heap.getNet().getMax()));
        memoryStatistics.add(ImmutablePair.of("nonHeapInitialAllocation", nonHeap.getNet().getInit()));
        memoryStatistics.add(ImmutablePair.of("nonHeapUsed", nonHeap.getNet().getUsed()));
        memoryStatistics.add(ImmutablePair.of("nonHeapCommitted", nonHeap.getNet().getCommitted()));
        memoryStatistics.add(ImmutablePair.of("nonHeapMaxAllowed", nonHeap.getNet().getMax()));
        return memoryStatistics;
    }

    public int defaultMaxLogEntries() {
        return (int) (remainingHeapKB() / 30);
    }

    public int defaultMaxExpectations() {
        return (int) (remainingHeapKB() / 400);
    }

    @Override
    public void updated(MockServerEventLog mockServerLog) {
        mockServerLogSize.set(mockServerLog.size());
        if (shouldUpdate()) {
            updateMemoryUsageMaximums();
            mockServerLog.setMaxSize(ConfigurationProperties.maxLogEntries());
        }
    }

    @Override
    public void updated(RequestMatchers requestMatchers, MockServerMatcherNotifier.Cause cause) {
        requestMatchersSize.set(requestMatchers.size());
        if (shouldUpdate()) {
            updateMemoryUsageMaximums();
            requestMatchers.setMaxSize(ConfigurationProperties.maxExpectations());
        }
    }

    private boolean shouldUpdate() {
        return memoryUpdateFrequency.incrementAndGet() % 500 == 0;
    }

    public void updateMemoryUsageMaximums() {
        ConfigurationProperties.defaultMaxExpectations(defaultMaxExpectations() + requestMatchersSize.get() / 2);
        ConfigurationProperties.defaultMaxLogEntries(defaultMaxLogEntries() + mockServerLogSize.get() / 2);
        logMemoryMetrics();
    }
}

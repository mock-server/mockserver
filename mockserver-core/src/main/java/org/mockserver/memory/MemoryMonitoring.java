package org.mockserver.memory;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerLogListener;
import org.mockserver.mock.listeners.MockServerMatcherListener;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;

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
import static org.mockserver.mock.HttpState.getPort;

public class MemoryMonitoring implements MockServerLogListener, MockServerMatcherListener {

    private static final AtomicInteger memoryUpdateFrequency = new AtomicInteger(0);
    private static final AtomicInteger currentLogEntriesCount = new AtomicInteger(0);
    private static final AtomicInteger currentExpectationsCount = new AtomicInteger(0);
    private static final List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
    private static final int MAX_LOG_ENTRIES_UPPER_LIMIT = 60000;
    private static final int MAX_EXPECTATIONS_UPPER_LIMIT = 5000;
    private final Configuration configuration;
    private final File csvFile;

    public MemoryMonitoring(Configuration configuration) {
        this(configuration, null, null);
    }

    public MemoryMonitoring(Configuration configuration, MockServerEventLog mockServerLog, RequestMatchers requestMatchers) {
        this.configuration = configuration;
        this.csvFile = new File(configuration.memoryUsageCsvDirectory(), "memoryUsage_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv");
        if (configuration.outputMemoryUsageCsv()) {
            if (!csvFile.exists()) {
                String line = buildStatistics().stream().map(Pair::getKey).collect(Collectors.joining(","));
                writeLineToCsv(line);
            }
        }
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
        if (configuration.outputMemoryUsageCsv()) {
            String line = buildStatistics().stream().map(Pair::getValue).map(String::valueOf).collect(Collectors.joining(","));
            writeLineToCsv(line);
        }
    }

    private void writeLineToCsv(String line) {
        try {
            FileOutputStream rawFileOutputStream = new FileOutputStream(csvFile, true);
            rawFileOutputStream.write((line + NEW_LINE).getBytes(StandardCharsets.UTF_8));
            rawFileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<ImmutablePair<String, Object>> buildStatistics() {
        Summary heap = getJVMMemory(MemoryType.HEAP);
        Summary nonHeap = getJVMMemory(MemoryType.NON_HEAP);
        List<ImmutablePair<String, Object>> memoryStatistics = new ArrayList<>();
        memoryStatistics.add(ImmutablePair.of("mockServerPort", getPort()));
        memoryStatistics.add(ImmutablePair.of("eventLogSize", currentLogEntriesCount.get()));
        memoryStatistics.add(ImmutablePair.of("maxLogEntries", configuration.maxLogEntries()));
        memoryStatistics.add(ImmutablePair.of("expectationsSize", currentExpectationsCount.get()));
        memoryStatistics.add(ImmutablePair.of("maxExpectations", configuration.maxExpectations()));
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

    public int startingMaxLogEntries() {
        return Math.min((int) (remainingHeapKB() / 30), MAX_LOG_ENTRIES_UPPER_LIMIT);
    }

    public int adjustedMaxLogEntries() {
        return Math.min(startingMaxLogEntries() + (currentLogEntriesCount.get() / 2), MAX_LOG_ENTRIES_UPPER_LIMIT);
    }

    public int startingMaxExpectations() {
        return Math.min((int) (remainingHeapKB() / 400), MAX_EXPECTATIONS_UPPER_LIMIT);
    }

    public int adjustedMaxExpectations() {
        return Math.min(startingMaxExpectations() + (currentExpectationsCount.get() / 2), MAX_EXPECTATIONS_UPPER_LIMIT);
    }

    @Override
    public void updated(MockServerEventLog mockServerLog) {
        currentLogEntriesCount.set(mockServerLog.size());
        if (shouldUpdate()) {
            logMemoryMetrics();
            mockServerLog.setMaxSize(configuration.maxLogEntries());
        }
    }

    @Override
    public void updated(RequestMatchers requestMatchers, MockServerMatcherNotifier.Cause cause) {
        currentExpectationsCount.set(requestMatchers.size());
        if (shouldUpdate()) {
            logMemoryMetrics();
            requestMatchers.setMaxSize(configuration.maxExpectations());
        }
    }

    private boolean shouldUpdate() {
        return memoryUpdateFrequency.incrementAndGet() % 500 == 0;
    }

}

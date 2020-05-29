package org.mockserver.memory;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpStateHandler;
import org.mockserver.model.ObjectWithJsonToString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockserver.character.Character.NEW_LINE;
import static org.slf4j.event.Level.INFO;

public class MemoryMonitoring {

    private final MockServerLogger mockServerLogger;
    private final HttpStateHandler httpStateHandler;
    private static final List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
    private static final String CSV_FILE = "memoryUsage_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv";

    static {
        if (ConfigurationProperties.outputMemoryUsageCsv()) {
            try {
                if (!new File(CSV_FILE).exists()) {
                    FileOutputStream rawFileOutputStream = new FileOutputStream(CSV_FILE, true);
                    rawFileOutputStream.write((buildStatistics(null).stream().map(Pair::getKey).collect(Collectors.joining(",")) + NEW_LINE).getBytes(StandardCharsets.UTF_8));
                    rawFileOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public MemoryMonitoring(MockServerLogger mockServerLogger, HttpStateHandler httpStateHandler) {
        this.mockServerLogger = mockServerLogger;
        this.httpStateHandler = httpStateHandler;
    }

    public void logMemoryMetrics() {
        List<ImmutablePair<String, Object>> memoryStatistics = buildStatistics(httpStateHandler);
        if (ConfigurationProperties.outputMemoryUsageCsv()) {
            try {
                FileOutputStream rawFileOutputStream = new FileOutputStream(CSV_FILE, true);
                rawFileOutputStream.write((memoryStatistics.stream().map(Pair::getValue).map(String::valueOf).collect(Collectors.joining(",")) + NEW_LINE).getBytes(StandardCharsets.UTF_8));
                rawFileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mockServerLogger.logEvent(
            new LogEntry()
                .setLogLevel(INFO)
                .setMessageFormat("memoryStatistics:{}")
                .setArguments(memoryStatistics)
        );
    }

    private static List<ImmutablePair<String, Object>> buildStatistics(HttpStateHandler httpStateHandler) {
        Summary heap = new Summary(memoryPoolMXBeans.stream().filter(bean -> bean.getType() == MemoryType.HEAP).collect(Collectors.toList()));
        Summary nonHeap = new Summary(memoryPoolMXBeans.stream().filter(bean -> bean.getType() == MemoryType.NON_HEAP).collect(Collectors.toList()));
        List<ImmutablePair<String, Object>> memoryStatistics = new ArrayList<>();
        memoryStatistics.add(ImmutablePair.of("eventLogSize", httpStateHandler != null ? httpStateHandler.eventLogSize() : 0));
        memoryStatistics.add(ImmutablePair.of("maxLogEntries", ConfigurationProperties.maxLogEntries()));
        memoryStatistics.add(ImmutablePair.of("expectationsSize", httpStateHandler != null ? httpStateHandler.expectationsSize() : 0));
        memoryStatistics.add(ImmutablePair.of("maxExpectations", ConfigurationProperties.maxExpectations()));
        memoryStatistics.add(ImmutablePair.of("webSocketClientRegistrySize", httpStateHandler != null ? httpStateHandler.webSocketClientRegistrySize() : 0));
        memoryStatistics.add(ImmutablePair.of("maxWebSocketExpectations", ConfigurationProperties.maxWebSocketExpectations()));
        memoryStatistics.add(ImmutablePair.of("actionHandlerThreadCount", ConfigurationProperties.actionHandlerThreadCount()));
        memoryStatistics.add(ImmutablePair.of("nioEventLoopThreadCount", ConfigurationProperties.nioEventLoopThreadCount()));
        memoryStatistics.add(ImmutablePair.of("webSocketClientEventLoopThreadCount", ConfigurationProperties.webSocketClientEventLoopThreadCount()));
        memoryStatistics.add(ImmutablePair.of("heapInitialAllocation", heap.net.init));
        memoryStatistics.add(ImmutablePair.of("heapUsed", heap.net.used));
        memoryStatistics.add(ImmutablePair.of("heapCommitted", heap.net.committed));
        memoryStatistics.add(ImmutablePair.of("heapMaxAllowed", heap.net.max));
        memoryStatistics.add(ImmutablePair.of("nonHeapInitialAllocation", nonHeap.net.init));
        memoryStatistics.add(ImmutablePair.of("nonHeapUsed", nonHeap.net.used));
        memoryStatistics.add(ImmutablePair.of("nonHeapCommitted", nonHeap.net.committed));
        memoryStatistics.add(ImmutablePair.of("nonHeapMaxAllowed", nonHeap.net.max));
        return memoryStatistics;
    }

    private static class Summary extends ObjectWithJsonToString {
        private Detail net;

        private Summary(Collection<MemoryPoolMXBean> memoryPoolMXBeans) {
            net = memoryPoolMXBeans
                .stream()
                .map(bean ->
                    new Detail()
                        .setInit(bean.getUsage().getInit())
                        .setUsed(bean.getUsage().getUsed())
                        .setCommitted(bean.getUsage().getCommitted())
                        .setMax(bean.getUsage().getMax())
                )
                .reduce(new Detail(), Detail::plus);
        }

        public Detail getNet() {
            return net;
        }

        public Summary setNet(Detail net) {
            this.net = net;
            return this;
        }
    }

    private static class Detail extends ObjectWithJsonToString {
        private long init;
        private long used;
        private long committed;
        private long max;

        private Detail plus(Detail detail) {
            return new Detail()
                .setInit(init + detail.init)
                .setUsed(used + detail.used)
                .setCommitted(committed + detail.committed)
                .setMax(max + detail.max);
        }

        public long getInit() {
            return init;
        }

        public Detail setInit(long init) {
            this.init = init;
            return this;
        }

        public long getUsed() {
            return used;
        }

        public Detail setUsed(long used) {
            this.used = used;
            return this;
        }

        public long getCommitted() {
            return committed;
        }

        public Detail setCommitted(long committed) {
            this.committed = committed;
            return this;
        }

        public long getMax() {
            return max;
        }

        public Detail setMax(long max) {
            this.max = max;
            return this;
        }
    }

    static String normalise(String input) {
        return input.replaceAll("\\s", "_").replaceAll("[^\\w]", "");
    }

}

package org.mockserver.metrics;

import io.netty.util.internal.PlatformDependent;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exposes JVM runtime metrics (heap / non-heap memory, threads, GC) as
 * Prometheus gauges so the dashboard Metrics view and Grafana can chart
 * MockServer's process health. Registered once alongside
 * {@link BuildInfoCollector} when metrics are enabled.
 * <p>
 * Reads only JDK {@code java.lang.management} MX beans — no extra dependency —
 * and is read-only/allocation-light, so scraping it has negligible overhead.
 * Values are sampled fresh on each {@link #collect()} (i.e. each scrape).
 */
public class JvmMetricsCollector implements MultiCollector {

    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private static final String MEMORY_USED = "jvm_memory_used_bytes";
    private static final String MEMORY_COMMITTED = "jvm_memory_committed_bytes";
    private static final String MEMORY_MAX = "jvm_memory_max_bytes";
    // Cumulative bytes allocated across all threads since JVM start. Unlike
    // jvm_memory_used_bytes (a LEVEL that GC saw-tooths, so a difference of two
    // samples measures net retention, not churn), this is a monotonically rising
    // COUNTER, so (sample_end - sample_start) is exactly the bytes allocated in the
    // window — the only faithful basis for an allocation-per-operation figure (e.g.
    // the TLS-handshake allocation cost the perf harness measures under
    // load). Sourced from HotSpot's com.sun.management ThreadMXBean; absent on a JVM
    // that does not implement it, in which case the metric is simply not emitted
    // (never a fabricated zero). Exposed as a gauge for parity with the other
    // cumulative jvm_* series here (GC_COUNT / GC_SECONDS are likewise gauges).
    private static final String MEMORY_ALLOCATED = "jvm_memory_allocated_bytes";
    // The JVM's NIO buffer pools ("direct", "mapped", ...): off-heap memory the heap metrics cannot
    // see. In the shipped JDK 25 runtime Netty's pooled buffers are NOT counted here but by
    // netty_direct_memory_used_bytes below. Names follow the Prometheus client_java convention.
    private static final String BUFFER_POOL_USED = "jvm_buffer_pool_used_bytes";
    private static final String BUFFER_POOL_BUFFERS = "jvm_buffer_pool_used_buffers";
    // Netty's own direct-memory counter. Netty maintains it only for some allocation modes and
    // returns -1 otherwise, in which case the metric is omitted rather than emitted as a fake value.
    private static final String NETTY_DIRECT_USED = "netty_direct_memory_used_bytes";
    private static final String THREADS_CURRENT = "jvm_threads_current";
    private static final String THREADS_DAEMON = "jvm_threads_daemon";
    private static final String GC_COUNT = "jvm_gc_collection_count";
    private static final String GC_SECONDS = "jvm_gc_collection_seconds_sum";
    // Info-style gauge (constant 1, meaning carried by labels) exposing what the
    // RUNNING JVM actually is — the JDK build and the garbage collector(s) in use.
    // Neither is derivable from any other metric here, yet both are exactly the
    // configuration a stored perf run must record about itself (a run measured
    // under ZGC+8GB must be distinguishable from one under G1). Resolving them from
    // the live process (MX beans + system properties) is the only faithful source:
    // a second JVM launched from the same image can pick a different ergonomic GC
    // because it does not inherit the entrypoint's heap sizing. Mirrors
    // {@link BuildInfoCollector}'s label-carried-value pattern.
    private static final String RUNTIME_INFO = "jvm_runtime_info";

    @Override
    public MetricSnapshots collect() {
        List<MetricSnapshot> snapshots = new ArrayList<>();

        MemoryUsage heap = MEMORY.getHeapMemoryUsage();
        MemoryUsage nonHeap = MEMORY.getNonHeapMemoryUsage();
        snapshots.add(areaGauge(MEMORY_USED, "JVM memory used in bytes", heap.getUsed(), nonHeap.getUsed()));
        snapshots.add(areaGauge(MEMORY_COMMITTED, "JVM memory committed in bytes", heap.getCommitted(), nonHeap.getCommitted()));
        snapshots.add(areaGauge(MEMORY_MAX, "JVM memory max in bytes (-1 if undefined)", heap.getMax(), nonHeap.getMax()));

        snapshots.add(simpleGauge(THREADS_CURRENT, "Current live thread count", THREADS.getThreadCount()));
        snapshots.add(simpleGauge(THREADS_DAEMON, "Daemon thread count", THREADS.getDaemonThreadCount()));

        long allocated = totalAllocatedBytes();
        if (allocated >= 0) {
            snapshots.add(simpleGauge(MEMORY_ALLOCATED, "Cumulative bytes allocated across all threads since JVM start (monotonic)", allocated));
        }

        addBufferPoolGauges(snapshots);
        long nettyDirect = nettyUsedDirectMemory();
        if (nettyDirect >= 0) {
            snapshots.add(simpleGauge(NETTY_DIRECT_USED, "Direct memory in use according to Netty's own counter (absent when Netty does not track it)", nettyDirect));
        }

        long gcCount = 0;
        long gcTimeMillis = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            long time = gc.getCollectionTime();
            if (count > 0) {
                gcCount += count;
            }
            if (time > 0) {
                gcTimeMillis += time;
            }
        }
        snapshots.add(simpleGauge(GC_COUNT, "Total number of GC collections across all collectors", gcCount));
        snapshots.add(simpleGauge(GC_SECONDS, "Total GC time across all collectors in seconds", gcTimeMillis / 1000.0));

        snapshots.add(runtimeInfoGauge());

        return new MetricSnapshots(snapshots);
    }

    private static void addBufferPoolGauges(List<MetricSnapshot> snapshots) {
        List<BufferPoolMXBean> pools;
        try {
            pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        } catch (Throwable ignore) {
            return;
        }
        if (pools.isEmpty()) {
            return;
        }
        GaugeSnapshot.Builder used = GaugeSnapshot.builder().name(BUFFER_POOL_USED)
            .help("Memory used by the JVM for NIO buffers in this pool (e.g. direct, mapped), in bytes");
        GaugeSnapshot.Builder buffers = GaugeSnapshot.builder().name(BUFFER_POOL_BUFFERS)
            .help("Number of NIO buffers currently in this pool (e.g. direct, mapped)");
        boolean anyUsed = false;
        boolean anyBuffers = false;
        for (BufferPoolMXBean pool : pools) {
            Labels labels = Labels.of("pool", label(pool.getName()));
            // -1 means the JVM cannot report the value; omit it rather than emit a fake reading
            long memoryUsed = pool.getMemoryUsed();
            if (memoryUsed >= 0) {
                used.dataPoint(GaugeDataPointSnapshot.builder().value(memoryUsed).labels(labels).build());
                anyUsed = true;
            }
            long count = pool.getCount();
            if (count >= 0) {
                buffers.dataPoint(GaugeDataPointSnapshot.builder().value(count).labels(labels).build());
                anyBuffers = true;
            }
        }
        if (anyUsed) {
            snapshots.add(used.build());
        }
        if (anyBuffers) {
            snapshots.add(buffers.build());
        }
    }

    private static long nettyUsedDirectMemory() {
        try {
            return PlatformDependent.usedDirectMemory();
        } catch (Throwable ignore) {
            return -1;
        }
    }

    private static GaugeSnapshot runtimeInfoGauge() {
        String gc = ManagementFactory.getGarbageCollectorMXBeans().stream()
            .map(GarbageCollectorMXBean::getName)
            .collect(Collectors.joining(","));
        return GaugeSnapshot.builder()
            .name(RUNTIME_INFO)
            .help("Running JVM build and garbage-collector information (value always 1; meaning is in the labels)")
            .dataPoint(GaugeDataPointSnapshot.builder()
                .value(1)
                .labels(Labels.of(
                    "gc", label(gc),
                    "java_runtime_version", label(System.getProperty("java.runtime.version")),
                    "java_vendor", label(System.getProperty("java.vendor")),
                    "java_version", label(System.getProperty("java.version")),
                    "vm_name", label(System.getProperty("java.vm.name"))
                ))
                .build())
            .build();
    }

    private static String label(String value) {
        return (value != null && !value.isEmpty()) ? value : "unknown";
    }

    private static GaugeSnapshot areaGauge(String name, String help, long heapValue, long nonHeapValue) {
        return GaugeSnapshot.builder()
            .name(name)
            .help(help)
            .dataPoint(GaugeDataPointSnapshot.builder().value(heapValue).labels(Labels.of("area", "heap")).build())
            .dataPoint(GaugeDataPointSnapshot.builder().value(nonHeapValue).labels(Labels.of("area", "nonheap")).build())
            .build();
    }

    private static GaugeSnapshot simpleGauge(String name, String help, double value) {
        return GaugeSnapshot.builder()
            .name(name)
            .help(help)
            .dataPoint(GaugeDataPointSnapshot.builder().value(value).build())
            .build();
    }

    /**
     * Cumulative bytes allocated across all threads, from HotSpot's
     * {@code com.sun.management.ThreadMXBean.getTotalThreadAllocatedBytes()}.
     * Returns {@code -1} (metric suppressed) when the running JVM does not
     * implement that extension or has thread-allocation accounting disabled, so a
     * non-HotSpot JVM never emits a fabricated value. Any reflective/linkage
     * failure degrades to {@code -1} rather than breaking the scrape.
     */
    private static long totalAllocatedBytes() {
        try {
            if (THREADS instanceof com.sun.management.ThreadMXBean) {
                com.sun.management.ThreadMXBean sunThreads = (com.sun.management.ThreadMXBean) THREADS;
                if (sunThreads.isThreadAllocatedMemorySupported() && sunThreads.isThreadAllocatedMemoryEnabled()) {
                    return sunThreads.getTotalThreadAllocatedBytes();
                }
            }
        } catch (Throwable ignore) {
            // no allocation accounting available on this JVM — suppress the metric
        }
        return -1;
    }

    @Override
    public List<String> getPrometheusNames() {
        return Arrays.asList(
            MEMORY_USED, MEMORY_COMMITTED, MEMORY_MAX, MEMORY_ALLOCATED,
            BUFFER_POOL_USED, BUFFER_POOL_BUFFERS, NETTY_DIRECT_USED,
            THREADS_CURRENT, THREADS_DAEMON,
            GC_COUNT, GC_SECONDS, RUNTIME_INFO
        );
    }
}

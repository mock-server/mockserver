package org.mockserver.netty.connection;

import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.ClassicHistogramBucket;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;

/**
 * Reads a MockServer latency histogram from the process-wide Prometheus registry, as a scrape would.
 */
final class RecordedDurations {

    static final String TRANSPORT = "mock_server_request_transport_duration_seconds";
    static final String HANDLER = "mock_server_request_duration_seconds";
    static final String READ_TO_DECODE = "mock_server_request_read_to_decode_duration_seconds";

    private RecordedDurations() {
    }

    static long count(String histogram) {
        HistogramSnapshot.HistogramDataPointSnapshot dataPoint = dataPoint(histogram);
        return dataPoint == null ? 0 : dataPoint.getCount();
    }

    static double sum(String histogram) {
        HistogramSnapshot.HistogramDataPointSnapshot dataPoint = dataPoint(histogram);
        return dataPoint == null ? 0 : dataPoint.getSum();
    }

    static long countOver(String histogram, double seconds) {
        HistogramSnapshot.HistogramDataPointSnapshot dataPoint = dataPoint(histogram);
        if (dataPoint == null) {
            return 0;
        }
        long atOrBelow = 0;
        for (ClassicHistogramBucket bucket : dataPoint.getClassicBuckets()) {
            if (bucket.getUpperBound() <= seconds) {
                atOrBelow += bucket.getCount();
            }
        }
        return dataPoint.getCount() - atOrBelow;
    }

    private static HistogramSnapshot.HistogramDataPointSnapshot dataPoint(String histogram) {
        for (MetricSnapshot snapshot : PrometheusRegistry.defaultRegistry.scrape()) {
            if (snapshot.getMetadata().getName().equals(histogram) && snapshot instanceof HistogramSnapshot) {
                return ((HistogramSnapshot) snapshot).getDataPoints().get(0);
            }
        }
        return null;
    }
}

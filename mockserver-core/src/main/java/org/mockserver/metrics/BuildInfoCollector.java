package org.mockserver.metrics;

import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.Labels;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.mockserver.version.Version;

import java.util.Collections;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class BuildInfoCollector implements MultiCollector {

    private static final String METRIC_NAME = "mock_server_build_info";

    public MetricSnapshots collect() {
        String version = Version.getVersion();
        String majorMinorVersion = Version.getMajorMinorVersion();
        String groupId = Version.getGroupId();
        String artifactId = Version.getArtifactId();

        GaugeSnapshot gaugeSnapshot = GaugeSnapshot.builder()
            .name(METRIC_NAME)
            .help("Mock Server build information")
            .dataPoint(
                GaugeSnapshot.GaugeDataPointSnapshot.builder()
                    .value(1)
                    .labels(Labels.of(
                        "artifact_id", artifactId != null ? artifactId : "unknown",
                        "group_id", groupId != null ? groupId : "unknown",
                        "major_minor_version", majorMinorVersion != null ? majorMinorVersion : "unknown",
                        "version", version != null ? version : "unknown"
                    ))
                    .build()
            )
            .build();

        return new MetricSnapshots(gaugeSnapshot);
    }

    @Override
    public List<String> getPrometheusNames() {
        return Collections.singletonList(METRIC_NAME);
    }

}

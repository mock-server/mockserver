package org.mockserver.metrics;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import org.mockserver.version.Version;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;

/**
 * @author jamesdbloom
 */
public class BuildInfoCollector extends Collector {

    public List<Collector.MetricFamilySamples> collect() {
        String version = Version.getVersion();
        String majorMinorVersion = Version.getMajorMinorVersion();
        String groupId = Version.getGroupId();
        String artifactId = Version.getArtifactId();

        return Collections.singletonList(
            new GaugeMetricFamily(
                "mock_server_build_info",
                "Mock Server build information",
                asList(
                    "version",
                    "major_minor_version",
                    "group_id",
                    "artifact_id"
                )
            ).addMetric(asList(
                version != null ? version : "unknown",
                majorMinorVersion != null ? majorMinorVersion : "unknown",
                groupId != null ? groupId : "unknown",
                artifactId != null ? artifactId : "unknown"
            ), 1L)
        );
    }

}
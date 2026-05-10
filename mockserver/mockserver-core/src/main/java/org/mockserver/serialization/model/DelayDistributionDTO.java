package org.mockserver.serialization.model;

import org.mockserver.model.DelayDistribution;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

public class DelayDistributionDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<DelayDistribution> {

    private DelayDistribution.Type type;
    private Long min;
    private Long max;
    private Long median;
    private Long p99;
    private Long mean;
    private Long stdDev;

    public DelayDistributionDTO(DelayDistribution distribution) {
        if (distribution != null) {
            type = distribution.getType();
            min = distribution.getMin();
            max = distribution.getMax();
            median = distribution.getMedian();
            p99 = distribution.getP99();
            mean = distribution.getMean();
            stdDev = distribution.getStdDev();
        }
    }

    public DelayDistributionDTO() {
    }

    public DelayDistribution buildObject() {
        DelayDistribution distribution = new DelayDistribution();
        distribution.setType(type);
        distribution.setMin(min != null ? Math.max(0, min) : null);
        distribution.setMax(max != null ? Math.max(0, max) : null);
        distribution.setMedian(median != null ? Math.max(1, median) : null);
        distribution.setP99(p99 != null ? Math.max(1, p99) : null);
        distribution.setMean(mean != null ? Math.max(0, mean) : null);
        distribution.setStdDev(stdDev != null ? Math.max(0, stdDev) : null);
        return distribution;
    }

    public DelayDistribution.Type getType() {
        return type;
    }

    public DelayDistributionDTO setType(DelayDistribution.Type type) {
        this.type = type;
        return this;
    }

    public Long getMin() {
        return min;
    }

    public DelayDistributionDTO setMin(Long min) {
        this.min = min;
        return this;
    }

    public Long getMax() {
        return max;
    }

    public DelayDistributionDTO setMax(Long max) {
        this.max = max;
        return this;
    }

    public Long getMedian() {
        return median;
    }

    public DelayDistributionDTO setMedian(Long median) {
        this.median = median;
        return this;
    }

    public Long getP99() {
        return p99;
    }

    public DelayDistributionDTO setP99(Long p99) {
        this.p99 = p99;
        return this;
    }

    public Long getMean() {
        return mean;
    }

    public DelayDistributionDTO setMean(Long mean) {
        this.mean = mean;
        return this;
    }

    public Long getStdDev() {
        return stdDev;
    }

    public DelayDistributionDTO setStdDev(Long stdDev) {
        this.stdDev = stdDev;
        return this;
    }
}

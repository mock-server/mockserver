package org.mockserver.templates.engine.helpers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.concurrent.ThreadLocalRandom;

public class MathTemplateHelper {

    public int randomInt(int min, int max) {
        if (max == Integer.MAX_VALUE) {
            long range = (long) max - (long) min + 1L;
            return (int) (min + ThreadLocalRandom.current().nextLong(range));
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public double randomDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public double randomDouble(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    public int abs(int value) {
        return Math.abs(value);
    }

    public double abs(double value) {
        return Math.abs(value);
    }

    public int min(int a, int b) {
        return Math.min(a, b);
    }

    public int max(int a, int b) {
        return Math.max(a, b);
    }

    public double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    public String format(double value, String pattern) {
        return new DecimalFormat(pattern).format(value);
    }

    public double ceil(double value) {
        return Math.ceil(value);
    }

    public double floor(double value) {
        return Math.floor(value);
    }

    @Override
    public String toString() {
        return "MathTemplateHelper";
    }
}

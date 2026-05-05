package org.mockserver.dashboard.serializers;

import org.apache.commons.lang3.StringUtils;

public class LogMessageDescription implements Description {
    final String firstPart;
    final String secondPart;
    final int length;
    final DescriptionProcessor descriptionProcessor;

    public LogMessageDescription(String firstPart, String secondPart, DescriptionProcessor descriptionProcessor) {
        this.firstPart = firstPart;
        this.secondPart = secondPart;
        this.length = firstPart.length() + secondPart.length();
        this.descriptionProcessor = descriptionProcessor;
    }

    public int length() {
        return length + 1;
    }

    public String toObject() {
        return firstPart + " " + secondPart + StringUtils.repeat(" ", descriptionProcessor.getMaxLogEventLength() - length + 1) + " ";
    }
}
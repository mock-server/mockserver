package org.mockserver.dashboard.serializers;

import org.apache.commons.lang3.StringUtils;

public class RequestDefinitionDescription implements Description {
    final String firstPart;
    final String secondPart;
    private final boolean openAPI;
    final int length;
    final DescriptionProcessor descriptionProcessor;

    public RequestDefinitionDescription(String firstPart, String secondPart, DescriptionProcessor descriptionProcessor, boolean openAPI) {
        this.firstPart = firstPart;
        this.secondPart = secondPart;
        this.openAPI = openAPI;
        this.length = firstPart.length() + secondPart.length();
        this.descriptionProcessor = descriptionProcessor;
    }

    public int length() {
        return length + 1;
    }

    public String toObject() {
        return firstPart + StringUtils.repeat(" ", (openAPI ? descriptionProcessor.getMaxOpenAPILength() : descriptionProcessor.getMaxHttpRequestLength()) - length + 1) + secondPart;
    }
}
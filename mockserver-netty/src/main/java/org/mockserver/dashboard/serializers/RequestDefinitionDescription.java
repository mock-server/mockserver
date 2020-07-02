package org.mockserver.dashboard.serializers;

import org.apache.commons.lang3.StringUtils;

public class RequestDefinitionDescription implements Description {
    final String firstPart;
    final String secondPart;
    private final boolean openAPI;
    final int length;
    final DescriptionProcessor descriptionProcessor;

    public RequestDefinitionDescription(String firstPart, String secondPart, DescriptionProcessor descriptionProcessor, boolean openAPI) {
        this.openAPI = openAPI;
        if (firstPart.length() + secondPart.length() > MAX_LENGTH) {
            if (secondPart.length() > firstPart.length()) {
                this.firstPart = firstPart;
                this.secondPart = StringUtils.abbreviate(secondPart, MAX_LENGTH - firstPart.length());
            } else {
                this.firstPart = StringUtils.abbreviate(firstPart, MAX_LENGTH - secondPart.length());
                this.secondPart = secondPart;
            }
        } else {
            this.firstPart = firstPart;
            this.secondPart = secondPart;
        }
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
package org.mockserver.dashboard.serializers;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;

public class RequestDefinitionObjectDescription implements Description {
    private final String first;
    private final Object object;
    private final String second;
    private final DescriptionProcessor descriptionProcessor;

    public RequestDefinitionObjectDescription(String first, Object object, String second, DescriptionProcessor descriptionProcessor) {
        this.first = first;
        this.object = object;
        this.second = second;
        this.descriptionProcessor = descriptionProcessor;
    }

    public int length() {
        return first.length() + 8 + second.length() + 1;
    }

    public Object toObject() {
        return ImmutableMap.of(
            "json", true,
            "object", object,
            "first", first,
            "second", StringUtils.repeat(" ", descriptionProcessor.getMaxOpenAPIObjectLength() - length() + 1) + second
        );
    }
}
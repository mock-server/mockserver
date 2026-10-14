package org.mockserver.dashboard.serializers;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;

/**
 * A received request that has no method or path, such as a DNS query or a binary message, sent as its kind and a
 * summary for the dashboard to lay out as it lays out an HTTP request's method and path.
 */
public class ReceivedRequestDescription implements Description {
    private final String kind;
    private final String summary;

    public ReceivedRequestDescription(String kind, String summary) {
        this.kind = kind;
        this.summary = StringUtils.abbreviate(summary, MAX_LENGTH);
    }

    public int length() {
        return kind.length() + summary.length() + 1;
    }

    public Object toObject() {
        return ImmutableMap.of(
            "json", false,
            "first", kind,
            "second", summary
        );
    }
}

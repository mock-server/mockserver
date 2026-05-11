package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarNameValuePair {

    @JsonProperty("name")
    private String name;

    @JsonProperty("value")
    private String value;

    public HarNameValuePair() {
    }

    public HarNameValuePair(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public HarNameValuePair withName(String name) {
        this.name = name;
        return this;
    }

    public String getValue() {
        return value;
    }

    public HarNameValuePair withValue(String value) {
        this.value = value;
        return this;
    }
}

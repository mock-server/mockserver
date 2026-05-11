package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarCreator {

    @JsonProperty("name")
    private String name;

    @JsonProperty("version")
    private String version;

    public String getName() {
        return name;
    }

    public HarCreator withName(String name) {
        this.name = name;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public HarCreator withVersion(String version) {
        this.version = version;
        return this;
    }
}

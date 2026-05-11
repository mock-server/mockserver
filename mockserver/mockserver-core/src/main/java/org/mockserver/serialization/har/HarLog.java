package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarLog {

    @JsonProperty("version")
    private final String version = "1.2";

    @JsonProperty("creator")
    private HarCreator creator;

    @JsonProperty("entries")
    private List<HarEntry> entries;

    public String getVersion() {
        return version;
    }

    public HarCreator getCreator() {
        return creator;
    }

    public HarLog withCreator(HarCreator creator) {
        this.creator = creator;
        return this;
    }

    public List<HarEntry> getEntries() {
        return entries;
    }

    public HarLog withEntries(List<HarEntry> entries) {
        this.entries = entries;
        return this;
    }
}

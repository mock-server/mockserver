package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarContent {

    @JsonProperty("size")
    private long size;

    @JsonProperty("mimeType")
    private String mimeType = "";

    @JsonProperty("text")
    private String text;

    @JsonProperty("encoding")
    private String encoding;

    public long getSize() {
        return size;
    }

    public HarContent withSize(long size) {
        this.size = size;
        return this;
    }

    public String getMimeType() {
        return mimeType;
    }

    public HarContent withMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public String getText() {
        return text;
    }

    public HarContent withText(String text) {
        this.text = text;
        return this;
    }

    public String getEncoding() {
        return encoding;
    }

    public HarContent withEncoding(String encoding) {
        this.encoding = encoding;
        return this;
    }
}

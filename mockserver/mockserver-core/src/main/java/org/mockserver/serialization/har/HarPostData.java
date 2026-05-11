package org.mockserver.serialization.har;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HarPostData {

    @JsonProperty("mimeType")
    private String mimeType;

    @JsonProperty("params")
    private List<HarNameValuePair> params;

    @JsonProperty("text")
    private String text;

    public String getMimeType() {
        return mimeType;
    }

    public HarPostData withMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public List<HarNameValuePair> getParams() {
        return params;
    }

    public HarPostData withParams(List<HarNameValuePair> params) {
        this.params = params;
        return this;
    }

    public String getText() {
        return text;
    }

    public HarPostData withText(String text) {
        this.text = text;
        return this;
    }
}

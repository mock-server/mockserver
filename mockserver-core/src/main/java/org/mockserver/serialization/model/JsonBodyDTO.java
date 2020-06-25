package org.mockserver.serialization.model;

import org.mockserver.matchers.MatchType;
import org.mockserver.model.Body;
import org.mockserver.model.JsonBody;

/**
 * @author jamesdbloom
 */
public class JsonBodyDTO extends BodyWithContentTypeDTO {

    private final String json;
    private final MatchType matchType;
    private final byte[] rawBytes;

    public JsonBodyDTO(JsonBody jsonBody) {
        this(jsonBody, null);
    }

    public JsonBodyDTO(JsonBody jsonBody, Boolean not) {
        super(Body.Type.JSON, not, jsonBody);
        json = jsonBody.getValue();
        matchType = jsonBody.getMatchType();
        rawBytes = jsonBody.getRawBytes();
    }

    public String getJson() {
        return json;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public JsonBody buildObject() {
        return new JsonBody(getJson(), getRawBytes(), getMediaType(), getMatchType());
    }
}

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
    private final byte[] rawBinaryData;

    public JsonBodyDTO(JsonBody jsonBody) {
        this(jsonBody, false);
    }

    public JsonBodyDTO(JsonBody jsonBody, Boolean not) {
        super(Body.Type.JSON, not, jsonBody.getContentType());
        json = jsonBody.getValue();
        matchType = jsonBody.getMatchType();
        rawBinaryData = jsonBody.getRawBytes();
    }

    public String getJson() {
        return json;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public byte[] getRawBinaryData() {
        return rawBinaryData;
    }

    public JsonBody buildObject() {
        return new JsonBody(getJson(), getRawBinaryData(), getMediaType(), getMatchType());
    }
}

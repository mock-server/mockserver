package org.mockserver.model;

import org.mockserver.matchers.JsonBodyMatchType;

/**
 * @author jamesdbloom
 */
public class JsonBody extends Body {

    public static final JsonBodyMatchType DEFAULT_MATCH_TYPE = JsonBodyMatchType.ONLY_MATCHING_FIELDS;
    private final String json;
    private final JsonBodyMatchType matchType;

    public JsonBody(String json) {
        this(json, DEFAULT_MATCH_TYPE);
    }

    public JsonBody(String json, JsonBodyMatchType matchType) {
        super(Type.JSON);
        this.json = json;
        this.matchType = matchType;
    }

    public static JsonBody json(String json) {
        return new JsonBody(json);
    }

    public static JsonBody json(String json, JsonBodyMatchType jsonBodyMatchType) {
        return new JsonBody(json, jsonBodyMatchType);
    }

    public String getValue() {
        return json;
    }

    public JsonBodyMatchType getMatchType() {
        return matchType;
    }

}

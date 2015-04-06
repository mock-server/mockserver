package org.mockserver.model;

import org.mockserver.matchers.MatchType;

/**
 * @author jamesdbloom
 */
public class JsonBody extends Body {

    public static final MatchType DEFAULT_MATCH_TYPE = MatchType.ONLY_MATCHING_FIELDS;
    private final String json;
    private final MatchType matchType;

    public JsonBody(String json) {
        this(json, DEFAULT_MATCH_TYPE);
    }

    public JsonBody(String json, MatchType matchType) {
        super(Type.JSON);
        this.json = json;
        this.matchType = matchType;
    }

    public static JsonBody json(String json) {
        return new JsonBody(json);
    }

    public static JsonBody json(String json, MatchType matchType) {
        return new JsonBody(json, matchType);
    }

    public String getValue() {
        return json;
    }

    public MatchType getMatchType() {
        return matchType;
    }

}

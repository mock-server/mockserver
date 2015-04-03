package org.mockserver.matchers;

import org.skyscreamer.jsonassert.JSONCompareMode;

/**
* @author jamesdbloom
*/
public enum JsonBodyMatchType {
    STRICT(JSONCompareMode.STRICT),
    ONLY_MATCHING_FIELDS(JSONCompareMode.LENIENT);

    private final JSONCompareMode nonExtensible;

    JsonBodyMatchType(JSONCompareMode nonExtensible) {

        this.nonExtensible = nonExtensible;
    }

    public JSONCompareMode getNonExtensible() {
        return nonExtensible;
    }
}

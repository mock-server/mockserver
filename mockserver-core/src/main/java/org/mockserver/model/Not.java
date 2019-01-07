package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.Charset;
import java.util.List;

import static org.mockserver.model.Header.header;

/**
 * @author jamesdbloom
 */
public class Not extends ObjectWithJsonToString {

    Boolean not;

    public static <T extends Not> T not(T t) {
        t.not = true;
        return t;
    }

    public static <T extends Not> T not(T t, Boolean not) {
        if (not != null && not) {
            t.not = true;
        }
        return t;
    }

    @JsonIgnore
    public boolean isNot() {
        return not != null && not;
    }

    public Boolean getNot() {
        return not;
    }

    public void setNot(Boolean not) {
        this.not = not;
    }
}

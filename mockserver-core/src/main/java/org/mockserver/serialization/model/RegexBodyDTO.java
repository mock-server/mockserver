package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.RegexBody;

/**
 * @author jamesdbloom
 */
public class RegexBodyDTO extends BodyDTO {

    private final String regex;

    public RegexBodyDTO(RegexBody regexBody) {
        this(regexBody, false);
    }

    public RegexBodyDTO(RegexBody regexBody, Boolean not) {
        super(Body.Type.REGEX, not);
        this.regex = regexBody.getValue();
    }

    public String getRegex() {
        return regex;
    }

    public RegexBody buildObject() {
        return new RegexBody(getRegex());
    }
}

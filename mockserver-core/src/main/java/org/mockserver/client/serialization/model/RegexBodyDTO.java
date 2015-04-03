package org.mockserver.client.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.RegexBody;

/**
 * @author jamesdbloom
 */
public class RegexBodyDTO extends BodyDTO {

    private String regex;

    public RegexBodyDTO(RegexBody regexBody) {
        super(Body.Type.REGEX);
        this.regex = regexBody.getValue();
    }

    protected RegexBodyDTO() {
    }

    public String getRegex() {
        return regex;
    }

    public RegexBody buildObject() {
        return new RegexBody(getRegex());
    }
}

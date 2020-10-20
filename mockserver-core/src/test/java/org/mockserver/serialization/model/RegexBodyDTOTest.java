package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.RegexBody;
import org.mockserver.model.XmlSchemaBody;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.RegexBody.regex;

/**
 * @author jamesdbloom
 */
public class RegexBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        RegexBodyDTO regexBody = new RegexBodyDTO(new RegexBody("some_body"));

        // then
        assertThat(regexBody.getRegex(), is("some_body"));
        assertThat(regexBody.getType(), is(Body.Type.REGEX));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        RegexBody regexBody = new RegexBodyDTO(new RegexBody("some_body")).buildObject();

        // then
        assertThat(regexBody.getValue(), is("some_body"));
        assertThat(regexBody.getType(), is(Body.Type.REGEX));
    }

    @Test
    public void shouldBuildCorrectObjectWithOptional() {
        // when
        RegexBody regexBody = new RegexBodyDTO((RegexBody) new RegexBody("some_body").withOptional(true)).buildObject();

        // then
        assertThat(regexBody.getValue(), is("some_body"));
        assertThat(regexBody.getType(), is(Body.Type.REGEX));
        assertThat(regexBody.getOptional(), is(true));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticBuilder() {
        assertThat(regex("some_body"), is(new RegexBody("some_body")));
    }

    @Test
    public void shouldHandleNull() {
        // given
        String body = null;

        // when
        RegexBody regexBody = new RegexBodyDTO(new RegexBody(body)).buildObject();

        // then
        assertThat(regexBody.getValue(), nullValue());
        assertThat(regexBody.getType(), is(Body.Type.REGEX));
    }

    @Test
    public void shouldHandleEmptyByteArray() {
        // given
        String body = "";

        // when
        RegexBody regexBody = new RegexBodyDTO(new RegexBody(body)).buildObject();

        // then
        assertThat(regexBody.getValue(), is(""));
        assertThat(regexBody.getType(), is(Body.Type.REGEX));
    }
}

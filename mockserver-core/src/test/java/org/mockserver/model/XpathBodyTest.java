package org.mockserver.model;

import com.google.common.base.Charsets;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.XPathBody.xpath;

/**
 * @author jamesdbloom
 */
public class XpathBodyTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        XPathBody xPathBody = new XPathBody("some_body");

        // then
        assertThat(xPathBody.getValue(), is("some_body"));
        assertThat(xPathBody.getType(), is(Body.Type.XPATH));
        assertThat(xPathBody.getContentType(), nullValue());
        assertThat(xPathBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
    }

    @Test
    public void shouldReturnValuesFromStaticBuilder() {
        // when
        XPathBody xPathBody = xpath("some_body");

        // then
        assertThat(xPathBody.getValue(), is("some_body"));
        assertThat(xPathBody.getType(), is(Body.Type.XPATH));
        assertThat(xPathBody.getContentType(), nullValue());
        assertThat(xPathBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
    }

}

package org.mockserver.model;

import com.google.common.base.Charsets;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.XmlBody.xml;

/**
 * @author jamesdbloom
 */
public class XmlBodyTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        XmlBody xmlBody = new XmlBody("some_body");

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getContentType(), nullValue());
        assertThat(xmlBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
    }

    @Test
    public void shouldReturnValuesFromStaticBuilder() {
        // when
        XmlBody xmlBody = xml("some_body");

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getContentType(), nullValue());
        assertThat(xmlBody.getCharset(Charsets.UTF_8), is(Charsets.UTF_8));
    }

}

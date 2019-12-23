package org.mockserver.model;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.model.XmlBody.xml;

/**
 * @author jamesdbloom
 */
public class XmlBodyTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(xml("some_body"), xml("some_body"));
        assertNotSame(xml("some_body"), xml("some_body"));
    }

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        XmlBody xmlBody = new XmlBody("some_body");

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getContentType(), is(XmlBody.DEFAULT_CONTENT_TYPE.toString()));
        assertThat(xmlBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(xmlBody.getContentType(), is(MediaType.create("application", "xml").toString()));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithCharset() {
        // when
        XmlBody xmlBody = new XmlBody("some_body", StandardCharsets.UTF_16);

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getCharset(null), is(StandardCharsets.UTF_16));
        assertThat(xmlBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_16));
        assertThat(xmlBody.getContentType(), is(MediaType.APPLICATION_XML_UTF_8.withCharset(StandardCharsets.UTF_16).toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructor() {
        // when
        XmlBody xmlBody = xml("some_body");

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getContentType(), is(XmlBody.DEFAULT_CONTENT_TYPE.toString()));
        assertThat(xmlBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(xmlBody.getContentType(), is(MediaType.create("application", "xml").toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithCharset() {
        // when
        XmlBody xmlBody = xml("some_body", StandardCharsets.UTF_16);

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getCharset(null), is(StandardCharsets.UTF_16));
        assertThat(xmlBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_16));
        assertThat(xmlBody.getContentType(), is(MediaType.APPLICATION_XML_UTF_8.withCharset(StandardCharsets.UTF_16).toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithNullCharset() {
        // when
        XmlBody xmlBody = xml("some_body", (Charset) null);

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getCharset(null), nullValue());
        assertThat(xmlBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(xmlBody.getContentType(), nullValue());
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithContentType() {
        // when
        XmlBody xmlBody = xml("some_body", MediaType.PLAIN_TEXT_UTF_8);

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getCharset(null), is(StandardCharsets.UTF_8));
        assertThat(xmlBody.getCharset(StandardCharsets.UTF_16), is(StandardCharsets.UTF_8));
        assertThat(xmlBody.getContentType(), is(MediaType.PLAIN_TEXT_UTF_8.toString()));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithNullMediaType() {
        // when
        XmlBody xmlBody = xml("some_body", (MediaType) null);

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getCharset(null), nullValue());
        assertThat(xmlBody.getCharset(StandardCharsets.UTF_8), is(StandardCharsets.UTF_8));
        assertThat(xmlBody.getContentType(), nullValue());
    }

}

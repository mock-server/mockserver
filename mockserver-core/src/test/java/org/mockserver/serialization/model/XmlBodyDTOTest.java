package org.mockserver.serialization.model;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.MediaType;
import org.mockserver.model.XmlBody;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockserver.model.XmlBody.xml;

/**
 * @author jamesdbloom
 */
public class XmlBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        XmlBodyDTO xmlBody = new XmlBodyDTO(new XmlBody("some_body"));

        // then
        assertThat(xmlBody.getXml(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getMediaType(), is(MediaType.create("application", "xml")));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMediaTypeAndRawBytes() {
        // when
        byte[] rawBytes = RandomUtils.nextBytes(20);
        XmlBodyDTO xmlBody = new XmlBodyDTO(new XmlBody("some_body", rawBytes, MediaType.create("application", "xml").withCharset(StandardCharsets.UTF_16)));

        // then
        assertThat(xmlBody.getXml(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getMediaType(), is(MediaType.create("application", "xml").withCharset(StandardCharsets.UTF_16)));
        assertThat(xmlBody.getRawBytes(), is(rawBytes));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithMediaType() {
        // when
        XmlBodyDTO xmlBody = new XmlBodyDTO(new XmlBody("some_body", null, MediaType.create("application", "xml").withCharset(StandardCharsets.UTF_16)));

        // then
        assertThat(xmlBody.getXml(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getMediaType(), is(MediaType.create("application", "xml").withCharset(StandardCharsets.UTF_16)));
        assertThat(xmlBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.UTF_16)));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithCharset() {
        // when
        XmlBodyDTO xmlBody = new XmlBodyDTO(new XmlBody("some_body", StandardCharsets.UTF_16));

        // then
        assertThat(xmlBody.getXml(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getMediaType(), is(MediaType.create("application", "xml").withCharset(StandardCharsets.UTF_16)));
        assertThat(xmlBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.UTF_16)));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        XmlBody xmlBody = new XmlBodyDTO(new XmlBody("some_body")).buildObject();

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getContentType(), is("application/xml"));
        assertThat(xmlBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void shouldBuildCorrectObjectWithMediaTypeAndRawBytes() {
        // when
        byte[] rawBytes = RandomUtils.nextBytes(20);
        XmlBody xmlBody = new XmlBodyDTO(new XmlBody("some_body", rawBytes, MediaType.create("application", "xml").withCharset(StandardCharsets.UTF_16))).buildObject();

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getContentType(), is("application/xml; charset=utf-16"));
        assertThat(xmlBody.getRawBytes(), is(rawBytes));
    }

    @Test
    public void shouldBuildCorrectObjectWithMediaType() {
        // when
        XmlBody xmlBody = new XmlBodyDTO(new XmlBody("some_body", null, MediaType.create("application", "xml").withCharset(StandardCharsets.UTF_16))).buildObject();

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getContentType(), is("application/xml; charset=utf-16"));
        assertThat(xmlBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.UTF_16)));
    }

    @Test
    public void shouldBuildCorrectObjectWithCharset() {
        // when
        XmlBody xmlBody = new XmlBodyDTO(new XmlBody("some_body", StandardCharsets.UTF_16)).buildObject();

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
        assertThat(xmlBody.getContentType(), is("application/xml; charset=utf-16"));
        assertThat(xmlBody.getRawBytes(), is("some_body".getBytes(StandardCharsets.UTF_16)));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticBuilder() {
        assertThat(xml("some_body"), is(new XmlBody("some_body")));
    }



    @Test
    public void shouldHandleNull() {
        // given
        String body = null;

        // when
        XmlBody xmlBody = new XmlBodyDTO(new XmlBody(body)).buildObject();

        // then
        assertThat(xmlBody.getValue(), nullValue());
        assertThat(xmlBody.getType(), is(Body.Type.XML));
    }

    @Test
    public void shouldHandleEmptyByteArray() {
        // given
        String body = "";

        // when
        XmlBody xmlBody = new XmlBodyDTO(new XmlBody(body)).buildObject();

        // then
        assertThat(xmlBody.getValue(), is(""));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
    }
}

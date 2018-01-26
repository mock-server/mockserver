package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.XmlBody;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.XmlBody.xml;

/**
 * @author jamesdbloom
 */
public class XmlBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        XmlBodyDTO xpathBody = new XmlBodyDTO(new XmlBody("some_body"));

        // then
        assertThat(xpathBody.getXml(), is("some_body"));
        assertThat(xpathBody.getType(), is(Body.Type.XML));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        XmlBody xmlBody = new XmlBodyDTO(new XmlBody("some_body")).buildObject();

        // then
        assertThat(xmlBody.getValue(), is("some_body"));
        assertThat(xmlBody.getType(), is(Body.Type.XML));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticBuilder() {
        assertThat(xml("some_body"), is(new XmlBody("some_body")));
    }
}

package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.XPathBody;
import org.mockserver.model.XmlBody;

import java.nio.charset.StandardCharsets;

import com.google.common.collect.ImmutableMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.XPathBody.xpath;

/**
 * @author jamesdbloom
 */
public class XPathBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        XPathBodyDTO xpathBody = new XPathBodyDTO(new XPathBody("some_body"));

        // then
        assertThat(xpathBody.getXPath(), is("some_body"));
        assertThat(xpathBody.getType(), is(Body.Type.XPATH));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        XPathBody xPathBody = new XPathBodyDTO(new XPathBody("some_body")).buildObject();

        // then
        assertThat(xPathBody.getValue(), is("some_body"));
        assertThat(xPathBody.getType(), is(Body.Type.XPATH));
    }

    @Test
    public void shouldBuildCorrectObjectWithOptional() {
        // when
        XPathBody xPathBody = new XPathBodyDTO((XPathBody) new XPathBody("some_body").withOptional(true)).buildObject();

        // then
        assertThat(xPathBody.getValue(), is("some_body"));
        assertThat(xPathBody.getType(), is(Body.Type.XPATH));
        assertThat(xPathBody.getOptional(), is(true));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticBuilder() {
        assertThat(xpath("some_body"), is(new XPathBody("some_body")));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticBuilderWithNamespacePrefixes() {
        assertThat(xpath("some_body", ImmutableMap.of("foo", "http://foo")), is(new XPathBody("some_body", ImmutableMap.of("foo", "http://foo"))));
    }
}

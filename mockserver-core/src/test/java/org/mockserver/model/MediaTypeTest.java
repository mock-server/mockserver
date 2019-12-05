package org.mockserver.model;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class MediaTypeTest {

    @Test
    public void shouldParseRFC7231ContentTypeExamples() {
        // when
        String[] rfcContentTypeExamples = new String[]{
            "text/html;charset=utf-8",
            "text/html;charset=UTF-8",
            "Text/HTML;Charset=\"utf-8\"",
            "text/html; charset=\"utf-8\""
        };

        for (String rfcContentTypeExample : rfcContentTypeExamples) {
            // then
            assertThat(rfcContentTypeExample, MediaType.parse(rfcContentTypeExample), is(new MediaType("text", "html").withCharset("utf-8")));
        }
    }

    @Test
    public void shouldParseContentTypeWithSpecialCharacters() {
        assertThat(MediaType.parse("application/my.type+xml"), is(new MediaType("application", "my.type+xml")));
        assertThat(MediaType.parse("application/my.type+xml;charset=utf-8"), is(new MediaType("application", "my.type+xml").withCharset("utf-8")));
        assertThat(MediaType.parse("application/my.type+xml; Charset=\"utf-8\""), is(new MediaType("application", "my.type+xml").withCharset("utf-8")));
    }

    @Test
    public void shouldParseWithoutCharset() {
        // when
        String[] rfcContentTypeExamples = new String[]{
            "text/html",
            "text/html;",
            "Text/HTML",
            "Text/HTML;",
        };

        for (String rfcContentTypeExample : rfcContentTypeExamples) {
            // then
            assertThat(rfcContentTypeExample, MediaType.parse(rfcContentTypeExample), is(new MediaType("text", "html")));
        }
    }

    @Test
    public void shouldParseWithoutType() {
        // when
        String[] rfcContentTypeExamples = new String[]{
            "charset=utf-8",
            "charset=UTF-8",
            "Charset=\"utf-8\"",
            "charset=\"utf-8\""
        };

        for (String rfcContentTypeExample : rfcContentTypeExamples) {
            // then
            assertThat(rfcContentTypeExample, MediaType.parse(rfcContentTypeExample), is(new MediaType(null, null).withCharset("utf-8")));
        }
    }

    @Test
    public void shouldSerialiseToStringRFC7231ContentTypeExamples() {
        // when
        String[] rfcContentTypeExamples = new String[]{
            "text/html;charset=utf-8",
            "text/html;charset=UTF-8",
            "Text/HTML;Charset=\"utf-8\"",
            "text/html; charset=\"utf-8\""
        };

        for (String rfcContentTypeExample : rfcContentTypeExamples) {
            // then
            assertThat(rfcContentTypeExample, MediaType.parse(rfcContentTypeExample).toString(), is("text/html; charset=utf-8"));
        }
    }

    @Test
    public void shouldSerialiseToStringWithoutCharset() {
        // when
        String[] rfcContentTypeExamples = new String[]{
            "text/html",
            "text/html;",
            "Text/HTML",
            "Text/HTML;",
        };

        for (String rfcContentTypeExample : rfcContentTypeExamples) {
            // then
            assertThat(rfcContentTypeExample, MediaType.parse(rfcContentTypeExample).toString(), is("text/html"));
        }
    }

    @Test
    public void shouldSerialiseToStringWithoutType() {
        // when
        String[] rfcContentTypeExamples = new String[]{
            "charset=utf-8",
            "charset=UTF-8",
            "Charset=\"utf-8\"",
            "charset=\"utf-8\""
        };

        for (String rfcContentTypeExample : rfcContentTypeExamples) {
            // then
            assertThat(rfcContentTypeExample, MediaType.parse(rfcContentTypeExample).toString(), is("charset=utf-8"));
        }
    }

    @Test
    public void shouldSerialiseToStringFromConstructor() {
        assertThat(MediaType.create("text", "html").withCharset(StandardCharsets.UTF_8).toString(), is("text/html; charset=utf-8"));
        assertThat(MediaType.create("text", "html").withCharset("utf-8").toString(), is("text/html; charset=utf-8"));
        assertThat(MediaType.create("text", "html").withCharset("UTF-8").toString(), is("text/html; charset=utf-8"));
        assertThat(MediaType.create(null, null).withCharset(StandardCharsets.UTF_8).toString(), is("charset=utf-8"));
    }

    @Test
    public void shouldSupportSettingCharsetObject() {
        // when
        String[] rfcContentTypeExamples = new String[]{
            "text/html;charset=utf-8",
            "text/html;charset=UTF-8",
            "Text/HTML;Charset=\"utf-8\"",
            "text/html; charset=\"utf-8\""
        };

        for (String rfcContentTypeExample : rfcContentTypeExamples) {
            // then
            assertThat(rfcContentTypeExample, MediaType.parse(rfcContentTypeExample), is(MediaType.create("text", "html").withCharset(StandardCharsets.UTF_8)));
        }
    }

    @Test
    public void shouldSupportSettingCharsetObjectWithoutType() {
        // when
        String[] rfcContentTypeExamples = new String[]{
            "charset=utf-8",
            "charset=UTF-8",
            "Charset=\"utf-8\"",
            "charset=\"utf-8\""
        };

        for (String rfcContentTypeExample : rfcContentTypeExamples) {
            // then
            assertThat(rfcContentTypeExample, MediaType.parse(rfcContentTypeExample), is(MediaType.create(null, null).withCharset(StandardCharsets.UTF_8)));
        }
    }

    @Test
    public void shouldSupportAdditionParameters() {
        assertThat(MediaType.parse("application/soap+xml;charset=UTF-8;action=\"somerandomstuff\""), is(new MediaType("application", "soap+xml", ImmutableMap.of(
            "action", "somerandomstuff"
        )).withCharset(StandardCharsets.UTF_8)));
    }

    @Test
    public void shouldSerialiseToStringWithAdditionParameters() {
        assertThat(new MediaType("application", "soap+xml", ImmutableMap.of(
            "action", "somerandomstuff"
        )).withCharset(StandardCharsets.UTF_8).toString(), is("application/soap+xml; action=somerandomstuff; charset=utf-8"));
    }

    @Test
    public void shouldSerialiseToStringWithAdditionParametersAndNoCharset() {
        assertThat(new MediaType("application", "soap+xml", ImmutableMap.of(
            "action", "somerandomstuff"
        )).toString(), is("application/soap+xml; action=somerandomstuff"));
    }

}
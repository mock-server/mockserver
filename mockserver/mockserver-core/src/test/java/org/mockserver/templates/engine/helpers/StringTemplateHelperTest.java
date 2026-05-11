package org.mockserver.templates.engine.helpers;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class StringTemplateHelperTest {

    private final StringTemplateHelper helper = new StringTemplateHelper();

    @Test
    public void shouldTrim() {
        assertThat(helper.trim("  hello  "), is("hello"));
        assertThat(helper.trim(null), is(""));
    }

    @Test
    public void shouldCapitalize() {
        assertThat(helper.capitalize("hello"), is("Hello"));
        assertThat(helper.capitalize(""), is(""));
        assertThat(helper.capitalize(null), is(""));
    }

    @Test
    public void shouldUppercase() {
        assertThat(helper.uppercase("hello"), is("HELLO"));
        assertThat(helper.uppercase(null), is(""));
    }

    @Test
    public void shouldLowercase() {
        assertThat(helper.lowercase("HELLO"), is("hello"));
        assertThat(helper.lowercase(null), is(""));
    }

    @Test
    public void shouldUrlEncode() {
        assertThat(helper.urlEncode("hello world"), is("hello+world"));
        assertThat(helper.urlEncode("a=b&c=d"), is("a%3Db%26c%3Dd"));
        assertThat(helper.urlEncode(null), is(""));
    }

    @Test
    public void shouldUrlDecode() {
        assertThat(helper.urlDecode("hello+world"), is("hello world"));
        assertThat(helper.urlDecode("a%3Db%26c%3Dd"), is("a=b&c=d"));
        assertThat(helper.urlDecode(null), is(""));
    }

    @Test
    public void shouldBase64Encode() {
        assertThat(helper.base64Encode("hello"), is("aGVsbG8="));
        assertThat(helper.base64Encode(null), is(""));
    }

    @Test
    public void shouldBase64Decode() {
        assertThat(helper.base64Decode("aGVsbG8="), is("hello"));
        assertThat(helper.base64Decode(null), is(""));
    }

    @Test
    public void shouldSubstringBefore() {
        assertThat(helper.substringBefore("hello-world", "-"), is("hello"));
        assertThat(helper.substringBefore("hello", "-"), is("hello"));
        assertThat(helper.substringBefore(null, "-"), is(""));
    }

    @Test
    public void shouldSubstringAfter() {
        assertThat(helper.substringAfter("hello-world", "-"), is("world"));
        assertThat(helper.substringAfter("hello", "-"), is(""));
        assertThat(helper.substringAfter(null, "-"), is(""));
    }

    @Test
    public void shouldReturnLength() {
        assertThat(helper.length("hello"), is(5));
        assertThat(helper.length(""), is(0));
        assertThat(helper.length(null), is(0));
    }

    @Test
    public void shouldContains() {
        assertThat(helper.contains("hello world", "world"), is(true));
        assertThat(helper.contains("hello", "world"), is(false));
        assertThat(helper.contains(null, "world"), is(false));
    }

    @Test
    public void shouldReplace() {
        assertThat(helper.replace("hello world", "world", "there"), is("hello there"));
        assertThat(helper.replace(null, "a", "b"), is(""));
        assertThat(helper.replace("hello", null, "b"), is("hello"));
    }

    @Test
    public void shouldBeRegisteredInTemplateFunctions() {
        Object stringsHelper = org.mockserver.templates.engine.TemplateFunctions.BUILT_IN_HELPERS.get("strings");
        assertThat(stringsHelper, is(notNullValue()));
        assertThat(stringsHelper, instanceOf(StringTemplateHelper.class));
    }
}

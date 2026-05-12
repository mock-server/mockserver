package org.mockserver.templates.engine.helpers;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class JsonTemplateHelperTest {

    private final JsonTemplateHelper helper = new JsonTemplateHelper();

    @Test
    public void shouldMergeJsonObjects() {
        String result = helper.merge("{\"a\":1}", "{\"b\":2}");
        assertThat(result, containsString("\"a\":1"));
        assertThat(result, containsString("\"b\":2"));
    }

    @Test
    public void shouldMergeOverwritingFields() {
        String result = helper.merge("{\"a\":1}", "{\"a\":2}");
        assertThat(result, is("{\"a\":2}"));
    }

    @Test
    public void shouldReturnFirstJsonWhenNotObjects() {
        String result = helper.merge("[1,2]", "[3,4]");
        assertThat(result, is("[1,2]"));
    }

    @Test
    public void shouldSortArrayByField() {
        String result = helper.sort("[{\"name\":\"charlie\"},{\"name\":\"alice\"},{\"name\":\"bob\"}]", "name");
        assertThat(result, is("[{\"name\":\"alice\"},{\"name\":\"bob\"},{\"name\":\"charlie\"}]"));
    }

    @Test
    public void shouldReturnOriginalWhenSortingNonArray() {
        String result = helper.sort("{\"a\":1}", "a");
        assertThat(result, is("{\"a\":1}"));
    }

    @Test
    public void shouldAddElementToArray() {
        String result = helper.arrayAdd("[1,2]", "3");
        assertThat(result, is("[1,2,3]"));
    }

    @Test
    public void shouldAddObjectToArray() {
        String result = helper.arrayAdd("[{\"a\":1}]", "{\"b\":2}");
        assertThat(result, containsString("\"b\":2"));
    }

    @Test
    public void shouldRemoveField() {
        String result = helper.remove("{\"a\":1,\"b\":2}", "a");
        assertThat(result, is("{\"b\":2}"));
    }

    @Test
    public void shouldReturnOriginalWhenRemovingFromNonObject() {
        String result = helper.remove("[1,2]", "a");
        assertThat(result, is("[1,2]"));
    }

    @Test
    public void shouldPrettyPrint() {
        String result = helper.prettyPrint("{\"a\":1}");
        assertThat(result, containsString("\"a\" : 1"));
    }

    @Test
    public void shouldExtractField() {
        assertThat(helper.field("{\"name\":\"test\"}", "name"), is("test"));
        assertThat(helper.field("{\"count\":5}", "count"), is("5"));
        assertThat(helper.field("{\"a\":1}", "missing"), is(""));
    }

    @Test
    public void shouldReturnArraySize() {
        assertThat(helper.size("[1,2,3]"), is(3));
        assertThat(helper.size("[]"), is(0));
        assertThat(helper.size("{\"a\":1}"), is(0));
    }

    @Test
    public void shouldBeRegisteredInTemplateFunctions() {
        Object jsonHelper = org.mockserver.templates.engine.TemplateFunctions.BUILT_IN_HELPERS.get("jsonTransform");
        assertThat(jsonHelper, is(notNullValue()));
        assertThat(jsonHelper, instanceOf(JsonTemplateHelper.class));
    }
}

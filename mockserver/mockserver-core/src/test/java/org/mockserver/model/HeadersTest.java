package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HeadersTest {
    @Test
    public void shouldBuildHeader() {
        // given
        Headers headers = new Headers();

        // when
        Header header = headers.build(string("name"), Arrays.asList(
            string("value_one"),
            string("value_two")
        ));

        // then
        assertThat(header, is(new Header(string("name"), string("value_one"), string("value_two"))));
    }

    @Test
    public void shouldAddEntriesAsHeaderVarargs() {
        // given
        Headers headers = new Headers();

        // when
        headers.withEntries(
            header("name_one", "value_one_one", "value_one_two"),
            header("name_two", "value_two_one", "value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        );

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header("name_one", "value_one_one", "value_one_two"),
            header("name_two", "value_two_one", "value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));
    }

    @Test
    public void shouldAddEntriesAsHeaderList() {
        // given
        Headers headers = new Headers();

        // when
        headers.withEntries(Arrays.asList(
            header("name_one", "value_one_one", "value_one_two"),
            header("name_two", "value_two_one", "value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header("name_one", "value_one_one", "value_one_two"),
            header("name_two", "value_two_one", "value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));
    }

    @Test
    public void shouldAddEntriesAsMap() {
        // given
        Headers headers = new Headers();
        Map<String, List<String>> entries = new LinkedHashMap<>();
        entries.put("name_one", Arrays.asList("value_one_one", "value_one_two"));
        entries.put("name_two", Arrays.asList("value_two_one", "value_two_two"));
        entries.put("name_three", Arrays.asList("value_three_one", "value_three_two"));

        // when
        headers.withEntries(entries);

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header("name_one", "value_one_one", "value_one_two"),
            header("name_two", "value_two_one", "value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));
    }

    @Test
    public void shouldAddEntryAsCookie() {
        // given
        Headers headers = new Headers();

        // when
        headers.withEntry(header("name_one", "value_one_one", "value_one_two"));
        headers.withEntry(header("name_two", "value_two_one", "value_two_two"));
        headers.withEntry(header("name_three", "value_three_one", "value_three_two"));

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header("name_one", "value_one_one", "value_one_two"),
            header("name_two", "value_two_one", "value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));
    }

    @Test
    public void shouldAddEntryAsNameAndValueString() {
        // given
        Headers headers = new Headers();

        // when
        headers.withEntry("name_one", "value_one_one", "value_one_two");
        headers.withEntry("name_two", "value_two_one", "value_two_two");
        headers.withEntry("name_three", "value_three_one", "value_three_two");

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header("name_one", "value_one_one", "value_one_two"),
            header("name_two", "value_two_one", "value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));
    }

    @Test
    public void shouldAddEntryAsNameAndValueNottableString() {
        // given
        Headers headers = new Headers();

        // when
        headers.withEntry(string("name_one"), not("value_one_one"), not("value_one_two"));
        headers.withEntry(not("name_two"), string("value_two_one"), string("value_two_two"));
        headers.withEntry(string("name_three"), string("value_three_one"), string("value_three_two"));

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header(string("name_one"), not("value_one_one"), not("value_one_two")),
            header(not("name_two"), string("value_two_one"), string("value_two_two")),
            header(string("name_three"), string("value_three_one"), string("value_three_two"))
        ));
    }

    @Test
    public void shouldReplaceEntryWithHeader() {
        // given
        Headers headers = new Headers();
        headers.withEntry(header("name_one", "value_one_one", "value_one_two"));
        headers.withEntry(header("name_two", "value_two_one", "value_two_two"));
        headers.withEntry(header("name_three", "value_three_one", "value_three_two"));

        // when
        headers.replaceEntry(header("name_two", "new_value_two_one", "new_value_two_two"));

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header("name_one", "value_one_one", "value_one_two"),
            header("name_two", "new_value_two_one", "new_value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));
    }

    @Test
    public void shouldReplaceEntryWithStrings() {
        // given
        Headers headers = new Headers();
        headers.withEntry(header("name_one", "value_one_one", "value_one_two"));
        headers.withEntry(header("name_two", "value_two_one", "value_two_two"));
        headers.withEntry(header("name_three", "value_three_one", "value_three_two"));

        // when
        headers.replaceEntry("name_two", "new_value_two_one", "new_value_two_two");

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header("name_one", "value_one_one", "value_one_two"),
            header("name_two", "new_value_two_one", "new_value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));
    }

    @Test
    public void shouldReplaceEntryWithHeaderIgnoringCase() {
        // given
        Headers headers = new Headers();
        headers.withEntry(header("name_one", "value_one_one", "value_one_two"));
        headers.withEntry(header("name_two", "value_two_one", "value_two_two"));
        headers.withEntry(header("name_three", "value_three_one", "value_three_two"));

        // when
        headers.replaceEntry(header("Name_Two", "new_value_two_one", "new_value_two_two"));

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header("name_one", "value_one_one", "value_one_two"),
            header("Name_Two", "new_value_two_one", "new_value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));
    }

    @Test
    public void shouldReplaceEntryWithStringsIgnoringCase() {
        // given
        Headers headers = new Headers();
        headers.withEntry(header("name_one", "value_one_one", "value_one_two"));
        headers.withEntry(header("name_two", "value_two_one", "value_two_two"));
        headers.withEntry(header("name_three", "value_three_one", "value_three_two"));

        // when
        headers.replaceEntry("Name_Two", "new_value_two_one", "new_value_two_two");

        // then
        assertThat(headers.getEntries().size(), is(3));
        assertThat(headers.getEntries(), hasItems(
            header("name_one", "value_one_one", "value_one_two"),
            header("Name_Two", "new_value_two_one", "new_value_two_two"),
            header("name_three", "value_three_one", "value_three_two")
        ));
    }

    @Test
    public void shouldRetrieveEntryValues() {
        // given
        Headers headers = new Headers();
        headers.withEntry(header("name_one", "value_one_one", "value_one_two"));
        headers.withEntry(header("name_two", "value_two_one", "value_two_two"));
        headers.withEntry(header("name_three", "value_three_one", "value_three_two"));

        // when
        List<String> values = headers.getValues("name_two");

        // then
        assertThat(values.size(), is(2));
        assertThat(values, hasItems(
            "value_two_one",
            "value_two_two"
        ));
    }

    @Test
    public void shouldGetFirstEntry() {
        // given
        Headers headers = new Headers();
        headers.withEntry(header("name_one", "value_one_one", "value_one_two"));
        headers.withEntry(header("name_two", "value_two_one", "value_two_two"));
        headers.withEntry(header("name_three", "value_three_one", "value_three_two"));

        // when
        String value = headers.getFirstValue("name_two");

        // then
        assertThat(value, is("value_two_one"));
    }

    @Test
    public void shouldContainEntryByKey() {
        // given
        Headers headers = new Headers();
        headers.withEntry(header("name_one", "value_one_one", "value_one_two"));
        headers.withEntry(header("name_two", "value_two_one", "value_two_two"));
        headers.withEntry(header("name_three", "value_three_one", "value_three_two"));

        // then
        assertTrue(headers.containsEntry("name_two"));
        assertFalse(headers.containsEntry("name_other"));
    }

    @Test
    public void shouldContainEntryByKeyAndValueString() {
        // given
        Headers headers = new Headers();
        headers.withEntry(header("name_one", "value_one_one", "value_one_two"));
        headers.withEntry(header("name_two", "value_two_one", "value_two_two"));
        headers.withEntry(header("name_three", "value_three_one", "value_three_two"));

        // then
        assertTrue(headers.containsEntry("name_two", "value_two_one"));
        assertTrue(headers.containsEntry("name_two", "value_two_two"));
        assertFalse(headers.containsEntry("name_other", "value_three_one"));
        assertFalse(headers.containsEntry("name_three", "value_three_other"));
    }

    @Test
    public void shouldContainEntryByKeyAndValueNottableString() {
        // given
        Headers headers = new Headers();
        headers.withEntry(string("name_one"), not("value_one_one"), not("value_one_two"));
        headers.withEntry(not("name_two"), string("value_two_one"), string("value_two_two"));
        headers.withEntry(string("name_three"), string("value_three_one"), string("value_three_two"));
        headers.withEntry(string("name_four"), string("value_four_one"));

        // then
        // exact match, not key, string first value
        assertTrue(headers.containsEntry(not("name_two"), string("value_two_one")));
        // exact match, not key, string second value
        assertTrue(headers.containsEntry(not("name_two"), string("value_two_two")));
        // exact match, string key, not first value
        assertTrue(headers.containsEntry(string("name_one"), not("value_one_one")));
        // exact match, string key, string second value
        assertTrue(headers.containsEntry(string("name_three"), string("value_three_two")));
        // not value
        assertTrue(headers.containsEntry(string("name_three"), not("value_other")));
        // not key
        assertTrue(headers.containsEntry(not("name_other"), string("value_three_one")));
        // matches (matched "name_one" -> "!value_one_one")
        assertTrue(headers.containsEntry(not("name_three"), string("value_three_one")));
        // matches ("!name_two" -> "value_two_one")
        assertTrue(headers.containsEntry(string("name_four"), not("value_four_one")));
        // non-match name
        assertFalse(headers.containsEntry(string("name_other"), string("value_three_one")));
        // non-match value
        assertFalse(headers.containsEntry(string("name_three"), string("value_three_other")));
    }

}

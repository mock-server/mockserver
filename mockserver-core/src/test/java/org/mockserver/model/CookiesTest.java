package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CookiesTest {
    @Test
    public void shouldBuildCookie() throws Exception {
        // given
        Cookies cookies = new Cookies();

        // when
        Cookie cookie = cookies.build(string("name"), string("value"));

        // then
        assertThat(cookie, is(new Cookie(string("name"), string("value"))));
    }

    @Test
    public void shouldAddEntriesAsCookieVarargs() throws Exception {
        // given
        Cookies cookies = new Cookies();

        // when
        cookies.withEntries(
                cookie("name_one", "value_one"),
                cookie("name_two", "value_two"),
                cookie("name_three", "value_three")
        );

        // then
        assertThat(cookies.getEntries().size(), is(3));
        assertThat(cookies.getEntries(), hasItems(
                cookie("name_one", "value_one"),
                cookie("name_two", "value_two"),
                cookie("name_three", "value_three")
        ));
    }

    @Test
    public void shouldAddEntriesAsCookieList() throws Exception {
        // given
        Cookies cookies = new Cookies();

        // when
        cookies.withEntries(Arrays.asList(
                cookie("name_one", "value_one"),
                cookie("name_two", "value_two"),
                cookie("name_three", "value_three")
        ));

        // then
        assertThat(cookies.getEntries().size(), is(3));
        assertThat(cookies.getEntries(), hasItems(
                cookie("name_one", "value_one"),
                cookie("name_two", "value_two"),
                cookie("name_three", "value_three")
        ));
    }

    @Test
    public void shouldAddEntryAsCookie() throws Exception {
        // given
        Cookies cookies = new Cookies();

        // when
        cookies.withEntry(cookie("name_one", "value_one"));
        cookies.withEntry(cookie("name_two", "value_two"));
        cookies.withEntry(cookie("name_three", "value_three"));

        // then
        assertThat(cookies.getEntries().size(), is(3));
        assertThat(cookies.getEntries(), hasItems(
                cookie("name_one", "value_one"),
                cookie("name_two", "value_two"),
                cookie("name_three", "value_three")
        ));
    }

    @Test
    public void shouldAddEntryAsNameAndValueString() throws Exception {
        // given
        Cookies cookies = new Cookies();

        // when
        cookies.withEntry("name_one", "value_one");
        cookies.withEntry("name_two", "value_two");
        cookies.withEntry("name_three", "value_three");

        // then
        assertThat(cookies.getEntries().size(), is(3));
        assertThat(cookies.getEntries(), hasItems(
                cookie("name_one", "value_one"),
                cookie("name_two", "value_two"),
                cookie("name_three", "value_three")
        ));
    }

    @Test
    public void shouldAddEntryAsNameAndValueNottableString() throws Exception {
        // given
        Cookies cookies = new Cookies();

        // when
        cookies.withEntry(string("name_one"), not("value_one"));
        cookies.withEntry(not("name_two"), string("value_two"));
        cookies.withEntry(string("name_three"), string("value_three"));

        // then
        assertThat(cookies.getEntries().size(), is(3));
        assertThat(cookies.getEntries(), hasItems(
                cookie(string("name_one"), not("value_one")),
                cookie(not("name_two"), string("value_two")),
                cookie(string("name_three"), string("value_three"))
        ));
    }

}
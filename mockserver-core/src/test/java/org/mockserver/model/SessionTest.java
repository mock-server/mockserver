package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.SessionEntry.sessionEntry;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;


public class SessionTest {
    @Test
    public void shouldBuildSessionEntry() {
        // given
        Session session = new Session();

        // when
        SessionEntry entry = session.build(string("key"), string("value"));

        // then
        assertThat(entry, is(new SessionEntry(string("key"), string("value"))));
    }

    @Test
    public void shouldAddEntriesAsSessionVarargs() {
        // given
        Session session = new Session();

        // when
        session.withEntries(
                sessionEntry("name_one", "value_one"),
                sessionEntry("name_two", "value_two"),
                sessionEntry("name_three", "value_three")
        );

        // then
        assertThat(session.getEntries().size(), is(3));
        assertThat(session.getEntries(), hasItems(
                sessionEntry("name_one", "value_one"),
                sessionEntry("name_two", "value_two"),
                sessionEntry("name_three", "value_three")
        ));
    }

    @Test
    public void shouldAddEntriesAsSessionEntryList() {
        // given
        Session session = new Session();

        // when
        session.withEntries(Arrays.asList(
                sessionEntry("name_one", "value_one"),
                sessionEntry("name_two", "value_two"),
                sessionEntry("name_three", "value_three")
        ));

        // then
        assertThat(session.getEntries().size(), is(3));
        assertThat(session.getEntries(), hasItems(
                sessionEntry("name_one", "value_one"),
                sessionEntry("name_two", "value_two"),
                sessionEntry("name_three", "value_three")
        ));
    }

    @Test
    public void shouldAddEntryAsSessionEntry() {
        // given
        Session session = new Session();

        // when
        session.withEntry(sessionEntry("name_one", "value_one"));
        session.withEntry(sessionEntry("name_two", "value_two"));
        session.withEntry(sessionEntry("name_three", "value_three"));

        // then
        assertThat(session.getEntries().size(), is(3));
        assertThat(session.getEntries(), hasItems(
                sessionEntry("name_one", "value_one"),
                sessionEntry("name_two", "value_two"),
                sessionEntry("name_three", "value_three")
        ));
    }

    @Test
    public void shouldAddEntryAsNameAndValueString() {
        // given
        Session session = new Session();

        // when
        session.withEntry("name_one", "value_one");
        session.withEntry("name_two", "value_two");
        session.withEntry("name_three", "value_three");

        // then
        assertThat(session.getEntries().size(), is(3));
        assertThat(session.getEntries(), hasItems(
                sessionEntry("name_one", "value_one"),
                sessionEntry("name_two", "value_two"),
                sessionEntry("name_three", "value_three")
        ));
    }

    @Test
    public void shouldAddEntryAsNameAndValueNottableString() {
        // given
        Session session = new Session();

        // when
        session.withEntry(string("name_one"), not("value_one"));
        session.withEntry(not("name_two"), string("value_two"));
        session.withEntry(string("name_three"), string("value_three"));

        // then
        assertThat(session.getEntries().size(), is(3));
        assertThat(session.getEntries(), hasItems(
                sessionEntry(string("name_one"), not("value_one")),
                sessionEntry(not("name_two"), string("value_two")),
                sessionEntry(string("name_three"), string("value_three"))
        ));
    }

}
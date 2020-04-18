package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Session;
import org.mockserver.model.SessionEntry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.NottableString.not;


public class SessionMatcherTest {

    @Test
    public void shouldMatchSingleSessionMatcherAndSingleMatchingEntry() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1","value1")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1","value1")
            )
        ));
    }

    @Test
    public void shouldNotMatchSingleSessionMatcherAndSingleNoneMatchingEntry() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1","value1")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("notKey1","value1")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1","value1")
        ), false).matches(
                null,
                new Session().withEntries(
                    new SessionEntry("key1","notValue1")
                )
        ));
    }

    @Test
    public void shouldMatchMultipleSessionMatcherAndMultipleMatchingEntries() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1"),
                new SessionEntry("key2", "value2")
            )
        ));
    }

    @Test
    public void shouldMatchRegexSessionMatcher() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key.*", "value.*")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1"),
                new SessionEntry("key2", "value2")
            )
        ));
    }

    @Test
    public void shouldNotMatchMatchingRegexControlPlaneSession() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key.*", "value.*")
            )
        ));
    }

    @Test
    public void shouldNotMatchMultipleSesisonMatcherAndMultipleNoneMatchingEntriesWithOneMismatch() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("notKey1", "value1"),
                new SessionEntry("key2", "value2")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "notValue1"),
                new SessionEntry("key2", "value2")
            )
        ));
    }

    @Test
    public void shouldNotMatchMultipleSessionMatcherAndMultipleNoneMatchingEntriesWithMultipleMismatches() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("notKey1", "value1"),
                new SessionEntry("notKey2", "value2")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "notValue1"),
                new SessionEntry("key2", "notValue2")
            )
        ));


        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key.*", "value.*")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("notKey1", "value1"),
                new SessionEntry("notKey2", "value2")
            )
        ));


        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key.*", "value.*")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "notValue1"),
                new SessionEntry("key2", "notValue2")
            )
        ));

    }

    @Test
    public void shouldNotMatchMultipleSessionMatcherAndMultipleNotEnoughMatchingEntries() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key2", "value2")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingSession() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1"),
                new SessionEntry("key2", "value2")
            )
        ));

        assertTrue(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key.*", "value.*")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1"),
                new SessionEntry("key2", "value2")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingSessionWithOnlySessionForEmptyList() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Session(), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1")
            )
        ));

        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1")
        ), false).matches(
            null,
            new Session()
        ));

        assertTrue(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry(not("key1"), not("value1"))
        ), false).matches(
            null,
            new Session()
        ));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Session(), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1"),
                new SessionEntry("key2", "value2")
            )
        ));
    }

    @Test
    public void shouldNotMatchNullEntryValue() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1"),
                new SessionEntry("key2", null)
            )
        ));
    }
    @Test
    public void shouldMatchEmptyEntryValueInExpectation() {
        assertTrue(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1"),
                new SessionEntry("key2", "value2")
            )
        ));
    }

    @Test
    public void shouldNotMatchMissingEntry() {
        assertFalse(new HashMapMatcher(new MockServerLogger(), new Session().withEntries(
            new SessionEntry("key1", "value1"),
            new SessionEntry("key2", "value2")
        ), false).matches(
            null,
            new Session().withEntries(
                new SessionEntry("key1", "value1")
            )
        ));
    }

}

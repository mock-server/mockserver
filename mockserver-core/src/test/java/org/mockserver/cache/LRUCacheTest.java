package org.mockserver.cache;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class LRUCacheTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger(LRUCacheTest.class);

    @Test
    public void shouldReturnCachedObjects() {
        // given
        LRUCache<String, Object> lruCache = new LRUCache<>(mockServerLogger, 5, MINUTES.toMillis(10));

        // when
        lruCache.put("one", "a");
        lruCache.put("two", "b");
        lruCache.put("one", "c");

        // then
        assertThat(lruCache.get("one"), is("c"));
        assertThat(lruCache.get("two"), is("b"));
    }

    @Test
    public void shouldNotCacheIfGloballyDisabled() {
        try {
            // given
            LRUCache.allCachesEnabled(false);
            LRUCache<String, Object> lruCache = new LRUCache<>(mockServerLogger, 5, MINUTES.toMillis(10));

            // when
            lruCache.put("one", "a");
            lruCache.put("two", "b");

            // then
            assertThat(lruCache.get("one"), nullValue());
            assertThat(lruCache.get("two"), nullValue());
        } finally {
            LRUCache.allCachesEnabled(true);
        }
    }

    @Test
    public void shouldExpireItems() throws InterruptedException {
        // given
        LRUCache<String, Object> lruCache = new LRUCache<>(mockServerLogger, 5, SECONDS.toMillis(1));

        // when
        lruCache.put("one", "a");
        lruCache.put("two", "b");

        // then
        assertThat(lruCache.get("one"), is("a"));
        assertThat(lruCache.get("two"), is("b"));

        // when
        SECONDS.sleep(2L);

        // then
        assertThat(lruCache.get("one"), nullValue());
        assertThat(lruCache.get("two"), nullValue());
    }

    @Test
    public void shouldExtendExpiryOnGet() throws InterruptedException {
        // given
        LRUCache<String, Object> lruCache = new LRUCache<>(mockServerLogger, 5, SECONDS.toMillis(5));

        // when
        lruCache.put("one", "a");
        lruCache.put("two", "b");

        // then
        assertThat(lruCache.get("one"), is("a"));
        assertThat(lruCache.get("two"), is("b"));

        // when
        SECONDS.sleep(3L);

        // then
        assertThat(lruCache.get("one"), is("a"));
        assertThat(lruCache.get("two"), is("b"));

        // when
        SECONDS.sleep(3L);

        // then
        assertThat(lruCache.get("one"), is("a"));
        assertThat(lruCache.get("two"), is("b"));
    }

    @Test
    public void shouldLimitCacheGlobally() {
        try {
            // given
            LRUCache.setMaxSizeOverride(3);
            LRUCache<String, Object> lruCache = new LRUCache<>(mockServerLogger, 5, SECONDS.toMillis(3));

            // when
            lruCache.put("one", "a");
            lruCache.put("two", "b");
            lruCache.put("three", "c");
            lruCache.put("four", "d");

            // then
            assertThat(lruCache.get("four"), is("d"));
            assertThat(lruCache.get("two"), is("b"));
            assertThat(lruCache.get("one"), is(nullValue()));
        } finally {
            LRUCache.setMaxSizeOverride(0);
        }
    }

    @Test
    public void shouldLimitCacheLocally() {
        // given
        LRUCache<String, Object> lruCache = new LRUCache<>(mockServerLogger, 4, SECONDS.toMillis(3));

        // when
        lruCache.put("one", "a");
        lruCache.put("two", "b");
        lruCache.put("three", "c");
        lruCache.put("four", "d");
        lruCache.put("five", "e");

        // then
        assertThat(lruCache.get("five"), is("e"));
        assertThat(lruCache.get("two"), is("b"));
        assertThat(lruCache.get("one"), is(nullValue()));
    }

    @Test
    public void shouldClearGlobally() {
        // given
        LRUCache<String, Object> lruCacheOne = new LRUCache<>(mockServerLogger, 5, MINUTES.toMillis(10));
        LRUCache<String, Object> lruCacheTwo = new LRUCache<>(mockServerLogger, 5, MINUTES.toMillis(10));
        LRUCache<String, Object> lruCacheThree = new LRUCache<>(mockServerLogger, 5, MINUTES.toMillis(10));
        lruCacheOne.put("one", "a");
        lruCacheTwo.put("one", "a");
        lruCacheThree.put("one", "a");

        // then
        assertThat(lruCacheOne.get("one"), is("a"));
        assertThat(lruCacheTwo.get("one"), is("a"));
        assertThat(lruCacheThree.get("one"), is("a"));

        // when
        LRUCache.clearAllCaches();

        // then
        assertThat(lruCacheOne.get("one"), is(nullValue()));
        assertThat(lruCacheTwo.get("one"), is(nullValue()));
        assertThat(lruCacheThree.get("one"), is(nullValue()));
    }

}
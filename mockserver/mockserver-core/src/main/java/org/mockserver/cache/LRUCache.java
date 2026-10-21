package org.mockserver.cache;

import com.google.common.annotations.VisibleForTesting;
import org.mockserver.logging.MockServerLogger;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@SuppressWarnings("unused")
public class LRUCache<K, V> {

    private static boolean allCachesEnabled = true;
    private static int maxSizeOverride = 0;
    private static final Set<LRUCache<?, ?>> allCaches = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final long ttlInMillis;
    private final int maxSize;
    private final ConcurrentHashMap<K, Entry<V>> map;
    private final ConcurrentLinkedQueue<K> queue;
    private final MockServerLogger mockServerLogger;

    public LRUCache(final MockServerLogger mockServerLogger, final int maxSize, long ttlInMillis) {
        this.mockServerLogger = mockServerLogger;
        this.maxSize = maxSize;
        this.map = new ConcurrentHashMap<>(maxSize);
        this.queue = new ConcurrentLinkedQueue<>();
        this.ttlInMillis = ttlInMillis;
        LRUCache.allCaches.add(this);
    }

    public static void allCachesEnabled(boolean enabled) {
        allCachesEnabled = enabled;
    }

    @VisibleForTesting
    public static void clearAllCaches() {
        // using synchronized foreach instead of a for-loop
        allCaches.forEach(cache -> {
            if (cache != null) {
                cache.clear();
            }
        });
    }

    public void put(K key, final V value) {
        put(key, value, ttlInMillis);
    }

    public void put(K key, final V value, long ttl) {
        if (allCachesEnabled && key != null) {
            if (map.containsKey(key)) {
                // ensure the queue is in FIFO order
                queue.remove(key);
            }
            while (queue.size() >= maxSize || maxSizeOverride > 0 && queue.size() >= maxSizeOverride) {
                K oldestKey = queue.poll();
                if (null != oldestKey) {
                    map.remove(oldestKey);
                }
            }
            queue.add(key);
            map.put(key, new Entry<>(ttl, expiryInMillis(ttl), value));
        }
    }

    private long expiryInMillis(long ttl) {
        return System.currentTimeMillis() + ttl;
    }

    /**
     * Atomically returns the value for {@code key}, computing and inserting it with {@code mappingFunction}
     * if absent. Unlike a {@code get}-then-{@code put} sequence this is race-free: concurrent callers for
     * the same absent key do not each build their own value and then clobber the cache — the mapping
     * function runs at most once and a single value instance is shared by and returned to all of them
     * (so there are no orphaned instances). Used where the cached value's identity, not just its content,
     * must be stable across threads.
     */
    public V getOrCompute(K key, java.util.function.Function<? super K, ? extends V> mappingFunction) {
        if (!allCachesEnabled || key == null) {
            // caching disabled or no key: compute without caching, preserving call-through behaviour
            return mappingFunction.apply(key);
        }
        V existing = get(key);
        if (existing != null) {
            return existing;
        }
        // evict BEFORE inserting (mirroring put()), so the key we are about to add can never be the one
        // polled here — this keeps the queue and the map in lock-step (no drift where a still-mapped key
        // is dropped from the queue and so escapes future LRU eviction)
        while (queue.size() >= maxSize || maxSizeOverride > 0 && queue.size() >= maxSizeOverride) {
            K oldestKey = queue.poll();
            if (null != oldestKey) {
                map.remove(oldestKey);
            }
        }
        // ConcurrentHashMap.computeIfAbsent runs the mapping function at most once for an absent key,
        // so racing callers share the one computed value rather than each building and clobbering one
        Entry<V> entry = map.computeIfAbsent(key, k -> {
            queue.add(k);
            return new Entry<>(ttlInMillis, expiryInMillis(ttlInMillis), mappingFunction.apply(k));
        });
        return entry.getValue();
    }

    public V get(K key) {
        if (allCachesEnabled && key != null) {
            if (map.containsKey(key)) {
                // remove from the queue and add it again in FIFO queue
                queue.remove(key);
                queue.add(key);
            }

            Entry<V> entry = map.get(key);
            if (entry != null) {
                if (entry.getExpiryInMillis() > System.currentTimeMillis()) {
                    return entry.updateExpiryInMillis(expiryInMillis(entry.getTtlInMillis())).getValue();
                } else {
                    delete(key);
                }
            }
        }
        return null;
    }

    public void delete(K key) {
        if (allCachesEnabled && key != null) {
            if (map.containsKey(key)) {
                map.remove(key);
                queue.remove(key);
            }
        }
    }

    public void deleteIf(java.util.function.Predicate<K> matches) {
        for (K key : map.keySet()) {
            if (matches.test(key)) {
                delete(key);
            }
        }
    }

    private void clear() {
        map.clear();
        queue.clear();
    }

    public static void setMaxSizeOverride(int maxSizeOverride) {
        LRUCache.maxSizeOverride = maxSizeOverride;
    }

}

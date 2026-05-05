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

    private void clear() {
        map.clear();
        queue.clear();
    }

    public static void setMaxSizeOverride(int maxSizeOverride) {
        LRUCache.maxSizeOverride = maxSizeOverride;
    }

}

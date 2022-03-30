package org.mockserver.templates.engine.mustache;

import com.samskivert.mustache.DefaultCollector;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.util.Map;

public class ExtendedCollector extends DefaultCollector {

    @Override
    public Mustache.VariableFetcher createFetcher(Object ctx, String name) {
        if (ctx instanceof Map<?, ?>) {
            return EXTENDED_MAP_FETCHER;
        }
        return super.createFetcher(ctx, name);
    }

    protected static final Mustache.VariableFetcher EXTENDED_MAP_FETCHER = new Mustache.VariableFetcher() {
        public Object get(Object ctx, String name) {
            if (ctx instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) ctx;
                if (map.containsKey(name)) {
                    return map.get(name);
                }
                // map entries, values and keys to be iterated over
                if ("entrySet".equals(name)) {
                    return map.entrySet();
                }
                if ("values".equals(name)) {
                    return map.values();
                }
                if ("keySet".equals(name)) {
                    return map.keySet();
                }
            }
            return Template.NO_FETCHER_FOUND;
        }

        @Override
        public String toString() {
            return "EXTENDED_MAP_FETCHER";
        }
    };

}

package org.mockserver.templates.engine.mustache;

import com.samskivert.mustache.Mustache;
import org.mockserver.templates.engine.helpers.ListTemplateHelper;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The {@code lists} helper as Mustache sees it. jmustache cannot pass arguments to a method, so
 * {@code pickRandom} and {@code arrayJoin} are sections whose body lists the values separated by {@code |}
 * ({@code {{#lists.pickRandom}}red|green|blue{{/lists.pickRandom}}}), and {@code range} takes its bounds as
 * the next two parts of the name ({@code {{#lists.range.1.5}}{{.}}{{/lists.range.1.5}}}).
 */
public class MustacheListTemplateHelper extends ListTemplateHelper {

    private final Mustache.Lambda pickRandom = (fragment, out) -> out.write(String.valueOf(pickRandom(values(fragment.execute()).toArray())));
    private final Mustache.Lambda arrayJoin = (fragment, out) -> {
        String body = fragment.execute();
        int separatorEnd = body.indexOf('|');
        if (separatorEnd < 0) {
            throw new IllegalArgumentException("lists.arrayJoin: write the separator first, then the values, all separated by |, such as {{#lists.arrayJoin}}, |red|green{{/lists.arrayJoin}}");
        }
        out.write(arrayJoin(body.substring(0, separatorEnd), values(body.substring(separatorEnd + 1)).toArray()));
    };
    private static final Map<String, Object> RANGE = new IntegerKeyedMap<>("from", from -> new IntegerKeyedMap<>("to", to -> range(from, to)));

    public MustacheListTemplateHelper() {
        super();
    }

    public MustacheListTemplateHelper(Random random) {
        super(random);
    }

    public Mustache.Lambda pickRandom() {
        return pickRandom;
    }

    public Mustache.Lambda arrayJoin() {
        return arrayJoin;
    }

    public Map<String, Object> range() {
        return RANGE;
    }

    /**
     * The values separated by {@code |}, each trimmed; empty values are left out, so a list rendered as
     * {@code {{#items}}{{.}}|{{/items}}} leaves no empty value at the end.
     */
    private static List<String> values(String body) {
        List<String> values = new ArrayList<>();
        int start = 0;
        while (start <= body.length()) {
            int end = body.indexOf('|', start);
            if (end < 0) {
                end = body.length();
            }
            String value = body.substring(start, end).trim();
            if (!value.isEmpty()) {
                values.add(value);
                if (values.size() > MAX_SIZE) {
                    throw new IllegalArgumentException("lists: a list has at most " + MAX_SIZE + " values");
                }
            }
            start = end + 1;
        }
        return values;
    }

    /**
     * A map that reads each key as a whole number, so jmustache can take a number from a name such as
     * {@code lists.range.1.5}. It holds nothing and cannot be listed.
     */
    private static final class IntegerKeyedMap<V> extends AbstractMap<String, Object> {
        private final String bound;
        private final java.util.function.IntFunction<V> value;

        private IntegerKeyedMap(String bound, java.util.function.IntFunction<V> value) {
            this.bound = bound;
            this.value = value;
        }

        @Override
        public boolean containsKey(Object key) {
            return key instanceof String;
        }

        @Override
        public Object get(Object key) {
            try {
                return value.apply(Integer.parseInt(String.valueOf(key).trim()));
            } catch (NumberFormatException notANumber) {
                throw new IllegalArgumentException("lists.range: " + bound + " must be a whole number, but was \"" + key + "\"; write the range as lists.range.1.5");
            }
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return Collections.emptySet();
        }
    }
}

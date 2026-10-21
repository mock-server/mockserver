package org.mockserver.templates.engine.helpers;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The {@code lists} helper: WireMock's {@code pickRandom}, {@code array}, {@code arrayJoin} and {@code range}.
 * It holds no per-render state. A list this helper builds has at most {@link #MAX_SIZE} values, so a number
 * taken from a request cannot make a template allocate without limit.
 */
public class ListTemplateHelper {

    public static final int MAX_SIZE = 10_000;
    static final int MIN_JOIN_LIMIT = 64 * 1024;

    // null picks with ThreadLocalRandom; set when templateFakerSeed is configured, so picks repeat across runs
    private final Random random;

    public ListTemplateHelper() {
        this(null);
    }

    public ListTemplateHelper(Random random) {
        this.random = random;
    }

    /**
     * One of the values, chosen at random. A single list or array argument is picked from, so
     * {@code pickRandom(list)} and {@code pickRandom('a', 'b')} both work.
     */
    public Object pickRandom(Object... values) {
        List<?> items = items("pickRandom", values);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("lists.pickRandom: there is nothing to pick from; pass at least one value");
        }
        return items.get(nextInt(items.size()));
    }

    /**
     * The values as a list. A single list or array argument is copied.
     */
    public List<Object> array(Object... values) {
        return new ArrayList<>(items("array", values));
    }

    /**
     * The values joined by {@code separator}. A single list or array argument has its elements joined. The result
     * is at most {@value #MIN_JOIN_LIMIT} characters or four times the length of the values, whichever is more, so a
     * long separator taken from a request cannot multiply the output.
     */
    public String arrayJoin(Object separator, Object... values) {
        String joiner = separator == null ? "" : String.valueOf(separator);
        List<?> items = items("arrayJoin", values);
        String[] texts = new String[items.size()];
        long valuesLength = 0;
        for (int i = 0; i < texts.length; i++) {
            texts[i] = String.valueOf(items.get(i));
            valuesLength += texts[i].length();
        }
        long joinedLength = valuesLength + (long) joiner.length() * Math.max(0, texts.length - 1);
        long limit = Math.max(MIN_JOIN_LIMIT, 4 * valuesLength);
        if (joinedLength > limit) {
            throw new IllegalArgumentException("lists.arrayJoin: the joined text would be " + joinedLength + " characters, over the limit of " + limit);
        }
        return String.join(joiner, texts);
    }

    /**
     * The whole numbers from {@code from} to {@code to}, both included, counting down when {@code to} is smaller.
     * Each bound can be a number or text holding one. The list is not stored, so its size costs nothing until read.
     */
    public List<Integer> range(Object from, Object to) {
        return range(integer("from", from), integer("to", to));
    }

    protected static List<Integer> range(int from, int to) {
        long size = Math.abs((long) to - from) + 1;
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("lists.range: a range has at most " + MAX_SIZE + " values, but " + from + " to " + to + " has " + size);
        }
        int step = from <= to ? 1 : -1;
        int length = (int) size;
        return new AbstractList<>() {
            @Override
            public Integer get(int index) {
                if (index < 0 || index >= length) {
                    throw new IndexOutOfBoundsException("index " + index + " is outside a range of " + length + " values");
                }
                return from + index * step;
            }

            @Override
            public int size() {
                return length;
            }
        };
    }

    protected int nextInt(int bound) {
        return random != null ? random.nextInt(bound) : ThreadLocalRandom.current().nextInt(bound);
    }

    /**
     * The values, or the elements of the single list, array or map (its values) passed instead.
     */
    private static List<?> items(String helper, Object[] values) {
        List<?> items;
        if (values == null) {
            items = Collections.emptyList();
        } else if (values.length == 1 && values[0] instanceof List<?> list) {
            items = list;
        } else if (values.length == 1 && values[0] instanceof Collection<?> collection) {
            checkSize(helper, collection.size());
            items = new ArrayList<>(collection);
        } else if (values.length == 1 && values[0] instanceof Map<?, ?> map) {
            checkSize(helper, map.size());
            items = new ArrayList<>(map.values());
        } else if (values.length == 1 && values[0] != null && values[0].getClass().isArray()) {
            Object array = values[0];
            int length = Array.getLength(array);
            checkSize(helper, length);
            List<Object> elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                elements.add(Array.get(array, i));
            }
            items = elements;
        } else {
            items = Arrays.asList(values);
        }
        checkSize(helper, items.size());
        return items;
    }

    private static void checkSize(String helper, int size) {
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("lists." + helper + ": a list has at most " + MAX_SIZE + " values, but this one has " + size);
        }
    }

    private static int integer(String name, Object value) {
        if (value instanceof Number number) {
            double asDouble = number.doubleValue();
            if (asDouble != Math.rint(asDouble) || asDouble > Integer.MAX_VALUE || asDouble < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("lists.range: " + name + " must be a whole number, but was " + value);
            }
            return number.intValue();
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("lists.range: " + name + " must be a whole number, but was \"" + text + "\"");
        }
    }

    @Override
    public String toString() {
        return "ListTemplateHelper";
    }
}

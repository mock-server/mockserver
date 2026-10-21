package org.mockserver.templates.engine.helpers;

import net.datafaker.Faker;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.event.Level;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * The {@code faker} helper: a Datafaker {@link Faker} that can also give a faker for another locale, with
 * {@code faker.locale('de-DE')} in Velocity and JavaScript or {@code {{faker.locale.de-DE.name.firstName}}}
 * in Mustache.
 * <p>
 * Datafaker keeps every {@link Locale} it is given in static maps that never shrink, so a locale taken from a
 * request must never reach it. A requested tag is reduced to its language and region by string parsing alone,
 * then matched against {@link #SUPPORTED_LOCALES}, the fixed set of locales Datafaker ships data for. Only those
 * locales are ever handed to Datafaker; any other tag gives the default (English) faker and a warning.
 */
public class TemplateFaker extends Faker {

    private static final MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(TemplateFaker.class);
    private static final int MAX_SHOWN_TAG_LENGTH = 35;
    static final int MAX_WARNED_TAGS = 64;
    static final Set<String> WARNED_TAGS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // the locales datafaker 2.x ships a <tag>.yml for; filtered once against the classpath in case the version changes
    private static final String[] SHIPPED_LOCALES = {
        "ar", "be", "bg", "by", "ca", "ca-CAT", "cs", "cs-CZ", "da-DK", "de", "de-AT", "de-CH", "el-GR", "en", "en-AU",
        "en-BORK", "en-CA", "en-GB", "en-IE", "en-IN", "en-MD", "en-MS", "en-NEP", "en-NG", "en-NZ", "en-PAK", "en-PH",
        "en-PK", "en-SG", "en-UG", "en-US", "en-ZA", "es", "es-AR", "es-MX", "es-PY", "et", "fa", "fi-FI", "fr", "fr-CA",
        "fr-CH", "he", "hr", "hu", "hy", "id", "id-ID", "it", "ja", "ka", "ko", "lv", "mk", "nb-NO", "nl", "nl-BE",
        "no-NO", "pl", "pt", "pt-BR", "ro-MD", "ru", "ru-MD", "sk", "sq", "sv", "sv-SE", "ta", "ta-IN", "th", "tr", "uk",
        "uz", "vi", "zh-CN", "zh-TW"
    };
    static final Set<String> SUPPORTED_LOCALES = supportedLocales();

    // null when unseeded; otherwise each locale faker gets its own Random with this seed, so it is reproducible
    private final Long seed;
    private final TemplateFaker root;
    // keyed only by SUPPORTED_LOCALES entries, so it can never hold more than that fixed set
    private final Map<String, TemplateFaker> localeFakers;
    private final Map<String, Object> localeLookup;

    public TemplateFaker() {
        super();
        this.seed = null;
        this.root = this;
        this.localeFakers = new LinkedHashMap<>();
        this.localeLookup = new LocaleLookup(this);
    }

    public TemplateFaker(long seed) {
        super(new Random(seed));
        this.seed = seed;
        this.root = this;
        this.localeFakers = new LinkedHashMap<>();
        this.localeLookup = new LocaleLookup(this);
    }

    private TemplateFaker(String supportedTag, TemplateFaker root) {
        super(toLocale(supportedTag), root.seed != null ? new Random(root.seed) : new Random());
        this.seed = root.seed;
        this.root = root;
        this.localeFakers = root.localeFakers;
        this.localeLookup = root.localeLookup;
    }

    /**
     * A faker that generates data for the locale, such as {@code de}, {@code de-DE}, {@code fr_FR} or {@code pt-BR}.
     * A region Datafaker has no data for falls back to the language ({@code de-BE} gives {@code de}); a language it
     * has no data for gives the default faker and logs a warning.
     */
    public TemplateFaker locale(String tag) {
        String supported = supportedTag(tag);
        if (supported == null) {
            warnUnsupported(tag);
            return root;
        }
        synchronized (localeFakers) {
            TemplateFaker cached = localeFakers.get(supported);
            if (cached == null) {
                cached = new TemplateFaker(supported, root);
                localeFakers.put(supported, cached);
            }
            return cached;
        }
    }

    /**
     * For Mustache, which cannot pass an argument: each key read from this map is a locale.
     */
    public Map<String, Object> locale() {
        return localeLookup;
    }

    /**
     * The supported tag for {@code tag}, by string parsing only: the language and the region (when Datafaker has
     * data for that pair), ignoring scripts, variants and extensions; null when Datafaker has no data for the language.
     */
    static String supportedTag(String tag) {
        if (tag == null) {
            return null;
        }
        String[] subtags = tag.trim().replace('_', '-').split("-", 3);
        String language = subtags[0].toLowerCase(Locale.ROOT);
        if (language.length() < 2 || language.length() > 3) {
            return null;
        }
        if (subtags.length > 1 && subtags[1].length() >= 2 && subtags[1].length() <= 4) {
            for (String supported : SUPPORTED_LOCALES) {
                if (supported.length() == language.length() + 1 + subtags[1].length()
                    && supported.startsWith(language + "-") && supported.regionMatches(true, language.length() + 1, subtags[1], 0, subtags[1].length())) {
                    return supported;
                }
            }
        }
        return SUPPORTED_LOCALES.contains(language) ? language : null;
    }

    // built from a SUPPORTED_LOCALES entry only; the Locale constructor keeps non-BCP-47 regions such as PAK
    @SuppressWarnings("deprecation")
    private static Locale toLocale(String supportedTag) {
        int dash = supportedTag.indexOf('-');
        return dash < 0 ? new Locale(supportedTag) : new Locale(supportedTag.substring(0, dash), supportedTag.substring(dash + 1));
    }

    /**
     * Warns once per distinct unsupported tag, and only for the first {@value #MAX_WARNED_TAGS} of them, so a locale
     * taken from each request cannot log on every request.
     */
    private static void warnUnsupported(String tag) {
        String shown = tag == null ? "null" : tag.length() > MAX_SHOWN_TAG_LENGTH ? tag.substring(0, MAX_SHOWN_TAG_LENGTH) + "..." : tag;
        if (WARNED_TAGS.size() >= MAX_WARNED_TAGS || !WARNED_TAGS.add(shown)) {
            return;
        }
        if (MOCK_SERVER_LOGGER.isEnabledForInstance(Level.WARN)) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setLogLevel(Level.WARN)
                    .setMessageFormat("faker.locale: Datafaker has no data for locale \"{}\", so the default English faker was used; use a locale such as en-GB, de, fr, ja or pt-BR")
                    .setArguments(shown)
            );
        }
    }

    private static Set<String> supportedLocales() {
        Set<String> supported = new TreeSet<>();
        for (String locale : SHIPPED_LOCALES) {
            if (Faker.class.getClassLoader().getResource(locale + ".yml") != null) {
                supported.add(locale);
            }
        }
        return Collections.unmodifiableSet(supported);
    }

    private static final class LocaleLookup extends AbstractMap<String, Object> {
        private final TemplateFaker faker;

        private LocaleLookup(TemplateFaker faker) {
            this.faker = faker;
        }

        @Override
        public boolean containsKey(Object key) {
            return key instanceof String;
        }

        @Override
        public Object get(Object key) {
            return faker.locale(String.valueOf(key));
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return Collections.emptySet();
        }
    }
}

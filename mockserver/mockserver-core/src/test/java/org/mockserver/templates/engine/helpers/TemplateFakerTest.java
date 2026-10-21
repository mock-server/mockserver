package org.mockserver.templates.engine.helpers;

import org.junit.Test;
import org.mockserver.templates.engine.TemplateFunctions;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assume.assumeTrue;

public class TemplateFakerTest {

    @Test
    public void shouldGiveAFakerForASupportedLocale() {
        TemplateFaker faker = new TemplateFaker();
        assertThat(faker.locale("de-AT").getContext().getLocale(), is(new Locale("de", "AT")));
        assertThat(faker.locale("pt_br").getContext().getLocale(), is(new Locale("pt", "BR")));
        assertThat(faker.locale(" ja ").getContext().getLocale(), is(new Locale("ja")));
        assertThat(faker.locale("en-PAK").getContext().getLocale(), is(new Locale("en", "PAK")));
        assertThat(faker.locale("de").address().state().isEmpty(), is(false));
    }

    @Test
    public void shouldReduceATagToALocaleDatafakerShips() {
        TemplateFaker faker = new TemplateFaker();
        TemplateFaker german = faker.locale("de");
        assertThat(faker.locale("de-DE"), sameInstance(german));
        assertThat(faker.locale("de-BE"), sameInstance(german));
        assertThat(faker.locale("de-x-r1"), sameInstance(german));
        assertThat(faker.locale("de-Latn-AT"), sameInstance(german));
        assertThat(faker.locale("de_AT"), sameInstance(faker.locale("de-AT")));
        assertThat(faker.locale("de-AT").locale("fr"), sameInstance(faker.locale("fr")));
        assertThat(faker.locale().get("de-AT"), sameInstance(faker.locale("de-AT")));
    }

    @Test
    public void shouldUseTheDefaultFakerForAnUnsupportedLocale() {
        TemplateFaker faker = new TemplateFaker();
        for (String unsupported : new String[]{"xx", "", "  ", "not a locale", "x".repeat(100), "e", "-de", null}) {
            assertThat(String.valueOf(unsupported), faker.locale(unsupported), sameInstance(faker));
            assertThat(String.valueOf(unsupported), faker.locale("de").locale(unsupported), sameInstance(faker));
        }
    }

    @Test
    public void shouldWarnOncePerUnsupportedLocaleAndForABoundedNumberOfLocales() {
        TemplateFaker faker = new TemplateFaker();
        // the set is shared with other tests running in parallel, so only facts they cannot change are checked
        faker.locale("qq");
        faker.locale("qq");
        assertThat(TemplateFaker.WARNED_TAGS.contains("qq") || TemplateFaker.WARNED_TAGS.size() >= TemplateFaker.MAX_WARNED_TAGS, is(true));
        for (int i = 0; i < 1_000; i++) {
            faker.locale("zz-request-" + i);
        }
        assertThat(TemplateFaker.WARNED_TAGS.size(), lessThanOrEqualTo(TemplateFaker.MAX_WARNED_TAGS + 4));
    }

    @Test
    public void shouldGenerateTheSameDataForTheSameSeed() {
        assertThat(new TemplateFaker(7L).locale("de").name().fullName(), is(new TemplateFaker(7L).locale("de").name().fullName()));
        assertThat(TemplateFunctions.resolveFaker(7L), instanceOf(TemplateFaker.class));
        assertThat(TemplateFunctions.BUILT_IN_HELPERS.get("faker"), instanceOf(TemplateFaker.class));
    }

    @Test
    public void shouldOnlyEverPassShippedLocalesToDatafaker() throws Exception {
        assertThat(TemplateFaker.SUPPORTED_LOCALES, hasItem("de"));
        assertThat(TemplateFaker.SUPPORTED_LOCALES, hasItem("en-PAK"));
        TemplateFaker faker = new TemplateFaker();
        Map<?, ?> datafakerLocales = datafakerSingletonLocales();
        int before = datafakerLocales == null ? 0 : datafakerLocales.size();
        for (int i = 0; i < 2_000; i++) {
            faker.locale("de-x-r" + i).name().firstName();
            faker.locale("zz-" + i);
            faker.locale("de-" + i);
        }
        Field cache = TemplateFaker.class.getDeclaredField("localeFakers");
        cache.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> cached = (Map<String, ?>) cache.get(faker);
        assertThat(cached.keySet(), everyItem(isIn(TemplateFaker.SUPPORTED_LOCALES)));
        if (datafakerLocales != null) {
            assertThat(datafakerLocales.size() - before, lessThanOrEqualTo(TemplateFaker.SUPPORTED_LOCALES.size()));
        }
    }

    // datafaker's static, never-evicted Locale map; null if a datafaker upgrade renames it
    private static Map<?, ?> datafakerSingletonLocales() {
        try {
            Field field = Class.forName("net.datafaker.internal.helper.SingletonLocale").getDeclaredField("LOCALE2SINGLETON_LOCALE");
            field.setAccessible(true);
            return (Map<?, ?>) field.get(null);
        } catch (Exception | LinkageError notInThisVersion) {
            return null;
        }
    }

    @Test
    public void shouldFindDatafakersLocaleMapSoTheBoundIsChecked() {
        assumeTrue("datafaker internals are as expected", datafakerSingletonLocales() != null);
    }
}

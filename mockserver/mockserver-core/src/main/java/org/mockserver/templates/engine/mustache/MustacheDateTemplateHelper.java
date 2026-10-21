package org.mockserver.templates.engine.mustache;

import com.samskivert.mustache.Mustache;
import org.mockserver.templates.engine.helpers.DateTemplateHelper;
import org.mockserver.templates.engine.helpers.TemplateDate;

import java.util.Locale;

/**
 * The {@code dates} helper as Mustache sees it. jmustache cannot pass arguments to a method, so as well as
 * the no-argument methods ({@code {{ dates.rfc1123 }}}, {@code {{ dates.now.firstDayOfNextMonth }}}) it
 * has a {@code render} section lambda whose body is a date and options separated by {@code |}:
 * {@code {{#dates.render}}{{request.headers.Date.0}}|offset=1 days|format=rfc1123{{/dates.render}}}.
 */
public class MustacheDateTemplateHelper extends DateTemplateHelper {

    private static final DateTemplateHelper DATES = new DateTemplateHelper();
    private static final Mustache.Lambda RENDER = (fragment, out) -> out.write(render(fragment.execute(), parts(fragment.decompile())));

    public Mustache.Lambda render() {
        return RENDER;
    }

    private static int parts(String text) {
        int parts = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '|') {
                parts++;
            }
        }
        return parts;
    }

    /**
     * The first part is the date ({@code now}, or anything {@code dates.parse} reads); then
     * {@code pattern=} (the pattern the date is written in), and {@code zone=}, {@code offset=} and
     * {@code truncate=} applied in the order written, and finally an optional {@code format=}. The first
     * {@code zone=} is also the zone a date with no zone or offset is read in.
     */
    static String render(String spec, int templateParts) {
        String[] parts = spec.split("\\|", -1);
        if (parts.length != templateParts) {
            // a value rendered into the section, such as a request header, must not add or change options
            throw new IllegalArgumentException("dates.render: a value inside the section contains |, which separates the date from its options; render only the date or an option value from request data, and make sure it has no |");
        }
        String pattern = null;
        String readZone = null;
        for (int i = 1; i < parts.length; i++) {
            String key = key(parts[i]);
            if (key.equals("pattern")) {
                pattern = value(parts[i]);
            } else if (readZone == null && (key.equals("zone") || key.equals("timezone"))) {
                readZone = value(parts[i]);
            }
        }
        TemplateDate date = DATES.parse(parts[0].trim(), pattern, readZone);
        for (int i = 1; i < parts.length; i++) {
            String key = key(parts[i]);
            String value = value(parts[i]);
            switch (key) {
                case "pattern":
                    break;
                case "zone":
                case "timezone":
                    date = date.withZone(value);
                    break;
                case "offset":
                    date = date.offset(value);
                    break;
                case "truncate":
                    date = date.truncate(value);
                    break;
                case "format":
                    if (i != parts.length - 1) {
                        throw new IllegalArgumentException("dates.render: format must be the last option in \"" + spec + "\"");
                    }
                    return date.format(value);
                default:
                    throw new IllegalArgumentException("dates.render: unknown option \"" + parts[i].trim() + "\" in \"" + spec + "\"; use pattern=, zone=, offset=, truncate= or format=");
            }
        }
        return date.toString();
    }

    private static String key(String option) {
        int equals = option.indexOf('=');
        if (equals < 0) {
            throw new IllegalArgumentException("dates.render: option \"" + option.trim() + "\" has no value; write it as name=value, such as offset=1 days");
        }
        return option.substring(0, equals).trim().toLowerCase(Locale.ROOT);
    }

    private static String value(String option) {
        return option.substring(option.indexOf('=') + 1).trim();
    }
}

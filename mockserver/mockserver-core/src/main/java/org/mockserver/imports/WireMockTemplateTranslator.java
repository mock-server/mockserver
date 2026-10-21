package org.mockserver.imports;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates the simple request references in WireMock's Handlebars templates into MockServer's
 * equivalents, so an imported stub that uses them keeps working.
 *
 * <p>This is deliberately not a Handlebars translator. It handles plain variable references only, such as
 * {@code {{request.path}}}, {@code {{request.query.x}}} and {@code {{request.headers.X}}}. Helpers, block
 * sections and triple-stash (unescaped) tags are reported as untranslatable, so the importer can leave
 * the text verbatim and warn.
 */
final class WireMockTemplateTranslator {

    private static final Pattern TAG = Pattern.compile("\\{\\{(\\{?)(.*?)(\\}?)\\}\\}", Pattern.DOTALL);
    private static final Pattern REQUEST_VALUE = Pattern.compile("\\{\\{\\{?\\s*request\\.(?!method\\s*}})");
    private static final Pattern BRACKETED_SEGMENT = Pattern.compile("\\.?\\[([^\\]]*)]");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,4}");
    private static final Pattern JSON_PATH_HELPER = Pattern.compile("jsonPath\\s+originalRequest\\.body\\s+'([^']*)'");
    private static final Pattern SIMPLE_JSON_PATH = Pattern.compile("\\$((?:\\.[A-Za-z_][A-Za-z0-9_-]*|\\[\\d+])+)");

    private WireMockTemplateTranslator() {
    }

    /**
     * The result of a translation: the translated text, and the original tags that could not be translated.
     * When {@link #untranslatable} is not empty, {@link #text} is not usable and the caller should keep the
     * source verbatim.
     */
    static final class Result {
        final String text;
        final boolean hasTags;
        final List<String> untranslatable;

        private Result(String text, boolean hasTags, List<String> untranslatable) {
            this.text = text;
            this.hasTags = hasTags;
            this.untranslatable = untranslatable;
        }

        boolean isTranslated() {
            return untranslatable.isEmpty();
        }
    }

    /**
     * What the request matcher fixes about the path, which some references depend on.
     *
     * @param exactPath    the exact path the stub matches ({@code urlPath} / {@code url}), or null
     * @param pathTemplate true when the stub matches a {@code urlPathTemplate}, so its named
     *                     parameters are available as MockServer path parameters
     */
    static final class PathContext {
        final String exactPath;
        final boolean pathTemplate;

        PathContext(String exactPath, boolean pathTemplate) {
            this.exactPath = exactPath;
            this.pathTemplate = pathTemplate;
        }
    }

    static boolean hasTags(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /**
     * True when the text renders a request value a client chooses (anything but the method).
     */
    static boolean referencesRequestValues(String text) {
        return text != null && REQUEST_VALUE.matcher(text).find();
    }

    /**
     * Translates a WireMock response template into a MockServer Mustache template.
     */
    static Result toMustache(String text, PathContext pathContext) {
        return translate(text, (tripleStash, expression) -> {
            if (tripleStash) {
                // unescaped output cannot be embedded safely in MockServer's JSON response template
                return null;
            }
            return mustacheReplacement(normalise(expression), pathContext);
        });
    }

    /**
     * Translates a WireMock webhook template into MockServer's after-action runtime expressions
     * ({@code {$request.header.X}} and similar).
     */
    static Result toRuntimeExpressions(String text) {
        return translate(text, (tripleStash, expression) -> {
            String trimmed = expression.trim();
            Matcher jsonPath = JSON_PATH_HELPER.matcher(trimmed);
            if (jsonPath.matches()) {
                String pointer = jsonPointer(jsonPath.group(1));
                return pointer == null ? null : "{$request.body#" + pointer + "}";
            }
            String[] parts = normalise(trimmed).split("\\.");
            if (parts.length == 2 && parts[0].equals("originalRequest") && parts[1].equals("method")) {
                return "{$request.method}";
            }
            if (parts.length == 3 && parts[0].equals("originalRequest")) {
                if (parts[1].equals("headers")) {
                    return "{$request.header." + parts[2] + "}";
                }
                if (parts[1].equals("query")) {
                    return "{$request.query." + parts[2] + "}";
                }
            }
            return null;
        });
    }

    private interface TagTranslator {
        String translate(boolean tripleStash, String expression);
    }

    private static Result translate(String text, TagTranslator translator) {
        if (text == null) {
            return new Result(null, false, List.of());
        }
        Matcher matcher = TAG.matcher(text);
        StringBuilder translated = new StringBuilder();
        List<String> untranslatable = new ArrayList<>();
        boolean hasTags = false;
        while (matcher.find()) {
            hasTags = true;
            String tag = matcher.group();
            boolean tripleStash = !matcher.group(1).isEmpty() && !matcher.group(3).isEmpty();
            String expression = matcher.group(2).trim();
            String replacement = translator.translate(tripleStash, expression);
            if (replacement == null) {
                untranslatable.add(tag);
                replacement = tag;
            }
            matcher.appendReplacement(translated, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(translated);
        return new Result(translated.toString(), hasTags, untranslatable);
    }

    /**
     * Maps one WireMock {@code request.*} reference onto the MockServer Mustache request model, or returns
     * null when there is no equivalent. A reference with whitespace is a helper call, which is not mapped.
     */
    private static String mustacheReplacement(String expression, PathContext pathContext) {
        if (expression.startsWith("request.pathSegments.") || expression.matches("request\\.path\\.\\d+")) {
            String index = expression.substring(expression.lastIndexOf('.') + 1);
            return NUMBER.matcher(index).matches() ? literalPathSegment(index, pathContext) : null;
        }
        String mustacheExpression = mustacheExpression(expression, pathContext);
        return mustacheExpression == null ? null : "{{" + mustacheExpression + "}}";
    }

    private static String mustacheExpression(String expression, PathContext pathContext) {
        if (expression.isEmpty() || !expression.startsWith("request.") || expression.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        String[] parts = expression.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        switch (parts[1]) {
            case "method":
            case "body":
                return parts.length == 2 ? expression : null;
            case "path":
                if (parts.length == 2) {
                    return expression;
                }
                return parts.length == 3 && pathContext.pathTemplate ? "request.pathParameters." + parts[2] + ".0" : null;
            case "query":
                return multiValued("request.queryStringParameters.", parts);
            case "headers":
                return multiValued("request.headers.", parts);
            case "cookies":
                return parts.length == 3 ? expression : null;
            default:
                return null;
        }
    }

    private static String multiValued(String prefix, String[] parts) {
        if (parts.length == 3) {
            return prefix + parts[2] + ".0";
        }
        if (parts.length == 4 && NUMBER.matcher(parts[3]).matches()) {
            return prefix + parts[2] + "." + parts[3];
        }
        return null;
    }

    /**
     * A path segment of an exact-path stub is fixed by the matcher, so it renders as a literal. Any other
     * path matcher lets the segment vary, which MockServer's template model cannot index.
     */
    private static String literalPathSegment(String index, PathContext pathContext) {
        if (pathContext.exactPath == null) {
            return null;
        }
        String[] segments = pathContext.exactPath.replaceFirst("^/", "").split("/");
        int i = Integer.parseInt(index);
        if (i >= segments.length || segments[i].contains("{") || segments[i].contains("}")) {
            return null;
        }
        return segments[i];
    }

    /**
     * Handlebars allows {@code a.[b]} and {@code a.[0]} segment literals; Mustache uses plain dotted names.
     */
    private static String normalise(String expression) {
        return BRACKETED_SEGMENT.matcher(expression).replaceAll(".$1");
    }

    private static String jsonPointer(String jsonPath) {
        Matcher matcher = SIMPLE_JSON_PATH.matcher(jsonPath);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1)
            .replaceAll("\\[(\\d+)]", ".$1")
            .replace("~", "~0")
            .replace(".", "/");
    }
}

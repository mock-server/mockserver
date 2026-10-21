package org.mockserver.serialization.java;

import com.google.common.base.Strings;
import org.apache.commons.text.StringEscapeUtils;
import org.mockserver.model.HttpTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpTemplateToJavaSerializer implements ToJavaSerializer<HttpTemplate> {

    @Override
    public String serialize(int numberOfSpacesToIndent, HttpTemplate httpTemplate) {
        StringBuffer output = new StringBuffer();
        if (httpTemplate != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("template(HttpTemplate.TemplateType.").append(httpTemplate.getTemplateType().name()).append(")");
            if (isNotBlank(httpTemplate.getTemplate())) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withTemplate(\"").append(StringEscapeUtils.escapeJava(httpTemplate.getTemplate())).append("\")");
            }
            if (isNotBlank(httpTemplate.getTemplateFile())) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withTemplateFile(\"").append(StringEscapeUtils.escapeJava(httpTemplate.getTemplateFile())).append("\")");
            }
            if (httpTemplate.getResponseOverride() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withResponseOverride(");
                output.append(new HttpResponseToJavaSerializer().serialize(numberOfSpacesToIndent + 2, httpTemplate.getResponseOverride()));
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(")");
            }
            if (httpTemplate.getResponseModifier() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withResponseModifier(");
                output.append(new HttpResponseModifierToJavaSerializer().serialize(numberOfSpacesToIndent + 2, httpTemplate.getResponseModifier()));
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(")");
            }
            if (httpTemplate.getParameters() != null) {
                for (Map.Entry<String, Object> parameter : httpTemplate.getParameters().entrySet()) {
                    appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withParameter(\"").append(StringEscapeUtils.escapeJava(parameter.getKey())).append("\", ");
                    appendValue(parameter.getValue(), output);
                    output.append(")");
                }
            }
            if (httpTemplate.getDelay() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withDelay(").append(new DelayToJavaSerializer().serialize(0, httpTemplate.getDelay())).append(")");
            }
        }

        return output.toString();
    }

    /**
     * A parameter value as Java. Maps are written as {@code Map.of} (or {@code Map.ofEntries} past ten entries), or,
     * when they hold a null, which those reject, as a {@code LinkedHashMap} filled in place. Lists are written as
     * {@code Arrays.asList}, which accepts nulls.
     */
    private static void appendValue(Object value, StringBuffer output) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String text) {
            output.append("\"").append(StringEscapeUtils.escapeJava(text)).append("\"");
        } else if (value instanceof Long number) {
            output.append(number).append("L");
        } else if (value instanceof Float number) {
            output.append(Float.isFinite(number) ? number + "F" : nonFinite("Float", number));
        } else if (value instanceof Double number) {
            output.append(Double.isFinite(number) ? number.toString() : nonFinite("Double", number));
        } else if (value instanceof Integer || value instanceof Short || value instanceof Byte || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof BigDecimal || value instanceof BigInteger) {
            output.append("new ").append(value.getClass().getName()).append("(\"").append(value).append("\")");
        } else if (value instanceof Map<?, ?> map) {
            if (map.containsValue(null)) {
                output.append("new java.util.LinkedHashMap<String, Object>() {{");
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    output.append(" put(\"").append(StringEscapeUtils.escapeJava(String.valueOf(entry.getKey()))).append("\", ");
                    appendValue(entry.getValue(), output);
                    output.append(");");
                }
                output.append(" }}");
                return;
            }
            boolean ofEntries = map.size() > 10;
            output.append(ofEntries ? "java.util.Map.ofEntries(" : "java.util.Map.of(");
            for (Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator(); entries.hasNext(); ) {
                Map.Entry<?, ?> entry = entries.next();
                output.append(ofEntries ? "java.util.Map.entry(" : "").append("\"").append(StringEscapeUtils.escapeJava(String.valueOf(entry.getKey()))).append("\", ");
                appendValue(entry.getValue(), output);
                output.append(ofEntries ? ")" : "").append(entries.hasNext() ? ", " : "");
            }
            output.append(")");
        } else if (value instanceof List<?> list) {
            output.append("java.util.Arrays.<Object>asList(");
            for (Iterator<?> elements = list.iterator(); elements.hasNext(); ) {
                Object element = elements.next();
                // a bare null as the only argument would be passed as the varargs array itself
                if (element == null) {
                    output.append("(Object) null");
                } else {
                    appendValue(element, output);
                }
                output.append(elements.hasNext() ? ", " : "");
            }
            output.append(")");
        } else {
            output.append("\"").append(StringEscapeUtils.escapeJava(String.valueOf(value))).append("\"");
        }
    }

    private static String nonFinite(String type, double number) {
        return type + (Double.isNaN(number) ? ".NaN" : number > 0 ? ".POSITIVE_INFINITY" : ".NEGATIVE_INFINITY");
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}

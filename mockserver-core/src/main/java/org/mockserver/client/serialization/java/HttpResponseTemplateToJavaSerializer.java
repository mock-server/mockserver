package org.mockserver.client.serialization.java;

import com.google.common.base.Strings;
import org.apache.commons.text.StringEscapeUtils;
import org.mockserver.model.HttpTemplate;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.client.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

/**
 * @author jamesdbloom
 */
public class HttpResponseTemplateToJavaSerializer implements ToJavaSerializer<HttpTemplate> {

    @Override
    public String serializeAsJava(int numberOfSpacesToIndent, HttpTemplate httpTemplate) {
        StringBuffer output = new StringBuffer();
        if (httpTemplate != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("template(HttpTemplate.TemplateType." + httpTemplate.getTemplateType().name() + ")");
            if (!Strings.isNullOrEmpty(httpTemplate.getTemplate())) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withTemplate(\"").append(StringEscapeUtils.escapeJava(httpTemplate.getTemplate())).append("\")");
            }
            if (httpTemplate.getDelay() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withDelay(").append(new DelayToJavaSerializer().serializeAsJava(0, httpTemplate.getDelay())).append(")");
            }
        }

        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}

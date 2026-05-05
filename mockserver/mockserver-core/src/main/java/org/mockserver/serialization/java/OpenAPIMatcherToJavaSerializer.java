package org.mockserver.serialization.java;

import com.google.common.base.Strings;
import org.mockserver.model.OpenAPIDefinition;

import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.java.ExpectationToJavaSerializer.INDENT_SIZE;

public class OpenAPIMatcherToJavaSerializer implements ToJavaSerializer<OpenAPIDefinition> {

    @Override
    public String serialize(int numberOfSpacesToIndent, OpenAPIDefinition OpenAPIMatcher) {
        StringBuffer output = new StringBuffer();
        if (OpenAPIMatcher != null) {
            appendNewLineAndIndent(numberOfSpacesToIndent * INDENT_SIZE, output).append("openAPIMatcher()");
            if (OpenAPIMatcher.getSpecUrlOrPayload() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withSpecUrlOrPayload(\"").append(OpenAPIMatcher.getSpecUrlOrPayload()).append("\")");
            }
            if (OpenAPIMatcher.getOperationId() != null) {
                appendNewLineAndIndent((numberOfSpacesToIndent + 1) * INDENT_SIZE, output).append(".withOperationId(").append(OpenAPIMatcher.getOperationId()).append(")");
            }
        }
        return output.toString();
    }

    private StringBuffer appendNewLineAndIndent(int numberOfSpacesToIndent, StringBuffer output) {
        return output.append(NEW_LINE).append(Strings.padStart("", numberOfSpacesToIndent, ' '));
    }
}

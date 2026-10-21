package org.mockserver.serialization.java;

import org.mockserver.model.AfterAction;

public class AfterActionToJavaSerializer implements ToJavaSerializer<AfterAction> {

    @Override
    public String serialize(int numberOfSpacesToIndent, AfterAction afterAction) {
        if (afterAction == null) {
            return "";
        }
        return new FluentJavaBuilder(numberOfSpacesToIndent, "AfterAction.afterAction()")
            .withObject("withHttpRequest", afterAction.getHttpRequest(), new HttpRequestToJavaSerializer())
            .withObject("withHttpRequestTemplate", afterAction.getHttpRequestTemplate(), new HttpTemplateToJavaSerializer())
            .withObject("withHttpClassCallback", afterAction.getHttpClassCallback(), new HttpClassCallbackToJavaSerializer())
            .comment(afterAction.getHttpObjectCallback() != null, "NOT POSSIBLE TO GENERATE CODE FOR OBJECT CALLBACK")
            .withDelay("withDelay", afterAction.getDelay())
            .with("withBlocking", afterAction.getBlocking())
            .withDelay("withTimeout", afterAction.getTimeout())
            .with("withFailurePolicy", afterAction.getFailurePolicy())
            .build();
    }
}

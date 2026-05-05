package org.mockserver.xml;

import org.junit.Test;

import javax.xml.xpath.XPathConstants;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class XPathEvaluatorTest {

    @Test
    public void shouldMatchMatchingXPath() {
        String xml = "" +
            "<element>" +
            "   <key>some_key</key>" +
            "   <value>some_value</value>" +
            "</element>";

        evaluateXPath(xml, "/element[key = 'some_key' and value = 'some_value']", "   some_key   some_value");
        evaluateXPath(xml, "/element[key = 'some_key']", "   some_key   some_value");
        evaluateXPath(xml, "/element/key", "some_key");
        evaluateXPath(xml, "/element[key and value]", "   some_key   some_value");
    }

    private void evaluateXPath(String matched, String expression, String expected) {
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        assertThat(new XPathEvaluator(expression, null).evaluateXPathExpression(matched, (xmlAsString, exception, level) -> throwable.set(exception), XPathConstants.STRING), is(expected));
        if (throwable.get() != null) {
            throw new RuntimeException(throwable.get().getMessage(), throwable.get());
        }
    }

}
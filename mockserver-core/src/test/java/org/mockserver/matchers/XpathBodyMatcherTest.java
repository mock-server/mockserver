package org.mockserver.matchers;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathFactory;
import java.util.Map;

public class XpathBodyMatcherTest {

    private String xmlBody = "<element><key>name</key><value>NAME</value></element>";

    @Test
    public void matchesEmptyBody() {
        // given
        XpathBodyMatcher matcher = prepareMatcher(ImmutableMap.<String, String>builder().build());

        // when
        boolean currentResult = matcher.matches("some body");

        // then
        Assert.assertTrue(currentResult);
    }

    @Test
    public void notMatchesNonXmlBody() {
        // given
        XpathBodyMatcher matcher = prepareMatcher(ImmutableMap.<String, String>builder().put("/element/key", "name").build());

        // when
        boolean currentResult = matcher.matches("NON XML BODY");

        // then
        Assert.assertFalse(currentResult);
    }

    @Test
    public void notMatchesForInvalidXpathExpression() {
        // given
        XpathBodyMatcher matcher = prepareMatcher(ImmutableMap.<String, String>builder().put("123*&^*#", "name").build());

        // when
        boolean currentResult = matcher.matches(xmlBody);

        // then
        Assert.assertFalse(currentResult);
    }

    @Test
    public void notMatchesForNotMatchingValue() {
        // given
        XpathBodyMatcher matcher = prepareMatcher(ImmutableMap.<String, String>builder().put("/element/key", "NAME").build());

        // when
        boolean currentResult = matcher.matches(xmlBody);

        // then
        Assert.assertFalse(currentResult);
    }

    @Test
    public void matches() {
        // given
        XpathBodyMatcher matcher = prepareMatcher(ImmutableMap.<String, String>builder().put("/element/key", "name").put("/element/value", "NAME").build());

        // when
        boolean currentResult = matcher.matches(xmlBody);

        // then
        Assert.assertTrue(currentResult);
    }

    private XpathBodyMatcher prepareMatcher(Map<String, String> xpathToValueMap) {
        try {
            return new XpathBodyMatcher(xpathToValueMap, XPathFactory.newInstance().newXPath(), DocumentBuilderFactory.newInstance().newDocumentBuilder());
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

}

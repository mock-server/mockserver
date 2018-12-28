package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.junit.Assert.*;
import static org.mockserver.matchers.NotMatcher.not;

/**
 * @author jamesdbloom
 */
public class JsonPathMatcherTest {

    @Test
    public void shouldMatchMatchingJsonPath() {
        String matched = "" +
                "{\n" +
            "    \"store\": {\n" +
            "        \"book\": [\n" +
            "            {\n" +
            "                \"category\": \"reference\",\n" +
            "                \"author\": \"Nigel Rees\",\n" +
            "                \"title\": \"Sayings of the Century\",\n" +
            "                \"price\": 8.95\n" +
            "            },\n" +
            "            {\n" +
            "                \"category\": \"fiction\",\n" +
            "                \"author\": \"Herman Melville\",\n" +
            "                \"title\": \"Moby Dick\",\n" +
            "                \"isbn\": \"0-553-21311-3\",\n" +
            "                \"price\": 8.99\n" +
            "            }\n" +
            "        ],\n" +
            "        \"bicycle\": {\n" +
            "            \"color\": \"red\",\n" +
            "            \"price\": 19.95\n" +
            "        }\n" +
            "    },\n" +
            "    \"expensive\": 10\n" +
            "}";
        assertTrue(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.price <= $['expensive'])]").matches(null, matched));
        assertTrue(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.isbn)]").matches(null, matched));
        assertTrue(new JsonPathMatcher(new MockServerLogger(),"$..bicycle[?(@.color)]").matches(null, matched));
    }

    @Test
    public void shouldNotMatchMatchingJsonPathWithNot() {
        String matched = "" +
            "{\n" +
            "    \"store\": {\n" +
            "        \"book\": [\n" +
            "            {\n" +
            "                \"category\": \"reference\",\n" +
            "                \"author\": \"Nigel Rees\",\n" +
            "                \"title\": \"Sayings of the Century\",\n" +
            "                \"price\": 8.95\n" +
            "            },\n" +
            "            {\n" +
            "                \"category\": \"fiction\",\n" +
            "                \"author\": \"Herman Melville\",\n" +
            "                \"title\": \"Moby Dick\",\n" +
            "                \"isbn\": \"0-553-21311-3\",\n" +
            "                \"price\": 8.99\n" +
            "            }\n" +
            "        ],\n" +
            "        \"bicycle\": {\n" +
            "            \"color\": \"red\",\n" +
            "            \"price\": 19.95\n" +
            "        }\n" +
            "    },\n" +
            "    \"expensive\": 10\n" +
            "}";
        assertFalse(not(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.price <= $['expensive'])]")).matches(null, matched));
        assertFalse(not(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.isbn)]")).matches(null, matched));
        assertFalse(not(new JsonPathMatcher(new MockServerLogger(),"$..bicycle[?(@.color)]")).matches(null, matched));
    }

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new JsonPathMatcher(new MockServerLogger(),"some_value").matches(null, "some_value"));
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"some_value").matches(null, "some_other_value"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(new JsonPathMatcher(new MockServerLogger(),null).matches(null, "some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"").matches(null, "some_value"));
    }

    @Test
    public void shouldNotMatchNotMatchingJsonPath() {
        String matched = "" +
            "{\n" +
            "    \"store\": {\n" +
            "        \"book\": [\n" +
            "            {\n" +
            "                \"category\": \"reference\",\n" +
            "                \"author\": \"Nigel Rees\",\n" +
            "                \"title\": \"Sayings of the Century\",\n" +
            "                \"price\": 8.95\n" +
            "            },\n" +
            "            {\n" +
            "                \"category\": \"fiction\",\n" +
            "                \"author\": \"Herman Melville\",\n" +
            "                \"title\": \"Moby Dick\",\n" +
            "                \"isbn\": \"0-553-21311-3\",\n" +
            "                \"price\": 8.99\n" +
            "            }\n" +
            "        ],\n" +
            "        \"bicycle\": {\n" +
            "            \"color\": \"red\",\n" +
            "            \"price\": 19.95\n" +
            "        }\n" +
            "    },\n" +
            "    \"expensive\": 10\n" +
            "}";
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.price > $['expensive'])]").matches(null, matched));
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.color)]").matches(null, matched));
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"$..bicycle[?(@.isbn)]").matches(null, matched));
    }
    @Test
    public void shouldMatchNotMatchingJsonPathWithNot() {
        String matched = "" +
            "{\n" +
            "    \"store\": {\n" +
            "        \"book\": [\n" +
            "            {\n" +
            "                \"category\": \"reference\",\n" +
            "                \"author\": \"Nigel Rees\",\n" +
            "                \"title\": \"Sayings of the Century\",\n" +
            "                \"price\": 8.95\n" +
            "            },\n" +
            "            {\n" +
            "                \"category\": \"fiction\",\n" +
            "                \"author\": \"Herman Melville\",\n" +
            "                \"title\": \"Moby Dick\",\n" +
            "                \"isbn\": \"0-553-21311-3\",\n" +
            "                \"price\": 8.99\n" +
            "            }\n" +
            "        ],\n" +
            "        \"bicycle\": {\n" +
            "            \"color\": \"red\",\n" +
            "            \"price\": 19.95\n" +
            "        }\n" +
            "    },\n" +
            "    \"expensive\": 10\n" +
            "}";
        assertTrue(not(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.price > $['expensive'])]")).matches(null, matched));
        assertTrue(not(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.color)]")).matches(null, matched));
        assertTrue(not(new JsonPathMatcher(new MockServerLogger(),"$..bicycle[?(@.isbn)]")).matches(null, matched));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"some_value").matches(null, null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"some_value").matches(null, ""));
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        MockServerLogger mockServerLogger = new MockServerLogger();
        assertEquals(new JsonPathMatcher(mockServerLogger,"some_value"), new JsonPathMatcher(mockServerLogger,"some_value"));
    }
}

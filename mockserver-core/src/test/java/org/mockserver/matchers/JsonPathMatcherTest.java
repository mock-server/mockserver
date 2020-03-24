package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.junit.Assert.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.NotMatcher.not;

/**
 * @author jamesdbloom
 */
public class JsonPathMatcherTest {

    @Test
    public void shouldMatchMatchingJsonPath() {
        String matched = "" +
                "{" + NEW_LINE +
            "    \"store\": {" + NEW_LINE +
            "        \"book\": [" + NEW_LINE +
            "            {" + NEW_LINE +
            "                \"category\": \"reference\"," + NEW_LINE +
            "                \"author\": \"Nigel Rees\"," + NEW_LINE +
            "                \"title\": \"Sayings of the Century\"," + NEW_LINE +
            "                \"price\": 8.95" + NEW_LINE +
            "            }," + NEW_LINE +
            "            {" + NEW_LINE +
            "                \"category\": \"fiction\"," + NEW_LINE +
            "                \"author\": \"Herman Melville\"," + NEW_LINE +
            "                \"title\": \"Moby Dick\"," + NEW_LINE +
            "                \"isbn\": \"0-553-21311-3\"," + NEW_LINE +
            "                \"price\": 8.99" + NEW_LINE +
            "            }" + NEW_LINE +
            "        ]," + NEW_LINE +
            "        \"bicycle\": {" + NEW_LINE +
            "            \"color\": \"red\"," + NEW_LINE +
            "            \"price\": 19.95" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"expensive\": 10" + NEW_LINE +
            "}";
        assertTrue(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.price <= $['expensive'])]").matches(null, matched));
        assertTrue(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.isbn)]").matches(null, matched));
        assertTrue(new JsonPathMatcher(new MockServerLogger(),"$..bicycle[?(@.color)]").matches(null, matched));
    }

    @Test
    public void shouldNotMatchMatchingJsonPathWithNot() {
        String matched = "" +
            "{" + NEW_LINE +
            "    \"store\": {" + NEW_LINE +
            "        \"book\": [" + NEW_LINE +
            "            {" + NEW_LINE +
            "                \"category\": \"reference\"," + NEW_LINE +
            "                \"author\": \"Nigel Rees\"," + NEW_LINE +
            "                \"title\": \"Sayings of the Century\"," + NEW_LINE +
            "                \"price\": 8.95" + NEW_LINE +
            "            }," + NEW_LINE +
            "            {" + NEW_LINE +
            "                \"category\": \"fiction\"," + NEW_LINE +
            "                \"author\": \"Herman Melville\"," + NEW_LINE +
            "                \"title\": \"Moby Dick\"," + NEW_LINE +
            "                \"isbn\": \"0-553-21311-3\"," + NEW_LINE +
            "                \"price\": 8.99" + NEW_LINE +
            "            }" + NEW_LINE +
            "        ]," + NEW_LINE +
            "        \"bicycle\": {" + NEW_LINE +
            "            \"color\": \"red\"," + NEW_LINE +
            "            \"price\": 19.95" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"expensive\": 10" + NEW_LINE +
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
            "{" + NEW_LINE +
            "    \"store\": {" + NEW_LINE +
            "        \"book\": [" + NEW_LINE +
            "            {" + NEW_LINE +
            "                \"category\": \"reference\"," + NEW_LINE +
            "                \"author\": \"Nigel Rees\"," + NEW_LINE +
            "                \"title\": \"Sayings of the Century\"," + NEW_LINE +
            "                \"price\": 8.95" + NEW_LINE +
            "            }," + NEW_LINE +
            "            {" + NEW_LINE +
            "                \"category\": \"fiction\"," + NEW_LINE +
            "                \"author\": \"Herman Melville\"," + NEW_LINE +
            "                \"title\": \"Moby Dick\"," + NEW_LINE +
            "                \"isbn\": \"0-553-21311-3\"," + NEW_LINE +
            "                \"price\": 8.99" + NEW_LINE +
            "            }" + NEW_LINE +
            "        ]," + NEW_LINE +
            "        \"bicycle\": {" + NEW_LINE +
            "            \"color\": \"red\"," + NEW_LINE +
            "            \"price\": 19.95" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"expensive\": 10" + NEW_LINE +
            "}";
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.price > $['expensive'])]").matches(null, matched));
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"$..book[?(@.color)]").matches(null, matched));
        assertFalse(new JsonPathMatcher(new MockServerLogger(),"$..bicycle[?(@.isbn)]").matches(null, matched));
    }
    @Test
    public void shouldMatchNotMatchingJsonPathWithNot() {
        String matched = "" +
            "{" + NEW_LINE +
            "    \"store\": {" + NEW_LINE +
            "        \"book\": [" + NEW_LINE +
            "            {" + NEW_LINE +
            "                \"category\": \"reference\"," + NEW_LINE +
            "                \"author\": \"Nigel Rees\"," + NEW_LINE +
            "                \"title\": \"Sayings of the Century\"," + NEW_LINE +
            "                \"price\": 8.95" + NEW_LINE +
            "            }," + NEW_LINE +
            "            {" + NEW_LINE +
            "                \"category\": \"fiction\"," + NEW_LINE +
            "                \"author\": \"Herman Melville\"," + NEW_LINE +
            "                \"title\": \"Moby Dick\"," + NEW_LINE +
            "                \"isbn\": \"0-553-21311-3\"," + NEW_LINE +
            "                \"price\": 8.99" + NEW_LINE +
            "            }" + NEW_LINE +
            "        ]," + NEW_LINE +
            "        \"bicycle\": {" + NEW_LINE +
            "            \"color\": \"red\"," + NEW_LINE +
            "            \"price\": 19.95" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }," + NEW_LINE +
            "    \"expensive\": 10" + NEW_LINE +
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

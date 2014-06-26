package org.mockserver.matchers;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class JsonStringMatcherTest {

    @Test
    public void shouldMatchExactMatchingJson() {
        // given
        String matched = "" +
                "{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"file\", \n" +
                "        \"value\": \"File\", \n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";

        // then
        assertTrue(new JsonStringMatcher("{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"file\", \n" +
                "        \"value\": \"File\", \n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}").matches(matched));
    }

    @Test
    public void shouldMatchMatchingSubJson() {
        // given
        String matched = "" +
                "{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"file\", \n" +
                "        \"value\": \"File\", \n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";

        // then
        assertTrue(new JsonStringMatcher("{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"file\", \n" +
                "        \"value\": \"File\"\n" +
                "    }\n" +
                "}").matches(matched));
        assertTrue(new JsonStringMatcher("{\n" +
                "    \"menu\": {\n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}").matches(matched));
    }

    @Test
    public void shouldMatchMatchingSubJsonWithDifferentArrayOrder() {
        // given
        String matched = "" +
                "{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"file\", \n" +
                "        \"value\": \"File\", \n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";

        // then
        assertTrue(new JsonStringMatcher("{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"file\", \n" +
                "        \"value\": \"File\"\n" +
                "    }\n" +
                "}").matches(matched));
        assertTrue(new JsonStringMatcher("{\n" +
                "    \"menu\": {\n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}").matches(matched));
    }

    @Test
    public void shouldNotMatchIllegalJson() {
        assertFalse(new JsonStringMatcher("illegal_json").matches("illegal_json"));
        assertFalse(new JsonStringMatcher("illegal_json").matches("some_other_illegal_json"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(new JsonStringMatcher(null).matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(new JsonStringMatcher("").matches("some_value"));
    }

    @Test
    public void shouldNotMatchNonMatchingJson() {
        // given
        String matched = "" +
                "{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"file\", \n" +
                "        \"value\": \"File\", \n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";

        // then
        assertFalse(new JsonStringMatcher("{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"wrong_value\", \n" +
                "        \"value\": \"File\", \n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}").matches(matched));
    }

    @Test
    public void shouldNotMatchNonMatchingSubJson() {
        // given
        String matched = "" +
                "{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"file\", \n" +
                "        \"value\": \"File\", \n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}";

        // then
        assertFalse(new JsonStringMatcher("{\n" +
                "    \"menu\": {\n" +
                "        \"id\": \"file\", \n" +
                "        \"value\": \"other_value\"\n" +
                "    }\n" +
                "}").matches(matched));
        assertFalse(new JsonStringMatcher("{\n" +
                "    \"menu\": {\n" +
                "        \"popup\": {\n" +
                "            \"menuitem\": [\n" +
                "                {\n" +
                "                    \"value\": \"New\", \n" +
                "                    \"onclick\": \"CreateNewDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Open\", \n" +
                "                    \"onclick\": \"OpenDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }, \n" +
                "                {\n" +
                "                    \"value\": \"Close\", \n" +
                "                    \"onclick\": \"CloseDoc()\"\n" +
                "                }\n" +
                "            ]\n" +
                "        }\n" +
                "    }\n" +
                "}").matches(matched));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new JsonStringMatcher("some_value").matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new JsonStringMatcher("some_value").matches(""));
    }

    @Test
    public void showHaveCorrectEqualsBehaviour(){
        assertEquals(new JsonStringMatcher("some_value"), new JsonStringMatcher("some_value"));
    }
}

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
                "{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"," + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}";

        // then
        assertTrue(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"," + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
    }

    @Test
    public void shouldMatchMatchingSubJson() {
        // given
        String matched = "" +
                "{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"," + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}";

        // then
        assertTrue(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
        assertTrue(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
    }

    @Test
    public void shouldMatchMatchingSubJsonWithSomeSubJsonFields() {
        // given
        String matched = "" +
                "{" + System.getProperty("line.separator") +
                "    \"glossary\": {" + System.getProperty("line.separator") +
                "        \"title\": \"example glossary\"," + System.getProperty("line.separator") +
                "        \"GlossDiv\": {" + System.getProperty("line.separator") +
                "            \"title\": \"S\"," + System.getProperty("line.separator") +
                "            \"GlossList\": {" + System.getProperty("line.separator") +
                "                \"GlossEntry\": {" + System.getProperty("line.separator") +
                "                    \"ID\": \"SGML\"," + System.getProperty("line.separator") +
                "                    \"SortAs\": \"SGML\"," + System.getProperty("line.separator") +
                "                    \"GlossTerm\": \"Standard Generalized Markup Language\"," + System.getProperty("line.separator") +
                "                    \"Acronym\": \"SGML\"," + System.getProperty("line.separator") +
                "                    \"Abbrev\": \"ISO 8879:1986\"," + System.getProperty("line.separator") +
                "                    \"GlossDef\": {" + System.getProperty("line.separator") +
                "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"," + System.getProperty("line.separator") +
                "                        \"GlossSeeAlso\": [" + System.getProperty("line.separator") +
                "                            \"GML\"," + System.getProperty("line.separator") +
                "                            \"XML\"" + System.getProperty("line.separator") +
                "                        ]" + System.getProperty("line.separator") +
                "                    }, " + System.getProperty("line.separator") +
                "                    \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            }" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}";

        // then
        assertTrue(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"glossary\": {" + System.getProperty("line.separator") +
                "        \"GlossDiv\": {" + System.getProperty("line.separator") +
                "            \"title\": \"S\"," + System.getProperty("line.separator") +
                "            \"GlossList\": {" + System.getProperty("line.separator") +
                "                \"GlossEntry\": {" + System.getProperty("line.separator") +
                "                    \"ID\": \"SGML\"," + System.getProperty("line.separator") +
                "                    \"Abbrev\": \"ISO 8879:1986\"," + System.getProperty("line.separator") +
                "                    \"GlossDef\": {" + System.getProperty("line.separator") +
                "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"" + System.getProperty("line.separator") +
                "                    }, " + System.getProperty("line.separator") +
                "                    \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            }" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
    }

    @Test
    public void shouldNotMatchMatchingSubJsonWithSomeSubJsonFields() {
        // given
        String matched = "" +
                "{" + System.getProperty("line.separator") +
                "    \"glossary\": {" + System.getProperty("line.separator") +
                "        \"title\": \"example glossary\"," + System.getProperty("line.separator") +
                "        \"GlossDiv\": {" + System.getProperty("line.separator") +
                "            \"title\": \"S\"," + System.getProperty("line.separator") +
                "            \"GlossList\": {" + System.getProperty("line.separator") +
                "                \"GlossEntry\": {" + System.getProperty("line.separator") +
                "                    \"ID\": \"SGML\"," + System.getProperty("line.separator") +
                "                    \"SortAs\": \"SGML\"," + System.getProperty("line.separator") +
                "                    \"GlossTerm\": \"Standard Generalized Markup Language\"," + System.getProperty("line.separator") +
                "                    \"Acronym\": \"SGML\"," + System.getProperty("line.separator") +
                "                    \"Abbrev\": \"ISO 8879:1986\"," + System.getProperty("line.separator") +
                "                    \"GlossDef\": {" + System.getProperty("line.separator") +
                "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"," + System.getProperty("line.separator") +
                "                        \"GlossSeeAlso\": [" + System.getProperty("line.separator") +
                "                            \"GML\"," + System.getProperty("line.separator") +
                "                            \"XML\"" + System.getProperty("line.separator") +
                "                        ]" + System.getProperty("line.separator") +
                "                    }, " + System.getProperty("line.separator") +
                "                    \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            }" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}";

        // then
        assertFalse(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"glossary\": {" + System.getProperty("line.separator") +
                "        \"GlossDiv\": {" + System.getProperty("line.separator") +
                "            \"title\": \"S\"," + System.getProperty("line.separator") +
                "            \"GlossList\": {" + System.getProperty("line.separator") +
                "                \"GlossEntry\": {" + System.getProperty("line.separator") +
                "                    \"ID\": \"SGML\"," + System.getProperty("line.separator") +
                "                    \"Abbrev\": \"ISO 8879:1986\"," + System.getProperty("line.separator") +
                "                    \"GlossDef\": {" + System.getProperty("line.separator") +
                "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"" + System.getProperty("line.separator") +
                "                    }, " + System.getProperty("line.separator") +
                "                    \"GlossSee\": \"markup\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            }" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.STRICT).matches(matched));
    }

    @Test
    public void shouldMatchMatchingSubJsonWithDifferentArrayOrder() {
        // given
        String matched = "" +
                "{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"," + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}";

        // then
        assertTrue(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
        assertTrue(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
    }

    @Test
    public void shouldNotMatchMatchingSubJsonWithDifferentArrayOrder() {
        // given
        String matched = "" +
                "{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"," + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}";

        // then
        assertFalse(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.STRICT).matches(matched));
        assertFalse(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.STRICT).matches(matched));
    }

    @Test
    public void shouldNotMatchIllegalJson() {
        assertFalse(new JsonStringMatcher("illegal_json", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches("illegal_json"));
        assertFalse(new JsonStringMatcher("illegal_json", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches("some_other_illegal_json"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertFalse(new JsonStringMatcher(null, JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertFalse(new JsonStringMatcher("", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches("some_value"));
    }

    @Test
    public void shouldNotMatchNonMatchingJson() {
        // given
        String matched = "" +
                "{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"," + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}";

        // then
        assertFalse(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"wrong_value\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"," + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
    }

    @Test
    public void shouldMatchJsonForIncorrectArrayOrder() {
        // given
        String matched = "{id:1,pets:[\"dog\",\"cat\",\"fish\"]}";

        // then
        assertTrue(new JsonStringMatcher("{id:1,pets:[\"cat\",\"dog\",\"fish\"]}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
    }

    @Test
    public void shouldNotMatchJsonForIncorrectArrayOrder() {
        // given
        String matched = "{id:1,pets:[\"dog\",\"cat\",\"fish\"]}";

        // then
        assertFalse(new JsonStringMatcher("{id:1,pets:[\"cat\",\"dog\",\"fish\"]}", JsonBodyMatchType.STRICT).matches(matched));
    }

    @Test
    public void shouldMatchJsonForExtraField() {
        // given
        String matched = "{id:1,pets:[\"dog\",\"cat\",\"fish\"],extraField:\"extraValue\"}";

        // then
        assertTrue(new JsonStringMatcher("{id:1,pets:[\"dog\",\"cat\",\"fish\"]}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
    }

    @Test
    public void shouldNotMatchJsonForExtraField() {
        // given
        String matched = "{id:1,pets:[\"dog\",\"cat\",\"fish\"],extraField:\"extraValue\"}";

        // then
        assertFalse(new JsonStringMatcher("{id:1,pets:[\"dog\",\"cat\",\"fish\"]}", JsonBodyMatchType.STRICT).matches(matched));
    }

    @Test
    public void shouldNotMatchNonMatchingSubJson() {
        // given
        String matched = "" +
                "{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"File\"," + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}";

        // then
        assertFalse(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"id\": \"file\"," + System.getProperty("line.separator") +
                "        \"value\": \"other_value\"" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
        assertFalse(new JsonStringMatcher("{" + System.getProperty("line.separator") +
                "    \"menu\": {" + System.getProperty("line.separator") +
                "        \"popup\": {" + System.getProperty("line.separator") +
                "            \"menuitem\": [" + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"New\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CreateNewDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Open\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"OpenDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }, " + System.getProperty("line.separator") +
                "                {" + System.getProperty("line.separator") +
                "                    \"value\": \"Close\"," + System.getProperty("line.separator") +
                "                    \"onclick\": \"CloseDoc()\"" + System.getProperty("line.separator") +
                "                }" + System.getProperty("line.separator") +
                "            ]" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(matched));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new JsonStringMatcher("some_value", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new JsonStringMatcher("some_value", JsonBodyMatchType.ONLY_MATCHING_FIELDS).matches(""));
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        assertEquals(new JsonStringMatcher("some_value", JsonBodyMatchType.ONLY_MATCHING_FIELDS), new JsonStringMatcher("some_value", JsonBodyMatchType.ONLY_MATCHING_FIELDS));
    }
}

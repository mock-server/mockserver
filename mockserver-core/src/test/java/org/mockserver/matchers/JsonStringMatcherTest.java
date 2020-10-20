package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static org.junit.Assert.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.NotMatcher.notMatcher;

/**
 * @author jamesdbloom
 */
public class JsonStringMatcherTest {

    @Test
    public void shouldMatchExactMatchingJson() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
    }

    @Test
    public void shouldMatchExactMatchingJsonWithPlaceholder() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"optional\": true," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"optional\": \"${json-unit.any-boolean}\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"${json-unit.ignore-element}\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
    }

    @Test
    public void shouldNotMatchExactMatchingJson() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertFalse(notMatcher(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS)).matches(null, matched));
    }

    @Test
    public void shouldMatchMatchingSubJson() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
    }

    @Test
    public void shouldMatchMatchingSubJsonWithSomeSubJsonFields() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"glossary\": {" + NEW_LINE +
            "        \"title\": \"example glossary\"," + NEW_LINE +
            "        \"GlossDiv\": {" + NEW_LINE +
            "            \"title\": \"S\"," + NEW_LINE +
            "            \"GlossList\": {" + NEW_LINE +
            "                \"GlossEntry\": {" + NEW_LINE +
            "                    \"ID\": \"SGML\"," + NEW_LINE +
            "                    \"SortAs\": \"SGML\"," + NEW_LINE +
            "                    \"GlossTerm\": \"Standard Generalized Markup Language\"," + NEW_LINE +
            "                    \"Acronym\": \"SGML\"," + NEW_LINE +
            "                    \"Abbrev\": \"ISO 8879:1986\"," + NEW_LINE +
            "                    \"GlossDef\": {" + NEW_LINE +
            "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"," + NEW_LINE +
            "                        \"GlossSeeAlso\": [" + NEW_LINE +
            "                            \"GML\"," + NEW_LINE +
            "                            \"XML\"" + NEW_LINE +
            "                        ]" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"GlossSee\": \"markup\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            }" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"glossary\": {" + NEW_LINE +
            "        \"GlossDiv\": {" + NEW_LINE +
            "            \"title\": \"S\"," + NEW_LINE +
            "            \"GlossList\": {" + NEW_LINE +
            "                \"GlossEntry\": {" + NEW_LINE +
            "                    \"ID\": \"SGML\"," + NEW_LINE +
            "                    \"Abbrev\": \"ISO 8879:1986\"," + NEW_LINE +
            "                    \"GlossDef\": {" + NEW_LINE +
            "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"GlossSee\": \"markup\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            }" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
    }

    @Test
    public void shouldNotMatchNotMatchingSubJsonWithSomeSubJsonFields() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"glossary\": {" + NEW_LINE +
            "        \"title\": \"example glossary\"," + NEW_LINE +
            "        \"GlossDiv\": {" + NEW_LINE +
            "            \"title\": \"S\"," + NEW_LINE +
            "            \"GlossList\": {" + NEW_LINE +
            "                \"GlossEntry\": {" + NEW_LINE +
            "                    \"ID\": \"SGML\"," + NEW_LINE +
            "                    \"SortAs\": \"SGML\"," + NEW_LINE +
            "                    \"GlossTerm\": \"Standard Generalized Markup Language\"," + NEW_LINE +
            "                    \"Acronym\": \"SGML\"," + NEW_LINE +
            "                    \"Abbrev\": \"ISO 8879:1986\"," + NEW_LINE +
            "                    \"GlossDef\": {" + NEW_LINE +
            "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"," + NEW_LINE +
            "                        \"GlossSeeAlso\": [" + NEW_LINE +
            "                            \"GML\"," + NEW_LINE +
            "                            \"XML\"" + NEW_LINE +
            "                        ]" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"GlossSee\": \"markup\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            }" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"glossary\": {" + NEW_LINE +
            "        \"GlossDiv\": {" + NEW_LINE +
            "            \"title\": \"S\"," + NEW_LINE +
            "            \"GlossList\": {" + NEW_LINE +
            "                \"GlossEntry\": {" + NEW_LINE +
            "                    \"ID\": \"SGML\"," + NEW_LINE +
            "                    \"Abbrev\": \"ISO 8879:1986\"," + NEW_LINE +
            "                    \"GlossDef\": {" + NEW_LINE +
            "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"GlossSee\": \"markup\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            }" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.STRICT).matches(null, matched));
    }

    @Test
    public void shouldMatchNotMatchingSubJsonWithSomeSubJsonFields() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"glossary\": {" + NEW_LINE +
            "        \"title\": \"example glossary\"," + NEW_LINE +
            "        \"GlossDiv\": {" + NEW_LINE +
            "            \"title\": \"S\"," + NEW_LINE +
            "            \"GlossList\": {" + NEW_LINE +
            "                \"GlossEntry\": {" + NEW_LINE +
            "                    \"ID\": \"SGML\"," + NEW_LINE +
            "                    \"SortAs\": \"SGML\"," + NEW_LINE +
            "                    \"GlossTerm\": \"Standard Generalized Markup Language\"," + NEW_LINE +
            "                    \"Acronym\": \"SGML\"," + NEW_LINE +
            "                    \"Abbrev\": \"ISO 8879:1986\"," + NEW_LINE +
            "                    \"GlossDef\": {" + NEW_LINE +
            "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"," + NEW_LINE +
            "                        \"GlossSeeAlso\": [" + NEW_LINE +
            "                            \"GML\"," + NEW_LINE +
            "                            \"XML\"" + NEW_LINE +
            "                        ]" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"GlossSee\": \"markup\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            }" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertTrue(notMatcher(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"glossary\": {" + NEW_LINE +
            "        \"GlossDiv\": {" + NEW_LINE +
            "            \"title\": \"S\"," + NEW_LINE +
            "            \"GlossList\": {" + NEW_LINE +
            "                \"GlossEntry\": {" + NEW_LINE +
            "                    \"ID\": \"SGML\"," + NEW_LINE +
            "                    \"Abbrev\": \"ISO 8879:1986\"," + NEW_LINE +
            "                    \"GlossDef\": {" + NEW_LINE +
            "                        \"para\": \"A meta-markup language, used to create markup languages such as DocBook.\"" + NEW_LINE +
            "                    }, " + NEW_LINE +
            "                    \"GlossSee\": \"markup\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            }" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.STRICT)).matches(null, matched));
    }

    @Test
    public void shouldMatchMatchingSubJsonWithDifferentArrayOrder() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
    }

    @Test
    public void shouldNotMatchMatchingSubJsonWithDifferentArrayOrder() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.STRICT).matches(null, matched));
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.STRICT).matches(null, matched));
    }

    @Test
    public void shouldNotMatchIllegalJson() {
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "illegal_json", MatchType.ONLY_MATCHING_FIELDS).matches(null, "illegal_json"));
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "illegal_json", MatchType.ONLY_MATCHING_FIELDS).matches(null, "some_other_illegal_json"));
    }

    @Test
    public void shouldNotMatchNullExpectation() {
        assertTrue(new JsonStringMatcher(new MockServerLogger(), null, MatchType.ONLY_MATCHING_FIELDS).matches(null, "some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectation() {
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "", MatchType.ONLY_MATCHING_FIELDS).matches(null, "some_value"));
    }

    @Test
    public void shouldNotMatchNonMatchingJson() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"wrong_value\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
    }

    @Test
    public void shouldMatchJsonForIncorrectArrayOrder() {
        // given
        String matched = "{id:1,pets:[\"dog\",\"cat\",\"fish\"]}";

        // then
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "{id:1,pets:[\"cat\",\"dog\",\"fish\"]}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
    }

    @Test
    public void shouldNotMatchJsonForIncorrectArrayOrder() {
        // given
        String matched = "{id:1,pets:[\"dog\",\"cat\",\"fish\"]}";

        // then
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "{id:1,pets:[\"cat\",\"dog\",\"fish\"]}", MatchType.STRICT).matches(null, matched));
    }

    @Test
    public void shouldMatchJsonForExtraField() {
        // given
        String matched = "{id:1,pets:[\"dog\",\"cat\",\"fish\"],extraField:\"extraValue\"}";

        // then
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "{id:1,pets:[\"dog\",\"cat\",\"fish\"]}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
    }

    @Test
    public void shouldNotMatchJsonForExtraField() {
        // given
        String matched = "{id:1,pets:[\"dog\",\"cat\",\"fish\"],extraField:\"extraValue\"}";

        // then
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "{id:1,pets:[\"dog\",\"cat\",\"fish\"]}", MatchType.STRICT).matches(null, matched));
    }

    @Test
    public void shouldNotMatchNonMatchingSubJson() {
        // given
        String matched = "" +
            "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"File\"," + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}";

        // then
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"id\": \"file\"," + NEW_LINE +
            "        \"value\": \"other_value\"" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "{" + NEW_LINE +
            "    \"menu\": {" + NEW_LINE +
            "        \"popup\": {" + NEW_LINE +
            "            \"menuitem\": [" + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"New\"," + NEW_LINE +
            "                    \"onclick\": \"CreateNewDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Open\"," + NEW_LINE +
            "                    \"onclick\": \"OpenDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }, " + NEW_LINE +
            "                {" + NEW_LINE +
            "                    \"value\": \"Close\"," + NEW_LINE +
            "                    \"onclick\": \"CloseDoc()\"" + NEW_LINE +
            "                }" + NEW_LINE +
            "            ]" + NEW_LINE +
            "        }" + NEW_LINE +
            "    }" + NEW_LINE +
            "}", MatchType.ONLY_MATCHING_FIELDS).matches(null, matched));
    }

    @Test
    public void shouldNotMatchNullTest() {
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "some_value", MatchType.ONLY_MATCHING_FIELDS).matches(null, null));
    }

    @Test
    public void shouldNotMatchEmptyTest() {
        assertFalse(new JsonStringMatcher(new MockServerLogger(), "some_value", MatchType.ONLY_MATCHING_FIELDS).matches(null, ""));
    }

    @Test
    public void shouldMatchBasicValues() {
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "null", MatchType.ONLY_MATCHING_FIELDS).matches(null, "null"));
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "1", MatchType.ONLY_MATCHING_FIELDS).matches(null, "1"));
        assertTrue(new JsonStringMatcher(new MockServerLogger(), "true", MatchType.ONLY_MATCHING_FIELDS).matches(null, "true"));
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        MockServerLogger mockServerLogger = new MockServerLogger();
        assertEquals(new JsonStringMatcher(mockServerLogger, "some_value", MatchType.ONLY_MATCHING_FIELDS), new JsonStringMatcher(mockServerLogger, "some_value", MatchType.ONLY_MATCHING_FIELDS));
    }
}

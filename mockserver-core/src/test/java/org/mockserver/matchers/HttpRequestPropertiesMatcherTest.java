package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.model.*;

import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.jar.Attributes.Name.CONTENT_TYPE;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.NotMatcher.notMatcher;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Cookie.schemaCookie;
import static org.mockserver.model.Header.schemaHeader;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonPathBody.jsonPath;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.KeyMatchStyle.MATCHING_KEY;
import static org.mockserver.model.KeyMatchStyle.SUB_SET;
import static org.mockserver.model.Not.not;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.Parameter.schemaParam;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.ParameterStyle.*;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.model.XmlSchemaBody.xmlSchema;

/**
 * @author jamesdbloom
 */
public class HttpRequestPropertiesMatcherTest {

    /**
     * Test Pattern For Fields:
     * - Nottable Matcher
     * - KeepAlive
     * - SSL
     * - Method
     * - Path
     * - PathParameters
     * - QueryStringParameters
     * - Headers
     * - Cookies
     * - Body:
     * - BinaryBody
     * - JsonBody
     * - JsonPathBody
     * - JsonSchemaBody
     * - ParameterBody
     * - RegexBody
     * - StringBody
     * - XPathBody
     * - XMLBody
     * - XMLSchemaBody
     * Then:
     * - simple
     * - regex
     * - regex control plane
     * - schema
     * - schema control plane
     */

    private final MockServerLogger mockServerLogger = new MockServerLogger(HttpRequestPropertiesMatcherTest.class);

    HttpRequestPropertiesMatcher update(RequestDefinition requestDefinition) {
        HttpRequestPropertiesMatcher httpRequestPropertiesMatcher = new HttpRequestPropertiesMatcher(mockServerLogger);
        httpRequestPropertiesMatcher.update(new Expectation(requestDefinition));
        return httpRequestPropertiesMatcher;
    }

    HttpRequestPropertiesMatcher updateForControlPlane(RequestDefinition requestDefinition) {
        HttpRequestPropertiesMatcher httpRequestPropertiesMatcher = new HttpRequestPropertiesMatcher(mockServerLogger);
        httpRequestPropertiesMatcher.update(requestDefinition);
        return httpRequestPropertiesMatcher;
    }

    // NOTTED MATCHER

    @Test
    public void shouldMatchWithNottedMatcher() {
        // requests match - matcher HttpRequest notted
        assertFalse(update(not(new HttpRequest().withMethod("HEAD"))).matches(null, new HttpRequest().withMethod("HEAD")));

        // requests match - matched HttpRequest notted
        assertFalse(update(new HttpRequest().withMethod("HEAD")).matches(null, not(new HttpRequest().withMethod("HEAD"))));

        // requests match - matcher HttpRequest notted & HttpRequestMatch notted
        assertTrue(notMatcher(update(not(new HttpRequest().withMethod("HEAD")))).matches(null, new HttpRequest().withMethod("HEAD")));

        // requests match - matched HttpRequest notted & HttpRequestMatch notted
        assertTrue(notMatcher(update(new HttpRequest().withMethod("HEAD"))).matches(null, not(new HttpRequest().withMethod("HEAD"))));

        // requests match - matcher HttpRequest notted & matched HttpRequest notted & HttpRequestMatch notted
        assertFalse(notMatcher(update(not(new HttpRequest().withMethod("HEAD")))).matches(null, not(new HttpRequest().withMethod("HEAD"))));
    }

    @Test
    public void shouldNotMatchWithNottedMatcher() {
        // requests don't match - matcher HttpRequest notted
        assertTrue(update(not(new HttpRequest().withMethod("HEAD"))).matches(null, new HttpRequest().withMethod("OPTIONS")));

        // requests don't match - matched HttpRequest notted
        assertTrue(update(new HttpRequest().withMethod("HEAD")).matches(null, not(new HttpRequest().withMethod("OPTIONS"))));

        // requests don't match - matcher HttpRequest notted & HttpRequestMatch notted
        assertFalse(notMatcher(update(not(new HttpRequest().withMethod("HEAD")))).matches(null, new HttpRequest().withMethod("OPTIONS")));

        // requests don't match - matched HttpRequest notted & HttpRequestMatch notted
        assertFalse(notMatcher(update(new HttpRequest().withMethod("HEAD"))).matches(null, not(new HttpRequest().withMethod("OPTIONS"))));

        // requests don't match - matcher HttpRequest notted & matched HttpRequest notted & HttpRequestMatch notted
        assertTrue(notMatcher(update(not(new HttpRequest().withMethod("HEAD")))).matches(null, not(new HttpRequest().withMethod("OPTIONS"))));
    }

    // KEEP ALIVE

    @Test
    public void shouldMatchKeepAlive() {
        assertTrue(update(new HttpRequest().withKeepAlive(true)).matches(null, new HttpRequest().withKeepAlive(true)));
        assertTrue(update(new HttpRequest().withKeepAlive(false)).matches(null, new HttpRequest().withKeepAlive(false)));
        assertTrue(update(new HttpRequest().withKeepAlive(null)).matches(null, new HttpRequest().withKeepAlive(null)));
        assertTrue(update(new HttpRequest().withKeepAlive(null)).matches(null, new HttpRequest().withKeepAlive(false)));
        assertTrue(update(new HttpRequest().withKeepAlive(null)).matches(null, new HttpRequest()));
        assertTrue(update(new HttpRequest()).matches(null, new HttpRequest().withKeepAlive(null)));
    }

    @Test
    public void shouldNotMatchKeepAlive() {
        assertFalse(update(new HttpRequest().withKeepAlive(true)).matches(null, new HttpRequest().withKeepAlive(false)));
        assertFalse(update(new HttpRequest().withKeepAlive(false)).matches(null, new HttpRequest().withKeepAlive(true)));
        assertFalse(update(new HttpRequest().withKeepAlive(true)).matches(null, new HttpRequest().withKeepAlive(null)));
        assertFalse(update(new HttpRequest().withKeepAlive(false)).matches(null, new HttpRequest().withKeepAlive(null)));
    }

    @Test
    public void shouldMatchKeepAliveForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withKeepAlive(true)).matches(null, new HttpRequest().withKeepAlive(true)));
        assertTrue(updateForControlPlane(new HttpRequest().withKeepAlive(false)).matches(null, new HttpRequest().withKeepAlive(false)));
        assertTrue(updateForControlPlane(new HttpRequest().withKeepAlive(null)).matches(null, new HttpRequest().withKeepAlive(null)));
        assertTrue(updateForControlPlane(new HttpRequest().withKeepAlive(null)).matches(null, new HttpRequest().withKeepAlive(false)));
        assertTrue(updateForControlPlane(new HttpRequest().withKeepAlive(null)).matches(null, new HttpRequest()));
        assertTrue(updateForControlPlane(new HttpRequest()).matches(null, new HttpRequest().withKeepAlive(null)));
    }

    @Test
    public void shouldNotMatchKeepAliveForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withKeepAlive(true)).matches(null, new HttpRequest().withKeepAlive(false)));
        assertFalse(updateForControlPlane(new HttpRequest().withKeepAlive(false)).matches(null, new HttpRequest().withKeepAlive(true)));
        assertFalse(updateForControlPlane(new HttpRequest().withKeepAlive(true)).matches(null, new HttpRequest().withKeepAlive(null)));
        assertFalse(updateForControlPlane(new HttpRequest().withKeepAlive(false)).matches(null, new HttpRequest().withKeepAlive(null)));
    }

    // SSL

    @Test
    public void shouldMatchSsl() {
        assertTrue(update(new HttpRequest().withSecure(true)).matches(null, new HttpRequest().withSecure(true)));
        assertTrue(update(new HttpRequest().withSecure(false)).matches(null, new HttpRequest().withSecure(false)));
        assertTrue(update(new HttpRequest().withSecure(null)).matches(null, new HttpRequest().withSecure(null)));
        assertTrue(update(new HttpRequest().withSecure(null)).matches(null, new HttpRequest().withSecure(false)));
        assertTrue(update(new HttpRequest().withSecure(null)).matches(null, new HttpRequest()));
        assertTrue(update(new HttpRequest()).matches(null, new HttpRequest().withSecure(null)));
    }

    @Test
    public void shouldNotMatchSsl() {
        assertFalse(update(new HttpRequest().withSecure(true)).matches(null, new HttpRequest().withSecure(false)));
        assertFalse(update(new HttpRequest().withSecure(false)).matches(null, new HttpRequest().withSecure(true)));
        assertFalse(update(new HttpRequest().withSecure(true)).matches(null, new HttpRequest().withSecure(null)));
        assertFalse(update(new HttpRequest().withSecure(false)).matches(null, new HttpRequest().withSecure(null)));
    }

    @Test
    public void shouldMatchSslForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withSecure(true)).matches(null, new HttpRequest().withSecure(true)));
        assertTrue(updateForControlPlane(new HttpRequest().withSecure(false)).matches(null, new HttpRequest().withSecure(false)));
        assertTrue(updateForControlPlane(new HttpRequest().withSecure(null)).matches(null, new HttpRequest().withSecure(null)));
        assertTrue(updateForControlPlane(new HttpRequest().withSecure(null)).matches(null, new HttpRequest().withSecure(false)));
        assertTrue(updateForControlPlane(new HttpRequest().withSecure(null)).matches(null, new HttpRequest()));
        assertTrue(updateForControlPlane(new HttpRequest()).matches(null, new HttpRequest().withSecure(null)));
    }

    @Test
    public void shouldNotMatchSslForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withSecure(true)).matches(null, new HttpRequest().withSecure(false)));
        assertFalse(updateForControlPlane(new HttpRequest().withSecure(false)).matches(null, new HttpRequest().withSecure(true)));
        assertFalse(updateForControlPlane(new HttpRequest().withSecure(true)).matches(null, new HttpRequest().withSecure(null)));
        assertFalse(updateForControlPlane(new HttpRequest().withSecure(false)).matches(null, new HttpRequest().withSecure(null)));
    }

    // METHOD

    @Test
    public void shouldMatchMethod() {
        assertTrue(update(new HttpRequest().withMethod(
            "HEAD"
        )).matches(null, new HttpRequest().withMethod(
            "HEAD"
        )));
    }

    @Test
    public void shouldNotMatchMethod() {
        assertFalse(update(new HttpRequest().withMethod(
            "HEAD"
        )).matches(null, new HttpRequest().withMethod(
            "OPTIONS"
        )));
    }

    @Test
    public void shouldMatchMethodWithRegex() {
        assertTrue(update(new HttpRequest().withMethod(
            "P[A-Z]{2}"
        )).matches(null, new HttpRequest().withMethod(
            "PUT"
        )));
    }

    @Test
    public void shouldNotMatchMethodWithRegex() {
        assertFalse(update(new HttpRequest().withMethod(
            "P[A-Z]{2}"
        )).matches(null, new HttpRequest().withMethod(
            "POST"
        )));
    }

    @Test
    public void shouldMatchMethodWithRegexForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withMethod(
            "PUT"
        )).matches(null, new HttpRequest().withMethod(
            "P[A-Z]{2}"
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withMethod(
            "P[A-Z]{2}"
        )).matches(null, new HttpRequest().withMethod(
            "P[A-Z]{2}"
        )));
        assertFalse(update(new HttpRequest().withMethod(
            "PUT"
        )).matches(null, new HttpRequest().withMethod(
            "P[A-Z]{2}"
        )));
    }

    @Test
    public void shouldNotMatchMethodWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withMethod(
            "POST"
        )).matches(null, new HttpRequest().withMethod(
            "P[A-Z]{2}"
        )));
    }

    @Test
    public void shouldMatchMethodWithSchema() {
        assertTrue(update(new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )).matches(null, new HttpRequest().withMethod(
            "POST"
        )));
        assertTrue(update(new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )).matches(null, new HttpRequest().withMethod(
            "PUT"
        )));
    }

    @Test
    public void shouldNotMatchMethodWithSchema() {
        assertFalse(update(new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )).matches(null, new HttpRequest().withMethod(
            "GET"
        )));
        assertFalse(update(new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )).matches(null, new HttpRequest().withMethod(
            "HEAD"
        )));
    }

    @Test
    public void shouldMatchMethodWithSchemaForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withMethod(
            "POST"
        )).matches(null, new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withMethod(
            "PUT"
        )).matches(null, new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )).matches(null, new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )));
        assertFalse(update(new HttpRequest().withMethod(
            "POST"
        )).matches(null, new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )));
        assertFalse(update(new HttpRequest().withMethod(
            "PUT"
        )).matches(null, new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )));
        assertFalse(update(new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )).matches(null, new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )));
    }

    @Test
    public void shouldNotMatchMethodWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withMethod(
            "GET"
        )).matches(null, new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withMethod(
            "HEAD"
        )).matches(null, new HttpRequest().withMethodSchema(
            "{ \"type\": \"string\", \"pattern\": \"^P.{2,3}$\" }"
        )));
    }

    // PATH

    @Test
    public void shouldMatchPath() {
        assertTrue(update(new HttpRequest().withPath(
            "somePath"
        )).matches(null, new HttpRequest().withPath(
            "somePath"
        )));
    }

    @Test
    public void shouldNotMatchPath() {
        assertFalse(update(new HttpRequest().withPath(
            "somePath"
        )).matches(null, new HttpRequest().withPath(
            "someOtherPath"
        )));
    }

    @Test
    public void shouldMatchEncodedPath() {
        assertTrue(update(new HttpRequest().withPath(
            "/dWM%2FdWM+ZA=="
        )).matches(null, new HttpRequest().withPath(
            "/dWM%2FdWM+ZA=="
        )));
    }

    @Test
    public void shouldNotMatchEncodedPath() {
        assertFalse(update(new HttpRequest().withPath(
            "/dWM%2FdWM+ZA=="
        )).matches(null, new HttpRequest().withPath(
            "/dWM/dWM+ZA=="
        )));
        assertFalse(update(new HttpRequest().withPath(
            "/dWM/dWM+ZA=="
        )).matches(null, new HttpRequest().withPath(
            "/dWM%2FdWM+ZA=="
        )));
    }

    @Test
    public void shouldMatchPathWithRegex() {
        assertTrue(update(new HttpRequest().withPath(
            "someP[a-z]{3}"
        )).matches(null, new HttpRequest().withPath(
            "somePath"
        )));
    }

    @Test
    public void shouldNotMatchPathWithRegex() {
        assertFalse(update(new HttpRequest().withPath(
            "someP[a-z]{2}"
        )).matches(null, new HttpRequest().withPath(
            "somePath"
        )));
    }

    @Test
    public void shouldMatchPathWithRegexForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withPath(
            "somePath"
        )).matches(null, new HttpRequest().withPath(
            "someP[a-z]{3}"
        )));
        assertFalse(update(new HttpRequest().withPath(
            "somePath"
        )).matches(null, new HttpRequest().withPath(
            "someP[a-z]{3}"
        )));
    }

    @Test
    public void shouldNotMatchPathWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withPath(
            "somePath"
        )).matches(null, new HttpRequest().withPath(
            "someP[a-z]{2}"
        )));
    }

    @Test
    public void shouldMatchPathWithSchema() {
        assertTrue(update(new HttpRequest().withPathSchema(
            "{ \"type\": \"string\", \"pattern\": \"^somePa.{2}$\" }"
        )).matches(null, new HttpRequest().withPath(
            "somePath"
        )));
    }

    @Test
    public void shouldNotMatchPathWithSchema() {
        assertFalse(update(new HttpRequest().withPathSchema(
            "{ \"type\": \"string\", \"pattern\": \"^somePa.{2}$\" }"
        )).matches(null, new HttpRequest().withPath(
            "someOtherPath"
        )));
    }

    @Test
    public void shouldMatchPathWithSchemaForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withPath(
            "somePath"
        )).matches(null, new HttpRequest().withPathSchema(
            "{ \"type\": \"string\", \"pattern\": \"^somePa.{2}$\" }"
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withPathSchema(
            "{ \"type\": \"string\", \"pattern\": \"^somePa.{2}$\" }"
        )).matches(null, new HttpRequest().withPathSchema(
            "{ \"type\": \"string\", \"pattern\": \"^somePa.{2}$\" }"
        )));
        assertFalse(update(new HttpRequest().withPath(
            "somePath"
        )).matches(null, new HttpRequest().withPathSchema(
            "{ \"type\": \"string\", \"pattern\": \"^somePa.{2}$\" }"
        )));
        assertFalse(update(new HttpRequest().withPathSchema(
            "{ \"type\": \"string\", \"pattern\": \"^somePa.{2}$\" }"
        )).matches(null, new HttpRequest().withPathSchema(
            "{ \"type\": \"string\", \"pattern\": \"^somePa.{2}$\" }"
        )));
    }

    @Test
    public void shouldNotMatchPathWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withPath(
            "someOtherPath"
        )).matches(null, new HttpRequest().withPathSchema(
            "{ \"type\": \"string\", \"pattern\": \"^somePa.{2}$\" }"
        )));
    }

    // PATH PARAMETERS

    @Test
    public void shouldMatchPathParameterInPath() {
        assertTrue(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
        ));
        assertTrue(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValueOne", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValueOne,someValueTwo"
            )
        ));
        assertTrue(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueTwo")
            )
        ));
        assertTrue(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValueOne", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValueOne,someValueTwo"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueOne", "someValueTwo")
            )
        ));
    }

    @Test
    public void shouldMatchNottedPathParameterInPath() {
        assertTrue(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "!someValue")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ));
        assertTrue(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "!someValueOne", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someOtherValueOne,someValueTwo"
            )
        ));
        assertTrue(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValueOne", "!someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValueOne,someOtherValueTwo"
            )
        ));
    }

    @Test
    public void shouldNotMatchPathParameterKeyInPath() {
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueTwo")
            )
        ));
    }

    @Test
    public void shouldNotMatchPathParameterValueInPath() {
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueTwo")
            )
        ));
    }

    @Test
    public void shouldNotMatchNottedPathParameterInPath() {
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "!someValue")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "!someValueOne", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValueOne,someValueTwo"
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValueOne", "!someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValueOne,someValueTwo"
            )
        ));
    }

    @Test
    public void shouldMatchPathParameterInParameterObject() {
        assertTrue(update(new HttpRequest().withPathParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withPathParameter(
            new Parameter("someKey", "someValue")
        )));
        assertTrue(update(new HttpRequest().withPathParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withPathParameter(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )));
        assertTrue(update(new HttpRequest().withPathParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withPathParameters(
            new Parameter("someKey", "someValue"),
            new Parameter("someKeyTwo", "someValueTwo")
        )));
        assertTrue(update(new HttpRequest().withPathParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withPathParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo"),
            new Parameter("someKeyTwo", "someValueOne", "someValueTwo")
        )));
    }

    @Test
    public void shouldMatchPathParameterInPathForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ));
        assertTrue(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someValueOne,someValueTwo"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValueOne", "someValueTwo")
            )
        ));
        assertTrue(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ));
        assertTrue(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someValueOne,someValueTwo"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueOne", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValueOne", "someValueTwo")
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/someValueOne,someValueTwo"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValueOne", "someValueTwo")
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/someValueOne,someValueTwo"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueOne", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValueOne", "someValueTwo")
            )
        ));
    }

    @Test
    public void shouldNotMatchPathParameterKeyInPathForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ));
        assertFalse(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ));
        assertFalse(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchPathParameterValueInPathForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ));
        assertFalse(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
            .withPathParameters(
                new Parameter("someKeyTwo", "someValueTwo")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someKey", "someValue")
            )
        ));
    }

    @Test
    public void shouldMatchPathParameterWithRegexInPath() {
        assertTrue(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
        ));
    }

    @Test
    public void shouldNotMatchPathParameterValueWithRegexInPath() {
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ));
    }

    @Test
    public void shouldMatchPathParameterWithRegexInParameterObject() {
        assertTrue(update(new HttpRequest().withPathParameters(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withPathParameter(
            new Parameter("someKey", "someValue")
        )));
    }

    @Test
    public void shouldMatchPathParameterWithRegexInPathForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
            )
        ));
    }

    @Test
    public void shouldNotMatchPathParameterValueWithRegexInPathForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
            )
        ));
    }

    @Test
    public void shouldMatchPathParameterWithSchemaInPathObject() {
        assertTrue(update(new HttpRequest().withPathParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withPathParameters(
            new Parameter("someKey", "someValue")
        )));
    }

    @Test
    public void shouldMatchPathParameterWithSchemaInParameterObject() {
        assertTrue(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
        ));
    }

    @Test
    public void shouldNotMatchPathParameterWithSchemaInParameterObject() {
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ));
    }

    @Test
    public void shouldMatchPathParameterWithSchemaInPathObjectForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
            )
        ));
        assertFalse(update(new HttpRequest()
            .withPath(
                "/some/path/someValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
            )
        ));
    }

    @Test
    public void shouldNotMatchPathParameterWithSchemaInPathObjectForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest()
            .withPath(
                "/some/path/someOtherValue"
            )
        ).matches(null, new HttpRequest()
            .withPath(
                "/some/path/{someKey}"
            )
            .withPathParameters(
                schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
            )
        ));
    }

    // QUERY STRING PARAMETERS

    @Test
    public void shouldMatchQueryStringParameter() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someValue")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue"),
            new Parameter("someKeyTwo", "someValueTwo")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo"),
            new Parameter("someKeyTwo", "someValueOne", "someValueTwo")
        )));
    }

    @Test
    public void shouldMatchQueryStringFormStyleParameter() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1,1,1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1,1,1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM),
                new Parameter("someKeyTwo", "a")
                    .withStyle(FORM)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "1,1,1"),
            new Parameter("someKeyTwo", "a,a,a")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1,1,1", "1,1,1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1,2,3", "1,2,3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM)
            ).withKeyMatchStyle(SUB_SET)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1,2,3", "1,2,3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM)
            )
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1,2,3", "1,2,3")
        )));
    }

    @Test
    public void shouldMatchQueryStringFormExpandedStyleParameter() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "1", "1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "1", "1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM_EXPLODED),
                new Parameter("someKeyTwo", "a")
                    .withStyle(FORM_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "1", "1", "1"),
            new Parameter("someKeyTwo", "a", "a", "a")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "1", "1", "1", "1", "1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM_EXPLODED)
            ).withKeyMatchStyle(SUB_SET)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(FORM_EXPLODED)
            )
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        )));
    }

    @Test
    public void shouldMatchQueryStringSpaceDelimitedStyleParameter() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1%201%201")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1+1+1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1 1 1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1%201%201")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1+1+1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1 1 1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED),
                new Parameter("someKeyTwo", "a")
                    .withStyle(SPACE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "1+1+1"),
            new Parameter("someKeyTwo", "a+a+a")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1+1+1", "1+1+1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1+2+3", "1+2+3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            ).withKeyMatchStyle(SUB_SET)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1%202%203", "1%202%203")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            ).withKeyMatchStyle(SUB_SET)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1+2+3", "1+2+3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            ).withKeyMatchStyle(SUB_SET)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1 2 3", "1 2 3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            )
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1%202%203", "1%202%203")
        )));
    }

    @Test
    public void shouldMatchQueryStringSpaceDelimitedExpandedStyleParameter() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "1", "1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "1", "1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED_EXPLODED),
                new Parameter("someKeyTwo", "a")
                    .withStyle(SPACE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "1", "1", "1"),
            new Parameter("someKeyTwo", "a", "a", "a")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "1", "1", "1", "1", "1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(SUB_SET)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED_EXPLODED)
            )
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        )));
    }

    @Test
    public void shouldMatchQueryStringPipeDelimitedStyleParameter() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1|1|1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1|1|1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED),
                new Parameter("someKeyTwo", "a")
                    .withStyle(PIPE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "1|1|1"),
            new Parameter("someKeyTwo", "a|a|a")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1|1|1", "1|1|1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1|2|3", "1|2|3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED)
            ).withKeyMatchStyle(SUB_SET)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1|2|3", "1|2|3")
        )));
    }

    @Test
    public void shouldMatchQueryStringPipeDelimitedExpandedStyleParameter() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "1", "1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "1", "1")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED_EXPLODED),
                new Parameter("someKeyTwo", "a")
                    .withStyle(PIPE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "1", "1", "1"),
            new Parameter("someKeyTwo", "a", "a", "a")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "1", "1", "1", "1", "1")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
            ).withKeyMatchStyle(MATCHING_KEY)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED_EXPLODED)
            ).withKeyMatchStyle(SUB_SET)
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameters(
                new Parameter("someKey", "1")
                    .withStyle(PIPE_DELIMITED_EXPLODED)
            )
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        )));
    }

    @Test
    public void shouldMatchEmptyQueryStringParameter() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someValue")
        )));
    }

    @Test
    public void shouldMatchNottedQueryStringParameter() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "!someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someOtherValue")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "!someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someOtherValueOne", "someOtherValueTwo")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameter("!someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someValue")
        )));
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameter("!someKey", "!someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someOtherValue"),
            new Parameter("notSomeKey", "someOtherValueOne", "someOtherValueTwo"),
            new Parameter("someOtherKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("!someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someOtherValue"),
            new Parameter("someKey", "someOtherValueOne", "someOtherValueTwo"),
            new Parameter("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKey() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someOtherKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someValue"),
            new Parameter("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterValue() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someOtherValue")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someOtherValueOne", "someValueTwo")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someValueOne", "someOtherValueTwo")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someOtherValue"),
            new Parameter("someKeyTwo", "someValueTwo")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someOtherValueOne", "someValueTwo"),
            new Parameter("someKeyTwo", "someValueTwoOne", "someValueTwoTwo")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValueOne", "someOtherValueTwo"),
            new Parameter("someKeyOther", "someValueTwoOne", "someValueTwoTwo")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKeyAndValue() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someOtherKey", "someOtherValue")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someOtherValue"),
            new Parameter("someOtherKeyTwo", "someOtherValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchNottedQueryStringParameterKey() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("!someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("!someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue"),
            new Parameter("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchNottedQueryStringParameterValue() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "!someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "!someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue"),
            new Parameter("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchNottedQueryStringParameterKeyAndValue() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("!someKey", "!someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("!someKey", "!someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someOtherValue"),
            new Parameter("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchEmptyQueryStringParameterForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
        )));
    }

    @Test
    public void shouldMatchQueryStringParameterWithRegex() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKeyWithRegex() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterValueWithRegex() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKeyAndValueWithRegex() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someOtherKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldMatchQueryStringParameterWithRegexForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKeyWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterValueWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someOtherValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKeyAndValueWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someOtherValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldMatchQueryStringParameterWithSchema() {
        assertTrue(update(new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKeyWithSchema() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterValueWithSchema() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKeyAndValueWithSchema() {
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            new Parameter("someOtherKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldMatchQueryStringParameterWithSchemaForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
        assertFalse(update(new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKeyWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterValueWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someKey", "someOtherValue")
        )).matches(null, new HttpRequest().withQueryStringParameters(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    @Test
    public void shouldNotMatchQueryStringParameterKeyAndValueWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withQueryStringParameters(
            new Parameter("someOtherKey", "someOtherValue")
        )).matches(null, new HttpRequest().withQueryStringParameter(
            schemaParam("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    // HEADERS

    @Test
    public void shouldMatchHeader() {
        assertTrue(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someValue")
        )));
        assertTrue(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someValueOne", "someValueTwo")
        )));
        assertTrue(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someValue"),
            new Header("someKeyTwo", "someValueTwo")
        )));
        assertTrue(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someValueOne", "someValueTwo"),
            new Header("someKeyTwo", "someValueOne", "someValueTwo")
        )));
    }

    @Test
    public void shouldMatchEmptyHeader() {
        assertTrue(update(new HttpRequest().withHeaders(
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someValue")
        )));
    }

    @Test
    public void shouldMatchNottedHeader() {
        assertTrue(update(new HttpRequest().withHeaders(
            new Header("someKey", "!someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someOtherValue")
        )));
        assertTrue(update(new HttpRequest().withHeaders(
            new Header("someKey", "!someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someOtherValueOne", "someOtherValueTwo")
        )));
        assertTrue(update(new HttpRequest().withHeaders(
            new Header("!someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someOtherKey", "someValue")
        )));
        assertTrue(update(new HttpRequest().withHeaders(
            new Header("!someKey", "!someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someOtherKey", "someOtherValue"),
            new Header("notSomeKey", "someOtherValueOne", "someOtherValueTwo"),
            new Header("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKey() {
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someOtherKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someOtherKey", "someValue"),
            new Header("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchHeaderValue() {
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someOtherValue")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someOtherValueOne", "someValueTwo")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someValueOne", "someOtherValueTwo")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someOtherValue"),
            new Header("someKeyTwo", "someValueTwo")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someOtherValueOne", "someValueTwo"),
            new Header("someKeyTwo", "someValueTwoOne", "someValueTwoTwo")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValueOne", "someValueTwo")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someValueOne", "someOtherValueTwo"),
            new Header("someKeyOther", "someValueTwoOne", "someValueTwoTwo")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKeyAndValue() {
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someOtherKey", "someOtherValue")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someOtherKey", "someOtherValue"),
            new Header("someOtherKeyTwo", "someOtherValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchNottedHeaderKey() {
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("!someKey", "someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("!someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someValue"),
            new Header("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchNottedHeaderValue() {
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "!someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "!someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someValue"),
            new Header("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchNottedHeaderKeyAndValue() {
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("!someKey", "!someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("!someKey", "!someValue")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someOtherValue"),
            new Header("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchEmptyHeaderForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
        )));
    }

    @Test
    public void shouldMatchHeaderWithRegex() {
        assertTrue(update(new HttpRequest().withHeaders(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKeyWithRegex() {
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchHeaderValueWithRegex() {
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKeyAndValueWithRegex() {
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someOtherKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldMatchHeaderWithRegexForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKeyWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someOtherKey", "someValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldNotMatchHeaderValueWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someKey", "someOtherValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKeyAndValueWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someOtherKey", "someOtherValue")
        )).matches(null, new HttpRequest().withHeader(
            new Header("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldMatchHeaderWithSchema() {
        assertTrue(update(new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKeyWithSchema() {
        assertFalse(update(new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchHeaderValueWithSchema() {
        assertFalse(update(new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKeyAndValueWithSchema() {
        assertFalse(update(new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withHeaders(
            new Header("someOtherKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldMatchHeaderWithSchemaForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            new Header("someKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
        assertFalse(update(new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKeyWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someOtherKey", "someValue")
        )).matches(null, new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    @Test
    public void shouldNotMatchHeaderValueWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someKey", "someOtherValue")
        )).matches(null, new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    @Test
    public void shouldNotMatchHeaderKeyAndValueWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withHeaders(
            new Header("someOtherKey", "someOtherValue")
        )).matches(null, new HttpRequest().withHeaders(
            schemaHeader("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    // COOKIES

    @Test
    public void shouldMatchCookie() {
        assertTrue(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someValue")
        )));
        assertTrue(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookies(
            new Cookie("someKey", "someValue"),
            new Cookie("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldMatchEmptyCookie() {
        assertTrue(update(new HttpRequest().withCookies(
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someValue")
        )));
    }

    @Test
    public void shouldMatchNottedCookie() {
        assertTrue(update(new HttpRequest().withCookies(
            new Cookie("someKey", "!someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someOtherValue")
        )));
        assertTrue(update(new HttpRequest().withCookies(
            new Cookie("!someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someOtherKey", "someValue")
        )));
        assertTrue(update(new HttpRequest().withCookies(
            new Cookie("!someKey", "!someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someOtherKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldNotMatchCookieValue() {
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someOtherValue")
        )));
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookies(
            new Cookie("someKey", "someOtherValue"),
            new Cookie("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchCookieKey() {
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someOtherKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookies(
            new Cookie("someOtherKey", "someValue"),
            new Cookie("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchCookieKeyAndValue() {
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someOtherKey", "someOtherValue")
        )));
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookies(
            new Cookie("someOtherKey", "someOtherValue"),
            new Cookie("someOtherKeyTwo", "someOtherValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchNottedCookieKey() {
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("!someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("!someKey", "someValue")
        )).matches(null, new HttpRequest().withCookies(
            new Cookie("someKey", "someValue"),
            new Cookie("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchNottedCookieValue() {
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "!someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "!someValue")
        )).matches(null, new HttpRequest().withCookies(
            new Cookie("someKey", "someValue"),
            new Cookie("someKeyTwo", "someValueTwo")
        )));
    }

    @Test
    public void shouldNotMatchNottedCookieKeyAndValue() {
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("!someKey", "!someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someValue")
        )));
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("!someKey", "!someValue")
        )).matches(null, new HttpRequest().withCookies(
            new Cookie("someKey", "someOtherValue"),
            new Cookie("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchEmptyCookieForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookies(
        )));
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookies(
        )));
    }

    @Test
    public void shouldMatchCookieWithRegex() {
        assertTrue(update(new HttpRequest().withCookies(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchCookieKeyWithRegex() {
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchCookieValueWithRegex() {
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldNotMatchCookieKeyAndValueWithRegex() {
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someOtherKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldMatchCookieWithRegexForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )));
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldNotMatchCookieKeyWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someOtherKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldNotMatchCookieValueWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someKey", "someOtherValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldNotMatchCookieKeyAndValueWithRegexForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someOtherKey", "someOtherValue")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someK[a-z]{2}", "someV[a-z]{4}")
        )));
    }

    @Test
    public void shouldMatchCookieWithSchema() {
        assertTrue(update(new HttpRequest().withCookies(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchCookieKeyWithSchema() {
        assertFalse(update(new HttpRequest().withCookies(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someOtherKey", "someValue")
        )));
    }

    @Test
    public void shouldNotMatchCookieValueWithSchema() {
        assertFalse(update(new HttpRequest().withCookies(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldNotMatchCookieKeyAndValueWithSchema() {
        assertFalse(update(new HttpRequest().withCookies(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withCookie(
            new Cookie("someOtherKey", "someOtherValue")
        )));
    }

    @Test
    public void shouldMatchCookieWithSchemaForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withCookies(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withCookie(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
        assertFalse(update(new HttpRequest().withCookies(
            new Cookie("someKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
        assertFalse(update(new HttpRequest().withCookies(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )).matches(null, new HttpRequest().withCookie(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    @Test
    public void shouldNotMatchCookieKeyWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someOtherKey", "someValue")
        )).matches(null, new HttpRequest().withCookie(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    @Test
    public void shouldNotMatchCookieValueWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someKey", "someOtherValue")
        )).matches(null, new HttpRequest().withCookie(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    @Test
    public void shouldNotMatchCookieKeyAndValueWithSchemaForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withCookies(
            new Cookie("someOtherKey", "someOtherValue")
        )).matches(null, new HttpRequest().withCookie(
            schemaCookie("someK[a-z]{2}", "{ \"type\": \"string\", \"pattern\": \"^someV[a-z]{4}$\" }")
        )));
    }

    // BODY

    // - BinaryBody

    @Test
    public void shouldMatchBinaryBody() {
        assertTrue(update(new HttpRequest().withBody(
            binary("some binary value".getBytes(UTF_8))
        )).matches(null, new HttpRequest().withBody(
            "some binary value".getBytes(UTF_8)
        )));
    }

    @Test
    public void shouldMatchNottedBinaryBody() {
        assertTrue(update(new HttpRequest().withBody(
            not(binary("some binary value".getBytes(UTF_8)))
        )).matches(null, new HttpRequest().withBody(
            "some other binary value".getBytes(UTF_8)
        )));
    }

    @Test
    public void shouldMatchBinaryBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            binary("some binary value".getBytes(UTF_8))
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody(
            "some binary value".getBytes(UTF_8)
        )));
        assertTrue(update(new HttpRequest().withBody(
            binary("some binary value".getBytes(UTF_8))
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchBinaryBody() {
        assertFalse(update(new HttpRequest().withBody(
            binary("some binary value".getBytes(UTF_8))
        )).matches(null, new HttpRequest().withBody(
            "some other binary value".getBytes(UTF_8)
        )));
    }

    @Test
    public void shouldNotMatchNottedBinaryBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(binary("some binary value".getBytes(UTF_8)))
        )).matches(null, new HttpRequest().withBody(
            "some binary value".getBytes(UTF_8)
        )));
    }

    @Test
    public void shouldNotMatchBinaryBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            binary("some binary value".getBytes(UTF_8))
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            binary("some binary value".getBytes(UTF_8))
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchBinaryBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            "some binary value".getBytes(UTF_8)
        )).matches(null, new HttpRequest().withBody(
            new BinaryBodyDTO(binary("some binary value".getBytes(UTF_8))).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            "some binary value".getBytes(UTF_8)
        )).matches(null, new HttpRequest().withBody(
            new BinaryBodyDTO(binary("some binary value".getBytes(UTF_8))).toString()
        )));
    }

    @Test
    public void shouldNotMatchBinaryBodyForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "some other binary value".getBytes(UTF_8)
        )).matches(null, new HttpRequest().withBody(
            new BinaryBodyDTO(binary("some binary value".getBytes(UTF_8))).toString()
        )));
    }

    // - JsonBody

    @Test
    public void shouldMatchJsonBody() {
        assertTrue(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"some_value\" }")
        )).matches(null, new HttpRequest().withBody(
            "" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}"
        )));
        assertTrue(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"some_value\" }")
        )).matches(null, new HttpRequest().withBody(
            json("" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}")
        )));
    }

    @Test
    public void shouldMatchNottedJsonBody() {
        assertTrue(update(new HttpRequest().withBody(
            not(json("{ \"some_field\": \"some_value\" }"))
        )).matches(null, new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            "{ \"some_other_field\": \"some_other_value\" }"
        )));
    }

    @Test
    public void shouldMatchJsonBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"some_value\" }")
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody(
            "" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}"
        )));
        assertTrue(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"some_value\" }")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchJsonArrayBody() {
        assertTrue(update(new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ ]" + NEW_LINE +
                "}" + NEW_LINE)
        )).matches(null, new HttpRequest().withBody(
            "" +
                "{" + NEW_LINE +
                "  \"digests\" : [ ]" + NEW_LINE +
                "}" + NEW_LINE
        )));
        assertTrue(update(new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ ]" + NEW_LINE +
                "}" + NEW_LINE)
        )).matches(null, new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ \"sha256:one\" ]" + NEW_LINE +
                "}" + NEW_LINE)
        )));
        assertTrue(update(new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ ]" + NEW_LINE +
                "}" + NEW_LINE)
        )).matches(null, new HttpRequest().withBody(
            "" +
                "{" + NEW_LINE +
                "  \"digests\" : [ \"sha256:one\" ]" + NEW_LINE +
                "}" + NEW_LINE
        )));
    }

    @Test
    public void shouldMatchJsonBodyWithCharset() {
        assertTrue(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"我说中国话\" }", UTF_8, MatchType.ONLY_MATCHING_FIELDS)
        )).matches(null, new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            "" +
                "{ " +
                "   \"some_field\": \"我说中国话\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}", UTF_8
        )));
        assertTrue(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"我说中国话\" }", UTF_8, MatchType.ONLY_MATCHING_FIELDS)
        )).matches(null, new HttpRequest().withBody(
            json("" +
                "{ " +
                "   \"some_field\": \"我说中国话\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}", UTF_8)
        )));
    }

    @Test
    public void shouldNotMatchJsonBody() {
        assertFalse(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"some_value\" }")
        )).matches(null, new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            "{ \"some_other_field\": \"some_other_value\" }"
        )));
        assertFalse(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"some_value\" }")
        )).matches(null, new HttpRequest().withBody(
            json("{ \"some_other_field\": \"some_other_value\" }")
        )));
    }

    @Test
    public void shouldNotMatchNottedJsonBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(json("{ \"some_field\": \"some_value\" }"))
        )).matches(null, new HttpRequest().withBody(
            "" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}"
        )));
    }

    @Test
    public void shouldNotMatchJsonBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"some_value\" }")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"some_value\" }")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchJsonBodyWithCharset() {
        assertFalse(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"我说中国话\" }", UTF_8, MatchType.ONLY_MATCHING_FIELDS)
        )).matches(null, new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            "{ \"some_other_field\": \"我说中国话\" }", UTF_8
        )));
        assertFalse(update(new HttpRequest().withBody(
            json("{ \"some_field\": \"我说中国话\" }", UTF_8, MatchType.ONLY_MATCHING_FIELDS)
        )).matches(null, new HttpRequest().withBody(
            json("{ \"some_other_field\": \"我说中国话\" }", UTF_8)
        )));
    }

    @Test
    public void shouldMatchJsonBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            "" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}"
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"some_value\" }", MediaType.APPLICATION_JSON)).toString()
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            json("" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}", MediaType.APPLICATION_JSON)
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"some_value\" }", MediaType.APPLICATION_JSON)).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            "" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}"
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"some_value\" }", MediaType.APPLICATION_JSON)).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            json("" +
                "{ " +
                "   \"some_field\": \"some_value\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}", MediaType.APPLICATION_JSON)
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"some_value\" }", MediaType.APPLICATION_JSON)).toString()
        )));
    }

    @Test
    public void shouldMatchJsonArrayBodyForControlPlane() {
        // matches without being serialised BodyDTO
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ ]" + NEW_LINE +
                "}" + NEW_LINE)
        )).matches(null, new HttpRequest().withBody(
            "" +
                "{" + NEW_LINE +
                "  \"digests\" : [ \"sha256:one\" ]" + NEW_LINE +
                "}" + NEW_LINE
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ ]" + NEW_LINE +
                "}" + NEW_LINE)
        )).matches(null, new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ \"sha256:one\" ]" + NEW_LINE +
                "}" + NEW_LINE)
        )));
        // matches as serialised BodyDTO for control-plane
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ \"sha256:one\" ]" + NEW_LINE +
                "}" + NEW_LINE, MediaType.APPLICATION_JSON)
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ \"sha256:one\" ]" + NEW_LINE +
                "}" + NEW_LINE, MediaType.APPLICATION_JSON)).toString()
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ \"sha256:one\" ]" + NEW_LINE +
                "}" + NEW_LINE, MediaType.APPLICATION_JSON)
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ ]" + NEW_LINE +
                "}" + NEW_LINE, MediaType.APPLICATION_JSON)).toString()
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ ]" + NEW_LINE +
                "}" + NEW_LINE, MediaType.APPLICATION_JSON)
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ \"sha256:one\" ]" + NEW_LINE +
                "}" + NEW_LINE, MediaType.APPLICATION_JSON)).toString()
        )));
        // doesn't work as serialised BodyDTO for non-control-plane
        assertFalse(update(new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ ]" + NEW_LINE +
                "}" + NEW_LINE, MediaType.APPLICATION_JSON)
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("" +
                "{" + NEW_LINE +
                "  \"digests\" : [ \"sha256:one\" ]" + NEW_LINE +
                "}" + NEW_LINE, MediaType.APPLICATION_JSON)).toString()
        )));
    }

    @Test
    public void shouldMatchJsonBodyWithCharsetForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            "" +
                "{ " +
                "   \"some_field\": \"我说中国话\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}", UTF_16
        )).matches(null, new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"我说中国话\" }", UTF_16, MatchType.ONLY_MATCHING_FIELDS)).toString()
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            json("" +
                "{ " +
                "   \"some_field\": \"我说中国话\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}", UTF_16)
        )).matches(null, new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"我说中国话\" }", UTF_16, MatchType.ONLY_MATCHING_FIELDS)).toString()
        )));
        assertFalse(update(new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            "" +
                "{ " +
                "   \"some_field\": \"我说中国话\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}", UTF_16
        )).matches(null, new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"我说中国话\" }", UTF_16, MatchType.ONLY_MATCHING_FIELDS)).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            json("" +
                "{ " +
                "   \"some_field\": \"我说中国话\", " +
                "   \"some_other_field\": \"some_other_value\" " +
                "}", UTF_16)
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"我说中国话\" }", UTF_16, MatchType.ONLY_MATCHING_FIELDS)).toString()
        )));
    }

    @Test
    public void shouldNotMatchJsonBodyForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "{ \"some_other_field\": \"some_other_value\" }"
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"some_value\" }")).toString()
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            json("{ \"some_other_field\": \"some_other_value\" }")
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"some_value\" }")).toString()
        )));
    }

    @Test
    public void shouldNotMatchJsonBodyWithCharsetForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString()).withBody(
            "{ \"some_other_field\": \"我说中国话\" }", UTF_8
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"我说中国话\" }", UTF_8, MatchType.ONLY_MATCHING_FIELDS)).toString()

        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            json("{ \"some_other_field\": \"我说中国话\" }", UTF_8)
        )).matches(null, new HttpRequest().withBody(
            new JsonBodyDTO(json("{ \"some_field\": \"我说中国话\" }", UTF_8, MatchType.ONLY_MATCHING_FIELDS)).toString()
        )));
    }

    // - JsonPathBody

    @Test
    public void shouldMatchJsonPathBody() {
        assertTrue(update(new HttpRequest().withBody(
            jsonPath("$..book[?(@.price > $['expensive'])]")
        )).matches(null, new HttpRequest().withBody(
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
                "                \"price\": 18.99" + NEW_LINE +
                "            }" + NEW_LINE +
                "        ]," + NEW_LINE +
                "        \"bicycle\": {" + NEW_LINE +
                "            \"color\": \"red\"," + NEW_LINE +
                "            \"price\": 19.95" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"expensive\": 10" + NEW_LINE +
                "}"
        )));
    }

    @Test
    public void shouldMatchNottedJsonPathBody() {
        assertTrue(update(new HttpRequest().withBody(
            not(jsonPath("$..book[?(@.price > $['expensive'])]"))
        )).matches(null, new HttpRequest().withBody(
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
                "            \"price\": 9.95" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"expensive\": 10" + NEW_LINE +
                "}"
        )));
    }

    @Test
    public void shouldMatchJsonPathBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            jsonPath("$..book[?(@.price > $['expensive'])]")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            jsonPath("$..book[?(@.price > $['expensive'])]")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchJsonPathBody() {
        assertFalse(update(new HttpRequest().withBody(
            jsonPath("$..book[?(@.price > $['expensive'])]")
        )).matches(null, new HttpRequest().withBody(
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
                "            \"price\": 9.95" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"expensive\": 10" + NEW_LINE +
                "}"
        )));
    }

    @Test
    public void shouldNotMatchNottedJsonPathBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(jsonPath("$..book[?(@.price > $['expensive'])]"))
        )).matches(null, new HttpRequest().withBody(
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
                "                \"price\": 18.99" + NEW_LINE +
                "            }" + NEW_LINE +
                "        ]," + NEW_LINE +
                "        \"bicycle\": {" + NEW_LINE +
                "            \"color\": \"red\"," + NEW_LINE +
                "            \"price\": 19.95" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"expensive\": 10" + NEW_LINE +
                "}"
        )));
    }

    @Test
    public void shouldNotMatchJsonPathBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            jsonPath("$..book[?(@.price > $['expensive'])]")
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody(
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
                "                \"price\": 18.99" + NEW_LINE +
                "            }" + NEW_LINE +
                "        ]," + NEW_LINE +
                "        \"bicycle\": {" + NEW_LINE +
                "            \"color\": \"red\"," + NEW_LINE +
                "            \"price\": 19.95" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"expensive\": 10" + NEW_LINE +
                "}"
        )));
        assertTrue(update(new HttpRequest().withBody(
            jsonPath("$..book[?(@.price > $['expensive'])]")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchJsonPathBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
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
                "                \"price\": 18.99" + NEW_LINE +
                "            }" + NEW_LINE +
                "        ]," + NEW_LINE +
                "        \"bicycle\": {" + NEW_LINE +
                "            \"color\": \"red\"," + NEW_LINE +
                "            \"price\": 19.95" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"expensive\": 10" + NEW_LINE +
                "}"
        )).matches(null, new HttpRequest().withBody(
            new JsonPathBodyDTO(jsonPath("$..book[?(@.price > $['expensive'])]")).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
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
                "}"
        )).matches(null, new HttpRequest().withBody(
            new JsonPathBodyDTO(jsonPath("$..book[?(@.price > $['expensive'])]")).toString()
        )));
    }

    @Test
    public void shouldNotMatchJsonPathBodyForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
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
                "            \"price\": 9.95" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"expensive\": 10" + NEW_LINE +
                "}"
        )).matches(null, new HttpRequest().withBody(
            new JsonPathBodyDTO(jsonPath("$..book[?(@.price > $['expensive'])]")).toString()
        )));
    }

    // - JsonSchemaBody

    // - with Json

    @Test
    public void shouldMatchJsonSchemaBody() {
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest().withBody(
            "\"someBody\""
        )));
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest().withBody(
            json("\"someBody\"")
        )));
    }

    @Test
    public void shouldMatchNottedJsonSchemaBody() {
        assertTrue(update(new HttpRequest().withBody(
            not(jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}"))
        )).matches(null, new HttpRequest().withBody(
            "\"someOtherBody\""
        )));
    }

    @Test
    public void shouldMatchJsonSchemaBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody(
            "\"someBody\""
        )));
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchJsonSchemaBodyWithComplexSchema() {
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest().withBody(
            "" +
                "{" + NEW_LINE +
                "    \"id\": 1," + NEW_LINE +
                "    \"name\": \"A green door\"," + NEW_LINE +
                "    \"price\": 12.50," + NEW_LINE +
                "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                "}"
        )));
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "    \"id\": 1," + NEW_LINE +
                "    \"name\": \"A green door\"," + NEW_LINE +
                "    \"price\": 12.50," + NEW_LINE +
                "    \"tags\": [\"home\", \"green\"]" + NEW_LINE +
                "}")
        )));
    }

    @Test
    public void shouldNotMatchJsonSchemaBody() {
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest().withBody(
            "\"someOtherBody\""
        )));
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest().withBody(
            json("\"someOtherBody\"")
        )));
    }

    @Test
    public void shouldNotMatchNottedJsonSchemaBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}"))
        )).matches(null, new HttpRequest().withBody(
            "\"someBody\""
        )));
    }

    @Test
    public void shouldNotMatchJsonSchemaBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchJsonSchemaBodyWithComplexSchema() {
        // too few tags in array
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest().withBody(
            "" +
                "{" + NEW_LINE +
                "    \"id\": 1," + NEW_LINE +
                "    \"name\": \"A green door\"," + NEW_LINE +
                "    \"price\": 12.50," + NEW_LINE +
                "    \"tags\": []" + NEW_LINE +
                "}"
        )));
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest().withBody(
            json("" +
                "{" + NEW_LINE +
                "    \"id\": 1," + NEW_LINE +
                "    \"name\": \"A green door\"," + NEW_LINE +
                "    \"price\": 12.50," + NEW_LINE +
                "    \"tags\": []" + NEW_LINE +
                "}")
        )));
    }

    @Test
    public void shouldMatchJsonSchemaBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            "\"someBody\""
        )).matches(null, new HttpRequest().withBody(
            new JsonSchemaBodyDTO(jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")).toString()
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            json("\"someBody\"")
        )).matches(null, new HttpRequest().withBody(
            new JsonSchemaBodyDTO(jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            "\"someBody\""
        )).matches(null, new HttpRequest().withBody(
            new JsonSchemaBodyDTO(jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            json("\"someBody\"")
        )).matches(null, new HttpRequest().withBody(
            new JsonSchemaBodyDTO(jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")).toString()
        )));
    }

    @Test
    public void shouldNotMatchJsonSchemaBodyForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "\"someOtherBody\""
        )).matches(null, new HttpRequest().withBody(
            new JsonSchemaBodyDTO(jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")).toString()
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            json("\"someOtherBody\"")
        )).matches(null, new HttpRequest().withBody(
            new JsonSchemaBodyDTO(jsonSchema("{" + NEW_LINE +
                "   \"type\": \"string\"," + NEW_LINE +
                "   \"pattern\": \"^someB[a-z]{3}$\"" + NEW_LINE +
                "}")).toString()
        )));
    }

    // - with Xml

    @Test
    public void shouldMatchJsonSchemaBodyWithXmlBody() {
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                "" +
                    "<root>" + NEW_LINE +
                    "    <id>1</id>" + NEW_LINE +
                    "    <name>A green door</name>" + NEW_LINE +
                    "    <price>12.5</price>" + NEW_LINE +
                    "    <tags>home</tags>" + NEW_LINE +
                    "    <tags>green</tags>" + NEW_LINE +
                    "</root>"
            )
        ));
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"خانه\", \"سبز\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML_UTF_8)
            .withBody(
                "" +
                    "<root>" + NEW_LINE +
                    "    <id>1</id>" + NEW_LINE +
                    "    <name>یک درب سبز</name>" + NEW_LINE +
                    "    <price>12.5</price>" + NEW_LINE +
                    "    <tags>خانه</tags>" + NEW_LINE +
                    "    <tags>سبز</tags>" + NEW_LINE +
                    "</root>"
            )
        ));
    }

    @Test
    public void shouldMatchNottedJsonSchemaBodyWithXmlBody() {
        // too few tags in array
        assertTrue(update(new HttpRequest().withBody(
            not(jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 3," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}"))
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                "" +
                    "<root>" + NEW_LINE +
                    "    <id>1</id>" + NEW_LINE +
                    "    <name>A green door</name>" + NEW_LINE +
                    "    <price>12.5</price>" + NEW_LINE +
                    "    <tags>home</tags>" + NEW_LINE +
                    "    <tags>green</tags>" + NEW_LINE +
                    "</root>"
            )
        ));
    }

    @Test
    public void shouldMatchJsonSchemaBodyWithXmlBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
                .withOptional(true)
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                "" +
                    "<root>" + NEW_LINE +
                    "    <id>1</id>" + NEW_LINE +
                    "    <name>A green door</name>" + NEW_LINE +
                    "    <price>12.5</price>" + NEW_LINE +
                    "    <tags>home</tags>" + NEW_LINE +
                    "    <tags>green</tags>" + NEW_LINE +
                    "</root>"
            )
        ));
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchJsonSchemaBodyWithXmlBody() {
        // too few tags in array
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 3," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                "" +
                    "<root>" + NEW_LINE +
                    "    <id>1</id>" + NEW_LINE +
                    "    <name>A green door</name>" + NEW_LINE +
                    "    <price>12.5</price>" + NEW_LINE +
                    "    <tags>home</tags>" + NEW_LINE +
                    "    <tags>green</tags>" + NEW_LINE +
                    "</root>"
            )
        ));
    }

    @Test
    public void shouldNotMatchNottedJsonSchemaBodyWithXmlBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}"))
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                "" +
                    "<root>" + NEW_LINE +
                    "    <id>1</id>" + NEW_LINE +
                    "    <name>A green door</name>" + NEW_LINE +
                    "    <price>12.5</price>" + NEW_LINE +
                    "    <tags>home</tags>" + NEW_LINE +
                    "    <tags>green</tags>" + NEW_LINE +
                    "</root>"
            )
        ));
    }

    @Test
    public void shouldNotMatchJsonSchemaBodyWithXmlBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchJsonSchemaBodyWithXmlBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                "" +
                    "<root>" + NEW_LINE +
                    "    <id>1</id>" + NEW_LINE +
                    "    <name>A green door</name>" + NEW_LINE +
                    "    <price>12.5</price>" + NEW_LINE +
                    "    <tags>home</tags>" + NEW_LINE +
                    "    <tags>green</tags>" + NEW_LINE +
                    "</root>"
            )
        ).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                jsonSchema("{" + NEW_LINE +
                    "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                    "    \"title\": \"Product\"," + NEW_LINE +
                    "    \"type\": \"object\"," + NEW_LINE +
                    "    \"properties\": {" + NEW_LINE +
                    "        \"id\": {" + NEW_LINE +
                    "            \"type\": \"integer\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"name\": {" + NEW_LINE +
                    "            \"type\": \"string\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"price\": {" + NEW_LINE +
                    "            \"type\": \"number\"," + NEW_LINE +
                    "            \"minimum\": 0," + NEW_LINE +
                    "            \"exclusiveMinimum\": true" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"tags\": {" + NEW_LINE +
                    "            \"type\": \"array\"," + NEW_LINE +
                    "            \"items\": {" + NEW_LINE +
                    "                \"type\": \"string\"," + NEW_LINE +
                    "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                    "            }," + NEW_LINE +
                    "            \"minItems\": 1," + NEW_LINE +
                    "            \"uniqueItems\": true" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                    "}")
            )
        ));
        assertFalse(update(new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                "" +
                    "<root>" + NEW_LINE +
                    "    <id>1</id>" + NEW_LINE +
                    "    <name>A green door</name>" + NEW_LINE +
                    "    <price>12.5</price>" + NEW_LINE +
                    "    <tags>home</tags>" + NEW_LINE +
                    "    <tags>green</tags>" + NEW_LINE +
                    "</root>"
            )
        ).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                jsonSchema("{" + NEW_LINE +
                    "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                    "    \"title\": \"Product\"," + NEW_LINE +
                    "    \"type\": \"object\"," + NEW_LINE +
                    "    \"properties\": {" + NEW_LINE +
                    "        \"id\": {" + NEW_LINE +
                    "            \"type\": \"integer\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"name\": {" + NEW_LINE +
                    "            \"type\": \"string\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"price\": {" + NEW_LINE +
                    "            \"type\": \"number\"," + NEW_LINE +
                    "            \"minimum\": 0," + NEW_LINE +
                    "            \"exclusiveMinimum\": true" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"tags\": {" + NEW_LINE +
                    "            \"type\": \"array\"," + NEW_LINE +
                    "            \"items\": {" + NEW_LINE +
                    "                \"type\": \"string\"," + NEW_LINE +
                    "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                    "            }," + NEW_LINE +
                    "            \"minItems\": 1," + NEW_LINE +
                    "            \"uniqueItems\": true" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                    "}")
            )
        ));
    }

    @Test
    public void shouldNotMatchJsonSchemaBodyWithXmlBodyForControlPlane() {
        // too few tags in array
        assertFalse(updateForControlPlane(new HttpRequest()
            .withContentType(MediaType.APPLICATION_XML)
            .withBody(
                "" +
                    "<root>" + NEW_LINE +
                    "    <id>1</id>" + NEW_LINE +
                    "    <name>A green door</name>" + NEW_LINE +
                    "    <price>12.5</price>" + NEW_LINE +
                    "    <tags>home</tags>" + NEW_LINE +
                    "    <tags>green</tags>" + NEW_LINE +
                    "</root>"
            )
        ).matches(null, new HttpRequest()
            .withBody(
                jsonSchema("{" + NEW_LINE +
                    "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                    "    \"title\": \"Product\"," + NEW_LINE +
                    "    \"type\": \"object\"," + NEW_LINE +
                    "    \"properties\": {" + NEW_LINE +
                    "        \"id\": {" + NEW_LINE +
                    "            \"type\": \"integer\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"name\": {" + NEW_LINE +
                    "            \"type\": \"string\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"price\": {" + NEW_LINE +
                    "            \"type\": \"number\"," + NEW_LINE +
                    "            \"minimum\": 0," + NEW_LINE +
                    "            \"exclusiveMinimum\": true" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"tags\": {" + NEW_LINE +
                    "            \"type\": \"array\"," + NEW_LINE +
                    "            \"items\": {" + NEW_LINE +
                    "                \"type\": \"string\"," + NEW_LINE +
                    "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                    "            }," + NEW_LINE +
                    "            \"minItems\": 3," + NEW_LINE +
                    "            \"uniqueItems\": true" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                    "}")
            )
        ));
    }

    // - with Form Parameters

    @Test
    public void shouldMatchJsonSchemaBodyWithFormParameters() {
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody("" +
                "id=1" +
                "&name=A+green+door" +
                "&price=12.5" +
                "&tags=home" +
                "&tags=green")
        ));
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"خانه\", \"سبز\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED.withCharset(UTF_8))
            .withBody("" +
                "id=1" +
                "&name=یک درب سبز" +
                "&price=12.5" +
                "&tags=خانه" +
                "&tags=سبز")
        ));
    }

    @Test
    public void shouldMatchNottedJsonSchemaBodyWithFormParameters() {
        assertTrue(update(new HttpRequest().withBody(
            not(jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 3," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}"))
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody("" +
                "id=1" +
                "&name=A+green+door" +
                "&price=12.5" +
                "&tags=home" +
                "&tags=green")
        ));
    }

    @Test
    public void shouldMatchJsonSchemaBodyWithFormParametersWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
                .withOptional(true)
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody("" +
                "id=1" +
                "&name=A+green+door" +
                "&price=12.5" +
                "&tags=home" +
                "&tags=green")
        ));
        assertTrue(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchJsonSchemaBodyWithFormParameters() {
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 3," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody("" +
                "id=1" +
                "&name=A+green+door" +
                "&price=12.5" +
                "&tags=home" +
                "&tags=green")
        ));
    }

    @Test
    public void shouldNotMatchNottedJsonSchemaBodyWithFormParameters() {
        assertFalse(update(new HttpRequest().withBody(
            not(jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}"))
        )).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody("" +
                "id=1" +
                "&name=A+green+door" +
                "&price=12.5" +
                "&tags=home" +
                "&tags=green")
        ));
    }

    @Test
    public void shouldNotMatchJsonSchemaBodyWithFormParametersWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            jsonSchema("{" + NEW_LINE +
                "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                "    \"title\": \"Product\"," + NEW_LINE +
                "    \"type\": \"object\"," + NEW_LINE +
                "    \"properties\": {" + NEW_LINE +
                "        \"id\": {" + NEW_LINE +
                "            \"type\": \"integer\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"name\": {" + NEW_LINE +
                "            \"type\": \"string\"" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"price\": {" + NEW_LINE +
                "            \"type\": \"number\"," + NEW_LINE +
                "            \"minimum\": 0," + NEW_LINE +
                "            \"exclusiveMinimum\": true" + NEW_LINE +
                "        }," + NEW_LINE +
                "        \"tags\": {" + NEW_LINE +
                "            \"type\": \"array\"," + NEW_LINE +
                "            \"items\": {" + NEW_LINE +
                "                \"type\": \"string\"," + NEW_LINE +
                "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                "            }," + NEW_LINE +
                "            \"minItems\": 1," + NEW_LINE +
                "            \"uniqueItems\": true" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                "}")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchJsonSchemaBodyWithFormParametersForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody("" +
                "id=1" +
                "&name=A+green+door" +
                "&price=12.5" +
                "&tags=home" +
                "&tags=green")
        ).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody(
                jsonSchema("{" + NEW_LINE +
                    "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                    "    \"title\": \"Product\"," + NEW_LINE +
                    "    \"type\": \"object\"," + NEW_LINE +
                    "    \"properties\": {" + NEW_LINE +
                    "        \"id\": {" + NEW_LINE +
                    "            \"type\": \"integer\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"name\": {" + NEW_LINE +
                    "            \"type\": \"string\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"price\": {" + NEW_LINE +
                    "            \"type\": \"number\"," + NEW_LINE +
                    "            \"minimum\": 0," + NEW_LINE +
                    "            \"exclusiveMinimum\": true" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"tags\": {" + NEW_LINE +
                    "            \"type\": \"array\"," + NEW_LINE +
                    "            \"items\": {" + NEW_LINE +
                    "                \"type\": \"string\"," + NEW_LINE +
                    "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                    "            }," + NEW_LINE +
                    "            \"minItems\": 1," + NEW_LINE +
                    "            \"uniqueItems\": true" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                    "}")
            )
        ));
        assertFalse(update(new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody("" +
                "id=1" +
                "&name=A+green+door" +
                "&price=12.5" +
                "&tags=home" +
                "&tags=green")
        ).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody(
                jsonSchema("{" + NEW_LINE +
                    "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                    "    \"title\": \"Product\"," + NEW_LINE +
                    "    \"type\": \"object\"," + NEW_LINE +
                    "    \"properties\": {" + NEW_LINE +
                    "        \"id\": {" + NEW_LINE +
                    "            \"type\": \"integer\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"name\": {" + NEW_LINE +
                    "            \"type\": \"string\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"price\": {" + NEW_LINE +
                    "            \"type\": \"number\"," + NEW_LINE +
                    "            \"minimum\": 0," + NEW_LINE +
                    "            \"exclusiveMinimum\": true" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"tags\": {" + NEW_LINE +
                    "            \"type\": \"array\"," + NEW_LINE +
                    "            \"items\": {" + NEW_LINE +
                    "                \"type\": \"string\"," + NEW_LINE +
                    "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                    "            }," + NEW_LINE +
                    "            \"minItems\": 1," + NEW_LINE +
                    "            \"uniqueItems\": true" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                    "}")
            )
        ));

    }

    @Test
    public void shouldNotMatchJsonSchemaBodyWithFormParametersForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody("" +
                "id=1" +
                "&name=A+green+door" +
                "&price=12.5" +
                "&tags=home" +
                "&tags=green")
        ).matches(null, new HttpRequest()
            .withContentType(MediaType.APPLICATION_FORM_URLENCODED)
            .withBody(
                jsonSchema("{" + NEW_LINE +
                    "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + NEW_LINE +
                    "    \"title\": \"Product\"," + NEW_LINE +
                    "    \"type\": \"object\"," + NEW_LINE +
                    "    \"properties\": {" + NEW_LINE +
                    "        \"id\": {" + NEW_LINE +
                    "            \"type\": \"integer\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"name\": {" + NEW_LINE +
                    "            \"type\": \"string\"" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"price\": {" + NEW_LINE +
                    "            \"type\": \"number\"," + NEW_LINE +
                    "            \"minimum\": 0," + NEW_LINE +
                    "            \"exclusiveMinimum\": true" + NEW_LINE +
                    "        }," + NEW_LINE +
                    "        \"tags\": {" + NEW_LINE +
                    "            \"type\": \"array\"," + NEW_LINE +
                    "            \"items\": {" + NEW_LINE +
                    "                \"type\": \"string\"," + NEW_LINE +
                    "                \"enum\": [\"home\", \"green\"]" + NEW_LINE +
                    "            }," + NEW_LINE +
                    "            \"minItems\": 3," + NEW_LINE +
                    "            \"uniqueItems\": true" + NEW_LINE +
                    "        }" + NEW_LINE +
                    "    }," + NEW_LINE +
                    "    \"required\": [\"id\", \"name\", \"price\"]" + NEW_LINE +
                    "}")
            )
        ));
    }

    // - ParameterBody

    @Test
    public void shouldMatchParameterBody() {
        assertTrue(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
            )
        )));
        assertTrue(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameTwo", "valueTwo")
            )
        )).matches(null, new HttpRequest().withBody(new ParameterBody(
            new Parameter("nameOne", "valueOne"),
            new Parameter("nameTwo", "valueTwo")
        ))));
        assertTrue(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameTwo", "valueTwo", "valueThree")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
            )
        )));
        assertTrue(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameTwo", "valueTwo")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
            )
        )));
        assertTrue(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameTwo", "valueThree")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
            )
        )));
        assertTrue(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameTwo", "valueT[a-z]{0,10}")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
            )
        )));
    }

    @Test
    public void shouldMatchNottedParameterBody() {
        assertTrue(update(new HttpRequest().withBody(
            not(new ParameterBody(new Parameter("name", "value"))))
        ).matches(null, new HttpRequest().withBody(
            new ParameterBody(new Parameter("wrongName", "value"))
        )));
    }

    @Test
    public void shouldMatchParameterBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne")
            )
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
            )
        )));
        assertTrue(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne")
            )
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchParameterBodyWithUrlEncodedBodyParameters() {
        // pass exact match
        assertTrue(update(new HttpRequest().withBody(params(param("name one", "value one"), param("nameTwo", "valueTwo"))
        )).matches(null, new HttpRequest().withBody(
            "name+one=value+one&nameTwo=valueTwo"
        )));

        // ignore extra parameters
        assertTrue(update(new HttpRequest().withBody(params(param("name one", "value one"))
        )).matches(null, new HttpRequest().withBody(
            "name+one=value+one&nameTwo=valueTwo"
        )));

        // matches multi-value parameters
        assertTrue(update(new HttpRequest().withBody(params(param("name one", "value one one", "value one two"))
        )).matches(null, new HttpRequest().withBody(
            "name+one=value+one+one&name+one=value+one+two"
        )));

        // matches multi-value parameters (ignore extra values)
        assertTrue(update(new HttpRequest().withBody(params(param("name one", "value one one"))
        )).matches(null, new HttpRequest().withBody(
            "name+one=value+one+one&name+one=value+one+two"
        )));
        assertTrue(update(new HttpRequest().withBody(params(param("name one", "value one two"))
        )).matches(null, new HttpRequest().withBody(
            "name+one=value+one+one&name+one=value+one+two"
        )));

        // matches using regex
        assertTrue(update(new HttpRequest().withBody(params(param("name one", "value [a-z]{0,10}"), param("nameTwo", "valueT[a-z]{0,10}"))
        )).matches(null, new HttpRequest().withBody(
            "name+one=value+one&nameTwo=valueTwo"
        )));

        // fail no match
        assertFalse(update(new HttpRequest().withBody(
            params(param("name one", "value one"))
        )).matches(null, new HttpRequest().withBody(
            "name+one=value+two"
        )));
    }

    @Test
    public void shouldMatchBodyFormStyleParameter() {
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1,1,1")
        ))));
        assertFalse(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1,1,1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM),
            new Parameter("someKeyTwo", "a")
                .withStyle(FORM)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1,1,1"),
            new Parameter("someKeyTwo", "a,a,a")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1,1,1", "1,1,1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1,2,3", "1,2,3")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1,2,3", "1,2,3")
        ))));
        assertFalse(update(new HttpRequest().withBody(
            params(
                new Parameter("someKey", "1")
                    .withStyle(FORM)
            ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "a,2,3")
        ))));
    }

    @Test
    public void shouldMatchBodyFormExpandedStyleParameter() {
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM_EXPLODED),
            new Parameter("someKeyTwo", "a")
                .withStyle(FORM_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1"),
            new Parameter("someKeyTwo", "a", "a", "a")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1", "1", "1", "1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(FORM_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        ))));
        assertFalse(update(new HttpRequest().withBody(
            params(
                new Parameter("someKey", "1")
            ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "a", "2", "3")
        ))));
    }

    @Test
    public void shouldMatchBodySpaceDelimitedStyleParameter() {
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1%201%201")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1+1+1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1 1 1")
        ))));
        assertFalse(update(new HttpRequest().withBody(
            params(
                new Parameter("someKey", "1")
            ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1%201%201")
        ))));
        assertFalse(update(new HttpRequest().withBody(
            params(
                new Parameter("someKey", "1")
            ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1+1+1")
        ))));
        assertFalse(update(new HttpRequest().withBody(
            params(
                new Parameter("someKey", "1")
            ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1 1 1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED),
            new Parameter("someKeyTwo", "a")
                .withStyle(SPACE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1+1+1"),
            new Parameter("someKeyTwo", "a+a+a")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1+1+1", "1+1+1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1%202%203", "1%202%203")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1+2+3", "1+2+3")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1 2 3", "1 2 3")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1%202%203", "1%202%203")
        ))));
        assertFalse(update(new HttpRequest().withBody(
            params(
                new Parameter("someKey", "1")
                    .withStyle(SPACE_DELIMITED)
            ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "a+2+3")
        ))));
    }

    @Test
    public void shouldMatchBodySpaceDelimitedExpandedStyleParameter() {
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED_EXPLODED),
            new Parameter("someKeyTwo", "a")
                .withStyle(SPACE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1"),
            new Parameter("someKeyTwo", "a", "a", "a")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1", "1", "1", "1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(SPACE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        ))));
        assertFalse(update(new HttpRequest().withBody(
            params(
                new Parameter("someKey", "1")
            ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "a", "2", "3")
        ))));
    }

    @Test
    public void shouldMatchBodyPipeDelimitedStyleParameter() {
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1|1|1")
        ))));
        assertFalse(update(new HttpRequest().withBody(
            params(
                new Parameter("someKey", "1")
            ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1|1|1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED),
            new Parameter("someKeyTwo", "a")
                .withStyle(PIPE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1|1|1"),
            new Parameter("someKeyTwo", "a|a|a")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1|1|1", "1|1|1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1|2|3", "1|2|3")
        ))));
        assertFalse(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "a|2|3", "b|2|3")
        ))));
    }

    @Test
    public void shouldMatchBodyPipeDelimitedExpandedStyleParameter() {
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED_EXPLODED),
            new Parameter("someKeyTwo", "a")
                .withStyle(PIPE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1"),
            new Parameter("someKeyTwo", "a", "a", "a")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "1", "1", "1", "1", "1")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        ))));
        assertTrue(update(new HttpRequest().withBody(params(
            new Parameter("someKey", "1")
                .withStyle(PIPE_DELIMITED_EXPLODED)
        ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "1", "2", "3", "1", "2", "3")
        ))));
        assertFalse(update(new HttpRequest().withBody(
            params(
                new Parameter("someKey", "1")
            ))).matches(null, new HttpRequest().withBody(params(
            new Parameter("someKey", "a", "2", "3")
        ))));
    }

    @Test
    public void shouldNotMatchParameterBody() {
        assertFalse(update(new HttpRequest().withBody(
            new ParameterBody(new Parameter("name", "value")))
        ).matches(null, new HttpRequest().withBody(
            new ParameterBody(new Parameter("wrongName", "value"))
        )));
        assertFalse(update(new HttpRequest().withBody(
            new ParameterBody(new Parameter("name", "value"))
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(new Parameter("name", "wrongValue"))
        )));
        assertFalse(update(new HttpRequest().withBody(
            new ParameterBody(new Parameter("name", "value"))
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(new Parameter("wrongName", "wrongValue"))
        )));
        assertFalse(update(new HttpRequest().withBody(
            new ParameterBody(new Parameter("name", "va[0-9]{1}ue"))
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(new Parameter("name", "wrongValue"))
        )));
    }

    @Test
    public void shouldNotMatchNottedParameterBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(new ParameterBody(
                new Parameter("nameOne", "valueOne")
            ))
        )).matches(null, new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
            )
        )));
    }

    @Test
    public void shouldNotMatchParameterBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne")
            )
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne")
            )
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchParameterBodyWithUrlEncodedBodyParameters() {
        assertFalse(update(new HttpRequest().withBody(
            params(param("name one", "wrong value"))
        )).matches(null, new HttpRequest().withBody(
            "name+one=value+one"
        )));
        assertFalse(update(new HttpRequest().withBody(
            params(param("wrong name", "value one"))
        )).matches(null, new HttpRequest().withBody(
            "name+one=value+one"
        )));
    }


    @Test
    public void shouldMatchParameterBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(
                new Parameter("nameOne", "valueOne")
            )).toString()
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(
                new Parameter("nameTwo", "valueTwo", "valueThree")
            )).toString()
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(
                new Parameter("nameTwo", "valueT[a-z]{0,10}")
            )).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(
                new Parameter("nameOne", "valueOne")
            )).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(
                new Parameter("nameTwo", "valueTwo", "valueThree")
            )).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo"),
                new Parameter("nameTwo", "valueThree")
            )
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(
                new Parameter("nameTwo", "valueT[a-z]{0,10}")
            )).toString()
        )));
    }

    @Test
    public void shouldNotMatchParameterBodyForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            new ParameterBody(new Parameter("wrongName", "value"))
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(new Parameter("name", "value"))).toString()
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            new ParameterBody(new Parameter("name", "wrongValue"))
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(new Parameter("name", "value"))).toString()
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            new ParameterBody(new Parameter("wrongName", "wrongValue"))
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(new Parameter("name", "value"))).toString()
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            new ParameterBody(new Parameter("name", "wrongValue"))
        )).matches(null, new HttpRequest().withBody(
            new ParameterBodyDTO(new ParameterBody(new Parameter("name", "va[0-9]{1}ue"))).toString()
        )));
    }

    // - RegexBody

    @Test
    public void shouldMatchRegexBody() {
        assertTrue(update(new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )).matches(null, new HttpRequest().withBody(
            "somebody"
        )));
    }

    @Test
    public void shouldMatchNottedRegexBody() {
        assertTrue(update(new HttpRequest().withBody(
            not(new RegexBody("someb[a-z]{3}"))
        )).matches(null, new HttpRequest().withBody(
            "wrongBody"
        )));
    }

    @Test
    public void shouldMatchRegexBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody(
            "somebody"
        )));
        assertTrue(update(new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchRegexBody() {
        assertFalse(update(new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )).matches(null, new HttpRequest().withBody(
            "wrongBody"
        )));
        assertFalse(update(new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )).matches(null, new HttpRequest().withBody(
            (String) null
        )));
        assertFalse(update(new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )).matches(null, new HttpRequest().withBody(
            ""
        )));
    }

    @Test
    public void shouldNotMatchNottedRegexBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(new RegexBody("someb[a-z]{3}"))
        )).matches(null, new HttpRequest().withBody(
            "somebody"
        )));
    }

    @Test
    public void shouldNotMatchRegexBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchRegexBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            "somebody"
        )).matches(null, new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            (String) null
        )).matches(null, new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            ""
        )).matches(null, new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )));
        assertFalse(update(new HttpRequest().withBody(
            "somebody"
        )).matches(null, new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )));
        assertTrue(update(new HttpRequest().withBody(
            (String) null
        )).matches(null, new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )));
        assertTrue(update(new HttpRequest().withBody(
            ""
        )).matches(null, new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )));
    }

    @Test
    public void shouldNotMatchRegexBodyForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "wrongBody"
        )).matches(null, new HttpRequest().withBody(
            new RegexBody("someb[a-z]{3}")
        )));
    }

    // - StringBody

    @Test
    public void shouldMatchStringBody() {
        assertTrue(update(new HttpRequest().withBody(
            new StringBody("somebody")
        )).matches(null, new HttpRequest().withBody(
            "somebody"
        )));
        assertTrue(update(new HttpRequest().withBody(
            new StringBody("somebody")
        )).matches(null, new HttpRequest().withBody(
            exact("somebody")
        )));
        assertTrue(update(new HttpRequest().withBody(
            (String) null
        )).matches(null, new HttpRequest().withBody(
            "somebody"
        )));
        assertTrue(update(new HttpRequest().withBody(
            (String) null
        )).matches(null, new HttpRequest().withBody(
            exact("somebody")
        )));
        assertTrue(update(new HttpRequest().withBody(
            ""
        )).matches(null, new HttpRequest().withBody(
            "somebody"
        )));
        assertTrue(update(new HttpRequest().withBody(
            ""
        )).matches(null, new HttpRequest().withBody(
            exact("somebody")
        )));
    }

    @Test
    public void shouldMatchNottedStringBody() {
        assertTrue(update(new HttpRequest().withBody(
            not(new StringBody("somebody"))
        )).matches(null, new HttpRequest().withBody(
            "wrongBody"
        )));
    }

    @Test
    public void shouldMatchStringBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            new StringBody("somebody")
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody(
            "somebody"
        )));
        assertTrue(update(new HttpRequest().withBody(
            new StringBody("somebody")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchStringBody() {
        assertFalse(update(new HttpRequest().withBody(
            new StringBody("somebody")
        )).matches(null, new HttpRequest().withBody(
            "wrongBody"
        )));
        assertFalse(update(new HttpRequest().withBody(
            exact("somebody")
        )).matches(null, new HttpRequest().withBody(
            (String) null
        )));
        assertFalse(update(new HttpRequest().withBody(
            exact("somebody")
        )).matches(null, new HttpRequest().withBody(
            ""
        )));
    }

    @Test
    public void shouldNotMatchNottedStringBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(new StringBody("somebody"))
        )).matches(null, new HttpRequest().withBody(
            "somebody"
        )));
    }

    @Test
    public void shouldNotMatchStringBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            new StringBody("somebody")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            new StringBody("somebody")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchStringBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            "somebody"
        )).matches(null, new HttpRequest().withBody(
            new StringBody("somebody")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            exact("somebody")
        )).matches(null, new HttpRequest().withBody(
            new StringBody("somebody")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            (String) null
        )).matches(null, new HttpRequest().withBody(
            exact("somebody")
        )));
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            ""
        )).matches(null, new HttpRequest().withBody(
            exact("somebody")
        )));
    }

    @Test
    public void shouldNotMatchStringBodyForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "wrongBody"
        )).matches(null, new HttpRequest().withBody(
            new StringBody("somebody")
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "somebody"
        )).matches(null, new HttpRequest().withBody(
            (String) null
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            exact("somebody")
        )).matches(null, new HttpRequest().withBody(
            (String) null
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "somebody"
        )).matches(null, new HttpRequest().withBody(
            ""
        )));
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            exact("somebody")
        )).matches(null, new HttpRequest().withBody(
            ""
        )));
    }

    @Test
    public void matchesMatchingBodyRegex() {
        assertTrue(update(new HttpRequest().withBody(regex("some[a-z]{4}")
        )).matches(null, new HttpRequest().withBody("somebody")));
    }

    @Test
    public void doesNotMatchIncorrectBodyRegex() {
        assertFalse(update(new HttpRequest().withBody(regex("some[a-z]{3}")
        )).matches(null, new HttpRequest().withBody("bodysome")));
    }


    // - XPathBody

    @Test
    public void shouldMatchXPathBody() {
        assertTrue(update(new HttpRequest().withBody(
            xpath("/element[key = 'some_key' and value = 'some_value']")
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )));
    }

    @Test
    public void shouldMatchNottedXPathBody() {
        assertTrue(update(new HttpRequest().withBody(
            not(xpath("/element[key = 'some_key' and value = 'some_value']"))
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "</element>"
        )));
    }

    @Test
    public void shouldMatchXPathBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            xpath("/element[key = 'some_key' and value = 'some_value']")
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )));
        assertTrue(update(new HttpRequest().withBody(
            xpath("/element[key = 'some_key' and value = 'some_value']")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchXPathBody() {
        assertFalse(update(new HttpRequest().withBody(
            xpath("/element[key = 'some_key' and value = 'some_value']")
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "</element>"
        )));
    }

    @Test
    public void shouldNotMatchNottedXPathBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(xpath("/element[key = 'some_key' and value = 'some_value']"))
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )));
    }

    @Test
    public void shouldNotMatchXPathBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            xpath("/element[key = 'some_key' and value = 'some_value']")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            xpath("/element[key = 'some_key' and value = 'some_value']")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchXPathBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )).matches(null, new HttpRequest().withBody(
            new XPathBodyDTO(xpath("/element[key = 'some_key' and value = 'some_value']")).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )).matches(null, new HttpRequest().withBody(
            new XPathBodyDTO(xpath("/element[key = 'some_key' and value = 'some_value']")).toString()
        )));
    }

    @Test
    public void shouldNotMatchXPathBodyForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "</element>"
        )).matches(null, new HttpRequest().withBody(
            new XPathBodyDTO(xpath("/element[key = 'some_key' and value = 'some_value']")).toString()
        )));
    }

    // - XMLBody

    @Test
    public void shouldMatchXmlBody() {
        assertTrue(update(new HttpRequest().withBody(
            xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )));
    }

    @Test
    public void shouldMatchNottedXmlBody() {
        assertTrue(update(new HttpRequest().withBody(
            not(xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"))
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "</element>"
        )));
    }

    @Test
    public void shouldMatchXmlBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )));
        assertTrue(update(new HttpRequest().withBody(
            xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchXmlBody() {
        assertFalse(update(new HttpRequest().withBody(
            xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "</element>"
        )));
    }

    @Test
    public void shouldNotMatchNottedXmlBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"))
        )).matches(null, new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )));
    }

    @Test
    public void shouldNotMatchXmlBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchXmlBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )).matches(null, new HttpRequest().withBody(
            new XmlBodyDTO(xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>"
        )).matches(null, new HttpRequest().withBody(
            new XmlBodyDTO(xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")).toString()
        )));
    }

    @Test
    public void shouldNotMatchXmlBodyForControlPlane() {
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "" +
                "<element>" +
                "   <key>some_key</key>" +
                "</element>"
        )).matches(null, new HttpRequest().withBody(
            new XmlBodyDTO(xml("" +
                "<element>" +
                "   <key>some_key</key>" +
                "   <value>some_value</value>" +
                "</element>")).toString()
        )));
    }

    // - XMLSchemaBody

    @Test
    public void shouldMatchXMLSchemaBody() {
        assertTrue(update(new HttpRequest().withBody(
            xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>")
        )).matches(null, new HttpRequest().withBody("" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
            "<notes>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Bob</to>" + NEW_LINE +
            "        <from>Bill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Buy Bread</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Jack</to>" + NEW_LINE +
            "        <from>Jill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Wash Shirts</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "</notes>"
        )));
    }

    @Test
    public void shouldMatchNottedXMLSchemaBody() {
        // from missing in first note
        assertTrue(update(new HttpRequest().withBody(
            not(xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>"))
        )).matches(null, new HttpRequest().withBody("" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
            "<notes>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Bob</to>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Buy Bread</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Jack</to>" + NEW_LINE +
            "        <from>Jill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Wash Shirts</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "</notes>"
        )));
    }

    @Test
    public void shouldMatchXMLSchemaBodyWithOptional() {
        assertTrue(update(new HttpRequest().withBody(
            xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>")
                .withOptional(true)
        )).matches(null, new HttpRequest().withBody("" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
            "<notes>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Bob</to>" + NEW_LINE +
            "        <from>Bill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Buy Bread</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Jack</to>" + NEW_LINE +
            "        <from>Jill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Wash Shirts</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "</notes>"
        )));
        assertTrue(update(new HttpRequest().withBody(
            xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>")
                .withOptional(true)
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldNotMatchXMLSchemaBody() {
        // from missing in first note
        assertFalse(update(new HttpRequest().withBody(
            xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>")
        )).matches(null, new HttpRequest().withBody("" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
            "<notes>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Bob</to>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Buy Bread</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Jack</to>" + NEW_LINE +
            "        <from>Jill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Wash Shirts</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "</notes>"
        )));
    }

    @Test
    public void shouldNotMatchNottedXMLSchemaBody() {
        assertFalse(update(new HttpRequest().withBody(
            not(xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>"))
        )).matches(null, new HttpRequest().withBody("" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
            "<notes>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Bob</to>" + NEW_LINE +
            "        <from>Bill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Buy Bread</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Jack</to>" + NEW_LINE +
            "        <from>Jill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Wash Shirts</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "</notes>"
        )));
    }

    @Test
    public void shouldNotMatchXMLSchemaBodyWithOptional() {
        assertFalse(update(new HttpRequest().withBody(
            xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>")
                .withOptional(false)
        )).matches(null, new HttpRequest()));
        assertFalse(update(new HttpRequest().withBody(
            xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>")
        )).matches(null, new HttpRequest()));
    }

    @Test
    public void shouldMatchXMLSchemaBodyForControlPlane() {
        assertTrue(updateForControlPlane(new HttpRequest().withBody(
            "" +
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                "<notes>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Bob</to>" + NEW_LINE +
                "        <from>Bill</from>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Buy Bread</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Jack</to>" + NEW_LINE +
                "        <from>Jill</from>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Wash Shirts</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "</notes>"
        )).matches(null, new HttpRequest().withBody(
            new XmlSchemaBodyDTO(xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>")).toString()
        )));
        assertFalse(update(new HttpRequest().withBody(
            "" +
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                "<notes>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Bob</to>" + NEW_LINE +
                "        <from>Bill</from>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Buy Bread</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Jack</to>" + NEW_LINE +
                "        <from>Jill</from>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Wash Shirts</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "</notes>"
        )).matches(null, new HttpRequest().withBody(
            new XmlSchemaBodyDTO(xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>")).toString()
        )));
    }

    @Test
    public void shouldNotMatchXMLSchemaBodyForControlPlane() {
        // from missing in first note
        assertFalse(updateForControlPlane(new HttpRequest().withBody(
            "" +
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                "<notes>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Bob</to>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Buy Bread</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Jack</to>" + NEW_LINE +
                "        <from>Jill</from>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Wash Shirts</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "</notes>"
        )).matches(null, new HttpRequest().withBody(
            new XmlSchemaBodyDTO(xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>")).toString()
        )));
    }

    @Test
    public void shouldReturnFormattedRequestWithStringBodyInToString() {
        assertEquals("{" + NEW_LINE +
                "  \"method\" : \"GET\"," + NEW_LINE +
                "  \"path\" : \"/some/path\"," + NEW_LINE +
                "  \"queryStringParameters\" : {" + NEW_LINE +
                "    \"parameterOneName\" : [ \"parameterOneValue\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"headers\" : {" + NEW_LINE +
                "    \"name\" : [ \"value\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"cookies\" : {" + NEW_LINE +
                "    \"name\" : \"[A-Z]{0,10}\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"body\" : \"some_body\"" + NEW_LINE +
                "}",
            update(request()
                .withMethod("GET")
                .withPath("/some/path")
                .withQueryStringParameters(param("parameterOneName", "parameterOneValue"))
                .withBody("some_body")
                .withHeaders(
                    new Header("name", "value"))
                .withCookies(new Cookie("name", "[A-Z]{0,10}"))
            ).toString()
        );
    }
}

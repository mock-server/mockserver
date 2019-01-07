package org.mockserver.examples.mockserver;

import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.Not;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.JsonPathBody.jsonPath;
import static org.mockserver.model.JsonSchemaBody.jsonSchema;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;
import static org.mockserver.model.RegexBody.regex;
import static org.mockserver.model.StringBody.exact;
import static org.mockserver.model.StringBody.subString;
import static org.mockserver.model.XPathBody.xpath;
import static org.mockserver.model.XmlBody.xml;
import static org.mockserver.model.XmlSchemaBody.xmlSchema;
import static org.mockserver.model.XmlSchemaBody.xmlSchemaFromResource;

/**
 * @author jamesdbloom
 */
public class RequestMatcherExamples {

    public void matchRequestByPath() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByPathExactlyTwice() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path"),
                Times.exactly(2)
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByPathExactlyOnceInTheNext60Seconds() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path"),
                Times.once(),
                TimeToLive.exactly(TimeUnit.SECONDS, 60L)
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByRegexPath() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    // matches any requests those path starts with "/some"
                    .withPath("/some.*")
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByNotMatchingPath() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    // matches any requests those path does NOT start with "/some"
                    .withPath(not("/some.*"))
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByMethodRegex() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    // matches any requests that does NOT have a "GET" method
                    .withMethod(not("P.*{2,3}"))
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByNotMatchingMethod() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    // matches any requests that does NOT have a "GET" method
                    .withMethod(not("GET"))
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByQueryParameterNameRegex() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
                    .withQueryStringParameters(
                        param("[A-z]{0,10}", "055CA455-1DF7-45BB-8535-4F83E7266092")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByQueryParameterRegexValue() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
                    .withQueryStringParameters(
                        param("cartId", "[A-Z0-9\\-]+")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByHeaders() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withMethod("GET")
                    .withPath("/some/path")
                    .withHeaders(
                        header("Accept", "application/json"),
                        header("Accept-Encoding", "gzip, deflate, br")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByHeaderNameRegex() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
                    .withHeader(
                        header("Accept.*")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByHeaderRegexNameAndValue() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
                    .withHeader(
                        header("Accept.*", ".*gzip.*")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByNotMatchingHeaderValue() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
                    .withHeaders(
                        // matches requests that have an Accept header without the value "application/json"
                        header(string("Accept"), not("application/json")),
                        // matches requests that have an Accept-Encoding without the substring "gzip"
                        header(string("Accept-Encoding"), not(".*gzip.*"))
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByNotMatchingHeaders() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
                    .withHeaders(
                        // matches requests that do not have either an Accept or an Accept-Encoding header
                        header(not("Accept")),
                        header(not("Accept-Encoding"))
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByCookiesAndQueryParameters() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withMethod("GET")
                    .withPath("/view/cart")
                    .withCookies(
                        cookie("session", "4930456C-C718-476F-971F-CB8E047AB349")
                    )
                    .withQueryStringParameters(
                        param("cartId", "055CA455-1DF7-45BB-8535-4F83E7266092")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestBySubStringBody() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(subString("some_string"))
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByRegexBody() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(regex("starts_with_.*"))
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyInUTF16() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(exact("我说中国话", StandardCharsets.UTF_16))
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyWithFormSubmission() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withMethod("POST")
                    .withHeaders(
                        header("Content-Type", "application/x-www-form-urlencoded")
                    )
                    .withBody(
                        params(
                            param("email", "joe.blogs@gmail.com"),
                            param("password", "secure_Password123")
                        )
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyWithXPath() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    // matches any request with an XML body containing
                    // an element that matches the XPath expression
                    .withBody(
                        xpath("/bookstore/book[price>30]/price")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );

        // matches a request with the following body:
        /*
        <?xml version="1.0" encoding="ISO-8859-1"?>
        <bookstore>
          <book category="COOKING">
            <title lang="en">Everyday Italian</title>
            <author>Giada De Laurentiis</author>
            <year>2005</year>
            <price>30.00</price>
          </book>
          <book category="CHILDREN">
            <title lang="en">Harry Potter</title>
            <author>J K. Rowling</author>
            <year>2005</year>
            <price>29.99</price>
          </book>
          <book category="WEB">
            <title lang="en">Learning XML</title>
            <author>Erik T. Ray</author>
            <year>2003</year>
            <price>31.95</price>
          </book>
        </bookstore>
         */
    }

    public void matchRequestByNotMatchingBodyWithXPath() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(
                        // matches any request with an XML body that does NOT
                        // contain an element that matches the XPath expression
                        Not.not(xpath("/bookstore/book[price>30]/price"))
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyWithXml() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(
                        xml("<bookstore>" + System.lineSeparator() +
                            "   <book nationality=\"ITALIAN\" category=\"COOKING\">" + System.lineSeparator() +
                            "       <title lang=\"en\">Everyday Italian</title>" + System.lineSeparator() +
                            "       <author>Giada De Laurentiis</author>" + System.lineSeparator() +
                            "       <year>2005</year>" + System.lineSeparator() +
                            "       <price>30.00</price>" + System.lineSeparator() +
                            "   </book>" + System.lineSeparator() +
                            "</bookstore>")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyWithXmlSchema() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(
                        xmlSchema("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + System.lineSeparator() +
                            "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + System.lineSeparator() +
                            "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + System.lineSeparator() +
                            "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + System.lineSeparator() +
                            "    <xs:element name=\"notes\">" + System.lineSeparator() +
                            "        <xs:complexType>" + System.lineSeparator() +
                            "            <xs:sequence>" + System.lineSeparator() +
                            "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + System.lineSeparator() +
                            "                    <xs:complexType>" + System.lineSeparator() +
                            "                        <xs:sequence>" + System.lineSeparator() +
                            "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + System.lineSeparator() +
                            "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + System.lineSeparator() +
                            "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + System.lineSeparator() +
                            "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + System.lineSeparator() +
                            "                        </xs:sequence>" + System.lineSeparator() +
                            "                    </xs:complexType>" + System.lineSeparator() +
                            "                </xs:element>" + System.lineSeparator() +
                            "            </xs:sequence>" + System.lineSeparator() +
                            "        </xs:complexType>" + System.lineSeparator() +
                            "    </xs:element>" + System.lineSeparator() +
                            "</xs:schema>")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyWithXmlSchemaByClasspath() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(
                        xmlSchemaFromResource("org/mockserver/examples/mockserver/testXmlSchema.xsd")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyWithJsonExactly() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(
                        json("{" + System.lineSeparator() +
                                "    \"id\": 1," + System.lineSeparator() +
                                "    \"name\": \"A green door\"," + System.lineSeparator() +
                                "    \"price\": 12.50," + System.lineSeparator() +
                                "    \"tags\": [\"home\", \"green\"]" + System.lineSeparator() +
                                "}",
                            MatchType.STRICT
                        )
                    )
            )
            .respond(
                response()
                    .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyWithJsonIgnoringExtraFields() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(
                        json("{" + System.lineSeparator() +
                                "    \"id\": 1," + System.lineSeparator() +
                                "    \"name\": \"A green door\"," + System.lineSeparator() +
                                "    \"price\": 12.50," + System.lineSeparator() +
                                "    \"tags\": [\"home\", \"green\"]" + System.lineSeparator() +
                                "}",
                            MatchType.ONLY_MATCHING_FIELDS
                        )
                    )
            )
            .respond(
                response()
                    .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyWithJsonSchema() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(
                        jsonSchema("{" + System.lineSeparator() +
                            "    \"$schema\": \"http://json-schema.org/draft-04/schema#\"," + System.lineSeparator() +
                            "    \"title\": \"Product\"," + System.lineSeparator() +
                            "    \"description\": \"A product from Acme's catalog\"," + System.lineSeparator() +
                            "    \"type\": \"object\"," + System.lineSeparator() +
                            "    \"properties\": {" + System.lineSeparator() +
                            "        \"id\": {" + System.lineSeparator() +
                            "            \"description\": \"The unique identifier for a product\"," + System.lineSeparator() +
                            "            \"type\": \"integer\"" + System.lineSeparator() +
                            "        }," + System.lineSeparator() +
                            "        \"name\": {" + System.lineSeparator() +
                            "            \"description\": \"Name of the product\"," + System.lineSeparator() +
                            "            \"type\": \"string\"" + System.lineSeparator() +
                            "        }," + System.lineSeparator() +
                            "        \"price\": {" + System.lineSeparator() +
                            "            \"type\": \"number\"," + System.lineSeparator() +
                            "            \"minimum\": 0," + System.lineSeparator() +
                            "            \"exclusiveMinimum\": true" + System.lineSeparator() +
                            "        }," + System.lineSeparator() +
                            "        \"tags\": {" + System.lineSeparator() +
                            "            \"type\": \"array\"," + System.lineSeparator() +
                            "            \"items\": {" + System.lineSeparator() +
                            "                \"type\": \"string\"" + System.lineSeparator() +
                            "            }," + System.lineSeparator() +
                            "            \"minItems\": 1," + System.lineSeparator() +
                            "            \"uniqueItems\": true" + System.lineSeparator() +
                            "        }" + System.lineSeparator() +
                            "    }," + System.lineSeparator() +
                            "    \"required\": [\"id\", \"name\", \"price\"]" + System.lineSeparator() +
                            "}"))
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }

    public void matchRequestByBodyWithJsonPath() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withBody(
                        jsonPath("$.store.book[?(@.price < 10)]")
                    )
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );
    }
}

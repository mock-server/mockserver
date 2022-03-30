function matchRequestByPath() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByPathExactlyTwice() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpResponse": {
            "body": "some_response_body"
        },
        "times": {
            "remainingTimes": 2,
            "unlimited": false
        },
        "timeToLive": {
            "unlimited": true
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByPathExactlyOnceInTheNext60Seconds() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpResponse": {
            "body": "some_response_body"
        },
        "times": {
            "remainingTimes": 1,
            "unlimited": false
        },
        "timeToLive": {
            "timeUnit": "SECONDS",
            "timeToLive": 60,
            "unlimited": false
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByRegexPath() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    // matches any requests those path starts with "/some"
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some.*"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByNotMatchingPath() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    // matches any requests those path does NOT start with "/some"
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "!/some.*"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByMethodRegex() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "method": "P.*{2,3}"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByNotMatchingMethod() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    // matches any requests that does NOT have a "GET" method
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "method": "!GET"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByPathAndPathParametersAndQueryParametersName() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path/{cartId}",
            "pathParameters": {
                "cartId": ["055CA455-1DF7-45BB-8535-4F83E7266092"]
            },
            "queryStringParameters": {
                "type": ["[A-Z0-9\\-]+"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByPathParameterRegexValue() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path/{cartId}",
            "pathParameters": {
                "cartId": ["[A-Z0-9\\-]+"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByPathParameterJsonSchemaValue() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path/{cartId}/{maxItemCount}",
            "pathParameters": {
                "cartId": [{
                    "schema": {
                        "type": "string",
                        "pattern": "^[A-Z0-9-]+$"
                    }
                }],
                "maxItemCount": [{
                    "schema": {
                        "type": "integer"
                    }
                }]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByQueryParameterRegexName() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "queryStringParameters": {
                "[A-z]{0,10}": ["055CA455-1DF7-45BB-8535-4F83E7266092"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByQueryParameterRegexValue() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "queryStringParameters": {
                "cartId": ["[A-Z0-9\\-]+"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByOptionalQueryParameterRegexValue() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "queryStringParameters": {
                "?cartId": ["[A-Z0-9\\-]+"],
                "?maxItemCount": [{
                    "schema": {
                        "type": "integer"
                    }
                }],
                "?userId": [{
                    "schema": {
                        "type": "string",
                        "format": "uuid"
                    }
                }]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByQueryParameterJsonSchemaValue() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "queryStringParameters": {
                "cartId": [{
                    "schema": {
                        "type": "string",
                        "pattern": "^[A-Z0-9-]+$"
                    }
                }],
                "maxItemCount": [{
                    "schema": {
                        "type": "integer"
                    }
                }]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByQueryParameterSubSet() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "queryStringParameters": {
                "multiValuedParameter": [{
                    "schema": {
                        "type": "string",
                        "pattern": "^[A-Z0-9-]+$"
                    }
                }],
                "maxItemCount": [{
                    "schema": {
                        "type": "integer"
                    }
                }]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByQueryParameterKeyMatching() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "queryStringParameters": {
                "keyMatchStyle": "MATCHING_KEY",
                "multiValuedParameter": [{
                    "schema": {
                        "type": "string",
                        "pattern": "^[A-Z0-9-]+$"
                    }
                }],
                "maxItemCount": [{
                    "schema": {
                        "type": "integer"
                    }
                }]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByHeaders() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "method": "GET",
            "path": "/some/path",
            "headers": {
                "Accept": ["application/json"],
                "Accept-Encoding": ["gzip, deflate, br"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByHeaderNameRegex() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "headers": {
                // matches requests that have any header starting with the name Accept
                "Accept.*": []
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByHeaderRegexNameAndValue() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "headers": {
                // matches requests that have a header with a name starting with Accept and a value containing gzip
                "Accept.*": [".*gzip.*"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByHeaderJsonSchemaValue() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "headers": {
                "Accept.*": [{
                    "schema": {
                        "type": "string",
                        "pattern": "^.*gzip.*$"
                    }
                }]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByEitherOrOptionalHeader() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "headers": {
                "headerOne|headerTwo": [".*"],
                "?headerOne": ["headerOneValue"],
                "?headerTwo": ["headerTwoValue"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByEitherOrOptionalHeader() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "id": "2c4f2747-bf8f-42dc-8c82-99f497884cfa",
        "priority": 0,
        "httpRequest": {
            "path": "/some/path",
            "headers": {
                "headerOne|headerTwo": [".*"],
                "?headerOne": ["headerOneValue"],
                "?headerTwo": ["headerTwoValue"]
            }
        },
        "times": {
            "unlimited": true
        },
        "timeToLive": {
            "unlimited": true
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByHeaderKeyMatching() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "headers": {
                "keyMatchStyle": "MATCHING_KEY",
                "multiValuedHeader": ["value.*"],
                "headerTwo": ["headerTwoValue"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByNotMatchingHeaderValue() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "headers": {
                // matches requests that have an Accept header without the value "application/json"
                "Accept": ["!application/json"],
                // matches requests that have an Accept-Encoding without the substring "gzip"
                "Accept-Encoding": ["!.*gzip.*"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByNotMatchingHeaders() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path",
            "headers": {
                // matches requests that do not have either an Accept or an Accept-Encoding header
                "!Accept": [".*"],
                "!Accept-Encoding": [".*"]
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByCookiesAndQueryParameters() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "method": "GET",
            "path": "/view/cart",
            "queryStringParameters": {
                "cartId": ["055CA455-1DF7-45BB-8535-4F83E7266092"]
            },
            "cookies": {
                "session": "4930456C-C718-476F-971F-CB8E047AB349"
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByCookiesAndQueryParameterJsonSchemaValues() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "method": "GET",
            "path": "/view/cart",
            "queryStringParameters": {
                "cartId": [{
                    "schema": {
                        "type": "string",
                        "format": "uuid"
                    }
                }]
            },
            "cookies": {
                "session": {
                    "schema": {
                        "type": "string",
                        "format": "uuid"
                    }
                }
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByOptionalCookiesAndQueryParameterJsonSchemaValues() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/view/cart",
            "queryStringParameters": {
                "cartId": [{
                    "schema": {
                        "type": "string",
                        "format": "uuid"
                    }
                }]
            },
            "cookies": {
                "?session": {
                    "schema": {
                        "type": "string",
                        "format": "uuid"
                    }
                }
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByRegexBody() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": "starts_with_.*"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyInUTF16() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                "type": "STRING",
                "string": "我说中国话",
                "contentType": "text/plain; charset=utf-16"
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithFormSubmission() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "method": "POST",
            "headers": {
                "Content-Type": ["application/x-www-form-urlencoded"]
            },
            "body": {
                "type": "PARAMETERS",
                "parameters": {
                    "email": ["joe.blogs@gmail.com"],
                    "password": ["secure_Password123"]
                }
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithXPath() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                // matches any request with an XML body containing
                // an element that matches the XPath expression
                "type": "XPATH",
                "xpath": "/bookstore/book[price>30]/price"
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
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

function matchRequestByNotMatchingBodyWithXPath() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                // matches any request with an XML body that does NOT
                // contain an element that matches the XPath expression
                "not": true,
                "type": "XPATH",
                "xpath": "/bookstore/book[price>30]/price"
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithXml() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                "type": "XML",
                "xml": "<bookstore>\n" +
                    "   <book nationality=\"ITALIAN\" category=\"COOKING\">\n" +
                    "       <title lang=\"en\">Everyday Italian</title>\n" +
                    "       <author>Giada De Laurentiis</author>\n" +
                    "       <year>2005</year>\n" +
                    "       <price>30.00</price>\n" +
                    "   </book>\n" +
                    "</bookstore>"
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithXmlWithPlaceholders() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                "type": "XML",
                "xml": "<bookstore>\n" +
                    "   <book nationality=\"ITALIAN\" category=\"COOKING\">\n" +
                    "       <title lang=\"en\">Everyday Italian</title>\n" +
                    "       <author>${xmlunit.ignore}</author>\n" +
                    "       <year>${xmlunit.isNumber}</year>\n" +
                    "       <price>30.00</price>\n" +
                    "   </book>\n" +
                    "</bookstore>"
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithXmlSchema() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                "type": "XML_SCHEMA",
                "xmlSchema": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">\n" +
                    "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->\n" +
                    "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->\n" +
                    "    <xs:element name=\"notes\">\n" +
                    "        <xs:complexType>\n" +
                    "            <xs:sequence>\n" +
                    "                <xs:element name=\"note\" maxOccurs=\"unbounded\">\n" +
                    "                    <xs:complexType>\n" +
                    "                        <xs:sequence>\n" +
                    "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>\n" +
                    "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>\n" +
                    "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>\n" +
                    "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>\n" +
                    "                        </xs:sequence>\n" +
                    "                    </xs:complexType>\n" +
                    "                </xs:element>\n" +
                    "            </xs:sequence>\n" +
                    "        </xs:complexType>\n" +
                    "    </xs:element>\n</xs:schema>"
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithJsonExactly() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                "type": "JSON",
                "json": JSON.stringify({
                    "id": 1,
                    "name": "A green door",
                    "price": 12.50,
                    "tags": ["home", "green"]
                }),
                "matchType": "STRICT"
            }
        },
        "httpResponse": {
            "statusCode": 202,
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithJsonIgnoringExtraFields() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                "type": "JSON",
                "json": JSON.stringify({
                    "id": 1,
                    "name": "A green door",
                    "price": 12.50,
                    "tags": ["home", "green"]
                })
            }
        },
        "httpResponse": {
            "statusCode": 202,
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithJsonIgnoringExtraFieldsInArrayObjects() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "method": "POST",
            "path": "/json",
            "body": {
                "type": "JSON",
                "json": {
                    "context": [
                        {
                            "source": "DECISION_REQUEST"
                        },
                        {
                            "source": "DECISION_REQUEST"
                        },
                        {
                            "source": "DECISION_REQUEST"
                        }
                    ]
                },
                "matchType": "ONLY_MATCHING_FIELDS"
            }
        },
        "httpResponse": {
            "statusCode": 200,
            "body": "some response"
        },
        "times": {
            "remainingTimes": 1,
            "unlimited": true
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithJsonWithPlaceholders() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                "type": "JSON",
                "json": {
                    "id": 1,
                    "name": "A green door",
                    "price": "${json-unit.ignore-element}",
                    "enabled": "${json-unit.any-boolean}",
                    "tags": ["home", "green"]
                }
            }
        },
        "httpResponse": {
            "statusCode": 202,
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithJsonSchema() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                "type": "JSON_SCHEMA",
                "jsonSchema": {
                    "$schema": "http://json-schema.org/draft-04/schema#",
                    "title": "Product",
                    "description": "A product from Acme's catalog",
                    "type": "object",
                    "properties": {
                        "id": {
                            "description": "The unique identifier for a product",
                            "type": "integer"
                        },
                        "name": {
                            "description": "Name of the product",
                            "type": "string"
                        },
                        "price": {
                            "type": "number",
                            "minimum": 0,
                            "exclusiveMinimum": true
                        },
                        "tags": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            },
                            "minItems": 1,
                            "uniqueItems": true
                        }
                    },
                    "required": [
                        "id",
                        "name",
                        "price"
                    ]
                }
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByBodyWithJsonPath() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                "type": "JSON_PATH",
                "jsonPath": "$.store.book[?(@.price < 10)]"
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByNotMatchingBodyWithJsonPath() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "body": {
                // matches any request with an JSON body that does NOT contain
                // one or more fields that match the JsonPath expression
                "not": true,
                "type": "JSON_PATH",
                "jsonPath": "$.store.book[?(@.price < 10)]"
            }
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

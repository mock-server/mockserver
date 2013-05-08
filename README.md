These instructions are currently being activity edited / created.  I have checked in the work so far but please check back in the next week or so to see the first completed version.

What is MockServer
==================

MockServer is an API to enable the mocking of any system you integrate with via HTTP (i.e. services, web sites, etc)

This API allows you to setup expectations using an example HTTP request and a HTTP response.  When the MockServer then receives a matching request it will provide the specified response.

Requests can be matched on:
* path (regex or plain text)
* body (regex or plain text)
* headers
* cookies
* query parameters (GET)
* body parameters (POST)

Responses can contain:
* status code
* body
* headers
* cookies

MockServer has already been used on multiple large commercial projects to run large test suits that execute in parallel.

Why use MockServer
==================

MockServer allows you to mock any server or service that you connect to over HTTP, such as REST or RPC service.  

This is useful in the following scenarios:
* testing 
 * easily recreate all types of responses for HTTP dependencies such as REST or RPC services to test applications easily and affectively 
 * isolate the system under test to ensure tests run reliably and only fail when there is a genuine bug.  It is important only the system under test is tested and not its dependencies to avoid tests failing due to irrelevant external changes such as network failure or a server being rebooted / redeployed.
 * easily setup mock responses independently for each test to ensure test data is encapsulated with each test.  Avoid sharing data between tests that is difficult to manage and maintain and risks test infecting each other.
* de-coupling development
 * start working against a service API before the service is available.  If an API or service is not yet fully developed MockServer can mock the API allowing the teams who is using the service to start work without being delayed. 
 * isolate development teams particularly critical during the initial development phases when the APIs / services may be extremely unstable and volatile.  Using the mock server allows development work to continue even when an external service fails.

How to use MockServer
=====================

To use the MockServer:
 1. create mock responses
 2. setup mock expectations
 3. run test

A system with service dependancies as follows:

![System In Production](/SystemInProduction.png)

Could be tested with MockServer, mocking the service dependancies, as follows:

![Mocking service dependancies with Mock Server](/SystemUnderTest.png)

1. create mock response
-----------------------

The mock response can include any aspects of a HTTP request including: 
* **response code** i.e. 200, 302, 404, etc
* **body** - a string containing any content
* **cookies** - each with a name and with one or more values, more complex cookies can be modeled by using the a Set-Cookie header
* **headers** - each with a name and one or more values
* **delay** - including both the time unit (java.util.concurrent.TimeUnit) and value

**Java**

To mock a response in Java use the org.mockserver.model.HttpResponse class which specifies the details of each HTTP response with a fluent API:

    public class HttpResponse {

        public HttpResponse withStatusCode(Integer responseCode);
        
        public HttpResponse withCookies(List<Cookie> cookies);
        public HttpResponse withCookies(Cookie... cookies);
        
        public HttpResponse withHeaders(List<Header> headers);
        public HttpResponse withHeaders(Header... headers);
        
        public HttpResponse withBody(String body);

        public HttpResponse withDelay(Delay delay);
    }
    
For example:

    HttpResponse httpResponse =
            new HttpResponse()
                    .withStatusCode(200)
                    .withHeaders(
                            new Header("Content-Type", "application/json; charset=utf-8"),
                            new Header("Cache-Control", "public, max-age=86400")
                    )
                    .withBody("{ message: 'a simple json response' }");
                    

**Javascript**

To mock a response in javascript use JSON to specify the details with the following format:  

    "httpResponse": {
        "statusCode": 200,
        "body": "",
        "cookies": [],
        "headers": [],
        "delay": {
            "timeUnit": "MICROSECONDS",
            "value": 0
        }
    }
    
Each cookie or header array entry has the following syntax:

    {
        "name": "",
        "values": ["", "", ...]
    }
    
The "timeUnit" value in "delay" can be:

    "NANOSECONDS"
    "MICROSECONDS"
    "MILLISECONDS"
    "SECONDS"
    "MINUTES"
    "HOURS"
    "DAYS"

The same example as above would be:

    "httpResponse": {
        "statusCode": 200,
        "body": "{ message: 'a simple json response' }",
        "headers": [
            {
                "name": "Content-Type",
                "values": ["application/json; charset=utf-8"]
            },
            {
                "name": "Cache-Control",
                "values": ["public, max-age=86400"]
            }
        ]
    }
    
2. setup mock expectations
--------------------------

**2.1 Request Matcher**

A mock expectation tells the mock server how to response when receiving a request.  To setup a mock expectation you need to provide the mock response (as described in 1. create mock response) and specify when and how often this response should be provided.  

To specify when a response should be provided a request matcher must be provided.  When the MockServer then receives a request that matches a matching request it will respond with the response specified in the mock expectation.

A request can be matched on the following:
* **method** i.e. GET, POST, PUT, HEAD, etc
* **path** - a regular expression such as "/jamesdbloom/mockserver.*" or an exaxct match
* **body** - a regular expression or an exaxct match
* **parameters** - match of query parameters, not all query parameters need to be specified but those that are specified must match exactly, query parameters not specified will be ignored
* **headers** - not all headers need to be specified but those that are specified must match exactly, headers not specified will be ignored
* **cookies** - not all cookies need to be specified but those that are specified must match exactly, cookies not specified will be ignored

For full details of the regulat expression format supported for body and path see [Java API for java.util.regex.Pattern](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html)

**Java**

To specify a request matcher in Java use the org.mockserver.model.HttpRequest class which specifies the details of each HTTP response with a fluent API:

    public class HttpRequest {
    
        public HttpRequest withMethod(String method);
    
        public HttpRequest withPath(String path);
        
        public HttpRequest withParameters(List<Parameter> parameters);
        public HttpRequest withParameters(Parameter... parameters);
    
        public HttpRequest withBody(String body);
        
        public HttpRequest withHeaders(List<Header> headers);
        public HttpRequest withHeaders(Header... headers);
    
        public HttpRequest withCookies(List<Cookie> cookies);
        public HttpRequest withCookies(Cookie... cookies);
    }
    
For example:

    HttpRequest httpRequest = new HttpRequest()
            .withMethod("POST")
            .withPath("/login")
            .withBody("{username: 'foo', password: 'bar'}")
            .withCookies(
                    new Cookie("sessionId", "2By8LOhBmaW5nZXJwcmludCIlMDAzMW")
            );
                    

**Javascript**

To specify a request matcher in javascript use JSON to specify the details with the following format:  

    "httpRequest": {
        "method": "",
        "path": "",
        "parameters": []
        "body": "",
        "cookies": [],
        "headers": [],
    }
    
Each cookie or header array entry has the following syntax:

    {
        "name": "",
        "values": ["", "", ...]
    }

The same example as above would be:

    "httpRequest": {
        "method": "POST",
        "path": "/login",
        "body": "{username: 'foo', password: 'bar'}",
        "cookies": [
            {
                "name": "sessionId",
                "values": ["2By8LOhBmaW5nZXJwcmludCIlMDAzMW"]
            }
        ],
        "headers": [],
        "parameters": []
    }
    
**2.2 Starting the MockServer**

Before any mock expectation can be sent to the MockServer it must be started.

As the MockServer depends on multiple other projects such as Embedded Jetty it is not possible for this project to provide a complete runnable.  Instead a build script in maven and a build script in gradle has been provided so make it simple to build and run the MockServer.

First clone the repository as follows:

    git clone https://github.com/jamesdbloom/mockservice.git
    
Next build and run the project using either Maven or Gradle.

**Maven**

To build a single executable jar file in maven run the following command:

    mvn clean package

This will produce a jar file under the target directory called mockserver-1.0-SNAPSHOT-jar-with-dependencies.jar

To run the MockServer then use the jar as follows:

    java -jar <path to mockserver-1.0-SNAPSHOT-jar-with-dependencies.jar> <port>
    
For example to run the MockServer on port 9999:

    java -jar target/mockserver-1.0-SNAPSHOT-jar-with-dependencies.jar 9999
    
**Gradle**

In gradle the project can be built and run in a single command as follows:

    gradle run -Pport=<port>
    
For example to run the MockServer on port 9999:

    gradle run -Pport=9999
    
**2.3 Sending Mock Expectation**

Once the mock response and the request matcher has been created these need to be sent to the MockServer to setup a mock expectation.  

**Java**

In Java this can be done as below.  The code below assumes you have started the MockServer on port 9999 and hostname "localhost".

    String hostname = "localhost";
    int port = 9999;

    MockServerClient mockServerClient = new MockServerClient(hostname, port);

    HttpRequest httpRequest = new HttpRequest()
            .withMethod("POST")
            .withPath("/login")
            .withBody("{username: 'foo', password: 'bar'}")
            .withCookies(
                    new Cookie("sessionId", "2By8LOhBmaW5nZXJwcmludCIlMDAzMW")
            );

    HttpResponse httpResponse =
            new HttpResponse()
                    .withStatusCode(200)
                    .withHeaders(
                            new Header("Content-Type", "application/json; charset=utf-8"),
                            new Header("Cache-Control", "public, max-age=86400")
                    )
                    .withBody("{ message: 'a simple json response' }");
                    
    Expectation expectation = new Expectation(httpRequest, Times.unlimited());

    mockServerClient.sendExpectation(expectation).respond(httpResponse));
    
The org.mockserver.matchers.Times class is used to specify how many times you want the MockServer to match a request:
    
To create an instance to Times use one of the static factor methods:

    Times.unlimited();
    Times.once();
    Times.exactly(int count);
    
**Javascript**

TODO

Requirements
============

* Java 7 - because this API uses Jetty 9 to increase reliability, simplicity and flexibility which in turn requires Java 7 (http://webtide.intalio.com/2012/09/jetty-9-features/)


<br/>
<a style="display: none;" href="https://plus.google.com/110954472544793839756?rel=author">James D Bloom</a>

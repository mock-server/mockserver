Mock Server
-----------

What is Mock Server
===================

API to enable the mocking of any system you integrate with via HTTP (i.e. services, web sites, etc)

This API allows you to setup expectations using an example HTTP request and a HTTP response.  When the Mock Server then receives a matching request it will provide the specified response.

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

Mock server has already been used on multiple large commercial projects to run large test suits that execute in parallel.

Why use Mock Server
===================

Mock server allows you to mock any server or service that you connect to over HTTP, such as REST or RPC service.  

This is useful in the following scenarios:
* testing 
 * easily recreate all types of responses for HTTP depedencies such as REST or RPC services to test applications easily and affectively 
 * isolate the system under test to ensure tests run reliably and only fail when there is a genuine bug.  It is important only the system under test is tested and not its dependancies to avoid tests failing due to irrelavent external changes such as network failure or a server being rebooted / redeployed.
 * easily setup mock responses independently for each test to ensure test data is encapsulated with each test.  Avoid sharing data between tests that is difficult to manage and maintane and risks test infecting each other.
* de-coupling development
 * start working against a service API before the service is available.  If an API or service is not yet fully developed mock server can mock the API allowing the teams who is using the service to start work without being delayed. 
 * isolate development teams particularly critical during the initial development phases when the APIs / services may be extremely unstable and volatile.  Using the mock server allows development work to continue even when an external service fails.

Requirements
============

* Java 7 - because this API uses Jetty 9 to increase reliability, simplicity and flexibility which in turn requires Java 7 (http://webtide.intalio.com/2012/09/jetty-9-features/)

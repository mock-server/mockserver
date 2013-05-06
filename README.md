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

Why use Mock Server
===================

Mock server allows you to mock any server or service that you connect to over HTTP, such as REST or RPC service.  

This is useful in the following scenarios:
* testing - mocking HTTP dependencies allows the tests to run repeatably ensuring tests only fail when there is a bug.  It is particularly important to isolate the system under test from external changes that will cause the tests to fail even though those changes are not within the scope of the test.
* de-coupling development - if a API or service is not yet fully developed mock server can mock the API allowing the teams to be isolated. This is particularly helpful during the initial development phases when the API / service may be extremely unstable and unreliable.

Requirements
============

* Java 7 - because this API uses Jetty 9 to increase reliability, simplicity and flexibility which in turn requires Java 7 (http://webtide.intalio.com/2012/09/jetty-9-features/)

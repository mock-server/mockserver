MockServer
========== 

For information on how to use the MockServer please see http://www.mock-server.com/

[![Build Status](https://travis-ci.org/jamesdbloom/mockserver.png?branch=master)](https://travis-ci.org/jamesdbloom/mockserver)
**Unfortunately both Travis CI and drone.io don't seem to be stable enough for MockServer.  The MockServer uses lots of socket connections to complete its integration tests.  The same build with no code or configuration changes run multiple times fails randomly on both Travis CI and drone.io.  It is likely due to the server infrastructure using either a hypervisor or containers that is causing issues with the heavy socket use in the integration tests.  As a result the build status will not be displayed any longer on github or at the top of the homepage.**

# Versions

### Maven Central

Maven Central contains the following MockServer artifacts:

* [mockserver-maven-plugin](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-maven-plugin) - a set of maven plugins to start, stop and fork MockServer using maven
* [mockserver-vertx](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-vertx) - a Vert.X module that mocks HTTP and HTTPS requests
* [mockserver-jetty](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-jetty) - a web server that mocks HTTP and HTTPS requests (using Embedded Jetty)
* [mockserver-jetty:jar-with-dependencies](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-jetty) - a fully stand alone web server embedded with all dependencies that mocks HTTP and HTTPS requests (using Embedded Jetty)
* [mockserver-war](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-war) - a deployable WAR for mocking HTTP and HTTP requests (that runs on any JEE web server)
* [mockserver-proxy](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-proxy) - an HTTP / HTTPS proxy that allows the recording and querying of requests and response
* [mockserver-client](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-client) - a Java and JavaScript client to communicate with both the server and the proxy

In addition MockServer SNAPSHOT artifacts can also be found on [Sonatype](https://oss.sonatype.org/index.html#nexus-search;quick~mockserver).

### Vert.X Module Registry

Vert.X Module Registry contains the following artifacts:

* [org.mock-server~mockserver-vertx~2.0](http://modulereg.vertx.io/) - a Vert.X module that mocks HTTP and HTTPS requests

Vert.X Module Registry will shortly also have the following artifacts:

* [org.mock-server~mockserver-vertx-proxy~2.0](http://modulereg.vertx.io/) - a Vert.X module that proxies HTTP and HTTPS allowing the recording and querying of requests and responses

# Issues

If you have any problems, please [check the project issues](https://github.com/jamesdbloom/mockserver/issues?state=open).

# Contributions

Pull requests are, of course, very welcome! Please read our [contributing to the project](https://github.com/jamesdbloom/mockserver/wiki/Contributing-to-the-project) guide first. Then head over to the [open issues](https://github.com/jamesdbloom/mockserver/issues?state=open) to see what we need help with. Make sure you let us know if you intend to work on something. Also, check out the [milestones](https://github.com/jamesdbloom/mockserver/issues/milestones) to see what is planned for future releases.

# Maintainers
* [James D Bloom](http://blog.jamesdbloom.com)
* [Samira Rabbanian](https://github.com/samirarabbanian)

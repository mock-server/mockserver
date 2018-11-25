MockServer &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Easily%20mock%20any%20system%20you%20integrate%20with%20via%20HTTP%20or%20HTTPS%2C%20or%20analysis%20and%20debug%20systems%20via%20HTTP%20or%20HTTPS%20by%20simple%20transparent%20proxying%20that%20allows%20easy%20inspection%20or%20modification%20of%20in%20flight%20requests&url=http://mock-server.com&via=jamesdbloom&hashtags=mock,proxy,http,testing,debug,developers)&nbsp; [![Build status](https://badge.buildkite.com/3b6803f4fe98cb5ed7bf18292a1434f800b53d8fecb92811d8.svg?branch=master&style=square&theme=slack)](https://buildkite.com/mockserver/mockserver)
========== 



# Documentation

For usage guide please see: [www.mock-server.com](http://www.mock-server.com/)

For chat room: [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/jamesdbloom/mockserver?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# Versions

### Maven Central [![mockserver](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-netty/badge.svg?style=flat)](http://search.maven.org/#search%7Cga%7C1%7Cmockserver)

Maven Central contains the following MockServer artifacts:

* [mockserver-netty](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-netty) - an HTTP(S) web server that mocks and records requests and responses
* [mockserver-netty:jar-with-dependencies](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-netty) - mockserver-netty (as above) with all dependencies embedded
* [mockserver-war](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-war) - a deployable WAR for mocking HTTP(S) responses (for any JEE web server)
* [mockserver-proxy-war](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-proxy-war) - a deployable WAR that records requests and responses (for any JEE web server)
* [mockserver-maven-plugin](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-maven-plugin) - a maven plugin to start, stop and fork MockServer using maven
* [mockserver-client-java](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-client-java) - a Java client to communicate with both the server and the proxy

In addition MockServer SNAPSHOT artifacts can also be found on [Sonatype](https://oss.sonatype.org/index.html#nexus-search;quick~org.mock-server).

### Node Module & Grunt Plugin

NPM Registry contains the following module:

* [mockserver-node](https://www.npmjs.org/package/mockserver-node) - a Node.js module and Grunt plugin to start and stop MockServer
    [![mockserver-node](https://nodei.co/npm/mockserver-node.png?downloads=true)](https://www.npmjs.org/package/mockserver-node)
* [mockserver-client-node](https://www.npmjs.org/package/mockserver-client) - a Node.js client for both the MockServer and the proxy 
    [![mockserver-client-node](https://nodei.co/npm/mockserver-client.png?downloads=true)](https://www.npmjs.org/package/mockserver-client)

### Docker Hub

Docker Hub contains the following artifacts:

* [MockServer Docker Container](https://hub.docker.com/r/jamesdbloom/mockserver/) - a Docker container containing the Netty MockServer and proxy

### Helm Chart

* [MockServer Helm Chart](helm/mockserver/README.md) - a Helm Chart that installs MockServer to a Kubernetes cluster

### MockServer Clients

* [mockserver-client-ruby ![mockserver-client](https://badge.fury.io/rb/mockserver-client.png)](https://rubygems.org/gems/mockserver-client) - Ruby client for both the MockServer and the proxy 
* [mockserver-client-java](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-client-java) - a Java client for both the MockServer and the proxy 
* [mockserver-client-node](https://www.npmjs.org/package/mockserver-client) - a Node.js and [browser](https://rawgit.com/jamesdbloom/mockserver-client-node/mockserver-5.5.0/mockServerClient.js) client for both the MockServer and the proxy

### Document
Date       | Version | Documentation                                  | Java API                                                              | REST API
:--------- |:------- |:---------------------------------------------- |:--------------------------------------------------------------------- |:-----------
20-Nov-15  | 5.5.0	 | [Documentation](http://mock-server.com)	      | [Java API](http://mock-server.com/versions/5.5.0/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.5.x)
20-Jun-18  | 5.4.1	 | [Documentation](https://5-4.mock-server.com)   | [Java API](http://mock-server.com/versions/5.4.1/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.4.x)
25-Dec-17  | 5.3.0	 | [Documentation](https://5-3.mock-server.com)   | [Java API](http://mock-server.com/versions/5.3.0/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
17-Dec-17  | 5.2.3	 | [Documentation](https://5-2.mock-server.com)   | [Java API](http://mock-server.com/versions/5.2.3/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
12-Dec-17  | 5.2.2	 | [Documentation](https://5-2.mock-server.com)   | [Java API](http://mock-server.com/versions/5.2.2/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
11-Dec-17  | 5.2.1	 | [Documentation](https://5-2.mock-server.com)   | [Java API](http://mock-server.com/versions/5.2.1/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
10-Dec-17  | 5.2.0	 | [Documentation](https://5-2.mock-server.com)   | [Java API](http://mock-server.com/versions/5.2.0/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
06-Dec-17  | 5.1.1	 | [Documentation](https://5-1.mock-server.com)   | [Java API](http://mock-server.com/versions/5.1.1/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.1.x)
05-Dec-17  | 5.1.0	 | [Documentation](https://5-1.mock-server.com)   | [Java API](http://mock-server.com/versions/5.1.0/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.1.x)
05-Dec-17  | 5.0.1	 | [Documentation](https://5-0.mock-server.com)   | [Java API](http://mock-server.com/versions/5.0.1/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.0.x)
04-Dec-17  | 5.0.0	 | [Documentation](https://5-0.mock-server.com)   | [Java API](http://mock-server.com/versions/5.0.0/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.0.x)
30-Nov-17  | 4.1.0	 | [Documentation](https://4-1.mock-server.com)   | [Java API](http://mock-server.com/versions/4.1.0/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/4.x.x)
28-Nov-17  | 4.0.0	 | [Documentation](https://4-0.mock-server.com)   | [Java API](http://mock-server.com/versions/4.0.0/apidocs/index.html)  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/4.x.x)
16-Nov-17  | 3.12	 |  | [Java API](http://mock-server.com/versions/3.12/apidocs/index.html)   | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
10-Sep-17  | 3.11	 |  | [Java API](http://mock-server.com/versions/3.11/apidocs/index.html)   | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
22-Jun-17  | 3.10.8	 |  | [Java API](http://mock-server.com/versions/3.10.8/apidocs/index.html) | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
02-May-17  | 3.10.7	 |  | [Java API](http://mock-server.com/versions/3.10.7/apidocs/index.html) | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
27-Apr-17  | 3.10.6	 |  | [Java API](http://mock-server.com/versions/3.10.6/apidocs/index.html) | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
25-Apr-17  | 3.10.5	 |  | [Java API](http://mock-server.com/versions/3.10.5/apidocs/index.html) | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
06-Mar-16  | 3.10.4	 |  |  | [REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)

# Issues

If you have any problems, please [check the project issues](https://github.com/jamesdbloom/mockserver/issues?state=open).

# Contributions

Pull requests are, of course, very welcome! Please read our [contributing to the project](https://github.com/jamesdbloom/mockserver/wiki/Contributing-to-the-project) guide first. Then head over to the [open issues](https://github.com/jamesdbloom/mockserver/issues?state=open) to see what we need help with. Make sure you let us know if you intend to work on something. Also, check out the [milestones](https://github.com/jamesdbloom/mockserver/milestones) to see what is planned for future releases.

# Maintainers
* [James D Bloom](http://blog.jamesdbloom.com)

[![Analytics](https://ga-beacon.appspot.com/UA-32687194-4/mockserver/README.md)](https://github.com/igrigorik/ga-beacon)

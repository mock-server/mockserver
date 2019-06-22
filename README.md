MockServer &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Easily%20mock%20any%20system%20you%20integrate%20with%20via%20HTTP%20or%20HTTPS%2C%20or%20analysis%20and%20debug%20systems%20via%20HTTP%20or%20HTTPS%20by%20simple%20transparent%20proxying%20that%20allows%20easy%20inspection%20or%20modification%20of%20in%20flight%20requests&url=http://mock-server.com&hashtags=mock,proxy,http,testing,debug,developers)&nbsp; [![Build status](https://badge.buildkite.com/3b6803f4fe98cb5ed7bf18292a1434f800b53d8fecb92811d8.svg?branch=master&style=square&theme=slack)](https://buildkite.com/mockserver/mockserver)&nbsp; 
[![GitHub license](https://img.shields.io/github/license/jamesdbloom/mockserver.svg)](https://github.com/jamesdbloom/mockserver/blob/master/LICENSE.md)&nbsp; 
[![GitHub stars](https://img.shields.io/github/stars/jamesdbloom/mockserver.svg)](https://github.com/jamesdbloom/mockserver/stargazers)
=====

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

* [MockServer Helm Chart](helm/mockserver/README.md) - a Helm Chart that installs MockServer to a Kubernetes cluster, available versions:
  * [5.6.0](http://www.mock-server.com/mockserver-5.6.0.tgz) 
  * [5.5.4](http://www.mock-server.com/mockserver-5.5.4.tgz) 
  * [5.5.1](http://www.mock-server.com/mockserver-5.5.1.tgz) 
  * [5.5.0](http://www.mock-server.com/mockserver-5.5.0.tgz) 
  * [5.4.1](http://www.mock-server.com/mockserver-5.4.1.tgz) 
  * [5.3.0](http://www.mock-server.com/mockserver-5.3.0.tgz)

### MockServer Clients

* [mockserver-client-ruby ![mockserver-client](https://badge.fury.io/rb/mockserver-client.png)](https://rubygems.org/gems/mockserver-client) - Ruby client for both the MockServer and the proxy 
* [mockserver-client-java](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-client-java) - a Java client for both the MockServer and the proxy 
* [mockserver-client-node](https://www.npmjs.org/package/mockserver-client) - a Node.js and [browser](https://rawgit.com/jamesdbloom/mockserver-client-node/mockserver-5.6.0/mockServerClient.js) client for both the MockServer and the proxy

### Document
Version | Date        | Git & Docker Tag / Git Hash                                                                                                                                                                 | Documentation                                                         | Java API                                                              | REST API
:-------|:------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------- |:--------------------------------------------------------------------- |:---------------------------------------------------------------------------------
5.6.0   | 26 Apr 2019 | [mockserver-5.6.0](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.6.0) / [8f82dc](https://github.com/jamesdbloom/mockserver/commit/8f82dc4d37271c3cbfe0b3a1963e91ec3a4ef7a7)   | [Documentation](http://mock-server.com)	                            | [Java API](http://mock-server.com/versions/5.6.0/apidocs/index.html)  | [5.6.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.6.x)
5.5.4   | 22 Jun 2019 | [mockserver-5.5.4](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.5.4) / [4ffd31](https://github.com/jamesdbloom/mockserver/commit/4ffd3162a3250f18d343901b30c3ee71a75b1982)   | [Documentation](https://5-5.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.5.4/apidocs/index.html)  | [5.5.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.5.x)
5.5.1   | 28 Dec 2018 | [mockserver-5.5.1](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.5.1) / [11d8a9](https://github.com/jamesdbloom/mockserver/commit/11d8a96b0eaf07b7fffd29444203503b1cdca653)   | [Documentation](https://5-5.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.5.1/apidocs/index.html)  | [5.5.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.5.x)
5.5.0   | 15 Nov 2018 | [mockserver-5.5.0](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.5.0) / [06e6fd](https://github.com/jamesdbloom/mockserver/commit/06e6fdc4757f13fb5943fc281d5e55dc1c30919d)   | [Documentation](https://5-5.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.5.0/apidocs/index.html)  | [5.5.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.5.x)
5.4.1   | 20 Jun 2018 | [mockserver-5.4.1](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.4.1) / [7cd5de](https://github.com/jamesdbloom/mockserver/commit/7cd5defc7463e8773d011467147a8a0f7e7b4af8)   | [Documentation](https://5-4.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.4.1/apidocs/index.html)  | [5.4.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.4.x)
5.3.0   | 25 Dec 2017 | [mockserver-5.3.0](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.3.0) / [ad62bb](https://github.com/jamesdbloom/mockserver/commit/ad62bbc4fdc1470818ffab14630623dc591ead74)   | [Documentation](https://5-3.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.3.0/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.2.3   | 17 Dec 2017 | [mockserver-5.2.3](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.2.3) / [e81c53](https://github.com/jamesdbloom/mockserver/commit/e81c53852b763f88b2399090ef414f074b3e3d81)   | [Documentation](https://5-2.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.2.3/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.2.2   | 12 Dec 2017 | [mockserver-5.2.2](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.2.2) / [b47090](https://github.com/jamesdbloom/mockserver/commit/b47090b579d35c7136b84378402ff466db0bfb60)   | [Documentation](https://5-2.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.2.2/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.2.1   | 11 Dec 2017 | [mockserver-5.2.1](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.2.1) / [834ec8](https://github.com/jamesdbloom/mockserver/commit/834ec8fcac335b10d09183cecfe6dae358a4080c)   | [Documentation](https://5-2.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.2.1/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.2.0   | 10 Dec 2017 | [mockserver-5.2.0](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.2.0) / [ccb4d2](https://github.com/jamesdbloom/mockserver/commit/ccb4d241b55dcebc9f8abfb3722cadad143f3acf)   | [Documentation](https://5-2.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.2.0/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.1.1   | 06 Dec 2017 | [mockserver-5.1.1](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.1.1) / [664afb](https://github.com/jamesdbloom/mockserver/commit/664afb2c539333ce89559fb3153e56bc48ba9cb5)   | [Documentation](https://5-1.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.1.1/apidocs/index.html)  | [5.1.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.1.x)
5.1.0   | 05 Dec 2017 | [mockserver-5.1.0](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.1.0) / [bbdda1](https://github.com/jamesdbloom/mockserver/commit/bbdda1898eb3f396d56f7268faa6c2a644449ae3)   | [Documentation](https://5-1.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.1.0/apidocs/index.html)  | [5.1.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.1.x)
5.0.1   | 05 Dec 2017 | [mockserver-5.0.1](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.0.1) / [975fb8](https://github.com/jamesdbloom/mockserver/commit/975fb8971da1cd32891201733a2bc6aa4080d7ae)   | [Documentation](https://5-0.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.0.1/apidocs/index.html)  | [5.0.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.0.x)
5.0.0   | 04 Dec 2017 | [mockserver-5.0.0](https://github.com/jamesdbloom/mockserver/tree/mockserver-5.0.0) / [ed5d13](https://github.com/jamesdbloom/mockserver/commit/ed5d13e863a25e00ab404735e183df2ce4afe635)   | [Documentation](https://5-0.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.0.0/apidocs/index.html)  | [5.0.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.0.x)
4.1.0   | 30 Nov 2017 | [mockserver-4.1.0](https://github.com/jamesdbloom/mockserver/tree/mockserver-4.1.0) / [4e37b2](https://github.com/jamesdbloom/mockserver/commit/4e37b27b9b1bc786d0b5f53d5f1a39dd457f5d34)   | [Documentation](https://4-1.mock-server.com)                          | [Java API](http://mock-server.com/versions/4.1.0/apidocs/index.html)  | [4.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/4.x.x)
4.0.0   | 28 Nov 2017 | [mockserver-4.0.0](https://github.com/jamesdbloom/mockserver/tree/mockserver-4.0.0) / [8b2455](https://github.com/jamesdbloom/mockserver/commit/8b24553c6b7aabbe4ef5e99b37449330f5b908d7)   | [Documentation](https://4-0.mock-server.com)                          | [Java API](http://mock-server.com/versions/4.0.0/apidocs/index.html)  | [4.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/4.x.x)
3.12    | 16 Nov 2017 | [mockserver-3.12](https://github.com/jamesdbloom/mockserver/tree/mockserver-3.12) / [d2a2b1](https://github.com/jamesdbloom/mockserver/commit/d2a2b1b7399e8405f2d19bc105c99a0a26327c61)     |                                                                       | [Java API](http://mock-server.com/versions/3.12/apidocs/index.html)   | [3.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
3.11    | 10 Sep 2017 | [mockserver-3.11](https://github.com/jamesdbloom/mockserver/tree/mockserver-3.11) / [fdcb11](https://github.com/jamesdbloom/mockserver/commit/fdcb1113ecd075ec7d9b1d065ed778dadebb1772)     |                                                                       | [Java API](http://mock-server.com/versions/3.11/apidocs/index.html)   | [3.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
3.10.8  | 22 Jun 2017 | [mockserver-3.10.8](https://github.com/jamesdbloom/mockserver/tree/mockserver-3.10.8) / [0e6e12](https://github.com/jamesdbloom/mockserver/commit/0e6e1227f5e3d5d9faa68434d3ed708edee7b9ee) |                                                                       | [Java API](http://mock-server.com/versions/3.10.8/apidocs/index.html) | [3.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
3.10.7  | 02 May 2017 | [mockserver-3.10.7](https://github.com/jamesdbloom/mockserver/tree/mockserver-3.10.7) / [99abb2](https://github.com/jamesdbloom/mockserver/commit/99abb290e31e9a65706e64a360f4ad318723f0ba) |                                                                       | [Java API](http://mock-server.com/versions/3.10.7/apidocs/index.html) | [3.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
3.10.6  | 27 Apr 2017 | [mockserver-3.10.6](https://github.com/jamesdbloom/mockserver/tree/mockserver-3.10.6) / [219897](https://github.com/jamesdbloom/mockserver/commit/2198972a3911efcf0fa116f4cdd0851ab31699c1) |                                                                       | [Java API](http://mock-server.com/versions/3.10.6/apidocs/index.html) | [3.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)
3.10.5  | 25 Apr 2017 | [mockserver-3.10.5](https://github.com/jamesdbloom/mockserver/tree/mockserver-3.10.5) / [a59e75](https://github.com/jamesdbloom/mockserver/commit/a59e750432f9d9431c1c6352953e1309d53178fc) |                                                                       | [Java API](http://mock-server.com/versions/3.10.5/apidocs/index.html) | [3.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server_api/3.x.x)

# Issues

If you have any problems, please [check the project issues](https://github.com/jamesdbloom/mockserver/issues?state=open).

# Contributions

Pull requests are, of course, very welcome! Please read our [contributing to the project](https://github.com/jamesdbloom/mockserver/wiki/Contributing-to-the-project) guide first. Then head over to the [open issues](https://github.com/jamesdbloom/mockserver/issues?state=open) to see what we need help with. Make sure you let us know if you intend to work on something. Also, check out the [milestones](https://github.com/jamesdbloom/mockserver/milestones) to see what is planned for future releases.

# Maintainers
* [James D Bloom](http://blog.jamesdbloom.com)

[![Analytics](https://ga-beacon.appspot.com/UA-32687194-4/mockserver/README.md)](https://github.com/igrigorik/ga-beacon)

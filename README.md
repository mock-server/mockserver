MockServer &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; [![Tweet](https://img.shields.io/twitter/url/http/shields.io.svg?style=social)](https://twitter.com/intent/tweet?text=Easily%20mock%20any%20system%20you%20integrate%20with%20via%20HTTP%20or%20HTTPS%2C%20or%20analysis%20and%20debug%20systems%20via%20HTTP%20or%20HTTPS%20by%20simple%20transparent%20proxying%20that%20allows%20easy%20inspection%20or%20modification%20of%20in%20flight%20requests&url=http://mock-server.com&hashtags=mock,proxy,http,testing,debug,developers) [![Build status](https://badge.buildkite.com/3b6803f4fe98cb5ed7bf18292a1434f800b53d8fecb92811d8.svg?branch=master&style=square&theme=slack)](https://buildkite.com/mockserver/mockserver) 
[![GitHub license](https://img.shields.io/github/license/mock-server/mockserver.svg)](https://github.com/mock-server/mockserver/blob/master/LICENSE.md) 
[![GitHub stars](https://img.shields.io/github/stars/mock-server/mockserver.svg)](https://github.com/mock-server/mockserver/stargazers)&nbsp;<a href="https://trello.com/b/dsfTCP46/mockserver"><img height="20px" width="47px" src="http://mock-server.com/images/trello_badge.png" alt="Trello Backlog"></a>&nbsp;&nbsp;<a height="40px" width="66px" href="https://join-mock-server-slack.herokuapp.com"><img height="40px" src="http://mock-server.com/images/slack-logo-slim.png" alt="Join Slack"></a>
=====

### Documentation

For usage guide please see: [www.mock-server.com](http://www.mock-server.com/)

### Change Log

Please see: [Change Log](https://github.com/mock-server/mockserver/blob/master/changelog.md)

### Community

<table>
    <tr> 
        <td>Chat</td>
        <td><a href="https://join-mock-server-slack.herokuapp.com" target="_blank"><img height="20px" src="http://mock-server.com/images/slack-logo-slim-md.png" alt="Join Slack"></a></td>
    </tr>
    <tr>
        <td>Feature Requests</td>
        <td><a href="https://github.com/mock-server/mockserver/issues"><img height="20px" src="http://mock-server.com/images/GitHub_Logo-md.png" alt="Github Issues"></a></td>
    </tr>
    <tr> 
        <td>Issues / Bugs</td>
        <td><a href="https://github.com/mock-server/mockserver/issues"><img height="20px" src="http://mock-server.com/images/GitHub_Logo-md.png" alt="Github Issues"></a></td>
    </tr>
    <tr>
        <td>Backlog</td>
        <td><a href="https://trello.com/b/dsfTCP46/mockserver" target="_blank"><img height="20px" src="http://mock-server.com/images/trello_badge-md.png" alt="Trello Backlog"></a></td>
    </tr>
</table>

### Versions

##### Maven Central [![mockserver](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-netty/badge.svg?style=flat)](http://search.maven.org/#search%7Cga%7C1%7Cmockserver)

Maven Central contains the following MockServer artifacts:

* [mockserver-netty](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-netty) - an HTTP(S) web server that mocks and records requests and responses
* [mockserver-netty:jar-with-dependencies](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-netty) - mockserver-netty (as above) with all dependencies embedded
* [mockserver-war](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-war) - a deployable WAR for mocking HTTP(S) responses (for any JEE web server)
* [mockserver-proxy-war](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-proxy-war) - a deployable WAR that records requests and responses (for any JEE web server)
* [mockserver-maven-plugin](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-maven-plugin) - a maven plugin to start, stop and fork MockServer using maven
* [mockserver-client-java](https://maven-badges.herokuapp.com/maven-central/org.mock-server/mockserver-client-java) - a Java client to communicate with both the server and the proxy

In addition MockServer SNAPSHOT artifacts can also be found on [Sonatype](https://oss.sonatype.org/index.html#nexus-search;quick~org.mock-server).

##### Node Module & Grunt Plugin

NPM Registry contains the following module:

* [mockserver-node](https://www.npmjs.org/package/mockserver-node) - a Node.js module and Grunt plugin to start and stop MockServer
    [![mockserver-node](https://nodei.co/npm/mockserver-node.png?downloads=true)](https://www.npmjs.org/package/mockserver-node)
* [mockserver-client-node](https://www.npmjs.org/package/mockserver-client) - a Node.js client for both the MockServer and the proxy 
    [![mockserver-client-node](https://nodei.co/npm/mockserver-client.png?downloads=true)](https://www.npmjs.org/package/mockserver-client)

##### Docker Hub

Docker Hub contains the following artifacts:

* [MockServer Docker Container](https://hub.docker.com/r/mockserver/mockserver/) - a Docker container containing the Netty MockServer and proxy

##### Helm Chart

* [MockServer Helm Chart](helm/mockserver/README.md) - a Helm Chart that installs MockServer to a Kubernetes cluster, available versions:
  * [5.12.0](http://www.mock-server.com/mockserver-5.12.0.tgz)
  * [5.11.2](http://www.mock-server.com/mockserver-5.11.2.tgz)
  * [5.11.1](http://www.mock-server.com/mockserver-5.11.1.tgz)
  * [5.11.0](http://www.mock-server.com/mockserver-5.11.0.tgz)
  * [5.10.0](http://www.mock-server.com/mockserver-5.10.0.tgz)
  * [5.9.0](http://www.mock-server.com/mockserver-5.9.0.tgz)
  * [5.8.1](http://www.mock-server.com/mockserver-5.8.1.tgz)
  * [5.8.0](http://www.mock-server.com/mockserver-5.8.0.tgz)
  * [5.7.2](http://www.mock-server.com/mockserver-5.7.2.tgz)
  * [5.7.1](http://www.mock-server.com/mockserver-5.7.1.tgz) 
  * [5.7.0](http://www.mock-server.com/mockserver-5.7.0.tgz) 
  * [5.6.1](http://www.mock-server.com/mockserver-5.6.1.tgz) 
  * [5.6.0](http://www.mock-server.com/mockserver-5.6.0.tgz) 
  * [5.5.4](http://www.mock-server.com/mockserver-5.5.4.tgz) 
  * [5.5.1](http://www.mock-server.com/mockserver-5.5.1.tgz) 
  * [5.5.0](http://www.mock-server.com/mockserver-5.5.0.tgz) 
  * [5.4.1](http://www.mock-server.com/mockserver-5.4.1.tgz) 
  * [5.3.0](http://www.mock-server.com/mockserver-5.3.0.tgz)

##### MockServer Clients

* [mockserver-client-ruby ![mockserver-client](https://badge.fury.io/rb/mockserver-client.png)](https://rubygems.org/gems/mockserver-client) - Ruby client for both the MockServer and the proxy 
* [mockserver-client-java](http://search.maven.org/#search%7Cga%7C1%7Cmockserver-client-java) - a Java client for both the MockServer and the proxy 
* [mockserver-client-node](https://www.npmjs.org/package/mockserver-client) - a Node.js and [browser](https://raw.githubusercontent.com/mock-server/mockserver-client-node/mockserver-5.10.0/mockServerClient.js) client for both the MockServer and the proxy

##### Previous Versions
Version | Date        | Git & Docker Tag / Git Hash                                                                                                                                                                 | Documentation                                                         | Java API                                                              | REST API
:-------|:------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------- |:--------------------------------------------------------------------- |:---------------------------------------------------------------------------------------
5.12.0  | 12 Feb 2022 | [mockserver-5.12.0](https://github.com/mock-server/mockserver/tree/mockserver-5.12.0) / [61747f](https://github.com/mock-server/mockserver/commit/61747fd20316603e7ff4c0dd0e3ee34ea386882f) | [Documentation](http://mock-server.com)	                            | [Java API](http://mock-server.com/versions/5.12.0/apidocs/index.html) | [5.12.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.12.x)
5.11.2  | 08 Nov 2020 | [mockserver-5.11.2](https://github.com/mock-server/mockserver/tree/mockserver-5.11.2) / [eb84f2](https://github.com/mock-server/mockserver/commit/eb84f20b9485233c6926e4067e1e8de652a112d6) | [Documentation](http://5-11.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.11.2/apidocs/index.html) | [5.11.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.11.x)
5.11.1  | 22 Jul 2020 | [mockserver-5.11.1](https://github.com/mock-server/mockserver/tree/mockserver-5.11.1) / [361e5c](https://github.com/mock-server/mockserver/commit/361e5c74e5c7fd906957edbd5a46bb27582e4f5c) | [Documentation](http://5-11.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.11.1/apidocs/index.html) | [5.11.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.11.x)
5.11.0  | 08 Jul 2020 | [mockserver-5.11.0](https://github.com/mock-server/mockserver/tree/mockserver-5.11.0) / [756758](https://github.com/mock-server/mockserver/commit/756758ebe3d032f3852411a9bb91c3c66d819ddc) | [Documentation](http://5-11.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.11.0/apidocs/index.html) | [5.11.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.11.x)
5.10.0  | 24 Mar 2020 | [mockserver-5.10.0](https://github.com/mock-server/mockserver/tree/mockserver-5.10.0) / [14124d](https://github.com/mock-server/mockserver/commit/14124d32ef96c207cc73cc5334c1d7236d8c7640) | [Documentation](http://5-10.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.10.0/apidocs/index.html) | [5.10.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.10.x)
5.9.0   | 01 Feb 2020 | [mockserver-5.9.0](https://github.com/mock-server/mockserver/tree/mockserver-5.9.0)   / [eacf07](https://github.com/mock-server/mockserver/commit/eacf07ad1eb738bacbf7c473f0d1aa62b4028602) | [Documentation](http://5-9.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.9.0/apidocs/index.html)  | [5.9.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.9.x)
5.8.1   | 23 Dec 2019 | [mockserver-5.8.1](https://github.com/mock-server/mockserver/tree/mockserver-5.8.1)   / [f0e9ab](https://github.com/mock-server/mockserver/commit/f0e9ab3b64f47f7f8f756d5ae8bf7b1b4611d8e6) | [Documentation](http://5-8.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.8.1/apidocs/index.html)  | [5.8.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.8.x)
5.8.0   | 01 Dec 2019 | [mockserver-5.8.0](https://github.com/mock-server/mockserver/tree/mockserver-5.8.0)   / [7c9fc5](https://github.com/mock-server/mockserver/commit/7c9fc5e5e831feac71dd68d0341ff089f37cec1e) | [Documentation](http://5-8.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.8.0/apidocs/index.html)  | [5.8.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.8.x)
5.7.2   | 16 Nov 2019 | [mockserver-5.7.2](https://github.com/mock-server/mockserver/tree/mockserver-5.7.2)   / [7c9fc5](https://github.com/mock-server/mockserver/commit/7c9fc5e5e831feac71dd68d0341ff089f37cec1e) | [Documentation](http://5-7.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.7.2/apidocs/index.html)  | [5.7.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.7.x)
5.7.1   | 09 Nov 2019 | [mockserver-5.7.1](https://github.com/mock-server/mockserver/tree/mockserver-5.7.1)   / [0ca353](https://github.com/mock-server/mockserver/commit/0ca3537023e9e0f9abcb09c92279891cbc0527c7) | [Documentation](http://5-7.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.7.1/apidocs/index.html)  | [5.7.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.7.x)
5.7.0   | 01 Nov 2019 | [mockserver-5.7.0](https://github.com/mock-server/mockserver/tree/mockserver-5.7.0)   / [b58bc5](https://github.com/mock-server/mockserver/commit/b58bc589efbc76272a2053a64e774a001f1bb0a2) | [Documentation](http://5-7.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.7.0/apidocs/index.html)  | [5.7.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.7.x)
5.6.1   | 21 Jul 2019 | [mockserver-5.6.1](https://github.com/mock-server/mockserver/tree/mockserver-5.6.1)   / [aec1fb](https://github.com/mock-server/mockserver/commit/aec1fbf1e826dc59fe4a19c3331ab6802ec4c3c7) | [Documentation](https://5-6.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.6.0/apidocs/index.html)  | [5.6.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.6.x)
5.6.0   | 21 Jun 2019 | [mockserver-5.6.0](https://github.com/mock-server/mockserver/tree/mockserver-5.6.0)   / [8f82dc](https://github.com/mock-server/mockserver/commit/8f82dc4d37271c3cbfe0b3a1963e91ec3a4ef7a7) | [Documentation](https://5-6.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.6.0/apidocs/index.html)  | [5.6.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.6.x)
5.5.4   | 26 Apr 2019 | [mockserver-5.5.4](https://github.com/mock-server/mockserver/tree/mockserver-5.5.4)   / [4ffd31](https://github.com/mock-server/mockserver/commit/4ffd3162a3250f18d343901b30c3ee71a75b1982) | [Documentation](https://5-5.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.5.4/apidocs/index.html)  | [5.5.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.5.x)
5.5.1   | 29 Dec 2018 | [mockserver-5.5.1](https://github.com/mock-server/mockserver/tree/mockserver-5.5.1)   / [11d8a9](https://github.com/mock-server/mockserver/commit/11d8a96b0eaf07b7fffd29444203503b1cdca653) | [Documentation](https://5-5.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.5.1/apidocs/index.html)  | [5.5.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.5.x)
5.5.0   | 15 Nov 2018 | [mockserver-5.5.0](https://github.com/mock-server/mockserver/tree/mockserver-5.5.0)   / [06e6fd](https://github.com/mock-server/mockserver/commit/06e6fdc4757f13fb5943fc281d5e55dc1c30919d) | [Documentation](https://5-5.mock-server.com)	                        | [Java API](http://mock-server.com/versions/5.5.0/apidocs/index.html)  | [5.5.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.5.x)
5.4.1   | 20 Jun 2018 | [mockserver-5.4.1](https://github.com/mock-server/mockserver/tree/mockserver-5.4.1)   / [7cd5de](https://github.com/mock-server/mockserver/commit/7cd5defc7463e8773d011467147a8a0f7e7b4af8) | [Documentation](https://5-4.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.4.1/apidocs/index.html)  | [5.4.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.4.x)
5.3.0   | 25 Dec 2017 | [mockserver-5.3.0](https://github.com/mock-server/mockserver/tree/mockserver-5.3.0)   / [ad62bb](https://github.com/mock-server/mockserver/commit/ad62bbc4fdc1470818ffab14630623dc591ead74) | [Documentation](https://5-3.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.3.0/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.2.3   | 17 Dec 2017 | [mockserver-5.2.3](https://github.com/mock-server/mockserver/tree/mockserver-5.2.3)   / [e81c53](https://github.com/mock-server/mockserver/commit/e81c53852b763f88b2399090ef414f074b3e3d81) | [Documentation](https://5-2.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.2.3/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.2.2   | 12 Dec 2017 | [mockserver-5.2.2](https://github.com/mock-server/mockserver/tree/mockserver-5.2.2)   / [b47090](https://github.com/mock-server/mockserver/commit/b47090b579d35c7136b84378402ff466db0bfb60) | [Documentation](https://5-2.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.2.2/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.2.1   | 11 Dec 2017 | [mockserver-5.2.1](https://github.com/mock-server/mockserver/tree/mockserver-5.2.1)   / [834ec8](https://github.com/mock-server/mockserver/commit/834ec8fcac335b10d09183cecfe6dae358a4080c) | [Documentation](https://5-2.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.2.1/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.2.0   | 10 Dec 2017 | [mockserver-5.2.0](https://github.com/mock-server/mockserver/tree/mockserver-5.2.0)   / [ccb4d2](https://github.com/mock-server/mockserver/commit/ccb4d241b55dcebc9f8abfb3722cadad143f3acf) | [Documentation](https://5-2.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.2.0/apidocs/index.html)  | [5.2.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.2.x)
5.1.1   | 06 Dec 2017 | [mockserver-5.1.1](https://github.com/mock-server/mockserver/tree/mockserver-5.1.1)   / [664afb](https://github.com/mock-server/mockserver/commit/664afb2c539333ce89559fb3153e56bc48ba9cb5) | [Documentation](https://5-1.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.1.1/apidocs/index.html)  | [5.1.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.1.x)
5.1.0   | 05 Dec 2017 | [mockserver-5.1.0](https://github.com/mock-server/mockserver/tree/mockserver-5.1.0)   / [bbdda1](https://github.com/mock-server/mockserver/commit/bbdda1898eb3f396d56f7268faa6c2a644449ae3) | [Documentation](https://5-1.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.1.0/apidocs/index.html)  | [5.1.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.1.x)
5.0.1   | 05 Dec 2017 | [mockserver-5.0.1](https://github.com/mock-server/mockserver/tree/mockserver-5.0.1)   / [975fb8](https://github.com/mock-server/mockserver/commit/975fb8971da1cd32891201733a2bc6aa4080d7ae) | [Documentation](https://5-0.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.0.1/apidocs/index.html)  | [5.0.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.0.x)
5.0.0   | 04 Dec 2017 | [mockserver-5.0.0](https://github.com/mock-server/mockserver/tree/mockserver-5.0.0)   / [ed5d13](https://github.com/mock-server/mockserver/commit/ed5d13e863a25e00ab404735e183df2ce4afe635) | [Documentation](https://5-0.mock-server.com)                          | [Java API](http://mock-server.com/versions/5.0.0/apidocs/index.html)  | [5.0.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.0.x)
4.1.0   | 30 Nov 2017 | [mockserver-4.1.0](https://github.com/mock-server/mockserver/tree/mockserver-4.1.0)   / [4e37b2](https://github.com/mock-server/mockserver/commit/4e37b27b9b1bc786d0b5f53d5f1a39dd457f5d34) | [Documentation](https://4-1.mock-server.com)                          | [Java API](http://mock-server.com/versions/4.1.0/apidocs/index.html)  | [4.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/4.x.x)
4.0.0   | 28 Nov 2017 | [mockserver-4.0.0](https://github.com/mock-server/mockserver/tree/mockserver-4.0.0)   / [8b2455](https://github.com/mock-server/mockserver/commit/8b24553c6b7aabbe4ef5e99b37449330f5b908d7) | [Documentation](https://4-0.mock-server.com)                          | [Java API](http://mock-server.com/versions/4.0.0/apidocs/index.html)  | [4.x.x REST API](https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/4.x.x)

### Issues

If you have any problems, please [check the project issues](https://github.com/mock-server/mockserver/issues?state=open) and avoid opening issues that have already been fixed.  When you open an issue please provide the following information:
- MockServer version (i.e. 5.11.2)
- How your running the MockServer (i.e maven plugin, docker, etc)
- MockServer log output, at INFO level (or higher)
- What the error is
- What you are trying to do

### Contributions

Pull requests are, of course, very welcome! Please read our [contributing to the project](https://github.com/mock-server/mockserver/wiki/Contributing-to-the-project) guide first. Then head over to the [open issues](https://github.com/mock-server/mockserver/issues?state=open) to see what we need help with. Make sure you let us know if you intend to work on something. Also check out <a href="https://trello.com/b/dsfTCP46/mockserver" target="_blank"><img height="20px" src="http://mock-server.com/images/trello_badge-md.png" alt="Trello Backlog"></a> to see what is already in the backlog.

### Feature Requests

Feature requests are submitted to [github issues](https://github.com/mock-server/mockserver/issues?state=open).  Once accepted they will be added to the <a href="https://trello.com/b/dsfTCP46/mockserver" target="_blank">backlog</a>.  Please check out <a href="https://trello.com/b/dsfTCP46/mockserver" target="_blank"><img height="20px" src="http://mock-server.com/images/trello_badge-md.png" alt="Trello Backlog"></a> to see what is already in the backlog.

### Maintainers
* [James D Bloom](http://blog.jamesdbloom.com)

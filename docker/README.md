[MockServer](http://www.mock-server.com)
==========

### Dependencies

* [dockerfile/java](http://dockerfile.github.io/#/java)

### Installation

1. Install [Docker](https://www.docker.io/).

2. Download [trusted build](https://index.docker.io/u/jamesdbloom/mockserver/) from public [Docker Registry](https://index.docker.io/): `docker pull jamesdbloom/mockserver`

### Usage

    docker run -d jamesdbloom/mockserver

### What is MockServer

MockServer is for mocking of any system you integrate with via HTTP or HTTPS (i.e. services, web sites, etc).

MockServer supports:

* mocking of any HTTP / HTTPS response when any request is matched ([learn more](http://www.mock-server.com/#mocking))
* recording requests and responses to analyse how a system behaves ([learn more](http://www.mock-server.com/#proxying))
* verifying which requests and responses have been sent as part of a test ([learn more](http://www.mock-server.com/#proxying))

This docker container will run an instance of the MockServer on the following ports:

* serverPort **8080**
* proxyPort **9090**

For information on how to use the MockServer please see http://www.mock-server.com

### Issues

If you have any problems, please [check the project issues](https://github.com/jamesdbloom/mockserver/issues?state=open).

### Contributions

Pull requests are, of course, very welcome! Please read our [contributing to the project](https://github.com/jamesdbloom/mockserver/wiki/Contributing-to-the-project) guide first. Then head over to the [open issues](https://github.com/jamesdbloom/mockserver/issues?state=open) to see what we need help with. Make sure you let us know if you intend to work on something. Also, check out the [milestones](https://github.com/jamesdbloom/mockserver/issues/milestones) to see what is planned for future releases.

### Maintainers
* [James D Bloom](http://blog.jamesdbloom.com)
* [Samira Bloom](https://github.com/samirabloom)

[MockServer](http://www.mock-server.com)
==========

### Dependencies

* [java](https://registry.hub.docker.com/u/library/java/)

### Installation

1. Install [Docker](https://www.docker.io/).

2. Download [trusted build](https://hub.docker.com/r/mockserver/mockserver) from public [Docker Registry](https://index.docker.io/): `docker pull mockserver/mockserver`

### Usage

* **FIRST** ensure you have the latest version

```bash
docker pull mockserver/mockserver
```
    
* **EITHER** run the container with no log output (i.e. in daemon mode)
 
```bash
docker run -d --rm --name mockserver -p <serverPort>:1080 mockserver/mockserver
```

* **OR** run the container with log output to console (i.e. in the foreground)
 
```bash
docker run --rm --name mockserver -p <serverPort>:1080 mockserver/mockserver
```

* **THEN** when your finished stop the container

```bash
docker stop mockserver && docker rm mockserver
```

The default command executed when the container runs is:
 
```bash
-logLevel INFO -serverPort 1080
```

This can be modified to change the command line options passed to the MockServer for example:

```bash
docker run --rm --name mockserver -p 1090:1090 mockserver/mockserver -logLevel INFO -serverPort 1090 -proxyRemotePort 443 -proxyRemoteHost mock-server.com
```

All configuration values (see: https://mock-server.com/mock_server/configuration_properties.html) can also be modified by passing environment variable through docker-compose.yml file. 

The following is a sample docker-compose.yml file for changing maximum expectations and maximum header size:

 ```
 mockServer:
   image: mockserver/mockserver:latest
   ports:
   - 1080:1080
   environment:
   - MOCKSERVER_MAX_EXPECTATIONS=100
   - MOCKSERVER_MAX_HEADER_SIZE=8192
 ```
 
### What is MockServer

MockServer is for mocking of any system you integrate with via HTTP or HTTPS (i.e. services, web sites, etc).

MockServer supports:

* mocking of any HTTP / HTTPS response when any request is matched ([learn more](http://www.mock-server.com/#what-is-mockserver))
* recording requests and responses to analyse how a system behaves ([learn more](http://www.mock-server.com/#what-is-mockserver))
* verifying which requests and responses have been sent as part of a test ([learn more](http://www.mock-server.com/#what-is-mockserver))

This docker container will (by default) run an instance of the MockServer on the following port:

* serverPort **1080**

For information on how to use the MockServer please see http://www.mock-server.com

### Issues

If you have any problems, please [check the project issues](https://github.com/mock-server/mockserver/issues?state=open).

### Contributions

Pull requests are, of course, very welcome! Please read our [contributing to the project](https://github.com/mock-server/mockserver/wiki/Contributing-to-the-project) guide first. Then head over to the [open issues](https://github.com/mock-server/mockserver/issues?state=open) to see what we need help with. Make sure you let us know if you intend to work on something. Also, check out the [milestones](https://github.com/mock-server/mockserver/issues/milestones) to see what is planned for future releases.

### Maintainers
* [James D Bloom](http://blog.jamesdbloom.com)

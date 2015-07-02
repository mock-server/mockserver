[MockServer](http://www.mock-server.com)
==========

### Dependencies

* [java](https://registry.hub.docker.com/u/library/java/)

### Installation

1. Install [Docker](https://www.docker.io/).

2. Download [trusted build](https://index.docker.io/u/jamesdbloom/mockserver/) from public [Docker Registry](https://index.docker.io/): `docker pull jamesdbloom/mockserver`

### Usage

* **FIRST** ensure you have the latest version

```bash
docker pull jamesdbloom/mockserver
```
    
* **EITHER** run the container with no log output (i.e. in daemon mode)
 
```bash
docker run -d --name mockserver -p <serverPort>:1080 -p <proxyPort>:1090 jamesdbloom/mockserver
```

* **OR** run the container with log output to console (i.e. in the foreground)
 
```bash
docker run --name mockserver -p <serverPort>:1080 -p <proxyPort>:1090 jamesdbloom/mockserver
```

* **THEN** when your finished stop the container

```bash
docker stop mockserver && docker rm mockserver
```

* **DEBUG** any issues or change the command line options you can run the container with a shell prompt

```bash
docker run -i -t --name mockserver -p 1080:1080 -p 1090:1090 jamesdbloom/mockserver /bin/bash
```

The default command executed when the container runs is:
 
```bash
/opt/mockserver/run_mockserver.sh -logLevel INFO -serverPort 1080 -proxyPort 1090
```

This can be modified to change the command line options passed to the `/opt/mockserver/run_mockserver.sh` script, which supports the following options:

```
run_mockserver.sh [-logLevel <level>] [-serverPort <port>] [-proxyPort <port>] [-proxyRemotePort <port>] [-proxyRemoteHost <hostname>]

 valid options are:
    -logLevel <level>            OFF, ERROR, WARN, INFO, DEBUG, TRACE or ALL
                                 as follows:
                                 WARN - exceptions and errors
                                 INFO - all interactions

    -serverPort <port>           Specifies the HTTP, HTTPS, SOCKS and HTTP
                                 CONNECT port for proxy. Port unification
                                 supports for all protocols on the same port

    -proxyPort <port>            Specifies the HTTP and HTTPS port for the
                                 MockServer. Port unification is used to
                                 support HTTP and HTTPS on the same port

    -proxyRemotePort <port>      Specifies the port to forward all proxy
                                 requests to (i.e. all requests received on
                                 portPort). This setting is used to enable
                                 the port forwarding mode therefore this
                                 option disables the HTTP, HTTPS, SOCKS and
                                 HTTP CONNECT support

    -proxyRemoteHost <hostname>  Specified the host to forward all proxy
                                 requests to (i.e. all requests received on
                                 portPort). This setting is ignored unless
                                 proxyRemotePort has been specified. If no
                                 value is provided for proxyRemoteHost when
                                 proxyRemotePort has been specified,
                                 proxyRemoteHost will default to "localhost".

i.e. run_mockserver.sh -logLevel INFO -serverPort 1080 -proxyPort 1090 -proxyRemotePort 80 -proxyRemoteHost www.mock-server.com
```

### What is MockServer

MockServer is for mocking of any system you integrate with via HTTP or HTTPS (i.e. services, web sites, etc).

MockServer supports:

* mocking of any HTTP / HTTPS response when any request is matched ([learn more](http://www.mock-server.com/#what-is-mockserver))
* recording requests and responses to analyse how a system behaves ([learn more](http://www.mock-server.com/#what-is-mockserver))
* verifying which requests and responses have been sent as part of a test ([learn more](http://www.mock-server.com/#what-is-mockserver))

This docker container will run an instance of the MockServer on the following ports:

* serverPort **1080**
* proxyPort **1090**

For information on how to use the MockServer please see http://www.mock-server.com

### Issues

If you have any problems, please [check the project issues](https://github.com/jamesdbloom/mockserver/issues?state=open).

### Contributions

Pull requests are, of course, very welcome! Please read our [contributing to the project](https://github.com/jamesdbloom/mockserver/wiki/Contributing-to-the-project) guide first. Then head over to the [open issues](https://github.com/jamesdbloom/mockserver/issues?state=open) to see what we need help with. Make sure you let us know if you intend to work on something. Also, check out the [milestones](https://github.com/jamesdbloom/mockserver/issues/milestones) to see what is planned for future releases.

### Maintainers
* [James D Bloom](http://blog.jamesdbloom.com)

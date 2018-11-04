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
docker run -d --name mockserver -p <serverPort>:1080 jamesdbloom/mockserver
```

* **OR** run the container with log output to console (i.e. in the foreground)
 
```bash
docker run --name mockserver -p <serverPort>:1080 jamesdbloom/mockserver
```

* **THEN** when your finished stop the container

```bash
docker stop mockserver && docker rm mockserver
```

* **DEBUG** any issues or change the command line options you can run the container with a shell prompt

```bash
docker run -i -t --name mockserver -p 1080:1080 jamesdbloom/mockserver /bin/bash
```

The default command executed when the container runs is:
 
```bash
/opt/mockserver/run_mockserver.sh -serverPort 1080 -logLevel INFO
```

This can be modified to change the command line options passed to the `/opt/mockserver/run_mockserver.sh` script, which supports the following options:

```
run_mockserver.sh -serverPort <port> [-proxyRemotePort <port>] [-proxyRemoteHost <hostname>] [-logLevel <level>] 

 valid options are:

    -serverPort <port>           The HTTP, HTTPS, SOCKS and HTTP CONNECT       
                                 port(s) for both mocking and proxying         
                                 requests.  Port unification is used to        
                                 support all protocols for proxying and        
                                 mocking on the same port(s). Supports         
                                 comma separated list for binding to           
                                 multiple ports.                               
        
    -proxyRemotePort <port>      Optionally enables port forwarding mode.      
                                 When specified all requests received will     
                                 be forwarded to the specified port, unless    
                                 they match an expectation.                    
        
    -proxyRemoteHost <hostname>  Specified the host to forward all proxy       
                                 requests to when port forwarding mode has     
                                 been enabled using the proxyRemotePort        
                                 option.  This setting is ignored unless       
                                 proxyRemotePort has been specified. If no     
                                 value is provided for proxyRemoteHost when    
                                 proxyRemotePort has been specified,           
                                 proxyRemoteHost will default to \"localhost\".
        
    -logLevel <level>            Optionally specify log level as TRACE, DEBUG, 
                                 INFO, WARN, ERROR or OFF. If not specified    
                                 default is INFO                               
        
i.e. run_mockserver.sh -logLevel INFO -serverPort 1080,1081 -proxyRemotePort 80 -proxyRemoteHost www.mock-server.com
```

The `logLevel` can also be modified by passing environment variable through docker-compose.yml file. The following is a sample docker-compose.yml file for changing logLevel:

 ```
 mockServer:
   image: jamesdbloom/mockserver:latest
   ports:
   - 1080:1080
   environment:
   - LOG_LEVEL=WARN
 ```
 
If no `LOG_LEVEL` value is passed in docker-compose, the default value will be `INFO` unless you run the container with other logLevel value on command line.  


### What is MockServer

MockServer is for mocking of any system you integrate with via HTTP or HTTPS (i.e. services, web sites, etc).

MockServer supports:

* mocking of any HTTP / HTTPS response when any request is matched ([learn more](http://www.mock-server.com/#what-is-mockserver))
* recording requests and responses to analyse how a system behaves ([learn more](http://www.mock-server.com/#what-is-mockserver))
* verifying which requests and responses have been sent as part of a test ([learn more](http://www.mock-server.com/#what-is-mockserver))

This docker container will run an instance of the MockServer on the following port:

* serverPort **1080**

For information on how to use the MockServer please see http://www.mock-server.com

### Issues

If you have any problems, please [check the project issues](https://github.com/jamesdbloom/mockserver/issues?state=open).

### Contributions

Pull requests are, of course, very welcome! Please read our [contributing to the project](https://github.com/jamesdbloom/mockserver/wiki/Contributing-to-the-project) guide first. Then head over to the [open issues](https://github.com/jamesdbloom/mockserver/issues?state=open) to see what we need help with. Make sure you let us know if you intend to work on something. Also, check out the [milestones](https://github.com/jamesdbloom/mockserver/issues/milestones) to see what is planned for future releases.

### Maintainers
* [James D Bloom](http://blog.jamesdbloom.com)

# mockserver-node 

> Node module and grunt plugin to start and stop [MockServer](http://mock-server.com/) and [MockServer](http://mock-server.com/) proxy

[![Build status](https://badge.buildkite.com/84d4f1ca00ee6639c1825ea31f0dcd50bd73088571813a219b.svg?style=square&theme=slack)](https://buildkite.com/mockserver/mockserver-node) [![Dependency Status](https://david-dm.org/mock-server/mockserver-node.png)](https://david-dm.org/mock-server/mockserver-node) [![devDependency Status](https://david-dm.org/mock-server/mockserver-node/dev-status.png)](https://david-dm.org/mock-server/mockserver-node#info=devDependencies)

[![NPM](https://nodei.co/npm/mockserver-node.png?downloads=true&stars=true)](https://nodei.co/npm/mockserver-node/) 

# Community

* Backlog:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://trello.com/b/dsfTCP46/mockserver" target="_blank"><img height="20px" src="http://mock-server.com/images/trello_badge-md.png" alt="Trello Backlog"></a>
* Freature Requests:&nbsp;&nbsp;<a href="https://github.com/mock-server/mockserver/issues"><img height="20px" src="http://mock-server.com/images/GitHub_Logo-md.png" alt="Github Issues"></a>
* Issues / Bugs:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://github.com/mock-server/mockserver/issues"><img height="20px" src="http://mock-server.com/images/GitHub_Logo-md.png" alt="Github Issues"></a>
* Chat:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href="https://join-mock-server-slack.herokuapp.com" target="_blank"><img height="20px" src="http://mock-server.com/images/slack-logo-slim-md.png" alt="Join Slack"></a>

## Getting Started

This node module can be used to start and stop [MockServer](http://mock-server.com/) and the [MockServer](http://mock-server.com/) proxy as a node module or as a Grunt plugin.  More information about the [MockServer](http://mock-server.com/) can be found at [mock-server.com](http://mock-server.com/). 

You may install this plugin / node module with the following command:

```shell
npm install mockserver-node --save-dev
```

## Node Module

To start or stop the MockServer from any Node.js code you need to import this module using `require('mockserver-node')` as follows:

```js
var mockserver = require('mockserver-node');
```

Then you can use either the `start_mockserver` or `stop_mockserver` functions as follows:

```js
mockserver.start_mockserver({
                serverPort: 1080,
                trace: true
            });

// do something

mockserver.stop_mockserver({
                serverPort: 1080
            });
```

The MockServer uses port unification to support HTTP, HTTPS, SOCKS, HTTP CONNECT, Port Forwarding Proxying on the same port. A client can then connect to the single port with both HTTP and HTTPS as the socket will automatically detected SSL traffic and decrypt it when required.

## Grunt Plugin

If you haven't used [Grunt](http://gruntjs.com/) before, be sure to check out the [Getting Started](http://gruntjs.com/getting-started) guide, as it explains how to create a [Gruntfile](http://gruntjs.com/sample-gruntfile) as well as install and use Grunt plugins.

In your project's Gruntfile, add a section named `start_mockserver` and `stop_mockserver` to the data object passed into `grunt.initConfig()`.

The following example will result in a both a MockServer and a MockServer Proxy being started on ports `1080` and `1090`.

```js
grunt.initConfig({
    start_mockserver: {
        options: {
            serverPort: 1080,
            trace: true
        }
    },
    stop_mockserver: {
        options: {
            serverPort: 1080
        }
    }
});

grunt.loadNpmTasks('mockserver-node');
```

## Request Log

**Note:** The request log will only be captured in MockServer if the log level is `INFO` (or more verbose, i.e. `DEBUG` or `TRACE`) therefore to capture the request log and use the `/retrieve` endpoint ensure either the option `trace: true` or the command line switch `--verbose` is set.

### Options

#### options.serverPort
Type: `Integer`
Default value: `undefined`

The HTTP, HTTPS, SOCKS and HTTP CONNECT port(s) for both mocking and proxying requests.  Port unification is used to support all protocols for proxying and mocking on the same port(s). Supports comma separated list for binding to multiple ports.

#### options.proxyRemotePort
Type: `Integer`
Default value: `undefined` 

Optionally enables port forwarding mode. When specified all requests received will be forwarded to the specified port, unless they match an expectation.

#### options.proxyRemoteHost
Type: `String`
Default value: `undefined`  

Specified the host to forward all proxy requests to when port forwarding mode has been enabled using the `proxyRemotePort` option.  This setting is ignored unless `proxyRemotePort` has been specified. If no value is provided for `proxyRemoteHost` when `proxyRemotePort` has been specified, `proxyRemoteHost` will default to `"localhost"`.

#### options.artifactoryHost
Type: `String` 
Default value: `oss.sonatype.org`

This value specifies the name of the artifact repository host.

#### options.artifactoryPath
Type: `String` 
Default value: `/content/repositories/releases/org/mock-server/mockserver-netty/`

This value specifies the path to the artifactory leading to the mockserver-netty jar with dependencies.

#### options.mockServerVersion
Type: `String` 
Default value: `5.15.0`

This value specifies the artifact version of MockServer to download.

**Note:** It is also possible to specify a SNAPSHOT version to get the latest unreleased changes.

#### options.verbose
Type: `Boolean`
Default value: `false`

This value indicates whether the MockServer logs should be written to the console.  In addition to logging additional output from the grunt task this options also sets the logging level of the MockServer to [**DEBUG**](http://www.mock-server.com/mock_server/debugging_issues.html). At **DEBUG** all matcher results, including when specific matchers fail (such as HeaderMatcher) are written to the log. The MockServer logs are written to ```mockserver.log``` in the current directory.  

**Note:** It is also possible to use the ```--verbose``` command line switch to enabled verbose level logging from the command line.

#### options.trace
Type: `Boolean`
Default value: `false`

This value sets the logging level of the MockServer to [**TRACE**](http://www.mock-server.com/mock_server/debugging_issues.html). At **TRACE** level (in addition to **INFO** level information) all matcher results, including when specific matchers fail (such as HeaderMatcher) are written to the log. The MockServer logs are written to ```mockserver.log``` in the current directory. 

#### options.javaDebugPort
Type: `Integer`
Default value: `undefined`

This value indicates whether Java debugging should be enabled and if so which port the debugger should listen on.  When this options is provided the following additional option is passed to the JVM:
 
```bash
"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + javaDebugPort
```  

Note that `suspend=y` is used so the MockServer will pause until the debugger is attached.  The grunt task will wait 50 seconds for the debugger to be attached before it exits with a failure status.
  
#### options.jvmOptions
Type: `String`
Default value: `undefined`

This value allows any system properties to be passed to the JVM that runs MockServer, for example:
 
```js
start_mockserver: {
    options: {
        serverPort: 1080,
        jvmOptions: "-Dmockserver.enableCORSForAllResponses=true"
    }
}
```  

#### options.startupRetries
Type: `Integer`
Default value if javaDebugPort is not set: `110`
Default value if javaDebugPort is set: `500`

This value indicates the how many times we will call the check to confirm if the mock server started up correctly. It will default to 110 which will take about 11 seconds to complete, this is normally long enough for the server to startup. The server can take longer to start up if Java debugging is enabled so this will default to 500. The default will, in some cases, need to be overridden as the JVM may take longer to start up on some architectures,  e.g. Mac seems to take a little longer.

## Contributing
In lieu of a formal styleguide, take care to maintain the existing coding style. Add unit tests for any new or changed functionality. Lint and test your code using [Grunt](http://gruntjs.com/).

## Changelog

All notable and significant changes are detailed in the [MockServer changelog](https://github.com/mock-server/mockserver/blob/master/changelog.md) 

---

Task submitted by [James D Bloom](http://blog.jamesdbloom.com)

[![Analytics](https://ga-beacon.appspot.com/UA-32687194-4/mockserver-node/README.md)](https://github.com/igrigorik/ga-beacon)

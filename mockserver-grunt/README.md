# mockserver-grunt 

> Start and stop MockServer from grunt

Grunt plugin to MockServer and the MockServer proxy to started and stopped from grunt.

[![Build Status](https://drone.io/github.com/jamesdbloom/mockserver/status.png)](https://drone.io/github.com/jamesdbloom/mockserver/latest)  [![Dependency Status](https://david-dm.org/jamesdbloom/mockserver-grunt.png)](https://david-dm.org/jamesdbloom/mockserver-grunt) [![devDependency Status](https://david-dm.org/jamesdbloom/mockserver-grunt/dev-status.png)](https://david-dm.org/jamesdbloom/mockserver-grunt#info=devDependencies)
[![Still Maintained](http://stillmaintained.com/jamesdbloom/mockserver.png)](http://stillmaintained.com/jamesdbloom/mockserver) [![Stories in Backlog](https://badge.waffle.io/jamesdbloom/mockserver.png?label=proposal&title=Proposals)](https://waffle.io/jamesdbloom/mockserver) [![Stories in Backlog](https://badge.waffle.io/jamesdbloom/mockserver.png?label=ready&title=Ready)](https://waffle.io/jamesdbloom/mockserver) [![Stories in Backlog](https://badge.waffle.io/jamesdbloom/mockserver.png?label=in%20progress&title=In%20Progress)](https://waffle.io/jamesdbloom/mockserver)



[![NPM](https://nodei.co/npm/mockserver-grunt.png?downloads=true&stars=true)](https://nodei.co/npm/mockserver-grunt/)

## Getting Started
This plugin requires Grunt `~0.4`

If you haven't used [Grunt](http://gruntjs.com/) before, be sure to check out the [Getting Started](http://gruntjs.com/getting-started) guide, as it explains how to create a [Gruntfile](http://gruntjs.com/sample-gruntfile) as well as install and use Grunt plugins. Once you're familiar with that process, you may install this plugin with this command:

```shell
npm install mockserver-grunt --save-dev
```

## The "start_mockserver" task

### Overview
In your project's Gruntfile, add a section named `start_mockserver` and `stop_mockserver` to the data object passed into `grunt.initConfig()`.

Typically the options section would not need to be provided as these values are read from the **package.json** file for the project.  In this example, however, custom options are used to override the default values.  For more details on the default values see below.

```js
grunt.initConfig({
    start_mockserver: {
        start: {
            options: {
                serverPort: 1080,
                serverSecurePort: 1082,
                proxyPort: 1090,
                proxySecurePort: 1092
            }
        }
    },
    stop_mockserver: {
        stop: {
    
        }
    }
});
```

This will result in a both a MockServer and a MockServer Proxy being started on both HTTP and HTTPS ports. 

To control what is started only specify the ports you require. For example if you only want to start the MockServer for HTTP only provide this port, if you do this the proxy will not be started and the MockServer will not create an HTTPS endpoint. 

### Options

#### options.serverPort
Type: `Integer`
Default value: ``

This value indicates that you want to start the MockServer using this value for the HTTP port.  The MockServer will only be started if either an HTTP i.e. `serverPort` or HTTPS i.e. `serverSecurePort` port is provided, if neither are provided the MockServer will not be started.

#### options.serverSecurePort
Type: `Integer`
Default value: ``

This value indicates that you want to start the MockServer using this value for the HTTPS port.  The MockServer will only be started if either an HTTP i.e. `serverPort` or HTTPS i.e. `serverSecurePort` port is provided, if neither are provided the MockServer will not be started.

#### options.proxyPort
Type: `Integer`
Default value: ``

This value indicates that you want to start the proxy using this value for the HTTP port.  The proxy will only be started if either an HTTP i.e. `proxyPort` or HTTPS i.e. `proxySecurePort` port is provided, if neither are provided the proxy will not be started.

#### options.proxySecurePort
Type: `Integer`
Default value: ``

This value indicates that you want to start the proxy using this value for the HTTPS port.  The proxy will only be started if either an HTTP i.e. `proxyPort` or HTTPS i.e. `proxySecurePort` port is provided, if neither are provided the proxy will not be started.

## Contributing
In lieu of a formal styleguide, take care to maintain the existing coding style. Add unit tests for any new or changed functionality. Lint and test your code using [Grunt](http://gruntjs.com/).

## Release History
 * 2014-28-10   v0.0.1   Released mockserver-grunt task

---

Task submitted by [James D Bloom](http://blog.jamesdbloom.com)

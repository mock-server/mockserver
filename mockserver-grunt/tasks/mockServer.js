/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */


module.exports = function (grunt) {

    'use strict';

    var mockServer,
        sleep = require('sleep');

    grunt.registerMultiTask('start_mockserver', 'Run MockServer from grunt build', function () {
            var done = this.async(),
                options = this.options(),
                spawn = require('child_process').spawn;

            var commandLineOptions = ['-Dfile.encoding=UTF-8', '-Dmockserver.logLevel=WARN', '-jar', grunt.file.expand('**/mockserver-netty-*-jar-with-dependencies.jar')];
            if (options.serverPort) {
                commandLineOptions.push("-serverPort");
                commandLineOptions.push(options.serverPort);
            }
            if (options.serverSecurePort) {
                commandLineOptions.push("-serverSecurePort");
                commandLineOptions.push(options.serverSecurePort);
            }
            if (options.proxyPort) {
                commandLineOptions.push("-proxyPort");
                commandLineOptions.push(options.proxyPort);
            }
            if (options.proxySecurePort) {
                commandLineOptions.push("-proxySecurePort");
                commandLineOptions.push(options.proxySecurePort);
            }
            grunt.verbose.writeln('Running \'java ' + commandLineOptions.join(' ') + '\'');
            mockServer = spawn('java', commandLineOptions, {
                stdio: [ 'ignore', (grunt.option('verbose') ? process.stdout : 'ignore'), process.stderr ]
            });

            sleep.sleep(5);

            done(true);
        }
    );

    grunt.registerTask('stop_mockserver', 'Stop MockServer from grunt build', function () {
            var done = this.async();
            if (mockServer) {
                mockServer.kill();
            }
            done(true);
        }
    );
};

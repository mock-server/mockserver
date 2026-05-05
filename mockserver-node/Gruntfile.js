/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */

'use strict';

module.exports = function (grunt) {

    grunt.initConfig({
        exec: {
            stop_existing_mockservers: './stop_MockServer.sh'
        },
        jshint: {
            all: [
                'Gruntfile.js',
                'tasks/*.js',
                '<%= nodeunit.grunt_started %>',
                '<%= nodeunit.grunt_stopped %>',
                '<%= nodeunit.grunt_failure %>'
            ],
            options: {
                jshintrc: '.jshintrc'
            }
        },
        start_mockserver: {
            options: {
                serverPort: 1080,
                jvmOptions: [
                    '-Dmockserver.enableCORSForAllResponses=true',
                    '-Dmockserver.corsAllowMethods="CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"',
                    '-Dmockserver.corsAllowHeaders="Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"',
                    '-Dmockserver.corsAllowCredentials=true -Dmockserver.corsMaxAgeInSeconds=300'
                ],
                mockServerVersion: "5.15.0"
            }
        },
        stop_mockserver: {
            options: {
                serverPort: 1080
            }
        },
        nodeunit: {
            grunt_started: [
                'test/grunt/started/*_test.js'
            ],
            grunt_stopped: [
                'test/grunt/stopped/*_test.js'
            ],
            grunt_failure: [
                'test/grunt/failure/*_test.js'
            ],
            node_started: [
                'test/node/started/*_test.js'
            ],
            node_stopped: [
                'test/node/stopped/*_test.js'
            ],
            node_failure: [
                'test/node/failure/*_test.js'
            ],
            options: {
                reporter: 'nested'
            }
        }
    });

    grunt.registerTask('download_jar', 'Download latest MockServer jar version', function () {
        var done = this.async();
        var artifactoryHost = 'oss.sonatype.org';
        var artifactoryPath = '/content/repositories/releases/org/mock-server/mockserver-netty/';
        require('./downloadJar').downloadJar('5.15.0', artifactoryHost, artifactoryPath).then(function () {
            done(true);
        }, function () {
            done(false);
        });
    });

    grunt.registerTask('deleted_jars', 'Delete any old MockServer jars', function () {
        var fs = require('fs');
        var currentMockServerJars = require('glob').sync('**/mockserver-netty-*-jar-with-dependencies.jar');
        currentMockServerJars.forEach(function (item) {
            fs.unlinkSync(item);
            console.log('Deleted ' + item);
        });
        currentMockServerJars.splice(0);
    });

    // load this plugin's task
    grunt.loadTasks('tasks');

    grunt.loadNpmTasks('grunt-exec');
    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-contrib-nodeunit');

    grunt.registerTask('test', [
        'start_mockserver:self',
        'nodeunit:grunt_started',
        'stop_mockserver:self',
        'nodeunit:grunt_stopped',
        'nodeunit:grunt_failure',
        'nodeunit:node_failure',
        'nodeunit:node_started'
    ]);

    grunt.registerTask('default', ['exec:stop_existing_mockservers', 'deleted_jars', 'download_jar', 'jshint', 'test']);
};

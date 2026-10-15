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
            stop_existing_mockservers: '../scripts/stop_MockServer.sh'
        },
        jshint: {
            all: [
                'Gruntfile.js',
                'tasks/*.js',
                'downloadBinary.js',
                'bin/mockserver.js',
                'test/downloadBinary_test.js',
                'test/downloadJar_test.js',
                'test/esModuleImport_test.js',
                'test/packageContents.js',
                'test/packageContents_test.js',
                'test/grunt/started/*_test.js',
                'test/grunt/stopped/*_test.js',
                'test/grunt/failure/*_test.js'
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
                mockServerVersion: require('./package.json').version
            }
        },
        stop_mockserver: {
            options: {
                serverPort: 1080
            }
        }
    });

    grunt.registerTask('download_jar', 'Download latest MockServer jar version', function () {
        var done = this.async();
        var artifactoryHost = 'repo1.maven.org';
        var artifactoryPath = '/maven2/org/mock-server/mockserver-netty/';
        // the release dry-run sets this: a version not yet on Maven Central cannot be downloaded
        var version = process.env.MOCKSERVER_DOWNLOAD_JAR_VERSION || require('./package.json').version;
        require('./downloadJar').downloadJar(version, artifactoryHost, artifactoryPath).then(function () {
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

    grunt.registerTask('default', ['jshint']);
};

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
            options: {
                jshintrc: '.jshintrc'
            },
            user_defaults: [
                'Gruntfile.js',
                'js/**/*.js',
                '!js/lib/**/*.js',
                '<%= nodeunit.no_proxy %>',
                '<%= nodeunit.with_proxy %>'
            ]
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
                mockServerVersion: "5.15.0",
                verbose: false
            }
        },
        stop_mockserver: {
            options: {
                serverPort: 1080
            }
        },
        nodeunit: {
            no_proxy: [
                'test/no_proxy/*_test.js'
            ],
            with_proxy: [
                'test/with_proxy/*_test.js'
            ],
            options: {
                reporter: 'nested'
            }
        },
        karma: {
            options: {
                configFile: 'test/karma.conf.js',
                logLevel: 'INFO',
                reporters: 'spec',
                browserDisconnectTimeout: 10 * 10000,
                browserNoActivityTimeout: 10 * 10000,
                singleRun: true,
                files: [
                    'mockServerClient.js',
                    'test/no_proxy/mock_server_browser_client_spec.js'
                ]
            },
            chrome: {
                browsers: ['Chrome']
            }
        },
        ts: {
            options: {
                noEmit: true
            },
            default: {
                src: [
                    'test/mockServerClient.ts'
                ]
            }
        }
    });

    grunt.loadNpmTasks('grunt-exec');
    grunt.loadNpmTasks('mockserver-node');
    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-contrib-nodeunit');
    grunt.loadNpmTasks('grunt-karma');
    grunt.loadNpmTasks("grunt-ts");

    grunt.registerTask('test_node', ['ts', 'start_mockserver', 'nodeunit', 'stop_mockserver']);
    grunt.registerTask('test_browser', ['start_mockserver', 'karma:chrome', 'stop_mockserver']);
    grunt.registerTask('test', ['start_mockserver', 'nodeunit', 'karma:chrome', 'stop_mockserver']);

    grunt.registerTask('default', ['exec:stop_existing_mockservers', 'jshint', 'test_node']);
    grunt.registerTask('headless', ['exec:stop_existing_mockservers', 'jshint', 'test_node']);
};

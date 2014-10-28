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
        jshint: {
            all: [
                'Gruntfile.js',
                'tasks/*.js',
                '<%= nodeunit.started %>',
                '<%= nodeunit.stopped %>'
            ],
            options: {
                jshintrc: '.jshintrc'
            }
        },
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
        },
        nodeunit: {
            started: [
                'test/started/*_test.js'
            ],
            stopped: [
                'test/stopped/*_test.js'
            ],
            options: {
                reporter: 'nested'
            }
        }
    });

    // load this plugin's task
    grunt.loadTasks('tasks');

    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.loadNpmTasks('grunt-contrib-nodeunit');

    grunt.registerTask('test', ['start_mockserver:start', 'nodeunit:started', 'stop_mockserver:stop', 'nodeunit:stopped']);

    grunt.registerTask('default', ['jshint', 'test']);
};

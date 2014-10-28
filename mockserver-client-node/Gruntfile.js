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
            options: {
                jshintrc: '.jshintrc'
            },
            user_defaults: [
                'Gruntfile.js',
                'js/**/*.js',
                '!js/lib/**/*.js',
                '<%= nodeunit.no_proxy %>'//,
//                '<%= nodeunit.with_proxy %>'
            ]
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
            no_proxy: [
                'test/no_proxy/*_test.js'
            ],
//            with_proxy: [
//                'test/with_proxy/*_test.js'
//            ],
            options: {
                reporter: 'nested'
            }
        }
    });

    grunt.loadNpmTasks('../../mockserver-grunt');
    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.loadNpmTasks('grunt-contrib-nodeunit');

    grunt.registerTask('test', ['start_mockserver:start', 'nodeunit', 'stop_mockserver:stop']);

    grunt.registerTask('default', ['jshint', 'test']);
};

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

    grunt.registerTask('linkModule', 'Link module to support other dependent modules', function () {
        var done = this.async();

        var spawn = require('child_process').spawn,
            MAX_ERROR_MESSAGE_LENGTH = 1024,
            stderrBuf = '',
            pkg = grunt.file.readJSON('package.json');

        var nodeProcess = spawn('npm', ['link']);

        grunt.log.ok('Linked module with name ' + pkg.name);

        nodeProcess.stdout.on('data', function (data) {
            grunt.log.writeln('[linkModule]: ' + data);
        });
        nodeProcess.stderr.on('data', function (data) {
            grunt.log.errorlns(data);
            if (data.length === undefined) {
                data = '' + data;
            }
            if (stderrBuf.length + data.length < MAX_ERROR_MESSAGE_LENGTH) {
                stderrBuf += data;
            }
        });

        nodeProcess.on('exit', function (code) {
            done(true);
        });
    });

    // load this plugin's task
    grunt.loadTasks('tasks');

    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.loadNpmTasks('grunt-contrib-nodeunit');

    grunt.registerTask('test', ['start_mockserver:start', 'nodeunit:started', 'stop_mockserver:stop', 'nodeunit:stopped']);

    grunt.registerTask('default', ['jshint', 'test', 'linkModule']);
};

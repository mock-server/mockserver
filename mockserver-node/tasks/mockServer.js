/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */

module.exports = function (grunt) {

    'use strict';

    var mockServer = require('../index.js');

    grunt.registerTask('start_mockserver', 'Run MockServer from grunt build', function () {
        var done = this.async();
        var options = this.options();
        options.verbose = options.trace || options.verbose || grunt.option('verbose');
        mockServer.start_mockserver(options).then(function() {
            done(true);
        }, function(err) {
            console.error(err);
            console.error('\n' +
                'mockserver-node - you must at least specify serverPort, for example:\n' +
                'start_mockserver: {\n' +
                '    options: {\n' +
                '        serverPort: 1080\n' +
                '    }\n' +
                '}\n');
            done(false);
        });
    });

    grunt.registerTask('stop_mockserver', 'Stop MockServer from grunt build', function() {
        var done = this.async();
        var options = this.options();
        options.verbose = grunt.option('verbose');
        mockServer.stop_mockserver(options).then(function() {
            done(true);
        }, function(err) {
            console.error(err);
            console.error('\n' +
                'mockserver-node - you must at least specify serverPort, for example:\n' +
                'stop_mockserver: {\n' +
                '    options: {\n' +
                '        serverPort: 1080\n' +
                '    }\n' +
                '}\n');
            done(false);
        });
    });
};

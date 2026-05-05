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
        start_mockserver: {
            missing_ports: {}
        },
        stop_mockserver: {
            missing_ports: {}
        }
    });

    // load this plugin's task
    grunt.loadTasks('../../../tasks');

};

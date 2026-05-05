/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */

(function () {
    "use strict";
  
    function downloadJar(version, artifactoryHost, artifactoryPath, logLevel) {
      var Q = require('q');
      var deferred = Q.defer();
      var https = require('follow-redirects').https;
      var fs = require('fs');
      var glob = require('glob');
      var dest = 'mockserver-netty-' + version + '-jar-with-dependencies.jar';
      var snapshot = version.indexOf("SNAPSHOT") !== -1;
      var options = {
        host: artifactoryHost,
        path: artifactoryPath && !snapshot ?
          artifactoryPath + version + "/mockserver-netty-" + version + "-jar-with-dependencies.jar" :
          "/service/local/artifact/maven/redirect?r=" + (snapshot ? "snapshots" : "releases") + "&g=org.mock-server&a=mockserver-netty&c=jar-with-dependencies&v=" + version,
        port: 443
      };
  
      var currentMockServerJars = glob.sync(__dirname + '/mockserver-netty-*-jar-with-dependencies.jar');
      currentMockServerJars.forEach(function (item) {
        if (item.indexOf(dest) === -1 || snapshot) {
          fs.unlinkSync(item);
          if (logLevel) {
            console.log('Deleted old version ' + item);
          }
        }
      });
  
      if (!fs.existsSync(__dirname + '/' + dest)) {
        if (logLevel) {
          console.log('Fetching ' + JSON.stringify(options, null, 2));
        }
        var req = https.request(options);
  
        req.once('error', function (error) {
          if (logLevel) {
            console.error('Fetching ' + JSON.stringify(options, null, 2) + ' failed with error ' + error);
          }
          deferred.reject(new Error('Fetching ' + JSON.stringify(options, null, 2) + ' failed with error ' + error));
        });
  
        req.once('response', function (res) {
          if (res.statusCode < 200 || res.statusCode >= 300) {
            if (logLevel) {
              console.error('Fetching ' + JSON.stringify(options, null, 2) + ' failed with HTTP status code ' + res.statusCode);
            }
            deferred.reject(new Error('Fetching ' + JSON.stringify(options, null, 2) + ' failed with HTTP status code ' + res.statusCode));
          } else {
            var writeStream = fs.createWriteStream(dest);
            res.pipe(writeStream);
  
            writeStream.on('error', function (error) {
              if (logLevel) {
                console.error('Saving ' + dest + ' failed with error ' + error);
              }
              deferred.reject(new Error('Saving ' + dest + ' failed with error ' + error));
            });
            writeStream.on('close', function () {
              if (logLevel) {
                console.log('Saved ' + dest + ' from ' + JSON.stringify(options, null, 2));
              }
              deferred.resolve();
            });
          }
        });
  
        req.end();
      } else {
        if (logLevel) {
          console.log('Skipping ' + JSON.stringify(options, null, 2) + ' as file already downloaded');
        }
        deferred.resolve();
      }
  
      return deferred.promise;
    }
  
    module.exports = {
      downloadJar: downloadJar
    };
  })();
  

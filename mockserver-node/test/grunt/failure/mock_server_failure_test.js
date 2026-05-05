(function () {

    'use strict';

    var testCase = require('nodeunit').testCase;
    var path = require('path');
    var exec = require('child_process').exec;
    var execOptions = {
        cwd: path.join(__dirname)
    };

    exports.mock_server_failure = {
        'mock server fails to start': testCase({
            'should fail start if configuration missing': function (test) {
                test.expect(1);
                exec('../../../node_modules/.bin/grunt start_mockserver:missing_ports', execOptions, function (error, stdout, stderr) {
                    stderr = stderr.replace(/\(node:\d*\) ExperimentalWarning: queueMicrotask\(\) is experimental\.\n/, '');
                    test.equal(
                        stderr,
                        "Please specify \"serverPort\", for example: \"start_mockserver({ serverPort: 1080 })\"\n" +
                        "\n" +
                        "mockserver-node - you must at least specify serverPort, for example:\n" +
                        "start_mockserver: {\n" +
                        "    options: {\n" +
                        "        serverPort: 1080\n" +
                        "    }\n" +
                        "}\n" +
                        "\n"
                    );
                    test.done();
                });
            },
            'should fail stop if configuration missing': function (test) {
                test.expect(1);
                exec('../../../node_modules/.bin/grunt stop_mockserver:missing_ports', execOptions, function (error, stdout, stderr) {
                    stderr = stderr.replace(/\(node:\d*\) ExperimentalWarning: queueMicrotask\(\) is experimental\.\n/, '');
                    test.equal(
                        stderr,
                        "Please specify \"serverPort\", for example: \"stop_mockserver({ serverPort: 1080 })\"\n" +
                        "\n" +
                        "mockserver-node - you must at least specify serverPort, for example:\n" +
                        "stop_mockserver: {\n" +
                        "    options: {\n" +
                        "        serverPort: 1080\n" +
                        "    }\n" +
                        "}\n" +
                        "\n"
                    );
                    test.done();
                });
            }
        })
    };

})();

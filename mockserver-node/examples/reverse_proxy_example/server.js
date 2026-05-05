var mockserver = require('mockserver-node');
var mockServerClient = require('mockserver-client').mockServerClient;
var HTTP_PORT = 1080;

mockserver
    .start_mockserver({
        serverPort: HTTP_PORT
    })
    .then(function () {
        // forward backend REST API request to local machine
        mockServerClient("localhost", HTTP_PORT)
            .mockAnyResponse({
                "httpRequest": {
                    "path": "/rest.*"
                },
                "httpForward": { // local machine Tomcat instance
                    "host": "127.0.0.1",
                    "port": 8080,
                    "scheme": "HTTP"
                },
                "times": {
                    "unlimited": true
                }
            })
            .then(
                function () {
                    // forward all other request to QA environment
                    mockServerClient("localhost", HTTP_PORT)
                        .mockAnyResponse({
                            "httpRequest": {
                                "path": "/.*"
                            },
                            "httpForward": { // QA environment load balancer
                                "host": "192.168.50.10",
                                "port": 443,
                                "scheme": "HTTPS"
                            },
                            "times": {
                                "unlimited": true
                            }
                        })
                        .then(
                            function () {
                                console.log("created expectations");
                            },
                            function (error) {
                                console.log(error);
                            }
                        );
                },
                function (error) {
                    console.log(error);
                }
            );
    });

console.log("started on port: " + HTTP_PORT);

// stop MockServer if Node exist abnormally
process.on('uncaughtException', function (err) {
    console.log('uncaught exception - stopping node server: ' + JSON.stringify(err));
    mockserver.stop_mockserver();
    throw err;
});

// stop MockServer if Node exits normally
process.on('exit', function () {
    console.log('exit - stopping node server');
    mockserver.stop_mockserver();
});

// stop MockServer when Ctrl-C is used
process.on('SIGINT', function () {
    console.log('SIGINT - stopping node server');
    mockserver.stop_mockserver();
    process.exit(0);
});

// stop MockServer when a kill shell command is used
process.on('SIGTERM', function () {
    console.log('SIGTERM - stopping node server');
    mockserver.stop_mockserver();
    process.exit(0);
});
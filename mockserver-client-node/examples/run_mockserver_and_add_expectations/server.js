var mockserver = require('mockserver-node');
var mockServerClient = require('mockserver-client').mockServerClient;
var mockserver_port = 1080;

mockserver
    .start_mockserver({serverPort: mockserver_port, verbose: true})
    .then(
        function () {
            mockServerClient("localhost", 1080).mockAnyResponse(
                {
                    'httpRequest': {
                        'path': '/somePathOne'
                    },
                    'httpResponse': {
                        'statusCode': 200,
                        'body': JSON.stringify({name: 'one'}),
                        'delay': {
                            'timeUnit': 'MILLISECONDS',
                            'value': 250
                        }
                    }
                }
            ).then(
                function () {
                    console.log("created \"/somePathOne\" expectation");
                },
                function (error) {
                    console.log(error.body);
                }
            );

            mockServerClient("localhost", 1080).mockAnyResponse(
                {
                    'httpRequest': {
                        'path': '/somePathTwo'
                    },
                    'httpResponse': {
                        'statusCode': 200,
                        'body': JSON.stringify({name: 'two'}),
                        'delay': {
                            'timeUnit': 'MILLISECONDS',
                            'value': 250
                        }
                    }
                }
            ).then(
                function () {
                    console.log("created \"/somePathTwo\" expectation");
                },
                function (error) {
                    console.log(error.body);
                }
            );
        },
        function (error) {
            console.log(JSON.stringify(error, null, "  "));
        }
    );

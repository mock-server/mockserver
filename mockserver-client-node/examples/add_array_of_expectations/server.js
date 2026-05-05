function addArrayOfExpectations() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse(
        [
            {
                'httpRequest': {
                    'path': '/somePathOne'
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({'value': 'one'})
                }
            },
            {
                'httpRequest': {
                    'path': '/somePathTwo'
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({'value': 'one'})
                }
            },
            {
                'httpRequest': {
                    'path': '/somePathThree'
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({'value': 'one'})
                }
            }
        ]
    )
        .then(
            function () {
                console.log("expectations created");
            },
            function (error) {
                console.log(error);
            }
        );
}

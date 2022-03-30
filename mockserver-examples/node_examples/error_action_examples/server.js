function randomBytesError() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpError": {
            "dropConnection": true,
            "responseBytes": "eQqmdjEEoaXnCvcK6lOAIZeU+Pn+womxmg=="
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function dropConnectionError() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "path": "/some/path"
        },
        "httpError": {
            "dropConnection": true
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}
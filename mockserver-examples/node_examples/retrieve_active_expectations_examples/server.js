function retrieveAllActiveExpectations() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .retrieveActiveExpectations({})
        .then(
            function (activeExpectations) {
                console.log(JSON.stringify(activeExpectations, null, "  "));
            },
            function (error) {
                console.log(error);
            }
        );
}

function retrieveActiveExpectationsUsingRequestMatcher() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).retrieveActiveExpectations({
        "path": "/some/path",
        "method": "POST"
    }).then(
        function (activeExpectations) {
            console.log(JSON.stringify(activeExpectations, null, "  "));
        },
        function (error) {
            console.log(error);
        }
    );
}

function retrieveActiveExpectationsInJson() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).retrieveActiveExpectations({
        "path": "/some/path",
        "method": "POST"
    }).then(
        function (activeExpectations) {
            console.log(JSON.stringify(activeExpectations, null, "  "));
        },
        function (error) {
            console.log(error);
        }
    );
}

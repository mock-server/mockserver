function retrieveAllRecordedExpectations() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .retrieveRecordedExpectations({})
        .then(
            function (recordedExpectations) {
                console.log(JSON.stringify(recordedExpectations, null, "  "));
            },
            function (error) {
                console.log(error);
            }
        );
}

function retrieveRecordedExpectationsUsingRequestMatcher() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).retrieveRecordedExpectations({
        "path": "/some/path",
        "method": "POST"
    }).then(
        function (recordedExpectations) {
            console.log(JSON.stringify(recordedExpectations, null, "  "));
        },
        function (error) {
            console.log(error);
        }
    );
}

function retrieveRecordedExpectationsInJson() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).retrieveRecordedExpectations({
        "path": "/some/path",
        "method": "POST"
    }).then(
        function (recordedExpectations) {
            console.log(JSON.stringify(recordedExpectations, null, "  "));
        },
        function (error) {
            console.log(error);
        }
    );
}

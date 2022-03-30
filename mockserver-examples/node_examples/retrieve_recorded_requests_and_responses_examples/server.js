function retrieveAllRecordedRequests() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .retrieveRecordedRequestsAndResponses({})
        .then(
            function (recordedRequestsAndResponses) {
                console.log(JSON.stringify(recordedRequestsAndResponses));
            },
            function (error) {
                console.log(error);
            }
        );
}

function retrieveRecordedRequestsUsingRequestMatcher() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).retrieveRecordedRequestsAndResponses({
        "path": "/some/path",
        "method": "POST"
    }).then(
        function (recordedRequestsAndResponses) {
            console.log(JSON.stringify(recordedRequestsAndResponses));
        },
        function (error) {
            console.log(error);
        }
    );
}

function retrieveRecordedRequestsInJson() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).retrieveRecordedRequestsAndResponses({
        "path": "/some/path",
        "method": "POST"
    }).then(
        function (recordedRequestsAndResponses) {
            console.log(JSON.stringify(recordedRequestsAndResponses));
        },
        function (error) {
            console.log(error);
        }
    );
}
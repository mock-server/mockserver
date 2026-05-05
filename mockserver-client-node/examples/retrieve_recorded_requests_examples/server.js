function retrieveAllRecordedRequests() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080)
        .retrieveRecordedRequests({})
        .then(
            function (recordedRequests) {
                console.log(JSON.stringify(recordedRequests, null, "  "));
            },
            function (error) {
                console.log(error);
            }
        );
}

function retrieveRecordedRequestsUsingRequestMatcher() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).retrieveRecordedRequests({
        "path": "/some/path",
        "method": "POST"
    }).then(
        function (recordedRequests) {
            console.log(JSON.stringify(recordedRequests, null, "  "));
        },
        function (error) {
            console.log(error);
        }
    );
}

function retrieveRecordedRequestsInJson() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).retrieveRecordedRequests({
        "path": "/some/path",
        "method": "POST"
    }).then(
        function (recordedRequests) {
            console.log(JSON.stringify(recordedRequests, null, "  "));
        },
        function (error) {
            console.log(error);
        }
    );
}

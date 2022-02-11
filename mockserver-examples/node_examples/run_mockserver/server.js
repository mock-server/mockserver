var mockserver = require('mockserver-node');

mockserver
    .start_mockserver({serverPort: 1080, verbose: true})
    .then(
        function () {
            console.log("started MockServer");
        },
        function (error) {
            console.log(JSON.stringify(error, null, "  "));
        }
    );

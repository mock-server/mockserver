var mockserver = require('mockserver-node');

mockserver
    .start_mockserver({
        serverPort: 1080,
        jvmOptions: [
            '-Dmockserver.enableCORSForAllResponses=true',
            '-Dmockserver.corsAllowMethods="CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"',
            '-Dmockserver.corsAllowHeaders="Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"',
            '-Dmockserver.corsAllowCredentials=true -Dmockserver.corsMaxAgeInSeconds=300'
        ],
        mockServerVersion: "5.15.0",
        verbose: true
    })
    .then(
        function () {
            console.log("started MockServer");
        },
        function (error) {
            console.log(JSON.stringify(error, null, "  "));
        }
    );


mockserver.start_mockserver({
    serverPort: 1080,
    systemProperties: "-Dmockserver.enableCORSForAllResponses=true " +
        "-Dmockserver.corsAllowMethods=\"CONNECT, DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE\" " +
        "-Dmockserver.corsAllowHeaders=\"Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization\" " +
        "-Dmockserver.corsAllowCredentials=\"true\" " +
        "-Dmockserver.corsMaxAgeInSeconds=\"300\""
});
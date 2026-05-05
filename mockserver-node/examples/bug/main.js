const ms = require("mockserver-node");
ms.start_mockserver({
    serverPort: 3030,
    verbose: true,
    debug: true,
    jvmOptions: [
        "-Dmockserver.enableCORSForAPI=true",
        '-Dmockserver.corsAllowMethods="CONNECT, X, GET, HEAD, OPTIONS, POST, PUT, PATCH, TRACE"',
    ],
});
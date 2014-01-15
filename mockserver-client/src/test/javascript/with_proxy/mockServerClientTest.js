describe("mockServerClient proxy integration:", function () {
    var xmlhttp;

    beforeEach(function () {
        xmlhttp = new XMLHttpRequest();
        mockServerClient("http://localhost:1080", "http://localhost:1090").resetMocks();
        mockServerClient("http://localhost:1080", "http://localhost:1090").resetProxy();
    });

    it("should verify exact number of requests have been sent", function () {
        // given
        var client = mockServerClient("http://localhost:1080", "http://localhost:1090");
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        client.mockSimpleResponse('/somePath', { name: 'value' }, 203);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");

        // when
        client.dumpProxyToLog();
        client.verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 2, true);
    });

    xit("should verify at least a number of requests have been sent", function () {
        // given
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");

        // when
        mockServerClient("http://localhost:1080", "http://localhost:1090").verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 1);
    });
});
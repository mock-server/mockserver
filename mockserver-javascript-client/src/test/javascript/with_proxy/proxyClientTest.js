describe("proxyClient client:", function () {
    var xmlhttp;

    beforeEach(function () {
        xmlhttp = new XMLHttpRequest();
        mockServerClient("localhost", 1080).reset();
        proxyClient("localhost", 1090).reset();
    });

    it("should verify exact number of requests have been sent", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        mockServerClient("localhost", 1080).mockSimpleResponse('/somePath', { name: 'value' }, 203);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");

        // when
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
        proxyClient("localhost", 1090).verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 1);
    });

    it("should clear proxy", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");

        // then
        client.verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 1);

        // when
        client.clear('/somePath');

        // then
        var passed = false;
        try {
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1);
        } catch (e) {
            passed = true;
        }
        expect(passed).toBeTruthy();
    });

    it("should reset proxy", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");

        // then
        client.verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 1);

        // when
        client.reset();

        // then
        var passed = false;
        try {
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1);
        } catch (e) {
            passed = true;
        }
        expect(passed).toBeTruthy();
    });
});
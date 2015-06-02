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
        mockServerClient("localhost", 1080).
            mockSimpleResponse('/somePath', { name: 'value' }, 203).
            mockSimpleResponse('/somePath', { name: 'value' }, 203);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(203);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(203);

        // when
        client.verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 2, true);
    });

    it("should verify at least a number of requests have been sent", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);

        // when
        client.verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 1);
    });


    it("should fail when no requests have been sent", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);

        // when
        expect(function () {
            client.verify(
                {
                    'path': '/someOtherPath'
                }, 1);
        }).toThrow();
    });

    it("should fail when not enough exact requests have been sent", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);

        // when
        expect(function () {
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 2, true);
        }).toThrow();
    });

    it("should fail when not enough at least requests have been sent", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);

        // when
        expect(function () {
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 2);
        }).toThrow();
    });

    it("should pass when correct sequence of requests have been sent", function () {
        // given
        var client = proxyClient("localhost", 1080);
        xmlhttp.open("POST", "http://localhost:1080/one", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/two", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/three", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);

        // when
        client.verifySequence(
            {
                'method': 'POST',
                'path': '/one',
                'body': 'someBody'
            },
            {
                'method': 'GET',
                'path': '/two'
            },
            {
                'method': 'GET',
                'path': '/three'
            }
        );
    });

    it("should fail when incorrect sequence of requests have been sent", function () {
        // given
        var client = proxyClient("localhost", 1080);
        xmlhttp.open("POST", "http://localhost:1080/one", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/two", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/three", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);

        // when - wrong order
        expect(function () {
            client.verifySequence(
                {
                    'method': 'POST',
                    'path': '/one',
                    'body': 'someBody'
                },
                {
                    'method': 'GET',
                    'path': '/three'
                },
                {
                    'method': 'GET',
                    'path': '/two'
                }
            );
        }).toThrow();

        // when - first request incorrect body
        expect(function () {
            client.verifySequence(
                {
                    'method': 'POST',
                    'path': '/one',
                    'body': 'some_incorrect_body'
                },
                {
                    'method': 'GET',
                    'path': '/two'
                },
                {
                    'method': 'GET',
                    'path': '/three'
                }
            );
        }).toThrow();
    });

    it("should clear proxy by path", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);

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
        expect(function () {
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1);
        }).toThrow();
    });

    it("should clear expectations by request matcher", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);

        // then
        client.verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 1);

        // when
        client.clear({
            "path": "/somePath"
        });

        // then
        expect(function () {
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1);
        }).toThrow();
    });

    it("should clear expectations by expectation matcher", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);

        // then
        client.verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 1);

        // when
        client.clear({
            "httpRequest": {
                "path": "/somePath"
            }
        });

        // then
        expect(function () {
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1);
        }).toThrow();
    });

    it("should reset proxy", function () {
        // given
        var client = proxyClient("localhost", 1090);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(404);

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
        expect(function () {
            client.verify(
                {
                    'method': 'POST',
                    'path': '/somePath',
                    'body': 'someBody'
                }, 1);
        }).toThrow();
    });
});
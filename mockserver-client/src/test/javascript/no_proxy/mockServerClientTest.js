describe("mockServerClient MockServer integration:", function () {
    var xmlhttp;

    beforeEach(function () {
        xmlhttp = new XMLHttpRequest();
        mockServerClient("http://localhost:1080", "http://localhost:1090").resetMocks();
        mockServerClient("http://localhost:1080", "http://localhost:1090").resetProxy();
    });

    it("should create full expectation", function () {
        // when
        mockServerClient("http://localhost:1080", "http://localhost:1090").mockAnyResponse(
            {
                'httpRequest': {
                    'method': 'POST',
                    'path': '/somePath',
                    'queryString': 'test=true',
                    'parameters': [
                        {
                            'name': 'test',
                            'values': [ 'true' ]
                        }
                    ],
                    'body': 'someBody'
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({ name: 'value' }),
                    'delay': {
                        'timeUnit': 'MILLISECONDS',
                        'value': 250
                    }
                },
                'times': {
                    'remainingTimes': 1,
                    'unlimited': false
                }
            }
        );

        // then - non matching request
        xmlhttp.open("GET", "http://localhost:1080/otherPath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching request
        xmlhttp.open("POST", "http://localhost:1080/somePath?test=true", false);
        xmlhttp.send("someBody");

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');

        // then - matching request, but no times remaining
        xmlhttp.open("POST", "http://localhost:1080/somePath?test=true", false);
        xmlhttp.send("someBody");

        expect(xmlhttp.status).toEqual(404);
    });

    it("should create simple response expectation", function () {
        // when
        mockServerClient("http://localhost:1080", "http://localhost:1090").mockSimpleResponse('/somePath', { name: 'value' }, 203);

        // then - non matching request
        xmlhttp.open("GET", "http://localhost:1080/otherPath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching request
        xmlhttp.open("POST", "http://localhost:1080/somePath?test=true", false);
        xmlhttp.send("someBody");

        expect(xmlhttp.status).toEqual(203);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');

        // then - matching request, but no times remaining
        xmlhttp.open("POST", "http://localhost:1080/somePath?test=true", false);
        xmlhttp.send("someBody");

        expect(xmlhttp.status).toEqual(404);
    });

    it("should clear expectations", function () {
        // when
        var client = mockServerClient("http://localhost:1080", "http://localhost:1090");
        client.mockSimpleResponse('/somePathOne', { name: 'value' }, 200);
        client.mockSimpleResponse('/somePathOne', { name: 'value' }, 200);
        client.mockSimpleResponse('/somePathTwo', { name: 'value' }, 200);

        // then - matching request
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');

        // when
        client.clearMocks('/somePathOne');

        // then - matching request but cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching request and not cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathTwo", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}')
    });
});
describe("mockServerClient client:", function () {
    var xmlhttp;

    beforeEach(function () {
        xmlhttp = new XMLHttpRequest();
        mockServerClient("localhost", 1080).reset();
        proxyClient("localhost", 1090).reset();
    });

    it("should create full expectation with Base64 encoded body", function () {
        // when
        mockServerClient("localhost", 1080).mockAnyResponse(
            {
                'httpRequest': {
                    'method': 'POST',
                    'path': '/somePath',
                    'queryStringParameters': [
                        {
                            'name': 'test',
                            'values': ['true']
                        }
                    ],
                    'body': {
                        'type': "STRING",
                        'value': 'someBody'
                    }
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'value'}),
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

    it("should create full expectation with string body", function () {
        // when
        mockServerClient("localhost", 1080).mockAnyResponse(
            {
                'httpRequest': {
                    'method': 'POST',
                    'path': '/somePath',
                    'queryStringParameters': [
                        {
                            'name': 'test',
                            'values': ['true']
                        }
                    ],
                    'body': {
                        'type': "STRING",
                        'value': 'someBody'
                    }
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'value'}),
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

    it("should match on method only", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'method': 'GET'
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'first_body'}),
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
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'method': 'POST'
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'second_body'}),
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

        // then - matching no expectation
        xmlhttp.open("PUT", "http://localhost:1080/somePath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching first expectation
        xmlhttp.open("GET", "http://localhost:1080/somePath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"first_body"}');

        // then - matching second expectation
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"second_body"}');
    });

    it("should match on path only", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'path': '/firstPath'
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'first_body'}),
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
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'path': '/secondPath'
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'second_body'}),
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

        // then - matching no expectation
        xmlhttp.open("GET", "http://localhost:1080/otherPath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching first expectation
        xmlhttp.open("GET", "http://localhost:1080/firstPath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"first_body"}');

        // then - matching second expectation
        xmlhttp.open("GET", "http://localhost:1080/secondPath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"second_body"}');
    });

    it("should match on query string parameters only", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'queryStringParameters': [
                        {
                            'name': 'param',
                            'values': ['first']
                        }
                    ]
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'first_body'}),
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
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'queryStringParameters': [
                        {
                            'name': 'param',
                            'values': ['second']
                        }
                    ]
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'second_body'}),
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

        // then - matching no expectation
        xmlhttp.open("GET", "http://localhost:1080/somePath?param=other", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching first expectation
        xmlhttp.open("GET", "http://localhost:1080/somePath?param=first", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"first_body"}');

        // then - matching second expectation
        xmlhttp.open("GET", "http://localhost:1080/somePath?param=second", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"second_body"}');
    });

    it("should match on body only", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'body': {
                        'type': "STRING",
                        'value': 'someBody'
                    }
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'first_body'}),
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
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'body': {
                        'type': "REGEX",
                        'value': 'someOtherBody'
                    }
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'second_body'}),
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

        // then - matching no expectation
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someIncorrectBody");

        expect(xmlhttp.status).toEqual(404);

        // then - matching first expectation
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"first_body"}');

        // then - matching second expectation
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someOtherBody");

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"second_body"}');
    });

    it("should match on headers only", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'headers': [
                        {
                            'name': 'header',
                            'values': ['first']
                        }
                    ]
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'first_body'}),
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
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'headers': [
                        {
                            'name': 'header',
                            'values': ['second']
                        }
                    ]
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'second_body'}),
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

        // then - matching no expectation
        xmlhttp.open("GET", "http://localhost:1080/somePath", false);
        xmlhttp.setRequestHeader('header', 'other');
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching first expectation
        xmlhttp.open("GET", "http://localhost:1080/somePath", false);
        xmlhttp.setRequestHeader('header', 'first');
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"first_body"}');

        // then - matching second expectation
        xmlhttp.open("GET", "http://localhost:1080/somePath", false);
        xmlhttp.setRequestHeader('header', 'second');
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"second_body"}');
    });

    it("should match on cookies only", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'cookies': [
                        {
                            'name': 'cookie',
                            'value': 'first'
                        }
                    ]
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'first_body'}),
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
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'cookies': [
                        {
                            'name': 'cookie',
                            'value': 'second'
                        }
                    ]
                },
                'httpResponse': {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'second_body'}),
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

        // then - matching no expectation
        document.cookie = "cookie=other";
        xmlhttp.open("GET", "http://localhost:1080/somePath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching first expectation
        document.cookie = "cookie=first";
        xmlhttp.open("GET", "http://localhost:1080/somePath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"first_body"}');

        // then - matching second expectation
        document.cookie = "cookie=second";
        xmlhttp.open("GET", "http://localhost:1080/somePath", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"second_body"}');
    });

    it("should create simple response expectation", function () {
        // when
        mockServerClient("localhost", 1080).mockSimpleResponse('/somePath', {name: 'value'}, 203);

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

    it("should update default headers for simple response expectation", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.setDefaultHeaders([
            {"name": "Content-Type", "values": ["application/json; charset=utf-8"]},
            {"name": "X-Test", "values": ["test-value"]}
        ]);
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203);

        // then - matching request
        xmlhttp.open("POST", "http://localhost:1080/somePath?test=true", false);
        xmlhttp.send("someBody");

        expect(xmlhttp.status).toEqual(203);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');
        expect(xmlhttp.getResponseHeader("X-Test")).toEqual("test-value");
    });

    it("should verify exact number of requests have been sent", function () {
        // given
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(203);

        // when
        client.verify(
            {
                'method': 'POST',
                'path': '/somePath',
                'body': 'someBody'
            }, 1, true);
    });

    it("should verify at least a number of requests have been sent", function () {
        // given
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203);
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203);
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
            }, 1);
    });

    it("should fail when no requests have been sent", function () {
        // given
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(203);

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
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(203);

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
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203);
        xmlhttp.open("POST", "http://localhost:1080/somePath", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(203);

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
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/one', {name: 'value'}, 203);
        client.mockSimpleResponse('/two', {name: 'value'}, 203);
        xmlhttp.open("POST", "http://localhost:1080/one", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(203);
        xmlhttp.open("GET", "http://localhost:1080/notFound", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/two", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(203);

        // when
        client.verifySequence(
            {
                'method': 'POST',
                'path': '/one',
                'body': 'someBody'
            },
            {
                'method': 'GET',
                'path': '/notFound'
            },
            {
                'method': 'GET',
                'path': '/two'
            }
        );
    });

    it("should fail when incorrect sequence of requests have been sent", function () {
        // given
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/one', {name: 'value'}, 203);
        client.mockSimpleResponse('/two', {name: 'value'}, 203);
        xmlhttp.open("POST", "http://localhost:1080/one", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(203);
        xmlhttp.open("GET", "http://localhost:1080/notFound", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/two", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(203);

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
                    'path': '/two'
                },
                {
                    'method': 'GET',
                    'path': '/notFound'
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
                    'path': '/notFound'
                },
                {
                    'method': 'GET',
                    'path': '/two'
                }
            );
        }).toThrow();
    });

    it("should clear expectations by path", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200);
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200);
        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200);

        // then - matching request
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');

        // when
        client.clear('/somePathOne');

        // then - matching request but cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching request and not cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathTwo", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');
    });

    it("should clear expectations by request matcher", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200);
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200);
        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200);

        // then - matching request
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');

        // when
        client.clear({
            "path": "/somePathOne"
        });

        // then - matching request but cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching request and not cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathTwo", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');
    });

    it("should clear expectations by expectation matcher", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200);
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200);
        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200);

        // then - matching request
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');

        // when
        client.clear({
            "httpRequest": {
                "path": "/somePathOne"
            }
        });

        // then - matching request but cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching request and not cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathTwo", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');
    });

    it("should reset expectations", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200);
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200);
        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200);

        // then - matching request
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(200);
        expect(xmlhttp.responseText).toEqual('{"name":"value"}');

        // when
        client.reset();

        // then - matching request but cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);

        // then - matching request but also cleared
        xmlhttp.open("GET", "http://localhost:1080/somePathTwo", false);
        xmlhttp.send();

        expect(xmlhttp.status).toEqual(404);
    });

    it("should retrieve some expectations using object matcher", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 303);

        // when
        var expectations = client.retrieveExpectations({
            "httpRequest": {
                "path": "/somePathOne"
            }
        });

        // then
        expect(expectations.length).toEqual(2);
        // first expectation
        expect(expectations[0].httpRequest.path).toEqual('/somePathOne');
        expect(expectations[0].httpResponse.body).toEqual('{"name":"one"}');
        expect(expectations[0].httpResponse.statusCode).toEqual(201);
        // second expectation
        expect(expectations[1].httpRequest.path).toEqual('/somePathOne');
        expect(expectations[1].httpResponse.body).toEqual('{"name":"one"}');
        expect(expectations[1].httpResponse.statusCode).toEqual(201);
    });

    it("should retrieve some expectations using path", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202);

        // when
        var expectations = client.retrieveExpectations("/somePathOne");

        // then
        expect(expectations.length).toEqual(2);
        // first expectation
        expect(expectations[0].httpRequest.path).toEqual('/somePathOne');
        expect(expectations[0].httpResponse.body).toEqual('{"name":"one"}');
        expect(expectations[0].httpResponse.statusCode).toEqual(201);
        // second expectation
        expect(expectations[1].httpRequest.path).toEqual('/somePathOne');
        expect(expectations[1].httpResponse.body).toEqual('{"name":"one"}');
        expect(expectations[1].httpResponse.statusCode).toEqual(201);
    });

    it("should retrieve all expectations using object matcher", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202);

        // when
        var expectations = client.retrieveExpectations({
            "httpRequest": {
                "path": "/somePath.*"
            }
        });

        // then
        expect(expectations.length).toEqual(3);
        // first expectation
        expect(expectations[0].httpRequest.path).toEqual('/somePathOne');
        expect(expectations[0].httpResponse.body).toEqual('{"name":"one"}');
        expect(expectations[0].httpResponse.statusCode).toEqual(201);
        // second expectation
        expect(expectations[1].httpRequest.path).toEqual('/somePathOne');
        expect(expectations[1].httpResponse.body).toEqual('{"name":"one"}');
        expect(expectations[1].httpResponse.statusCode).toEqual(201);
        // third expectation
        expect(expectations[2].httpRequest.path).toEqual('/somePathTwo');
        expect(expectations[2].httpResponse.body).toEqual('{"name":"two"}');
        expect(expectations[2].httpResponse.statusCode).toEqual(202);
    });

    it("should retrieve all expectations using null matcher", function () {
        // when
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202);

        // when
        var expectations = client.retrieveExpectations();

        // then
        expect(expectations.length).toEqual(3);
        // first expectation
        expect(expectations[0].httpRequest.path).toEqual('/somePathOne');
        expect(expectations[0].httpResponse.body).toEqual('{"name":"one"}');
        expect(expectations[0].httpResponse.statusCode).toEqual(201);
        // second expectation
        expect(expectations[1].httpRequest.path).toEqual('/somePathOne');
        expect(expectations[1].httpResponse.body).toEqual('{"name":"one"}');
        expect(expectations[1].httpResponse.statusCode).toEqual(201);
        // third expectation
        expect(expectations[2].httpRequest.path).toEqual('/somePathTwo');
        expect(expectations[2].httpResponse.body).toEqual('{"name":"two"}');
        expect(expectations[2].httpResponse.statusCode).toEqual(202);
    });

    it("should retrieve some requests using object matcher", function () {
        // given
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202);
        xmlhttp.open("POST", "http://localhost:1080/somePathOne", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(201);
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(201);
        xmlhttp.open("GET", "http://localhost:1080/notFound", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/somePathTwo", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(202);

        // when
        var requests = client.retrieveRequests({
            "httpRequest": {
                "path": "/somePathOne"
            }
        });

        // then
        expect(requests.length).toEqual(2);
        // first request
        expect(requests[0].path).toEqual('/somePathOne');
        expect(requests[0].method).toEqual('POST');
        expect(requests[0].body.string).toEqual('someBody');
        // second request
        expect(requests[1].path).toEqual('/somePathOne');
        expect(requests[1].method).toEqual('GET');
    });

    it("should retrieve some requests using path", function () {
        // given
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202);
        xmlhttp.open("POST", "http://localhost:1080/somePathOne", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(201);
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(201);
        xmlhttp.open("GET", "http://localhost:1080/notFound", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/somePathTwo", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(202);

        // when
        var requests = client.retrieveRequests("/somePathOne");

        // then
        expect(requests.length).toEqual(2);
        // first request
        expect(requests[0].path).toEqual('/somePathOne');
        expect(requests[0].method).toEqual('POST');
        expect(requests[0].body.string).toEqual('someBody');
        // second request
        expect(requests[1].path).toEqual('/somePathOne');
        expect(requests[1].method).toEqual('GET');
    });

    it("should retrieve all requests using object matcher", function () {
        // given
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202);
        xmlhttp.open("POST", "http://localhost:1080/somePathOne", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(201);
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(201);
        xmlhttp.open("GET", "http://localhost:1080/notFound", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/somePathTwo", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(202);

        // when
        var requests = client.retrieveRequests({
            "httpRequest": {
                "path": "/.*"
            }
        });

        // then
        expect(requests.length).toEqual(4);
        // first request
        expect(requests[0].path).toEqual('/somePathOne');
        expect(requests[0].method).toEqual('POST');
        expect(requests[0].body.string).toEqual('someBody');
        // second request
        expect(requests[1].path).toEqual('/somePathOne');
        expect(requests[1].method).toEqual('GET');
        // third request
        expect(requests[2].path).toEqual('/notFound');
        expect(requests[2].method).toEqual('GET');
        // fourth request
        expect(requests[3].path).toEqual('/somePathTwo');
        expect(requests[3].method).toEqual('GET');

    });

    it("should retrieve all requests using null matcher", function () {
        // given
        var client = mockServerClient("localhost", 1080);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201);
        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202);
        xmlhttp.open("POST", "http://localhost:1080/somePathOne", false);
        xmlhttp.send("someBody");
        expect(xmlhttp.status).toEqual(201);
        xmlhttp.open("GET", "http://localhost:1080/somePathOne", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(201);
        xmlhttp.open("GET", "http://localhost:1080/notFound", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(404);
        xmlhttp.open("GET", "http://localhost:1080/somePathTwo", false);
        xmlhttp.send();
        expect(xmlhttp.status).toEqual(202);

        // when
        var requests = client.retrieveRequests();

        // then
        expect(requests.length).toEqual(4);
        // first request
        expect(requests[0].path).toEqual('/somePathOne');
        expect(requests[0].method).toEqual('POST');
        expect(requests[0].body.string).toEqual('someBody');
        // second request
        expect(requests[1].path).toEqual('/somePathOne');
        expect(requests[1].method).toEqual('GET');
        // third request
        expect(requests[2].path).toEqual('/notFound');
        expect(requests[2].method).toEqual('GET');
        // fourth request
        expect(requests[3].path).toEqual('/somePathTwo');
        expect(requests[3].method).toEqual('GET');
    });
});
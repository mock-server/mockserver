function guid() {
    function s4() {
        return Math.floor((1 + Math.random()) * 0x10000)
            .toString(16)
            .substring(1);
    }

    return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' + s4() + s4() + s4();
}

describe("mockServerClient client:", function () {
    var mockServerPort = 1080;
    var uuid = guid();

    function HttpRequest() {
        var xmlhttp = new XMLHttpRequest();
        if (navigator.userAgent.indexOf('PhantomJS') !== -1) {
            var _this = {
                open: function (method, url) {
                    xmlhttp.open(method, url, false);
                },
                setRequestHeader: function (name, value) {
                    xmlhttp.setRequestHeader(name, value);
                },
                send: function (data) {
                    xmlhttp.send(data);
                    _this.onload.call(xmlhttp);
                }
            };
            return _this;
        }
        return xmlhttp;
    }

    var fail = function (error) {
        throw (error || "error");
    };

    var originalTimeout;
    var client = mockServerClient("localhost", mockServerPort).setDefaultHeaders(undefined, [
        {"name": "Vary", "values": [uuid]}
    ]);
    var clientOverTls = mockServerClient("localhost", mockServerPort, null, true).setDefaultHeaders(undefined, [
        {"name": "Vary", "values": [uuid]}
    ]);

    beforeEach(function (done) {
        originalTimeout = jasmine.DEFAULT_TIMEOUT_INTERVAL;
        jasmine.DEFAULT_TIMEOUT_INTERVAL = 250 * 1000;
        client.clear().then(function () {
            done();
        }, fail);
    });

    afterEach(function (done) {
        jasmine.DEFAULT_TIMEOUT_INTERVAL = originalTimeout;
        done();
    });

    it("should create full expectation with string body", function (done) {
        // when
        client.mockAnyResponse(
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
                        'type': 'STRING',
                        'string': 'someBody'
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
        ).then(function () {

            // then - non matching request
            var xmlhttp = HttpRequest();
            xmlhttp.onload = function () {
                expect(this.status).toEqual(404);

                // then - matching request
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(200);
                    expect(this.responseText).toEqual('{"name":"value"}');

                    // then - matching request, but no times remaining
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        expect(this.status).toEqual(404);

                        done();
                    };
                    xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.send("someBody");
                };
                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send("someBody");
            };
            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/otherPath");
            xmlhttp.setRequestHeader("Vary", uuid);
            xmlhttp.send();
        }, fail);
    });

    it("should create full expectation with string body over tls", function (done) {
        // when
        clientOverTls.mockAnyResponse(
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
                        'type': 'STRING',
                        'string': 'someBody'
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
        ).then(function () {
            // then - matching request
            var xmlhttp = HttpRequest();
            xmlhttp.onload = function () {
                expect(this.status).toEqual(200);
                expect(this.responseText).toEqual('{"name":"value"}');

                done();
            };
            xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
            xmlhttp.setRequestHeader("Vary", uuid);
            xmlhttp.send("someBody");
        }, fail);
    });

    it("should expose server validation failure", function (done) {
        // when
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'paths': '/somePath',
                    'body': {
                        'type': "STRING",
                        'vaue': 'someBody'
                    }
                },
                'httpResponse': {}
            }
        ).then(fail, function (error) {
            expect(error).toEqual("1 error:\n" +
                " - object instance has properties which are not allowed by the schema: [\"paths\"] for field \"/httpRequest\"\n" +
                    "\n" +
                    "See: https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.15.x for OpenAPI Specification");
            done();
        });
    });

    it("should match on method only", function (done) {
        // when
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
        ).then(function () {
            // and - another expectation
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
            ).then(function () {
                // then - matching no expectation
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(404);

                    // then - matching first expectation
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        expect(this.status).toEqual(200);
                        expect(this.responseText).toEqual('{"name":"first_body"}');

                        // then - matching second expectation
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(200);
                            expect(this.responseText).toEqual('{"name":"second_body"}');

                            done();
                        };
                        xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send();
                    };
                    xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePath");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.send();
                };
                xmlhttp.open("PUT", "http://localhost:" + mockServerPort + "/somePath");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send();
            }, fail);
        }, fail);
    });

    it("should match on path only", function (done) {
        // when
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
        ).then(function () {
            // and - another expectation
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
            ).then(function () {
                // then - matching no expectation
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(404);

                    // then - matching first expectation
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        expect(this.status).toEqual(200);
                        expect(this.responseText).toEqual('{"name":"first_body"}');

                        // then - matching second expectation
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(200);
                            expect(this.responseText).toEqual('{"name":"second_body"}');

                            done();
                        };
                        xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/secondPath");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send();
                    };
                    xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/firstPath");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.send();
                };
                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/otherPath");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send();
            }, fail);
        }, fail);
    });

    it("should match on query string parameters only", function (done) {
        // when
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
        ).then(function () {
            // and - another expectation
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
            ).then(function () {
                // then - matching no expectation
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(404);

                    // then - matching first expectation
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        expect(this.status).toEqual(200);
                        expect(this.responseText).toEqual('{"name":"first_body"}');

                        // then - matching second expectation
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(200);
                            expect(this.responseText).toEqual('{"name":"second_body"}');

                            done();
                        };
                        xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePath?param=second");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send();
                    };
                    xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePath?param=first");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.send();
                };
                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePath?param=other");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send();
            }, fail);
        }, fail);
    });

    it("should match on body only", function (done) {
        // when
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'body': {
                        'type': "STRING",
                        'string': 'someBody'
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
        ).then(function () {
            // and - another expectation
            client.mockAnyResponse(
                {
                    'httpRequest': {
                        'body': {
                            'type': "REGEX",
                            'regex': 'someOtherBody'
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
            ).then(function () {
                // then - matching no expectation
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(404);

                    // then - matching first expectation
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        expect(this.status).toEqual(200);
                        expect(this.responseText).toEqual('{"name":"first_body"}');

                        // then - matching second expectation
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(200);
                            expect(this.responseText).toEqual('{"name":"second_body"}');

                            done();
                        };
                        xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send("someOtherBody");
                    };
                    xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.send("someBody");
                };
                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send("someIncorrectBody");
            });
        }, fail);
    });

    it("should match on headers only", function (done) {
        // when
        client.mockAnyResponse(
            {
                'httpRequest': {
                    'headers': [
                        {
                            'name': 'Allow',
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
        ).then(function () {
            // and - another expectation
            client.mockAnyResponse(
                {
                    'httpRequest': {
                        'headers': [
                            {
                                'name': 'Allow',
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
            ).then(function () {
                // then - matching no expectation
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(404);

                    // then - matching first expectation
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        debugger;
                        expect(this.status).toEqual(200);
                        expect(this.responseText).toEqual('{"name":"first_body"}');

                        // then - matching second expectation
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(200);
                            expect(this.responseText).toEqual('{"name":"second_body"}');

                            done();
                        };
                        xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathZ");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.setRequestHeader('Allow', 'second');
                        xmlhttp.send();
                    };
                    xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathY");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.setRequestHeader('Allow', 'first');
                    xmlhttp.send();
                };
                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathX");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.setRequestHeader('Allow', 'other');
                xmlhttp.send();
            }, fail);
        }, fail);
    });

    it("should create simple response expectation", function (done) {
        // when
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203)
            .then(function () {
                // then - non matching request
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(404);

                    // then - matching request
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        expect(this.status).toEqual(203);
                        expect(this.responseText).toEqual('{"name":"value"}');

                        // then - matching request, but no times remaining
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(404);

                            done();
                        };
                        xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send("someBody");
                    };
                    xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.send("someBody");
                };
                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/otherPath");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send();
            }, fail);
    });

    it("should create expectation with method callback", function (done) {
        // when
        client.mockWithCallback({
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
                'string': 'someBody'
            }
        }, function (request) {
            if (request.method === 'POST' && request.path === '/somePath') {
                return {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'value'})
                };
            } else {
                return {
                    'statusCode': 406
                };
            }
        }).then(function () {

            // then - non matching request
            var xmlhttp = HttpRequest();
            xmlhttp.onload = function () {
                expect(this.status).toEqual(404);

                // then - matching request
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(200);
                    expect(this.responseText).toEqual('{"name":"value"}');

                    // then - matching request, but no times remaining
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        expect(this.status).toEqual(404);

                        done();
                    };
                    xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.send("someBody");
                };
                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send("someBody");
            };
            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/otherPath");
            xmlhttp.setRequestHeader("Vary", uuid);
            xmlhttp.send();
        }, fail);
    });

    it("should create expectation with method callback over tls", function (done) {
        // when
        clientOverTls.mockWithCallback({
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
                'string': 'someBody'
            }
        }, function (request) {
            if (request.method === 'POST' && request.path === '/somePath') {
                return {
                    'statusCode': 200,
                    'body': JSON.stringify({name: 'value'})
                };
            } else {
                return {
                    'statusCode': 406
                };
            }
        }).then(function () {

            // then - non matching request
            var xmlhttp = HttpRequest();
            xmlhttp.onload = function () {
                expect(this.status).toEqual(404);

                // then - matching request
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(200);
                    expect(this.responseText).toEqual('{"name":"value"}');

                    // then - matching request, but no times remaining
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        expect(this.status).toEqual(404);

                        done();
                    };
                    xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.send("someBody");
                };
                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send("someBody");
            };
            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/otherPath");
            xmlhttp.setRequestHeader("Vary", uuid);
            xmlhttp.send();
        }, fail);
    });

    it("should create multiple parallel expectation with method callback", function (done) {
        // when
        client.mockWithCallback({
            'method': 'GET',
            'path': '/one'
        }, function (request) {
            if (request.method === 'GET' && request.path === '/one') {
                return {
                    'statusCode': 201,
                    'body': 'one'
                };
            } else {
                return {
                    'statusCode': 406
                };
            }
        }, {
            remainingTimes: 2,
            unlimited: false
        })
            .then(function () {

                client.mockWithCallback({
                    'method': 'GET',
                    'path': '/two'
                }, function (request) {
                    if (request.method === 'GET' && request.path === '/two') {
                        return {
                            'statusCode': 202,
                            'body': 'two'
                        };
                    } else {
                        return {
                            'statusCode': 406
                        };
                    }
                })
                    .then(function () {

                        // then - first matching request
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(201);
                            expect(this.responseText).toEqual('one');

                            // then - second matching request
                            var xmlhttp = HttpRequest();
                            xmlhttp.onload = function () {
                                expect(this.status).toEqual(202);
                                expect(this.responseText).toEqual('two');

                                // then - first matching request again
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(201);
                                    expect(this.responseText).toEqual('one');

                                    done();
                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/one");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            };
                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/two");
                            xmlhttp.setRequestHeader("Vary", uuid);
                            xmlhttp.send("someBody");
                        };
                        xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/one");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send();
                    }, fail);
            }, fail);
    });

    it("should create expectation with method callback with numeric times", function (done) {
        // when
        client.mockWithCallback({
            'method': 'GET',
            'path': '/one'
        }, function (request) {
            if (request.method === 'GET' && request.path === '/one') {
                return {
                    'statusCode': 201,
                    'body': 'one'
                };
            } else {
                return {
                    'statusCode': 406
                };
            }
        }, 2).then(function () {

            // then - first matching request
            var xmlhttp = HttpRequest();
            xmlhttp.onload = function () {
                expect(this.status).toEqual(201);
                expect(this.responseText).toEqual('one');

                // then - first matching request again
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(201);
                    expect(this.responseText).toEqual('one');

                    // then - should no match request again
                    var xmlhttp = HttpRequest();
                    xmlhttp.onload = function () {
                        expect(this.status).toEqual(404);
                        expect(this.responseText).toEqual('');

                        done();
                    };
                    xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/one");
                    xmlhttp.setRequestHeader("Vary", uuid);
                    xmlhttp.send();
                };
                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/one");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send();
            };
            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/one");
            xmlhttp.setRequestHeader("Vary", uuid);
            xmlhttp.send();
        }, fail);
    });

    it("should update default headers for simple response expectation", function (done) {
        // when
        client.setDefaultHeaders([
            {"name": "Content-Type", "values": ["application/json; charset=utf-8"]},
            {"name": "Location", "values": ["test-value"]}
        ]);
        // and - an expectation
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
            // then - matching request
            var xmlhttp = HttpRequest();
            xmlhttp.onload = function () {
                expect(this.status).toEqual(203);
                expect(this.responseText).toEqual('{"name":"value"}');
                expect(this.getResponseHeader("Location")).toEqual("test-value");

                done();
            };
            xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath?test=true");
            xmlhttp.setRequestHeader("Vary", uuid);
            xmlhttp.send("someBody");
        });
    });

    it("should verify exact number of requests have been sent", function (done) {
        // given
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
            var xmlhttp = HttpRequest();
            xmlhttp.onload = function () {
                expect(this.status).toEqual(203);

                // when - verify at least one request
                client.verify(
                    {
                        'method': 'POST',
                        'path': '/somePath',
                        'body': 'someBody'
                    }, 1, 1).then(function () {
                    done();
                }, fail);
            };
            xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
            xmlhttp.setRequestHeader("Vary", uuid);
            xmlhttp.send("someBody");
        }, fail);

    });

    it("should verify at least a number of requests have been sent", function (done) {
        // given
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203)
            .then(function () {
                client.mockSimpleResponse('/somePath', {name: 'value'}, 203)
                    .then(function () {
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(203);
                            var xmlhttp = HttpRequest();
                            xmlhttp.onload = function () {
                                expect(this.status).toEqual(203);

                                // when
                                client.verify(
                                    {
                                        'method': 'POST',
                                        'path': '/somePath',
                                        'body': 'someBody'
                                    }, 1)
                                    .then(function () {
                                        done();
                                    }, fail);
                            };
                            xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
                            xmlhttp.setRequestHeader("Vary", uuid);
                            xmlhttp.send("someBody");
                        };
                        xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send("someBody");
                    }, fail);
            }, fail);
    });

    it("should fail when no requests have been sent", function (done) {
        // given
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203)
            .then(function () {
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(203);

                    // when - verify at least one request (should fail)
                    client.verify({
                        'path': '/someOtherPath'
                    }, 1)
                        .then(fail, function (error) {
                            expect(error).toContain("Request not found at least once, expected:<{\n" +
                                "  \"path\" : \"/someOtherPath\",\n" +
                                "  \"headers\" : {\n" +
                                "    \"Vary\" : [ \"" + uuid + "\" ]\n" +
                                "  }\n" +
                                "}> but was:<");
                            done();
                        });
                };
                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send("someBody");
            }, fail);
    });

    it("should fail when not enough exact requests have been sent", function (done) {
        // given
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203)
            .then(function () {
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(203);

                        // when - verify exact two requests (should fail)
                    client.verify(
                        {
                            'method': 'POST',
                            'path': '/somePath',
                            'body': 'someBody'
                        }, 2, 3)
                        .then(fail, function (error) {
                            expect(error).toContain("Request not found between 2 and 3 times, expected:<{\n" +
                                "  \"method\" : \"POST\",\n" +
                                "  \"path\" : \"/somePath\",\n" +
                                "  \"body\" : \"someBody\",\n" +
                                "  \"headers\" : {\n" +
                                "    \"Vary\" : [ \"" + uuid + "\" ]\n" +
                                "  }\n" +
                                "}> but was:<");
                            done();
                        });
                };
                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send("someBody");
            }, fail);
    });

    it("should fail when not enough at least requests have been sent", function (done) {
        // given
        client.mockSimpleResponse('/somePath', {name: 'value'}, 203)
            .then(function () {
                var xmlhttp = HttpRequest();
                xmlhttp.onload = function () {
                    expect(this.status).toEqual(203);

                    // when
                    client.verify(
                        {
                            'method': 'POST',
                            'path': '/somePath',
                            'body': 'someBody'
                        }, 2)
                        .then(fail, function (error) {
                            expect(error).toContain("Request not found at least 2 times, expected:<{\n" +
                                "  \"method\" : \"POST\",\n" +
                                "  \"path\" : \"/somePath\",\n" +
                                "  \"body\" : \"someBody\",\n" +
                                "  \"headers\" : {\n" +
                                "    \"Vary\" : [ \"" + uuid + "\" ]\n" +
                                "  }\n" +
                                "}> but was:<");
                            done();
                        });
                };
                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePath");
                xmlhttp.setRequestHeader("Vary", uuid);
                xmlhttp.send("someBody");
            }, fail);
    });

    it("should pass when correct sequence of requests have been sent", function (done) {
        // given
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                    .then(function () {
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(201);

                            var xmlhttp = HttpRequest();
                            xmlhttp.onload = function () {
                                expect(this.status).toEqual(404);

                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(202);

                                    // when
                                    client.verifySequence(
                                        {
                                            'method': 'POST',
                                            'path': '/somePathOne',
                                            'body': 'someBody'
                                        },
                                        {
                                            'method': 'GET',
                                            'path': '/notFound'
                                        },
                                        {
                                            'method': 'GET',
                                            'path': '/somePathTwo'
                                        }
                                    ).then(function () {
                                        done();
                                    }, fail);
                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            };
                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/notFound");
                            xmlhttp.setRequestHeader("Vary", uuid);
                            xmlhttp.send();
                        };
                        xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePathOne");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send("someBody");
                    }, fail);
            }, fail);
    });

    it("should fail when incorrect sequence (wrong order) of requests have been sent", function (done) {
        // given
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                    .then(function () {
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(201);

                            var xmlhttp = HttpRequest();
                            xmlhttp.onload = function () {
                                expect(this.status).toEqual(404);

                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(202);

                                    // when - wrong order
                                    client.verifySequence(
                                        {
                                            'method': 'POST',
                                            'path': '/somePathOne',
                                            'body': 'someBody'
                                        },
                                        {
                                            'method': 'GET',
                                            'path': '/somePathTwo'
                                        },
                                        {
                                            'method': 'GET',
                                            'path': '/notFound'
                                        }
                                    ).then(fail, function (error) {
                                        expect(error).toContain("Request sequence not found, expected:<[ {\n" +
                                            "  \"method\" : \"POST\",\n" +
                                            "  \"path\" : \"/somePathOne\",\n" +
                                            "  \"body\" : \"someBody\",\n" +
                                            "  \"headers\" : {\n" +
                                            "    \"Vary\" : [ \"" + uuid + "\" ]\n" +
                                            "  }\n" +
                                            "}, {\n" +
                                            "  \"method\" : \"GET\",\n" +
                                            "  \"path\" : \"/somePathTwo\",\n" +
                                            "  \"headers\" : {\n" +
                                            "    \"Vary\" : [ \"" + uuid + "\" ]\n" +
                                            "  }\n" +
                                            "}, {\n" +
                                            "  \"method\" : \"GET\",\n" +
                                            "  \"path\" : \"/notFound\",\n" +
                                            "  \"headers\" : {\n" +
                                            "    \"Vary\" : [ \"" + uuid + "\" ]\n" +
                                            "  }\n" +
                                            "} ]> but was:<[ {\n");
                                        done();
                                    });


                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            };
                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/notFound");
                            xmlhttp.setRequestHeader("Vary", uuid);
                            xmlhttp.send();
                        };
                        xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePathOne");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send("someBody");
                    }, fail);
            }, fail);
    });

    it("should fail when incorrect sequence (first request incorrect body) of requests have been sent", function (done) {
        // given
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                    .then(function () {
                        var xmlhttp = HttpRequest();
                        xmlhttp.onload = function () {
                            expect(this.status).toEqual(201);

                            var xmlhttp = HttpRequest();
                            xmlhttp.onload = function () {
                                expect(this.status).toEqual(404);

                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(202);

                                    // when - first request incorrect body
                                    client.verifySequence(
                                        {
                                            'method': 'POST',
                                            'path': '/somePathOne',
                                            'body': 'some_incorrect_body'
                                        },
                                        {
                                            'method': 'GET',
                                            'path': '/notFound'
                                        },
                                        {
                                            'method': 'GET',
                                            'path': '/somePathTwo'
                                        }
                                    ).then(fail, function (error) {
                                        expect(error).toContain("Request sequence not found, expected:<[ {\n" +
                                            "  \"method\" : \"POST\",\n" +
                                            "  \"path\" : \"/somePathOne\",\n" +
                                            "  \"body\" : \"some_incorrect_body\",\n" +
                                            "  \"headers\" : {\n" +
                                            "    \"Vary\" : [ \"" + uuid + "\" ]\n" +
                                            "  }\n" +
                                            "}, {\n" +
                                            "  \"method\" : \"GET\",\n" +
                                            "  \"path\" : \"/notFound\",\n" +
                                            "  \"headers\" : {\n" +
                                            "    \"Vary\" : [ \"" + uuid + "\" ]\n" +
                                            "  }\n" +
                                            "}, {\n" +
                                            "  \"method\" : \"GET\",\n" +
                                            "  \"path\" : \"/somePathTwo\",\n" +
                                            "  \"headers\" : {\n" +
                                            "    \"Vary\" : [ \"" + uuid + "\" ]\n" +
                                            "  }\n" +
                                            "} ]> but was:<[ {\n");
                                        done();
                                    });
                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            };
                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/notFound");
                            xmlhttp.setRequestHeader("Vary", uuid);
                            xmlhttp.send();
                        };
                        xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePathOne");
                        xmlhttp.setRequestHeader("Vary", uuid);
                        xmlhttp.send("someBody");
                    }, fail);
            }, fail);
    });

    it("should clear expectations and logs by path", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200)
                            .then(function () {

                                // then - matching request
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {

                                    expect(this.status).toEqual(200);
                                    expect(this.responseText).toEqual('{"name":"value"}');

                                    // when
                                    client.clear('/somePathOne')
                                        .then(function () {

                                            // then - matching request but cleared
                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {

                                                expect(this.status).toEqual(404);

                                                client.clear('/somePathOne')
                                                    .then(function () {
                                                        // then - return no logs for clear requests
                                                        client.retrieveRecordedRequests({
                                                            "httpRequest": {
                                                                "path": "/somePathOne"
                                                            }
                                                        }).then(function (requests) {
                                                            expect(requests.length).toEqual(0);


                                                            // then - matching request and not cleared
                                                            var xmlhttp = HttpRequest();
                                                            xmlhttp.onload = function () {
                                                                expect(this.status).toEqual(200);
                                                                expect(this.responseText).toEqual('{"name":"value"}');


                                                                // then - return logs for not cleared requests
                                                                client.retrieveRecordedRequests({
                                                                    "httpRequest": {
                                                                        "path": "/somePathTwo"
                                                                    }
                                                                })
                                                                    .then(function (requests) {
                                                                        expect(requests.length).toEqual(1);
                                                                        expect(requests[0].path).toEqual('/somePathTwo');

                                                                        done();
                                                                    }, fail);
                                                            };
                                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                                            xmlhttp.setRequestHeader("Vary", uuid);
                                                            xmlhttp.send();
                                                        }, fail);
                                                    }, fail);
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        }, fail);
                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should clear expectations by request matcher", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200)
                            .then(function () {

                                // then - matching request
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(200);
                                    expect(this.responseText).toEqual('{"name":"value"}');

                                    // when
                                    client.clear({
                                        "path": "/somePathOne"
                                    })
                                        .then(function () {

                                            // then - matching request but cleared
                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {
                                                expect(this.status).toEqual(404);

                                                // then - matching request and not cleared
                                                var xmlhttp = HttpRequest();
                                                xmlhttp.onload = function () {
                                                    expect(this.status).toEqual(200);
                                                    expect(this.responseText).toEqual('{"name":"value"}');

                                                    done();
                                                };
                                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                                xmlhttp.setRequestHeader("Vary", uuid);
                                                xmlhttp.send();
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        }, fail);
                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should clear expectations by expectation matcher", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200)
                            .then(function () {
                                // then - matching request
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(200);
                                    expect(this.responseText).toEqual('{"name":"value"}');

                                    // when - some expectations cleared
                                    client.clear({
                                        "httpRequest": {
                                            "path": "/somePathOne"
                                        }
                                    })
                                        .then(function () {
                                            // then - request matching cleared expectation should return 404
                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {
                                                expect(this.status).toEqual(404);

                                                // then - matching request and not cleared
                                                var xmlhttp = HttpRequest();
                                                xmlhttp.onload = function () {
                                                    expect(this.status).toEqual(200);
                                                    expect(this.responseText).toEqual('{"name":"value"}');

                                                    done();
                                                };
                                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                                xmlhttp.setRequestHeader("Vary", uuid);
                                                xmlhttp.send();
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        }, fail);
                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should clear only expectations by path", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200)
                            .then(function () {
                                // then - matching request
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(200);
                                    expect(this.responseText).toEqual('{"name":"value"}');

                                    // when
                                    client.clear("/somePathOne", "EXPECTATIONS")
                                        .then(function () {
                                            // then - matching request but cleared
                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {
                                                expect(this.status).toEqual(404);

                                                // when
                                                client.clear("/somePathOne", "EXPECTATIONS")
                                                    .then(function () {
                                                        // then - return no logs for clear requests
                                                        var requests = client.retrieveRecordedRequests({
                                                            "httpRequest": {
                                                                "path": "/somePathOne"
                                                            }
                                                        })
                                                            .then(function (requests) {
                                                                expect(requests.length).toEqual(2);
                                                                expect(requests[0].path).toEqual('/somePathOne');
                                                                expect(requests[1].path).toEqual('/somePathOne');

                                                                done();
                                                            }, fail);
                                                    }, fail);
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        }, fail);
                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should clear only logs by path", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200)
                            .then(function () {
                                // then - matching request
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(200);
                                    expect(this.responseText).toEqual('{"name":"value"}');

                                    // when
                                    client.clear("/somePathOne", "LOG")
                                        .then(function () {
                                            // then - matching request but cleared
                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {
                                                expect(this.status).toEqual(200);
                                                expect(this.responseText).toEqual('{"name":"value"}');

                                                // when
                                                client.clear("/somePathOne", "LOG")
                                                    .then(function () {
                                                        // then - return no logs for clear requests
                                                        client.retrieveRecordedRequests({
                                                            "httpRequest": {
                                                                "path": "/somePathOne"
                                                            }
                                                        })
                                                            .then(function (requests) {
                                                                expect(requests.length).toEqual(0);

                                                                done();
                                                            }, fail);
                                                    }, fail);
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        }, fail);
                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should reset expectations", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200)
                            .then(function () {
                                // then - a matching request (that returns 200)
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(200);
                                    expect(this.responseText).toEqual('{"name":"value"}');

                                    // when
                                    client.clear()
                                        .then(function () {
                                            // then - request matching one reset expectation should return 404
                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {
                                                expect(this.status).toEqual(404);

                                                // then - request matching other reset expectation should return 404
                                                var xmlhttp = HttpRequest();
                                                xmlhttp.onload = function () {
                                                    expect(this.status).toEqual(404);

                                                    done();
                                                };
                                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                                xmlhttp.setRequestHeader("Vary", uuid);
                                                xmlhttp.send();
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        }, fail);
                                };
                                xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send();
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should retrieve some recorded requests using object matcher", function (done) {
        // given
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                            .then(function () {
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(201);

                                    var xmlhttp = HttpRequest();
                                    xmlhttp.onload = function () {
                                        expect(this.status).toEqual(201);

                                        var xmlhttp = HttpRequest();
                                        xmlhttp.onload = function () {
                                            expect(this.status).toEqual(404);

                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {
                                                expect(this.status).toEqual(202);

                                                // when
                                                var requests = client.retrieveRecordedRequests({
                                                    "httpRequest": {
                                                        "path": "/somePathOne"
                                                    }
                                                })
                                                    .then(function (requests) {

                                                        // then
                                                        expect(requests.length).toEqual(2);
                                                        // first request
                                                        expect(requests[0].path).toEqual('/somePathOne');
                                                        expect(requests[0].method).toEqual('POST');
                                                        if (window.navigator.userAgent.indexOf('Phantom') !== -1) {
                                                            expect(requests[0].body).toEqual('someBody');
                                                        } else if (window.navigator.userAgent.indexOf('Chrome') !== -1) {
                                                            expect(requests[0].body).toEqual({
                                                                contentType: "text/plain; charset=utf-8",
                                                                string: "someBody",
                                                                rawBytes: 'c29tZUJvZHk=',
                                                                type: "STRING"
                                                            });
                                                        }
                                                        // second request
                                                        expect(requests[1].path).toEqual('/somePathOne');
                                                        expect(requests[1].method).toEqual('GET');

                                                        done();
                                                    }, fail);
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        };
                                        xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/notFound");
                                        xmlhttp.setRequestHeader("Vary", uuid);
                                        xmlhttp.send();
                                    };
                                    xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                    xmlhttp.setRequestHeader("Vary", uuid);
                                    xmlhttp.send();
                                };
                                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send("someBody");
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should retrieve some recorded requests using path", function (done) {
        // given
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                            .then(function () {
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(201);

                                    var xmlhttp = HttpRequest();
                                    xmlhttp.onload = function () {
                                        expect(this.status).toEqual(201);

                                        var xmlhttp = HttpRequest();
                                        xmlhttp.onload = function () {
                                            expect(this.status).toEqual(404);

                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {
                                                expect(this.status).toEqual(202);

                                                // when
                                                var requests = client.retrieveRecordedRequests("/somePathOne")
                                                    .then(function (requests) {

                                                        // then
                                                        expect(requests.length).toEqual(2);
                                                        // first request
                                                        expect(requests[0].path).toEqual('/somePathOne');
                                                        expect(requests[0].method).toEqual('POST');
                                                        if (window.navigator.userAgent.indexOf('Phantom') !== -1) {
                                                            expect(requests[0].body).toEqual('someBody');
                                                        } else if (window.navigator.userAgent.indexOf('Chrome') !== -1) {
                                                            expect(requests[0].body).toEqual({
                                                                contentType: "text/plain; charset=utf-8",
                                                                string: "someBody",
                                                                rawBytes: 'c29tZUJvZHk=',
                                                                type: "STRING"
                                                            });
                                                        }
                                                        // second request
                                                        expect(requests[1].path).toEqual('/somePathOne');
                                                        expect(requests[1].method).toEqual('GET');

                                                        done();
                                                    }, fail);
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        };
                                        xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/notFound");
                                        xmlhttp.setRequestHeader("Vary", uuid);
                                        xmlhttp.send();
                                    };
                                    xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                    xmlhttp.setRequestHeader("Vary", uuid);
                                    xmlhttp.send();
                                };
                                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send("someBody");
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should retrieve all recorded requests using object matcher", function (done) {
        // given
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                            .then(function () {

                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(201);

                                    var xmlhttp = HttpRequest();
                                    xmlhttp.onload = function () {
                                        expect(this.status).toEqual(201);

                                        var xmlhttp = HttpRequest();
                                        xmlhttp.onload = function () {
                                            expect(this.status).toEqual(404);

                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {
                                                expect(this.status).toEqual(202);

                                                // when
                                                var requests = client.retrieveRecordedRequests({
                                                    "httpRequest": {
                                                        "path": "/.*"
                                                    }
                                                })
                                                    .then(function (requests) {

                                                        // then
                                                        expect(requests.length).toEqual(4);
                                                        // first request
                                                        expect(requests[0].path).toEqual('/somePathOne');
                                                        expect(requests[0].method).toEqual('POST');
                                                        if (window.navigator.userAgent.indexOf('Phantom') !== -1) {
                                                            expect(requests[0].body).toEqual('someBody');
                                                        } else if (window.navigator.userAgent.indexOf('Chrome') !== -1) {
                                                            expect(requests[0].body).toEqual({
                                                                contentType: "text/plain; charset=utf-8",
                                                                string: "someBody",
                                                                rawBytes: 'c29tZUJvZHk=',
                                                                type: "STRING"
                                                            });
                                                        }
                                                        // second request
                                                        expect(requests[1].path).toEqual('/somePathOne');
                                                        expect(requests[1].method).toEqual('GET');
                                                        // third request
                                                        expect(requests[2].path).toEqual('/notFound');
                                                        expect(requests[2].method).toEqual('GET');
                                                        // fourth request
                                                        expect(requests[3].path).toEqual('/somePathTwo');
                                                        expect(requests[3].method).toEqual('GET');

                                                        done();
                                                    }, fail);
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        };
                                        xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/notFound");
                                        xmlhttp.setRequestHeader("Vary", uuid);
                                        xmlhttp.send();
                                    };
                                    xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                    xmlhttp.setRequestHeader("Vary", uuid);
                                    xmlhttp.send();
                                };
                                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send("someBody");
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should retrieve all recorded requests using null matcher", function (done) {
        // given
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                            .then(function () {
                                var xmlhttp = HttpRequest();
                                xmlhttp.onload = function () {
                                    expect(this.status).toEqual(201);

                                    var xmlhttp = HttpRequest();
                                    xmlhttp.onload = function () {
                                        expect(this.status).toEqual(201);

                                        var xmlhttp = HttpRequest();
                                        xmlhttp.onload = function () {
                                            expect(this.status).toEqual(404);

                                            var xmlhttp = HttpRequest();
                                            xmlhttp.onload = function () {
                                                expect(this.status).toEqual(202);

                                                // when
                                                client.retrieveRecordedRequests()
                                                    .then(function (requests) {
                                                        // then
                                                        expect(requests.length).toEqual(4);
                                                        // first request
                                                        expect(requests[0].path).toEqual('/somePathOne');
                                                        expect(requests[0].method).toEqual('POST');
                                                        if (window.navigator.userAgent.indexOf('Phantom') !== -1) {
                                                            expect(requests[0].body).toEqual('someBody');
                                                        } else if (window.navigator.userAgent.indexOf('Chrome') !== -1) {
                                                            expect(requests[0].body).toEqual({
                                                                contentType: "text/plain; charset=utf-8",
                                                                string: "someBody",
                                                                rawBytes: 'c29tZUJvZHk=',
                                                                type: "STRING"
                                                            });
                                                        }
                                                        // second request
                                                        expect(requests[1].path).toEqual('/somePathOne');
                                                        expect(requests[1].method).toEqual('GET');
                                                        // third request
                                                        expect(requests[2].path).toEqual('/notFound');
                                                        expect(requests[2].method).toEqual('GET');
                                                        // fourth request
                                                        expect(requests[3].path).toEqual('/somePathTwo');
                                                        expect(requests[3].method).toEqual('GET');

                                                        done();
                                                    }, fail);
                                            };
                                            xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathTwo");
                                            xmlhttp.setRequestHeader("Vary", uuid);
                                            xmlhttp.send();
                                        };
                                        xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/notFound");
                                        xmlhttp.setRequestHeader("Vary", uuid);
                                        xmlhttp.send();
                                    };
                                    xmlhttp.open("GET", "http://localhost:" + mockServerPort + "/somePathOne");
                                    xmlhttp.setRequestHeader("Vary", uuid);
                                    xmlhttp.send();
                                };
                                xmlhttp.open("POST", "http://localhost:" + mockServerPort + "/somePathOne");
                                xmlhttp.setRequestHeader("Vary", uuid);
                                xmlhttp.send("someBody");
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should retrieve some active expectations using object matcher", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 303)
                            .then(function () {
                                // when
                                var expectations = client.retrieveActiveExpectations({
                                    "httpRequest": {
                                        "path": "/somePathOne"
                                    }
                                })
                                    .then(function (expectations) {
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

                                        done();
                                    }, fail);
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should retrieve some active expectations using path", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                            .then(function () {
                                // when
                                var expectations = client.retrieveActiveExpectations("/somePathOne")
                                    .then(function (expectations) {
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

                                        done();
                                    }, fail);
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should retrieve all active expectations using object matcher", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                            .then(function () {
                                // when
                                var expectations = client.retrieveActiveExpectations({
                                    "httpRequest": {
                                        "path": "/somePath.*"
                                    }
                                })
                                    .then(function (expectations) {
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

                                        done();
                                    }, fail);
                            }, fail);
                    }, fail);
            }, fail);
    });

    it("should retrieve all active expectations using null matcher", function (done) {
        // when
        client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
            .then(function () {
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                    .then(function () {
                        client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202)
                            .then(function () {
                                // when
                                var expectations = client.retrieveActiveExpectations()
                                    .then(function (expectations) {

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

                                        done();
                                    }, fail);
                            }, fail);
                    }, fail);
            }, fail);
    });

});
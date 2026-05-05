(function () {

    'use strict';

    function guid() {
        function s4() {
            return Math.floor((1 + Math.random()) * 0x10000)
                .toString(16)
                .substring(1);
        }

        return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' + s4() + s4() + s4();
    }

    var mockServer = require('../../');
    var mockServerClient = mockServer.mockServerClient;
    var Q = require('q');
    var http = require('http');
    var mockServerPort = 1080;
    var uuid = guid();

    function sendRequest(method, host, port, path, jsonBody, headers) {
        var deferred = Q.defer();

        var body = (typeof jsonBody === "string" ? jsonBody : JSON.stringify(jsonBody || ""));
        var options = {
            method: method,
            host: host,
            port: port,
            headers: headers || {},
            path: path
        };
        options.headers.Connection = "keep-alive";

        var callback = function (response) {
            var data = '';

            response.on('data', function (chunk) {
                data += chunk;
            });

            response.on('end', function () {
                if (response.statusCode >= 400 && response.statusCode < 600) {
                    if (response.statusCode === 404) {
                        deferred.reject("404 Not Found");
                    } else {
                        deferred.reject(data);
                    }
                } else {
                    deferred.resolve({
                        statusCode: response.statusCode,
                        headers: response.headers,
                        body: data
                    });
                }
            });
        };

        var req = http.request(options, callback);
        if (options.method === "POST") {
            req.write(body);
        }
        req.end();

        return deferred.promise;
    }

    var client;
    var clientOverTls;

    exports.mock_server_node_client_test = {
        setUp: function (callback) {
            client = mockServerClient("localhost", mockServerPort);
            client.reset().then(function () {
                clientOverTls = mockServerClient("localhost", mockServerPort, undefined, true);
                clientOverTls.reset().then(function () {
                    callback();
                }, function (error) {
                    throw 'Failed with error ' + JSON.stringify(error);
                });
            }, function (error) {
                throw 'Failed with error ' + JSON.stringify(error);
            });
        },

        'should create full expectation with string body': function (test) {
            // when
            var mockAnyResponse = client.mockAnyResponse(
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
            );
            mockAnyResponse.then(function () {

                // then - non matching request
                sendRequest("GET", "localhost", mockServerPort, "/otherPath")
                    .then(function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    }, function (error) {
                        test.equal(error, "404 Not Found");
                    })
                    .then(function () {

                        // then - matching request
                        sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody")
                            .then(function (response) {
                                test.equal(response.statusCode, 200);
                                test.equal(response.body, '{"name":"value"}');
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                // then - matching request, but no times remaining
                                sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody")
                                    .then(function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    }, function (error) {
                                        test.equal(error, "404 Not Found");
                                    })
                                    .then(function () {
                                        test.done();
                                    });
                            });
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should create full expectation with string body over tls': function (test) {
            // when
            var mockAnyResponse = clientOverTls.mockAnyResponse(
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
            );
            mockAnyResponse.then(function () {
                // then - matching request
                sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody")
                    .then(function (response) {
                        test.equal(response.statusCode, 200);
                        test.equal(response.body, '{"name":"value"}');
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    })
                    .then(function () {
                        test.done();
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should create expectations from array': function (test) {
            // when
            client.mockAnyResponse([
                {
                    'httpRequest': {
                        'path': '/somePathOne'
                    },
                    'httpResponse': {
                        'body': JSON.stringify({name: 'one'})
                    }
                },
                {
                    'httpRequest': {
                        'path': '/somePathTwo'
                    },
                    'httpResponse': {
                        'body': JSON.stringify({name: 'two'})
                    }
                },
                {
                    'httpRequest': {
                        'path': '/somePathThree'
                    },
                    'httpResponse': {
                        'body': JSON.stringify({name: 'three'})
                    }
                }
            ]).then(function () {

                // then
                sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                    .then(function (response) {
                        test.equal(response.statusCode, 200);
                        test.equal(response.body, '{"name":"one"}');

                        // then
                        sendRequest("GET", "localhost", mockServerPort, "/somePathTwo", "someBody")
                            .then(function (response) {
                                test.equal(response.statusCode, 200);
                                test.equal(response.body, '{"name":"two"}');

                                // then
                                sendRequest("GET", "localhost", mockServerPort, "/somePathThree", "someBody")
                                    .then(function (response) {
                                        test.equal(response.statusCode, 200);
                                        test.equal(response.body, '{"name":"three"}');
                                        test.done();
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    });
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should set default headers on expectation array': function (test) {
            // when
            client.setDefaultHeaders([
                {"name": "x-test-default", "values": ["default-value"]}
            ]);
            client.mockAnyResponse([
                {
                    'httpRequest': {
                        'path': '/somePathOne'
                    },
                    'httpResponse': {
                        'body': JSON.stringify({name: 'one'})
                    }
                },
                {
                    'httpRequest': {
                        'path': '/somePathTwo'
                    },
                    'httpResponse': {
                        'body': JSON.stringify({name: 'two'}),
                        'headers': [
                            {"name": "x-test", "values": ["test-value"]}
                        ]
                    }
                },
                {
                    'httpRequest': {
                        'path': '/somePathThree'
                    },
                    'httpResponse': {
                        'body': JSON.stringify({name: 'three'})
                    }
                }
            ]).then(function () {

                // then - matching first request
                sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                    .then(function (response) {
                        test.equal(response.statusCode, 200);
                        test.equal(response.body, '{"name":"one"}');
                        test.equal(response.headers["x-test-default"], "default-value");

                        // then - matching second request
                        sendRequest("GET", "localhost", mockServerPort, "/somePathTwo", "someBody")
                            .then(function (response) {
                                test.equal(response.statusCode, 200);
                                test.equal(response.body, '{"name":"two"}');
                                test.equal(response.headers["x-test-default"], "default-value");
                                test.equal(response.headers["x-test"], "test-value");

                                // then - matching third request
                                sendRequest("GET", "localhost", mockServerPort, "/somePathThree", "someBody")
                                    .then(function (response) {
                                        test.equal(response.statusCode, 200);
                                        test.equal(response.body, '{"name":"three"}');
                                        test.equal(response.headers["x-test-default"], "default-value");
                                        test.done();
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    });
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should clear default headers': function (test) {
            // when
            client.mockAnyResponse({
                'httpRequest': {
                    'path': '/somePathOne'
                },
                'httpResponse': {
                    'body': JSON.stringify({name: 'one'})
                }
            }).then(function () {

                // then - matching request
                sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                    .then(function (response) {
                        test.equal(response.statusCode, 200);
                        test.equal(response.body, '{"name":"one"}');

                        client.setDefaultHeaders([], []);
                        client.mockAnyResponse({
                            'httpRequest': {
                                'path': '/somePathTwo'
                            },
                            'httpResponse': {
                                'body': JSON.stringify({name: 'one'})
                            }
                        }).then(function () {

                            // then - matching request
                            sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                .then(function (response) {
                                    test.equal(response.statusCode, 200);
                                    test.equal(response.body, '{"name":"one"}');
                                    test.ok(!(response.headers["Content-Type"] || response.headers["content-type"]));
                                    test.ok(!(response.headers["Cache-Control"] || response.headers["cache-control"]));

                                    test.done();
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should expose server validation failure': function (test) {
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
            ).then(function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            }, function (error) {
                test.equal(error, "incorrect expectation json format for:\n" +
                    "\n" +
                    "  {\n" +
                    "    \"httpRequest\" : {\n" +
                    "      \"paths\" : \"/somePath\",\n" +
                    "      \"body\" : {\n" +
                    "        \"type\" : \"STRING\",\n" +
                    "        \"vaue\" : \"someBody\"\n" +
                    "      }\n" +
                    "    },\n" +
                    "    \"httpResponse\" : { }\n" +
                    "  }\n" +
                    "\n" +
                    " schema validation errors:\n" +
                    "\n" +
                    "  2 errors:\n" +
                    "   - $.httpRequest.paths: is not defined in the schema and the schema does not allow additional properties\n" +
                    "   - $.httpRequest.specUrlOrPayload: is missing, but is required, if specifying OpenAPI request matcher\n" +
                    "  \n" +
                    "  OpenAPI Specification: https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi/5.15.x\n" +
                    "  Documentation: https://mock-server.com/mock_server/creating_expectations.html");
                test.done();
            });
        },

        'should match on method only': function (test) {
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
                    sendRequest("PUT", "localhost", mockServerPort, "/somePath")
                        .then(function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        }, function (error) {
                            test.equal(error, "404 Not Found");
                        })
                        .then(function () {

                            // then - matching first expectation
                            sendRequest("GET", "localhost", mockServerPort, "/somePath")
                                .then(function (response) {
                                    test.equal(response.statusCode, 200);
                                    test.equal(response.body, '{"name":"first_body"}');
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                })
                                .then(function () {

                                    // then - request that matches second expectation
                                    sendRequest("POST", "localhost", mockServerPort, "/somePath")
                                        .then(function (response) {
                                            test.equal(response.statusCode, 200);
                                            test.equal(response.body, '{"name":"second_body"}');
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        })
                                        .then(function () {
                                            test.done();
                                        });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should match on path only': function (test) {
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
                    sendRequest("GET", "localhost", mockServerPort, "/otherPath")
                        .then(function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        }, function (error) {
                            test.equal(error, "404 Not Found");
                        })
                        .then(function () {

                            // then - matching first expectation
                            sendRequest("GET", "localhost", mockServerPort, "/firstPath")
                                .then(function (response) {
                                    test.equal(response.statusCode, 200);
                                    test.equal(response.body, '{"name":"first_body"}');
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                })
                                .then(function () {

                                    // then - request that matches second expectation
                                    sendRequest("GET", "localhost", mockServerPort, "/secondPath")
                                        .then(function (response) {
                                            test.equal(response.statusCode, 200);
                                            test.equal(response.body, '{"name":"second_body"}');
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        })
                                        .then(function () {
                                            test.done();
                                        });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should match on query string parameters only': function (test) {
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
                    sendRequest("GET", "localhost", mockServerPort, "/somePath?param=other")
                        .then(function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        }, function (error) {
                            test.equal(error, "404 Not Found");
                        })
                        .then(function () {

                            // then - matching first expectation
                            sendRequest("GET", "localhost", mockServerPort, "/somePath?param=first")
                                .then(function (response) {
                                    test.equal(response.statusCode, 200);
                                    test.equal(response.body, '{"name":"first_body"}');
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                })
                                .then(function () {

                                    // then - request that matches second expectation
                                    sendRequest("GET", "localhost", mockServerPort, "/somePath?param=second")
                                        .then(function (response) {
                                            test.equal(response.statusCode, 200);
                                            test.equal(response.body, '{"name":"second_body"}');
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        })
                                        .then(function () {
                                            test.done();
                                        });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should match on body only': function (test) {
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
                    sendRequest("POST", "localhost", mockServerPort, "/otherPath", "someIncorrectBody")
                        .then(function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        }, function (error) {
                            test.equal(error, "404 Not Found");
                        })
                        .then(function () {

                            // then - matching first expectation
                            sendRequest("POST", "localhost", mockServerPort, "/somePath", "someBody")
                                .then(function (response) {
                                    test.equal(response.statusCode, 200);
                                    test.equal(response.body, '{"name":"first_body"}');
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                })
                                .then(function () {

                                    // then - request that matches second expectation
                                    sendRequest("POST", "localhost", mockServerPort, "/somePath", "someOtherBody")
                                        .then(function (response) {
                                            test.equal(response.statusCode, 200);
                                            test.equal(response.body, '{"name":"second_body"}');
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        })
                                        .then(function () {
                                            test.done();
                                        });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should match on headers only': function (test) {
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
                    sendRequest("GET", "localhost", mockServerPort, "/somePath", "", {'Allow': 'other'})
                        .then(function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        }, function (error) {
                            test.equal(error, "404 Not Found");
                        })
                        .then(function () {

                            // then - matching first expectation
                            sendRequest("GET", "localhost", mockServerPort, "/somePath", "", {'Allow': 'first'})
                                .then(function (response) {
                                    test.equal(response.statusCode, 200);
                                    test.equal(response.body, '{"name":"first_body"}');
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                })
                                .then(function () {

                                    // then - request that matches second expectation
                                    sendRequest("GET", "localhost", mockServerPort, "/somePath", "", {'Allow': 'second'})
                                        .then(function (response) {
                                            test.equal(response.statusCode, 200);
                                            test.equal(response.body, '{"name":"second_body"}');
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        })
                                        .then(function () {
                                            test.done();
                                        });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should support headers object format in matcher': function (test) {
            // when
            client.setDefaultHeaders([
                {"name": "x-test-default", "values": ["default-value"]}
            ]);
            client.mockAnyResponse(
                {
                    'httpRequest': {
                        'path': '/somePath'
                    },
                    'httpResponse': {
                        'statusCode': 200,
                        'body': JSON.stringify({name: 'first_body'}),
                        'headers': {
                            'x-test': ['test-value']
                        },
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
                // then - matching first expectation
                sendRequest("GET", "localhost", mockServerPort, "/somePath")
                    .then(function (response) {
                        test.equal(response.statusCode, 200);
                        test.equal(response.body, '{"name":"first_body"}');
                        test.equal(response.headers["x-test-default"], "default-value");
                        test.equal(response.headers["x-test"], "test-value");
                        test.done();
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should support headers object format in default headers': function (test) {
            // when
            client.setDefaultHeaders({
                "x-test-default": ["default-value"]
            });
            client.mockAnyResponse(
                {
                    'httpRequest': {
                        'path': '/somePath'
                    },
                    'httpResponse': {
                        'statusCode': 200,
                        'body': JSON.stringify({name: 'first_body'}),
                        'headers': [
                            {
                                'name': 'x-test',
                                'values': ['test-value']
                            }
                        ],
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
                // then - matching first expectation
                sendRequest("GET", "localhost", mockServerPort, "/somePath", "", {'Allow': 'first'})
                    .then(function (response) {
                        test.equal(response.statusCode, 200);
                        test.equal(response.body, '{"name":"first_body"}');
                        test.equal(response.headers["x-test-default"], "default-value");
                        test.equal(response.headers["x-test"], "test-value");
                        test.done();
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should support headers object format in default headers and matcher': function (test) {
            // when
            client.setDefaultHeaders([
                {"name": "x-test-default", "values": ["default-value"]}
            ]);
            client.mockAnyResponse(
                {
                    'httpRequest': {
                        'path': '/somePath'
                    },
                    'httpResponse': {
                        'statusCode': 200,
                        'body': JSON.stringify({name: 'first_body'}),
                        'headers': {
                            'x-test': ['test-value']
                        },
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
                // then - matching first expectation
                sendRequest("GET", "localhost", mockServerPort, "/somePath", "", {'Allow': 'first'})
                    .then(function (response) {
                        test.equal(response.statusCode, 200);
                        test.equal(response.body, '{"name":"first_body"}');
                        test.equal(response.headers["x-test-default"], "default-value");
                        test.equal(response.headers["x-test"], "test-value");
                        test.done();
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should match on cookies only': function (test) {
            // when
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
            ).then(function () {
                // and - another expectation
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
                ).then(function () {
                    // then - matching no expectation
                    sendRequest("GET", "localhost", mockServerPort, "/somePath", "", {'Cookie': 'cookie=other'})
                        .then(function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        }, function (error) {
                            test.equal(error, "404 Not Found");
                        })
                        .then(function () {

                            // then - matching first expectation
                            sendRequest("GET", "localhost", mockServerPort, "/somePath", "", {'Cookie': 'cookie=first'})
                                .then(function (response) {
                                    test.equal(response.statusCode, 200);
                                    test.equal(response.body, '{"name":"first_body"}');
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                })
                                .then(function () {

                                    // then - request that matches second expectation
                                    sendRequest("GET", "localhost", mockServerPort, "/somePath", "", {'Cookie': 'cookie=second'})
                                        .then(function (response) {
                                            test.equal(response.statusCode, 200);
                                            test.equal(response.body, '{"name":"second_body"}');
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        })
                                        .then(function () {
                                            test.done();
                                        });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should create simple response expectation': function (test) {
            // when
            client.mockSimpleResponse('/somePath', {name: 'value'}, 203)
                .then(function () {
                    // then - non matching request
                    sendRequest("POST", "localhost", mockServerPort, "/otherPath")
                        .then(function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        }, function (error) {
                            test.equal(error, "404 Not Found");
                        })
                        .then(function () {

                            // then - matching request
                            sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody")
                                .then(function (response) {
                                    test.equal(response.statusCode, 203);
                                    test.equal(response.body, '{"name":"value"}');
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                })
                                .then(function () {

                                    // then - matching request, but no times remaining
                                    sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody")
                                        .then(function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        }, function (error) {
                                            test.equal(error, "404 Not Found");
                                        })
                                        .then(function () {
                                            test.done();
                                        });
                                });
                        });
                });
        },

        'should create expectation with open api request': function (test) {
            // when
            var mockAnyResponse = client.mockAnyResponse(
                {
                    'httpRequest': {
                        'specUrlOrPayload': 'https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json',
                        'operationId': 'listPets'
                    },
                    'httpResponse': {
                        'statusCode': 200,
                        'body': "open_api_response"
                    }
                }
            );
            mockAnyResponse.then(function () {
                // then - non matching request
                sendRequest("GET", "localhost", mockServerPort, "/otherPath")
                    .then(function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    }, function (error) {
                        test.equal(error, "404 Not Found");
                    })
                    .then(function () {

                        // then - matching request
                        sendRequest("GET", "localhost", mockServerPort, "/v1/pets?limit=10")
                            .then(function (response) {
                                test.equal(response.statusCode, 200);
                                test.equal(response.body, 'open_api_response');
                                test.done();
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            });
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should create open api expectation': function (test) {
            // when
            var mockAnyResponse = client.openAPIExpectation({
                'specUrlOrPayload': 'https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json',
                'operationsAndResponses': {
                    'showPetById': '200',
                    'listPets': '200'
                }
            });
            mockAnyResponse.then(function () {

                // then - non matching request
                sendRequest("GET", "localhost", mockServerPort, "/otherPath")
                    .then(function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    }, function (error) {
                        test.equal(error, "404 Not Found");
                    })
                    .then(function () {

                        // then - matching request
                        sendRequest("GET", "localhost", mockServerPort, "/v1/pets?limit=10")
                            .then(function (response) {
                                test.equal(response.statusCode, 200);
                                test.equal(response.body, '[ {\n' +
                                    '  "id" : 0,\n' +
                                    '  "name" : "some_string_value",\n' +
                                    '  "tag" : "some_string_value"\n' +
                                    '} ]');
                                test.done();
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            });
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should create expectation with method callback': function (test) {
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
            }, 1, 10, {
                timeUnit: "HOURS",
                timeToLive: 1
            }, "some_id")
                .then(function () {

                    // then - non matching request
                    sendRequest("POST", "localhost", mockServerPort, "/someOtherPath?test=true", "", {'Vary': uuid})
                        .then(function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        }, function (error) {
                            test.equal(error, "404 Not Found");
                        })
                        .then(function () {

                            // then - matching request
                            sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody", {'Vary': uuid})
                                .then(function (response) {

                                    test.equal(response.statusCode, 200);
                                    test.equal(response.body, '{"name":"value"}');

                                    // then - matching request, but no times remaining
                                    sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody", {'Vary': uuid})
                                        .then(function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        }, function (error) {
                                            test.equal(error, "404 Not Found");
                                            test.done();
                                        });
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
        },

        'should create expectation with method callback over tls': function (test) {
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
            })
                .then(function () {

                    // then - non matching request
                    sendRequest("POST", "localhost", mockServerPort, "/someOtherPath?test=true", "", {'Vary': uuid})
                        .then(function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        }, function (error) {
                            test.equal(error, "404 Not Found");
                        })
                        .then(function () {

                            // then - matching request
                            sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody", {'Vary': uuid})
                                .then(function (response) {

                                    test.equal(response.statusCode, 200);
                                    test.equal(response.body, '{"name":"value"}');

                                    // then - matching request, but no times remaining
                                    sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody", {'Vary': uuid})
                                        .then(function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        }, function (error) {
                                            test.equal(error, "404 Not Found");
                                            test.done();
                                        });
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
        },

        'should create multiple parallel expectations with method callback': function (test) {
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
                            sendRequest("GET", "localhost", mockServerPort, "/one", "", {'Vary': uuid})
                                .then(function (response) {

                                    test.equal(response.statusCode, 201);
                                    test.equal(response.body, 'one');

                                    // then - second matching request
                                    sendRequest("GET", "localhost", mockServerPort, "/two", "someBody", {'Vary': uuid})
                                        .then(function (response) {

                                            test.equal(response.statusCode, 202);
                                            test.equal(response.body, 'two');

                                            // then - first matching request again
                                            sendRequest("GET", "localhost", mockServerPort, "/one", "", {'Vary': uuid})
                                                .then(function (response) {

                                                    test.equal(response.statusCode, 201);
                                                    test.equal(response.body, 'one');

                                                    test.done();
                                                }, function (error) {
                                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                    test.done();
                                                });
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        });
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
        },

        'should create expectation with method callback with numeric times': function (test) {
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
                sendRequest("GET", "localhost", mockServerPort, "/one", "", {'Vary': uuid})
                    .then(function (response) {
                        test.equal(response.statusCode, 201);
                        test.equal(response.body, 'one');

                        // then - first matching request again
                        sendRequest("GET", "localhost", mockServerPort, "/one", "", {'Vary': uuid})
                            .then(function (response) {
                                test.equal(response.statusCode, 201);
                                test.equal(response.body, 'one');

                                // then - should no match request again
                                sendRequest("GET", "localhost", mockServerPort, "/one", "", {'Vary': uuid})
                                    .then(function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    }, function (error) {
                                        test.equal(error, "404 Not Found");
                                        test.done();
                                    });
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should update default headers for simple response expectation': function (test) {
            // when
            client.setDefaultHeaders([
                {"name": "content-type", "values": ["application/json; charset=utf-8"]},
                {"name": "x-test", "values": ["test-value"]}
            ]);
            // and - an expectation
            client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
                // then - matching request
                sendRequest("POST", "localhost", mockServerPort, "/somePath?test=true", "someBody")
                    .then(function (response) {
                        test.equal(response.statusCode, 203);
                        test.equal(response.body, '{"name":"value"}');
                        test.equal(response.headers["content-type"], "application/json; charset=utf-8");
                        test.equal(response.headers["x-test"], "test-value");
                        test.done();
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should verify exact number of requests have been sent': function (test) {
            // given
            client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
                // and - a request
                sendRequest("POST", "localhost", mockServerPort, "/somePath", "someBody")
                    .then(function (response) {
                        test.equal(response.statusCode, 203);
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    })
                    .then(function () {

                        // when - verify at least one request
                        client.verify(
                            {
                                'method': 'POST',
                                'path': '/somePath',
                                'body': 'someBody'
                            }, 1, 1).then(function () {
                            test.done();
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        });
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should verify at least a number of requests have been sent': function (test) {
            // given
            client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
                    // and - a request
                    sendRequest("POST", "localhost", mockServerPort, "/somePath", "someBody")
                        .then(function (response) {
                            test.equal(response.statusCode, 203);
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        })
                        .then(function () {
                            // and - another request
                            sendRequest("POST", "localhost", mockServerPort, "/somePath", "someBody")
                                .then(function (response) {
                                    test.equal(response.statusCode, 203);
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                })
                                .then(function () {

                                    // when - verify at least one request
                                    client.verify(
                                        {
                                            'method': 'POST',
                                            'path': '/somePath',
                                            'body': 'someBody'
                                        }, 1).then(function () {
                                        test.done();
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should fail when no requests have been sent': function (test) {
            // given
            client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
                // and - a request
                sendRequest("POST", "localhost", mockServerPort, "/somePath", "someBody")
                    .then(function (response) {
                        test.equal(response.statusCode, 203);
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    })
                    .then(function () {

                        // when - verify at least one request (should fail)
                        client.verify({
                            'path': '/someOtherPath'
                        }, 1)
                            .then(function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            }, function (message) {
                                test.ok(message.startsWith("Request not found at least once, expected:<{\n" +
                                    "  \"path\" : \"/someOtherPath\"\n" +
                                    "}> but was:<{\n"), message);
                                test.done();
                            });
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should fail when not enough exact requests have been sent': function (test) {
            // given
            client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
                // and - a request
                sendRequest("POST", "localhost", mockServerPort, "/somePath", "someBody")
                    .then(function (response) {
                        test.equal(response.statusCode, 203);
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    })
                    .then(function () {

                        // when - verify exact two requests (should fail)
                        client.verify(
                            {
                                'method': 'POST',
                                'path': '/somePath',
                                'body': 'someBody'
                            }, 2, 3)
                            .then(function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            }, function (message) {
                                test.ok(message.startsWith("Request not found between 2 and 3 times, expected:<{\n" +
                                    "  \"body\" : \"someBody\",\n" +
                                    "  \"method\" : \"POST\",\n" +
                                    "  \"path\" : \"/somePath\"\n" +
                                    "}> but was:<{\n"), message);
                                test.done();
                            });
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should fail when not enough at least requests have been sent': function (test) {
            // given
            client.mockSimpleResponse('/somePath', {name: 'value'}, 203).then(function () {
                // and - a request
                sendRequest("POST", "localhost", mockServerPort, "/somePath", "someBody")
                    .then(function (response) {
                        test.equal(response.statusCode, 203);
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    })
                    .then(function () {

                        // when - verify at least two requests (should fail)
                        client.verify(
                            {
                                'method': 'POST',
                                'path': '/somePath',
                                'body': 'someBody'
                            }, 2)
                            .then(function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            }, function (message) {
                                test.ok(message.startsWith("Request not found at least 2 times, expected:<{\n" +
                                    "  \"body\" : \"someBody\",\n" +
                                    "  \"method\" : \"POST\",\n" +
                                    "  \"path\" : \"/somePath\"\n" +
                                    "}> but was:<{\n"), message);
                                test.done();
                            });
                    });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should pass when correct sequence of requests have been sent': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                    // and - a request
                    sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                        .then(function (response) {
                            test.equal(response.statusCode, 201);
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        })
                        .then(function () {

                            sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                .then(function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                }, function (error) {
                                    test.equal(error, "404 Not Found");
                                })
                                .then(function () {

                                    sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                        .then(function (response) {
                                            test.equal(response.statusCode, 202);
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        })
                                        .then(function () {

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
                                                test.done();
                                            }, function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            });
                                        });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should fail when incorrect sequence (wrong order) of requests have been sent': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                    // and - a request
                    sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                        .then(function (response) {
                            test.equal(response.statusCode, 201);
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        })
                        .then(function () {

                            sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                .then(function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                }, function (error) {
                                    test.equal(error, "404 Not Found");
                                })
                                .then(function () {

                                    sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                        .then(function (response) {
                                            test.equal(response.statusCode, 202);
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        })
                                        .then(function () {

                                            // when - wrong order
                                            client.verifySequence(
                                                {
                                                    'body': 'someBody',
                                                    'method': 'POST',
                                                    'path': '/somePathOne'
                                                },
                                                {
                                                    'method': 'GET',
                                                    'path': '/somePathTwo'
                                                },
                                                {
                                                    'method': 'GET',
                                                    'path': '/notFound'
                                                }
                                            )
                                                .then(function (error) {
                                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                    test.done();
                                                }, function (message) {
                                                    test.ok(message.startsWith("Request sequence not found, expected:<[ {\n" +
                                                        "  \"body\" : \"someBody\",\n" +
                                                        "  \"method\" : \"POST\",\n" +
                                                        "  \"path\" : \"/somePathOne\"\n" +
                                                        "}, {\n" +
                                                        "  \"method\" : \"GET\",\n" +
                                                        "  \"path\" : \"/somePathTwo\"\n" +
                                                        "}, {\n" +
                                                        "  \"method\" : \"GET\",\n" +
                                                        "  \"path\" : \"/notFound\"\n" +
                                                        "} ]> but was:<[ {\n"), message);
                                                    test.done();
                                                });

                                        });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should fail when incorrect sequence (first request incorrect body) of requests have been sent': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                    // and - a request
                    sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                        .then(function (response) {
                            test.equal(response.statusCode, 201);
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        })
                        .then(function () {

                            sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                .then(function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                }, function (error) {
                                    test.equal(error, "404 Not Found");
                                })
                                .then(function () {

                                    sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                        .then(function (response) {
                                            test.equal(response.statusCode, 202);
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        })
                                        .then(function () {

                                            // when - first request incorrect body
                                            client.verifySequence(
                                                {
                                                    'body': 'some_incorrect_body',
                                                    'method': 'POST',
                                                    'path': '/somePathOne'
                                                },
                                                {
                                                    'method': 'GET',
                                                    'path': '/notFound'
                                                },
                                                {
                                                    'method': 'GET',
                                                    'path': '/somePathTwo'
                                                }
                                            ).then(function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            }, function (message) {
                                                test.ok(message.startsWith("Request sequence not found, expected:<[ {\n" +
                                                    "  \"body\" : \"some_incorrect_body\",\n" +
                                                    "  \"method\" : \"POST\",\n" +
                                                    "  \"path\" : \"/somePathOne\"\n" +
                                                    "}, {\n" +
                                                    "  \"method\" : \"GET\",\n" +
                                                    "  \"path\" : \"/notFound\"\n" +
                                                    "}, {\n" +
                                                    "  \"method\" : \"GET\",\n" +
                                                    "  \"path\" : \"/somePathTwo\"\n" +
                                                    "} ]> but was:<[ {\n"), message);
                                                test.done();
                                            });
                                        });
                                });
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should clear expectations and logs by path': function (test) {
            // when
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                    // and - another expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                        // and - a matching request (that returns 200)
                        sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                            .then(function (response) {
                                test.equal(response.statusCode, 200);
                                test.equal(response.body, '{"name":"value"}');
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                // when - some expectations cleared
                                client.clear('/somePathOne').then(function () {

                                    // then - request matching cleared expectation should return 404
                                    sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                        .then(function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        }, function (error) {
                                            test.strictEqual(error, "404 Not Found");
                                        })
                                        .then(function () {

                                            // and - request matching non-cleared expectation should return 200
                                            sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                .then(function (response) {
                                                    test.equal(response.statusCode, 200);
                                                    test.equal(response.body, '{"name":"value"}');
                                                    // then - return no logs for clear requests
                                                    client.clear('/somePathOne').then(function () {
                                                        client.retrieveRecordedRequests({
                                                            "path": "/somePathOne"
                                                        })
                                                            .then(function (requests) {
                                                                test.equal(requests.length, 0);
                                                                // then - return logs for not cleared requests
                                                                client.retrieveRecordedRequests({
                                                                    "httpRequest": {
                                                                        "path": "/somePathTwo"
                                                                    }
                                                                })
                                                                    .then(function (requests) {
                                                                        test.equal(requests.length, 1);
                                                                        test.equal(requests[0].path, '/somePathTwo');

                                                                        test.done();
                                                                    }, function (error) {
                                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                                        test.done();
                                                                    });
                                                            }, function (error) {
                                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                                test.done();
                                                            });
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    });
                                                }, function (error) {
                                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                    test.done();
                                                });
                                        });
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should clear expectations by request matcher': function (test) {
            // when
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                    // and - another expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                        // and - a matching request (that returns 200)
                        sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                            .then(function (response) {
                                test.equal(response.statusCode, 200);
                                test.equal(response.body, '{"name":"value"}');
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                // when - some expectations cleared
                                client.clear({
                                    "path": "/somePathOne"
                                })
                                    .then(function () {

                                        // then - request matching cleared expectation should return 404
                                        sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                            .then(function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            }, function (error) {
                                                test.strictEqual(error, "404 Not Found");
                                            })
                                            .then(function () {

                                                // and - request matching non-cleared expectation should return 200
                                                sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                    .then(function (response) {
                                                        test.equal(response.statusCode, 200);
                                                        test.equal(response.body, '{"name":"value"}');
                                                        test.done();
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    });
                                            });
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should clear expectations by expectation matcher': function (test) {
            // when
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                    // and - another expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                        // and - a matching request (that returns 200)
                        sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                            .then(function (response) {
                                test.equal(response.statusCode, 200);
                                test.equal(response.body, '{"name":"value"}');
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                // when - some expectations cleared
                                client.clear({
                                    "httpRequest": {
                                        "path": "/somePathOne"
                                    }
                                })
                                    .then(function () {

                                        // then - request matching cleared expectation should return 404
                                        sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                            .then(function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            }, function (error) {
                                                test.strictEqual(error, "404 Not Found");
                                            })
                                            .then(function () {

                                                // and - request matching non-cleared expectation should return 200
                                                sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                    .then(function (response) {
                                                        test.equal(response.statusCode, 200);
                                                        test.equal(response.body, '{"name":"value"}');
                                                        test.done();
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    });
                                            });
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should clear only logs by path': function (test) {
            // when
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                    // and - another expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                        // and - a matching request (that returns 200)
                        sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                            .then(function (response) {
                                test.equal(response.statusCode, 200);
                                test.equal(response.body, '{"name":"value"}');
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                // when - some expectations cleared
                                client.clear('/somePathOne', 'EXPECTATIONS').then(function () {

                                    // then - request matching cleared expectation should return 404
                                    sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                        .then(function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        }, function (error) {
                                            test.strictEqual(error, "404 Not Found");
                                        })
                                        .then(function () {

                                            // and - request matching non-cleared expectation should return 200
                                            sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                .then(function (response) {
                                                    test.equal(response.statusCode, 200);
                                                    test.equal(response.body, '{"name":"value"}');

                                                    // then - return no logs for clear requests
                                                    client.clear('/somePathOne', 'LOG').then(function () {
                                                        client.retrieveRecordedRequests({
                                                            "path": "/somePathOne"
                                                        })
                                                            .then(function (requests) {
                                                                test.strictEqual(requests.length, 0);
                                                                test.done();
                                                            }, function (error) {
                                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                                test.done();
                                                            });
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    });
                                                }, function (error) {
                                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                    test.done();
                                                });
                                        });
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should clear only expectation by path': function (test) {
            // when
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                    // and - another expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                        // and - a matching request (that returns 200)
                        sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                            .then(function (response) {
                                test.strictEqual(response.statusCode, 200);
                                test.strictEqual(response.body, '{"name":"value"}');
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                // when - some expectations cleared
                                client.clear('/somePathOne', 'EXPECTATIONS').then(function () {

                                    // then - request matching cleared expectation should return 404
                                    sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                        .then(function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        }, function (error) {
                                            test.strictEqual(error, "404 Not Found");
                                        })
                                        .then(function () {

                                            // and - request matching non-cleared expectation should return 200
                                            sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                .then(function (response) {
                                                    test.strictEqual(response.statusCode, 200);
                                                    test.strictEqual(response.body, '{"name":"value"}');
                                                    // then - return no logs for clear requests
                                                    client.clear('/somePathOne', 'LOG').then(function () {
                                                        client.retrieveRecordedRequests({
                                                            "path": "/somePathOne"
                                                        })
                                                            .then(function (requests) {
                                                                test.strictEqual(requests.length, 0);
                                                                // then - return logs for not cleared requests
                                                                client.retrieveRecordedRequests({
                                                                    "httpRequest": {
                                                                        "path": "/somePathTwo"
                                                                    }
                                                                })
                                                                    .then(function (requests) {
                                                                        test.strictEqual(requests.length, 1);
                                                                        test.strictEqual(requests[0].path, '/somePathTwo');

                                                                        test.done();
                                                                    }, function (error) {
                                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                                        test.done();
                                                                    });
                                                            }, function (error) {
                                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                                test.done();
                                                            });
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    });
                                                }, function (error) {
                                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                    test.done();
                                                });
                                        });
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should reset expectations': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                // and - another expectation
                client.mockSimpleResponse('/somePathOne', {name: 'value'}, 200).then(function () {
                    // and - another expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'value'}, 200).then(function () {
                        // and - a matching request (that returns 200)
                        sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                            .then(function (response) {
                                test.strictEqual(response.statusCode, 200);
                                test.strictEqual(response.body, '{"name":"value"}');
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                // when - all expectations reset
                                client.reset().then(function () {

                                    // then - request matching one reset expectation should return 404
                                    sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                        .then(function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        }, function (error) {
                                            test.strictEqual(error, "404 Not Found");
                                        })
                                        .then(function () {

                                            // then - request matching other reset expectation should return 404
                                            sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                .then(function (error) {
                                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                    test.done();
                                                }, function (error) {
                                                    test.strictEqual(error, "404 Not Found");
                                                    test.done();
                                                });

                                        });
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve some expectations using object matcher': function (test) {
            // when
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {
                        // when
                        client.retrieveActiveExpectations({
                            "httpRequest": {
                                "path": "/somePathOne"
                            }
                        })
                            .then(function (expectations) {
                                // then
                                test.strictEqual(expectations.length, 2);
                                try {
                                    // first expectation
                                    test.strictEqual(expectations[0].httpRequest.path, '/somePathOne');
                                    test.strictEqual(expectations[0].httpResponse.body, '{"name":"one"}');
                                    test.strictEqual(expectations[0].httpResponse.statusCode, 201);
                                    // second expectation
                                    test.strictEqual(expectations[1].httpRequest.path, '/somePathOne');
                                    test.strictEqual(expectations[1].httpResponse.body, '{"name":"one"}');
                                    test.strictEqual(expectations[1].httpResponse.statusCode, 201);
                                } catch (exception) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                }
                                test.done();
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve some expectations using path': function (test) {
            // when
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {

                        // when
                        client.retrieveActiveExpectations("/somePathOne").then(function (expectations) {
                            // then
                            test.strictEqual(expectations.length, 2);
                            try {
                                // first expectation
                                test.strictEqual(expectations[0].httpRequest.path, '/somePathOne');
                                test.strictEqual(expectations[0].httpResponse.body, '{"name":"one"}');
                                test.strictEqual(expectations[0].httpResponse.statusCode, 201);
                                // second expectation
                                test.strictEqual(expectations[1].httpRequest.path, '/somePathOne');
                                test.strictEqual(expectations[1].httpResponse.body, '{"name":"one"}');
                                test.strictEqual(expectations[1].httpResponse.statusCode, 201);
                            } catch (exception) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                            }
                            test.done();
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        });

                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve all expectations using object matcher': function (test) {
            // when
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {

                        // when
                        client.retrieveActiveExpectations({
                            "httpRequest": {
                                "path": "/somePath.*"
                            }
                        })
                            .then(function (expectations) {
                                // then
                                test.strictEqual(expectations.length, 3);
                                try {
                                    // first expectation
                                    test.strictEqual(expectations[0].httpRequest.path, '/somePathOne');
                                    test.strictEqual(expectations[0].httpResponse.body, '{"name":"one"}');
                                    test.strictEqual(expectations[0].httpResponse.statusCode, 201);
                                    // second expectation
                                    test.strictEqual(expectations[1].httpRequest.path, '/somePathOne');
                                    test.strictEqual(expectations[1].httpResponse.body, '{"name":"one"}');
                                    test.strictEqual(expectations[1].httpResponse.statusCode, 201);
                                    // third expectation
                                    test.strictEqual(expectations[2].httpRequest.path, '/somePathTwo');
                                    test.strictEqual(expectations[2].httpResponse.body, '{"name":"two"}');
                                    test.strictEqual(expectations[2].httpResponse.statusCode, 202);
                                } catch (exception) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                }
                                test.done();
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            });

                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });

                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve all expectations using null matcher': function (test) {
            // when
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {

                        // when
                        client.retrieveActiveExpectations().then(function (expectations) {
                            // then
                            test.strictEqual(expectations.length, 3);
                            try {
                                // first expectation
                                test.strictEqual(expectations[0].httpRequest.path, '/somePathOne');
                                test.strictEqual(expectations[0].httpResponse.body, '{"name":"one"}');
                                test.strictEqual(expectations[0].httpResponse.statusCode, 201);
                                // second expectation
                                test.strictEqual(expectations[1].httpRequest.path, '/somePathOne');
                                test.strictEqual(expectations[1].httpResponse.body, '{"name":"one"}');
                                test.strictEqual(expectations[1].httpResponse.statusCode, 201);
                                // third expectation
                                test.strictEqual(expectations[2].httpRequest.path, '/somePathTwo');
                                test.strictEqual(expectations[2].httpResponse.body, '{"name":"two"}');
                                test.strictEqual(expectations[2].httpResponse.statusCode, 202);
                            } catch (exception) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                            }
                            test.done();
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve some requests using object matcher': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {

                        sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                            .then(function (response) {
                                test.strictEqual(response.statusCode, 201);
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                    .then(function (response) {
                                        test.strictEqual(response.statusCode, 201);
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    })
                                    .then(function () {

                                        sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                            .then(function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            }, function (error) {
                                                test.strictEqual(error, "404 Not Found");
                                            })
                                            .then(function () {

                                                sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                    .then(function (response) {
                                                        test.strictEqual(response.statusCode, 202);
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    })
                                                    .then(function () {

                                                        // when
                                                        client.retrieveRecordedRequests({
                                                            "httpRequest": {
                                                                "path": "/somePathOne"
                                                            }
                                                        })
                                                            .then(function (requests) {
                                                                // then
                                                                test.strictEqual(requests.length, 2);
                                                                try {
                                                                    // first request
                                                                    test.strictEqual(requests[0].path, '/somePathOne');
                                                                    test.strictEqual(requests[0].method, 'POST');
                                                                    test.strictEqual(requests[0].body, 'someBody');
                                                                    // second request
                                                                    test.strictEqual(requests[1].path, '/somePathOne');
                                                                    test.strictEqual(requests[1].method, 'GET');
                                                                } catch (exception) {
                                                                    test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                                                }
                                                                test.done();
                                                            }, function (error) {
                                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                                test.done();
                                                            });
                                                    });
                                            });
                                    });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve some requests using path': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {

                        sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                            .then(function (response) {
                                test.strictEqual(response.statusCode, 201);
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                    .then(function (response) {
                                        test.strictEqual(response.statusCode, 201);
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    })
                                    .then(function () {

                                        sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                            .then(function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            }, function (error) {
                                                test.strictEqual(error, "404 Not Found");
                                            })
                                            .then(function () {

                                                sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                    .then(function (response) {
                                                        test.strictEqual(response.statusCode, 202);
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    })
                                                    .then(function () {

                                                        // when
                                                        client.retrieveRecordedRequests("/somePathOne").then(function (requests) {
                                                            // then
                                                            test.strictEqual(requests.length, 2);
                                                            try {
                                                                // first request
                                                                test.strictEqual(requests[0].path, '/somePathOne');
                                                                test.strictEqual(requests[0].method, 'POST');
                                                                test.strictEqual(requests[0].body, 'someBody');
                                                                // second request
                                                                test.strictEqual(requests[1].path, '/somePathOne');
                                                                test.strictEqual(requests[1].method, 'GET');
                                                            } catch (exception) {
                                                                test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                                            }
                                                            test.done();
                                                        }, function (error) {
                                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                            test.done();
                                                        });
                                                    });
                                            });
                                    });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve all requests using object matcher': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {

                        sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                            .then(function (response) {
                                test.strictEqual(response.statusCode, 201);
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                    .then(function (response) {
                                        test.strictEqual(response.statusCode, 201);
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    })
                                    .then(function () {

                                        sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                            .then(function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            }, function (error) {
                                                test.strictEqual(error, "404 Not Found");
                                            })
                                            .then(function () {

                                                sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                    .then(function (response) {
                                                        test.strictEqual(response.statusCode, 202);
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    })
                                                    .then(function () {

                                                        // when
                                                        client.retrieveRecordedRequests({
                                                            "httpRequest": {
                                                                "path": "/.*"
                                                            }
                                                        })
                                                            .then(function (requests) {
                                                                // then
                                                                test.strictEqual(requests.length, 4);
                                                                try {
                                                                    // first request
                                                                    test.strictEqual(requests[0].path, '/somePathOne');
                                                                    test.strictEqual(requests[0].method, 'POST');
                                                                    test.strictEqual(requests[0].body, 'someBody');
                                                                    // second request
                                                                    test.strictEqual(requests[1].path, '/somePathOne');
                                                                    test.strictEqual(requests[1].method, 'GET');
                                                                    // third request
                                                                    test.strictEqual(requests[2].path, '/notFound');
                                                                    test.strictEqual(requests[2].method, 'GET');
                                                                    // fourth request
                                                                    test.strictEqual(requests[3].path, '/somePathTwo');
                                                                    test.strictEqual(requests[3].method, 'GET');
                                                                } catch (exception) {
                                                                    test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                                                }
                                                                test.done();
                                                            }, function (error) {
                                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                                test.done();
                                                            });
                                                    });
                                            });
                                    });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve all requests using path': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {

                        sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                            .then(function (response) {
                                test.strictEqual(response.statusCode, 201);
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                    .then(function (response) {
                                        test.strictEqual(response.statusCode, 201);
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    })
                                    .then(function () {

                                        sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                            .then(function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            }, function (error) {
                                                test.strictEqual(error, "404 Not Found");
                                            })
                                            .then(function () {

                                                sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                    .then(function (response) {
                                                        test.strictEqual(response.statusCode, 202);
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    })
                                                    .then(function () {

                                                        // when
                                                        client.retrieveRecordedRequests("/.*").then(function (requests) {
                                                            // then
                                                            test.strictEqual(requests.length, 4);
                                                            try {
                                                                // first request
                                                                test.strictEqual(requests[0].path, '/somePathOne');
                                                                test.strictEqual(requests[0].method, 'POST');
                                                                test.strictEqual(requests[0].body, 'someBody');
                                                                // second request
                                                                test.strictEqual(requests[1].path, '/somePathOne');
                                                                test.strictEqual(requests[1].method, 'GET');
                                                                // third request
                                                                test.strictEqual(requests[2].path, '/notFound');
                                                                test.strictEqual(requests[2].method, 'GET');
                                                                // fourth request
                                                                test.strictEqual(requests[3].path, '/somePathTwo');
                                                                test.strictEqual(requests[3].method, 'GET');
                                                            } catch (exception) {
                                                                test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                                            }
                                                            test.done();
                                                        }, function (error) {
                                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                            test.done();
                                                        });
                                                    });
                                            });
                                    });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve some requests and responses using object matcher': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {

                        sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                            .then(function (response) {
                                test.strictEqual(response.statusCode, 201);
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                    .then(function (response) {
                                        test.strictEqual(response.statusCode, 201);
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    })
                                    .then(function () {

                                        sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                            .then(function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            }, function (error) {
                                                test.strictEqual(error, "404 Not Found");
                                            })
                                            .then(function () {

                                                sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                    .then(function (response) {
                                                        test.strictEqual(response.statusCode, 202);
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    })
                                                    .then(function () {

                                                        // when
                                                        client.retrieveRecordedRequestsAndResponses({
                                                            "httpRequest": {
                                                                "path": "/somePathOne"
                                                            }
                                                        })
                                                            .then(function (requests) {
                                                                // then
                                                                test.strictEqual(requests.length, 2);
                                                                try {
                                                                    // first request
                                                                    test.strictEqual(requests[0].httpRequest.path, '/somePathOne');
                                                                    test.strictEqual(requests[0].httpRequest.method, 'POST');
                                                                    test.strictEqual(requests[0].httpRequest.body, 'someBody');
                                                                    test.strictEqual(requests[0].httpResponse.statusCode, 201);
                                                                    test.strictEqual(requests[0].httpResponse.body, '{"name":"one"}');
                                                                    // second request
                                                                    test.strictEqual(requests[1].httpRequest.path, '/somePathOne');
                                                                    test.strictEqual(requests[1].httpRequest.method, 'GET');
                                                                    test.strictEqual(requests[1].httpResponse.statusCode, 201);
                                                                    test.strictEqual(requests[1].httpResponse.body, '{"name":"one"}');
                                                                } catch (exception) {
                                                                    test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                                                }
                                                                test.done();
                                                            }, function (error) {
                                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                                test.done();
                                                            });
                                                    });
                                            });
                                    });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve some requests and responses using path': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                // and - second expectation
                client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201).then(function () {
                    // and - third expectation
                    client.mockSimpleResponse('/somePathTwo', {name: 'two'}, 202).then(function () {

                        sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                            .then(function (response) {
                                test.strictEqual(response.statusCode, 201);
                            }, function (error) {
                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                test.done();
                            })
                            .then(function () {

                                sendRequest("GET", "localhost", mockServerPort, "/somePathOne")
                                    .then(function (response) {
                                        test.strictEqual(response.statusCode, 201);
                                    }, function (error) {
                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                        test.done();
                                    })
                                    .then(function () {

                                        sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                            .then(function (error) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                test.done();
                                            }, function (error) {
                                                test.strictEqual(error, "404 Not Found");
                                            })
                                            .then(function () {

                                                sendRequest("GET", "localhost", mockServerPort, "/somePathTwo")
                                                    .then(function (response) {
                                                        test.strictEqual(response.statusCode, 202);
                                                    }, function (error) {
                                                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                        test.done();
                                                    })
                                                    .then(function () {

                                                        // when
                                                        client.retrieveRecordedRequestsAndResponses("/somePathOne").then(function (requests) {
                                                            // then
                                                            test.strictEqual(requests.length, 2);
                                                            try {
                                                                // first request
                                                                test.strictEqual(requests[0].httpRequest.path, '/somePathOne');
                                                                test.strictEqual(requests[0].httpRequest.method, 'POST');
                                                                test.strictEqual(requests[0].httpRequest.body, 'someBody');
                                                                test.strictEqual(requests[0].httpResponse.statusCode, 201);
                                                                test.strictEqual(requests[0].httpResponse.body, '{"name":"one"}');
                                                                // second request
                                                                test.strictEqual(requests[1].httpRequest.path, '/somePathOne');
                                                                test.strictEqual(requests[1].httpRequest.method, 'GET');
                                                                test.strictEqual(requests[1].httpResponse.statusCode, 201);
                                                                test.strictEqual(requests[1].httpResponse.body, '{"name":"one"}');
                                                            } catch (exception) {
                                                                test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                                            }
                                                            test.done();
                                                        }, function (error) {
                                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                                            test.done();
                                                        });
                                                    });
                                            });
                                    });
                            });
                    }, function (error) {
                        test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                        test.done();
                    });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
            }, function (error) {
                test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                test.done();
            });
        },

        'should retrieve some logs using object matcher': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                .then(function () {

                    sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                        .then(function (response) {
                            test.equal(response.statusCode, 201);


                            sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                .then(function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                }, function (error) {
                                    test.equal(error, "404 Not Found");

                                    // when
                                    client
                                        .retrieveLogMessages({
                                            "httpRequest": {
                                                "path": "/somePathOne"
                                            }
                                        })
                                        .then(function (logMessages) {

                                            // then
                                            test.equal(logMessages.length, 6);

                                            try {
                                                test.ok(logMessages[0].indexOf('resetting all expectations and request logs') !== -1, logMessages[0]);
                                                test.ok(logMessages[1].indexOf("creating expectation:\n") !== -1, logMessages[1]);
                                                test.ok(logMessages[2].indexOf("received request:\n" +
                                                    "\n" +
                                                    "  {\n" +
                                                    "    \"method\" : \"POST\",\n" +
                                                    "    \"path\" : \"/somePathOne\",\n") !== -1, logMessages[2]);
                                                test.ok(logMessages[3].indexOf("request:\n" +
                                                    "\n" +
                                                    "  {\n" +
                                                    "    \"method\" : \"POST\",\n" +
                                                    "    \"path\" : \"/somePathOne\",\n") !== -1, logMessages[3]);
                                            } catch (exception) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                            }
                                            test.done();
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        });
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
        },

        'should retrieve some logs using using path': function (test) {
            // given
            client.mockSimpleResponse('/somePathOne', {name: 'one'}, 201)
                .then(function () {

                    sendRequest("POST", "localhost", mockServerPort, "/somePathOne", "someBody")
                        .then(function (response) {
                            test.equal(response.statusCode, 201);


                            sendRequest("GET", "localhost", mockServerPort, "/notFound")
                                .then(function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                }, function (error) {
                                    test.equal(error, "404 Not Found");

                                    // when
                                    client
                                        .retrieveLogMessages("/somePathOne")
                                        .then(function (logMessages) {

                                            // then
                                            test.equal(logMessages.length, 6);

                                            try {
                                                test.ok(logMessages[0].indexOf('resetting all expectations and request logs') !== -1, logMessages[0]);
                                                test.ok(logMessages[1].indexOf("creating expectation:\n") !== -1, logMessages[1]);
                                                test.ok(logMessages[2].indexOf("received request:\n" +
                                                    "\n" +
                                                    "  {\n" +
                                                    "    \"method\" : \"POST\",\n" +
                                                    "    \"path\" : \"/somePathOne\",\n") !== -1, logMessages[2]);
                                                test.ok(logMessages[3].indexOf("request:\n" +
                                                    "\n" +
                                                    "  {\n" +
                                                    "    \"method\" : \"POST\",\n" +
                                                    "    \"path\" : \"/somePathOne\",\n") !== -1, logMessages[3]);
                                            } catch (exception) {
                                                test.ok(false, "failed with the following error \n" + JSON.stringify(exception));
                                            }
                                            test.done();
                                        }, function (error) {
                                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                            test.done();
                                        });
                                }, function (error) {
                                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                                    test.done();
                                });
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
        },

        'should bind to additional port': function (test) {
            // given
            client.bind([mockServerPort + 1])
                .then(function (response) {
                    test.equal(response.statusCode, 200);
                    test.ok(response.body.indexOf("{\n" +
                        "  \"artifactId\" : \"mockserver-core\",\n" +
                        "  \"groupId\" : \"org.mock-server\",\n" +
                        "  \"ports\" : [ " + (mockServerPort + 1) + " ],\n" +
                        "  \"version\" : \"5.15.0\"\n" +
                        "}") !== -1, response.body);
                    sendRequest("PUT", "localhost", mockServerPort + 1, "/status")
                        .then(function (response) {
                            test.equal(response.statusCode, 200);
                            test.ok(response.body.indexOf("{\n" +
                                "  \"artifactId\" : \"mockserver-core\",\n" +
                                "  \"groupId\" : \"org.mock-server\",\n" +
                                "  \"ports\" : [ " + mockServerPort + ", " + (mockServerPort + 1) + " ],\n" +
                                "  \"version\" : \"5.15.0\"\n" +
                                "}") !== -1, response.body);
                            test.done();
                        }, function (error) {
                            test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                            test.done();
                        });
                }, function (error) {
                    test.ok(false, "failed with the following error \n" + JSON.stringify(error));
                    test.done();
                });
        }
    };

})();
#### Basic Expectation

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "GET",
    "path": "/view/cart",
    "queryStringParameters": {
      "cartId": [
        "055CA455-1DF7-45BB-8535-4F83E7266092"
      ]
    },
    "cookies": {
      "session": "4930456C-C718-476F-971F-CB8E047AB349"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Add Array Of Expectations

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '[
  {
    "httpRequest": {
      "path": "/somePathOne"
    },
    "httpResponse": {
      "statusCode": 200,
      "body": {
        "value": "one"
      }
    }
  },
  {
    "httpRequest": {
      "path": "/somePathTwo"
    },
    "httpResponse": {
      "statusCode": 200,
      "body": {
        "value": "one"
      }
    }
  },
  {
    "httpRequest": {
      "path": "/somePathThree"
    },
    "httpResponse": {
      "statusCode": 200,
      "body": {
        "value": "one"
      }
    }
  }
]
```

#### Class Callback

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some.*"
  },
  "httpClassCallback": {
    "callbackClass": "org.mockserver.examples.mockserver.CallbackActionExamples$TestExpectationCallback"
  }
}'
```

#### Verify Requests Exact

```bash
curl -X PUT 'localhost:1080/mockserver/verify' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "times": {
    "atLeast": 2,
    "atMost": 2
  }
}'
```

#### Verify Requests Receive At Least Twice

```bash
curl -X PUT 'localhost:1080/mockserver/verify' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "times": {
    "atLeast": 2
  }
}'
```

#### Verify Requests Receive At Most Twice

```bash
curl -X PUT 'localhost:1080/mockserver/verify' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "times": {
    "atMost": 2
  }
}'
```

#### Verify Requests Receive Exactly Twice

```bash
curl -X PUT 'localhost:1080/mockserver/verify' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "times": {
    "atLeast": 2,
    "atMost": 2
  }
}'
```

#### Verify Requests Receive At Least Twice By OpenAPI

```bash
curl -X PUT 'localhost:1080/mockserver/verify' \
-d '{
  "httpRequest": {
    "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json"
  },
  "times": {
    "atLeast": 2,
    "atMost": 2
  }
}'
```

#### Verify Requests Receive At Least Twice By OpenAPI With Operation

```bash
curl -X PUT 'localhost:1080/mockserver/verify' \
-d '{
  "httpRequest": {
    "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
    "operationId": "showPetById"
  },
  "times": {
    "atLeast": 2,
    "atMost": 2
  }
}'
```

#### Verify Request Sequence

```bash
curl -X PUT 'localhost:1080/mockserver/verifySequence' \
-d '[
  {
    "path": "/some/path/one"
  },
  {
    "path": "/some/path/two"
  },
  {
    "path": "/some/path/three"
  }
]
```

#### Verify Request Sequence Using Open API

```bash
curl -X PUT 'localhost:1080/mockserver/verifySequence' \
-d '[
  {
    "path": "/status"
  },
  {
    "specUrlOrPayload": "org/mockserver/mock/openapi_petstore_example.json",
    "operationId": "listPets"
  },
  {
    "specUrlOrPayload": "org/mockserver/mock/openapi_petstore_example.json",
    "operationId": "showPetById"
  }
]
```
#### Verify Request Sequence Using Open API

```bash
curl -X PUT 'localhost:1080/mockserver/verifySequence' \
-d '[
  {
    "id": "31e4ca35-66c6-4645-afeb-6e66c4ca0559"
  },
  {
    "id": "66c6ca35-ca35-66f5-8feb-5e6ac7ca0559"
  },
  {
    "id": "ca3531e4-23c8-ff45-88f5-4ca0c7ca0559"
  }
]
```

#### Retrieve Recorded Requests Filtered By Request Matcher

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=REQUESTS&format=JSON"
-d '{
    "path": "/some/path",
    "method": "POST"
}'
```

#### Retrieve Recorded Log Messages Filtered By Request Matcher

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=LOGS" \
-d '{
    "path": "/some/path",
    "method": "POST"
}'
```

#### Clear With Request Properties Matcher


```bash
curl -X PUT "http://localhost:1080/mockserver/clear" -d '{
    "path": "/some/path"
}'
```

#### Clear With Open API Request Matcher

```bash
curl -X PUT "http://localhost:1080/mockserver/clear" -d '{
    "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
    "operationId": "showPetById"
}'
```

#### Clear With Expectation Id

```bash
curl -X PUT "http://localhost:1080/mockserver/clear" -d '{
    "id": "31e4ca35-66c6-4645-afeb-6e66c4ca0559"
}'
```

#### Clear Requests And Logs With Request Properties Matcher

```bash
curl -X PUT "http://localhost:1080/mockserver/clear?type=LOGS" -d '{
    "path": "/some/path"
}'
```

#### Clear Requests And Logs With Open API Request Properties Matcher

```bash
curl -X PUT "http://localhost:1080/mockserver/clear?type=LOGS" -d '{
    "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
    "operationId": "showPetById"
}'
```

#### Reset

```bash
curl -X PUT "http://localhost:1080/mockserver/reset
```

#### Random Bytes Error

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpError": {
    "dropConnection": true,
    "responseBytes": "eQqmdjEEoaXnCvcK6lOAIZeU+Pn+womxmg=="
  }
}'
```

#### Drop Connection Error

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpError": {
    "dropConnection": true
  }
}'
```

#### Forward Request In HTTP

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpForward": {
    "host": "mock-server.com",
    "port": 80,
    "scheme": "HTTP"
  }
}'
```

#### Forward Request In HTTPS

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpForward": {
    "host": "mock-server.com",
    "port": 443,
    "scheme": "HTTPS"
  }
}'
```

#### Forward Overridden Request

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpOverrideForwardedRequest": {
    "httpRequest": {
      "path": "/some/other/path",
      "headers": {
        "Host": [
          "target.host.com"
        ]
      }
    }
  }
}'
```

#### Forward Overridden Request And Response

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpOverrideForwardedRequest": {
    "httpRequest": {
      "path": "/some/other/path",
      "headers": {
        "Host": [
          "target.host.com"
        ]
      }
    },
    "httpResponse": {
      "body": "some_overridden_body"
    }
  }
}'
```

#### Forward Overridden And Modified Request

```bash
curl -X PUT "http://localhost:1080/mockserver/expectation" -d '{
    "httpRequest": {
        "path": "/some/path"
    },
    "httpOverrideForwardedRequest": {
        "requestOverride": {
            "headers": {
                "Host": [
                    "target.host.com"
                ]
            }, 
            "body": "some_overridden_body"
        }, 
        "requestModifier": {
            "cookies": {
                "add": {
                    "cookieToAddOne": "addedValue", 
                    "cookieToAddTwo": "addedValue"
                }, 
                "remove": [
                    "overrideCookieToRemove", 
                    "requestCookieToRemove"
                ], 
                "replace": {
                    "overrideCookieToReplace": "replacedValue", 
                    "requestCookieToReplace": "replacedValue", 
                    "extraCookieToReplace": "shouldBeIgnore"
                }
            }, 
            "headers": {
                "add": {
                    "headerToAddTwo": [
                        "addedValue"
                    ], 
                    "headerToAddOne": [
                        "addedValue"
                    ]
                }, 
                "remove": [
                    "overrideHeaderToRemove", 
                    "requestHeaderToRemove"
                ], 
                "replace": {
                    "requestHeaderToReplace": [
                        "replacedValue"
                    ], 
                    "overrideHeaderToReplace": [
                        "replacedValue"
                    ], 
                    "extraHeaderToReplace": [
                        "shouldBeIgnore"
                    ]
                }
            }, 
            "path": {
                "regex": "^/(.+)/(.+)$", 
                "substitution": "/prefix/$1/infix/$2/postfix"
            }, 
            "queryStringParameters": {
                "add": {
                    "parameterToAddTwo": [
                        "addedValue"
                    ], 
                    "parameterToAddOne": [
                        "addedValue"
                    ]
                }, 
                "remove": [
                    "overrideParameterToRemove", 
                    "requestParameterToRemove"
                ], 
                "replace": {
                    "requestParameterToReplace": [
                        "replacedValue"
                    ], 
                    "overrideParameterToReplace": [
                        "replacedValue"
                    ], 
                    "extraParameterToReplace": [
                        "shouldBeIgnore"
                    ]
                }
            }
        }
    }
}'
```

#### Forward Overridden And Modified Request And Response

```bash
curl -X PUT "http://localhost:1080/mockserver/expectation" -d '{
    "httpRequest": {
        "path": "/some/path"
    },
    "httpOverrideForwardedRequest": {
        "requestOverride": {
            "headers": {
                "Host": [
                    "target.host.com"
                ]
            }, 
            "body": "some_overridden_body"
        }, 
        "requestModifier": {
            "cookies": {
                "add": {
                    "cookieToAddOne": "addedValue", 
                    "cookieToAddTwo": "addedValue"
                }, 
                "remove": [
                    "overrideCookieToRemove", 
                    "requestCookieToRemove"
                ], 
                "replace": {
                    "overrideCookieToReplace": "replacedValue", 
                    "requestCookieToReplace": "replacedValue", 
                    "extraCookieToReplace": "shouldBeIgnore"
                }
            }, 
            "headers": {
                "add": {
                    "headerToAddTwo": [
                        "addedValue"
                    ], 
                    "headerToAddOne": [
                        "addedValue"
                    ]
                }, 
                "remove": [
                    "overrideHeaderToRemove", 
                    "requestHeaderToRemove"
                ], 
                "replace": {
                    "requestHeaderToReplace": [
                        "replacedValue"
                    ], 
                    "overrideHeaderToReplace": [
                        "replacedValue"
                    ], 
                    "extraHeaderToReplace": [
                        "shouldBeIgnore"
                    ]
                }
            }, 
            "path": {
                "regex": "^/(.+)/(.+)$", 
                "substitution": "/prefix/$1/infix/$2/postfix"
            }, 
            "queryStringParameters": {
                "add": {
                    "parameterToAddTwo": [
                        "addedValue"
                    ], 
                    "parameterToAddOne": [
                        "addedValue"
                    ]
                }, 
                "remove": [
                    "overrideParameterToRemove", 
                    "requestParameterToRemove"
                ], 
                "replace": {
                    "requestParameterToReplace": [
                        "replacedValue"
                    ], 
                    "overrideParameterToReplace": [
                        "replacedValue"
                    ], 
                    "extraParameterToReplace": [
                        "shouldBeIgnore"
                    ]
                }
            }
        }, 
        "responseOverride": {
            "body": "some_overridden_body"
        },
        "responseModifier": {
            "cookies": {
                "add": {
                    "cookieToAddOne": "addedValue", 
                    "cookieToAddTwo": "addedValue"
                }, 
                "remove": [
                    "overrideCookieToRemove", 
                    "requestCookieToRemove"
                ], 
                "replace": {
                    "overrideCookieToReplace": "replacedValue", 
                    "requestCookieToReplace": "replacedValue", 
                    "extraCookieToReplace": "shouldBeIgnore"
                }
            }, 
            "headers": {
                "add": {
                    "headerToAddTwo": [
                        "addedValue"
                    ], 
                    "headerToAddOne": [
                        "addedValue"
                    ]
                }, 
                "remove": [
                    "overrideHeaderToRemove", 
                    "requestHeaderToRemove"
                ], 
                "replace": {
                    "requestHeaderToReplace": [
                        "replacedValue"
                    ], 
                    "overrideHeaderToReplace": [
                        "replacedValue"
                    ], 
                    "extraHeaderToReplace": [
                        "shouldBeIgnore"
                    ]
                }
            }
        }
    }
}'
```

#### Forward Overridden Request And Change Host And Port

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpOverrideForwardedRequest": {
    "httpRequest": {
      "path": "/some/other/path",
      "headers": {
        "Host": [
          "any.host.com"
        ]
      },
      "socketAddress": {
        "host": "target.host.com",
        "port": 1234,
        "scheme": "HTTPS"
      }
    }
  }
}'
```

#### Forward Overridden Request With Delay

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpOverrideForwardedRequest": {
    "httpRequest": {
      "path": "/some/other/path",
      "headers": {
        "Host": [
          "target.host.com"
        ]
      }
    },
    "delay": {
      "timeUnit": "SECONDS",
      "value": 20
    }
  }
}'
```

#### Forward Using Javascript Template

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpForwardTemplate": {
    "template": "return {'path' : \"/somePath\", 'queryStringParameters' : {'userId' : request.queryStringParameters && request.queryStringParameters['userId']},'headers' : {'Host' : [ \"localhost:1081\" ]}, 'body': {'name': 'value'}};",
    "templateType": "JAVASCRIPT"
  }
}'
```

#### Forward Using Javascript Template With Delay

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpForwardTemplate": {
    "template": "return {'path' : \"/somePath\", 'cookies' : {'SessionId' : request.cookies && request.cookies['SessionId']}, 'headers' : {'Host' : [ \"localhost:1081\" ]},'keepAlive' : true, 'secure' : true, 'body' : \"some_body\"};",
    "templateType": "JAVASCRIPT",
    "delay": {
      "timeUnit": "SECONDS",
      "value": 20
    }
  }
}'
```

#### Forward Using Velocity Template

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpForwardTemplate": {
    "template": "{'path' : \"/somePath\", 'queryStringParameters' : { 'userId' : [ \"$!request.queryStringParameters['userId'][0]\" ]},'cookies' : {'SessionId' : \"$!request.cookies['SessionId']\"},'headers' : {'Host' : [ \"localhost:1081\" ]}, 'body': \"{'name': 'value'}\"}",
    "templateType": "VELOCITY"
  }
}'
```

#### Open API Expectations Loaded By HTTP URL

NOT JSON

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json"
}'
```

#### Open API Expectations Operation

NOT JSON

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
    "operationId": "showPetById"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Open API Expectations Loaded By Classpath Location

NOT JSON

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "specUrlOrPayload": "org/mockserver/mock/openapi_petstore_example.json"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Request Matcher By Open API Loaded By HTTP URL

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json"
}'
```

#### Request Matcher By Open API Operation

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
    "operationId": "showPetById"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Request Matcher By Open API Loaded By Classpath Location

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "specUrlOrPayload": "org/mockserver/mock/openapi_petstore_example.json"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Update Expectation By Id

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "id": "630a6e5b-9d61-4668-a18f-a0d3df558583",
  "priority": 0,
  "httpRequest": {
    "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/mock/openapi_petstore_example.json",
    "operationId": "showPetById"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some_response_body"
  },
  "times": {
    "unlimited": true
  },
  "timeToLive": {
    "unlimited": true
  }
}'
```

#### Match Request By Path

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Path Exactly Twice

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponse": {
    "body": "some_response_body"
  },
  "times": {
    "remainingTimes": 2,
    "unlimited": false
  },
  "timeToLive": {
    "unlimited": true
  }
}'
```

#### Match Request By Path Exactly Once In The Next 60 Seconds

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponse": {
    "body": "some_response_body"
  },
  "times": {
    "remainingTimes": 1,
    "unlimited": false
  },
  "timeToLive": {
    "timeUnit": "SECONDS",
    "timeToLive": 60,
    "unlimited": false
  }
}'
```

#### Match Request By Regex Path

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some.*"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Not Matching Path

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "!/some.*"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Method Regex

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "P.*{2,3}"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Not Matching Method

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "!GET"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Path And Path Parameters And Query Parameters Name

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path/{cartId}",
    "pathParameters": {
      "cartId": [
        "055CA455-1DF7-45BB-8535-4F83E7266092"
      ]
    },
    "queryStringParameters": {
      "type": [
        "[A-Z0-9\\-]+"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Path Parameter Regex Value

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path/{cartId}",
    "pathParameters": {
      "cartId": [
        "[A-Z0-9\\-]+"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Path Parameter Json Schema Value

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path/{cartId}/{maxItemCount}",
    "pathParameters": {
      "cartId": [
        {
          "schema": {
            "type": "string",
            "pattern": "^[A-Z0-9-]+$"
          }
        }
      ],
      "maxItemCount": [
        {
          "schema": {
            "type": "integer"
          }
        }
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Query Parameter Regex Name

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "queryStringParameters": {
      "[A-z]{0,10}": [
        "055CA455-1DF7-45BB-8535-4F83E7266092"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Query Parameter Regex Value

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "queryStringParameters": {
      "cartId": [
        "[A-Z0-9\\-]+"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Optional Query Parameter Regex Value

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "queryStringParameters": {
      "?cartId": [
        "[A-Z0-9\\-]+"
      ],
      "?maxItemCount": [
        {
          "schema": {
            "type": "integer"
          }
        }
      ],
      "?userId": [
        {
          "schema": {
            "type": "string",
            "format": "uuid"
          }
        }
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Query Parameter Json Schema Value

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "queryStringParameters": {
      "cartId": [
        {
          "schema": {
            "type": "string",
            "pattern": "^[A-Z0-9-]+$"
          }
        }
      ],
      "maxItemCount": [
        {
          "schema": {
            "type": "integer"
          }
        }
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Query Parameter Sub Set

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "queryStringParameters": {
      "multiValuedParameter": [
        {
          "schema": {
            "type": "string",
            "pattern": "^[A-Z0-9-]+$"
          }
        }
      ],
      "maxItemCount": [
        {
          "schema": {
            "type": "integer"
          }
        }
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Query Parameter Key Matching

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "queryStringParameters": {
      "keyMatchStyle": "MATCHING_KEY",
      "multiValuedParameter": [
        {
          "schema": {
            "type": "string",
            "pattern": "^[A-Z0-9-]+$"
          }
        }
      ],
      "maxItemCount": [
        {
          "schema": {
            "type": "integer"
          }
        }
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Headers

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "GET",
    "path": "/some/path",
    "headers": {
      "Accept": [
        "application/json"
      ],
      "Accept-Encoding": [
        "gzip, deflate, br"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Header Name Regex

Matches requests that have any header starting with the name Accept

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "headers": {
      "Accept.*": []
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Header Regex Name And Value

Matches requests that have a header with a name starting with Accept and a value containing gzip

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "headers": {
      "Accept.*": [
        ".*gzip.*"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Header Json Schema Value

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "headers": {
      "Accept.*": [
        {
          "schema": {
            "type": "string",
            "pattern": "^.*gzip.*$"
          }
        }
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Either Or Optional Header

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "headers": {
      "headerOne|headerTwo": [
        ".*"
      ],
      "?headerOne": [
        "headerOneValue"
      ],
      "?headerTwo": [
        "headerTwoValue"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Either Or Optional Header

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "id": "2c4f2747-bf8f-42dc-8c82-99f497884cfa",
  "priority": 0,
  "httpRequest": {
    "path": "/some/path",
    "headers": {
      "headerOne|headerTwo": [
        ".*"
      ],
      "?headerOne": [
        "headerOneValue"
      ],
      "?headerTwo": [
        "headerTwoValue"
      ]
    }
  },
  "times": {
    "unlimited": true
  },
  "timeToLive": {
    "unlimited": true
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Header Key Matching

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "headers": {
      "keyMatchStyle": "MATCHING_KEY",
      "multiValuedHeader": [
        "value.*"
      ],
      "headerTwo": [
        "headerTwoValue"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Not Matching Header Value

Matches requests that have an Accept header without the value "application/json" and that have an Accept-Encoding without the substring "gzip"

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "headers": {
      "Accept": [
        "!application/json"
      ],
      "Accept-Encoding": [
        "!.*gzip.*"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Not Matching Headers

Matches requests that do not have either an Accept or an Accept-Encoding header

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path",
    "headers": {
      "!Accept": [
        ".*"
      ],
      "!Accept-Encoding": [
        ".*"
      ]
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Cookies And Query Parameters

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "GET",
    "path": "/view/cart",
    "queryStringParameters": {
      "cartId": [
        "055CA455-1DF7-45BB-8535-4F83E7266092"
      ]
    },
    "cookies": {
      "session": "4930456C-C718-476F-971F-CB8E047AB349"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Cookies And Query Parameter Json Schema Values

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "GET",
    "path": "/view/cart",
    "queryStringParameters": {
      "cartId": [
        {
          "schema": {
            "type": "string",
            "format": "uuid"
          }
        }
      ]
    },
    "cookies": {
      "session": {
        "schema": {
          "type": "string",
          "format": "uuid"
        }
      }
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Optional Cookies And Query Parameter Json Schema Values

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/view/cart",
    "queryStringParameters": {
      "cartId": [
        {
          "schema": {
            "type": "string",
            "format": "uuid"
          }
        }
      ]
    },
    "cookies": {
      "?session": {
        "schema": {
          "type": "string",
          "format": "uuid"
        }
      }
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Regex Body

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": "starts_with_.*"
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body In UTF 16

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "STRING",
      "string": "我说中国话",
      "contentType": "text/plain; charset=utf-16"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With Form Submission

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "POST",
    "headers": {
      "Content-Type": [
        "application/x-www-form-urlencoded"
      ]
    },
    "body": {
      "type": "PARAMETERS",
      "parameters": {
        "email": [
          "joe.blogs@gmail.com"
        ],
        "password": [
          "secure_Password123"
        ]
      }
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With XPath

Matches any request with an XML body containing an element that matches the XPath expression, for example with the following body:

```xml
<?xml version="1.0" encoding="ISO-8859-1"?>
<bookstore>
    <book category="COOKING">
        <title lang="en">Everyday Italian</title>
        <author>Giada De Laurentiis</author>
        <year>2005</year>
        <price>30.00</price>
    </book>
    <book category="CHILDREN">
        <title lang="en">Harry Potter</title>
        <author>J K. Rowling</author>
        <year>2005</year>
        <price>29.99</price>
    </book>
    <book category="WEB">
        <title lang="en">Learning XML</title>
        <author>Erik T. Ray</author>
        <year>2003</year>
        <price>31.95</price>
    </book>
</bookstore>
```

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "XPATH",
      "xpath": "/bookstore/book[price>30]/price"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Not Matching Body With XPath

Matches any request with an XML body that does NOT contain an element that matches the XPath expression

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "not": true,
      "type": "XPATH",
      "xpath": "/bookstore/book[price>30]/price"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With Xml

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "XML",
      "xml": "<bookstore><book nationality=\"ITALIAN\" category=\"COOKING\"><title lang=\"en\">Everyday Italian</title><author>Giada De Laurentiis</author><year>2005</year><price>30.00</price></book></bookstore>"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With Xml With Placeholders

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "XML",
      "xml": "<bookstore><book nationality=\"ITALIAN\" category=\"COOKING\"><title lang=\"en\">Everyday Italian</title><author>${xmlunit.ignore}</author><year>${xmlunit.isNumber}</year><price>30.00</price></book></bookstore>"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With Xml Schema

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "XML_SCHEMA",
      "xmlSchema": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xs:schema xmlns:xs=\"https://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\"><!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) --><!-- with XmlGrid.net Free Online Service https://xmlgrid.net --><xs:element name=\"notes\"><xs:complexType><xs:sequence><xs:element name=\"note\" maxOccurs=\"unbounded\"><xs:complexType><xs:sequence><xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element><xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element><xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element><xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element></xs:sequence></xs:complexType></xs:element></xs:sequence></xs:complexType></xs:element>\n</xs:schema>"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With Json Exactly

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "JSON",
      "json": {
        "id": 1,
        "name": "A green door",
        "price": 12.50,
        "tags": [
          "home",
          "green"
        ]
      },
      "matchType": "STRICT"
    }
  },
  "httpResponse": {
    "statusCode": 202,
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With Json Ignoring Extra Fields

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "JSON",
      "json": {
        "id": 1,
        "name": "A green door",
        "price": 12.50,
        "tags": [
          "home",
          "green"
        ]
      }
    }
  },
  "httpResponse": {
    "statusCode": 202,
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With Json Ignoring Extra Fields In Array Objects

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "POST",
    "path": "/json",
    "body": {
      "type": "JSON",
      "json": {
        "context": [
          {
            "source": "DECISION_REQUEST"
          },
          {
            "source": "DECISION_REQUEST"
          },
          {
            "source": "DECISION_REQUEST"
          }
        ]
      },
      "matchType": "ONLY_MATCHING_FIELDS"
    }
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response"
  },
  "times": {
    "remainingTimes": 1,
    "unlimited": true
  }
}'
```

#### Match Request By Body With Json With Placeholders

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "JSON",
      "json": {
        "id": 1,
        "name": "A green door",
        "price": "${json-unit.ignore-element}",
        "enabled": "${json-unit.any-boolean}",
        "tags": [
          "home",
          "green"
        ]
      }
    }
  },
  "httpResponse": {
    "statusCode": 202,
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With Json Schema

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "JSON_SCHEMA",
      "jsonSchema": {
        "$schema": "https://json-schema.org/draft-04/schema#",
        "title": "Product",
        "description": "A product from Acme's catalog",
        "type": "object",
        "properties": {
          "id": {
            "description": "The unique identifier for a product",
            "type": "integer"
          },
          "name": {
            "description": "Name of the product",
            "type": "string"
          },
          "price": {
            "type": "number",
            "minimum": 0,
            "exclusiveMinimum": true
          },
          "tags": {
            "type": "array",
            "items": {
              "type": "string"
            },
            "minItems": 1,
            "uniqueItems": true
          }
        },
        "required": [
          "id",
          "name",
          "price"
        ]
      }
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Body With Json Path

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "type": "JSON_PATH",
      "jsonPath": "$.store.book[?(@.price < 10)]"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request By Not Matching Body With Json Path

Matches any request with an JSON body that does NOT contain one or more fields that match the JsonPath expression

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "body": {
      "not": true,
      "type": "JSON_PATH",
      "jsonPath": "$.store.book[?(@.price < 10)]"
    }
  },
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request All Requests

If no request matcher is specified then every request matched

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpResponse": {
    "body": "some_response_body"
  }
}'
```

#### Match Request All Requests And Return UTF-16

If no request matcher is specified then every request matched

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpResponse": {
    "headers": {
      "content-type": [
        "text/plain; charset=utf-16"
      ]
    },
    "body": {
      "type": "BINARY",
      "base64Bytes": "/v9iEYv0Ti1W/Yvd"
    }
  }
}'
```

#### Match Request And Return UTF-16

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "GET",
    "path": "/simple"
  },
  "httpResponse": {
    "body": {
      "type": "STRING",
      "string": "سلام",
      "contentType": "text/plain; charset=utf-8"
    }
  },
  "times": {
    "unlimited": true
  }
}'
```

#### Match Request All Requests And Return Simple Response With Plan Text Body

If no request matcher is specified then every request matched

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpResponse": {
    "headers": {
      "Content-Type": [
        "plain/text"
      ]
    },
    "body": "some_response_body"
  }
}'
```

#### Match Request All Requests And Return Response With Cookie

If no request matcher is specified then every request matched

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpResponse": {
    "headers": {
      "Content-Type": [
        "plain/text"
      ]
    },
    "cookies": {
      "Session": "97d43b1e-fe03-4855-926a-f448eddac32f"
    },
    "body": "some_response_body"
  }
}'
```

#### Match Request And Return Response With Status Code And Custom Reason Phrase

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "method": "POST",
    "path": "/some/path"
  },
  "httpResponse": {
    "statusCode": 418,
    "reasonPhrase": "I'm a teapot"
  }
}'
```

#### Match Request And Return Response With Binary PNG Body

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/ws/rest/user/[0-9]+/icon/[0-9]+\\.png"
  },
  "httpResponse": {
    "statusCode": 200,
    "headers": {
      "content-type": [
        "image/png"
      ],
      "content-disposition": [
        "form-data; name=\"test.png\"; filename=\"test.png\""
      ]
    },
    "body": {
      "type": "BINARY",
      "base64Bytes": "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"
    }
  }
}'
```

#### Match Request And Return Response After Ten Second Delay

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponse": {
    "body": "some_response_body",
    "delay": {
      "timeUnit": "SECONDS",
      "value": 10
    }
  }
}'
```

#### Match Request And Return Response With Connection Options To Suppress Headers

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponse": {
    "body": "some_response_body",
    "connectionOptions": {
      "suppressContentLengthHeader": true,
      "suppressConnectionHeader": true
    }
  }
}'
```

#### Match Request And Return Response With Connection Options To Override Headers

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponse": {
    "body": "some_response_body",
    "connectionOptions": {
      "contentLengthHeaderOverride": 10,
      "keepAliveOverride": false
    }
  }
}'
```

#### Match Request And Return Response With Connection Options To Close Socket

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponse": {
    "body": "some_response_body",
    "connectionOptions": {
      "closeSocket": true
    }
  }
}'
```

#### Javascript Templated Response

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponseTemplate": {
    "template": "return {'statusCode': 200,'cookies': {'session': request.headers['session-id'][0]},'headers': {'Date': Date()}, 'body': {method: request.method, path: request.path, body: request.body}};",
    "templateType": "JAVASCRIPT"
  }
}'
```

#### Javascript Templated Response With Delay

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponseTemplate": {
    "template": "if (request.method === 'POST' && request.path === '/somePath') { return {'statusCode': 200, 'body': {name: 'value'}}; } else { return {'statusCode': 406, 'body': request.body};}",
    "templateType": "JAVASCRIPT",
    "delay": {
      "timeUnit": "MINUTES",
      "value": 2
    }
  }
}'
```

#### Velocity Templated Response

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
  "httpRequest": {
    "path": "/some/path"
  },
  "httpResponseTemplate": {
    "template": "{\"statusCode\": 200,\"cookies\": {\"session\": \"$!request.headers['Session-Id'][0]\" },\"headers\": {\"Client-User-Agent\": [ \"$!request.headers['User-Agent'][0]\" ] },\"body\": $!request.body}",
    "templateType": "VELOCITY"
  }
}'
```

#### Retrieve All Active Expectations

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=ACTIVE_EXPECTATIONS"
```

#### Retrieve Active Expectations In Json Filtered By Request Matcher

/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=JSON

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=ACTIVE_EXPECTATIONS&format=JSON" \
-d '{
    "path": "/some/path",
    "method": "POST"
}'
```

#### Retrieve All Recorded Expectations

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS"
```

#### Retrieve Recorded Expectations In Json Filtered By Request Matcher

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=RECORDED_EXPECTATIONS&format=JSON" \
-d '{
    "path": "/some/path",
    "method": "POST"
}'
```

#### Retrieve All Recorded Requests

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=REQUESTS"
```

#### Retrieve Recorded Requests In Json Filtered By Request Matcher

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=REQUESTS&format=JSON" \
-d '{
    "path": "/some/path",
    "method": "POST"
}'
```

#### Retrieve All Recorded Requests And There Responses

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=REQUEST_RESPONSES"
```

#### Retrieve Recorded Requests And There Responses In Json Filtered By Request Matcher

```bash
curl -X PUT "http://localhost:1080/mockserver/retrieve?type=REQUEST_RESPONSES&format=JSON" \
-d '{
    "path": "/some/path",
    "method": "POST"
}'
```

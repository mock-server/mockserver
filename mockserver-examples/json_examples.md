###### expectation - binary response

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/json"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": {
      "type": "BINARY",
      "base64Bytes": "iVBORw0KGgoAAAANSUhEUgAAAC4AAAA0CAYAAAD19ArKAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAyhpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDUuNS1jMDIxIDc5LjE1NTc3MiwgMjAxNC8wMS8xMy0xOTo0NDowMCAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIiB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIgeG1sbnM6c3RSZWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9zVHlwZS9SZXNvdXJjZVJlZiMiIHhtcDpDcmVhdG9yVG9vbD0iQWRvYmUgUGhvdG9zaG9wIENDIDIwMTQgKE1hY2ludG9zaCkiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6NkExNjYxNzFBRjlDMTFFNDg5OEI5RTRGRUE2RDI1Q0QiIHhtcE1NOkRvY3VtZW50SUQ9InhtcC5kaWQ6NkExNjYxNzJBRjlDMTFFNDg5OEI5RTRGRUE2RDI1Q0QiPiA8eG1wTU06RGVyaXZlZEZyb20gc3RSZWY6aW5zdGFuY2VJRD0ieG1wLmlpZDo2QTE2NjE2RkFGOUMxMUU0ODk4QjlFNEZFQTZEMjVDRCIgc3RSZWY6ZG9jdW1lbnRJRD0ieG1wLmRpZDo2QTE2NjE3MEFGOUMxMUU0ODk4QjlFNEZFQTZEMjVDRCIvPiA8L3JkZjpEZXNjcmlwdGlvbj4gPC9yZGY6UkRGPiA8L3g6eG1wbWV0YT4gPD94cGFja2V0IGVuZD0iciI/PjcxawAAABPhSURBVHja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"
    }
  }
}
```

###### expectation - response headers

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/json"
  },
  "httpResponse": {
    "statusCode": 200,
    "headers": [
      {
        "name": "Accept-Ranges",
        "values": [
          "bytes"
        ]
      },
      {
        "name": "vary",
        "values": [
          "accept-encoding"
        ]
      },
      {
        "name": "ETag",
        "values": [
          "W/\"1773-2239948642\""
        ]
      },
      {
        "name": "Connection",
        "values": [
          "keep-alive"
        ]
      },
      {
        "name": "Last-Modified",
        "values": [
          "Fri, 20 Feb 2015 09:48:17 GMT"
        ]
      },
      {
        "name": "cache-control",
        "values": [
          "max-age=60"
        ]
      },
      {
        "name": "Content-Length",
        "values": [
          "6003"
        ]
      },
      {
        "name": "Date",
        "values": [
          "Sat, 11 Apr 2015 11:51:34 GMT"
        ]
      },
      {
        "name": "X-Powered-By",
        "values": [
          "Express"
        ]
      },
      {
        "name": "Content-Type",
        "values": [
          "image/png"
        ]
      }
    ]
  }
}
```

###### expectation - connection options (override headers - don't close)

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/connectContentAndOpen"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response",
    "connectionOptions": {
      "contentLengthHeaderOverride": 5,
      "keepAliveOverride": true,
      "closeSocket": false
    }
  },
  "times": {
    "remainingTimes": 1,
    "unlimited": true
  }
}
```

###### expectation - connection options (override headers)

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/connectContent"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response",
    "connectionOptions": {
      "contentLengthHeaderOverride": 50,
      "keepAliveOverride": true,
      "closeSocket": true
    }
  },
  "times": {
    "remainingTimes": 1,
    "unlimited": true
  }
}
```

###### expectation - connection options (suppress headers - don't close)

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/connectOpen"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response",
    "connectionOptions": {
      "suppressContentLengthHeader": true,
      "suppressConnectionHeader": true,
      "closeSocket": false
    }
  },
  "times": {
    "remainingTimes": 1,
    "unlimited": true
  }
}
```

###### expectation - connection options (suppress headers)

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/connect"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response",
    "connectionOptions": {
      "suppressContentLengthHeader": true,
      "suppressConnectionHeader": true,
      "closeSocket": true
    }
  },
  "times": {
    "remainingTimes": 1,
    "unlimited": true
  }
}
```

###### expectation - cookie

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/cookie",
    "cookies": [
      {
        "name": "name",
        "value": "value"
      }
    ]
  },
  "httpResponse": {
    "statusCode": 200
  }
}
```

###### expectation - cors pre-flight

```json
{
  "httpRequest": {
    "method": "OPTIONS"
  },
  "httpResponse": {
    "headers": [
      {
        "name": "Access-Control-Allow-Origin",
        "values": [
          "*"
        ]
      },
      {
        "name": "Access-Control-Allow-Methods",
        "values": [
          "DELETE, GET, HEAD, OPTIONS, POST, PUT, PATCH"
        ]
      },
      {
        "name": "Access-Control-Allow-Headers",
        "values": [
          "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"
        ]
      },
      {
        "name": "Access-Control-Expose-Headers",
        "values": [
          "Allow, Content-Encoding, Content-Length, Content-Type, ETag, Expires, Last-Modified, Location, Server, Vary, Authorization"
        ]
      },
      {
        "name": "Access-Control-Max-Age",
        "values": [
          "300"
        ]
      },
      {
        "name": "connection",
        "values": [
          "close"
        ]
      }
    ]
  }
}
```

###### expectation - encoded path

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/dWM%2FdWM+ZA=="
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some encoded path response"
  },
  "times": {
    "remainingTimes": 1,
    "unlimited": false
  }
}
```

###### expectation - error simple

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/error"
  },
  "httpError": {
    "delay": {
      "timeUnit": "SECONDS",
      "value": 5
    },
    "dropConnection": true
  }
}
```

###### expectation - error with response bytes

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/errorWithResponseBytes"
  },
  "httpError": {
    "delay": {
      "timeUnit": "SECONDS",
      "value": 5
    },
    "dropConnection": true,
    "responseBytes": "c29tZV9ieXRlcw=="
  }
}
```

###### expectation - forward

```json
{
  "httpRequest": {
    "headers": {
      "host": [
        "www.mock-server.com"
      ]
    },
    "method": "GET"
  },
  "httpForward": {
    "host": "www.mock-server.com",
    "port": 443,
    "scheme": "HTTPS"
  }
}
```

###### expectation - forward override host header

```json
{
  "httpRequest": {
    "path": "/forward"
  },
  "httpOverrideForwardedRequest": {
    "httpRequest": {
      "headers": {
        "host": [
          "www.mock-server.com"
        ]
      }
    }
  }
}
```

###### expectation - forward override path

```json
{
  "httpRequest": {
    "path": "/forward"
  },
  "httpOverrideForwardedRequest": {
    "httpRequest": {
      "path": "/simple"
    }
  }
}
```

###### expectation - forward with delay

```json
{
  "httpRequest": {
    "headers": {
      "host": [
        "www.mock-server.com"
      ]
    },
    "method": "GET"
  },
  "httpForward": {
    "delay": {
      "timeUnit": "SECONDS",
      "value": 60
    },
    "host": "www.mock-server.com",
    "port": 443,
    "scheme": "HTTPS"
  }
}

```

###### expectation - json  request

```json
{
  "httpRequest": {
    "method": "POST",
    "path": "/json",
    "body": {
      "type": "JSON",
      "json": "{\"access_token\":\"6899B611-EFC6-47BB-8101-C06D4BDB14FA\"}"
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
}
```

###### expectation - not cookie

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/cookieNot",
    "cookies": [
      {
        "name": "!name",
        "value": "value"
      }
    ]
  },
  "httpResponse": {
    "statusCode": 200
  }
}
```

###### expectation - not cookie value

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/cookieNotValue",
    "cookies": [
      {
        "name": "name",
        "value": "!value"
      }
    ]
  },
  "httpResponse": {
    "statusCode": 200
  }
}
```

###### expectation - not header value

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/headerNotValue",
    "headers": [
      {
        "name": "key",
        "values": [
          "!value"
        ]
      }
    ]
  },
  "httpResponse": {
    "statusCode": 200
  }
}
```

###### expectation - not parameter name

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/parameterNotName",
    "queryStringParameters": {
      "!name": [
        "valueOne",
        "valueTwo"
      ]
    }
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "not param name"
  }
}
```

###### expectation - not parameter value

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/parameterNotValue",
    "queryStringParameters": [
      {
        "name": "name",
        "values": [
          "!valueOne",
          "valueTwo"
        ]
      }
    ]
  },
  "httpResponse": {
    "statusCode": 200
  }
}
```

###### expectation - not regex body

```json
{
  "httpRequest": {
    "method": "POST",
    "path": "/notBody",
    "body": {
      "not": true,
      "type": "REGEX",
      "regex": "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
    }
  },
  "httpResponse": {
    "statusCode": 200
  }
}

```

###### expectation - override content-type

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/test"
  },
  "httpResponse": {
    "statusCode": 200,
    "headers": [
      {
        "name": "Content-Type",
        "values": [
          "text/xml"
        ]
      }
    ],
    "body": "<test>Hello world</test>"
  }
}
```

###### expectation - query parameter

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/json",
    "queryStringParameters": [
      {
        "name": "foo",
        "values": [
          "bar"
        ]
      }
    ]
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response"
  }
}
```

###### expectation - regex body

```json
{
  "httpRequest": {
    "method": "POST",
    "path": "/notBody",
    "body": {
      "not": true,
      "type": "REGEX",
      "regex": "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
    }
  },
  "httpResponse": {
    "statusCode": 200
  }
}

```

###### expectation - secure

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/secure",
    "secure": true
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response"
  },
  "times": {
    "remainingTimes": 1,
    "unlimited": true
  }
}
```

###### expectation - simple

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/simple"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response"
  }
}
```

###### expectation - simple POST

```json
{
  "httpRequest": {
    "method": "POST",
    "path": "/simple"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response"
  }
}
```

###### expectation - simple with delay

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/delay"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response",
    "delay": {
      "timeUnit": "SECONDS",
      "value": 1
    }
  }
}
```

###### expectation - substring body

```json
{
  "httpRequest": {
    "method": "POST",
    "path": "/substringBody",
    "body": {
      "type": "STRING",
      "substring": true,
      "string": "transferBetweenAccounts"
    }
  },
  "httpResponse": {
    "statusCode": 200
  }
}

```

###### expectation - template javascript

```json
{
  "httpRequest": {
    "path": "/templatePath"
  },
  "httpResponseTemplate": {
    "templateType": "JAVASCRIPT",
    "template": "if (request.method === 'POST' && request.path === '/templatePath') { return { 'statusCode': 200, 'body': JSON.stringify({name: 'value'}) }; } else { return { 'statusCode': 406, 'body': request.body }; }",
    "delay": {
      "timeUnit": "MICROSECONDS",
      "value": 1
    }
  },
  "times": {
    "remainingTimes": 3,
    "unlimited": false
  }
}
```

###### expectation - template velocity

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/simple"
  },
  "httpResponseTemplate": {
    "templateType": "VELOCITY",
    "template": "#if ( $request.method == 'POST' && $request.path == '/somePath' ) { 'statusCode': 200, 'body': \"{'name': 'value'}\" } #else { 'statusCode': 406, 'body': \"$!request.body\" } #end",
    "delay": {
      "timeUnit": "MICROSECONDS",
      "value": 1
    }
  },
  "times": {
    "remainingTimes": 1,
    "unlimited": false
  }
}
```

###### expectation - time to live

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/simple"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response"
  },
  "timeToLive": {
    "timeUnit": "HOURS",
    "timeToLive": 2
  }
}
```

###### expectation - times & time to live

```json
{
  "httpRequest": {
    "method": "GET",
    "path": "/simple"
  },
  "httpResponse": {
    "statusCode": 200,
    "body": "some response"
  },
  "times": {
    "remainingTimes": 2,
    "unlimited": false
  },
  "timeToLive": {
    "timeUnit": "HOURS",
    "timeToLive": 2
  }
}
```

###### expectation - xpath body

```json
{
  "httpRequest": {
    "method": "POST",
    "path": "/SomeService",
    "body": {
      "type": "XPATH",
      "xpath": "(* and /[local-name()='Envelope']/[local-name()='Header']/[local-name()='Context']/Id[text()='ValueX'])"
    }
  },
  "httpResponse": {
    "statusCode": 200,
    "headers": [
      {
        "name": "Content-Type",
        "values": [
          "application/soap+xml; charset=utf-8"
        ]
      }
    ],
    "body": "some_response"
  }
}
```

###### verify - path only

```json
{
  "httpRequest": {
    "path": "/simple"
  },
  "times": {
    "count": 1
  }
}
```

###### verify - json body

```json
{
  "httpRequest": {
    "path": "/json",
    "body": {
      "type": "JSON",
      "json": "{'one_name':'one_value', 'two_name':'two_value'}"
    }
  },
  "times": {
    "count": 1
  }
}
```

###### verify - complex sequence

```json
{
  "httpRequests": [
    {
      "method": "someMethod",
      "path": "somePath",
      "queryStringParameters": [
        {
          "name": "queryStringParameterNameOne",
          "values": [
            "queryStringParameterValueOne_One",
            "queryStringParameterValueOne_Two"
          ]
        },
        {
          "name": "queryStringParameterNameTwo",
          "values": [
            "queryStringParameterValueTwo_One"
          ]
        }
      ],
      "body": {
        "type": "STRING",
        "string": "someBody"
      },
      "cookies": [
        {
          "name": "someCookieName",
          "value": "someCookieValue"
        }
      ],
      "headers": [
        {
          "name": "someHeaderName",
          "values": [
            "someHeaderValue"
          ]
        }
      ]
    },
    {
      "method": "someMethod",
      "path": "somePath",
      "queryStringParameters": [
        {
          "name": "queryStringParameterNameOne",
          "values": [
            "queryStringParameterValueOne_One",
            "queryStringParameterValueOne_Two"
          ]
        },
        {
          "name": "queryStringParameterNameTwo",
          "values": [
            "queryStringParameterValueTwo_One"
          ]
        }
      ],
      "body": {
        "type": "STRING",
        "string": "someBody"
      },
      "cookies": [
        {
          "name": "someCookieName",
          "value": "someCookieValue"
        }
      ],
      "headers": [
        {
          "name": "someHeaderName",
          "values": [
            "someHeaderValue"
          ]
        }
      ]
    }
  ]
}
```
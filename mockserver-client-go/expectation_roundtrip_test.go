package mockserver

import (
	"encoding/json"
	"reflect"
	"testing"
)

// This file guards that the typed Expectation model round-trips every MockServer
// expectation feature the server can emit, without silently dropping fields.
//
// Each test takes a server-shaped JSON fixture, json.Unmarshal's it into an
// Expectation, marshals it back, and asserts the two are *semantically* equal
// (key-order-insensitive, numeric-type-insensitive) via decodeJSON+DeepEqual.
// A missing struct field would be dropped on marshal and fail the comparison.
//
// Fixtures use the header/cookie/parameter OBJECT wire form ({"name":[...]})
// which the map[string][]string fields represent losslessly. Any Delay object
// includes timeUnit+value because those fields are always serialized.

// decodeJSON parses JSON into the generic interface{} tree (map[string]interface{},
// []interface{}, float64, string, bool, nil) so two documents can be compared
// independently of key order or integer/float spelling.
func decodeJSON(t *testing.T, b []byte) interface{} {
	t.Helper()
	var v interface{}
	if err := json.Unmarshal(b, &v); err != nil {
		t.Fatalf("decodeJSON: %v\ninput: %s", err, b)
	}
	return v
}

// assertRoundTrip unmarshals fixture into an Expectation, marshals it back and
// asserts semantic equality with the original fixture.
func assertRoundTrip(t *testing.T, fixture string) {
	t.Helper()
	var exp Expectation
	if err := json.Unmarshal([]byte(fixture), &exp); err != nil {
		t.Fatalf("unmarshal fixture into Expectation: %v", err)
	}
	out, err := json.Marshal(&exp)
	if err != nil {
		t.Fatalf("marshal Expectation: %v", err)
	}
	want := decodeJSON(t, []byte(fixture))
	got := decodeJSON(t, out)
	if !reflect.DeepEqual(want, got) {
		t.Errorf("round-trip mismatch\nfixture: %s\noutput : %s", fixture, out)
	}
}

func TestExpectationChaosRoundTrip(t *testing.T) {
	// A representative chaos profile including an explicit 0.0 probability to
	// prove pointer fields preserve a meaningful zero rather than dropping it.
	assertRoundTrip(t, `{
      "httpResponse": {"statusCode": 200},
      "chaos": {
        "errorStatus": 503,
        "retryAfter": "5",
        "errorProbability": 0.25,
        "dropConnectionProbability": 0,
        "latency": {"timeUnit": "MILLISECONDS", "value": 200},
        "seed": 42,
        "succeedFirst": 0,
        "failRequestCount": 3,
        "outageAfterMillis": 1000,
        "outageDurationMillis": 5000,
        "truncateBodyAtFraction": 0.5,
        "malformedBody": true,
        "slowResponseChunkSize": 8,
        "slowResponseChunkDelay": {"timeUnit": "MILLISECONDS", "value": 10},
        "quotaName": "q1",
        "quotaLimit": 100,
        "quotaWindowMillis": 60000,
        "quotaErrorStatus": 429,
        "degradationRampMillis": 30000
      }
    }`)
}

func TestExpectationRateLimitRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpResponse": {"statusCode": 200},
      "rateLimit": {
        "name": "shared",
        "algorithm": "token_bucket",
        "limit": 10,
        "windowMillis": 1000,
        "burst": 20,
        "refillPerSecond": 2.5,
        "errorStatus": 429,
        "retryAfter": "1"
      }
    }`)
}

func TestForwardWithFallbackRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpForwardWithFallback": {
        "httpForward": {"host": "example.com", "port": 443, "scheme": "HTTPS"},
        "fallbackResponse": {"statusCode": 503, "body": "upstream down"},
        "fallbackOnStatusCodes": [500, 502, 503],
        "fallbackOnTimeout": true,
        "delay": {"timeUnit": "MILLISECONDS", "value": 50},
        "primary": true
      }
    }`)
}

func TestForwardValidateActionRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpForwardValidateAction": {
        "specUrlOrPayload": "file:///specs/petstore.yaml",
        "host": "api.internal",
        "port": 8080,
        "scheme": "HTTP",
        "validateRequest": true,
        "validateResponse": false,
        "validationMode": "LOG_ONLY",
        "delay": {"timeUnit": "MILLISECONDS", "value": 25},
        "primary": true
      }
    }`)
}

func TestGrpcBidiResponseRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "grpcBidiResponse": {
        "statusName": "OK",
        "statusMessage": "done",
        "headers": {"grpc-encoding": ["identity"]},
        "messages": [{"json": "{\"seq\":1}", "delay": {"timeUnit": "MILLISECONDS", "value": 5}}],
        "rules": [
          {"matchJson": "{\"cmd\":\"ping\"}", "responses": [{"json": "{\"cmd\":\"pong\"}"}]}
        ],
        "closeConnection": false,
        "delay": {"timeUnit": "SECONDS", "value": 1},
        "primary": true
      }
    }`)
}

func TestBeforeAndAfterActionsRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpResponse": {"statusCode": 201},
      "beforeActions": [
        {
          "httpRequest": {"method": "POST", "path": "/audit", "body": "before"},
          "delay": {"timeUnit": "MILLISECONDS", "value": 5},
          "blocking": true,
          "timeout": {"timeUnit": "SECONDS", "value": 2},
          "failurePolicy": "FAIL_FAST"
        }
      ],
      "afterActions": [
        {"httpClassCallback": {"callbackClass": "com.example.Notify"}, "blocking": false, "failurePolicy": "BEST_EFFORT"},
        {"httpObjectCallback": {"clientId": "abc123"}}
      ]
    }`)
}

func TestTemplatedAfterActionRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpResponse": {"statusCode": 200},
      "beforeActions": [
        {"httpRequestTemplate": {"templateType": "VELOCITY", "template": "{ \"path\": \"/before\" }"}, "failurePolicy": "FAIL_FAST"}
      ],
      "afterActions": [
        {"httpRequestTemplate": {"templateType": "MUSTACHE", "templateFile": "hooks/after.mustache"}, "delay": {"timeUnit": "MILLISECONDS", "value": 5}}
      ]
    }`)
}

func TestStepsRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "steps": [
        {
          "httpRequest": {"path": "/step1"},
          "httpForward": {"host": "svc-a", "port": 80},
          "blocking": true,
          "timeout": {"timeUnit": "SECONDS", "value": 3},
          "failurePolicy": "BEST_EFFORT"
        },
        {
          "httpResponse": {"statusCode": 200, "body": "final"},
          "responder": true,
          "delay": {"timeUnit": "MILLISECONDS", "value": 10}
        },
        {"httpError": {"dropConnection": true}},
        {"httpOverrideForwardedRequest": {"requestOverride": {"path": "/rewritten"}}}
      ]
    }`)
}

func TestCaptureRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpResponse": {"statusCode": 200},
      "capture": [
        {"source": "jsonPath", "expression": "$.token", "into": "authToken"},
        {"source": "header", "expression": "X-Request-Id", "into": "reqId"},
        {"source": "pathParameter", "expression": "id", "into": "userId"}
      ]
    }`)
}

func TestNamespacePercentageRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "percentage": 25,
      "namespace": "tenant-a",
      "httpResponse": {"statusCode": 200}
    }`)
}

func TestDnsRequestMatcherRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpRequest": {"dnsName": "example.com", "dnsType": "A", "dnsClass": "IN"},
      "dnsResponse": {
        "responseCode": "NOERROR",
        "answerRecords": [{"name": "example.com", "type": "A", "value": "1.2.3.4", "ttl": 60}]
      }
    }`)
}

func TestOpenAPIRequestDefinitionRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpRequest": {
        "specUrlOrPayload": "file:///specs/petstore.yaml",
        "operationId": "listPets",
        "contextPathPrefix": "/v1"
      },
      "httpResponse": {"statusCode": 200}
    }`)
}

func TestRequestMatcherExtrasRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpRequest": {
        "method": "GET",
        "path": "/x",
        "not": true,
        "protocol": "HTTP_2",
        "respondBeforeBody": true,
        "secure": true,
        "keepAlive": false,
        "headers": {"Accept": ["application/json"]},
        "cookies": {"session": "s1"},
        "queryStringParameters": {"q": ["1", "2"]},
        "pathParameters": {"id": ["7"]},
        "socketAddress": {"host": "h", "port": 9090, "scheme": "HTTPS"}
      },
      "httpResponse": {"statusCode": 200}
    }`)
}

// TestBodyMatcherVariantsRoundTrip proves every request body-matcher variant
// round-trips (via the generic Body field). Each variant is embedded as the
// httpRequest.body of an expectation.
func TestBodyMatcherVariantsRoundTrip(t *testing.T) {
	bodies := []string{
		`{"type": "STRING", "string": "hello", "subString": true, "contentType": "text/plain", "not": false, "optional": true}`,
		`{"type": "JSON", "json": "{\"a\":1}", "matchType": "ONLY_MATCHING_FIELDS", "matchNumbersAsStrings": true, "contentType": "application/json"}`,
		`{"type": "JSON_SCHEMA", "jsonSchema": "{\"type\":\"object\"}"}`,
		`{"type": "JSON_SCHEMA", "jsonSchema": {"type": "object", "required": ["a"]}}`,
		`{"type": "JSON_PATH", "jsonPath": "$.store.book[0]"}`,
		`{"type": "XML", "xml": "<a/>", "contentType": "application/xml"}`,
		`{"type": "XML_SCHEMA", "xmlSchema": "<xs:schema/>"}`,
		`{"type": "XPATH", "xpath": "/a/b"}`,
		`{"type": "REGEX", "regex": "a.*z"}`,
		`{"type": "PARAMETERS", "parameters": {"k": ["v1", "v2"]}}`,
		`{"type": "BINARY", "base64Bytes": "AQID", "contentType": "application/octet-stream"}`,
		`{"type": "MULTIPART", "fields": {"f": ["v"]}, "filenames": {"file": ["a.txt"]}, "partContentTypes": {"file": ["text/plain"]}}`,
		`{"type": "GRAPHQL", "query": "{ hero { name } }", "operationName": "op", "variablesSchema": "{}", "selectionSetMatchType": "AST_SUBSET", "fields": ["hero"], "schema": "type Query"}`,
		`{"type": "JSON_RPC", "method": "sum", "paramsSchema": "{}"}`,
		`{"type": "WASM", "moduleName": "validator"}`,
	}
	for _, body := range bodies {
		fixture := `{"httpRequest": {"path": "/b", "body": ` + body + `}, "httpResponse": {"statusCode": 200}}`
		assertRoundTrip(t, fixture)
	}
}

// TestAllOfBodyRoundTrip covers the composite ALL_OF request body matcher.
func TestAllOfBodyRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpRequest": {"path": "/b", "body": {"type": "ALL_OF", "bodyAllOf": [
        {"type": "REGEX", "regex": "a"},
        {"type": "JSON_PATH", "jsonPath": "$.x"}
      ]}},
      "httpResponse": {"statusCode": 200}
    }`)
}

// TestResponseExtrasRoundTrip covers trailers, statusCodeRange, recoverAfter,
// connectionOptions.chunkSize and the delay template/distribution fields.
func TestResponseExtrasRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpResponse": {
        "statusCode": 200,
        "reasonPhrase": "OK",
        "headers": {"Content-Type": ["application/json"]},
        "cookies": {"c": "1"},
        "body": "hi",
        "trailers": {"X-Checksum": ["abc"]},
        "statusCodeRange": "200-299",
        "delay": {
          "timeUnit": "MILLISECONDS",
          "value": 100,
          "template": "$!request.body",
          "templateType": "VELOCITY",
          "distribution": {"type": "GAUSSIAN", "mean": 100, "stdDev": 10}
        },
        "connectionOptions": {
          "suppressContentLengthHeader": false,
          "contentLengthHeaderOverride": 12,
          "suppressConnectionHeader": true,
          "chunkSize": 256,
          "keepAliveOverride": true,
          "closeSocket": false,
          "closeSocketDelay": {"timeUnit": "SECONDS", "value": 1}
        },
        "recoverAfter": {
          "failTimes": 2,
          "failResponse": {"statusCode": 503},
          "idempotencyHeader": "Idempotency-Key"
        }
      }
    }`)
}

func TestDelayDistributionVariantsRoundTrip(t *testing.T) {
	for _, dist := range []string{
		`{"type": "UNIFORM", "min": 10, "max": 100}`,
		`{"type": "LOG_NORMAL", "median": 50, "p99": 500}`,
		`{"type": "GAUSSIAN", "mean": 0, "stdDev": 0}`,
	} {
		fixture := `{"httpResponse": {"statusCode": 200, "delay": {"timeUnit": "MILLISECONDS", "value": 1, "distribution": ` + dist + `}}}`
		assertRoundTrip(t, fixture)
	}
}

func TestOverrideForwardedRequestModifiersRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpOverrideForwardedRequest": {
        "requestOverride": {"path": "/new"},
        "requestModifier": {
          "path": {"regex": "^/old", "substitution": "/new"},
          "queryStringParameters": {"add": {"k": ["v"]}, "remove": ["drop"]},
          "headers": {"replace": {"X-A": ["b"]}},
          "cookies": {"add": {"c": "1"}, "remove": ["old"]}
        },
        "responseOverride": {"statusCode": 202},
        "responseModifier": {
          "headers": {"add": {"X-Trace": ["1"]}, "remove": ["Server"]},
          "cookies": {"replace": {"sid": "2"}}
        },
        "delay": {"timeUnit": "MILLISECONDS", "value": 5}
      }
    }`)
}

func TestHttpTemplateResponseOverrideRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpResponseTemplate": {
        "templateType": "MUSTACHE",
        "template": "{{statusCode}}",
        "delay": {"timeUnit": "MILLISECONDS", "value": 2},
        "responseOverride": {"statusCode": 200, "headers": {"X-T": ["1"]}},
        "responseModifier": {"headers": {"remove": ["Date"]}}
      }
    }`)
}

func TestHttpErrorStreamErrorRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpError": {"streamError": 8, "delay": {"timeUnit": "MILLISECONDS", "value": 1}}
    }`)
}

// TestKitchenSinkExpectationRoundTrip is one expectation exercising the request
// matcher plus a broad set of auxiliary features (chaos, rateLimit, before/after
// actions, steps, capture, cross-protocol scenarios, namespace, percentage,
// scenario state, multi-response mode, times and TTL) simultaneously. It is a
// serialization-fidelity fixture, not necessarily a server-valid combination.
func TestKitchenSinkExpectationRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "id": "exp-1",
      "priority": 10,
      "percentage": 50,
      "namespace": "tenant-x",
      "chaos": {"errorProbability": 0.1, "latency": {"timeUnit": "MILLISECONDS", "value": 20}},
      "rateLimit": {"name": "rl", "algorithm": "fixed_window", "limit": 5, "windowMillis": 1000},
      "httpRequest": {
        "method": "POST",
        "path": "/api/{id}",
        "not": false,
        "protocol": "HTTP_1_1",
        "secure": true,
        "keepAlive": true,
        "headers": {"Accept": ["application/json"]},
        "cookies": {"session": "s"},
        "queryStringParameters": {"q": ["v"]},
        "pathParameters": {"id": ["42"]},
        "socketAddress": {"host": "h", "port": 8443, "scheme": "HTTPS"},
        "jwt": {"claims": {"scope": "read"}, "issuer": "iss", "audience": "aud", "algorithm": "HS256"},
        "body": {"type": "JSON", "json": "{\"a\":1}", "matchType": "ONLY_MATCHING_FIELDS"}
      },
      "httpResponses": [
        {"statusCode": 200, "body": "one"},
        {"statusCode": 500, "body": "two"}
      ],
      "responseMode": "WEIGHTED",
      "responseWeights": [3, 1],
      "switchAfter": 4,
      "beforeActions": [{"httpRequest": {"method": "POST", "path": "/pre"}, "blocking": true, "failurePolicy": "FAIL_FAST"}],
      "afterActions": [{"httpClassCallback": {"callbackClass": "com.example.After"}, "failurePolicy": "BEST_EFFORT"}],
      "steps": [{"httpResponse": {"statusCode": 200}, "responder": true}],
      "capture": [{"source": "jsonPath", "expression": "$.id", "into": "id"}],
      "crossProtocolScenarios": [
        {"trigger": "DNS_QUERY", "matchPattern": "api", "scenarioName": "s", "targetState": "next"}
      ],
      "scenarioName": "checkout",
      "scenarioState": "start",
      "newScenarioState": "paid",
      "times": {"remainingTimes": 5, "unlimited": false},
      "timeToLive": {"timeUnit": "MINUTES", "timeToLive": 30, "unlimited": false}
    }`)
}

// TestTypedBodyBuildersWireShape asserts the typed body-matcher builders emit the
// exact server wire shape (independent of the generic Body passthrough path).
func TestTypedBodyBuildersWireShape(t *testing.T) {
	cases := []struct {
		body *TypedBody
		want string
	}{
		{StringBody("hi"), `{"type": "STRING", "string": "hi"}`},
		{SubStringBody("hi"), `{"type": "STRING", "string": "hi", "subString": true}`},
		{JSONMatchBody("{}", "STRICT"), `{"type": "JSON", "json": "{}", "matchType": "STRICT"}`},
		{JSONSchemaBody("{\"type\":\"object\"}"), `{"type": "JSON_SCHEMA", "jsonSchema": "{\"type\":\"object\"}"}`},
		{JSONPathBody("$.a"), `{"type": "JSON_PATH", "jsonPath": "$.a"}`},
		{XMLBody("<a/>"), `{"type": "XML", "xml": "<a/>"}`},
		{XMLSchemaBody("<xs/>"), `{"type": "XML_SCHEMA", "xmlSchema": "<xs/>"}`},
		{XPathBody("/a"), `{"type": "XPATH", "xpath": "/a"}`},
		{RegexBody("a"), `{"type": "REGEX", "regex": "a"}`},
		{ParameterBody(map[string][]string{"k": {"v"}}), `{"type": "PARAMETERS", "parameters": {"k": ["v"]}}`},
		// MULTIPART must emit "fields" (regression guard for the former GraphQL/MULTIPART json-tag collision).
		{MultipartBody(map[string][]string{"f": {"v"}}, map[string][]string{"file": {"a.txt"}}, nil), `{"type": "MULTIPART", "fields": {"f": ["v"]}, "filenames": {"file": ["a.txt"]}}`},
		{BinaryBody("AQID"), `{"type": "BINARY", "base64Bytes": "AQID"}`},
		{GraphQLBody("{ a }", "op"), `{"type": "GRAPHQL", "query": "{ a }", "operationName": "op"}`},
		{WasmBody("m"), `{"type": "WASM", "moduleName": "m"}`},
	}
	for _, c := range cases {
		out, err := json.Marshal(c.body)
		if err != nil {
			t.Fatalf("marshal %s: %v", c.want, err)
		}
		if !reflect.DeepEqual(decodeJSON(t, out), decodeJSON(t, []byte(c.want))) {
			t.Errorf("typed body wire shape mismatch\nwant: %s\ngot : %s", c.want, out)
		}
	}
}

// TestConstructedKitchenSinkRoundTrip builds an Expectation in Go touching every
// new nested type via struct literals/helpers, then asserts marshal->unmarshal->
// marshal is stable (proves the typed structs themselves round-trip losslessly).
func TestConstructedKitchenSinkRoundTrip(t *testing.T) {
	b := func(v bool) *bool { return &v }
	i := func(v int) *int { return &v }
	i64 := func(v int64) *int64 { return &v }
	f := func(v float64) *float64 { return &v }

	exp := Expectation{
		ID:         "c1",
		Percentage: 40,
		Namespace:  "ns",
		Chaos: &HttpChaosProfile{
			ErrorProbability:          f(0),
			DropConnectionProbability: f(0.5),
			Latency:                   &Delay{TimeUnit: "MILLISECONDS", Value: 5},
			Seed:                      i64(7),
			SucceedFirst:              i(0),
			MalformedBody:             b(true),
			OutageAfterMillis:         i64(100),
		},
		RateLimit: &RateLimit{Name: "rl", Algorithm: RateLimitTokenBucket, Burst: 5, RefillPerSecond: f(1.5)},
		HttpRequest: &HttpRequest{
			Method: "GET", Path: "/x", Not: b(false), Protocol: "HTTP_3",
			Body: GraphQLBody("{ a }", "op"),
		},
		HttpResponse: &HttpResponse{
			StatusCode:        200,
			Trailers:          map[string][]string{"X-Sum": {"1"}},
			StatusCodeRange:   "200-299",
			Delay:             &Delay{TimeUnit: "MILLISECONDS", Value: 10, Distribution: &DelayDistribution{Type: "UNIFORM", Min: i(1), Max: i(9)}},
			ConnectionOptions: &ConnectionOptions{ChunkSize: i(128), CloseSocket: b(false)},
			RecoverAfter:      &RecoverAfter{FailTimes: i(2), FailResponse: &HttpResponse{StatusCode: 503}, IdempotencyHeader: "Idempotency-Key"},
		},
		HttpForwardWithFallback: &HttpForwardWithFallback{
			HttpForward:           &HttpForward{Host: "h", Port: 80},
			FallbackResponse:      &HttpResponse{StatusCode: 503},
			FallbackOnStatusCodes: []int{502, 503},
			FallbackOnTimeout:     b(true),
		},
		HttpForwardValidateAction: &HttpForwardValidateAction{
			SpecUrlOrPayload: "spec", Host: "h", Port: 8080, Scheme: "HTTP",
			ValidateRequest: b(true), ValidateResponse: b(false), ValidationMode: ValidationModeLogOnly,
		},
		GrpcBidiResponse: &GrpcBidiResponse{
			StatusName: "OK",
			Rules:      []GrpcBidiRule{{MatchJson: "{}", Responses: []GrpcStreamMessage{{JSON: "{}"}}}},
			Primary:    b(true),
		},
		BeforeActions: []AfterAction{{HttpRequest: &HttpRequest{Method: "POST", Path: "/pre"}, Blocking: b(true), Timeout: &Delay{TimeUnit: "SECONDS", Value: 1}, FailurePolicy: FailurePolicyFailFast}},
		AfterActions:  []AfterAction{{HttpClassCallback: &HttpClassCallback{CallbackClass: "C"}, FailurePolicy: FailurePolicyBestEffort}},
		Steps:         []ExpectationStep{{HttpResponse: &HttpResponse{StatusCode: 200}, Responder: b(true)}},
		Capture:       []CaptureRule{NewCaptureRule(CaptureSourceJSONPath, "$.id", "id")},
		HttpError:     nil,
		Times:         &Times{RemainingTimes: 3},
		TimeToLive:    UnlimitedTTL(),
	}

	first, err := json.Marshal(&exp)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	var back Expectation
	if err := json.Unmarshal(first, &back); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	second, err := json.Marshal(&back)
	if err != nil {
		t.Fatalf("re-marshal: %v", err)
	}
	if !reflect.DeepEqual(decodeJSON(t, first), decodeJSON(t, second)) {
		t.Errorf("constructed round-trip mismatch\nfirst : %s\nsecond: %s", first, second)
	}
}

// TestLlmCompletionExtrasRoundTrip covers the completion-level fidelity fields
// closed in the go gap wave: the extended usage counts, streamingPhysics'
// subwordStreaming, enforceOutputSchema, toolChoice and the reasoning fields.
func TestLlmCompletionExtrasRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpRequest": {"method": "POST", "path": "/v1/messages"},
      "httpLlmResponse": {
        "provider": "ANTHROPIC",
        "model": "claude-sonnet-4",
        "completion": {
          "text": "hi",
          "usage": {
            "inputTokens": 10,
            "outputTokens": 25,
            "cachedInputTokens": 2,
            "cacheCreationTokens": 3,
            "reasoningTokens": 4
          },
          "streaming": true,
          "outputSchema": "{\"type\":\"object\"}",
          "enforceOutputSchema": true,
          "toolChoice": "auto",
          "reasoningText": "thinking",
          "reasoningSignature": "sig",
          "streamingPhysics": {"tokensPerSecond": 50, "subwordStreaming": false}
        },
        "primary": true
      }
    }`)
}

// TestLlmRerankModerationContentFilterRoundTrip covers the sibling LLM response
// payloads: rerank, moderation and contentFilter.
func TestLlmRerankModerationContentFilterRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpRequest": {"method": "POST", "path": "/v1/embeddings"},
      "httpLlmResponse": {
        "provider": "OPENAI",
        "model": "text-embedding-3-small",
        "embedding": {"dimensions": 1536, "deterministicFromInput": true, "seed": 42},
        "rerank": {"topN": 3, "deterministicFromInput": true, "seed": 42},
        "moderation": {"flaggedCategories": ["violence"], "model": "text-moderation-latest"},
        "contentFilter": {"hate": "low", "sexual": "safe", "violence": "medium", "selfHarm": "safe"}
      }
    }`)
}

// TestGrpcTemplateTypeRoundTrip covers templateType on both gRPC stream-message
// sites: grpcStreamResponse.messages[*] and grpcBidiResponse.rules[*].responses[*].
func TestGrpcTemplateTypeRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "grpcStreamResponse": {
        "statusName": "OK",
        "messages": [
          {"json": "{\"id\":1}"},
          {"json": "{\"id\":2}", "templateType": "VELOCITY", "delay": {"timeUnit": "MILLISECONDS", "value": 100}}
        ]
      }
    }`)
	assertRoundTrip(t, `{
      "grpcBidiResponse": {
        "statusName": "OK",
        "rules": [
          {"matchJson": "{\"name\":\"world\"}", "responses": [{"json": "{\"reply\":\"hi\"}", "templateType": "MUSTACHE"}]}
        ]
      }
    }`)
}

// TestOverrideForwardedRequestModernFormRoundTrip covers the modern oneOf wire
// form of httpOverrideForwardedRequest (httpRequest/httpResponse keys).
func TestOverrideForwardedRequestModernFormRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpRequest": {"path": "/ovr"},
      "httpOverrideForwardedRequest": {
        "httpRequest": {
          "path": "/other",
          "headers": {"Host": ["target.host.com"]},
          "socketAddress": {"host": "target.host.com", "port": 1234, "scheme": "HTTPS"}
        },
        "httpResponse": {"body": "overridden"},
        "delay": {"timeUnit": "MILLISECONDS", "value": 5}
      }
    }`)
}

// TestWebSocketMatchersRoundTrip covers httpWebSocketResponse.matchers
// (per-incoming-frame response rules).
func TestWebSocketMatchersRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpRequest": {"path": "/ws/echo"},
      "httpWebSocketResponse": {
        "subprotocol": "chat",
        "messages": [{"text": "hello"}, {"binary": "AQID", "delay": {"timeUnit": "MILLISECONDS", "value": 5}}],
        "matchers": [
          {"frameType": "TEXT", "textMatcher": "ping", "responses": [{"text": "pong"}]}
        ],
        "closeConnection": false
      }
    }`)
}

// TestChaosGraphqlAndTimestampRoundTrip covers the GraphQL error-envelope chaos
// fields (including an explicit graphqlNullifyData:false) and the top-level
// expectation timestamp.
func TestChaosGraphqlAndTimestampRoundTrip(t *testing.T) {
	assertRoundTrip(t, `{
      "httpRequest": {"path": "/chaos"},
      "httpResponse": {"body": "ok"},
      "chaos": {
        "errorStatus": 500,
        "graphqlErrors": true,
        "graphqlErrorMessage": "boom",
        "graphqlErrorCode": "INTERNAL_SERVER_ERROR",
        "graphqlNullifyData": false
      },
      "timestamp": "2026-07-03T12:00:00.000Z"
    }`)
}

// TestConditionalRequestRoundTrip covers a conditional (if/then/else) matcher
// whose branches are an HTTP matcher, a nested conditional with an OpenAPI
// branch, and an OpenAPI matcher, with `not` at two levels.
func TestConditionalRequestRoundTrip(t *testing.T) {
	fixture := `{
      "httpRequest": {
        "not": true,
        "if": {"method": "POST", "headers": {"content-type": ["application/json"]}},
        "then": {
          "if": {"path": "/orders"},
          "then": {"specUrlOrPayload": "file:///specs/orders.yaml", "operationId": "createOrder", "not": false},
          "else": {"body": {"type": "JSON_SCHEMA", "jsonSchema": "{\"type\": \"object\"}"}}
        },
        "else": {"specUrlOrPayload": {"openapi": "3.0.0"}, "operationId": "listOrders", "contextPathPrefix": "/v1"}
      },
      "httpResponse": {"statusCode": 200}
    }`
	assertRoundTrip(t, fixture)

	var exp Expectation
	if err := json.Unmarshal([]byte(fixture), &exp); err != nil {
		t.Fatal(err)
	}
	req := exp.HttpRequest
	if req.Not == nil || !*req.Not || req.If == nil || req.If.Method != "POST" {
		t.Fatalf("conditional guard not read: %+v", req)
	}
	if req.Then == nil || req.Then.If == nil || req.Then.If.Path != "/orders" {
		t.Fatalf("nested conditional then-branch not read: %+v", req.Then)
	}
	if req.Then.Then == nil || req.Then.Then.OperationId != "createOrder" || req.Then.Then.Not == nil || *req.Then.Then.Not {
		t.Errorf("nested OpenAPI branch not read: %+v", req.Then.Then)
	}
	if req.Else == nil || req.Else.OperationId != "listOrders" || req.Else.ContextPathPrefix != "/v1" {
		t.Errorf("OpenAPI else-branch not read: %+v", req.Else)
	}
}

// TestConditionalRequestAbsentElseOmitted proves an unset branch is left off
// the wire, and that a plain request matcher gains no if/then/else keys.
func TestConditionalRequestAbsentElseOmitted(t *testing.T) {
	out, err := json.Marshal(ConditionalRequest(
		&HttpRequest{Method: "POST"},
		&HttpRequest{Path: "/orders"},
		nil,
	))
	if err != nil {
		t.Fatal(err)
	}
	want := decodeJSON(t, []byte(`{"if": {"method": "POST"}, "then": {"path": "/orders"}}`))
	if got := decodeJSON(t, out); !reflect.DeepEqual(want, got) {
		t.Errorf("unexpected conditional JSON: %s", out)
	}

	out, err = json.Marshal(Request().Method("GET").Path("/x").Build())
	if err != nil {
		t.Fatal(err)
	}
	if got := decodeJSON(t, out); !reflect.DeepEqual(decodeJSON(t, []byte(`{"method": "GET", "path": "/x"}`)), got) {
		t.Errorf("plain request changed on the wire: %s", out)
	}
}

// TestConditionalRequestBuilderMatchesConstructor proves the fluent
// If/Then/Else builder writes the same JSON as ConditionalRequest.
func TestConditionalRequestBuilderMatchesConstructor(t *testing.T) {
	built, err := json.Marshal(Request().
		If(Request().Method("POST")).
		Then(Request().Path("/orders")).
		Else(Request().Method("GET")).
		Build())
	if err != nil {
		t.Fatal(err)
	}
	constructed, err := json.Marshal(ConditionalRequest(
		&HttpRequest{Method: "POST"},
		&HttpRequest{Path: "/orders"},
		&HttpRequest{Method: "GET"},
	))
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(decodeJSON(t, built), decodeJSON(t, constructed)) {
		t.Errorf("builder %s != constructor %s", built, constructed)
	}
}

// TestConditionalRequestWebsiteExample builds exactly what the Go tab of
// creating_expectations.html (button_conditional_request_definition) builds and
// asserts the PUT body equals that example's REST API JSON.
func TestConditionalRequestWebsiteExample(t *testing.T) {
	var body []byte
	ts := stubServer(t, 201, "", nil, nil, &body)
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if _, err := client.Upsert(Expectation{
		HttpRequest: ConditionalRequest(
			&HttpRequest{Method: "POST", Headers: map[string][]string{"content-type": {"application/json"}}},
			&HttpRequest{Body: JSONSchemaBody(`{"type": "object", "required": ["orderId"]}`)},
			&HttpRequest{Method: "GET"},
		),
		HttpResponse: &HttpResponse{StatusCode: 200},
	}); err != nil {
		t.Fatal(err)
	}

	want := decodeJSON(t, []byte(`[{
    "httpRequest": {
        "if": {
            "method": "POST",
            "headers": { "content-type": ["application/json"] }
        },
        "then": {
            "body": {
                "type": "JSON_SCHEMA",
                "jsonSchema": "{\"type\": \"object\", \"required\": [\"orderId\"]}"
            }
        },
        "else": {
            "method": "GET"
        }
    },
    "httpResponse": {
        "statusCode": 200
    }
}]`))
	if got := decodeJSON(t, body); !reflect.DeepEqual(want, got) {
		t.Errorf("website example JSON mismatch\ngot: %s", body)
	}
}

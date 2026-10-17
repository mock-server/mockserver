// Captures and replays a remote MCP server with the MockServer Go client.
//
// Records an MCP (Model Context Protocol) client's JSON-RPC calls to the live
// Parallel Search MCP server (https://search.parallel.ai/mcp) through MockServer,
// then replays them offline from expectations that match on the JSON-RPC method
// and echo the caller's JSON-RPC id with a Velocity template.
//
//	go run .                    # replay the shared fixture (offline)
//	MCP_CAPTURE=true go run .   # capture live, then replay the capture
//
// Run against a MockServer on localhost:1080 (override with MOCKSERVER_HOST /
// MOCKSERVER_PORT). An optional PARALLEL_API_KEY is sent upstream during capture.
package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

const upstreamHost = "search.parallel.ai"

func check(condition bool, format string, args ...interface{}) {
	if !condition {
		fmt.Fprintf(os.Stderr, "FAILED: "+format+"\n", args...)
		os.Exit(1)
	}
}

func must(err error) {
	check(err == nil, "%v", err)
}

// page is one entry of the "results" array Parallel returns from web_search / web_fetch.
type page struct {
	URL      string   `json:"url"`
	Title    string   `json:"title"`
	Excerpts []string `json:"excerpts"`
}

// mcpClient is a minimal MCP Streamable HTTP client: one JSON-RPC message per POST.
type mcpClient struct {
	url, apiKey string
	nextID      int
}

func (m *mcpClient) post(message map[string]interface{}) []byte {
	payload, err := json.Marshal(message)
	must(err)
	req, err := http.NewRequest("POST", m.url, bytes.NewReader(payload))
	must(err)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json, text/event-stream")
	// an explicit User-Agent: Parallel's CDN rejects some library defaults with 403
	req.Header.Set("User-Agent", "mockserver-example/1.0")
	if m.apiKey != "" {
		req.Header.Set("Authorization", "Bearer "+m.apiKey)
	}
	resp, err := (&http.Client{Timeout: 120 * time.Second}).Do(req)
	must(err)
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	must(err)
	check(resp.StatusCode < 400, "%s: HTTP %d %s", message["method"], resp.StatusCode, body)
	return body
}

func (m *mcpClient) notify(method string) {
	m.post(map[string]interface{}{"jsonrpc": "2.0", "method": method})
}

// request sends a JSON-RPC request, checks the reply echoes its id and decodes "result" into out.
func (m *mcpClient) request(method string, params, out interface{}) {
	id := m.nextID
	m.nextID++
	var reply struct {
		ID     json.RawMessage `json:"id"`
		Result json.RawMessage `json:"result"`
	}
	must(json.Unmarshal(m.post(map[string]interface{}{"jsonrpc": "2.0", "method": method, "params": params, "id": id}), &reply))
	check(string(reply.ID) == strconv.Itoa(id), "%s: id %s != %d", method, reply.ID, id)
	must(json.Unmarshal(reply.Result, out))
}

func (m *mcpClient) callTool(name string, arguments map[string]interface{}) []page {
	var result struct {
		Content []struct {
			Text string `json:"text"`
		} `json:"content"`
	}
	m.request("tools/call", map[string]interface{}{"name": name, "arguments": arguments}, &result)
	check(len(result.Content) > 0, "%s returned no content", name)
	var payload struct {
		Results []page `json:"results"`
	}
	must(json.Unmarshal([]byte(result.Content[0].Text), &payload)) // Parallel returns its payload as JSON text
	return payload.Results
}

// runSession is the application under test: initialize, list tools, search, then fetch.
func runSession(m *mcpClient) (server string, tools []string, search, fetch []page) {
	var init struct {
		ServerInfo struct {
			Name string `json:"name"`
		} `json:"serverInfo"`
	}
	m.request("initialize", map[string]interface{}{"protocolVersion": "2025-06-18", "capabilities": map[string]interface{}{},
		"clientInfo": map[string]string{"name": "mockserver-example", "version": "1.0.0"}}, &init)
	m.notify("notifications/initialized")
	var list struct {
		Tools []struct {
			Name string `json:"name"`
		} `json:"tools"`
	}
	m.request("tools/list", map[string]interface{}{}, &list)
	for _, tool := range list.Tools {
		tools = append(tools, tool.Name)
	}
	search = m.callTool("web_search", map[string]interface{}{"objective": "What is MockServer?",
		"search_queries": []string{"MockServer mock HTTP server"}})
	fetch = m.callTool("web_fetch", map[string]interface{}{"urls": []string{"https://www.mock-server.com/"},
		"objective": "What is MockServer?"})
	return init.ServerInfo.Name, tools, search, fetch
}

// bodyJSON parses a recorded body: MockServer records JSON as a string, or as {"json": ...} / {"string": ...}.
func bodyJSON(body interface{}) map[string]interface{} {
	if wrapped, ok := body.(map[string]interface{}); ok {
		if inner, ok := wrapped["json"]; ok {
			body = inner
		} else if inner, ok := wrapped["string"]; ok {
			body = inner
		}
	}
	if text, ok := body.(string); ok {
		var parsed map[string]interface{}
		decoder := json.NewDecoder(strings.NewReader(text))
		decoder.UseNumber()
		if text == "" || decoder.Decode(&parsed) != nil {
			return nil
		}
		return parsed
	}
	parsed, _ := body.(map[string]interface{})
	return parsed
}

// replayExpectation turns one recorded JSON-RPC exchange into a replay expectation that echoes the caller's id.
func replayExpectation(recorded mockserver.Expectation) mockserver.Expectation {
	requestBody := bodyJSON(recorded.HttpRequest.Body)
	method, _ := requestBody["method"].(string)
	request := &mockserver.HttpRequest{Method: "POST", Path: "/mcp", Body: &mockserver.TypedBody{Type: "JSON_RPC", Method: method}}
	if method == "tools/call" {
		params, _ := requestBody["params"].(map[string]interface{})
		tool, _ := params["name"].(string)
		request.Body = mockserver.JSONPathBody(fmt.Sprintf("$[?(@.method == 'tools/call' && @.params.name == '%s')]", tool))
	}
	statusCode := 200
	var responseBody map[string]interface{}
	if recorded.HttpResponse != nil {
		if recorded.HttpResponse.StatusCode != 0 {
			statusCode = recorded.HttpResponse.StatusCode
		}
		responseBody = bodyJSON(recorded.HttpResponse.Body)
	}
	if len(responseBody) == 0 { // a notification: replay the bare status (202 Accepted)
		return mockserver.Expectation{HttpRequest: request, HttpResponse: &mockserver.HttpResponse{StatusCode: statusCode}}
	}
	key := "result"
	if _, ok := responseBody[key]; !ok {
		key = "error"
	}
	var compact bytes.Buffer
	encoder := json.NewEncoder(&compact)
	encoder.SetEscapeHTML(false)
	must(encoder.Encode(responseBody[key]))
	// escape Velocity metacharacters so recorded text (e.g. markdown "#") renders literally
	escaped := strings.NewReplacer("$", "${esc.d}", "#", "${esc.h}", `\`, "${esc.b}").Replace(strings.TrimSpace(compact.String()))
	template := fmt.Sprintf(`{"statusCode": %d, "headers": [{"name": "Content-Type", "values": ["application/json"]}], `+
		`"body": {"jsonrpc": "2.0", "id": $!{request.jsonRpcRawId}, "%s": %s}}`, statusCode, key, escaped)
	built := mockserver.ResponseTemplate("VELOCITY").Template(template).Build()
	return mockserver.Expectation{HttpRequest: request, HttpResponseTemplate: &built}
}

func capture(client *mockserver.Client, mcpURL string) []mockserver.Expectation {
	must(client.Reset())
	// keep credentials (e.g. the forwarded Authorization header) out of MockServer's own log
	_, err := client.UpdateConfiguration(`{"redactSecretsInLog": true}`)
	must(err)
	_, err = client.Upsert(mockserver.Expectation{
		HttpRequest: mockserver.Request().Path("/mcp").BuildPtr(),
		HttpOverrideForwardedRequest: &mockserver.HttpOverrideForwardedRequest{RequestOverride: &mockserver.HttpRequest{
			Headers:       map[string][]string{"Host": {upstreamHost}},
			SocketAddress: &mockserver.SocketAddress{Host: upstreamHost, Port: 443, Scheme: "HTTPS"},
		}},
	})
	must(err)
	fmt.Printf("capture: forwarding /mcp to https://%s/mcp\n", upstreamHost)
	server, tools, search, fetch := runSession(&mcpClient{url: mcpURL, apiKey: os.Getenv("PARALLEL_API_KEY"), nextID: 1})
	check(len(fetch) > 0, "web_fetch returned no results")
	fmt.Printf("capture: %s, tools [%s], %d search results, fetched %s\n", server, strings.Join(tools, ", "), len(search), fetch[0].URL)
	// MockServer records every proxied exchange as an expectation; rewrite each one for replay
	recorded, err := client.RetrieveRecordedExpectations(mockserver.Request().Path("/mcp"))
	must(err)
	var expectations []mockserver.Expectation
	for _, r := range recorded {
		expectations = append(expectations, replayExpectation(r))
	}
	fmt.Printf("capture: recorded %d exchanges -> %d replay expectations\n", len(recorded), len(expectations))
	return expectations
}

func replay(client *mockserver.Client, mcpURL string, expectations []mockserver.Expectation, source string) {
	must(client.Reset()) // drops the forward expectation and the recorded log
	_, err := client.Upsert(expectations...)
	must(err)
	fmt.Printf("replay: loaded %d expectations from %s\n", len(expectations), source)
	server, tools, search, fetch := runSession(&mcpClient{url: mcpURL, nextID: 1})
	joined := "," + strings.Join(tools, ",") + ","
	check(strings.Contains(joined, ",web_search,") && strings.Contains(joined, ",web_fetch,"), "tools/list returned %v", tools)
	check(len(search) > 0, "web_search returned no results")
	for _, result := range search {
		check(result.URL != "", "web_search returned a result without a url")
	}
	check(len(fetch) > 0 && len(fetch[0].Excerpts) > 0 && fetch[0].Excerpts[0] != "", "web_fetch returned no content")
	forwarded, err := client.RetrieveRecordedExpectations(mockserver.Request().Path("/mcp")) // only proxied traffic is recorded
	must(err)
	check(len(forwarded) == 0, "%d requests were forwarded upstream during replay", len(forwarded))
	fmt.Printf("replay: %s, tools [%s]\n", server, strings.Join(tools, ", "))
	fmt.Printf("replay: web_search -> %d results, first %s\n", len(search), search[0].URL)
	fmt.Printf("replay: web_fetch -> %s (%d chars)\n", fetch[0].URL, utf8.RuneCountInString(fetch[0].Excerpts[0]))
	fmt.Printf("replay: 0 requests forwarded to %s -- answered from recorded fixtures\n", upstreamHost)
}

func main() {
	host := os.Getenv("MOCKSERVER_HOST")
	if host == "" {
		host = "localhost"
	}
	port := 1080
	if p := os.Getenv("MOCKSERVER_PORT"); p != "" {
		n, err := strconv.Atoi(p)
		must(err)
		port = n
	}
	mcpURL := fmt.Sprintf("http://%s:%d/mcp", host, port)
	client := mockserver.New(host, port)
	if os.Getenv("MCP_CAPTURE") == "true" {
		replay(client, mcpURL, capture(client, mcpURL), "the live capture")
		return
	}
	_, source, _, _ := runtime.Caller(0) // the fixture lives beside this example, not the working directory
	fixture := filepath.Join(filepath.Dir(source), "..", "..", "json", "parallel_search_mcp", "replay_expectations.json")
	data, err := os.ReadFile(fixture)
	must(err)
	var expectations []mockserver.Expectation
	must(json.Unmarshal(data, &expectations))
	replay(client, mcpURL, expectations, filepath.Base(fixture))
}

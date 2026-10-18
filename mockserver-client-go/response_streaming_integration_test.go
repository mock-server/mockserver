package mockserver_test

import (
	"encoding/json"
	"io"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"

	mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"
)

// These tests prove SSE and LLM actions registered via the client are served with the right bytes.

// sseEvent is one parsed Server-Sent Event frame.
type sseEvent struct {
	ID, Event, Data, Retry string
}

// parseSse splits a text/event-stream body into its events (blank-line
// separated "field: value" frames).
func parseSse(body string) []sseEvent {
	var events []sseEvent
	for _, frame := range strings.Split(strings.ReplaceAll(body, "\r\n", "\n"), "\n\n") {
		if strings.TrimSpace(frame) == "" {
			continue
		}
		var e sseEvent
		for _, line := range strings.Split(frame, "\n") {
			name, value, _ := strings.Cut(line, ":")
			value = strings.TrimPrefix(value, " ")
			switch name {
			case "id":
				e.ID = value
			case "event":
				e.Event = value
			case "data":
				e.Data = value
			case "retry":
				e.Retry = value
			}
		}
		events = append(events, e)
	}
	return events
}

func TestIntegration_SseResponseStreamedOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := client.When(
		mockserver.Request().Method("GET").Path("/gowire-sse"),
	).RespondSse(
		mockserver.SseResponse().
			StatusCode(200).
			AddEvent(mockserver.SseEvent{Event: "greet", Data: "hello-go", ID: "1"}).
			AddEvent(mockserver.SseEvent{Event: "update", Data: "world-go", ID: "42", Retry: 3000}).
			CloseConnection(true),
	); err != nil {
		t.Fatalf("registering SSE expectation: %v", err)
	}

	status, headers, body := getRawBytes(t, "/gowire-sse")
	if status != 200 {
		t.Fatalf("SSE response returned status %d, want 200", status)
	}
	if ct := headers.Get("Content-Type"); !strings.HasPrefix(ct, "text/event-stream") {
		t.Errorf("SSE Content-Type = %q, want text/event-stream", ct)
	}
	got := parseSse(string(body))
	want := []sseEvent{
		{ID: "1", Event: "greet", Data: "hello-go"},
		{ID: "42", Event: "update", Data: "world-go", Retry: "3000"},
	}
	if len(got) != len(want) {
		t.Fatalf("SSE stream had %d events %+v, want %d %+v (raw body %q)", len(got), got, len(want), want, body)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("SSE event %d = %+v, want %+v", i, got[i], want[i])
		}
	}
}

func TestIntegration_LlmResponseServesOpenAiEnvelopeOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	if _, err := mockserver.LlmMock("/gowire-llm/v1/chat/completions").
		WithProvider(mockserver.ProviderOpenAI).
		WithModel("gpt-4o-gowire").
		RespondingWithCompletion(
			mockserver.NewCompletion().
				WithText("Hello from Go").
				WithStopReason("stop").
				WithUsage(mockserver.NewUsage().WithInputTokens(11).WithOutputTokens(7)),
		).ApplyTo(client); err != nil {
		t.Fatalf("registering LLM expectation: %v", err)
	}

	status, headers, body := postJSON(t, "/gowire-llm/v1/chat/completions",
		`{"model":"gpt-4o-gowire","messages":[{"role":"user","content":"hi"}]}`)
	if status != 200 {
		t.Fatalf("LLM response returned status %d, want 200 (body %q)", status, body)
	}
	if ct := headers.Get("Content-Type"); !strings.HasPrefix(ct, "application/json") {
		t.Errorf("LLM Content-Type = %q, want application/json", ct)
	}

	var envelope struct {
		ID      string `json:"id"`
		Object  string `json:"object"`
		Model   string `json:"model"`
		Choices []struct {
			Message struct {
				Role    string `json:"role"`
				Content string `json:"content"`
			} `json:"message"`
			FinishReason string `json:"finish_reason"`
		} `json:"choices"`
		Usage struct {
			PromptTokens     int `json:"prompt_tokens"`
			CompletionTokens int `json:"completion_tokens"`
			TotalTokens      int `json:"total_tokens"`
		} `json:"usage"`
	}
	if err := json.Unmarshal(body, &envelope); err != nil {
		t.Fatalf("LLM body is not JSON: %v (%q)", err, body)
	}
	if envelope.Object != "chat.completion" || !strings.HasPrefix(envelope.ID, "chatcmpl-") {
		t.Errorf("envelope object/id = %q/%q, want chat.completion/chatcmpl-* -- not an OpenAI envelope", envelope.Object, envelope.ID)
	}
	if envelope.Model != "gpt-4o-gowire" {
		t.Errorf("envelope model = %q, want gpt-4o-gowire", envelope.Model)
	}
	if len(envelope.Choices) != 1 {
		t.Fatalf("envelope has %d choices, want 1 (%s)", len(envelope.Choices), body)
	}
	c := envelope.Choices[0]
	if c.Message.Role != "assistant" || c.Message.Content != "Hello from Go" || c.FinishReason != "stop" {
		t.Errorf("choice = %+v, want assistant/'Hello from Go'/stop", c)
	}
	if envelope.Usage.PromptTokens != 11 || envelope.Usage.CompletionTokens != 7 || envelope.Usage.TotalTokens != 18 {
		t.Errorf("usage = %+v, want prompt 11, completion 7, total 18", envelope.Usage)
	}
}

func TestIntegration_LlmStreamingResponseServesOpenAiChunksOverWire(t *testing.T) {
	client := skipIfNoServer(t)
	mustReset(t, client)

	const text = "Streamed hello from Go"
	if _, err := mockserver.LlmMock("/gowire-llm-stream/v1/chat/completions").
		WithProvider(mockserver.ProviderOpenAI).
		WithModel("gpt-4o-gowire").
		RespondingWithCompletion(mockserver.NewCompletion().WithText(text).WithStreamingOn()).
		ApplyTo(client); err != nil {
		t.Fatalf("registering streaming LLM expectation: %v", err)
	}

	status, headers, body := postJSON(t, "/gowire-llm-stream/v1/chat/completions",
		`{"model":"gpt-4o-gowire","stream":true,"messages":[{"role":"user","content":"hi"}]}`)
	if status != 200 {
		t.Fatalf("streaming LLM response returned status %d, want 200 (body %q)", status, body)
	}
	if ct := headers.Get("Content-Type"); !strings.HasPrefix(ct, "text/event-stream") {
		t.Errorf("streaming LLM Content-Type = %q, want text/event-stream -- streaming flag not honoured", ct)
	}

	events := parseSse(string(body))
	if len(events) < 2 || events[len(events)-1].Data != "[DONE]" {
		t.Fatalf("stream must end with data: [DONE], got %+v", events)
	}
	var assembled strings.Builder
	finish := ""
	for _, e := range events[:len(events)-1] {
		var chunk struct {
			Object  string `json:"object"`
			Choices []struct {
				Delta struct {
					Content string `json:"content"`
				} `json:"delta"`
				FinishReason *string `json:"finish_reason"`
			} `json:"choices"`
		}
		if err := json.Unmarshal([]byte(e.Data), &chunk); err != nil {
			t.Fatalf("chunk is not JSON: %v (%q)", err, e.Data)
		}
		if chunk.Object != "chat.completion.chunk" {
			t.Errorf("chunk object = %q, want chat.completion.chunk", chunk.Object)
		}
		for _, ch := range chunk.Choices {
			assembled.WriteString(ch.Delta.Content)
			if ch.FinishReason != nil {
				finish = *ch.FinishReason
			}
		}
	}
	if assembled.String() != text {
		t.Errorf("reassembled streamed content = %q, want %q", assembled.String(), text)
	}
	if finish != "stop" {
		t.Errorf("final finish_reason = %q, want stop", finish)
	}
}

// postJSON POSTs a JSON body to path (relative to MOCKSERVER_URL) and returns
// the status, headers and raw body bytes.
func postJSON(t *testing.T, path, payload string) (int, http.Header, []byte) {
	t.Helper()
	client := &http.Client{
		Timeout:   10 * time.Second,
		Transport: &http.Transport{DisableKeepAlives: true},
	}
	resp, err := client.Post(os.Getenv("MOCKSERVER_URL")+path, "application/json", strings.NewReader(payload))
	if err != nil {
		t.Fatalf("POST %s failed: %v", path, err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatalf("reading %s body: %v", path, err)
	}
	return resp.StatusCode, resp.Header, body
}

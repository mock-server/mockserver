package mockserver

import (
	"strings"
	"testing"
)

func TestEscapeVelocity_EscapesBackslashBeforeMetacharacters(t *testing.T) {
	// shared cross-client table, asserted identically in all eight client builders
	cases := map[string]string{
		`a\$b`:       `a${esc.b}${esc.d}b`,
		`a\#b`:       `a${esc.b}${esc.h}b`,
		`end\`:       `end${esc.b}`,
		`\\$`:        `${esc.b}${esc.b}${esc.d}`,
		`${esc.b}`:   `${esc.d}{esc.b}`,
		`é\$ü`:       `é${esc.b}${esc.d}ü`,
		`a\nb \"q\"`: `a\nb \"q\"`,
		`\\x`:        `\\x`,
		`$#`:         `${esc.d}${esc.h}`,
		``:           ``,
	}
	for in, want := range cases {
		if got := escapeVelocity(in); got != want {
			t.Errorf("escapeVelocity(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestMcpMock_EscapesBackslashInToolResponse(t *testing.T) {
	exps := McpMock("/mcp").
		WithTool("escapes", func(tb *McpToolBuilder) {
			tb.RespondingWith(`a\$b`, false)
		}).
		Build()

	call := findByTemplate(exps, `"content": [`)
	if call == nil {
		t.Fatalf("tools/call not found")
	}
	// JSON-escaped `a\\$b`, then each backslash and the dollar escaped for Velocity
	if !strings.Contains(call.HttpResponseTemplate.Template, `a${esc.b}${esc.b}${esc.d}b`) {
		t.Errorf("backslash velocity escaping wrong: %q", call.HttpResponseTemplate.Template)
	}
}

func TestA2aMock_EscapesBackslashInTaskResponse(t *testing.T) {
	exps := A2aMock("/a2a").WithDefaultTaskResponse(`a\#b`).Build()

	send := findByTemplate(exps, `"artifacts"`)
	if send == nil {
		t.Fatalf("tasks/send not found")
	}
	if !strings.Contains(send.HttpResponseTemplate.Template, `a${esc.b}${esc.b}${esc.h}b`) {
		t.Errorf("backslash velocity escaping wrong: %q", send.HttpResponseTemplate.Template)
	}
}

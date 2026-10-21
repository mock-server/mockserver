package mockserver

import (
	"encoding/json"
	"strings"
)

// notChar and optionalChar are the markers MockServer strips from the front of a
// plain matcher string: '!' negates the matcher, '?' makes it optional.
const (
	notChar      = '!'
	optionalChar = '?'
)

// MatcherValue is a single matcher value for a header, query-string parameter,
// cookie or path parameter.
//
// MockServer's plain-string wire form encodes negation and optionality as
// leading markers, and the server strips them unconditionally when reading. A
// value whose own first character is '!' or '?' therefore cannot be sent as a
// bare string: "header X is exactly !foo" would be read back as "header X is
// anything but foo" -- which matches almost every request, so the expectation
// silently passes for the wrong reason instead of failing loudly.
//
// MatcherValue keeps the value and the flags apart, and falls back to the
// object form ({"not":false,"value":"!foo"}) only when the plain form would be
// misread. Everything else stays byte-identical on the wire, so existing
// expectations are unaffected.
//
// Use Literal for an exact value and NotLiteral to negate one:
//
//	req.HeaderMatcher("X-Tag", mockserver.Literal("!foo"))    // X-Tag IS "!foo"
//	req.HeaderMatcher("X-Tag", mockserver.NotLiteral("foo"))  // X-Tag is NOT "foo"
//
// Use Before, After or EqualToDateTime to compare the value as a date-time:
//
//	req.HeaderMatcher("X-Issued-At", mockserver.After("now -1 hours"))
type MatcherValue struct {
	// Value is the matcher text, taken verbatim -- markers in it are not parsed.
	Value string
	// Not negates the matcher.
	Not bool
	// Optional marks the header/parameter/cookie as optional.
	Optional bool
	// DateTime, when non-nil, makes this a date-time matcher; Value and
	// Optional must then be empty. See Before, After and EqualToDateTime.
	DateTime *DateTimeMatcher
}

// Literal returns a matcher for exactly value, even when value starts with '!'
// or '?'.
func Literal(value string) MatcherValue {
	return MatcherValue{Value: value}
}

// NotLiteral returns a matcher that matches anything except exactly value.
func NotLiteral(value string) MatcherValue {
	return MatcherValue{Value: value, Not: true}
}

// OptionalLiteral returns a matcher for exactly value that need not be present.
func OptionalLiteral(value string) MatcherValue {
	return MatcherValue{Value: value, Optional: true}
}

// literals converts plain wire strings to MatcherValues by parsing them exactly
// as the server would, so an existing "!foo" keeps meaning "not foo". The
// meaning is preserved; the representation changes from an encoded string to
// explicit fields.
func literals(values []string) []MatcherValue {
	out := make([]MatcherValue, 0, len(values))
	for _, v := range values {
		out = append(out, parsePlain(v))
	}
	return out
}

// isBlank mirrors Java's StringUtils.isBlank: nil, empty or whitespace only.
func isBlank(s string) bool { return strings.TrimSpace(s) == "" }

// serialise renders the plain-string form, matching NottableString.serialise().
func (m MatcherValue) serialise() string {
	var b strings.Builder
	if m.Optional {
		b.WriteByte(optionalChar)
	}
	if m.Not {
		b.WriteByte(notChar)
	}
	if !isBlank(m.Value) {
		b.WriteString(m.Value)
	}
	return b.String()
}

// parsePlain mirrors NottableString.string(String): it strips an optional
// marker, then a not marker, then an optional marker again.
func parsePlain(s string) MatcherValue {
	m := MatcherValue{}
	if !isBlank(s) {
		if len(s) > 0 && s[0] == optionalChar {
			m.Optional = true
			s = s[1:]
		}
		if len(s) > 0 && s[0] == notChar {
			m.Not = true
			s = s[1:]
		}
		if len(s) > 0 && s[0] == optionalChar {
			m.Optional = true
			s = s[1:]
		}
	}
	m.Value = s
	return m
}

// ambiguous reports whether re-reading the plain form would change the value,
// the negation or the optionality. Decided by actually re-parsing rather than
// by testing for a leading marker, so it stays correct for compound markers
// ("?!", "!?") and any marker added later.
func (m MatcherValue) ambiguous() bool {
	if m.DateTime != nil {
		// a date-time matcher has no plain-string form at all
		return true
	}
	if isBlank(m.Value) {
		// A blank value has no marker to misread, and it cannot be expressed in
		// the object form either: the server's object-form reader ignores a
		// blank "value", so escaping one would silently drop the matcher.
		return false
	}
	reparsed := parsePlain(m.serialise())
	return reparsed.Not != m.Not || reparsed.Optional != m.Optional || reparsed.Value != m.Value
}

// MarshalJSON emits the plain string when it round-trips, and the object form
// when it would not.
func (m MatcherValue) MarshalJSON() ([]byte, error) {
	if m.DateTime != nil {
		return m.marshalDateTime()
	}
	if !m.ambiguous() {
		return json.Marshal(m.serialise())
	}
	// "not" is written even when false so the intent is unmistakable to a reader
	// and to the other clients, rather than relying on absent-means-false.
	obj := struct {
		Not      bool   `json:"not"`
		Optional bool   `json:"optional,omitempty"`
		Value    string `json:"value"`
	}{Not: m.Not, Optional: m.Optional, Value: m.Value}
	return json.Marshal(obj)
}

// UnmarshalJSON accepts the plain-string form, the nottable object form and the
// date-time object form.
func (m *MatcherValue) UnmarshalJSON(data []byte) error {
	var s string
	if err := json.Unmarshal(data, &s); err == nil {
		*m = parsePlain(s)
		return nil
	}
	var obj struct {
		Not      *bool   `json:"not"`
		Optional *bool   `json:"optional"`
		Value    *string `json:"value"`
		dateTimeWire
	}
	if err := json.Unmarshal(data, &obj); err != nil {
		return err
	}
	dateTime, err := obj.dateTimeWire.toMatcher()
	if err != nil {
		return err
	}
	*m = MatcherValue{DateTime: dateTime}
	if obj.Value != nil {
		m.Value = *obj.Value
	}
	if obj.Not != nil {
		m.Not = *obj.Not
	}
	if obj.Optional != nil {
		m.Optional = *obj.Optional
	}
	return nil
}

// String returns the plain-string form, for logging. A date-time matcher has no
// plain form, so it is rendered as its JSON object instead.
func (m MatcherValue) String() string {
	if m.DateTime != nil {
		if b, err := json.Marshal(m); err == nil {
			return string(b)
		}
	}
	return m.serialise()
}

package mockserver

import (
	"encoding/json"
	"errors"
	"fmt"
)

// DateTimeOperator is how a date-time matcher compares the actual value with the
// expected one. Its value is the JSON field the expected value is written under.
type DateTimeOperator string

const (
	// DateTimeBefore matches an actual value earlier than the expected one.
	DateTimeBefore DateTimeOperator = "before"
	// DateTimeAfter matches an actual value later than the expected one.
	DateTimeAfter DateTimeOperator = "after"
	// DateTimeEqualTo matches an actual value at the same instant as the expected one.
	DateTimeEqualTo DateTimeOperator = "equalToDateTime"
)

// DateTimeMatcher is the date-time form of a MatcherValue: the header, query
// parameter, path parameter or cookie value is parsed as a date-time and
// compared with Expected. Build one with Before, After or EqualToDateTime and
// the MatcherValue With* options rather than directly.
//
// The server validates the date-time text and the options; an empty string
// option is treated as unset and is not sent.
type DateTimeMatcher struct {
	// Operator is DateTimeBefore, DateTimeAfter or DateTimeEqualTo.
	Operator DateTimeOperator
	// Expected is "now", "now" with an offset such as "now -1 hours", or an
	// ISO-8601, RFC-1123 or epoch date-time.
	Expected string
	// ActualFormat is how the actual value is written: a Java DateTimeFormatter
	// pattern or iso, rfc1123, epochSeconds or epochMillis.
	ActualFormat string
	// TruncateExpected truncates the expected value, such as "startOfDay".
	TruncateExpected string
	// TruncateActual truncates the actual value, such as "first day of month".
	TruncateActual string
	// ApplyTruncationLast truncates "now" after adding its offset rather than
	// before. nil leaves it unset.
	ApplyTruncationLast *bool
	// Zone is the zone for values without one, for truncation and for day,
	// month and year offsets. The server default is UTC.
	Zone string
}

// Before returns a matcher for a date-time earlier than expected.
func Before(expected string) MatcherValue { return dateTimeValue(DateTimeBefore, expected) }

// After returns a matcher for a date-time later than expected.
func After(expected string) MatcherValue { return dateTimeValue(DateTimeAfter, expected) }

// EqualToDateTime returns a matcher for a date-time at the same instant as
// expected.
func EqualToDateTime(expected string) MatcherValue {
	return dateTimeValue(DateTimeEqualTo, expected)
}

func dateTimeValue(operator DateTimeOperator, expected string) MatcherValue {
	return MatcherValue{DateTime: &DateTimeMatcher{Operator: operator, Expected: expected}}
}

// WithActualFormat returns a copy whose actual value is parsed with format.
// Like every date-time option it applies only to a value built with Before,
// After or EqualToDateTime; on any other value marshalling fails.
func (m MatcherValue) WithActualFormat(format string) MatcherValue {
	return m.withDateTime(func(d *DateTimeMatcher) { d.ActualFormat = format })
}

// WithTruncateExpected returns a copy that truncates the expected value.
func (m MatcherValue) WithTruncateExpected(truncation string) MatcherValue {
	return m.withDateTime(func(d *DateTimeMatcher) { d.TruncateExpected = truncation })
}

// WithTruncateActual returns a copy that truncates the actual value.
func (m MatcherValue) WithTruncateActual(truncation string) MatcherValue {
	return m.withDateTime(func(d *DateTimeMatcher) { d.TruncateActual = truncation })
}

// WithApplyTruncationLast returns a copy that truncates "now" after (true) or
// before (false) adding its offset.
func (m MatcherValue) WithApplyTruncationLast(applyLast bool) MatcherValue {
	return m.withDateTime(func(d *DateTimeMatcher) { d.ApplyTruncationLast = &applyLast })
}

// WithZone returns a copy that uses zone, such as "Europe/London".
func (m MatcherValue) WithZone(zone string) MatcherValue {
	return m.withDateTime(func(d *DateTimeMatcher) { d.Zone = zone })
}

// WithNot returns a copy that is negated (true) or not (false). It applies to
// every kind of MatcherValue.
func (m MatcherValue) WithNot(not bool) MatcherValue {
	m.Not = not
	return m
}

// withDateTime changes a copy of the DateTimeMatcher, never the one the receiver
// points at, so values derived from the same base do not affect each other.
func (m MatcherValue) withDateTime(set func(*DateTimeMatcher)) MatcherValue {
	var d DateTimeMatcher
	if m.DateTime != nil {
		d = *m.DateTime
	}
	set(&d)
	m.DateTime = &d
	return m
}

// dateTimeWire is the date-time half of the object form. Exactly one of the
// three operator fields is set; the others are written only when set.
type dateTimeWire struct {
	Before              *string `json:"before,omitempty"`
	After               *string `json:"after,omitempty"`
	EqualToDateTime     *string `json:"equalToDateTime,omitempty"`
	ActualFormat        *string `json:"actualFormat,omitempty"`
	TruncateExpected    *string `json:"truncateExpected,omitempty"`
	TruncateActual      *string `json:"truncateActual,omitempty"`
	ApplyTruncationLast *bool   `json:"applyTruncationLast,omitempty"`
	Zone                *string `json:"zone,omitempty"`
}

// toMatcher returns nil when the object carries no date-time field at all.
func (w dateTimeWire) toMatcher() (*DateTimeMatcher, error) {
	d := &DateTimeMatcher{}
	operators := 0
	for _, op := range []struct {
		operator DateTimeOperator
		expected *string
	}{{DateTimeBefore, w.Before}, {DateTimeAfter, w.After}, {DateTimeEqualTo, w.EqualToDateTime}} {
		if op.expected != nil {
			operators++
			d.Operator = op.operator
			d.Expected = *op.expected
		}
	}
	hasOptions := w.ActualFormat != nil || w.TruncateExpected != nil || w.TruncateActual != nil ||
		w.ApplyTruncationLast != nil || w.Zone != nil
	switch {
	case operators == 0 && !hasOptions:
		return nil, nil
	case operators == 0:
		return nil, errors.New("mockserver: date-time matcher options need one of before, after or equalToDateTime")
	case operators > 1:
		return nil, errors.New("mockserver: date-time matcher has more than one of before, after and equalToDateTime")
	}
	d.ActualFormat = derefString(w.ActualFormat)
	d.TruncateExpected = derefString(w.TruncateExpected)
	d.TruncateActual = derefString(w.TruncateActual)
	d.ApplyTruncationLast = w.ApplyTruncationLast
	d.Zone = derefString(w.Zone)
	return d, nil
}

// marshalDateTime writes the object form in the same field order as the Java
// serialiser, with "not" only when true.
func (m MatcherValue) marshalDateTime() ([]byte, error) {
	if m.Value != "" || m.Optional {
		return nil, errors.New("mockserver: a date-time matcher cannot also have a value or be optional")
	}
	d := m.DateTime
	wire := dateTimeWire{
		ActualFormat:        nonEmpty(d.ActualFormat),
		TruncateExpected:    nonEmpty(d.TruncateExpected),
		TruncateActual:      nonEmpty(d.TruncateActual),
		ApplyTruncationLast: d.ApplyTruncationLast,
		Zone:                nonEmpty(d.Zone),
	}
	expected := d.Expected
	switch d.Operator {
	case DateTimeBefore:
		wire.Before = &expected
	case DateTimeAfter:
		wire.After = &expected
	case DateTimeEqualTo:
		wire.EqualToDateTime = &expected
	default:
		return nil, fmt.Errorf("mockserver: date-time matcher operator %q is not before, after or equalToDateTime; build it with Before, After or EqualToDateTime", d.Operator)
	}
	return json.Marshal(struct {
		Not bool `json:"not,omitempty"`
		dateTimeWire
	}{Not: m.Not, dateTimeWire: wire})
}

func nonEmpty(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

func derefString(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

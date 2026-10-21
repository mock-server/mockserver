using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace MockServer.Client.Models;

/// <summary>
/// A single matcher value for a header, query-string parameter, cookie or path parameter.
/// </summary>
/// <remarks>
/// MockServer's plain-string wire form encodes negation and optionality as leading markers, and the
/// server strips them unconditionally when reading. A value whose own first character is
/// <c>'!'</c> or <c>'?'</c> therefore cannot be sent as a bare string: "header X is exactly
/// <c>!foo</c>" would be read back as "header X is anything but <c>foo</c>" — which matches almost
/// every request, so the expectation silently passes for the wrong reason instead of failing loudly.
/// <para>
/// <see cref="MatcherValue"/> keeps the value and the flags apart, and falls back to the object form
/// (<c>{"not":false,"value":"!foo"}</c>) only when the plain form would be misread. Everything else
/// stays byte-identical on the wire, so existing expectations are unaffected. Use
/// <see cref="Literal"/> for an exact value and <see cref="NotLiteral"/> to negate one:
/// </para>
/// <code>
/// HttpRequest.Request().WithHeaderMatcher("X-Tag", MatcherValue.Literal("!foo"));   // X-Tag IS "!foo"
/// HttpRequest.Request().WithHeaderMatcher("X-Tag", MatcherValue.NotLiteral("foo")); // X-Tag is NOT "foo"
/// </code>
/// This mirrors the escape the Java client already had and the Go client gained.
/// <para>
/// A matcher value can instead be a date-time matcher, which has no plain-string form and is always
/// sent as an object (<c>{"after":"now -1 hours"}</c>). Build one with <see cref="Before"/>,
/// <see cref="After"/> or <see cref="EqualToDateTime"/> and refine it with the <c>With*</c> methods,
/// each of which returns a new value:
/// </para>
/// <code>
/// HttpRequest.Request().WithHeaderMatcher("X-Issued-At", MatcherValue.After("now -1 hours"));
/// HttpRequest.Request().WithHeaderMatcher("If-Modified-Since",
///     MatcherValue.Before("2026-10-21T00:00:00Z").WithActualFormat("rfc1123"));
/// </code>
/// A plain string such as <c>"before:2026-01-01"</c> is still a literal, never a date-time matcher.
/// </remarks>
[JsonConverter(typeof(MatcherValueConverter))]
public readonly struct MatcherValue : IEquatable<MatcherValue>
{
    // The markers MockServer strips from the front of a plain matcher string:
    // '!' negates the matcher, '?' makes it optional.
    private const char NotChar = '!';
    private const char OptionalChar = '?';

    /// <summary>The matcher text, taken verbatim — markers inside it are not parsed.</summary>
    public string Value { get; }

    /// <summary>Negates the matcher (match anything except <see cref="Value"/>).</summary>
    public bool Not { get; }

    /// <summary>Marks the header / parameter / cookie as optional.</summary>
    public bool Optional { get; }

    /// <summary>
    /// The date-time comparison, or <c>null</c> when this is not a date-time matcher. Set only through
    /// <see cref="Before"/>, <see cref="After"/> or <see cref="EqualToDateTime"/>.
    /// </summary>
    public DateTimeMatchOperator? DateTimeOperator { get; }

    /// <summary>
    /// The expected date-time text for a date-time matcher: <c>now</c>, <c>now &lt;offset&gt;</c>, or an
    /// ISO-8601 / RFC-1123 / epoch date-time. The server validates it.
    /// </summary>
    public string? DateTimeExpected { get; }

    /// <summary>The format of the actual (request) value, for a date-time matcher; <c>null</c> when unset.</summary>
    public string? ActualFormat { get; }

    /// <summary>How the expected value is truncated, for a date-time matcher; <c>null</c> when unset.</summary>
    public string? TruncateExpected { get; }

    /// <summary>How the actual value is truncated, for a date-time matcher; <c>null</c> when unset.</summary>
    public string? TruncateActual { get; }

    /// <summary>Whether truncation is applied after the offset, for a date-time matcher; <c>null</c> when unset.</summary>
    public bool? ApplyTruncationLast { get; }

    /// <summary>The time zone used to interpret the values, for a date-time matcher; <c>null</c> when unset.</summary>
    public string? Zone { get; }

    /// <summary>True when this is a date-time matcher, which is always sent in its object form.</summary>
    public bool IsDateTime => DateTimeOperator.HasValue;

    /// <summary>Creates a matcher value from explicit fields.</summary>
    public MatcherValue(string value, bool not = false, bool optional = false)
        : this(value, not, optional, null, null, null, null, null, null, null)
    {
    }

    private MatcherValue(
        string value,
        bool not,
        bool optional,
        DateTimeMatchOperator? dateTimeOperator,
        string? dateTimeExpected,
        string? actualFormat,
        string? truncateExpected,
        string? truncateActual,
        bool? applyTruncationLast,
        string? zone)
    {
        Value = value ?? string.Empty;
        Not = not;
        Optional = optional;
        DateTimeOperator = dateTimeOperator;
        DateTimeExpected = dateTimeExpected;
        ActualFormat = actualFormat;
        TruncateExpected = truncateExpected;
        TruncateActual = truncateActual;
        ApplyTruncationLast = applyTruncationLast;
        Zone = zone;
    }

    internal static MatcherValue CreateDateTime(
        DateTimeMatchOperator dateTimeOperator,
        string expected,
        bool not,
        string? actualFormat,
        string? truncateExpected,
        string? truncateActual,
        bool? applyTruncationLast,
        string? zone) =>
        new(string.Empty, not, false, dateTimeOperator, expected ?? throw new ArgumentNullException(nameof(expected)),
            actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone);

    /// <summary>A matcher for exactly <paramref name="value"/>, even when it starts with <c>'!'</c> or <c>'?'</c>.</summary>
    public static MatcherValue Literal(string value) => new(value);

    /// <summary>A matcher that matches anything except exactly <paramref name="value"/>.</summary>
    public static MatcherValue NotLiteral(string value) => new(value, not: true);

    /// <summary>A matcher for exactly <paramref name="value"/> that need not be present.</summary>
    public static MatcherValue OptionalLiteral(string value) => new(value, optional: true);

    /// <summary>A date-time matcher: the actual value must be before <paramref name="expected"/>.</summary>
    public static MatcherValue Before(string expected) =>
        CreateDateTime(DateTimeMatchOperator.Before, expected, false, null, null, null, null, null);

    /// <summary>A date-time matcher: the actual value must be after <paramref name="expected"/>.</summary>
    public static MatcherValue After(string expected) =>
        CreateDateTime(DateTimeMatchOperator.After, expected, false, null, null, null, null, null);

    /// <summary>A date-time matcher: the actual value must equal <paramref name="expected"/>.</summary>
    public static MatcherValue EqualToDateTime(string expected) =>
        CreateDateTime(DateTimeMatchOperator.EqualToDateTime, expected, false, null, null, null, null, null);

    /// <summary>Returns a copy of this date-time matcher with the actual value's format set.</summary>
    /// <exception cref="InvalidOperationException">This is not a date-time matcher.</exception>
    public MatcherValue WithActualFormat(string actualFormat) =>
        RequireDateTime(nameof(WithActualFormat)).CopyDateTime(actualFormat: actualFormat);

    /// <summary>Returns a copy of this date-time matcher with the expected value's truncation set.</summary>
    /// <exception cref="InvalidOperationException">This is not a date-time matcher.</exception>
    public MatcherValue WithTruncateExpected(string truncateExpected) =>
        RequireDateTime(nameof(WithTruncateExpected)).CopyDateTime(truncateExpected: truncateExpected);

    /// <summary>Returns a copy of this date-time matcher with the actual value's truncation set.</summary>
    /// <exception cref="InvalidOperationException">This is not a date-time matcher.</exception>
    public MatcherValue WithTruncateActual(string truncateActual) =>
        RequireDateTime(nameof(WithTruncateActual)).CopyDateTime(truncateActual: truncateActual);

    /// <summary>Returns a copy of this date-time matcher that applies truncation after (or before) the offset.</summary>
    /// <exception cref="InvalidOperationException">This is not a date-time matcher.</exception>
    public MatcherValue WithApplyTruncationLast(bool applyTruncationLast) =>
        RequireDateTime(nameof(WithApplyTruncationLast)).CopyDateTime(applyTruncationLast: applyTruncationLast);

    /// <summary>Returns a copy of this date-time matcher with the time zone set.</summary>
    /// <exception cref="InvalidOperationException">This is not a date-time matcher.</exception>
    public MatcherValue WithZone(string zone) =>
        RequireDateTime(nameof(WithZone)).CopyDateTime(zone: zone);

    /// <summary>Returns a copy of this matcher with its negation set.</summary>
    public MatcherValue WithNot(bool not) =>
        new(Value, not, Optional, DateTimeOperator, DateTimeExpected, ActualFormat, TruncateExpected, TruncateActual,
            ApplyTruncationLast, Zone);

    private MatcherValue RequireDateTime(string method)
    {
        if (!IsDateTime)
        {
            throw new InvalidOperationException(
                $"{method} applies only to a date-time matcher; start from MatcherValue.Before, After or EqualToDateTime");
        }
        return this;
    }

    private MatcherValue CopyDateTime(
        string? actualFormat = null,
        string? truncateExpected = null,
        string? truncateActual = null,
        bool? applyTruncationLast = null,
        string? zone = null) =>
        new(Value, Not, Optional, DateTimeOperator, DateTimeExpected,
            actualFormat ?? ActualFormat,
            truncateExpected ?? TruncateExpected,
            truncateActual ?? TruncateActual,
            applyTruncationLast ?? ApplyTruncationLast,
            zone ?? Zone);

    /// <summary>
    /// Parses a plain wire string into a <see cref="MatcherValue"/> exactly as the server would, so an
    /// existing <c>"!foo"</c> keeps meaning "not foo". The meaning is preserved; only the representation
    /// changes from an encoded string to explicit fields. Enables a plain string to stand in for a
    /// <see cref="MatcherValue"/>.
    /// </summary>
    public static implicit operator MatcherValue(string value) => ParsePlain(value);

    /// <summary>
    /// Renders the plain-string form, matching the server's <c>NottableString.serialise()</c>: an
    /// optional marker, then a not marker, then the value (a blank value contributes nothing). A
    /// date-time matcher has no plain form; it is always written as an object.
    /// </summary>
    public string Serialise()
    {
        var builder = new StringBuilder();
        if (Optional)
        {
            builder.Append(OptionalChar);
        }
        if (Not)
        {
            builder.Append(NotChar);
        }
        if (!string.IsNullOrWhiteSpace(Value))
        {
            builder.Append(Value);
        }
        return builder.ToString();
    }

    /// <summary>
    /// Mirrors the server's <c>NottableString.string(String)</c>: strip an optional marker, then a not
    /// marker, then an optional marker again.
    /// </summary>
    public static MatcherValue ParsePlain(string? s)
    {
        var value = s ?? string.Empty;
        var not = false;
        var optional = false;
        if (!string.IsNullOrWhiteSpace(value))
        {
            if (value.Length > 0 && value[0] == OptionalChar)
            {
                optional = true;
                value = value.Substring(1);
            }
            if (value.Length > 0 && value[0] == NotChar)
            {
                not = true;
                value = value.Substring(1);
            }
            if (value.Length > 0 && value[0] == OptionalChar)
            {
                optional = true;
                value = value.Substring(1);
            }
        }
        return new MatcherValue(value, not, optional);
    }

    /// <summary>
    /// Reports whether re-reading the plain form would change the value, the negation or the
    /// optionality. Decided by actually re-parsing rather than by testing for a leading marker, so it
    /// stays correct for compound markers (<c>"?!"</c>, <c>"!?"</c>) and any marker added later. A
    /// blank value is never ambiguous: it has no marker to misread, and the server's object-form reader
    /// ignores a blank <c>value</c>, so escaping one would silently drop the matcher. A date-time
    /// matcher is always ambiguous, because the plain form cannot carry it at all.
    /// </summary>
    public bool IsAmbiguous()
    {
        if (IsDateTime)
        {
            return true;
        }
        if (string.IsNullOrWhiteSpace(Value))
        {
            return false;
        }
        var reparsed = ParsePlain(Serialise());
        return reparsed.Not != Not || reparsed.Optional != Optional || reparsed.Value != Value;
    }

    /// <inheritdoc/>
    public bool Equals(MatcherValue other) =>
        Not == other.Not && Optional == other.Optional && string.Equals(Value, other.Value, StringComparison.Ordinal)
        && DateTimeOperator == other.DateTimeOperator
        && string.Equals(DateTimeExpected, other.DateTimeExpected, StringComparison.Ordinal)
        && string.Equals(ActualFormat, other.ActualFormat, StringComparison.Ordinal)
        && string.Equals(TruncateExpected, other.TruncateExpected, StringComparison.Ordinal)
        && string.Equals(TruncateActual, other.TruncateActual, StringComparison.Ordinal)
        && ApplyTruncationLast == other.ApplyTruncationLast
        && string.Equals(Zone, other.Zone, StringComparison.Ordinal);

    /// <inheritdoc/>
    public override bool Equals(object? obj) => obj is MatcherValue other && Equals(other);

    /// <inheritdoc/>
    public override int GetHashCode()
    {
        unchecked
        {
            var hash = Value is null ? 0 : StringComparer.Ordinal.GetHashCode(Value);
            hash = (hash * 397) ^ Not.GetHashCode();
            hash = (hash * 397) ^ Optional.GetHashCode();
            hash = (hash * 397) ^ DateTimeOperator.GetHashCode();
            hash = (hash * 397) ^ (DateTimeExpected is null ? 0 : StringComparer.Ordinal.GetHashCode(DateTimeExpected));
            hash = (hash * 397) ^ (ActualFormat is null ? 0 : StringComparer.Ordinal.GetHashCode(ActualFormat));
            hash = (hash * 397) ^ (TruncateExpected is null ? 0 : StringComparer.Ordinal.GetHashCode(TruncateExpected));
            hash = (hash * 397) ^ (TruncateActual is null ? 0 : StringComparer.Ordinal.GetHashCode(TruncateActual));
            hash = (hash * 397) ^ ApplyTruncationLast.GetHashCode();
            hash = (hash * 397) ^ (Zone is null ? 0 : StringComparer.Ordinal.GetHashCode(Zone));
            return hash;
        }
    }

    public static bool operator ==(MatcherValue left, MatcherValue right) => left.Equals(right);

    public static bool operator !=(MatcherValue left, MatcherValue right) => !left.Equals(right);

    /// <summary>Returns the plain-string form, or the JSON object form for a date-time matcher, for logging.</summary>
    public override string ToString() => IsDateTime ? JsonSerializer.Serialize(this) : Serialise();
}

/// <summary>The comparison a date-time <see cref="MatcherValue"/> makes against its expected value.</summary>
public enum DateTimeMatchOperator
{
    /// <summary>The actual value is before the expected value (wire field <c>before</c>).</summary>
    Before,

    /// <summary>The actual value is after the expected value (wire field <c>after</c>).</summary>
    After,

    /// <summary>The actual value equals the expected value (wire field <c>equalToDateTime</c>).</summary>
    EqualToDateTime
}

/// <summary>
/// <see cref="System.Text.Json"/> converter for <see cref="MatcherValue"/>. Writes the plain string
/// when it round-trips and the object form (<c>{"not":false,"value":"!foo"}</c>) when it would not;
/// reads both the plain-string and the object form. A date-time matcher is always written and read as
/// its own object form (<c>{"after":"now -1 hours","zone":"UTC"}</c>).
/// </summary>
public sealed class MatcherValueConverter : JsonConverter<MatcherValue>
{
    private static readonly (string Name, DateTimeMatchOperator Operator)[] DateTimeOperators =
    {
        ("before", DateTimeMatchOperator.Before),
        ("after", DateTimeMatchOperator.After),
        ("equalToDateTime", DateTimeMatchOperator.EqualToDateTime)
    };

    public override MatcherValue Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        switch (reader.TokenType)
        {
            case JsonTokenType.String:
                return MatcherValue.ParsePlain(reader.GetString());
            case JsonTokenType.Null:
                return MatcherValue.Literal(string.Empty);
            case JsonTokenType.StartObject:
                using (var doc = JsonDocument.ParseValue(ref reader))
                {
                    var root = doc.RootElement;
                    if (TryReadDateTime(root, out var dateTime))
                    {
                        return dateTime;
                    }
                    var value = root.TryGetProperty("value", out var v) && v.ValueKind == JsonValueKind.String
                        ? v.GetString()!
                        : string.Empty;
                    var not = root.TryGetProperty("not", out var n) && n.ValueKind == JsonValueKind.True;
                    var optional = root.TryGetProperty("optional", out var o) && o.ValueKind == JsonValueKind.True;
                    return new MatcherValue(value, not, optional);
                }
            default:
                throw new JsonException($"Unexpected token {reader.TokenType} reading a MatcherValue");
        }
    }

    public override void Write(Utf8JsonWriter writer, MatcherValue value, JsonSerializerOptions options)
    {
        if (value.IsDateTime)
        {
            WriteDateTime(writer, value);
            return;
        }

        if (!value.IsAmbiguous())
        {
            writer.WriteStringValue(value.Serialise());
            return;
        }

        // The object property names are the literal wire names ("not"/"optional"/"value"), independent
        // of any camelCase policy. "not" is written even when false so the intent is unmistakable to a
        // reader and to the other clients, rather than relying on absent-means-false.
        writer.WriteStartObject();
        writer.WriteBoolean("not", value.Not);
        if (value.Optional)
        {
            writer.WriteBoolean("optional", value.Optional);
        }
        writer.WriteString("value", value.Value);
        writer.WriteEndObject();
    }

    // Field order and presence mirror the server's NottableStringSerializer.writeDateTime: "not" only
    // when true, every option only when set, and never a null.
    private static void WriteDateTime(Utf8JsonWriter writer, MatcherValue value)
    {
        writer.WriteStartObject();
        if (value.Not)
        {
            writer.WriteBoolean("not", true);
        }
        writer.WriteString(OperatorName(value.DateTimeOperator!.Value), value.DateTimeExpected ?? string.Empty);
        WriteIfSet(writer, "actualFormat", value.ActualFormat);
        WriteIfSet(writer, "truncateExpected", value.TruncateExpected);
        WriteIfSet(writer, "truncateActual", value.TruncateActual);
        if (value.ApplyTruncationLast.HasValue)
        {
            writer.WriteBoolean("applyTruncationLast", value.ApplyTruncationLast.Value);
        }
        WriteIfSet(writer, "zone", value.Zone);
        writer.WriteEndObject();
    }

    private static void WriteIfSet(Utf8JsonWriter writer, string name, string? value)
    {
        if (value is not null)
        {
            writer.WriteString(name, value);
        }
    }

    private static string OperatorName(DateTimeMatchOperator dateTimeOperator)
    {
        foreach (var candidate in DateTimeOperators)
        {
            if (candidate.Operator == dateTimeOperator)
            {
                return candidate.Name;
            }
        }
        throw new JsonException($"Unknown date-time operator {dateTimeOperator}");
    }

    // A date-time object carries exactly one operator and never "value", "schema" or "optional"; the
    // server rejects anything else, so such a shape is reported rather than silently reinterpreted.
    private static readonly string[] DateTimeOptionNames =
        { "actualFormat", "truncateExpected", "truncateActual", "applyTruncationLast", "zone" };

    private static bool TryReadDateTime(JsonElement root, out MatcherValue dateTime)
    {
        dateTime = default;
        DateTimeMatchOperator? found = null;
        string? expected = null;
        foreach (var candidate in DateTimeOperators)
        {
            if (!root.TryGetProperty(candidate.Name, out var element) || element.ValueKind == JsonValueKind.Null)
            {
                continue;
            }
            if (found.HasValue)
            {
                throw new JsonException("A date-time matcher must have exactly one of before, after or equalToDateTime");
            }
            if (element.ValueKind != JsonValueKind.String)
            {
                throw new JsonException($"Date-time matcher field \"{candidate.Name}\" must be a string");
            }
            found = candidate.Operator;
            expected = element.GetString();
        }

        if (!found.HasValue)
        {
            foreach (var option in DateTimeOptionNames)
            {
                if (IsPresent(root, option))
                {
                    // the server rejects this shape too; reading it as a plain value would drop the option
                    throw new JsonException($"Date-time matcher option \"{option}\" needs one of before, after or equalToDateTime");
                }
            }
            return false;
        }
        if (IsPresent(root, "value") || IsPresent(root, "schema")
            || (root.TryGetProperty("optional", out var optional) && optional.ValueKind == JsonValueKind.True))
        {
            throw new JsonException("A date-time matcher cannot be combined with value, schema or optional");
        }

        dateTime = MatcherValue.CreateDateTime(
            found.Value,
            expected!,
            root.TryGetProperty("not", out var not) && not.ValueKind == JsonValueKind.True,
            ReadString(root, "actualFormat"),
            ReadString(root, "truncateExpected"),
            ReadString(root, "truncateActual"),
            ReadBoolean(root, "applyTruncationLast"),
            ReadString(root, "zone"));
        return true;
    }

    private static bool IsPresent(JsonElement root, string name) =>
        root.TryGetProperty(name, out var element) && element.ValueKind != JsonValueKind.Null;

    private static string? ReadString(JsonElement root, string name) =>
        root.TryGetProperty(name, out var element) && element.ValueKind == JsonValueKind.String
            ? element.GetString()
            : null;

    private static bool? ReadBoolean(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out var element))
        {
            return null;
        }
        return element.ValueKind switch
        {
            JsonValueKind.True => true,
            JsonValueKind.False => false,
            _ => null
        };
    }
}

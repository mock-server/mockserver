using System.Text.Json;

namespace MockServer.Client.Models;

/// <summary>
/// Fluent builder for <see cref="HttpTemplate"/>.
/// </summary>
public sealed class HttpTemplateBuilder
{
    private readonly HttpTemplate _template = new();

    internal HttpTemplateBuilder(TemplateType type)
    {
        _template.TemplateType = type;
    }

    /// <summary>
    /// Sets the inline template content.
    /// </summary>
    public HttpTemplateBuilder WithTemplate(string template)
    {
        _template.Template = template;
        return this;
    }

    /// <summary>
    /// Sets the path to a file containing the template (classpath or filesystem).
    /// The inline template, when present, takes precedence over the file.
    /// </summary>
    public HttpTemplateBuilder WithTemplateFile(string templateFile)
    {
        _template.TemplateFile = templateFile;
        return this;
    }

    /// <summary>
    /// Adds a value the template reads as <c>parameters.&lt;name&gt;</c>: text, a number, a boolean, a list, a
    /// dictionary or null.
    /// </summary>
    public HttpTemplateBuilder WithParameter(string name, object? value)
    {
        (_template.Parameters ??= new Dictionary<string, JsonElement>())[name] = JsonSerializer.SerializeToElement(value);
        return this;
    }

    /// <summary>Adds each entry as a parameter; see <see cref="WithParameter"/>.</summary>
    public HttpTemplateBuilder WithParameters(IDictionary<string, object?> parameters)
    {
        foreach (var parameter in parameters)
        {
            WithParameter(parameter.Key, parameter.Value);
        }
        return this;
    }

    public HttpTemplateBuilder WithDelay(TimeUnit timeUnit, long value)
    {
        _template.Delay = new Delay { TimeUnit = timeUnit, Value = value };
        return this;
    }

    /// <summary>Marks this as the primary action when the expectation configures more than one.</summary>
    public HttpTemplateBuilder WithPrimary(bool primary = true)
    {
        _template.Primary = primary;
        return this;
    }

    public HttpTemplate Build() => _template;

    /// <summary>
    /// Implicit conversion to HttpTemplate for ergonomic use.
    /// </summary>
    public static implicit operator HttpTemplate(HttpTemplateBuilder builder) => builder.Build();
}

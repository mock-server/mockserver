<?php

declare(strict_types=1);

namespace MockServer;

/**
 * Fluent builder for a template action (the {@code httpTemplate} shape).
 *
 * This one shape is served under two expectation action keys — as a response
 * template ({@code httpResponseTemplate}) or a forward/request template
 * ({@code httpForwardTemplate}) — so attach it with
 * {@see Expectation::httpResponseTemplate()} /
 * {@see Expectation::httpForwardTemplate()} or the fluent
 * {@see ForwardChainExpectation::respondWithTemplate()} /
 * {@see ForwardChainExpectation::forwardWithTemplate()}.
 *
 * Wire keys: delay, templateType, template, templateFile, primary,
 * responseOverride, parameters.
 *
 * NOTE — deferred field: {@code responseModifier} (a nested
 * headers/cookies add-replace-remove structure with recursively-nested
 * sub-modifiers) is NOT modelled by this builder. It is a distinct nested
 * sub-feature that no shared fixture exercises; it needs its own builder rather
 * than an opaque associative-array passthrough, and is left for a follow-up. A
 * template that needs it cannot be fully expressed by this class yet.
 *
 * @example
 *   HttpTemplate::template(HttpTemplate::MUSTACHE, '{"statusCode": 200, "body": "{{request.path}}"}');
 */
class HttpTemplate implements \JsonSerializable
{
    /** Apache Velocity template engine. */
    public const VELOCITY = 'VELOCITY';
    /** JavaScript (Nashorn/GraalJS) template engine. */
    public const JAVASCRIPT = 'JAVASCRIPT';
    /** Mustache template engine. */
    public const MUSTACHE = 'MUSTACHE';

    private ?Delay $delay = null;
    private ?string $templateType = null;
    private ?string $template = null;
    private ?string $templateFile = null;
    private ?bool $primary = null;
    private ?HttpResponse $responseOverride = null;
    /** @var array<string, mixed> */
    private array $parameters = [];

    /**
     * Static factory. The template body is rendered by the {@code templateType}
     * engine to produce the response (or forwarded request).
     *
     * @param string $templateType one of VELOCITY, JAVASCRIPT, MUSTACHE
     * @param string $template     the template body
     */
    public static function template(string $templateType, string $template): self
    {
        $httpTemplate = new self();
        $httpTemplate->templateType = strtoupper($templateType);
        $httpTemplate->template = $template;
        return $httpTemplate;
    }

    /**
     * Static factory for a template read from a file on the MockServer host,
     * rather than an inline body.
     *
     * @param string $templateType one of VELOCITY, JAVASCRIPT, MUSTACHE
     * @param string $templateFile path to the template file on the server
     */
    public static function templateFile(string $templateType, string $templateFile): self
    {
        $httpTemplate = new self();
        $httpTemplate->templateType = strtoupper($templateType);
        $httpTemplate->templateFile = $templateFile;
        return $httpTemplate;
    }

    public function delay(Delay $delay): self
    {
        $this->delay = $delay;
        return $this;
    }

    /**
     * @param string $templateType one of VELOCITY, JAVASCRIPT, MUSTACHE
     */
    public function templateType(string $templateType): self
    {
        $this->templateType = strtoupper($templateType);
        return $this;
    }

    public function withTemplate(string $template): self
    {
        $this->template = $template;
        return $this;
    }

    public function withTemplateFile(string $templateFile): self
    {
        $this->templateFile = $templateFile;
        return $this;
    }

    public function primary(bool $primary): self
    {
        $this->primary = $primary;
        return $this;
    }

    /**
     * Merge the rendered template output over this base response.
     */
    public function responseOverride(HttpResponse $responseOverride): self
    {
        $this->responseOverride = $responseOverride;
        return $this;
    }

    /**
     * Add a value the template reads as {@code parameters.<name>}, such as
     * {@code $parameters.greeting} (Velocity), {@code {{ parameters.greeting }}}
     * (Mustache) or {@code parameters.greeting} (JavaScript). A value is any JSON
     * value; pass an empty object as {@code new \stdClass()} so it is not written as [].
     */
    public function withParameter(string $name, mixed $value): self
    {
        $this->parameters[$name] = $value;
        return $this;
    }

    /**
     * Add each entry as a parameter; see {@see withParameter()}.
     *
     * @param array<string, mixed> $parameters
     */
    public function withParameters(array $parameters): self
    {
        foreach ($parameters as $name => $value) {
            $this->parameters[(string) $name] = $value;
        }
        return $this;
    }

    /**
     * @return array<string, mixed>
     */
    public function jsonSerialize(): array
    {
        return $this->toArray();
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(): array
    {
        $data = [];
        if ($this->delay !== null) {
            $data['delay'] = $this->delay->toArray();
        }
        if ($this->templateType !== null) {
            $data['templateType'] = $this->templateType;
        }
        if ($this->template !== null) {
            $data['template'] = $this->template;
        }
        if ($this->templateFile !== null) {
            $data['templateFile'] = $this->templateFile;
        }
        if ($this->primary !== null) {
            $data['primary'] = $this->primary;
        }
        if ($this->responseOverride !== null) {
            $data['responseOverride'] = $this->responseOverride->toArray();
        }
        if ($this->parameters !== []) {
            $data['parameters'] = $this->parameters;
        }
        return $data;
    }
}

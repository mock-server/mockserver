<?php

declare(strict_types=1);

namespace MockServer\Diagnostics;

/**
 * The recorded request a {@see RequestExplanation} explains.
 */
final class ExplainedRequest
{
    /**
     * @param string|null $timestamp When the request was recorded, as the event log formats it.
     * @param string|null $method The request method.
     * @param string|null $path The request path.
     * @param bool $bodyTruncated Whether the event log kept only the start of the body, so body matching used that copy.
     * @param string|null $bodyTruncatedNote The server's note on the truncated body, or null.
     */
    public function __construct(
        public readonly ?string $timestamp,
        public readonly ?string $method,
        public readonly ?string $path,
        public readonly bool $bodyTruncated,
        public readonly ?string $bodyTruncatedNote,
    ) {
    }

    /** @param array<string, mixed> $data */
    public static function fromArray(array $data): self
    {
        return new self(
            Fields::string($data, 'timestamp'),
            Fields::string($data, 'method'),
            Fields::string($data, 'path'),
            Fields::bool($data, 'bodyTruncated') ?? false,
            Fields::string($data, 'bodyTruncatedNote'),
        );
    }
}

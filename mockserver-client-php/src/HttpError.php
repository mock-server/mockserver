<?php

declare(strict_types=1);

namespace MockServer;

/**
 * Fluent builder for an HTTP error action.
 *
 * @example
 *   $error = HttpError::error()
 *       ->dropConnection(true)
 *       ->responseBytes(base64_encode("garbage"));
 *
 * Wire keys: dropConnection, resetConnection, responseBytes, streamError, primary.
 */
class HttpError implements \JsonSerializable
{
    private ?bool $dropConnection = null;
    private ?bool $resetConnection = null;
    private ?string $responseBytes = null;
    private ?int $streamError = null;
    private ?bool $primary = null;
    private ?Delay $delay = null;

    /**
     * Static factory for fluent construction.
     */
    public static function error(): self
    {
        return new self();
    }

    public function dropConnection(bool $dropConnection): self
    {
        $this->dropConnection = $dropConnection;
        return $this;
    }

    /**
     * Reset the connection with a TCP RST instead of returning a response, so the
     * client sees "connection reset by peer". Takes precedence over streamError
     * and dropConnection.
     */
    public function resetConnection(bool $resetConnection): self
    {
        $this->resetConnection = $resetConnection;
        return $this;
    }

    /**
     * @param string $responseBytes Base64-encoded bytes to return before dropping
     */
    public function responseBytes(string $responseBytes): self
    {
        $this->responseBytes = $responseBytes;
        return $this;
    }

    /**
     * Reset the matched request stream with this error code instead of
     * returning a response (HTTP/2 RST_STREAM, HTTP/3 RESET_STREAM). HTTP/1.1
     * has no stream concept, so there it falls back to dropping the connection.
     *
     * @param int $streamError protocol-level stream error code, e.g. 2 (INTERNAL_ERROR)
     */
    public function streamError(int $streamError): self
    {
        $this->streamError = $streamError;
        return $this;
    }

    /**
     * Apply a delay before the error is produced.
     */
    public function delay(Delay $delay): self
    {
        $this->delay = $delay;
        return $this;
    }

    /**
     * Mark this action as the primary one for the expectation.
     *
     * Only meaningful when an expectation carries more than one action: the
     * server requires exactly one to be marked primary and rejects the
     * expectation otherwise. Omitting it from a re-submitted expectation can
     * silently change which action executes.
     */
    public function primary(bool $primary): self
    {
        $this->primary = $primary;
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

        if ($this->dropConnection !== null) {
            $data['dropConnection'] = $this->dropConnection;
        }
        if ($this->resetConnection !== null) {
            $data['resetConnection'] = $this->resetConnection;
        }
        if ($this->responseBytes !== null) {
            $data['responseBytes'] = $this->responseBytes;
        }
        if ($this->streamError !== null) {
            $data['streamError'] = $this->streamError;
        }
        if ($this->delay !== null) {
            $data['delay'] = $this->delay->toArray();
        }
        if ($this->primary !== null) {
            $data['primary'] = $this->primary;
        }

        return $data;
    }
}

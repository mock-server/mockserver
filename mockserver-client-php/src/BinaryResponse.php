<?php

declare(strict_types=1);

namespace MockServer;

/**
 * Fluent builder for a raw binary (TCP/binary-proxy) response action.
 *
 * Produces the {@code binaryResponse} action JSON. Wire keys:
 * binaryData (base64-encoded), delay, primary, upstream.
 *
 * @example
 *   BinaryResponse::response()->fromBytes("\x00\x01\x02");
 */
class BinaryResponse implements \JsonSerializable
{
    /** Write the binary data; do not forward the message (the server's default). */
    public const ANSWER_ONLY = 'ANSWER_ONLY';

    /** Write the binary data, forward the message and drop the upstream's reply. */
    public const ANSWER_AND_FORWARD = 'ANSWER_AND_FORWARD';

    /** Forward the message and write the binary data in place of the upstream's reply. */
    public const FORWARD_AND_REPLACE = 'FORWARD_AND_REPLACE';

    private ?string $binaryData = null;
    private ?Delay $delay = null;
    private ?bool $primary = null;
    private ?string $upstream = null;

    public static function response(): self
    {
        return new self();
    }

    /**
     * Set the response payload from raw bytes (base64-encoded on the wire).
     *
     * @param string $bytes Raw binary data
     */
    public function fromBytes(string $bytes): self
    {
        $this->binaryData = base64_encode($bytes);
        return $this;
    }

    /**
     * Set the response payload from already base64-encoded data.
     */
    public function binaryData(string $base64): self
    {
        $this->binaryData = $base64;
        return $this;
    }

    public function delay(Delay $delay): self
    {
        $this->delay = $delay;
        return $this;
    }

    public function primary(bool $primary): self
    {
        $this->primary = $primary;
        return $this;
    }

    /**
     * What happens upstream to the matched message on a connection MockServer
     * relays to an upstream (matchBinaryExpectationsWhenProxying). The last
     * two values need binaryMessageFraming POSTGRESQL on the server.
     *
     * @param string $upstream ANSWER_ONLY, ANSWER_AND_FORWARD or FORWARD_AND_REPLACE
     */
    public function upstream(string $upstream): self
    {
        $this->upstream = $upstream;
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
        if ($this->binaryData !== null) {
            $data['binaryData'] = $this->binaryData;
        }
        if ($this->delay !== null) {
            $data['delay'] = $this->delay->toArray();
        }
        if ($this->primary !== null) {
            $data['primary'] = $this->primary;
        }
        if ($this->upstream !== null) {
            $data['upstream'] = $this->upstream;
        }
        return $data;
    }
}

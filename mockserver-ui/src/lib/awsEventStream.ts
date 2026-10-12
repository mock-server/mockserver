/**
 * Decoder for the AWS event-stream binary framing
 * (`application/vnd.amazon.eventstream`) that Bedrock Runtime uses for
 * `InvokeModelWithResponseStream` and `ConverseStream` responses.
 *
 * Each message is: total length (u32 BE), headers length (u32 BE), prelude
 * CRC32, headers, payload, message CRC32. Decoding is defensive: it stops at the
 * first frame that is truncated or fails its message CRC (which also covers the
 * prelude) and returns what came before, so a capture cut off mid-frame still
 * yields its complete frames.
 */

export interface AwsEventStreamMessage {
  headers: Record<string, string>;
  payload: Uint8Array;
}

const PRELUDE_LENGTH = 12;
const MESSAGE_CRC_LENGTH = 4;
const MIN_MESSAGE_LENGTH = PRELUDE_LENGTH + MESSAGE_CRC_LENGTH;

let crcTable: Uint32Array | null = null;

function crc32(bytes: Uint8Array, start: number, end: number): number {
  if (!crcTable) {
    crcTable = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      crcTable[n] = c >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (let i = start; i < end; i++) crc = crcTable[(crc ^ bytes[i]!) & 0xff]! ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

/**
 * Header value byte lengths by type id; -1 means a u16-length-prefixed value
 * (6 = byte array, 7 = string). Types 0 and 1 are boolean true/false with no value.
 */
const FIXED_HEADER_VALUE_LENGTHS: Record<number, number> = {
  0: 0, 1: 0, 2: 1, 3: 2, 4: 4, 5: 8, 6: -1, 7: -1, 8: 8, 9: 16,
};

function decodeHeaders(view: DataView, bytes: Uint8Array, start: number, end: number): Record<string, string> | null {
  const headers: Record<string, string> = {};
  const decoder = new TextDecoder();
  let pos = start;
  while (pos < end) {
    const nameLength = bytes[pos]!;
    pos += 1;
    if (pos + nameLength + 1 > end) return null;
    const name = decoder.decode(bytes.subarray(pos, pos + nameLength));
    pos += nameLength;
    const type = bytes[pos]!;
    pos += 1;
    const fixed = FIXED_HEADER_VALUE_LENGTHS[type];
    if (fixed === undefined) return null;
    if (fixed >= 0) {
      if (pos + fixed > end) return null;
      pos += fixed;
      continue;
    }
    if (pos + 2 > end) return null;
    const valueLength = view.getUint16(pos);
    pos += 2;
    if (pos + valueLength > end) return null;
    if (type === 7) headers[name] = decoder.decode(bytes.subarray(pos, pos + valueLength));
    pos += valueLength;
  }
  return headers;
}

/**
 * Decode concatenated event-stream messages. Returns an empty array when the
 * bytes do not start with a valid frame (so non-event-stream bodies are ignored).
 */
export function decodeAwsEventStream(bytes: Uint8Array): AwsEventStreamMessage[] {
  const messages: AwsEventStreamMessage[] = [];
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let offset = 0;
  while (bytes.length - offset >= MIN_MESSAGE_LENGTH) {
    const totalLength = view.getUint32(offset);
    const headersLength = view.getUint32(offset + 4);
    if (
      totalLength < MIN_MESSAGE_LENGTH ||
      offset + totalLength > bytes.length ||
      headersLength > totalLength - MIN_MESSAGE_LENGTH ||
      view.getUint32(offset + totalLength - MESSAGE_CRC_LENGTH) !==
        crc32(bytes, offset, offset + totalLength - MESSAGE_CRC_LENGTH)
    ) {
      break;
    }
    const headersStart = offset + PRELUDE_LENGTH;
    const headers = decodeHeaders(view, bytes, headersStart, headersStart + headersLength);
    if (!headers) break;
    messages.push({
      headers,
      payload: bytes.slice(headersStart + headersLength, offset + totalLength - MESSAGE_CRC_LENGTH),
    });
    offset += totalLength;
  }
  return messages;
}

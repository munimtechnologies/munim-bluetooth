/**
 * Byte helpers for the hex-string APIs. They use lookup tables instead of
 * per-byte string formatting/parsing, which matters for high-rate data
 * (notifications, L2CAP, Classic) that still flows through hex strings.
 */

const HEX_DIGITS = '0123456789abcdef'
const BYTE_TO_HEX: string[] = new Array(256)
for (let i = 0; i < 256; i++) {
  BYTE_TO_HEX[i] = HEX_DIGITS[i >> 4]! + HEX_DIGITS[i & 0x0f]!
}

// 0xff marks a character that is not a hex digit.
const HEX_TO_NIBBLE = new Uint8Array(128).fill(0xff)
for (let i = 0; i < 10; i++) HEX_TO_NIBBLE[48 + i] = i
for (let i = 0; i < 6; i++) {
  HEX_TO_NIBBLE[65 + i] = 10 + i
  HEX_TO_NIBBLE[97 + i] = 10 + i
}

export type BytesLike = ArrayBuffer | ArrayBufferView | ArrayLike<number>

function toUint8Array(value: BytesLike): Uint8Array {
  if (value instanceof Uint8Array) return value
  if (value instanceof ArrayBuffer) return new Uint8Array(value)
  if (ArrayBuffer.isView(value)) {
    return new Uint8Array(value.buffer, value.byteOffset, value.byteLength)
  }
  return Uint8Array.from(value)
}

/** Encode bytes as a lowercase hex string ("" for no bytes). */
export function bytesToHex(value: BytesLike): string {
  const bytes = toUint8Array(value)
  let out = ''
  for (let i = 0; i < bytes.length; i++) {
    out += BYTE_TO_HEX[bytes[i]!]
  }
  return out
}

/**
 * Decode a hex string (upper or lower case, optional spaces) into bytes.
 * Throws a TypeError for odd lengths or non-hex characters.
 */
export function hexToBytes(hex: string): Uint8Array {
  const clean = hex.indexOf(' ') >= 0 ? hex.replace(/ /g, '') : hex
  if (clean.length % 2 !== 0) {
    throw new TypeError('Hex string must have an even number of digits')
  }
  const out = new Uint8Array(clean.length / 2)
  for (let i = 0, j = 0; i < out.length; i++, j += 2) {
    const hi = clean.charCodeAt(j)
    const lo = clean.charCodeAt(j + 1)
    const h = hi < 128 ? HEX_TO_NIBBLE[hi]! : 0xff
    const l = lo < 128 ? HEX_TO_NIBBLE[lo]! : 0xff
    if (h === 0xff || l === 0xff) {
      throw new TypeError(`Invalid hex digit near index ${j}`)
    }
    out[i] = (h << 4) | l
  }
  return out
}

/**
 * Return an ArrayBuffer holding exactly `value`'s bytes, copying only when
 * the input is a view over part of a larger buffer (or not a buffer at all).
 */
export function toArrayBuffer(value: BytesLike): ArrayBuffer {
  if (value instanceof ArrayBuffer) return value
  const bytes = toUint8Array(value)
  if (
    bytes.byteOffset === 0 &&
    bytes.byteLength === bytes.buffer.byteLength &&
    bytes.buffer instanceof ArrayBuffer
  ) {
    return bytes.buffer
  }
  return bytes.slice().buffer as ArrayBuffer
}

import { bytesToHex, hexToBytes, toArrayBuffer } from '../src/bytes'

describe('bytesToHex', () => {
  it('encodes every byte value as two lowercase digits', () => {
    const all = Uint8Array.from({ length: 256 }, (_, i) => i)
    const hex = bytesToHex(all)
    expect(hex).toHaveLength(512)
    for (let i = 0; i < 256; i++) {
      expect(hex.slice(i * 2, i * 2 + 2)).toBe(i.toString(16).padStart(2, '0'))
    }
  })

  it('accepts ArrayBuffer, views over part of a buffer, and plain arrays', () => {
    const buffer = Uint8Array.from([1, 2, 3, 4]).buffer
    expect(bytesToHex(buffer)).toBe('01020304')
    expect(bytesToHex(new Uint8Array(buffer, 1, 2))).toBe('0203')
    expect(bytesToHex(new DataView(buffer, 2))).toBe('0304')
    expect(bytesToHex([0xde, 0xad, 0xbe, 0xef])).toBe('deadbeef')
    expect(bytesToHex(new ArrayBuffer(0))).toBe('')
  })
})

describe('hexToBytes', () => {
  it('round-trips with bytesToHex', () => {
    const bytes = Uint8Array.from({ length: 1000 }, (_, i) => (i * 37) & 0xff)
    expect(hexToBytes(bytesToHex(bytes))).toEqual(bytes)
  })

  it('accepts upper case and spaces', () => {
    expect(Array.from(hexToBytes('DE ad Be EF'))).toEqual([0xde, 0xad, 0xbe, 0xef])
  })

  it('rejects odd lengths and non-hex characters', () => {
    expect(() => hexToBytes('abc')).toThrow(TypeError)
    expect(() => hexToBytes('zz')).toThrow(TypeError)
    expect(() => hexToBytes('0é')).toThrow(TypeError)
  })
})

describe('toArrayBuffer', () => {
  it('returns an ArrayBuffer unchanged', () => {
    const buffer = new ArrayBuffer(4)
    expect(toArrayBuffer(buffer)).toBe(buffer)
  })

  it('reuses a full-length view buffer and copies partial views', () => {
    const full = new Uint8Array([1, 2, 3])
    expect(toArrayBuffer(full)).toBe(full.buffer)

    const partial = new Uint8Array(full.buffer, 1, 2)
    const copy = toArrayBuffer(partial)
    expect(copy).not.toBe(full.buffer)
    expect(Array.from(new Uint8Array(copy))).toEqual([2, 3])
  })

  it('converts number arrays', () => {
    expect(Array.from(new Uint8Array(toArrayBuffer([9, 8])))).toEqual([9, 8])
  })
})

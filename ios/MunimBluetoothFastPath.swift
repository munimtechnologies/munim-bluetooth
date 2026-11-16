//
//  MunimBluetoothFastPath.swift
//  munim-bluetooth
//
//  Hex codec and native batching for the high-rate data paths.
//

import Foundation
import NitroModules

/// Table-driven hex codec. `String(format: "%02x")` per byte (the previous
/// approach) allocated and parsed a format string for every byte of every
/// notification, scan record and stream chunk.
enum MunimHex {
    private static let digits: [UInt8] = Array("0123456789abcdef".utf8)

    static func encode(_ data: Data) -> String {
        if data.isEmpty { return "" }
        var out = [UInt8](repeating: 0, count: data.count * 2)
        var index = 0
        for byte in data {
            out[index] = digits[Int(byte >> 4)]
            out[index + 1] = digits[Int(byte & 0x0F)]
            index += 2
        }
        return String(decoding: out, as: UTF8.self)
    }

    /// Decodes like the original `hexStringToData`: surrounding whitespace
    /// is trimmed, an odd digit count gets a leading zero, and an empty or
    /// invalid string yields nil.
    static func decode(_ hex: String) -> Data? {
        var chars = Array(hex.trimmingCharacters(in: .whitespacesAndNewlines).utf8)
        if chars.isEmpty { return nil }
        if chars.count % 2 != 0 {
            chars.insert(UInt8(ascii: "0"), at: 0)
        }
        var out = Data(count: chars.count / 2)
        var valid = true
        out.withUnsafeMutableBytes { (buffer: UnsafeMutableRawBufferPointer) in
            var j = 0
            for i in 0..<buffer.count {
                guard let high = nibble(chars[j]), let low = nibble(chars[j + 1]) else {
                    valid = false
                    return
                }
                buffer[i] = (high << 4) | low
                j += 2
            }
        }
        return valid ? out : nil
    }

    private static func nibble(_ c: UInt8) -> UInt8? {
        switch c {
        case UInt8(ascii: "0")...UInt8(ascii: "9"): return c - UInt8(ascii: "0")
        case UInt8(ascii: "a")...UInt8(ascii: "f"): return c - UInt8(ascii: "a") + 10
        case UInt8(ascii: "A")...UInt8(ascii: "F"): return c - UInt8(ascii: "A") + 10
        default: return nil
        }
    }
}

extension ArrayBuffer {
    /// An owning ArrayBuffer holding a copy of `data`.
    static func munimCopy(_ data: Data) -> ArrayBuffer {
        let buffer = ArrayBuffer.allocate(size: data.count)
        if !data.isEmpty {
            data.copyBytes(to: buffer.data, count: data.count)
        }
        return buffer
    }

    /// Copies the bytes out. Call on the JS thread that passed the buffer:
    /// a JS-owned buffer must not be read later from another thread.
    func munimCopyToData() -> Data {
        if size == 0 { return Data() }
        return Data(bytes: data, count: size)
    }
}

/// Collects items on one serial queue and delivers them in batches.
/// `interval <= 0` delivers each item right away; with a `keyOf`, a batch
/// keeps the latest item per key in first-seen order. Not thread-safe:
/// call every method on `queue`.
final class MunimBatcher<T> {
    private let interval: TimeInterval
    private let maxBatchSize: Int
    private let queue: DispatchQueue
    private let keyOf: ((T) -> String)?
    private let deliver: ([T]) -> Void
    private var items: [T] = []
    private var keyIndex: [String: Int] = [:]
    private var generation = 0
    private var scheduled = false
    private var closed = false

    init(
        intervalMs: Double,
        maxBatchSize: Int,
        queue: DispatchQueue,
        keyOf: ((T) -> String)?,
        deliver: @escaping ([T]) -> Void
    ) {
        self.interval = max(intervalMs, 0) / 1_000
        self.maxBatchSize = maxBatchSize
        self.queue = queue
        self.keyOf = keyOf
        self.deliver = deliver
    }

    func add(_ item: T) {
        guard !closed else { return }
        if let key = keyOf?(item) {
            if let index = keyIndex[key] {
                items[index] = item
            } else {
                keyIndex[key] = items.count
                items.append(item)
            }
        } else {
            items.append(item)
        }
        if interval <= 0 || items.count >= maxBatchSize {
            flush()
        } else if !scheduled {
            scheduled = true
            let expected = generation
            queue.asyncAfter(deadline: .now() + interval) { [weak self] in
                guard let self, self.generation == expected else { return }
                self.flush()
            }
        }
    }

    func flush() {
        scheduled = false
        generation &+= 1
        guard !closed, !items.isEmpty else { return }
        let batch = items
        items = []
        keyIndex = [:]
        deliver(batch)
    }

    func close() {
        closed = true
        scheduled = false
        generation &+= 1
        items = []
        keyIndex = [:]
    }
}

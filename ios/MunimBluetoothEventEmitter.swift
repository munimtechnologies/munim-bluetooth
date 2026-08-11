//
//  MunimBluetoothEventEmitter.swift
//  munim-bluetooth
//
//  Event emitter for Bluetooth events
//

import Foundation
import React

@objc(MunimBluetoothEventEmitter)
class MunimBluetoothEventEmitter: RCTEventEmitter {

    private struct PendingEvent {
        let name: String
        let body: [String: Any]
        let approximateBytes: Int
        let coalescingKey: String?
    }

    public private(set) static var shared: MunimBluetoothEventEmitter?
    private static let stateLock = NSLock()
    private static var pendingEvents: [PendingEvent] = []
    private static var pendingEventBytes = 0
    private static let maxPendingEventCount = 256
    private static let maxPendingEventBytes = 1_048_576
    private var hasListeners = false

    override init() {
        super.init()
        Self.stateLock.lock()
        Self.shared = self
        Self.stateLock.unlock()
    }

    override func supportedEvents() -> [String]! {
        return [
            "deviceFound",
            "onDeviceFound",
            "scanResult",
            "scanFailed",
            "advertisingStartFailed",
            "advertisingStarted",
            "adapterStateChanged",
            "deviceConnected",
            "deviceDisconnected",
            "connectionStateChanged",
            "servicesDiscovered",
            "characteristicValueChanged",
            "mtuChanged",
            "phyChanged",
            "gattOperationCompleted",
            "bondStateChanged",
            "l2capChannelPublished",
            "l2capChannelPublishFailed",
            "l2capChannelUnpublished",
            "l2capChannelOpened",
            "l2capChannelOpenFailed",
            "l2capChannelClosed",
            "l2capDataReceived",
            "peripheralReadRequest",
            "peripheralWriteRequest",
            "peripheralExecuteWriteRequest",
            "peripheralSubscribed",
            "peripheralUnsubscribed",
            "rssiUpdated",
            "classicDeviceFound",
            "classicScanFailed",
            "classicScanFinished",
            "classicConnected",
            "classicDisconnected",
            "classicConnectionReceived",
            "classicServerStarted",
            "classicServerStopped",
            "classicDataReceived",
            "backgroundSessionStarted",
            "backgroundSessionStopped",
            "backgroundSessionRestored",
            "backgroundSessionStartFailed",
            "multipeerStarted",
            "multipeerStopped",
            "multipeerStartFailed",
            "multipeerPeerFound",
            "multipeerPeerLost",
            "multipeerPeerStateChanged",
            "multipeerInvitationReceived",
            "multipeerMessageReceived"
        ]
    }

    override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    override func startObserving() {
        let events: [PendingEvent]
        Self.stateLock.lock()
        hasListeners = true
        events = Self.pendingEvents
        Self.pendingEvents.removeAll(keepingCapacity: true)
        Self.pendingEventBytes = 0
        Self.stateLock.unlock()

        events.forEach { emitOnMain($0.name, body: $0.body) }
    }

    override func stopObserving() {
        Self.stateLock.lock()
        hasListeners = false
        Self.stateLock.unlock()
    }

    func emitDeviceFound(_ deviceData: [String: Any]) {
        emitOnMain("deviceFound", body: deviceData)
        emitOnMain("onDeviceFound", body: deviceData)
        emitOnMain("scanResult", body: deviceData)
    }

    func emit(_ eventName: String, body: [String: Any]) {
        Self.emitOrQueue(eventName, body: body)
    }

    static func emitOrQueue(_ eventName: String, body: [String: Any]) {
        let emitter: MunimBluetoothEventEmitter?
        stateLock.lock()
        if let shared, shared.hasListeners {
            emitter = shared
        } else {
            emitter = nil
            enqueue(eventName, body: body)
        }
        stateLock.unlock()

        emitter?.emitOnMain(eventName, body: body)
    }

    private func emitOnMain(_ eventName: String, body: [String: Any]) {
        if Thread.isMainThread {
            sendEvent(withName: eventName, body: body)
            return
        }

        DispatchQueue.main.async { [weak self] in
            self?.sendEvent(withName: eventName, body: body)
        }
    }

    // Must be called while stateLock is held.
    private static func enqueue(_ eventName: String, body: [String: Any]) {
        let bytes = approximateSize(of: body) + eventName.utf8.count
        guard bytes <= maxPendingEventBytes else {
            return
        }

        let key = coalescingKey(eventName: eventName, body: body)
        if let key,
           let existingIndex = pendingEvents.firstIndex(where: { $0.coalescingKey == key }) {
            pendingEventBytes -= pendingEvents[existingIndex].approximateBytes
            pendingEvents.remove(at: existingIndex)
        }

        while pendingEvents.count >= maxPendingEventCount ||
            pendingEventBytes + bytes > maxPendingEventBytes {
            if let index = pendingEvents.firstIndex(where: { $0.coalescingKey != nil }) ??
                pendingEvents.firstIndex(where: { isHighRateEvent($0.name) }) {
                pendingEventBytes -= pendingEvents[index].approximateBytes
                pendingEvents.remove(at: index)
            } else if isHighRateEvent(eventName) {
                return
            } else if !pendingEvents.isEmpty {
                pendingEventBytes -= pendingEvents.removeFirst().approximateBytes
            } else {
                return
            }
        }

        pendingEvents.append(PendingEvent(
            name: eventName,
            body: body,
            approximateBytes: bytes,
            coalescingKey: key
        ))
        pendingEventBytes += bytes
    }

    private static func coalescingKey(eventName: String, body: [String: Any]) -> String? {
        switch eventName {
        case "deviceFound", "onDeviceFound", "scanResult":
            guard let id = body["id"] as? String else { return nil }
            return "\(eventName)|\(id)"
        case "rssiUpdated":
            guard let id = body["deviceId"] as? String else { return nil }
            return "\(eventName)|\(id)"
        case "characteristicValueChanged":
            guard let deviceId = body["deviceId"] as? String,
                  let serviceUUID = body["serviceUUID"] as? String,
                  let characteristicUUID = body["characteristicUUID"] as? String else {
                return nil
            }
            return "\(eventName)|\(deviceId)|\(serviceUUID)|\(characteristicUUID)"
        default:
            return nil
        }
    }

    private static func isHighRateEvent(_ eventName: String) -> Bool {
        switch eventName {
        case "deviceFound", "onDeviceFound", "scanResult", "rssiUpdated",
             "characteristicValueChanged", "l2capDataReceived",
             "multipeerMessageReceived":
            return true
        default:
            return false
        }
    }

    private static func approximateSize(of value: Any) -> Int {
        switch value {
        case let string as String:
            return string.utf8.count
        case let data as Data:
            return data.count
        case let dictionary as [String: Any]:
            return dictionary.reduce(0) {
                $0 + $1.key.utf8.count + approximateSize(of: $1.value)
            }
        case let array as [Any]:
            return array.reduce(0) { $0 + approximateSize(of: $1) }
        case is NSNumber:
            return MemoryLayout<Double>.size
        case is NSNull:
            return 1
        default:
            return String(describing: value).utf8.count
        }
    }
}

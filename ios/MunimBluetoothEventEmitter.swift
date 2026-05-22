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

    public static var shared: MunimBluetoothEventEmitter?
    private static var pendingEvents: [(name: String, body: [String: Any])] = []
    private var hasListeners = false

    override init() {
        super.init()
        MunimBluetoothEventEmitter.shared = self
    }

    override func supportedEvents() -> [String]! {
        return [
            "deviceFound",
            "onDeviceFound",
            "scanResult",
            "scanFailed",
            "advertisingStartFailed",
            "advertisingStarted",
            "deviceConnected",
            "deviceDisconnected",
            "connectionStateChanged",
            "servicesDiscovered",
            "characteristicValueChanged",
            "l2capChannelPublished",
            "l2capChannelPublishFailed",
            "l2capChannelUnpublished",
            "l2capChannelOpened",
            "l2capChannelOpenFailed",
            "l2capChannelClosed",
            "l2capDataReceived",
            "peripheralReadRequest",
            "peripheralWriteRequest",
            "peripheralSubscribed",
            "peripheralUnsubscribed",
            "rssiUpdated",
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
            "multipeerMessageReceived"
        ]
    }

    override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    override func startObserving() {
        hasListeners = true
        MunimBluetoothEventEmitter.flushPendingEvents()
    }

    override func stopObserving() {
        hasListeners = false
    }

    func emitDeviceFound(_ deviceData: [String: Any]) {
        emitOnMain("deviceFound", body: deviceData)
        emitOnMain("onDeviceFound", body: deviceData)
        emitOnMain("scanResult", body: deviceData)
    }

    func emit(_ eventName: String, body: [String: Any]) {
        guard hasListeners else {
            MunimBluetoothEventEmitter.pendingEvents.append((name: eventName, body: body))
            return
        }

        emitOnMain(eventName, body: body)
    }

    static func emitOrQueue(_ eventName: String, body: [String: Any]) {
        guard let shared, shared.hasListeners else {
            pendingEvents.append((name: eventName, body: body))
            return
        }

        shared.emit(eventName, body: body)
    }

    private static func flushPendingEvents() {
        guard let shared, shared.hasListeners, !pendingEvents.isEmpty else {
            return
        }

        let events = pendingEvents
        pendingEvents.removeAll()
        events.forEach { shared.emit($0.name, body: $0.body) }
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
}

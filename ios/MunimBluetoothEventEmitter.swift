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
    
    override init() {
        super.init()
        MunimBluetoothEventEmitter.shared = self
    }
    
    override func supportedEvents() -> [String]! {
        return [
            "deviceFound",
            "onDeviceFound",
            "scanResult",
            "connectionStateChanged",
            "characteristicValueChanged"
        ]
    }
    
    override static func requiresMainQueueSetup() -> Bool {
        return false
    }
    
    func emitDeviceFound(_ deviceData: [String: Any]) {
        sendEvent(withName: "deviceFound", body: deviceData)
    }
}

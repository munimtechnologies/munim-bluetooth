//
//  HybridMunimBluetooth.swift
//  munim-bluetooth
//
//  Created by sheehanmunim on 11/12/2025.
//

import Foundation
import CoreBluetooth
import NitroModules
import React

class HybridMunimBluetooth: HybridMunimBluetoothSpec {
    // Peripheral Manager
    private var peripheralManager: CBPeripheralManager?
    private var peripheralServices: [CBMutableService] = []
    private var currentAdvertisingData: AdvertisingDataTypes?
    
    // Central Manager
    private var centralManager: CBCentralManager?
    private var discoveredPeripherals: [String: CBPeripheral] = [:]
    private var connectedPeripherals: [String: CBPeripheral] = [:]
    private var peripheralCharacteristics: [String: [CBCharacteristic]] = [:]
    private var scanOptions: ScanOptions?
    private var isScanning = false
    
    // Event emitter
    private var eventEmitter: NitroEventEmitter?
    
    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
        centralManager = CBCentralManager(delegate: self, queue: nil)
        eventEmitter = NitroEventEmitter(moduleName: "MunimBluetooth")
    }
    
    // MARK: - Peripheral Features
    
    func startAdvertising(options: AdvertisingOptions) throws {
        guard let peripheralManager = peripheralManager,
              peripheralManager.state == .poweredOn else {
            throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not powered on"])
        }
        
        var advertisingData: [String: Any] = [:]
        
        // Service UUIDs
        if !options.serviceUUIDs.isEmpty {
            let uuids = options.serviceUUIDs.compactMap { CBUUID(string: $0) }
            advertisingData[CBAdvertisementDataServiceUUIDsKey] = uuids
        }
        
        // Local name
        if let localName = options.localName {
            advertisingData[CBAdvertisementDataLocalNameKey] = localName
        }
        
        // Manufacturer data
        if let manufacturerData = options.manufacturerData,
           let data = hexStringToData(manufacturerData) {
            advertisingData[CBAdvertisementDataManufacturerDataKey] = data
        }
        
        // Advertising data
        if let advertisingDataTypes = options.advertisingData {
            processAdvertisingData(advertisingDataTypes, into: &advertisingData)
        }
        
        currentAdvertisingData = options.advertisingData
        peripheralManager.startAdvertising(advertisingData as? [String: Any])
    }
    
    func updateAdvertisingData(advertisingData: AdvertisingDataTypes) throws {
        guard let peripheralManager = peripheralManager,
              peripheralManager.state == .poweredOn else {
            throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not powered on"])
        }
        
        peripheralManager.stopAdvertising()
        
        var newAdvertisingData: [String: Any] = [:]
        processAdvertisingData(advertisingData, into: &newAdvertisingData)
        
        currentAdvertisingData = advertisingData
        peripheralManager.startAdvertising(newAdvertisingData as? [String: Any])
    }
    
    func getAdvertisingData() throws -> Promise<AdvertisingDataTypes> {
        return Promise { resolver in
            resolver.resolve(self.currentAdvertisingData ?? AdvertisingDataTypes())
        }
    }
    
    func stopAdvertising() throws {
        peripheralManager?.stopAdvertising()
        currentAdvertisingData = nil
    }
    
    func setServices(services: [GATTService]) throws {
        peripheralServices.removeAll()
        
        for service in services {
            let serviceUUID = CBUUID(string: service.uuid)
            let mutableService = CBMutableService(type: serviceUUID, primary: true)
            
            var characteristics: [CBMutableCharacteristic] = []
            
            for characteristic in service.characteristics {
                let charUUID = CBUUID(string: characteristic.uuid)
                
                var properties: CBCharacteristicProperties = []
                for prop in characteristic.properties {
                    switch prop {
                    case "read":
                        properties.insert(.read)
                    case "write":
                        properties.insert(.write)
                    case "writeWithoutResponse":
                        properties.insert(.writeWithoutResponse)
                    case "notify":
                        properties.insert(.notify)
                    case "indicate":
                        properties.insert(.indicate)
                    default:
                        break
                    }
                }
                
                var value: Data?
                if let valueString = characteristic.value {
                    value = hexStringToData(valueString)
                    if !properties.contains(.read) {
                        properties.insert(.read)
                    }
                }
                
                let permissions: CBAttributePermissions = value != nil ? .readable : [.readable, .writeable]
                
                let mutableChar = CBMutableCharacteristic(
                    type: charUUID,
                    properties: properties,
                    value: value,
                    permissions: permissions
                )
                
                characteristics.append(mutableChar)
            }
            
            mutableService.characteristics = characteristics
            peripheralServices.append(mutableService)
        }
        
        for service in peripheralServices {
            peripheralManager?.add(service)
        }
    }
    
    // MARK: - Central/Manager Features
    
    func isBluetoothEnabled() throws -> Promise<Bool> {
        return Promise { resolver in
            let isEnabled = self.centralManager?.state == .poweredOn
            resolver.resolve(isEnabled ?? false)
        }
    }
    
    func requestBluetoothPermission() throws -> Promise<Bool> {
        return Promise { resolver in
            // In iOS, permissions are handled by CBPeripheralManager/CBCentralManager
            resolver.resolve(true)
        }
    }
    
    func startScan(options: ScanOptions?) throws {
        guard let centralManager = centralManager,
              centralManager.state == .poweredOn else {
            throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not powered on"])
        }
        
        scanOptions = options
        isScanning = true
        
        var scanOptions: [String: Any] = [:]
        if let options = options {
            scanOptions[CBCentralManagerScanOptionAllowDuplicatesKey] = options.allowDuplicates ?? false
        }
        
        centralManager.scanForPeripherals(withServices: nil, options: scanOptions as [String : Any])
    }
    
    func stopScan() throws {
        centralManager?.stopScan()
        isScanning = false
    }
    
    func connect(deviceId: String) throws -> Promise<Void> {
        return Promise { resolver in
            guard let peripheral = self.discoveredPeripherals[deviceId] else {
                resolver.reject(NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not found"]))
                return
            }
            
            self.centralManager?.connect(peripheral, options: nil)
            self.connectedPeripherals[deviceId] = peripheral
            resolver.resolve(())
        }
    }
    
    func disconnect(deviceId: String) throws {
        guard let peripheral = connectedPeripherals[deviceId] else { return }
        centralManager?.cancelPeripheralConnection(peripheral)
        connectedPeripherals.removeValue(forKey: deviceId)
    }
    
    func discoverServices(deviceId: String) throws -> Promise<[GATTService]> {
        return Promise { resolver in
            guard let peripheral = self.connectedPeripherals[deviceId] else {
                resolver.reject(NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
                return
            }
            
            peripheral.discoverServices(nil)
            resolver.resolve([])
        }
    }
    
    func readCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws -> Promise<CharacteristicValue> {
        return Promise { resolver in
            resolver.reject(NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Not implemented"]))
        }
    }
    
    func writeCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String, value: String, writeType: WriteType?) throws -> Promise<Void> {
        return Promise { resolver in
            resolver.reject(NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Not implemented"]))
        }
    }
    
    func subscribeToCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        // Not implemented
    }
    
    func unsubscribeFromCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        // Not implemented
    }
    
    func getConnectedDevices() throws -> Promise<[String]> {
        return Promise { resolver in
            resolver.resolve(Array(self.connectedPeripherals.keys))
        }
    }
    
    func readRSSI(deviceId: String) throws -> Promise<Double> {
        return Promise { resolver in
            guard let peripheral = self.connectedPeripherals[deviceId] else {
                resolver.reject(NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
                return
            }
            
            peripheral.readRSSI()
            resolver.resolve(0)
        }
    }
    
    func addListener(eventName: String) throws {
        // Event management
    }
    
    func removeListeners(count: Double) throws {
        // Event management
    }
    
    // MARK: - Helper Methods
    
    private func hexStringToData(_ hex: String) -> Data? {
        var data = Data()
        var hex = hex
        
        if hex.count % 2 != 0 {
            hex = "0" + hex
        }
        
        let regex = try! NSRegularExpression(pattern: "[0-9a-f]{1,2}", options: .caseInsensitive)
        regex.enumerateMatches(in: hex, range: NSRange(hex.startIndex..., in: hex)) { match, _, _ in
            let byteStr = (hex as NSString).substring(with: match!.range)
            let num = UInt8(byteStr, radix: 16)!
            data.append(num)
        }
        
        return data.isEmpty ? nil : data
    }
    
    private func processAdvertisingData(_ data: AdvertisingDataTypes, into advertisingData: inout [String: Any]) {
        if let flags = data.flags {
            advertisingData[CBAdvertisementDataIsConnectable] = true
        }
        
        if let completeLocalName = data.completeLocalName {
            advertisingData[CBAdvertisementDataLocalNameKey] = completeLocalName
        }
        
        if let txPowerLevel = data.txPowerLevel {
            advertisingData[CBAdvertisementDataTxPowerLevelKey] = txPowerLevel
        }
    }
}

// MARK: - CBPeripheralManagerDelegate Implementation
extension HybridMunimBluetooth: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        // Handle state updates
    }
    
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            eventEmitter?.emit("advertisingError", ["error": error.localizedDescription])
        } else {
            eventEmitter?.emit("advertisingStarted", [:])
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            eventEmitter?.emit("serviceError", ["error": error.localizedDescription])
        } else {
            eventEmitter?.emit("serviceAdded", ["uuid": service.uuid.uuidString])
        }
    }
}

// MARK: - CBCentralManagerDelegate Implementation
extension HybridMunimBluetooth: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        let state = central.state
        eventEmitter?.emit("bluetoothStateChanged", ["state": state.rawValue])
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let deviceId = peripheral.identifier.uuidString
        discoveredPeripherals[deviceId] = peripheral
        
        eventEmitter?.emit("deviceFound", [
            "id": deviceId,
            "name": peripheral.name ?? "",
            "rssi": RSSI.intValue
        ])
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let deviceId = peripheral.identifier.uuidString
        connectedPeripherals[deviceId] = peripheral
        peripheral.delegate = self
        
        eventEmitter?.emit("deviceConnected", ["id": deviceId])
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        connectedPeripherals.removeValue(forKey: deviceId)
        peripheralCharacteristics.removeValue(forKey: deviceId)
        
        eventEmitter?.emit("deviceDisconnected", ["id": deviceId])
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        eventEmitter?.emit("connectionFailed", [
            "id": deviceId,
            "error": error?.localizedDescription ?? "Unknown error"
        ])
    }
}

// MARK: - CBPeripheralDelegate Implementation
extension HybridMunimBluetooth: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        guard let services = peripheral.services else { return }
        
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
        
        eventEmitter?.emit("servicesDiscovered", ["id": deviceId])
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        guard let characteristics = service.characteristics else { return }
        
        if peripheralCharacteristics[deviceId] == nil {
            peripheralCharacteristics[deviceId] = []
        }
        peripheralCharacteristics[deviceId]?.append(contentsOf: characteristics)
        
        eventEmitter?.emit("characteristicsDiscovered", ["id": deviceId])
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        guard let data = characteristic.value else { return }
        
        let hexString = data.map { String(format: "%02x", $0) }.joined()
        
        eventEmitter?.emit("characteristicValueChanged", [
            "id": deviceId,
            "serviceUUID": characteristic.service?.uuid.uuidString ?? "",
            "characteristicUUID": characteristic.uuid.uuidString,
            "value": hexString
        ])
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            eventEmitter?.emit("writeError", [
                "id": deviceId,
                "error": error.localizedDescription
            ])
        } else {
            eventEmitter?.emit("writeSuccess", ["id": deviceId])
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didReadRSSI RSSI: NSNumber, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            eventEmitter?.emit("rssiError", ["error": error.localizedDescription])
        } else {
            eventEmitter?.emit("rssiUpdated", ["id": deviceId, "rssi": RSSI.intValue])
        }
    }
}

// MARK: - Helper Classes
class NitroEventEmitter {
    private let moduleName: String
    
    init(moduleName: String) {
        self.moduleName = moduleName
    }
    
    func emit(_ eventName: String, _ body: [String: Any]) {
        let sendEvent = {
            guard let bridge = RCTBridge.current() ?? RCTBridge.currentBridge() else {
                NSLog("[\(self.moduleName)] Unable to emit event \(eventName): missing bridge")
                return
            }
            
            bridge.enqueueJSCall(
                "RCTDeviceEventEmitter",
                method: "emit",
                args: [eventName, body],
                completion: nil
            )
        }
        
        if Thread.isMainThread {
            sendEvent()
        } else {
            DispatchQueue.main.async(execute: sendEvent)
        }
    }
}

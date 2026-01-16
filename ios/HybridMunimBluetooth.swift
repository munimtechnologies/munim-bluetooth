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
    
    override func startAdvertising(options: AdvertisingOptions) throws {
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
    
    override func updateAdvertisingData(advertisingData: AdvertisingDataTypes) throws {
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
    
    override func getAdvertisingData() throws -> Promise<AdvertisingDataTypes> {
        return Promise { resolver in
            resolver.resolve(self.currentAdvertisingData ?? AdvertisingDataTypes())
        }
    }
    
    override func stopAdvertising() throws {
        peripheralManager?.stopAdvertising()
        currentAdvertisingData = nil
    }
    
    override func setServices(services: [GATTService]) throws {
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
    
    override func isBluetoothEnabled() throws -> Promise<Bool> {
        return Promise { resolver in
            let isEnabled = self.centralManager?.state == .poweredOn
            resolver.resolve(isEnabled ?? false)
        }
    }
    
    override func requestBluetoothPermission() throws -> Promise<Bool> {
        return Promise { resolver in
            // In iOS, permissions are handled by CBPeripheralManager/CBCentralManager
            resolver.resolve(true)
        }
    }
    
    override func startScan(options: ScanOptions?) throws {
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
    
    override func stopScan() throws {
        centralManager?.stopScan()
        isScanning = false
    }
    
    override func connect(deviceId: String) throws -> Promise<Void> {
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
    
    override func disconnect(deviceId: String) throws {
        guard let peripheral = connectedPeripherals[deviceId] else { return }
        centralManager?.cancelPeripheralConnection(peripheral)
        connectedPeripherals.removeValue(forKey: deviceId)
    }
    
    override func discoverServices(deviceId: String) throws -> Promise<[GATTService]> {
        return Promise { resolver in
            guard let peripheral = self.connectedPeripherals[deviceId] else {
                resolver.reject(NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
                return
            }
            
            peripheral.discoverServices(nil)
            resolver.resolve([])
        }
    }
    
    override func readCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws -> Promise<CharacteristicValue> {
        return Promise { resolver in
            resolver.reject(NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Not implemented"]))
        }
    }
    
    override func writeCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String, value: String, writeType: WriteType?) throws -> Promise<Void> {
        return Promise { resolver in
            resolver.reject(NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Not implemented"]))
        }
    }
    
    override func subscribeToCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        // Not implemented
    }
    
    override func unsubscribeFromCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        // Not implemented
    }
    
    override func getConnectedDevices() throws -> Promise<[String]> {
        return Promise { resolver in
            resolver.resolve(Array(self.connectedPeripherals.keys))
        }
    }
    
    override func readRSSI(deviceId: String) throws -> Promise<Double> {
        return Promise { resolver in
            guard let peripheral = self.connectedPeripherals[deviceId] else {
                resolver.reject(NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
                return
            }
            
            peripheral.readRSSI()
            resolver.resolve(0)
        }
    }
    
    override func addListener(eventName: String) throws {
        // Event management
    }
    
    override func removeListeners(count: Double) throws {
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

// MARK: - CBPeripheralManagerDelegate
extension HybridMunimBluetooth: CBPeripheralManagerDelegate {
        guard let peripheralManager = peripheralManager,
              peripheralManager.state == .poweredOn else {
            throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not powered on"])
        }
        
        var advertisingData: [String: Any] = [:]
        
        // Process comprehensive advertising data
        if let advertisingDataDict = options["advertisingData"] as? [String: Any] {
            processAdvertisingData(advertisingDataDict, into: &advertisingData)
        }
        
        // Legacy support
        if let localName = options["localName"] as? String {
            advertisingData[CBAdvertisementDataLocalNameKey] = localName
        }
        
        if let serviceUUIDs = options["serviceUUIDs"] as? [String], !serviceUUIDs.isEmpty {
            let uuids = serviceUUIDs.compactMap { CBUUID(string: $0) }
            advertisingData[CBAdvertisementDataServiceUUIDsKey] = uuids
        }
        
        if let manufacturerData = options["manufacturerData"] as? String,
           let data = hexStringToData(manufacturerData) {
            advertisingData[CBAdvertisementDataManufacturerDataKey] = data
        }
        
        currentAdvertisingData = advertisingData
        peripheralManager.startAdvertising(advertisingData as? [String: Any])
    }
    
    func updateAdvertisingData(advertisingData: [String: Any]) throws {
        guard let peripheralManager = peripheralManager,
              peripheralManager.state == .poweredOn else {
            throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not powered on"])
        }
        
        peripheralManager.stopAdvertising()
        
        var newAdvertisingData: [String: Any] = [:]
        processAdvertisingData(advertisingData, into: &newAdvertisingData)
        
        currentAdvertisingData = newAdvertisingData
        peripheralManager.startAdvertising(newAdvertisingData as? [String: Any])
    }
    
    func getAdvertisingData() throws -> [String: Any] {
        return currentAdvertisingData
    }
    
    func stopAdvertising() throws {
        peripheralManager?.stopAdvertising()
        currentAdvertisingData = [:]
    }
    
    func setServices(services: [[String: Any]]) throws {
        peripheralServices.removeAll()
        
        for serviceDict in services {
            guard let uuidString = serviceDict["uuid"] as? String else { continue }
            
            let serviceUUID = CBUUID(string: uuidString)
            let service = CBMutableService(type: serviceUUID, primary: true)
            
            var characteristics: [CBMutableCharacteristic] = []
            
            if let characteristicsArray = serviceDict["characteristics"] as? [[String: Any]] {
                for charDict in characteristicsArray {
                    guard let charUUIDString = charDict["uuid"] as? String else { continue }
                    
                    let charUUID = CBUUID(string: charUUIDString)
                    var properties: CBCharacteristicProperties = []
                    
                    if let propertiesArray = charDict["properties"] as? [String] {
                        for prop in propertiesArray {
                            switch prop {
                            case "read":
                                properties.insert(.read)
                            case "write":
                                properties.insert(.write)
                            case "notify":
                                properties.insert(.notify)
                            case "indicate":
                                properties.insert(.indicate)
                            default:
                                break
                            }
                        }
                    }
                    
                    var value: Data?
                    if let valueString = charDict["value"] as? String {
                        value = valueString.data(using: .utf8)
                        properties.insert(.read)
                    }
                    
                    let permissions: CBAttributePermissions = value != nil ? .readable : [.readable, .writeable]
                    
                    let characteristic = CBMutableCharacteristic(
                        type: charUUID,
                        properties: properties,
                        value: value,
                        permissions: permissions
                    )
                    
                    characteristics.append(characteristic)
                }
            }
            
            service.characteristics = characteristics
            peripheralServices.append(service)
        }
        
        peripheralManager?.removeAllServices()
        for service in peripheralServices {
            peripheralManager?.add(service)
        }
    }
    
    // MARK: - CBPeripheralManagerDelegate
    extension HybridMunimBluetooth: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        // Handle state updates
    }
    
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            // Emit error event
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            // Emit error event
        }
    }
}

// MARK: - CBCentralManagerDelegate

extension HybridMunimBluetooth: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        // Handle state updates
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let deviceId = peripheral.identifier.uuidString
        discoveredPeripherals[deviceId] = peripheral
        
        // Emit deviceFound event
        eventEmitter?.emit("deviceFound", [
            "id": deviceId,
            "name": peripheral.name ?? "",
            "rssi": RSSI.intValue,
            "advertisingData": advertisementData
        ])
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let deviceId = peripheral.identifier.uuidString
        connectedPeripherals[deviceId] = peripheral
        peripheral.delegate = self
        
        // Emit deviceConnected event
        eventEmitter?.emit("deviceConnected", ["id": deviceId])
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        connectedPeripherals.removeValue(forKey: deviceId)
        peripheralCharacteristics.removeValue(forKey: deviceId)
        
        // Emit deviceDisconnected event
        eventEmitter?.emit("deviceDisconnected", ["id": deviceId])
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        // Emit connectionFailed event
        eventEmitter?.emit("connectionFailed", [
            "id": deviceId,
            "error": error?.localizedDescription ?? "Unknown error"
        ])
    }
}

// MARK: - CBPeripheralDelegate

extension HybridMunimBluetooth: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        guard let services = peripheral.services else { return }
        
        var servicesArray: [[String: Any]] = []
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
            servicesArray.append(["uuid": service.uuid.uuidString])
        }
        
        // Emit servicesDiscovered event
        eventEmitter?.emit("servicesDiscovered", [
            "id": deviceId,
            "services": servicesArray
        ])
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        guard let characteristics = service.characteristics else { return }
        
        if peripheralCharacteristics[deviceId] == nil {
            peripheralCharacteristics[deviceId] = []
        }
        peripheralCharacteristics[deviceId]?.append(contentsOf: characteristics)
        
        // Emit characteristicsDiscovered event
        var characteristicsArray: [[String: Any]] = []
        for characteristic in characteristics {
            var properties: [String] = []
            if characteristic.properties.contains(.read) { properties.append("read") }
            if characteristic.properties.contains(.write) { properties.append("write") }
            if characteristic.properties.contains(.notify) { properties.append("notify") }
            if characteristic.properties.contains(.indicate) { properties.append("indicate") }
            
            characteristicsArray.append([
                "uuid": characteristic.uuid.uuidString,
                "properties": properties
            ])
        }
        
        eventEmitter?.emit("characteristicsDiscovered", [
            "id": deviceId,
            "serviceUUID": service.uuid.uuidString,
            "characteristics": characteristicsArray
        ])
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        guard let data = characteristic.value else { return }
        
        let hexString = data.map { String(format: "%02x", $0) }.joined()
        
        // Emit characteristicValueChanged event
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
                "serviceUUID": characteristic.service?.uuid.uuidString ?? "",
                "characteristicUUID": characteristic.uuid.uuidString,
                "error": error.localizedDescription
            ])
        } else {
            eventEmitter?.emit("writeSuccess", [
                "id": deviceId,
                "serviceUUID": characteristic.service?.uuid.uuidString ?? "",
                "characteristicUUID": characteristic.uuid.uuidString
            ])
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            eventEmitter?.emit("subscriptionError", [
                "id": deviceId,
                "serviceUUID": characteristic.service?.uuid.uuidString ?? "",
                "characteristicUUID": characteristic.uuid.uuidString,
                "error": error.localizedDescription
            ])
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didReadRSSI RSSI: NSNumber, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            eventEmitter?.emit("rssiError", [
                "id": deviceId,
                "error": error.localizedDescription
            ])
        } else {
            eventEmitter?.emit("rssiUpdated", [
                "id": deviceId,
                "rssi": RSSI.intValue
            ])
        }
    }
}

// MARK: - Event Emitter Helper

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

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
    private var currentAdvertisingData: [String: Any] = [:]
    
    // Central Manager
    private var centralManager: CBCentralManager?
    private var discoveredPeripherals: [String: CBPeripheral] = [:]
    private var connectedPeripherals: [String: CBPeripheral] = [:]
    private var peripheralCharacteristics: [String: [CBCharacteristic]] = [:]
    private var scanOptions: [String: Any]?
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
    
    func startAdvertising(options: [String: Any]) throws {
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
    
    // MARK: - Central Features
    
    func isBluetoothEnabled() throws -> Bool {
        guard let centralManager = centralManager else { return false }
        return centralManager.state == .poweredOn
    }
    
    func requestBluetoothPermission() throws -> Bool {
        guard let centralManager = centralManager else { return false }
        // On iOS, permission is requested automatically when creating CBCentralManager
        // The state will be .unauthorized if permission is denied
        return centralManager.state != .unauthorized
    }
    
    func startScan(options: [String: Any]?) throws {
        guard let centralManager = centralManager,
              centralManager.state == .poweredOn else {
            throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not powered on"])
        }
        
        guard !isScanning else { return }
        
        scanOptions = options
        isScanning = true
        discoveredPeripherals.removeAll()
        
        var serviceUUIDs: [CBUUID]?
        if let serviceUUIDsArray = options?["serviceUUIDs"] as? [String] {
            serviceUUIDs = serviceUUIDsArray.compactMap { CBUUID(string: $0) }
        }
        
        centralManager.scanForPeripherals(withServices: serviceUUIDs, options: [
            CBCentralManagerScanOptionAllowDuplicatesKey: options?["allowDuplicates"] as? Bool ?? false
        ])
    }
    
    func stopScan() throws {
        guard isScanning else { return }
        centralManager?.stopScan()
        isScanning = false
        scanOptions = nil
    }
    
    func connect(deviceId: String) throws {
        guard let peripheral = discoveredPeripherals[deviceId] else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Device not found"])
        }
        
        centralManager?.connect(peripheral, options: nil)
    }
    
    func disconnect(deviceId: String) throws {
        guard let peripheral = connectedPeripherals[deviceId] ?? discoveredPeripherals[deviceId] else {
            return
        }
        
        centralManager?.cancelPeripheralConnection(peripheral)
    }
    
    func discoverServices(deviceId: String) throws -> [[String: Any]] {
        guard let peripheral = connectedPeripherals[deviceId] else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Device not connected"])
        }
        
        peripheral.discoverServices(nil)
        
        // Wait for services to be discovered (in real implementation, this would be async)
        // For now, return empty array - services will be discovered via delegate callbacks
        return []
    }
    
    func readCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws -> [String: Any] {
        guard let peripheral = connectedPeripherals[deviceId] else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Device not connected"])
        }
        
        guard let characteristics = peripheralCharacteristics[deviceId],
              let characteristic = characteristics.first(where: {
                  $0.service?.uuid == CBUUID(string: serviceUUID) &&
                  $0.uuid == CBUUID(string: characteristicUUID)
              }) else {
            throw NSError(domain: "MunimBluetooth", code: 3, userInfo: [NSLocalizedDescriptionKey: "Characteristic not found"])
        }
        
        peripheral.readValue(for: characteristic)
        
        // Return empty for now - value will come via delegate callback
        return [
            "value": "",
            "serviceUUID": serviceUUID,
            "characteristicUUID": characteristicUUID
        ]
    }
    
    func writeCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String, value: String, writeType: String?) throws {
        guard let peripheral = connectedPeripherals[deviceId] else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Device not connected"])
        }
        
        guard let characteristics = peripheralCharacteristics[deviceId],
              let characteristic = characteristics.first(where: {
                  $0.service?.uuid == CBUUID(string: serviceUUID) &&
                  $0.uuid == CBUUID(string: characteristicUUID)
              }) else {
            throw NSError(domain: "MunimBluetooth", code: 3, userInfo: [NSLocalizedDescriptionKey: "Characteristic not found"])
        }
        
        guard let data = hexStringToData(value) else {
            throw NSError(domain: "MunimBluetooth", code: 4, userInfo: [NSLocalizedDescriptionKey: "Invalid hex string"])
        }
        
        let type: CBCharacteristicWriteType = (writeType == "writeWithoutResponse") ? .withoutResponse : .withResponse
        peripheral.writeValue(data, for: characteristic, type: type)
    }
    
    func subscribeToCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        guard let peripheral = connectedPeripherals[deviceId] else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Device not connected"])
        }
        
        guard let characteristics = peripheralCharacteristics[deviceId],
              let characteristic = characteristics.first(where: {
                  $0.service?.uuid == CBUUID(string: serviceUUID) &&
                  $0.uuid == CBUUID(string: characteristicUUID)
              }) else {
            throw NSError(domain: "MunimBluetooth", code: 3, userInfo: [NSLocalizedDescriptionKey: "Characteristic not found"])
        }
        
        peripheral.setNotifyValue(true, for: characteristic)
    }
    
    func unsubscribeFromCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        guard let peripheral = connectedPeripherals[deviceId] else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Device not connected"])
        }
        
        guard let characteristics = peripheralCharacteristics[deviceId],
              let characteristic = characteristics.first(where: {
                  $0.service?.uuid == CBUUID(string: serviceUUID) &&
                  $0.uuid == CBUUID(string: characteristicUUID)
              }) else {
            throw NSError(domain: "MunimBluetooth", code: 3, userInfo: [NSLocalizedDescriptionKey: "Characteristic not found"])
        }
        
        peripheral.setNotifyValue(false, for: characteristic)
    }
    
    func getConnectedDevices() throws -> [String] {
        return Array(connectedPeripherals.keys)
    }
    
    func readRSSI(deviceId: String) throws -> Double {
        guard let peripheral = connectedPeripherals[deviceId] else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Device not connected"])
        }
        
        peripheral.readRSSI()
        // RSSI will come via delegate callback
        return 0.0
    }
    
    // MARK: - Event Management
    
    func addListener(eventName: String) throws {
        // Event listeners are handled by the event emitter
        // This is a no-op as Nitro modules handle events differently
    }
    
    func removeListeners(count: Double) throws {
        // Event listeners are handled by the event emitter
        // This is a no-op as Nitro modules handle events differently
    }
    
    // MARK: - Helper Methods
    
    private func processAdvertisingData(_ dataDict: [String: Any], into advertisingData: inout [String: Any]) {
        // Flags
        if let flags = dataDict["flags"] as? Int {
            let isConnectable = (flags & 0x02) != 0
            advertisingData[CBAdvertisementDataIsConnectable] = isConnectable
        }
        
        // Service UUIDs
        addServiceUUIDs(dataDict["incompleteServiceUUIDs16"], to: &advertisingData, key: CBAdvertisementDataServiceUUIDsKey)
        addServiceUUIDs(dataDict["completeServiceUUIDs16"], to: &advertisingData, key: CBAdvertisementDataServiceUUIDsKey)
        addServiceUUIDs(dataDict["incompleteServiceUUIDs32"], to: &advertisingData, key: CBAdvertisementDataServiceUUIDsKey)
        addServiceUUIDs(dataDict["completeServiceUUIDs32"], to: &advertisingData, key: CBAdvertisementDataServiceUUIDsKey)
        addServiceUUIDs(dataDict["incompleteServiceUUIDs128"], to: &advertisingData, key: CBAdvertisementDataServiceUUIDsKey)
        addServiceUUIDs(dataDict["completeServiceUUIDs128"], to: &advertisingData, key: CBAdvertisementDataServiceUUIDsKey)
        
        // Local Name
        if let shortenedName = dataDict["shortenedLocalName"] as? String {
            advertisingData[CBAdvertisementDataLocalNameKey] = shortenedName
        }
        if let completeName = dataDict["completeLocalName"] as? String {
            advertisingData[CBAdvertisementDataLocalNameKey] = completeName
        }
        
        // Tx Power Level
        if let txPower = dataDict["txPowerLevel"] as? Int {
            advertisingData[CBAdvertisementDataTxPowerLevelKey] = txPower
        }
        
        // Service Solicitation
        addServiceUUIDs(dataDict["serviceSolicitationUUIDs16"], to: &advertisingData, key: CBAdvertisementDataSolicitedServiceUUIDsKey)
        addServiceUUIDs(dataDict["serviceSolicitationUUIDs128"], to: &advertisingData, key: CBAdvertisementDataSolicitedServiceUUIDsKey)
        addServiceUUIDs(dataDict["serviceSolicitationUUIDs32"], to: &advertisingData, key: CBAdvertisementDataSolicitedServiceUUIDsKey)
        
        // Service Data
        addServiceData(dataDict["serviceData16"], to: &advertisingData)
        addServiceData(dataDict["serviceData32"], to: &advertisingData)
        addServiceData(dataDict["serviceData128"], to: &advertisingData)
        
        // Appearance
        if let appearance = dataDict["appearance"] as? Int {
            let appearanceData = Data(bytes: [UInt8(appearance & 0xFF), UInt8((appearance >> 8) & 0xFF)], count: 2)
            advertisingData[CBUUID(string: "1800")] = appearanceData
        }
        
        // Manufacturer Data
        if let manufacturerData = dataDict["manufacturerData"] as? String,
           let data = hexStringToData(manufacturerData) {
            advertisingData[CBAdvertisementDataManufacturerDataKey] = data
        }
    }
    
    private func addServiceUUIDs(_ uuids: Any?, to advertisingData: inout [String: Any], key: String) {
        guard let uuidArray = uuids as? [String] else { return }
        
        let cbUUIDs = uuidArray.compactMap { CBUUID(string: $0) }
        if !cbUUIDs.isEmpty {
            if var existing = advertisingData[key] as? [CBUUID] {
                existing.append(contentsOf: cbUUIDs)
                advertisingData[key] = existing
            } else {
                advertisingData[key] = cbUUIDs
            }
        }
    }
    
    private func addServiceData(_ serviceDataArray: Any?, to advertisingData: inout [String: Any]) {
        guard let array = serviceDataArray as? [[String: Any]] else { return }
        
        for serviceData in array {
            guard let uuid = serviceData["uuid"] as? String,
                  let dataString = serviceData["data"] as? String,
                  let data = hexStringToData(dataString) else { continue }
            
            advertisingData[uuid] = data
        }
    }
    
    private func hexStringToData(_ hexString: String) -> Data? {
        let cleanHex = hexString.replacingOccurrences(of: " ", with: "")
        guard cleanHex.count % 2 == 0 else { return nil }
        
        var data = Data()
        var index = cleanHex.startIndex
        
        while index < cleanHex.endIndex {
            let nextIndex = cleanHex.index(index, offsetBy: 2)
            guard nextIndex <= cleanHex.endIndex else { break }
            
            let byteString = String(cleanHex[index..<nextIndex])
            if let byte = UInt8(byteString, radix: 16) {
                data.append(byte)
            }
            
            index = nextIndex
        }
        
        return data
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

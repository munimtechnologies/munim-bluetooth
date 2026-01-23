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
    
    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(delegate: self, queue: nil)
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    // MARK: - Event Emission
    private func emitDeviceFound(device: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        // Build device data dictionary
        var deviceData: [String: Any] = [
            "id": device.identifier.uuidString,
            "rssi": rssi.intValue
        ]
        
        // Add device name if available
        if let name = device.name {
            deviceData["name"] = name
        }
        
        // Extract and add advertising data
        if let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String {
            deviceData["localName"] = localName
        }
        
        if let serviceUUIDs = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] {
            deviceData["serviceUUIDs"] = serviceUUIDs.map { $0.uuidString }
        }
        
        if let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data {
            deviceData["manufacturerData"] = manufacturerData.map { String(format: "%02x", $0) }.joined()
        }
        
        if let txPowerLevel = advertisementData[CBAdvertisementDataTxPowerLevelKey] as? NSNumber {
            deviceData["txPowerLevel"] = txPowerLevel.intValue
        }
        
        if let isConnectable = advertisementData[CBAdvertisementDataIsConnectable] as? NSNumber {
            deviceData["isConnectable"] = isConnectable.boolValue
        }
        
        // Store advertising data
        deviceData["advertisingData"] = advertisementData
        
        // Emit event through the event emitter
        if let emitter = MunimBluetoothEventEmitter.shared {
            emitter.emitDeviceFound(deviceData)
            NSLog("[MunimBluetooth] ✅ Device found event emitted: %@", device.identifier.uuidString)
        } else {
            NSLog("[MunimBluetooth] ⚠️ Event emitter not initialized!")
        }
    }
    
    // MARK: - Peripheral Features
    
    func startAdvertising(options: AdvertisingOptions) throws {
        guard let peripheralManager = peripheralManager else {
            throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Peripheral manager not initialized"])
        }
        
        guard peripheralManager.state == .poweredOn else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not powered on. Current state: \(peripheralManager.state.rawValue)"])
        }
        
        // Stop any existing advertising first
        if peripheralManager.isAdvertising {
            NSLog("[MunimBluetooth] Stopping existing advertising")
            peripheralManager.stopAdvertising()
        }
        
        var advertisingData: [String: Any] = [:]
        
        // Service UUIDs
        if !options.serviceUUIDs.isEmpty {
            let uuids = options.serviceUUIDs.compactMap { CBUUID(string: $0) }
            advertisingData[CBAdvertisementDataServiceUUIDsKey] = uuids
            NSLog("[MunimBluetooth] Advertising service UUIDs: %@", options.serviceUUIDs)
        }
        
        // Local name
        if let localName = options.localName {
            advertisingData[CBAdvertisementDataLocalNameKey] = localName
            NSLog("[MunimBluetooth] Advertising local name: %@", localName)
        }
        
        // Manufacturer data
        if let manufacturerData = options.manufacturerData,
           let data = hexStringToData(manufacturerData) {
            advertisingData[CBAdvertisementDataManufacturerDataKey] = data
            NSLog("[MunimBluetooth] Advertising manufacturer data: %@ bytes", String(data.count))
        }
        
        // Advertising data
        if let advertisingDataTypes = options.advertisingData {
            processAdvertisingData(advertisingDataTypes, into: &advertisingData)
        }
        
        currentAdvertisingData = options.advertisingData
        
        NSLog("[MunimBluetooth] Starting advertising with data: %@", advertisingData)
        peripheralManager.startAdvertising(advertisingData)
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
        let promise = Promise<AdvertisingDataTypes>()
        promise.resolve(withResult: self.currentAdvertisingData ?? AdvertisingDataTypes())
        return promise
    }
    
    func stopAdvertising() throws {
        peripheralManager?.stopAdvertising()
        currentAdvertisingData = nil
    }
    
    func setServices(services: [GATTService]) throws {
        guard let peripheralManager = peripheralManager else {
            throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Peripheral manager not initialized"])
        }
        
        guard peripheralManager.state == .poweredOn else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not powered on. Current state: \(peripheralManager.state.rawValue)"])
        }
        
        // Remove existing services first
        peripheralManager.removeAllServices()
        peripheralServices.removeAll()
        
        NSLog("[MunimBluetooth] Setting up %d services", services.count)
        
        for service in services {
            let serviceUUID = CBUUID(string: service.uuid)
            let mutableService = CBMutableService(type: serviceUUID, primary: true)
            
            var characteristics: [CBMutableCharacteristic] = []
            
            NSLog("[MunimBluetooth] Service %@: %d characteristics", service.uuid, service.characteristics.count)
            
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
                NSLog("[MunimBluetooth] Characteristic added: %@ with properties: %lu", characteristic.uuid, properties.rawValue)
            }
            
            mutableService.characteristics = characteristics
            peripheralServices.append(mutableService)
            
            NSLog("[MunimBluetooth] Adding service to peripheral manager: %@", service.uuid)
            peripheralManager.add(mutableService)
        }
        
        NSLog("[MunimBluetooth] All services added successfully")
    }
    
    // MARK: - Central/Manager Features
    
    func isBluetoothEnabled() throws -> Promise<Bool> {
        let promise = Promise<Bool>()
        let isEnabled = self.centralManager?.state == .poweredOn
        promise.resolve(withResult: isEnabled ?? false)
        return promise
    }
    
    func requestBluetoothPermission() throws -> Promise<Bool> {
        let promise = Promise<Bool>()
        // In iOS, permissions are handled by CBPeripheralManager/CBCentralManager
        promise.resolve(withResult: true)
        return promise
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
        let promise = Promise<Void>()
        guard let peripheral = self.discoveredPeripherals[deviceId] else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not found"]))
            return promise
        }
        
        self.centralManager?.connect(peripheral, options: nil)
        self.connectedPeripherals[deviceId] = peripheral
        promise.resolve(withResult: ())
        return promise
    }
    
    func disconnect(deviceId: String) throws {
        guard let peripheral = connectedPeripherals[deviceId] else { return }
        centralManager?.cancelPeripheralConnection(peripheral)
        connectedPeripherals.removeValue(forKey: deviceId)
    }
    
    func discoverServices(deviceId: String) throws -> Promise<[GATTService]> {
        let promise = Promise<[GATTService]>()
        guard let peripheral = self.connectedPeripherals[deviceId] else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
            return promise
        }
        
        peripheral.discoverServices(nil)
        promise.resolve(withResult: [])
        return promise
    }
    
    func readCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws -> Promise<CharacteristicValue> {
        let promise = Promise<CharacteristicValue>()
        promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Not implemented"]))
        return promise
    }
    
    func writeCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String, value: String, writeType: WriteType?) throws -> Promise<Void> {
        let promise = Promise<Void>()
        promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Not implemented"]))
        return promise
    }
    
    func subscribeToCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        // Not implemented
    }
    
    func unsubscribeFromCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        // Not implemented
    }
    
    func getConnectedDevices() throws -> Promise<[String]> {
        let promise = Promise<[String]>()
        promise.resolve(withResult: Array(self.connectedPeripherals.keys))
        return promise
    }
    
    func readRSSI(deviceId: String) throws -> Promise<Double> {
        let promise = Promise<Double>()
        guard let peripheral = self.connectedPeripherals[deviceId] else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
            return promise
        }
        
        peripheral.readRSSI()
        promise.resolve(withResult: 0)
        return promise
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
            NSLog("Bluetooth event")
        } else {
            NSLog("Bluetooth event")
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error = error {
            NSLog("Bluetooth event")
        } else {
            NSLog("Bluetooth event")
        }
    }
}

// MARK: - CBCentralManagerDelegate Implementation
extension HybridMunimBluetooth: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        let state = central.state
        NSLog("Bluetooth event")
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let deviceId = peripheral.identifier.uuidString
        discoveredPeripherals[deviceId] = peripheral
        
        // Emit the device found event
        emitDeviceFound(device: peripheral, advertisementData: advertisementData, rssi: RSSI)
        
        NSLog("Bluetooth: deviceFound - %@", deviceId)
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let deviceId = peripheral.identifier.uuidString
        connectedPeripherals[deviceId] = peripheral
        peripheral.delegate = self
        
        NSLog("Bluetooth event")
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        connectedPeripherals.removeValue(forKey: deviceId)
        peripheralCharacteristics.removeValue(forKey: deviceId)
        
        NSLog("Bluetooth event")
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        NSLog("Bluetooth: connectionFailed")
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
        
        NSLog("Bluetooth event")
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        guard let characteristics = service.characteristics else { return }
        
        if peripheralCharacteristics[deviceId] == nil {
            peripheralCharacteristics[deviceId] = []
        }
        peripheralCharacteristics[deviceId]?.append(contentsOf: characteristics)
        
        NSLog("Bluetooth event")
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        guard let data = characteristic.value else { return }
        
        let hexString = data.map { String(format: "%02x", $0) }.joined()
        
        NSLog("Bluetooth: characteristicValueChanged")
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            NSLog("Bluetooth: writeError")
        } else {
            NSLog("Bluetooth event")
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didReadRSSI RSSI: NSNumber, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            NSLog("Bluetooth event")
        } else {
            NSLog("Bluetooth event")
        }
    }
}

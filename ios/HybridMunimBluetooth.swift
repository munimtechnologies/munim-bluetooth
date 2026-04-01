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

private let centralRestoreIdentifier = "com.munimbluetooth.central"
private let peripheralRestoreIdentifier = "com.munimbluetooth.peripheral"

private final class PeripheralManagerDelegateProxy: NSObject, CBPeripheralManagerDelegate {
    weak var owner: HybridMunimBluetooth?
    
    init(owner: HybridMunimBluetooth) {
        self.owner = owner
    }
    
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        owner?.handlePeripheralManagerDidUpdateState(peripheral)
    }
    
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        owner?.handlePeripheralManagerDidStartAdvertising(peripheral, error: error)
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        owner?.handlePeripheralManagerDidAddService(peripheral, service: service, error: error)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, willRestoreState dict: [String : Any]) {
        owner?.handlePeripheralManagerWillRestoreState(peripheral, state: dict)
    }
}

private final class CentralManagerDelegateProxy: NSObject, CBCentralManagerDelegate {
    weak var owner: HybridMunimBluetooth?
    
    init(owner: HybridMunimBluetooth) {
        self.owner = owner
    }
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        owner?.handleCentralManagerDidUpdateState(central)
    }

    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String : Any]) {
        owner?.handleCentralManagerWillRestoreState(central, state: dict)
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        owner?.handleCentralManagerDidDiscover(central, peripheral: peripheral, advertisementData: advertisementData, rssi: RSSI)
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        owner?.handleCentralManagerDidConnect(central, peripheral: peripheral)
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        owner?.handleCentralManagerDidDisconnectPeripheral(central, peripheral: peripheral, error: error)
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        owner?.handleCentralManagerDidFailToConnect(central, peripheral: peripheral, error: error)
    }
}

private final class PeripheralDelegateProxy: NSObject, CBPeripheralDelegate {
    weak var owner: HybridMunimBluetooth?
    
    init(owner: HybridMunimBluetooth) {
        self.owner = owner
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        owner?.handlePeripheralDidDiscoverServices(peripheral, error: error)
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        owner?.handlePeripheralDidDiscoverCharacteristics(peripheral, service: service, error: error)
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        owner?.handlePeripheralDidUpdateValue(peripheral, characteristic: characteristic, error: error)
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        owner?.handlePeripheralDidWriteValue(peripheral, characteristic: characteristic, error: error)
    }
    
    func peripheral(_ peripheral: CBPeripheral, didReadRSSI RSSI: NSNumber, error: Error?) {
        owner?.handlePeripheralDidReadRSSI(peripheral, rssi: RSSI, error: error)
    }
}

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
    private var pendingConnectionPromises: [String: Promise<Void>] = [:]
    private var pendingServiceDiscoveryPromises: [String: Promise<[GATTService]>] = [:]
    private var pendingCharacteristicDiscoveryCounts: [String: Int] = [:]
    private var pendingReadPromises: [String: Promise<CharacteristicValue>] = [:]
    private var pendingRSSIPromises: [String: Promise<Double>] = [:]
    private var scanOptions: ScanOptions?
    private var isScanning = false
    private var isBackgroundSessionActive = false
    private lazy var peripheralManagerDelegateProxy = PeripheralManagerDelegateProxy(owner: self)
    private lazy var centralManagerDelegateProxy = CentralManagerDelegateProxy(owner: self)
    private lazy var peripheralDelegateProxy = PeripheralDelegateProxy(owner: self)
    
    override init() {
        super.init()
        peripheralManager = CBPeripheralManager(
            delegate: peripheralManagerDelegateProxy,
            queue: nil,
            options: [
                CBPeripheralManagerOptionRestoreIdentifierKey: peripheralRestoreIdentifier
            ]
        )
        centralManager = CBCentralManager(
            delegate: centralManagerDelegateProxy,
            queue: nil,
            options: [
                CBCentralManagerOptionRestoreIdentifierKey: centralRestoreIdentifier
            ]
        )
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
        
        // Service UUIDs - ALLOWED
        if !options.serviceUUIDs.isEmpty {
            let uuids = options.serviceUUIDs.compactMap { CBUUID(string: $0) }
            advertisingData[CBAdvertisementDataServiceUUIDsKey] = uuids
            NSLog("[MunimBluetooth] Advertising service UUIDs: %@", options.serviceUUIDs)
        }
        
        // Local name - ALLOWED
        if let localName = options.localName {
            advertisingData[CBAdvertisementDataLocalNameKey] = localName
            NSLog("[MunimBluetooth] Advertising local name: %@", localName)
        }
        
        // Manufacturer data - NOT ALLOWED by iOS for peripheral advertising
        // This can only be included when you're a central scanning for peripherals
        if options.manufacturerData != nil {
            NSLog("[MunimBluetooth] ⚠️ WARNING: Manufacturer data cannot be advertised on iOS")
            NSLog("[MunimBluetooth] iOS only allows localName and serviceUUIDs in peripheral advertisements")
            // Don't add it to advertisingData - it will cause a warning/error
        }
        
        // Advertising data types - Most are NOT ALLOWED
        if let advertisingDataTypes = options.advertisingData {
            // Only process allowed fields
            if let completeLocalName = advertisingDataTypes.completeLocalName {
                advertisingData[CBAdvertisementDataLocalNameKey] = completeLocalName
                NSLog("[MunimBluetooth] Using complete local name from advertising data: %@", completeLocalName)
            }
            
            // Warn about unsupported fields
            if advertisingDataTypes.txPowerLevel != nil {
                NSLog("[MunimBluetooth] ⚠️ WARNING: txPowerLevel cannot be set in peripheral advertisements on iOS")
            }
            if advertisingDataTypes.flags != nil {
                NSLog("[MunimBluetooth] ⚠️ WARNING: flags cannot be set in peripheral advertisements on iOS")
            }
        }
        
        currentAdvertisingData = options.advertisingData
        
        NSLog("[MunimBluetooth] Starting advertising with allowed data: %@", advertisingData)
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
                
                // Important: In CoreBluetooth, if you provide a 'value' parameter,
                // the characteristic becomes cached and read-only.
                // For writable characteristics, the value MUST be nil.
                var value: Data? = nil
                let hasWriteProperty = properties.contains(.write) || properties.contains(.writeWithoutResponse)
                
                if !hasWriteProperty {
                    // Only set a static value for read-only characteristics
                    if let valueString = characteristic.value {
                        value = hexStringToData(valueString)
                    }
                }
                
                // Always ensure read is present if we have a value
                if value != nil && !properties.contains(.read) {
                    properties.insert(.read)
                }
                
                // Set permissions based on properties
                var permissions: CBAttributePermissions = []
                if properties.contains(.read) {
                    permissions.insert(.readable)
                }
                if hasWriteProperty {
                    permissions.insert(.writeable)
                }
                
                let mutableChar = CBMutableCharacteristic(
                    type: charUUID,
                    properties: properties,
                    value: value,
                    permissions: permissions
                )
                
                characteristics.append(mutableChar)
                NSLog("[MunimBluetooth] Characteristic added: %@ with properties: %lu, hasValue: %@", 
                      characteristic.uuid, properties.rawValue, value != nil ? "YES" : "NO")
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

        let serviceUUIDs = options?.serviceUUIDs?.map { CBUUID(string: $0) }
        centralManager.scanForPeripherals(
            withServices: serviceUUIDs?.isEmpty == false ? serviceUUIDs : nil,
            options: scanOptions as [String : Any]
        )
    }
    
    func stopScan() throws {
        centralManager?.stopScan()
        isScanning = false
    }
    
    func connect(deviceId: String) throws -> Promise<Void> {
        let promise = Promise<Void>()
        if connectedPeripherals[deviceId] != nil {
            promise.resolve(withResult: ())
            return promise
        }

        guard let peripheral = self.discoveredPeripherals[deviceId] else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not found"]))
            return promise
        }

        pendingConnectionPromises[deviceId] = promise
        peripheral.delegate = peripheralDelegateProxy
        self.centralManager?.connect(peripheral, options: nil)
        return promise
    }
    
    func disconnect(deviceId: String) throws {
        guard let peripheral = connectedPeripherals[deviceId] else { return }
        centralManager?.cancelPeripheralConnection(peripheral)
        connectedPeripherals.removeValue(forKey: deviceId)
        rejectPendingOperations(for: deviceId, error: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Disconnected from \(deviceId)"]))
    }
    
    func discoverServices(deviceId: String) throws -> Promise<[GATTService]> {
        let promise = Promise<[GATTService]>()
        guard let peripheral = self.connectedPeripherals[deviceId] else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
            return promise
        }

        if let services = peripheral.services,
           services.allSatisfy({ $0.characteristics != nil }) {
            promise.resolve(withResult: buildGATTServices(from: services))
            return promise
        }

        pendingServiceDiscoveryPromises[deviceId] = promise
        peripheral.discoverServices(nil)
        return promise
    }
    
    func readCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws -> Promise<CharacteristicValue> {
        let promise = Promise<CharacteristicValue>()

        guard let peripheral = connectedPeripherals[deviceId] else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
            return promise
        }

        guard let characteristic = findCharacteristic(
            deviceId: deviceId,
            serviceUUID: serviceUUID,
            characteristicUUID: characteristicUUID
        ) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Characteristic not found"]))
            return promise
        }

        pendingReadPromises[characteristicKey(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristicUUID)] = promise
        peripheral.readValue(for: characteristic)
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

        pendingRSSIPromises[deviceId] = promise
        peripheral.readRSSI()
        return promise
    }

    func startBackgroundSession(options: BackgroundSessionOptions) throws {
        isBackgroundSessionActive = true

        let advertisingOptions = AdvertisingOptions(
            serviceUUIDs: options.serviceUUIDs,
            localName: options.localName,
            manufacturerData: nil,
            advertisingData: nil
        )

        try startAdvertising(options: advertisingOptions)
        try startScan(
            options: ScanOptions(
                serviceUUIDs: options.serviceUUIDs,
                allowDuplicates: options.allowDuplicates,
                scanMode: options.scanMode
            )
        )
    }

    func stopBackgroundSession() throws {
        isBackgroundSessionActive = false
        try stopScan()
        try stopAdvertising()
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

    private func characteristicKey(deviceId: String, serviceUUID: String, characteristicUUID: String) -> String {
        "\(deviceId.lowercased())|\(serviceUUID.lowercased())|\(characteristicUUID.lowercased())"
    }

    private func buildGATTServices(from services: [CBService]) -> [GATTService] {
        services.map { service in
            GATTService(
                uuid: service.uuid.uuidString,
                characteristics: (service.characteristics ?? []).map { characteristic in
                    GATTCharacteristic(
                        uuid: characteristic.uuid.uuidString,
                        properties: mapProperties(characteristic.properties),
                        value: characteristic.value?.map { String(format: "%02x", $0) }.joined()
                    )
                }
            )
        }
    }

    private func mapProperties(_ properties: CBCharacteristicProperties) -> [String] {
        var result: [String] = []
        if properties.contains(.read) {
            result.append("read")
        }
        if properties.contains(.write) {
            result.append("write")
        }
        if properties.contains(.writeWithoutResponse) {
            result.append("writeWithoutResponse")
        }
        if properties.contains(.notify) {
            result.append("notify")
        }
        if properties.contains(.indicate) {
            result.append("indicate")
        }
        return result
    }

    private func findCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) -> CBCharacteristic? {
        guard let peripheral = connectedPeripherals[deviceId],
              let services = peripheral.services else {
            return nil
        }

        let matchingService = services.first {
            $0.uuid.uuidString.caseInsensitiveCompare(serviceUUID) == .orderedSame
        }
        let matchingCharacteristic = matchingService?.characteristics?.first {
            $0.uuid.uuidString.caseInsensitiveCompare(characteristicUUID) == .orderedSame
        }
        return matchingCharacteristic
    }

    private func rejectPendingOperations(for deviceId: String, error: Error) {
        pendingConnectionPromises.removeValue(forKey: deviceId)?.reject(withError: error)
        pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.reject(withError: error)
        pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
        pendingRSSIPromises.removeValue(forKey: deviceId)?.reject(withError: error)

        let prefix = "\(deviceId.lowercased())|"
        for key in pendingReadPromises.keys where key.hasPrefix(prefix) {
            pendingReadPromises.removeValue(forKey: key)?.reject(withError: error)
        }
    }

    // MARK: - CoreBluetooth Delegate Forwarding
    
    func handlePeripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        // Handle state updates
    }

    func handlePeripheralManagerWillRestoreState(_ peripheral: CBPeripheralManager, state: [String: Any]) {
        if peripheral.isAdvertising {
            isBackgroundSessionActive = true
        }
    }
    
    func handlePeripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            NSLog("Bluetooth event")
        } else {
            NSLog("Bluetooth event")
        }
    }
    
    func handlePeripheralManagerDidAddService(_ peripheral: CBPeripheralManager, service: CBService, error: Error?) {
        if let error = error {
            NSLog("Bluetooth event")
        } else {
            NSLog("Bluetooth event")
        }
    }
    
    func handleCentralManagerDidUpdateState(_ central: CBCentralManager) {
        let state = central.state
        NSLog("Bluetooth event")
    }

    func handleCentralManagerWillRestoreState(_ central: CBCentralManager, state: [String: Any]) {
        if let scanServices = state[CBCentralManagerRestoredStateScanServicesKey] as? [CBUUID] {
            scanOptions = ScanOptions(
                serviceUUIDs: scanServices.map { $0.uuidString },
                allowDuplicates: nil,
                scanMode: nil
            )
            isScanning = true
            isBackgroundSessionActive = true
        }
    }
    
    func handleCentralManagerDidDiscover(_ central: CBCentralManager, peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let deviceId = peripheral.identifier.uuidString
        discoveredPeripherals[deviceId] = peripheral
        
        // Emit the device found event
        emitDeviceFound(device: peripheral, advertisementData: advertisementData, rssi: RSSI)
        
        NSLog("Bluetooth: deviceFound - %@", deviceId)
    }
    
    func handleCentralManagerDidConnect(_ central: CBCentralManager, peripheral: CBPeripheral) {
        let deviceId = peripheral.identifier.uuidString
        connectedPeripherals[deviceId] = peripheral
        peripheral.delegate = peripheralDelegateProxy
        pendingConnectionPromises.removeValue(forKey: deviceId)?.resolve(withResult: ())
        
        NSLog("Bluetooth event")
    }
    
    func handleCentralManagerDidDisconnectPeripheral(_ central: CBCentralManager, peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        connectedPeripherals.removeValue(forKey: deviceId)
        peripheralCharacteristics.removeValue(forKey: deviceId)
        rejectPendingOperations(
            for: deviceId,
            error: error ?? NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Disconnected from \(deviceId)"])
        )
        
        NSLog("Bluetooth event")
    }
    
    func handleCentralManagerDidFailToConnect(_ central: CBCentralManager, peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        rejectPendingOperations(
            for: deviceId,
            error: error ?? NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to connect to \(deviceId)"])
        )
        NSLog("Bluetooth: connectionFailed")
    }
    
    func handlePeripheralDidDiscoverServices(_ peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString

        if let error = error {
            pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.reject(withError: error)
            pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
            return
        }

        guard let services = peripheral.services else {
            pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.resolve(withResult: [])
            pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
            return
        }

        if services.isEmpty {
            pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.resolve(withResult: [])
            pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
            return
        }

        pendingCharacteristicDiscoveryCounts[deviceId] = services.count
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
        
        NSLog("Bluetooth event")
    }
    
    func handlePeripheralDidDiscoverCharacteristics(_ peripheral: CBPeripheral, service: CBService, error: Error?) {
        let deviceId = peripheral.identifier.uuidString

        if let error = error {
            pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.reject(withError: error)
            pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
            return
        }

        guard let characteristics = service.characteristics else { return }

        if peripheralCharacteristics[deviceId] == nil {
            peripheralCharacteristics[deviceId] = []
        }
        peripheralCharacteristics[deviceId]?.append(contentsOf: characteristics)

        if let remaining = pendingCharacteristicDiscoveryCounts[deviceId] {
            let nextRemaining = max(remaining - 1, 0)
            if nextRemaining == 0 {
                pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
                pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.resolve(
                    withResult: buildGATTServices(from: peripheral.services ?? [])
                )
            } else {
                pendingCharacteristicDiscoveryCounts[deviceId] = nextRemaining
            }
        }
        
        NSLog("Bluetooth event")
    }
    
    func handlePeripheralDidUpdateValue(_ peripheral: CBPeripheral, characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString

        if let error = error {
            pendingReadPromises.removeValue(
                forKey: characteristicKey(
                    deviceId: deviceId,
                    serviceUUID: characteristic.service.uuid.uuidString,
                    characteristicUUID: characteristic.uuid.uuidString
                )
            )?.reject(withError: error)
            return
        }

        guard let data = characteristic.value else { return }

        let hexString = data.map { String(format: "%02x", $0) }.joined()
        pendingReadPromises.removeValue(
            forKey: characteristicKey(
                deviceId: deviceId,
                serviceUUID: characteristic.service.uuid.uuidString,
                characteristicUUID: characteristic.uuid.uuidString
            )
        )?.resolve(
            withResult: CharacteristicValue(
                value: hexString,
                serviceUUID: characteristic.service.uuid.uuidString,
                characteristicUUID: characteristic.uuid.uuidString
            )
        )
        
        NSLog("Bluetooth: characteristicValueChanged")
    }
    
    func handlePeripheralDidWriteValue(_ peripheral: CBPeripheral, characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            NSLog("Bluetooth: writeError")
        } else {
            NSLog("Bluetooth event")
        }
    }
    
    func handlePeripheralDidReadRSSI(_ peripheral: CBPeripheral, rssi RSSI: NSNumber, error: Error?) {
        let deviceId = peripheral.identifier.uuidString

        if let error = error {
            pendingRSSIPromises.removeValue(forKey: deviceId)?.reject(withError: error)
            NSLog("Bluetooth event")
            return
        }

        pendingRSSIPromises.removeValue(forKey: deviceId)?.resolve(withResult: RSSI.doubleValue)
        NSLog("Bluetooth event")
    }
}

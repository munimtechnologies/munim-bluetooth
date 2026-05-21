//
//  HybridMunimBluetooth.swift
//  munim-bluetooth
//
//  Created by sheehanmunim on 11/12/2025.
//

import Foundation
import CoreBluetooth
import MultipeerConnectivity
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

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        owner?.handlePeripheralManagerDidReceiveRead(peripheral, request: request)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        owner?.handlePeripheralManagerDidReceiveWrite(peripheral, requests: requests)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        owner?.handlePeripheralManagerDidSubscribe(peripheral, central: central, characteristic: characteristic)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        owner?.handlePeripheralManagerDidUnsubscribe(peripheral, central: central, characteristic: characteristic)
    }

    func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        owner?.handlePeripheralManagerIsReadyToUpdateSubscribers(peripheral)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didPublishL2CAPChannel PSM: CBL2CAPPSM, error: Error?) {
        owner?.handlePeripheralManagerDidPublishL2CAPChannel(peripheral, psm: PSM, error: error)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didUnpublishL2CAPChannel PSM: CBL2CAPPSM, error: Error?) {
        owner?.handlePeripheralManagerDidUnpublishL2CAPChannel(peripheral, psm: PSM, error: error)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didOpen channel: CBL2CAPChannel?, error: Error?) {
        owner?.handlePeripheralManagerDidOpenL2CAPChannel(peripheral, channel: channel, error: error)
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

    func peripheral(_ peripheral: CBPeripheral, didDiscoverIncludedServicesFor service: CBService, error: Error?) {
        owner?.handlePeripheralDidDiscoverIncludedServices(peripheral, service: service, error: error)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverDescriptorsFor characteristic: CBCharacteristic, error: Error?) {
        owner?.handlePeripheralDidDiscoverDescriptors(peripheral, characteristic: characteristic, error: error)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        owner?.handlePeripheralDidUpdateValue(peripheral, characteristic: characteristic, error: error)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor descriptor: CBDescriptor, error: Error?) {
        owner?.handlePeripheralDidUpdateDescriptorValue(peripheral, descriptor: descriptor, error: error)
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        owner?.handlePeripheralDidWriteValue(peripheral, characteristic: characteristic, error: error)
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor descriptor: CBDescriptor, error: Error?) {
        owner?.handlePeripheralDidWriteDescriptorValue(peripheral, descriptor: descriptor, error: error)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        owner?.handlePeripheralDidUpdateNotificationState(peripheral, characteristic: characteristic, error: error)
    }

    func peripheral(_ peripheral: CBPeripheral, didReadRSSI RSSI: NSNumber, error: Error?) {
        owner?.handlePeripheralDidReadRSSI(peripheral, rssi: RSSI, error: error)
    }

    func peripheral(_ peripheral: CBPeripheral, didOpen channel: CBL2CAPChannel?, error: Error?) {
        owner?.handlePeripheralDidOpenL2CAPChannel(peripheral, channel: channel, error: error)
    }
}

private final class L2CAPStreamDelegateProxy: NSObject, StreamDelegate {
    weak var owner: HybridMunimBluetooth?

    init(owner: HybridMunimBluetooth) {
        self.owner = owner
    }

    func stream(_ aStream: Stream, handle eventCode: Stream.Event) {
        owner?.handleL2CAPStream(aStream, eventCode: eventCode)
    }
}

private final class MultipeerSessionDelegateProxy: NSObject, MCSessionDelegate {
    weak var owner: HybridMunimBluetooth?

    init(owner: HybridMunimBluetooth) {
        self.owner = owner
    }

    func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {
        owner?.handleMultipeerStateChanged(peerID: peerID, state: state)
    }

    func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {
        owner?.handleMultipeerReceivedData(data, fromPeer: peerID)
    }

    func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {}

    func session(
        _ session: MCSession,
        didStartReceivingResourceWithName resourceName: String,
        fromPeer peerID: MCPeerID,
        with progress: Progress
    ) {}

    func session(
        _ session: MCSession,
        didFinishReceivingResourceWithName resourceName: String,
        fromPeer peerID: MCPeerID,
        at localURL: URL?,
        withError error: Error?
    ) {}
}

private final class MultipeerAdvertiserDelegateProxy: NSObject, MCNearbyServiceAdvertiserDelegate {
    weak var owner: HybridMunimBluetooth?

    init(owner: HybridMunimBluetooth) {
        self.owner = owner
    }

    func advertiser(
        _ advertiser: MCNearbyServiceAdvertiser,
        didReceiveInvitationFromPeer peerID: MCPeerID,
        withContext context: Data?,
        invitationHandler: @escaping (Bool, MCSession?) -> Void
    ) {
        owner?.handleMultipeerInvitation(fromPeer: peerID, invitationHandler: invitationHandler)
    }

    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didNotStartAdvertisingPeer error: Error) {
        owner?.handleMultipeerStartFailed(error: error)
    }
}

private final class MultipeerBrowserDelegateProxy: NSObject, MCNearbyServiceBrowserDelegate {
    weak var owner: HybridMunimBluetooth?

    init(owner: HybridMunimBluetooth) {
        self.owner = owner
    }

    func browser(_ browser: MCNearbyServiceBrowser, foundPeer peerID: MCPeerID, withDiscoveryInfo info: [String : String]?) {
        owner?.handleMultipeerFoundPeer(peerID, discoveryInfo: info)
    }

    func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        owner?.handleMultipeerLostPeer(peerID)
    }

    func browser(_ browser: MCNearbyServiceBrowser, didNotStartBrowsingForPeers error: Error) {
        owner?.handleMultipeerStartFailed(error: error)
    }
}

class HybridMunimBluetooth: HybridMunimBluetoothSpec {
    // Peripheral Manager
    private var peripheralManager: CBPeripheralManager?
    private var peripheralServices: [CBMutableService] = []
    private var configuredServices: [GATTService] = []
    private var peripheralCharacteristicValues: [String: Data] = [:]
    private var subscribedCentrals: [String: [CBCentral]] = [:]
    private var pendingSubscriberUpdates: [(CBMutableCharacteristic, Data, [CBCentral]?)] = []
    private var currentAdvertisingData: AdvertisingDataTypes?
    private var pendingL2CAPPublishPromises: [Promise<L2CAPChannel>] = []
    private var publishedL2CAPPSMs: Set<CBL2CAPPSM> = []

    // Central Manager
    private var centralManager: CBCentralManager?
    private var discoveredPeripherals: [String: CBPeripheral] = [:]
    private var connectedPeripherals: [String: CBPeripheral] = [:]
    private var peripheralCharacteristics: [String: [CBCharacteristic]] = [:]
    private var pendingConnectionPromises: [String: Promise<Void>] = [:]
    private var pendingServiceDiscoveryPromises: [String: Promise<[GATTService]>] = [:]
    private var pendingCharacteristicDiscoveryCounts: [String: Int] = [:]
    private var pendingReadPromises: [String: Promise<CharacteristicValue>] = [:]
    private var pendingWritePromises: [String: Promise<Void>] = [:]
    private var pendingDescriptorReadPromises: [String: Promise<DescriptorValue>] = [:]
    private var pendingDescriptorWritePromises: [String: Promise<Void>] = [:]
    private var pendingDescriptorWriteValues: [String: Data] = [:]
    private var pendingRSSIPromises: [String: Promise<Double>] = [:]
    private var pendingL2CAPOpenPromises: [String: Promise<L2CAPChannel>] = [:]
    private var pendingL2CAPOpenPSMs: [String: CBL2CAPPSM] = [:]
    private var l2capChannels: [String: CBL2CAPChannel] = [:]
    private var l2capInputStreamIds: [ObjectIdentifier: String] = [:]
    private var connectionTimeouts: [String: DispatchWorkItem] = [:]
    private var operationTimeouts: [String: DispatchWorkItem] = [:]
    private var scanOptions: ScanOptions?
    private var isScanning = false
    private var isBackgroundSessionActive = false
    private lazy var peripheralManagerDelegateProxy = PeripheralManagerDelegateProxy(owner: self)
    private lazy var centralManagerDelegateProxy = CentralManagerDelegateProxy(owner: self)
    private lazy var peripheralDelegateProxy = PeripheralDelegateProxy(owner: self)
    private lazy var l2capStreamDelegateProxy = L2CAPStreamDelegateProxy(owner: self)

    // Apple Multipeer Connectivity
    private var multipeerPeerID: MCPeerID?
    private var multipeerSession: MCSession?
    private var multipeerAdvertiser: MCNearbyServiceAdvertiser?
    private var multipeerBrowser: MCNearbyServiceBrowser?
    private var multipeerServiceType: String?
    private var multipeerAutoInvite = true
    private var multipeerAutoAcceptInvitations = true
    private var multipeerInviteTimeout: TimeInterval = 30
    private var multipeerLocalRuntimePeerId = UUID().uuidString
    private var multipeerPeerIds: [MCPeerID: String] = [:]
    private var multipeerPeersById: [String: MCPeerID] = [:]
    private var multipeerDiscoveryInfoById: [String: [MultipeerDiscoveryInfoEntry]] = [:]
    private var multipeerStatesById: [String: MultipeerPeerState] = [:]
    private lazy var multipeerSessionDelegateProxy = MultipeerSessionDelegateProxy(owner: self)
    private lazy var multipeerAdvertiserDelegateProxy = MultipeerAdvertiserDelegateProxy(owner: self)
    private lazy var multipeerBrowserDelegateProxy = MultipeerBrowserDelegateProxy(owner: self)

    override init() {
        super.init()
        initializeBluetoothManagers()
    }

    // MARK: - Event Emission
    private func emitDeviceFound(device: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        let advertisingPayload = advertisingDataPayload(from: advertisementData)

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
        if let localName = advertisingPayload["completeLocalName"] as? String {
            deviceData["localName"] = localName
        }

        if let serviceUUIDs = advertisingPayload["serviceUUIDs"] as? [String] {
            deviceData["serviceUUIDs"] = serviceUUIDs
        }

        if let manufacturerData = advertisingPayload["manufacturerData"] as? String {
            deviceData["manufacturerData"] = manufacturerData
        }

        if let txPowerLevel = advertisingPayload["txPowerLevel"] as? Double {
            deviceData["txPowerLevel"] = Int(txPowerLevel)
        }

        if let isConnectable = advertisingPayload["isConnectable"] as? Bool {
            deviceData["isConnectable"] = isConnectable
        }

        deviceData["advertisingData"] = advertisingPayload

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
            processAdvertisingData(advertisingDataTypes, into: &advertisingData)
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

        currentAdvertisingData = normalizeAdvertisingData(
            options.advertisingData,
            serviceUUIDs: options.serviceUUIDs,
            localName: options.localName
        )

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

        currentAdvertisingData = normalizeAdvertisingData(advertisingData, serviceUUIDs: nil, localName: nil)
        peripheralManager.startAdvertising(newAdvertisingData)
    }

    func getAdvertisingData() throws -> Promise<AdvertisingDataTypes> {
        let promise = Promise<AdvertisingDataTypes>()
        promise.resolve(withResult: self.currentAdvertisingData ?? AdvertisingDataTypes())
        return promise
    }

    func stopAdvertising() throws {
        peripheralManager?.stopAdvertising()
        peripheralManager?.removeAllServices()
        peripheralServices.removeAll()
        peripheralCharacteristicValues.removeAll()
        subscribedCentrals.removeAll()
        pendingSubscriberUpdates.removeAll()
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
        configuredServices = services
        peripheralCharacteristicValues.removeAll()
        subscribedCentrals.removeAll()
        pendingSubscriberUpdates.removeAll()

        NSLog("[MunimBluetooth] Setting up %d services", services.count)

        var createdServices: [(GATTService, CBMutableService)] = []

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
                let hasWriteProperty = properties.contains(.write) || properties.contains(.writeWithoutResponse)

                let initialValue = characteristic.value.flatMap { hexStringToData($0) }
                let characteristicValueKey = peripheralCharacteristicKey(
                    serviceUUID: service.uuid,
                    characteristicUUID: characteristic.uuid
                )
                if let initialValue = initialValue {
                    peripheralCharacteristicValues[characteristicValueKey] = initialValue
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
                    value: nil,
                    permissions: permissions
                )
                mutableChar.descriptors = characteristic.descriptors?.compactMap {
                    makeMutableDescriptor(from: $0)
                }

                characteristics.append(mutableChar)
                NSLog("[MunimBluetooth] Characteristic added: %@ with properties: %lu, hasValue: %@",
                      characteristic.uuid, properties.rawValue, initialValue != nil ? "YES" : "NO")
            }

            mutableService.characteristics = characteristics
            createdServices.append((service, mutableService))
        }

        let servicesByUUID = Dictionary(
            uniqueKeysWithValues: createdServices.map { ($0.0.uuid.lowercased(), $0.1) }
        )

        for (service, mutableService) in createdServices {
            mutableService.includedServices = service.includedServices?.compactMap {
                servicesByUUID[$0.lowercased()]
            }
            peripheralServices.append(mutableService)

            NSLog("[MunimBluetooth] Adding service to peripheral manager: %@", service.uuid)
            peripheralManager.add(mutableService)
        }

        NSLog("[MunimBluetooth] All services added successfully")
    }

    func updateCharacteristicValue(serviceUUID: String, characteristicUUID: String, value: String, notify: Bool?) throws -> Promise<Void> {
        let promise = Promise<Void>()
        guard let data = hexStringToData(value) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Value must be a hex string"]))
            return promise
        }

        let key = peripheralCharacteristicKey(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID)
        peripheralCharacteristicValues[key] = data

        if notify == true {
            guard let characteristic = findLocalMutableCharacteristic(serviceUUID: serviceUUID, characteristicUUID: characteristicUUID) else {
                promise.reject(withError: NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Local characteristic not found"]))
                return promise
            }

            guard characteristic.properties.contains(.notify) || characteristic.properties.contains(.indicate) else {
                promise.reject(withError: NSError(domain: "MunimBluetooth", code: 3, userInfo: [NSLocalizedDescriptionKey: "Characteristic does not support notify or indicate"]))
                return promise
            }

            sendValueToSubscribedCentrals(characteristic: characteristic, value: data)
        }

        promise.resolve(withResult: ())
        return promise
    }

    // MARK: - Central/Manager Features

    func isBluetoothEnabled() throws -> Promise<Bool> {
        let promise = Promise<Bool>()
        let isEnabled = self.centralManager?.state == .poweredOn
        promise.resolve(withResult: isEnabled)
        return promise
    }

    func requestBluetoothPermission() throws -> Promise<Bool> {
        let promise = Promise<Bool>()
        promise.resolve(withResult: CBManager.authorization == .allowedAlways)
        return promise
    }

    func getCapabilities() throws -> Promise<BluetoothCapabilities> {
        let promise = Promise<BluetoothCapabilities>()
        promise.resolve(withResult: BluetoothCapabilities(
            platform: "ios",
            supportsBleCentral: true,
            supportsBlePeripheral: true,
            supportsDescriptors: true,
            supportsIncludedServices: true,
            supportsMtu: false,
            supportsPhy: false,
            supportsBonding: false,
            supportsExtendedAdvertising: false,
            supportsL2cap: true,
            supportsClassicBluetooth: false,
            supportsBackgroundBle: true,
            supportsMultipeerConnectivity: true
        ))
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

        guard let peripheral = resolvePeripheral(deviceId: deviceId) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not found"]))
            return promise
        }

        pendingConnectionPromises[deviceId] = promise
        scheduleConnectionTimeout(deviceId: deviceId, peripheral: peripheral)
        peripheral.delegate = peripheralDelegateProxy
        self.centralManager?.connect(peripheral, options: nil)
        return promise
    }

    func disconnect(deviceId: String) throws {
        let peripheral = connectedPeripherals[deviceId] ?? discoveredPeripherals[deviceId]
        if let peripheral = peripheral {
            centralManager?.cancelPeripheralConnection(peripheral)
        }
        connectedPeripherals.removeValue(forKey: deviceId)
        connectionTimeouts.removeValue(forKey: deviceId)?.cancel()
        rejectPendingOperations(for: deviceId, error: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Disconnected from \(deviceId)"]))
    }

    func discoverServices(deviceId: String) throws -> Promise<[GATTService]> {
        let promise = Promise<[GATTService]>()
        guard let peripheral = self.connectedPeripherals[deviceId] else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
            return promise
        }

        if let services = peripheral.services,
           services.allSatisfy({ $0.characteristics != nil && $0.includedServices != nil }) {
            promise.resolve(withResult: buildGATTServices(from: services))
            return promise
        }

        pendingServiceDiscoveryPromises[deviceId] = promise
        scheduleOperationTimeout(
            key: "services|\(deviceId.lowercased())",
            message: "Service discovery timed out for \(deviceId)"
        ) { [weak self] in
            self?.pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.reject(withError: NSError(
                domain: "MunimBluetooth",
                code: 408,
                userInfo: [NSLocalizedDescriptionKey: "Service discovery timed out for \(deviceId)"]
            ))
            self?.pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
        }
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

        let key = characteristicKey(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristicUUID)
        pendingReadPromises[key] = promise
        scheduleOperationTimeout(key: "read|\(key)", message: "Characteristic read timed out for \(key)") { [weak self] in
            self?.pendingReadPromises.removeValue(forKey: key)?.reject(withError: NSError(
                domain: "MunimBluetooth",
                code: 408,
                userInfo: [NSLocalizedDescriptionKey: "Characteristic read timed out for \(key)"]
            ))
        }
        peripheral.readValue(for: characteristic)
        return promise
    }

    func readDescriptor(deviceId: String, serviceUUID: String, characteristicUUID: String, descriptorUUID: String) throws -> Promise<DescriptorValue> {
        let promise = Promise<DescriptorValue>()
        guard let peripheral = connectedPeripherals[deviceId],
              let characteristic = findCharacteristic(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristicUUID) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device, service, or characteristic not found"]))
            return promise
        }

        let key = descriptorKey(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristicUUID, descriptorUUID: descriptorUUID)
        pendingDescriptorReadPromises[key] = promise
        scheduleOperationTimeout(key: "descriptorRead|\(key)", message: "Descriptor read timed out for \(key)") { [weak self] in
            self?.pendingDescriptorReadPromises.removeValue(forKey: key)?.reject(withError: NSError(
                domain: "MunimBluetooth",
                code: 408,
                userInfo: [NSLocalizedDescriptionKey: "Descriptor read timed out for \(key)"]
            ))
        }
        if let descriptor = findDescriptor(characteristic: characteristic, descriptorUUID: descriptorUUID) {
            peripheral.readValue(for: descriptor)
        } else {
            peripheral.discoverDescriptors(for: characteristic)
        }
        return promise
    }

    func writeCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String, value: String, writeType: WriteType?) throws -> Promise<Void> {
        let promise = Promise<Void>()

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

        guard let data = hexStringToData(value) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid hex string for characteristic write"]))
            return promise
        }

        let cbWriteType: CBCharacteristicWriteType = writeType == .writewithoutresponse ? .withoutResponse : .withResponse
        if cbWriteType == .withoutResponse {
            guard characteristic.properties.contains(.writeWithoutResponse) else {
                promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Characteristic does not support writeWithoutResponse"]))
                return promise
            }
            peripheral.writeValue(data, for: characteristic, type: .withoutResponse)
            promise.resolve(withResult: ())
            return promise
        }

        guard characteristic.properties.contains(.write) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Characteristic does not support write"]))
            return promise
        }

        let key = characteristicKey(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristicUUID)
        pendingWritePromises[key] = promise
        scheduleOperationTimeout(key: "write|\(key)", message: "Characteristic write timed out for \(key)") { [weak self] in
            self?.pendingWritePromises.removeValue(forKey: key)?.reject(withError: NSError(
                domain: "MunimBluetooth",
                code: 408,
                userInfo: [NSLocalizedDescriptionKey: "Characteristic write timed out for \(key)"]
            ))
        }
        peripheral.writeValue(data, for: characteristic, type: .withResponse)
        return promise
    }

    func writeDescriptor(deviceId: String, serviceUUID: String, characteristicUUID: String, descriptorUUID: String, value: String) throws -> Promise<Void> {
        let promise = Promise<Void>()
        guard let peripheral = connectedPeripherals[deviceId],
              let characteristic = findCharacteristic(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristicUUID) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device, service, or characteristic not found"]))
            return promise
        }
        guard let data = hexStringToData(value) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid hex string for descriptor write"]))
            return promise
        }

        let key = descriptorKey(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristicUUID, descriptorUUID: descriptorUUID)
        pendingDescriptorWritePromises[key] = promise
        pendingDescriptorWriteValues[key] = data
        scheduleOperationTimeout(key: "descriptorWrite|\(key)", message: "Descriptor write timed out for \(key)") { [weak self] in
            self?.pendingDescriptorWriteValues.removeValue(forKey: key)
            self?.pendingDescriptorWritePromises.removeValue(forKey: key)?.reject(withError: NSError(
                domain: "MunimBluetooth",
                code: 408,
                userInfo: [NSLocalizedDescriptionKey: "Descriptor write timed out for \(key)"]
            ))
        }
        if let descriptor = findDescriptor(characteristic: characteristic, descriptorUUID: descriptorUUID) {
            peripheral.writeValue(data, for: descriptor)
        } else {
            peripheral.discoverDescriptors(for: characteristic)
        }
        return promise
    }

    func subscribeToCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        guard let peripheral = connectedPeripherals[deviceId],
              let characteristic = findCharacteristic(
                deviceId: deviceId,
                serviceUUID: serviceUUID,
                characteristicUUID: characteristicUUID
              ) else {
            return
        }

        peripheral.setNotifyValue(true, for: characteristic)
    }

    func unsubscribeFromCharacteristic(deviceId: String, serviceUUID: String, characteristicUUID: String) throws {
        guard let peripheral = connectedPeripherals[deviceId],
              let characteristic = findCharacteristic(
                deviceId: deviceId,
                serviceUUID: serviceUUID,
                characteristicUUID: characteristicUUID
              ) else {
            return
        }

        peripheral.setNotifyValue(false, for: characteristic)
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
        scheduleOperationTimeout(key: "rssi|\(deviceId.lowercased())", message: "RSSI read timed out for \(deviceId)") { [weak self] in
            self?.pendingRSSIPromises.removeValue(forKey: deviceId)?.reject(withError: NSError(
                domain: "MunimBluetooth",
                code: 408,
                userInfo: [NSLocalizedDescriptionKey: "RSSI read timed out for \(deviceId)"]
            ))
        }
        peripheral.readRSSI()
        return promise
    }

    func requestMTU(deviceId: String, mtu: Double) throws -> Promise<Double> {
        unsupportedPromise("requestMTU is not exposed by CoreBluetooth on iOS")
    }

    func setPreferredPhy(deviceId: String, txPhy: BluetoothPhy, rxPhy: BluetoothPhy, phyOption: BluetoothPhyOption?) throws -> Promise<Void> {
        unsupportedPromise("setPreferredPhy is not exposed by CoreBluetooth on iOS")
    }

    func readPhy(deviceId: String) throws -> Promise<PhyStatus> {
        unsupportedPromise("readPhy is not exposed by CoreBluetooth on iOS")
    }

    func getBondState(deviceId: String) throws -> Promise<BondState> {
        let promise = Promise<BondState>()
        promise.resolve(withResult: .unsupported)
        return promise
    }

    func createBond(deviceId: String) throws -> Promise<BondState> {
        unsupportedPromise("Explicit bonding is handled by iOS and is not exposed through CoreBluetooth")
    }

    func removeBond(deviceId: String) throws -> Promise<BondState> {
        unsupportedPromise("Removing bonds is not exposed by iOS public APIs")
    }

    func startExtendedAdvertising(options: ExtendedAdvertisingOptions) throws -> Promise<String> {
        unsupportedPromise("BLE extended advertising is not exposed by CoreBluetooth on iOS")
    }

    func stopExtendedAdvertising(advertisingId: String) throws {}

    func publishL2CAPChannel(encryptionRequired: Bool?) throws -> Promise<L2CAPChannel> {
        let promise = Promise<L2CAPChannel>()
        guard let peripheralManager = peripheralManager,
              peripheralManager.state == .poweredOn else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not powered on"]))
            return promise
        }

        pendingL2CAPPublishPromises.append(promise)
        peripheralManager.publishL2CAPChannel(withEncryption: encryptionRequired ?? false)
        return promise
    }

    func unpublishL2CAPChannel(psm: Double) throws {
        let psmValue = CBL2CAPPSM(UInt16(psm))
        peripheralManager?.unpublishL2CAPChannel(psmValue)
        publishedL2CAPPSMs.remove(psmValue)
    }

    func openL2CAPChannel(deviceId: String, psm: Double) throws -> Promise<L2CAPChannel> {
        let promise = Promise<L2CAPChannel>()
        guard let peripheral = connectedPeripherals[deviceId] else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Device not connected"]))
            return promise
        }

        let psmValue = CBL2CAPPSM(UInt16(psm))
        pendingL2CAPOpenPromises[deviceId] = promise
        pendingL2CAPOpenPSMs[deviceId] = psmValue
        scheduleOperationTimeout(key: "l2capOpen|\(deviceId.lowercased())", message: "L2CAP open timed out for \(deviceId)") { [weak self] in
            self?.pendingL2CAPOpenPSMs.removeValue(forKey: deviceId)
            self?.pendingL2CAPOpenPromises.removeValue(forKey: deviceId)?.reject(withError: NSError(
                domain: "MunimBluetooth",
                code: 408,
                userInfo: [NSLocalizedDescriptionKey: "L2CAP open timed out for \(deviceId)"]
            ))
        }
        peripheral.openL2CAPChannel(psmValue)
        return promise
    }

    func closeL2CAPChannel(channelId: String) throws {
        closeL2CAPChannelInternal(channelId: channelId, emitEvent: true)
    }

    func sendL2CAPData(channelId: String, value: String) throws -> Promise<Void> {
        let promise = Promise<Void>()
        guard let channel = l2capChannels[channelId] else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "L2CAP channel not open"]))
            return promise
        }
        guard let data = hexStringToData(value) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Value must be a hex string"]))
            return promise
        }

        let bytes = [UInt8](data)
        let written = bytes.withUnsafeBufferPointer { buffer -> Int in
            guard let baseAddress = buffer.baseAddress else { return 0 }
            return channel.outputStream.write(baseAddress, maxLength: buffer.count)
        }

        if written == bytes.count {
            promise.resolve(withResult: ())
        } else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 3, userInfo: [NSLocalizedDescriptionKey: "L2CAP write failed"]))
        }
        return promise
    }

    func startClassicScan() throws {
        throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Classic Bluetooth is not available to iOS apps through public APIs"])
    }

    func stopClassicScan() throws {}

    func connectClassic(deviceId: String, serviceUUID: String?) throws -> Promise<Void> {
        unsupportedPromise("Classic Bluetooth is not available to iOS apps through public APIs")
    }

    func startClassicServer(serviceUUID: String?, serviceName: String?) throws -> Promise<Void> {
        unsupportedPromise("Classic Bluetooth is not available to iOS apps through public APIs")
    }

    func stopClassicServer(serviceUUID: String?) throws {}

    func disconnectClassic(deviceId: String) throws {}

    func writeClassic(deviceId: String, value: String) throws -> Promise<Void> {
        unsupportedPromise("Classic Bluetooth is not available to iOS apps through public APIs")
    }

    func startBackgroundSession(options: BackgroundSessionOptions) throws {
        isBackgroundSessionActive = true

        do {
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
            emit("backgroundSessionStarted", body: [
                "platform": "ios",
                "serviceUUIDs": options.serviceUUIDs,
                "localName": options.localName ?? NSNull()
            ])
        } catch {
            isBackgroundSessionActive = false
            emit("backgroundSessionStartFailed", body: [
                "platform": "ios",
                "error": error.localizedDescription
            ])
            throw error
        }
    }

    func stopBackgroundSession() throws {
        isBackgroundSessionActive = false
        try stopScan()
        try stopAdvertising()
        emit("backgroundSessionStopped", body: ["platform": "ios"])
    }

    func startMultipeerSession(options: MultipeerSessionOptions) throws {
        do {
            let serviceType = try validateMultipeerServiceType(options.serviceType)
            stopMultipeerSessionInternal(emitEvent: false)

            let peerID = MCPeerID(displayName: normalizedMultipeerDisplayName(options.displayName))
            let session = MCSession(
                peer: peerID,
                securityIdentity: nil,
                encryptionPreference: multipeerEncryptionPreference(options.encryptionPreference)
            )
            session.delegate = multipeerSessionDelegateProxy

            multipeerPeerID = peerID
            multipeerSession = session
            multipeerServiceType = serviceType
            multipeerAutoInvite = options.autoInvite ?? true
            multipeerAutoAcceptInvitations = options.autoAcceptInvitations ?? true
            multipeerInviteTimeout = TimeInterval(max(1, options.inviteTimeout ?? 30))
            multipeerLocalRuntimePeerId = UUID().uuidString

            let advertiser = MCNearbyServiceAdvertiser(
                peer: peerID,
                discoveryInfo: multipeerDiscoveryInfoDictionary(options.discoveryInfo),
                serviceType: serviceType
            )
            advertiser.delegate = multipeerAdvertiserDelegateProxy
            multipeerAdvertiser = advertiser

            let browser = MCNearbyServiceBrowser(peer: peerID, serviceType: serviceType)
            browser.delegate = multipeerBrowserDelegateProxy
            multipeerBrowser = browser

            advertiser.startAdvertisingPeer()
            browser.startBrowsingForPeers()

            NSLog("[MunimBluetooth] Multipeer started service=%@ peer=%@", serviceType, peerID.displayName)

            emit("multipeerStarted", body: [
                "platform": "ios",
                "serviceType": serviceType,
                "peerId": multipeerLocalRuntimePeerId,
                "displayName": peerID.displayName
            ])
        } catch {
            emit("multipeerStartFailed", body: [
                "platform": "ios",
                "error": error.localizedDescription
            ])
            throw error
        }
    }

    func stopMultipeerSession() throws {
        stopMultipeerSessionInternal(emitEvent: true)
    }

    func inviteMultipeerPeer(peerId: String) throws {
        guard let browser = multipeerBrowser,
              let session = multipeerSession else {
            throw NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Multipeer session is not active"])
        }

        guard let peerID = multipeerPeersById[peerId] else {
            throw NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Multipeer peer not found"])
        }

        browser.invitePeer(peerID, to: session, withContext: nil, timeout: multipeerInviteTimeout)
    }

    func getMultipeerPeers() throws -> Promise<[MultipeerPeer]> {
        let promise = Promise<[MultipeerPeer]>()
        let peers = multipeerPeersById.values.map { multipeerPeerPayload(for: $0) }
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
        promise.resolve(withResult: peers)
        return promise
    }

    func sendMultipeerMessage(value: String, peerIds: [String]?, reliable: Bool?) throws -> Promise<Void> {
        let promise = Promise<Void>()

        guard let session = multipeerSession else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Multipeer session is not active"]))
            return promise
        }

        guard let data = hexStringToData(value) else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 2, userInfo: [NSLocalizedDescriptionKey: "Value must be a hex string"]))
            return promise
        }

        let targetPeers: [MCPeerID]
        if let peerIds = peerIds, !peerIds.isEmpty {
            let requestedPeers = peerIds.compactMap { multipeerPeersById[$0] }
            targetPeers = session.connectedPeers.filter { requestedPeers.contains($0) }
        } else {
            targetPeers = session.connectedPeers
        }

        guard !targetPeers.isEmpty else {
            promise.reject(withError: NSError(domain: "MunimBluetooth", code: 3, userInfo: [NSLocalizedDescriptionKey: "No connected Multipeer peers to send to"]))
            return promise
        }

        do {
            try session.send(data, toPeers: targetPeers, with: (reliable ?? true) ? .reliable : .unreliable)
            promise.resolve(withResult: ())
        } catch {
            promise.reject(withError: error)
        }

        return promise
    }

    func addListener(eventName: String) throws {
        // Event management
    }

    func removeListeners(count: Double) throws {
        // Event management
    }

    // MARK: - Helper Methods

    private func validateMultipeerServiceType(_ serviceType: String) throws -> String {
        let trimmed = serviceType.trimmingCharacters(in: .whitespacesAndNewlines)
        let allowedCharacters = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyz0123456789-")

        guard !trimmed.isEmpty, trimmed.count <= 15 else {
            throw NSError(domain: "MunimBluetooth", code: 1001, userInfo: [NSLocalizedDescriptionKey: "Multipeer serviceType must be 1-15 characters"])
        }

        guard trimmed.unicodeScalars.allSatisfy({ allowedCharacters.contains($0) }),
              trimmed.first != "-",
              trimmed.last != "-" else {
            throw NSError(domain: "MunimBluetooth", code: 1002, userInfo: [NSLocalizedDescriptionKey: "Multipeer serviceType may contain only lowercase letters, numbers, and interior hyphens"])
        }

        return trimmed
    }

    private func normalizedMultipeerDisplayName(_ displayName: String?) -> String {
        var value = displayName?.trimmingCharacters(in: .whitespacesAndNewlines)
        if value?.isEmpty != false {
            value = ProcessInfo.processInfo.processName
        }

        var result = value ?? "Munim Peer"
        while result.utf8.count > 63 {
            result.removeLast()
        }

        return result.isEmpty ? "Munim Peer" : result
    }

    private func multipeerDiscoveryInfoDictionary(_ entries: [MultipeerDiscoveryInfoEntry]?) -> [String: String]? {
        guard let entries else {
            return nil
        }

        var dictionary: [String: String] = [:]
        for entry in entries {
            let key = entry.key.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !key.isEmpty else {
                continue
            }
            dictionary[key] = entry.value
        }

        return dictionary.isEmpty ? nil : dictionary
    }

    private func multipeerDiscoveryInfoEntries(_ dictionary: [String: String]?) -> [MultipeerDiscoveryInfoEntry]? {
        guard let dictionary, !dictionary.isEmpty else {
            return nil
        }

        return dictionary.keys.sorted().map { key in
            MultipeerDiscoveryInfoEntry(key: key, value: dictionary[key] ?? "")
        }
    }

    private func multipeerEncryptionPreference(_ preference: MultipeerEncryptionPreference?) -> MCEncryptionPreference {
        switch preference {
        case .some(.none):
            return .none
        case .some(.optional):
            return .optional
        case .some(.required), nil:
            return .required
        }
    }

    private func multipeerPeerState(_ state: MCSessionState) -> MultipeerPeerState {
        switch state {
        case .connected:
            return .connected
        case .connecting:
            return .connecting
        case .notConnected:
            return .notconnected
        @unknown default:
            return .notconnected
        }
    }

    private func multipeerRuntimeId(for peerID: MCPeerID) -> String {
        if let id = multipeerPeerIds[peerID] {
            return id
        }

        let id = UUID().uuidString
        multipeerPeerIds[peerID] = id
        multipeerPeersById[id] = peerID
        if multipeerStatesById[id] == nil {
            multipeerStatesById[id] = .notconnected
        }
        return id
    }

    private func multipeerPeerPayload(for peerID: MCPeerID) -> MultipeerPeer {
        let id = multipeerRuntimeId(for: peerID)
        return MultipeerPeer(
            id: id,
            displayName: peerID.displayName,
            state: multipeerStatesById[id] ?? .notconnected,
            discoveryInfo: multipeerDiscoveryInfoById[id]
        )
    }

    private func multipeerPeerEventBody(for peerID: MCPeerID) -> [String: Any] {
        let peer = multipeerPeerPayload(for: peerID)
        var body: [String: Any] = [
            "id": peer.id,
            "displayName": peer.displayName,
            "state": peer.state.stringValue
        ]

        if let discoveryInfo = peer.discoveryInfo {
            body["discoveryInfo"] = discoveryInfo.map { ["key": $0.key, "value": $0.value] }
        }

        return body
    }

    private func stopMultipeerSessionInternal(emitEvent: Bool) {
        let wasActive = multipeerSession != nil || multipeerAdvertiser != nil || multipeerBrowser != nil

        multipeerBrowser?.stopBrowsingForPeers()
        multipeerBrowser?.delegate = nil
        multipeerBrowser = nil

        multipeerAdvertiser?.stopAdvertisingPeer()
        multipeerAdvertiser?.delegate = nil
        multipeerAdvertiser = nil

        multipeerSession?.disconnect()
        multipeerSession?.delegate = nil
        multipeerSession = nil

        multipeerPeerID = nil
        multipeerServiceType = nil
        multipeerPeerIds.removeAll()
        multipeerPeersById.removeAll()
        multipeerDiscoveryInfoById.removeAll()
        multipeerStatesById.removeAll()

        if emitEvent && wasActive {
            emit("multipeerStopped", body: ["platform": "ios"])
        }
    }

    private func hexStringToData(_ hex: String) -> Data? {
        var data = Data()
        var hex = hex.trimmingCharacters(in: .whitespacesAndNewlines)

        if hex.count % 2 != 0 {
            hex = "0" + hex
        }

        var index = hex.startIndex
        while index < hex.endIndex {
            let nextIndex = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<nextIndex], radix: 16) else {
                return nil
            }
            data.append(byte)
            index = nextIndex
        }

        return data.isEmpty ? nil : data
    }

    private func initializeBluetoothManagers() {
        let createManagers = {
            self.peripheralManager = CBPeripheralManager(
                delegate: self.peripheralManagerDelegateProxy,
                queue: nil,
                options: self.coreBluetoothOptions(
                    restoreIdentifier: peripheralRestoreIdentifier,
                    requiredBackgroundMode: "bluetooth-peripheral",
                    optionKey: CBPeripheralManagerOptionRestoreIdentifierKey
                )
            )
            self.centralManager = CBCentralManager(
                delegate: self.centralManagerDelegateProxy,
                queue: nil,
                options: self.coreBluetoothOptions(
                    restoreIdentifier: centralRestoreIdentifier,
                    requiredBackgroundMode: "bluetooth-central",
                    optionKey: CBCentralManagerOptionRestoreIdentifierKey
                )
            )
        }

        if Thread.isMainThread {
            createManagers()
        } else {
            DispatchQueue.main.sync(execute: createManagers)
        }
    }

    private func coreBluetoothOptions(
        restoreIdentifier: String,
        requiredBackgroundMode: String,
        optionKey: String
    ) -> [String: Any]? {
        guard let backgroundModes = Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String],
              backgroundModes.contains(requiredBackgroundMode) else {
            return nil
        }

        return [optionKey: restoreIdentifier]
    }

    private func dataToHexString(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }

    private func advertisingDataPayload(from advertisementData: [String: Any]) -> [String: Any] {
        var payload: [String: Any] = [:]

        if let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String {
            payload["completeLocalName"] = localName
        }

        if let serviceUUIDs = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] {
            let uuidStrings = serviceUUIDs.map { $0.uuidString }
            payload["serviceUUIDs"] = uuidStrings
            addServiceUUIDBuckets(uuidStrings, to: &payload)
        }

        if let overflowServiceUUIDs = advertisementData[CBAdvertisementDataOverflowServiceUUIDsKey] as? [CBUUID] {
            let uuidStrings = overflowServiceUUIDs.map { $0.uuidString }
            payload["overflowServiceUUIDs"] = uuidStrings
            addIncompleteServiceUUIDBuckets(uuidStrings, to: &payload)
        }

        if let solicitedServiceUUIDs = advertisementData[CBAdvertisementDataSolicitedServiceUUIDsKey] as? [CBUUID] {
            addSolicitedServiceUUIDBuckets(solicitedServiceUUIDs.map { $0.uuidString }, to: &payload)
        }

        if let serviceData = advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data] {
            let entries = serviceData.map { uuid, data in
                ["uuid": uuid.uuidString, "data": dataToHexString(data)]
            }.sorted { lhs, rhs in
                (lhs["uuid"] ?? "") < (rhs["uuid"] ?? "")
            }

            if !entries.isEmpty {
                payload["serviceData"] = entries
                addServiceDataBuckets(entries, to: &payload)
            }
        }

        if let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data {
            payload["manufacturerData"] = dataToHexString(manufacturerData)
        }

        if let txPowerLevel = advertisementData[CBAdvertisementDataTxPowerLevelKey] as? NSNumber {
            payload["txPowerLevel"] = txPowerLevel.doubleValue
        }

        if let isConnectable = advertisementData[CBAdvertisementDataIsConnectable] as? NSNumber {
            payload["isConnectable"] = isConnectable.boolValue
        }

        return payload
    }

    private func addServiceUUIDBuckets(_ uuidStrings: [String], to payload: inout [String: Any]) {
        addUUIDBuckets(
            uuidStrings,
            key16: "completeServiceUUIDs16",
            key32: "completeServiceUUIDs32",
            key128: "completeServiceUUIDs128",
            to: &payload
        )
    }

    private func addIncompleteServiceUUIDBuckets(_ uuidStrings: [String], to payload: inout [String: Any]) {
        addUUIDBuckets(
            uuidStrings,
            key16: "incompleteServiceUUIDs16",
            key32: "incompleteServiceUUIDs32",
            key128: "incompleteServiceUUIDs128",
            to: &payload
        )
    }

    private func addSolicitedServiceUUIDBuckets(_ uuidStrings: [String], to payload: inout [String: Any]) {
        addUUIDBuckets(
            uuidStrings,
            key16: "serviceSolicitationUUIDs16",
            key32: "serviceSolicitationUUIDs32",
            key128: "serviceSolicitationUUIDs128",
            to: &payload
        )
    }

    private func addUUIDBuckets(
        _ uuidStrings: [String],
        key16: String,
        key32: String,
        key128: String,
        to payload: inout [String: Any]
    ) {
        let uuid16 = uuidStrings.filter { uuidBitWidth($0) == 16 }
        let uuid32 = uuidStrings.filter { uuidBitWidth($0) == 32 }
        let uuid128 = uuidStrings.filter { uuidBitWidth($0) == 128 }

        if !uuid16.isEmpty {
            payload[key16] = uuid16
        }
        if !uuid32.isEmpty {
            payload[key32] = uuid32
        }
        if !uuid128.isEmpty {
            payload[key128] = uuid128
        }
    }

    private func addServiceDataBuckets(_ entries: [[String: String]], to payload: inout [String: Any]) {
        let serviceData16 = entries.filter { uuidBitWidth($0["uuid"] ?? "") == 16 }
        let serviceData32 = entries.filter { uuidBitWidth($0["uuid"] ?? "") == 32 }
        let serviceData128 = entries.filter { uuidBitWidth($0["uuid"] ?? "") == 128 }

        if !serviceData16.isEmpty {
            payload["serviceData16"] = serviceData16
        }
        if !serviceData32.isEmpty {
            payload["serviceData32"] = serviceData32
        }
        if !serviceData128.isEmpty {
            payload["serviceData128"] = serviceData128
        }
    }

    private func uuidBitWidth(_ uuidString: String) -> Int {
        switch uuidString.replacingOccurrences(of: "-", with: "").count {
        case 4:
            return 16
        case 8:
            return 32
        default:
            return 128
        }
    }

    private func processAdvertisingData(_ data: AdvertisingDataTypes, into advertisingData: inout [String: Any]) {
        if let localName = data.completeLocalName ?? data.shortenedLocalName {
            advertisingData[CBAdvertisementDataLocalNameKey] = localName
        }

        var serviceUUIDs: [String] = []
        serviceUUIDs.append(contentsOf: data.incompleteServiceUUIDs16 ?? [])
        serviceUUIDs.append(contentsOf: data.completeServiceUUIDs16 ?? [])
        serviceUUIDs.append(contentsOf: data.incompleteServiceUUIDs32 ?? [])
        serviceUUIDs.append(contentsOf: data.completeServiceUUIDs32 ?? [])
        serviceUUIDs.append(contentsOf: data.incompleteServiceUUIDs128 ?? [])
        serviceUUIDs.append(contentsOf: data.completeServiceUUIDs128 ?? [])

        if !serviceUUIDs.isEmpty {
            advertisingData[CBAdvertisementDataServiceUUIDsKey] = serviceUUIDs.map { CBUUID(string: $0) }
        }
    }

    private func normalizeAdvertisingData(
        _ data: AdvertisingDataTypes?,
        serviceUUIDs: [String]?,
        localName: String?
    ) -> AdvertisingDataTypes {
        AdvertisingDataTypes(
            flags: nil,
            incompleteServiceUUIDs16: nil,
            completeServiceUUIDs16: data?.completeServiceUUIDs16,
            incompleteServiceUUIDs32: nil,
            completeServiceUUIDs32: data?.completeServiceUUIDs32,
            incompleteServiceUUIDs128: nil,
            completeServiceUUIDs128: serviceUUIDs ?? data?.completeServiceUUIDs128,
            shortenedLocalName: nil,
            completeLocalName: data?.completeLocalName ?? localName,
            txPowerLevel: nil,
            serviceSolicitationUUIDs16: nil,
            serviceSolicitationUUIDs128: nil,
            serviceData16: nil,
            serviceData32: nil,
            serviceData128: nil,
            appearance: nil,
            serviceSolicitationUUIDs32: nil,
            manufacturerData: nil
        )
    }

    private func makeMutableDescriptor(from descriptor: GATTDescriptor) -> CBMutableDescriptor? {
        let descriptorUUID = CBUUID(string: descriptor.uuid)
        let normalizedUUID = descriptorUUID.uuidString.lowercased()
        let userDescriptionUUID = CBUUID(string: CBUUIDCharacteristicUserDescriptionString).uuidString.lowercased()
        let formatUUID = CBUUID(string: CBUUIDCharacteristicFormatString).uuidString.lowercased()

        if normalizedUUID == userDescriptionUUID {
            return CBMutableDescriptor(
                type: descriptorUUID,
                value: descriptorStringValue(descriptor.value) as NSString
            )
        }

        if normalizedUUID == formatUUID {
            return CBMutableDescriptor(
                type: descriptorUUID,
                value: descriptor.value.flatMap { hexStringToData($0) } as NSData?
            )
        }

        NSLog(
            "[MunimBluetooth] Skipping descriptor %@ on iOS. CoreBluetooth only supports local mutable user-description and characteristic-format descriptors.",
            descriptor.uuid
        )
        return nil
    }

    private func descriptorStringValue(_ value: String?) -> String {
        guard let value = value else {
            return ""
        }

        if let data = hexStringToData(value),
           let decoded = String(data: data, encoding: .utf8) {
            return decoded
        }

        return value
    }

    private func emit(_ eventName: String, body: [String: Any]) {
        MunimBluetoothEventEmitter.emitOrQueue(eventName, body: body)
    }

    private func characteristicKey(deviceId: String, serviceUUID: String, characteristicUUID: String) -> String {
        "\(deviceId.lowercased())|\(serviceUUID.lowercased())|\(characteristicUUID.lowercased())"
    }

    private func descriptorKey(deviceId: String, serviceUUID: String, characteristicUUID: String, descriptorUUID: String) -> String {
        "\(characteristicKey(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristicUUID))|\(descriptorUUID.lowercased())"
    }

    private func peripheralCharacteristicKey(serviceUUID: String, characteristicUUID: String) -> String {
        "\(serviceUUID.lowercased())|\(characteristicUUID.lowercased())"
    }

    private func peripheralCharacteristicKey(_ characteristic: CBCharacteristic) -> String {
        peripheralCharacteristicKey(
            serviceUUID: characteristic.service?.uuid.uuidString ?? "",
            characteristicUUID: characteristic.uuid.uuidString
        )
    }

    private func resolvePeripheral(deviceId: String) -> CBPeripheral? {
        if let uuid = UUID(uuidString: deviceId),
           let centralManager = centralManager {
            let peripherals = centralManager.retrievePeripherals(withIdentifiers: [uuid])
            if let peripheral = peripherals.first {
                discoveredPeripherals[deviceId] = peripheral
                return peripheral
            }
        }

        return discoveredPeripherals[deviceId]
    }

    private func scheduleConnectionTimeout(deviceId: String, peripheral: CBPeripheral) {
        connectionTimeouts.removeValue(forKey: deviceId)?.cancel()

        let workItem = DispatchWorkItem { [weak self, weak peripheral] in
            guard let self = self,
                  let pendingPromise = self.pendingConnectionPromises.removeValue(forKey: deviceId) else {
                return
            }

            self.connectionTimeouts.removeValue(forKey: deviceId)
            if let peripheral = peripheral {
                self.centralManager?.cancelPeripheralConnection(peripheral)
            }
            self.connectedPeripherals.removeValue(forKey: deviceId)
            pendingPromise.reject(withError: NSError(
                domain: "MunimBluetooth",
                code: 408,
                userInfo: [NSLocalizedDescriptionKey: "Connection timed out for \(deviceId)"]
            ))
        }

        connectionTimeouts[deviceId] = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 15.0, execute: workItem)
    }

    private func scheduleOperationTimeout(key: String, message: String, onTimeout: @escaping () -> Void) {
        operationTimeouts.removeValue(forKey: key)?.cancel()

        let workItem = DispatchWorkItem { [weak self] in
            _ = message
            onTimeout()
            self?.operationTimeouts.removeValue(forKey: key)
        }

        operationTimeouts[key] = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 15.0, execute: workItem)
    }

    private func cancelOperationTimeout(key: String) {
        operationTimeouts.removeValue(forKey: key)?.cancel()
    }

    private func cancelOperationTimeouts(for deviceId: String) {
        let normalizedDeviceId = deviceId.lowercased()
        for key in Array(operationTimeouts.keys) where key == "services|\(normalizedDeviceId)" ||
            key == "rssi|\(normalizedDeviceId)" ||
            key == "l2capOpen|\(normalizedDeviceId)" ||
            key.contains("|\(normalizedDeviceId)|") {
            operationTimeouts.removeValue(forKey: key)?.cancel()
        }
    }

    private func sendValueToSubscribedCentrals(characteristic: CBMutableCharacteristic, value: Data) {
        let key = peripheralCharacteristicKey(characteristic)
        let centrals = subscribedCentrals[key]
        let sent = peripheralManager?.updateValue(value, for: characteristic, onSubscribedCentrals: centrals) ?? false
        if !sent {
            pendingSubscriberUpdates.append((characteristic, value, centrals))
        }
    }

    private func buildGATTServices(from services: [CBService]) -> [GATTService] {
        services.map { service in
            GATTService(
                uuid: service.uuid.uuidString,
                characteristics: (service.characteristics ?? []).map { characteristic in
                    GATTCharacteristic(
                        uuid: characteristic.uuid.uuidString,
                        properties: mapProperties(characteristic.properties),
                        value: characteristic.value?.map { String(format: "%02x", $0) }.joined(),
                        descriptors: characteristic.descriptors?.map { descriptor in
                            GATTDescriptor(
                                uuid: descriptor.uuid.uuidString,
                                value: descriptor.value.flatMap { descriptorValueToHex($0) },
                                permissions: nil
                            )
                        }
                    )
                },
                includedServices: service.includedServices?.map { $0.uuid.uuidString }
            )
        }
    }

    private func servicePayload(_ services: [GATTService]) -> [[String: Any]] {
        services.map { service -> [String: Any] in
            let characteristicPayloads: [[String: Any]] = service.characteristics.map { characteristic in
                let descriptorPayloads: [[String: Any]] = (characteristic.descriptors ?? []).map { descriptor in
                    var descriptorPayload: [String: Any] = ["uuid": descriptor.uuid]
                    descriptorPayload["value"] = descriptor.value ?? NSNull()
                    descriptorPayload["permissions"] = descriptor.permissions ?? NSNull()
                    return descriptorPayload
                }

                var characteristicPayload: [String: Any] = [
                    "uuid": characteristic.uuid,
                    "properties": characteristic.properties,
                    "descriptors": descriptorPayloads
                ]
                characteristicPayload["value"] = characteristic.value ?? NSNull()
                return characteristicPayload
            }

            return [
                "uuid": service.uuid,
                "characteristics": characteristicPayloads,
                "includedServices": service.includedServices ?? []
            ]
        }
    }

    private func completeServiceDiscoveryStep(peripheral: CBPeripheral, deviceId: String) {
        guard let remaining = pendingCharacteristicDiscoveryCounts[deviceId] else {
            return
        }

        let nextRemaining = max(remaining - 1, 0)
        if nextRemaining > 0 {
            pendingCharacteristicDiscoveryCounts[deviceId] = nextRemaining
            return
        }

        pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
        cancelOperationTimeout(key: "services|\(deviceId.lowercased())")
        let services = buildGATTServices(from: peripheral.services ?? [])
        pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.resolve(withResult: services)
        emit("servicesDiscovered", body: [
            "deviceId": deviceId,
            "services": servicePayload(services)
        ])
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

    private func findDescriptor(characteristic: CBCharacteristic, descriptorUUID: String) -> CBDescriptor? {
        characteristic.descriptors?.first {
            $0.uuid.uuidString.caseInsensitiveCompare(descriptorUUID) == .orderedSame
        }
    }

    private func findLocalMutableCharacteristic(serviceUUID: String, characteristicUUID: String) -> CBMutableCharacteristic? {
        let matchingService = peripheralServices.first {
            $0.uuid.uuidString.caseInsensitiveCompare(serviceUUID) == .orderedSame
        }
        return matchingService?.characteristics?.compactMap { $0 as? CBMutableCharacteristic }.first {
            $0.uuid.uuidString.caseInsensitiveCompare(characteristicUUID) == .orderedSame
        }
    }

    private func descriptorValueToHex(_ value: Any) -> String? {
        if let data = value as? Data {
            return data.map { String(format: "%02x", $0) }.joined()
        }
        if let string = value as? String {
            return string.data(using: .utf8)?.map { String(format: "%02x", $0) }.joined()
        }
        if let number = value as? NSNumber {
            return String(format: "%02x", number.uint8Value)
        }
        return nil
    }

    private func dataToHex(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }

    private func registerL2CAPChannel(_ channel: CBL2CAPChannel, deviceId: String?) -> L2CAPChannel {
        let channelId = UUID().uuidString
        l2capChannels[channelId] = channel
        l2capInputStreamIds[ObjectIdentifier(channel.inputStream)] = channelId

        channel.inputStream.delegate = l2capStreamDelegateProxy
        channel.inputStream.schedule(in: .main, forMode: .default)
        channel.inputStream.open()
        channel.outputStream.schedule(in: .main, forMode: .default)
        channel.outputStream.open()

        let l2capChannel = L2CAPChannel(id: channelId, psm: Double(channel.psm), deviceId: deviceId)
        emit("l2capChannelOpened", body: [
            "channelId": channelId,
            "psm": Double(channel.psm),
            "deviceId": deviceId ?? NSNull()
        ])
        return l2capChannel
    }

    private func closeL2CAPChannelInternal(channelId: String, emitEvent: Bool) {
        guard let channel = l2capChannels.removeValue(forKey: channelId) else {
            return
        }

        l2capInputStreamIds.removeValue(forKey: ObjectIdentifier(channel.inputStream))
        channel.inputStream.delegate = nil
        channel.inputStream.remove(from: .main, forMode: .default)
        channel.inputStream.close()
        channel.outputStream.remove(from: .main, forMode: .default)
        channel.outputStream.close()

        if emitEvent {
            emit("l2capChannelClosed", body: [
                "channelId": channelId,
                "psm": Double(channel.psm),
                "deviceId": channel.peer.identifier.uuidString
            ])
        }
    }

    private func closeL2CAPChannels(for deviceId: String) {
        let channelIds = l2capChannels.compactMap { entry -> String? in
            entry.value.peer.identifier.uuidString == deviceId ? entry.key : nil
        }
        for channelId in channelIds {
            closeL2CAPChannelInternal(channelId: channelId, emitEvent: true)
        }
    }

    func handleL2CAPStream(_ aStream: Stream, eventCode: Stream.Event) {
        guard let channelId = l2capInputStreamIds[ObjectIdentifier(aStream)] else {
            return
        }

        switch eventCode {
        case .hasBytesAvailable:
            guard let inputStream = aStream as? InputStream,
                  let channel = l2capChannels[channelId] else {
                return
            }
            var buffer = [UInt8](repeating: 0, count: 4096)
            while inputStream.hasBytesAvailable {
                let count = inputStream.read(&buffer, maxLength: buffer.count)
                if count > 0 {
                    let data = Data(buffer.prefix(count))
                    emit("l2capDataReceived", body: [
                        "channelId": channelId,
                        "psm": Double(channel.psm),
                        "deviceId": channel.peer.identifier.uuidString,
                        "value": dataToHex(data)
                    ])
                } else if count < 0 {
                    closeL2CAPChannelInternal(channelId: channelId, emitEvent: true)
                    break
                } else {
                    break
                }
            }

        case .endEncountered, .errorOccurred:
            closeL2CAPChannelInternal(channelId: channelId, emitEvent: true)

        default:
            break
        }
    }

    func handleMultipeerFoundPeer(_ peerID: MCPeerID, discoveryInfo: [String: String]?) {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in
                self?.handleMultipeerFoundPeer(peerID, discoveryInfo: discoveryInfo)
            }
            return
        }

        if let localPeerID = multipeerPeerID, peerID == localPeerID {
            return
        }

        let peerId = multipeerRuntimeId(for: peerID)
        multipeerDiscoveryInfoById[peerId] = multipeerDiscoveryInfoEntries(discoveryInfo)
        if multipeerStatesById[peerId] == nil {
            multipeerStatesById[peerId] = .notconnected
        }

        emit("multipeerPeerFound", body: multipeerPeerEventBody(for: peerID))

        if multipeerAutoInvite,
           multipeerStatesById[peerId] != .connected,
           let browser = multipeerBrowser,
           let session = multipeerSession {
            NSLog("[MunimBluetooth] Multipeer inviting peer=%@ id=%@", peerID.displayName, peerId)
            browser.invitePeer(peerID, to: session, withContext: nil, timeout: multipeerInviteTimeout)
        }
    }

    func handleMultipeerLostPeer(_ peerID: MCPeerID) {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in
                self?.handleMultipeerLostPeer(peerID)
            }
            return
        }

        guard let peerId = multipeerPeerIds[peerID] else {
            return
        }

        emit("multipeerPeerLost", body: ["peerId": peerId])

        if multipeerStatesById[peerId] != .connected {
            multipeerPeerIds.removeValue(forKey: peerID)
            multipeerPeersById.removeValue(forKey: peerId)
            multipeerDiscoveryInfoById.removeValue(forKey: peerId)
            multipeerStatesById.removeValue(forKey: peerId)
        }
    }

    func handleMultipeerInvitation(fromPeer peerID: MCPeerID, invitationHandler: @escaping (Bool, MCSession?) -> Void) {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in
                self?.handleMultipeerInvitation(fromPeer: peerID, invitationHandler: invitationHandler)
            }
            return
        }

        _ = multipeerRuntimeId(for: peerID)
        NSLog("[MunimBluetooth] Multipeer invitation from peer=%@ autoAccept=%@", peerID.displayName, multipeerAutoAcceptInvitations ? "true" : "false")
        emit("multipeerPeerFound", body: multipeerPeerEventBody(for: peerID))
        invitationHandler(multipeerAutoAcceptInvitations, multipeerAutoAcceptInvitations ? multipeerSession : nil)
    }

    func handleMultipeerStateChanged(peerID: MCPeerID, state: MCSessionState) {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in
                self?.handleMultipeerStateChanged(peerID: peerID, state: state)
            }
            return
        }

        let peerId = multipeerRuntimeId(for: peerID)
        multipeerStatesById[peerId] = multipeerPeerState(state)
        NSLog("[MunimBluetooth] Multipeer peer=%@ state=%@", peerID.displayName, multipeerPeerState(state).stringValue)
        emit("multipeerPeerStateChanged", body: multipeerPeerEventBody(for: peerID))
    }

    func handleMultipeerReceivedData(_ data: Data, fromPeer peerID: MCPeerID) {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in
                self?.handleMultipeerReceivedData(data, fromPeer: peerID)
            }
            return
        }

        emit("multipeerMessageReceived", body: [
            "peerId": multipeerRuntimeId(for: peerID),
            "displayName": peerID.displayName,
            "value": dataToHex(data)
        ])
        NSLog("[MunimBluetooth] Multipeer received %ld bytes from peer=%@", data.count, peerID.displayName)
    }

    func handleMultipeerStartFailed(error: Error) {
        guard Thread.isMainThread else {
            DispatchQueue.main.async { [weak self] in
                self?.handleMultipeerStartFailed(error: error)
            }
            return
        }

        NSLog("[MunimBluetooth] Multipeer failed: %@", error.localizedDescription)
        emit("multipeerStartFailed", body: [
            "platform": "ios",
            "error": error.localizedDescription
        ])
        stopMultipeerSessionInternal(emitEvent: false)
    }

    private func unsupportedPromise<T>(_ message: String) -> Promise<T> {
        let promise = Promise<T>()
        promise.reject(withError: NSError(domain: "MunimBluetooth", code: 501, userInfo: [NSLocalizedDescriptionKey: message]))
        return promise
    }

    private func rejectPendingOperations(for deviceId: String, error: Error) {
        connectionTimeouts.removeValue(forKey: deviceId)?.cancel()
        cancelOperationTimeouts(for: deviceId)
        pendingConnectionPromises.removeValue(forKey: deviceId)?.reject(withError: error)
        pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.reject(withError: error)
        pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
        pendingRSSIPromises.removeValue(forKey: deviceId)?.reject(withError: error)

        let prefix = "\(deviceId.lowercased())|"
        for key in Array(pendingReadPromises.keys) where key.hasPrefix(prefix) {
            pendingReadPromises.removeValue(forKey: key)?.reject(withError: error)
        }
        for key in Array(pendingWritePromises.keys) where key.hasPrefix(prefix) {
            pendingWritePromises.removeValue(forKey: key)?.reject(withError: error)
        }
        for key in Array(pendingDescriptorReadPromises.keys) where key.hasPrefix(prefix) {
            pendingDescriptorReadPromises.removeValue(forKey: key)?.reject(withError: error)
        }
        for key in Array(pendingDescriptorWritePromises.keys) where key.hasPrefix(prefix) {
            pendingDescriptorWritePromises.removeValue(forKey: key)?.reject(withError: error)
            pendingDescriptorWriteValues.removeValue(forKey: key)
        }

        pendingL2CAPOpenPromises.removeValue(forKey: deviceId)?.reject(withError: error)
        pendingL2CAPOpenPSMs.removeValue(forKey: deviceId)
        closeL2CAPChannels(for: deviceId)
    }

    // MARK: - CoreBluetooth Delegate Forwarding

    func handlePeripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        // Handle state updates
    }

    func handlePeripheralManagerWillRestoreState(_ peripheral: CBPeripheralManager, state: [String: Any]) {
        isBackgroundSessionActive = true

        if let restoredServices = state[CBPeripheralManagerRestoredStateServicesKey] as? [CBMutableService] {
            peripheralServices = restoredServices
            for service in restoredServices {
                for characteristic in service.characteristics ?? [] {
                    guard let mutableCharacteristic = characteristic as? CBMutableCharacteristic,
                          let value = mutableCharacteristic.value else {
                        continue
                    }
                    peripheralCharacteristicValues[peripheralCharacteristicKey(mutableCharacteristic)] = value
                }
            }
        }

        let restoredServiceUUIDs = peripheralServices.map { $0.uuid.uuidString }
        emit("backgroundSessionRestored", body: [
            "platform": "ios",
            "role": "peripheral",
            "isAdvertising": peripheral.isAdvertising,
            "serviceUUIDs": restoredServiceUUIDs
        ])
    }

    func handlePeripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error = error {
            emit("advertisingStartFailed", body: ["error": error.localizedDescription])
            NSLog("Bluetooth: advertising failed - %@", error.localizedDescription)
        } else {
            emit("advertisingStarted", body: [:])
            NSLog("Bluetooth: advertising started")
        }
    }

    func handlePeripheralManagerDidAddService(_ peripheral: CBPeripheralManager, service: CBService, error: Error?) {
        if let error = error {
            NSLog("Bluetooth: didAdd service failed - %@", error.localizedDescription)
        } else {
            NSLog("Bluetooth: didAdd service succeeded")
        }
    }

    func handlePeripheralManagerDidReceiveRead(_ peripheral: CBPeripheralManager, request: CBATTRequest) {
        guard request.characteristic.properties.contains(.read) else {
            peripheral.respond(to: request, withResult: .readNotPermitted)
            return
        }

        let key = peripheralCharacteristicKey(request.characteristic)
        let value = peripheralCharacteristicValues[key] ?? Data()
        guard request.offset <= value.count else {
            peripheral.respond(to: request, withResult: .invalidOffset)
            return
        }

        request.value = value.subdata(in: request.offset..<value.count)
        peripheral.respond(to: request, withResult: .success)
        NSLog(
            "Bluetooth: peripheral read central=%@ characteristic=%@ value=%@",
            request.central.identifier.uuidString,
            request.characteristic.uuid.uuidString,
            dataToHexString(value)
        )
        emit("peripheralReadRequest", body: [
            "centralId": request.central.identifier.uuidString,
            "serviceUUID": request.characteristic.service?.uuid.uuidString ?? "",
            "characteristicUUID": request.characteristic.uuid.uuidString,
            "value": dataToHexString(value)
        ])
    }

    func handlePeripheralManagerDidReceiveWrite(_ peripheral: CBPeripheralManager, requests: [CBATTRequest]) {
        for request in requests {
            let canWrite = request.characteristic.properties.contains(.write) ||
                request.characteristic.properties.contains(.writeWithoutResponse)
            guard canWrite else {
                peripheral.respond(to: request, withResult: .writeNotPermitted)
                return
            }

            let key = peripheralCharacteristicKey(request.characteristic)
            let incomingValue = request.value ?? Data()
            var value = peripheralCharacteristicValues[key] ?? Data()
            guard request.offset <= value.count else {
                peripheral.respond(to: request, withResult: .invalidOffset)
                return
            }

            if request.offset == 0 {
                value = incomingValue
            } else {
                let replaceEnd = min(request.offset + incomingValue.count, value.count)
                value.replaceSubrange(request.offset..<replaceEnd, with: incomingValue)
            }
            peripheralCharacteristicValues[key] = value

            if let mutableCharacteristic = request.characteristic as? CBMutableCharacteristic,
               mutableCharacteristic.properties.contains(.notify) || mutableCharacteristic.properties.contains(.indicate) {
                sendValueToSubscribedCentrals(characteristic: mutableCharacteristic, value: value)
            }

            emit("peripheralWriteRequest", body: [
                "centralId": request.central.identifier.uuidString,
                "serviceUUID": request.characteristic.service?.uuid.uuidString ?? "",
                "characteristicUUID": request.characteristic.uuid.uuidString,
                "value": dataToHexString(value)
            ])
            NSLog(
                "Bluetooth: peripheral write central=%@ characteristic=%@ value=%@",
                request.central.identifier.uuidString,
                request.characteristic.uuid.uuidString,
                dataToHexString(value)
            )
        }

        if let first = requests.first {
            peripheral.respond(to: first, withResult: .success)
        }
    }

    func handlePeripheralManagerDidSubscribe(_ peripheral: CBPeripheralManager, central: CBCentral, characteristic: CBCharacteristic) {
        let key = peripheralCharacteristicKey(characteristic)
        var centrals = subscribedCentrals[key] ?? []
        if !centrals.contains(where: { $0.identifier == central.identifier }) {
            centrals.append(central)
        }
        subscribedCentrals[key] = centrals
        emit("peripheralSubscribed", body: [
            "centralId": central.identifier.uuidString,
            "serviceUUID": characteristic.service?.uuid.uuidString ?? "",
            "characteristicUUID": characteristic.uuid.uuidString
        ])
        NSLog(
            "Bluetooth: peripheral subscribed central=%@ characteristic=%@",
            central.identifier.uuidString,
            characteristic.uuid.uuidString
        )
    }

    func handlePeripheralManagerDidUnsubscribe(_ peripheral: CBPeripheralManager, central: CBCentral, characteristic: CBCharacteristic) {
        let key = peripheralCharacteristicKey(characteristic)
        subscribedCentrals[key] = subscribedCentrals[key]?.filter { $0.identifier != central.identifier }
        emit("peripheralUnsubscribed", body: [
            "centralId": central.identifier.uuidString,
            "serviceUUID": characteristic.service?.uuid.uuidString ?? "",
            "characteristicUUID": characteristic.uuid.uuidString
        ])
        NSLog(
            "Bluetooth: peripheral unsubscribed central=%@ characteristic=%@",
            central.identifier.uuidString,
            characteristic.uuid.uuidString
        )
    }

    func handlePeripheralManagerIsReadyToUpdateSubscribers(_ peripheral: CBPeripheralManager) {
        let updates = pendingSubscriberUpdates
        pendingSubscriberUpdates.removeAll()

        for (characteristic, value, centrals) in updates {
            let sent = peripheral.updateValue(value, for: characteristic, onSubscribedCentrals: centrals)
            if !sent {
                pendingSubscriberUpdates.append((characteristic, value, centrals))
                break
            }
        }
    }

    func handlePeripheralManagerDidPublishL2CAPChannel(_ peripheral: CBPeripheralManager, psm: CBL2CAPPSM, error: Error?) {
        guard !pendingL2CAPPublishPromises.isEmpty else {
            return
        }

        let promise = pendingL2CAPPublishPromises.removeFirst()
        if let error = error {
            promise.reject(withError: error)
            emit("l2capChannelPublishFailed", body: [
                "psm": Double(psm),
                "error": error.localizedDescription
            ])
            return
        }

        publishedL2CAPPSMs.insert(psm)
        let channel = L2CAPChannel(id: "server:\(psm)", psm: Double(psm), deviceId: nil)
        promise.resolve(withResult: channel)
        emit("l2capChannelPublished", body: [
            "channelId": channel.id,
            "psm": Double(psm)
        ])
    }

    func handlePeripheralManagerDidUnpublishL2CAPChannel(_ peripheral: CBPeripheralManager, psm: CBL2CAPPSM, error: Error?) {
        publishedL2CAPPSMs.remove(psm)
        emit("l2capChannelUnpublished", body: [
            "psm": Double(psm),
            "error": error?.localizedDescription ?? NSNull()
        ])
    }

    func handlePeripheralManagerDidOpenL2CAPChannel(_ peripheral: CBPeripheralManager, channel: CBL2CAPChannel?, error: Error?) {
        if let error = error {
            emit("l2capChannelOpenFailed", body: ["error": error.localizedDescription])
            return
        }
        guard let channel = channel else {
            emit("l2capChannelOpenFailed", body: ["error": "No L2CAP channel was provided"])
            return
        }

        _ = registerL2CAPChannel(channel, deviceId: channel.peer.identifier.uuidString)
    }

    func handleCentralManagerDidUpdateState(_ central: CBCentralManager) {
        NSLog("Bluetooth: central state updated - %ld", central.state.rawValue)
    }

    func handleCentralManagerWillRestoreState(_ central: CBCentralManager, state: [String: Any]) {
        var restoredPeripheralIds: [String] = []

        if let restoredPeripherals = state[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] {
            for peripheral in restoredPeripherals {
                let deviceId = peripheral.identifier.uuidString
                peripheral.delegate = peripheralDelegateProxy
                discoveredPeripherals[deviceId] = peripheral
                restoredPeripheralIds.append(deviceId)

                if peripheral.state == .connected {
                    connectedPeripherals[deviceId] = peripheral
                }
            }
        }

        if let scanServices = state[CBCentralManagerRestoredStateScanServicesKey] as? [CBUUID] {
            scanOptions = ScanOptions(
                serviceUUIDs: scanServices.map { $0.uuidString },
                allowDuplicates: nil,
                scanMode: nil
            )
            isScanning = true
            isBackgroundSessionActive = true
        }

        isBackgroundSessionActive = true
        emit("backgroundSessionRestored", body: [
            "platform": "ios",
            "role": "central",
            "isScanning": isScanning,
            "serviceUUIDs": scanOptions?.serviceUUIDs ?? [],
            "deviceIds": restoredPeripheralIds
        ])
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
        connectionTimeouts.removeValue(forKey: deviceId)?.cancel()
        connectedPeripherals[deviceId] = peripheral
        peripheral.delegate = peripheralDelegateProxy
        pendingConnectionPromises.removeValue(forKey: deviceId)?.resolve(withResult: ())
        emit("deviceConnected", body: ["deviceId": deviceId])

        NSLog("Bluetooth: connected peripheral=%@", deviceId)
    }

    func handleCentralManagerDidDisconnectPeripheral(_ central: CBCentralManager, peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        connectedPeripherals.removeValue(forKey: deviceId)
        peripheralCharacteristics.removeValue(forKey: deviceId)
        connectionTimeouts.removeValue(forKey: deviceId)?.cancel()
        rejectPendingOperations(
            for: deviceId,
            error: error ?? NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Disconnected from \(deviceId)"])
        )
        emit("deviceDisconnected", body: ["deviceId": deviceId])

        NSLog("Bluetooth: disconnected peripheral=%@", deviceId)
    }

    func handleCentralManagerDidFailToConnect(_ central: CBCentralManager, peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        connectionTimeouts.removeValue(forKey: deviceId)?.cancel()
        rejectPendingOperations(
            for: deviceId,
            error: error ?? NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to connect to \(deviceId)"])
        )
        NSLog("Bluetooth: connection failed peripheral=%@", deviceId)
    }

    func handlePeripheralDidDiscoverServices(_ peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString

        if let error = error {
            cancelOperationTimeout(key: "services|\(deviceId.lowercased())")
            pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.reject(withError: error)
            pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
            return
        }

        guard let services = peripheral.services else {
            cancelOperationTimeout(key: "services|\(deviceId.lowercased())")
            pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.resolve(withResult: [])
            pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
            return
        }

        if services.isEmpty {
            cancelOperationTimeout(key: "services|\(deviceId.lowercased())")
            pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.resolve(withResult: [])
            pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
            return
        }

        pendingCharacteristicDiscoveryCounts[deviceId] = services.count * 2
        for service in services {
            peripheral.discoverIncludedServices(nil, for: service)
            peripheral.discoverCharacteristics(nil, for: service)
        }

        NSLog("Bluetooth: discovered %d service(s) for %@", services.count, deviceId)
    }

	    func handlePeripheralDidDiscoverCharacteristics(_ peripheral: CBPeripheral, service: CBService, error: Error?) {
        let deviceId = peripheral.identifier.uuidString

        if let error = error {
            cancelOperationTimeout(key: "services|\(deviceId.lowercased())")
            pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.reject(withError: error)
            pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
            return
        }

        if peripheralCharacteristics[deviceId] == nil {
            peripheralCharacteristics[deviceId] = []
        }
        peripheralCharacteristics[deviceId]?.append(contentsOf: service.characteristics ?? [])
        completeServiceDiscoveryStep(peripheral: peripheral, deviceId: deviceId)

	        NSLog(
                "Bluetooth: discovered %d characteristic(s) for service %@",
                service.characteristics?.count ?? 0,
                service.uuid.uuidString
            )
	    }

    func handlePeripheralDidDiscoverIncludedServices(_ peripheral: CBPeripheral, service: CBService, error: Error?) {
        let deviceId = peripheral.identifier.uuidString

        if let error = error {
            cancelOperationTimeout(key: "services|\(deviceId.lowercased())")
            pendingServiceDiscoveryPromises.removeValue(forKey: deviceId)?.reject(withError: error)
            pendingCharacteristicDiscoveryCounts.removeValue(forKey: deviceId)
            return
        }

        completeServiceDiscoveryStep(peripheral: peripheral, deviceId: deviceId)
    }

    func handlePeripheralDidDiscoverDescriptors(_ peripheral: CBPeripheral, characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        let serviceUUID = characteristic.service?.uuid.uuidString ?? ""
        let prefix = characteristicKey(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristic.uuid.uuidString) + "|"

        if let error = error {
            for key in Array(pendingDescriptorReadPromises.keys) where key.hasPrefix(prefix) {
                cancelOperationTimeout(key: "descriptorRead|\(key)")
                pendingDescriptorReadPromises.removeValue(forKey: key)?.reject(withError: error)
            }
            for key in Array(pendingDescriptorWritePromises.keys) where key.hasPrefix(prefix) {
                cancelOperationTimeout(key: "descriptorWrite|\(key)")
                pendingDescriptorWritePromises.removeValue(forKey: key)?.reject(withError: error)
                pendingDescriptorWriteValues.removeValue(forKey: key)
            }
            return
        }

        for key in Array(pendingDescriptorReadPromises.keys) where key.hasPrefix(prefix) {
            let descriptorUUID = String(key.split(separator: "|").last ?? "")
            if let descriptor = findDescriptor(characteristic: characteristic, descriptorUUID: descriptorUUID) {
                peripheral.readValue(for: descriptor)
            } else {
                cancelOperationTimeout(key: "descriptorRead|\(key)")
                pendingDescriptorReadPromises.removeValue(forKey: key)?.reject(withError: NSError(
                    domain: "MunimBluetooth",
                    code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "Descriptor not found: \(descriptorUUID)"]
                ))
            }
        }
        for key in Array(pendingDescriptorWritePromises.keys) where key.hasPrefix(prefix) {
            let descriptorUUID = String(key.split(separator: "|").last ?? "")
            if let descriptor = findDescriptor(characteristic: characteristic, descriptorUUID: descriptorUUID),
               let value = pendingDescriptorWriteValues[key] {
                peripheral.writeValue(value, for: descriptor)
            } else {
                cancelOperationTimeout(key: "descriptorWrite|\(key)")
                pendingDescriptorWriteValues.removeValue(forKey: key)
                pendingDescriptorWritePromises.removeValue(forKey: key)?.reject(withError: NSError(
                    domain: "MunimBluetooth",
                    code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "Descriptor not found: \(descriptorUUID)"]
                ))
            }
        }
    }

	    func handlePeripheralDidUpdateValue(_ peripheral: CBPeripheral, characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        let serviceUUID = characteristic.service?.uuid.uuidString ?? ""

        if let error = error {
            cancelOperationTimeout(key: "read|\(characteristicKey(deviceId: deviceId, serviceUUID: serviceUUID, characteristicUUID: characteristic.uuid.uuidString))")
            pendingReadPromises.removeValue(
                forKey: characteristicKey(
                    deviceId: deviceId,
                    serviceUUID: serviceUUID,
                    characteristicUUID: characteristic.uuid.uuidString
                )
            )?.reject(withError: error)
            return
        }

        let data = characteristic.value ?? Data()

        let hexString = data.map { String(format: "%02x", $0) }.joined()
        let key = characteristicKey(
            deviceId: deviceId,
            serviceUUID: serviceUUID,
            characteristicUUID: characteristic.uuid.uuidString
        )
        cancelOperationTimeout(key: "read|\(key)")
        let value = CharacteristicValue(
            value: hexString,
            serviceUUID: serviceUUID,
            characteristicUUID: characteristic.uuid.uuidString
        )
        pendingReadPromises.removeValue(forKey: key)?.resolve(withResult: value)

        emit("characteristicValueChanged", body: [
            "deviceId": deviceId,
            "serviceUUID": serviceUUID,
            "characteristicUUID": characteristic.uuid.uuidString,
            "value": hexString
        ])

	        NSLog(
                "Bluetooth: characteristic value peripheral=%@ characteristic=%@ value=%@",
                deviceId,
                characteristic.uuid.uuidString,
                hexString
            )
	    }

    func handlePeripheralDidUpdateDescriptorValue(_ peripheral: CBPeripheral, descriptor: CBDescriptor, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        guard let characteristic = descriptor.characteristic else {
            return
        }
        let serviceUUID = characteristic.service?.uuid.uuidString ?? ""
        let key = descriptorKey(
            deviceId: deviceId,
            serviceUUID: serviceUUID,
            characteristicUUID: characteristic.uuid.uuidString,
            descriptorUUID: descriptor.uuid.uuidString
        )

        if let error = error {
            cancelOperationTimeout(key: "descriptorRead|\(key)")
            pendingDescriptorReadPromises.removeValue(forKey: key)?.reject(withError: error)
            return
        }

        cancelOperationTimeout(key: "descriptorRead|\(key)")
        pendingDescriptorReadPromises.removeValue(forKey: key)?.resolve(withResult: DescriptorValue(
            value: descriptor.value.flatMap { descriptorValueToHex($0) } ?? "",
            serviceUUID: serviceUUID,
            characteristicUUID: characteristic.uuid.uuidString,
            descriptorUUID: descriptor.uuid.uuidString
        ))
    }

	    func handlePeripheralDidWriteValue(_ peripheral: CBPeripheral, characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        let serviceUUID = characteristic.service?.uuid.uuidString ?? ""
        let key = characteristicKey(
            deviceId: deviceId,
            serviceUUID: serviceUUID,
            characteristicUUID: characteristic.uuid.uuidString
        )

        if let error = error {
            cancelOperationTimeout(key: "write|\(key)")
            pendingWritePromises.removeValue(forKey: key)?.reject(withError: error)
            NSLog("Bluetooth: writeError")
        } else {
            cancelOperationTimeout(key: "write|\(key)")
            pendingWritePromises.removeValue(forKey: key)?.resolve(withResult: ())
            NSLog("Bluetooth: write succeeded characteristic=%@", characteristic.uuid.uuidString)
	        }
	    }

    func handlePeripheralDidWriteDescriptorValue(_ peripheral: CBPeripheral, descriptor: CBDescriptor, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        guard let characteristic = descriptor.characteristic else {
            return
        }
        let key = descriptorKey(
            deviceId: deviceId,
            serviceUUID: characteristic.service?.uuid.uuidString ?? "",
            characteristicUUID: characteristic.uuid.uuidString,
            descriptorUUID: descriptor.uuid.uuidString
        )
        pendingDescriptorWriteValues.removeValue(forKey: key)
        if let error = error {
            cancelOperationTimeout(key: "descriptorWrite|\(key)")
            pendingDescriptorWritePromises.removeValue(forKey: key)?.reject(withError: error)
        } else {
            cancelOperationTimeout(key: "descriptorWrite|\(key)")
            pendingDescriptorWritePromises.removeValue(forKey: key)?.resolve(withResult: ())
        }
    }

    func handlePeripheralDidUpdateNotificationState(_ peripheral: CBPeripheral, characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            NSLog("Bluetooth: notification state error - %@", error.localizedDescription)
        } else {
            NSLog(
                "Bluetooth: notification state updated characteristic=%@ notifying=%@",
                characteristic.uuid.uuidString,
                characteristic.isNotifying ? "YES" : "NO"
            )
        }
    }

    func handlePeripheralDidOpenL2CAPChannel(_ peripheral: CBPeripheral, channel: CBL2CAPChannel?, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        cancelOperationTimeout(key: "l2capOpen|\(deviceId.lowercased())")
        let promise = pendingL2CAPOpenPromises.removeValue(forKey: deviceId)
        pendingL2CAPOpenPSMs.removeValue(forKey: deviceId)

        if let error = error {
            promise?.reject(withError: error)
            emit("l2capChannelOpenFailed", body: [
                "deviceId": deviceId,
                "error": error.localizedDescription
            ])
            return
        }

        guard let channel = channel else {
            let error = NSError(domain: "MunimBluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "No L2CAP channel was provided"])
            promise?.reject(withError: error)
            emit("l2capChannelOpenFailed", body: [
                "deviceId": deviceId,
                "error": error.localizedDescription
            ])
            return
        }

        let l2capChannel = registerL2CAPChannel(channel, deviceId: deviceId)
        promise?.resolve(withResult: l2capChannel)
    }

    func handlePeripheralDidReadRSSI(_ peripheral: CBPeripheral, rssi RSSI: NSNumber, error: Error?) {
        let deviceId = peripheral.identifier.uuidString

        if let error = error {
            cancelOperationTimeout(key: "rssi|\(deviceId.lowercased())")
            pendingRSSIPromises.removeValue(forKey: deviceId)?.reject(withError: error)
            NSLog("Bluetooth: RSSI error peripheral=%@ error=%@", deviceId, error.localizedDescription)
            return
        }

        cancelOperationTimeout(key: "rssi|\(deviceId.lowercased())")
        pendingRSSIPromises.removeValue(forKey: deviceId)?.resolve(withResult: RSSI.doubleValue)
        emit("rssiUpdated", body: ["deviceId": deviceId, "rssi": RSSI.doubleValue])
        NSLog("Bluetooth: RSSI peripheral=%@ value=%@", deviceId, RSSI)
    }
}

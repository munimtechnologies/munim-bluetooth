import { type HybridObject } from 'react-native-nitro-modules'

// Service Data Entry
export interface ServiceDataEntry {
  uuid: string
  data: string
}

// BLE advertising data types. Android can advertise all supported fields when
// payload size and hardware allow it; iOS advertising is limited by
// CoreBluetooth to local name and service UUIDs.
export interface AdvertisingDataTypes {
  // 0x01 - Flags
  flags?: number

  // 0x02-0x07 - Service UUIDs
  incompleteServiceUUIDs16?: string[]
  completeServiceUUIDs16?: string[]
  incompleteServiceUUIDs32?: string[]
  completeServiceUUIDs32?: string[]
  incompleteServiceUUIDs128?: string[]
  completeServiceUUIDs128?: string[]

  // 0x08-0x09 - Local Name
  shortenedLocalName?: string
  completeLocalName?: string

  // 0x0A - Tx Power Level
  txPowerLevel?: number

  // 0x14-0x15 - Service Solicitation
  serviceSolicitationUUIDs16?: string[]
  serviceSolicitationUUIDs128?: string[]

  // 0x16, 0x20, 0x21 - Service Data
  serviceData16?: ServiceDataEntry[]
  serviceData32?: ServiceDataEntry[]
  serviceData128?: ServiceDataEntry[]

  // 0x19 - Appearance (partial support)
  appearance?: number

  // 0x1F - Service Solicitation (32-bit)
  serviceSolicitationUUIDs32?: string[]

  // 0xFF - Manufacturer Specific Data
  manufacturerData?: string
}

// BLE Device information
export interface BLEDevice {
  id: string
  name?: string
  localName?: string
  rssi?: number
  advertisingData?: AdvertisingDataTypes
  serviceUUIDs?: string[]
  serviceData?: ServiceDataEntry[]
  manufacturerData?: string
  txPowerLevel?: number
  isConnectable?: boolean
}

// Scan mode type
export type ScanMode = 'lowPower' | 'balanced' | 'lowLatency'

// Scan options
export interface ScanOptions {
  serviceUUIDs?: string[]
  allowDuplicates?: boolean
  scanMode?: ScanMode
}

export interface GATTDescriptor {
  uuid: string
  value?: string
  permissions?: string[]
}

// GATT Characteristic
export interface GATTCharacteristic {
  uuid: string
  properties: string[]
  value?: string
  descriptors?: GATTDescriptor[]
}

// GATT Service
export interface GATTService {
  uuid: string
  characteristics: GATTCharacteristic[]
  includedServices?: string[]
}

// Characteristic value
export interface CharacteristicValue {
  value: string
  serviceUUID: string
  characteristicUUID: string
}

export interface DescriptorValue {
  value: string
  serviceUUID: string
  characteristicUUID: string
  descriptorUUID: string
}

// Write type for characteristic writes
export type WriteType = 'write' | 'writeWithoutResponse'

export type BluetoothPhy = 'le1m' | 'le2m' | 'leCoded'

export type BluetoothPhyOption = 'none' | 's2' | 's8'

export interface PhyStatus {
  txPhy: BluetoothPhy
  rxPhy: BluetoothPhy
}

export type BondState = 'none' | 'bonding' | 'bonded' | 'unsupported'

export interface BluetoothCapabilities {
  platform: string
  supportsBleCentral: boolean
  supportsBlePeripheral: boolean
  supportsDescriptors: boolean
  supportsIncludedServices: boolean
  supportsMtu: boolean
  supportsPhy: boolean
  supportsBonding: boolean
  supportsExtendedAdvertising: boolean
  supportsL2cap: boolean
  supportsClassicBluetooth: boolean
  supportsBackgroundBle: boolean
  supportsMultipeerConnectivity: boolean
}

// Advertising options for startAdvertising
export interface AdvertisingOptions {
  serviceUUIDs: string[]
  localName?: string
  manufacturerData?: string
  advertisingData?: AdvertisingDataTypes
}

export interface ExtendedAdvertisingOptions {
  serviceUUIDs?: string[]
  localName?: string
  manufacturerData?: string
  advertisingData?: AdvertisingDataTypes
  connectable?: boolean
  scannable?: boolean
  legacyMode?: boolean
  anonymous?: boolean
  includeTxPower?: boolean
  interval?: number
  txPowerLevel?: number
  primaryPhy?: BluetoothPhy
  secondaryPhy?: BluetoothPhy
}

export interface BackgroundSessionOptions {
  serviceUUIDs: string[]
  localName?: string
  allowDuplicates?: boolean
  scanMode?: ScanMode
  androidNotificationChannelId?: string
  androidNotificationChannelName?: string
  androidNotificationTitle?: string
  androidNotificationText?: string
}

export type MultipeerEncryptionPreference = 'none' | 'optional' | 'required'

export type MultipeerPeerState = 'notConnected' | 'connecting' | 'connected'

export interface MultipeerDiscoveryInfoEntry {
  key: string
  value: string
}

export interface MultipeerSessionOptions {
  /**
   * Bonjour service type, 1-15 chars: lowercase letters, numbers, hyphen.
   * Example: "anonmesh" or "munim-chat".
   */
  serviceType: string
  displayName?: string
  discoveryInfo?: MultipeerDiscoveryInfoEntry[]
  autoInvite?: boolean
  autoAcceptInvitations?: boolean
  inviteTimeout?: number
  encryptionPreference?: MultipeerEncryptionPreference
}

export interface MultipeerPeer {
  id: string
  displayName: string
  state: MultipeerPeerState
  discoveryInfo?: MultipeerDiscoveryInfoEntry[]
}

export interface L2CAPChannel {
  id: string
  psm: number
  deviceId?: string
}

export interface MunimBluetooth
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {
  // ========== Peripheral Features ==========

  /**
   * Start advertising as a Bluetooth peripheral with platform-aware advertising data.
   *
   * @param options - An object with serviceUUIDs (string[]) and advertising data types.
   *                  iOS only advertises local name and service UUIDs.
   *                  This must be a plain JS object (no Maps/Sets/functions).
   */
  startAdvertising(options: AdvertisingOptions): void

  /**
   * Update advertising data while advertising is active.
   *
   * @param advertisingData - The new advertising data to use.
   */
  updateAdvertisingData(advertisingData: AdvertisingDataTypes): void

  /**
   * Get current advertising data.
   *
   * @returns Promise resolving to current advertising data.
   */
  getAdvertisingData(): Promise<AdvertisingDataTypes>

  /**
   * Stop BLE advertising.
   */
  stopAdvertising(): void

  /**
   * Set GATT services and characteristics for the Bluetooth peripheral.
   *
   * @param services - An array of service objects, each with a uuid and an array of characteristics.
   *                  This must be serializable to a plain JS array (no Maps/Sets/functions).
   */
  setServices(services: GATTService[]): void

  /**
   * Update a local peripheral characteristic value and optionally notify/indicate
   * subscribed centrals.
   *
   * @param serviceUUID - The UUID of the local GATT service.
   * @param characteristicUUID - The UUID of the local characteristic.
   * @param value - Hex-encoded value.
   * @param notify - When true, push the value to subscribed centrals.
   */
  updateCharacteristicValue(
    serviceUUID: string,
    characteristicUUID: string,
    value: string,
    notify?: boolean
  ): Promise<void>

  // ========== Central/Manager Features ==========

  /**
   * Check if Bluetooth is enabled on the device.
   *
   * @returns Promise resolving to true if Bluetooth is enabled, false otherwise.
   */
  isBluetoothEnabled(): Promise<boolean>

  /**
   * Request selected Bluetooth permissions (Android) or check authorization status (iOS).
   *
   * @param permissions - Android capabilities to request.
   * @returns Promise resolving to true if permissions are granted, false otherwise.
   */
  requestBluetoothPermission(permissions?: string[]): Promise<boolean>

  /**
   * Return the Bluetooth features this platform can support through public APIs.
   */
  getCapabilities(): Promise<BluetoothCapabilities>

  /**
   * Start scanning for BLE devices.
   *
   * @param options - Optional scan configuration including service UUIDs to filter by.
   */
  startScan(options?: ScanOptions): void

  /**
   * Stop scanning for BLE devices.
   */
  stopScan(): void

  /**
   * Connect to a BLE device.
   *
   * @param deviceId - The unique identifier of the device to connect to.
   * @returns Promise resolving when connection is established or rejected.
   */
  connect(deviceId: string): Promise<void>

  /**
   * Disconnect from a BLE device.
   *
   * @param deviceId - The unique identifier of the device to disconnect from.
   */
  disconnect(deviceId: string): void

  /**
   * Discover GATT services for a connected device.
   *
   * @param deviceId - The unique identifier of the connected device.
   * @returns Promise resolving to array of discovered services.
   */
  discoverServices(deviceId: string): Promise<GATTService[]>

  /**
   * Read a characteristic value from a connected device.
   *
   * @param deviceId - The unique identifier of the connected device.
   * @param serviceUUID - The UUID of the service containing the characteristic.
   * @param characteristicUUID - The UUID of the characteristic to read.
   * @returns Promise resolving to the characteristic value.
   */
  readCharacteristic(
    deviceId: string,
    serviceUUID: string,
    characteristicUUID: string
  ): Promise<CharacteristicValue>

  /**
   * Read a descriptor value from a connected BLE device.
   */
  readDescriptor(
    deviceId: string,
    serviceUUID: string,
    characteristicUUID: string,
    descriptorUUID: string
  ): Promise<DescriptorValue>

  /**
   * Write a value to a characteristic on a connected device.
   *
   * @param deviceId - The unique identifier of the connected device.
   * @param serviceUUID - The UUID of the service containing the characteristic.
   * @param characteristicUUID - The UUID of the characteristic to write.
   * @param value - The value to write (hex string).
   * @param writeType - Optional write type: 'write' or 'writeWithoutResponse'. Defaults to 'write'.
   * @returns Promise resolving when write is complete.
   */
  writeCharacteristic(
    deviceId: string,
    serviceUUID: string,
    characteristicUUID: string,
    value: string,
    writeType?: WriteType
  ): Promise<void>

  /**
   * Write a descriptor value on a connected BLE device.
   */
  writeDescriptor(
    deviceId: string,
    serviceUUID: string,
    characteristicUUID: string,
    descriptorUUID: string,
    value: string
  ): Promise<void>

  /**
   * Subscribe to notifications/indications from a characteristic.
   *
   * @param deviceId - The unique identifier of the connected device.
   * @param serviceUUID - The UUID of the service containing the characteristic.
   * @param characteristicUUID - The UUID of the characteristic to subscribe to.
   */
  subscribeToCharacteristic(
    deviceId: string,
    serviceUUID: string,
    characteristicUUID: string
  ): void

  /**
   * Unsubscribe from notifications/indications from a characteristic.
   *
   * @param deviceId - The unique identifier of the connected device.
   * @param serviceUUID - The UUID of the service containing the characteristic.
   * @param characteristicUUID - The UUID of the characteristic to unsubscribe from.
   */
  unsubscribeFromCharacteristic(
    deviceId: string,
    serviceUUID: string,
    characteristicUUID: string
  ): void

  /**
   * Get list of currently connected devices.
   *
   * @returns Promise resolving to array of connected device IDs.
   */
  getConnectedDevices(): Promise<string[]>

  /**
   * Read RSSI (signal strength) for a connected device.
   *
  * @param deviceId - The unique identifier of the connected device.
  * @returns Promise resolving to RSSI value in dBm.
  */
  readRSSI(deviceId: string): Promise<number>

  /**
   * Request a BLE ATT MTU. Android supports this directly; iOS negotiates MTU internally.
   */
  requestMTU(deviceId: string, mtu: number): Promise<number>

  /**
   * Set preferred BLE PHY where supported.
   */
  setPreferredPhy(
    deviceId: string,
    txPhy: BluetoothPhy,
    rxPhy: BluetoothPhy,
    phyOption?: BluetoothPhyOption
  ): Promise<void>

  /**
   * Read current BLE PHY where supported.
   */
  readPhy(deviceId: string): Promise<PhyStatus>

  /**
   * Return the current bond/pairing state for a device.
   */
  getBondState(deviceId: string): Promise<BondState>

  /**
   * Start platform pairing/bonding for a device.
   */
  createBond(deviceId: string): Promise<BondState>

  /**
   * Remove an Android bond. Unsupported on iOS public APIs.
   */
  removeBond(deviceId: string): Promise<BondState>

  /**
   * Start BLE extended advertising where supported.
   */
  startExtendedAdvertising(options: ExtendedAdvertisingOptions): Promise<string>

  /**
   * Stop a BLE extended advertising set.
   */
  stopExtendedAdvertising(advertisingId: string): void

  /**
   * Publish a local L2CAP channel where supported.
   */
  publishL2CAPChannel(encryptionRequired?: boolean): Promise<L2CAPChannel>

  /**
   * Stop a local L2CAP channel.
   */
  unpublishL2CAPChannel(psm: number): void

  /**
   * Open an outbound L2CAP channel to a BLE device.
   */
  openL2CAPChannel(deviceId: string, psm: number): Promise<L2CAPChannel>

  /**
   * Close an L2CAP channel.
   */
  closeL2CAPChannel(channelId: string): void

  /**
   * Send hex data over an open L2CAP channel.
   */
  sendL2CAPData(channelId: string, value: string): Promise<void>

  /**
   * Start Classic Bluetooth discovery. Android only.
   */
  startClassicScan(): void

  /**
   * Stop Classic Bluetooth discovery. Android only.
   */
  stopClassicScan(): void

  /**
   * Connect to a Classic Bluetooth RFCOMM service. Android only.
   */
  connectClassic(deviceId: string, serviceUUID?: string): Promise<void>

  /**
   * Listen for incoming Classic Bluetooth RFCOMM connections. Android only.
   */
  startClassicServer(serviceUUID?: string, serviceName?: string): Promise<void>

  /**
   * Stop a Classic Bluetooth RFCOMM listener. Android only.
   */
  stopClassicServer(serviceUUID?: string): void

  /**
   * Disconnect a Classic Bluetooth device. Android only.
   */
  disconnectClassic(deviceId: string): void

  /**
   * Write hex data to a Classic Bluetooth RFCOMM connection. Android only.
   */
  writeClassic(deviceId: string, value: string): Promise<void>

  /**
   * Start a best-effort background BLE session.
   *
   * Android uses a foreground service so BLE can continue after the app leaves
   * the foreground, and restores scan/advertising/configured GATT services
   * after normal service process recreation. iOS relies on Bluetooth background
   * modes and CoreBluetooth state restoration, with terminated-state relaunch
   * still limited by Apple's current relaunch rules. User force-quit/force-stop
   * is controlled by the OS and cannot be bypassed.
   */
  startBackgroundSession(options: BackgroundSessionOptions): void

  /**
   * Stop the active background BLE session.
   */
  stopBackgroundSession(): void

  /**
   * Start Apple Multipeer Connectivity discovery/session transport.
   *
   * iOS/iPadOS/macOS/tvOS only. Android cannot join Apple's Multipeer
   * Connectivity sessions and rejects the related promises.
   */
  startMultipeerSession(options: MultipeerSessionOptions): void

  /**
   * Stop the active Apple Multipeer Connectivity session.
   */
  stopMultipeerSession(): void

  /**
   * Invite a discovered Multipeer peer by runtime peer id.
   */
  inviteMultipeerPeer(peerId: string): void

  /**
   * Return discovered/connected Multipeer peers for this runtime session.
   */
  getMultipeerPeers(): Promise<MultipeerPeer[]>

  /**
   * Send hex data to connected Multipeer peers. Omit peerIds to broadcast to
   * all connected peers.
   */
  sendMultipeerMessage(
    value: string,
    peerIds?: string[],
    reliable?: boolean
  ): Promise<void>

  // ========== Event Management ==========

  /**
   * Add an event listener.
   *
   * @param eventName - The name of the event to listen for.
   */
  addListener(eventName: string): void

  /**
   * Remove event listeners.
   *
   * @param count - Number of listeners to remove.
   */
  removeListeners(count: number): void
}

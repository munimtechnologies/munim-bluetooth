import { NitroModules } from 'react-native-nitro-modules'
import {
  DeviceEventEmitter,
  NativeEventEmitter,
  NativeModules,
  Platform,
} from 'react-native'
import type {
  MunimBluetooth as MunimBluetoothSpec,
  AdvertisingDataTypes,
  BLEDevice,
  BackgroundSessionOptions,
  MultipeerSessionOptions,
  MultipeerPeer,
  MultipeerDiscoveryInfoEntry,
  MultipeerEncryptionPreference,
  MultipeerPeerState,
  ScanOptions,
  GATTService,
  GATTDescriptor,
  CharacteristicValue,
  DescriptorValue,
  BluetoothCapabilities,
  BluetoothPhy,
  BluetoothPhyOption,
  BondState,
  PhyStatus,
  ExtendedAdvertisingOptions,
  L2CAPChannel,
} from './specs/munim-bluetooth.nitro'

export type BluetoothEventMap = {
  deviceFound: BLEDevice
  onDeviceFound: BLEDevice
  scanResult: BLEDevice
  scanFailed: { errorCode: number; message: string }
  advertisingStarted: Record<string, never>
  advertisingStartFailed: { error?: string; errorCode?: number; message?: string }
  classicDeviceFound: BLEDevice & { bondState?: string }
  classicScanFailed: { message: string }
  classicScanFinished: Record<string, never>
  classicConnected: { deviceId: string }
  classicDisconnected: { deviceId: string }
  classicConnectionReceived: { deviceId: string }
  classicServerStarted: { serviceUUID: string; serviceName: string }
  classicServerStopped: { serviceUUID: string }
  classicDataReceived: { deviceId: string; value: string }
  deviceConnected: { deviceId: string }
  deviceDisconnected: { deviceId: string }
  servicesDiscovered: { deviceId: string; services: GATTService[] }
  characteristicValueChanged: CharacteristicValue & { deviceId: string }
  l2capChannelPublished: { channelId: string; psm: number }
  l2capChannelPublishFailed: { psm?: number; error: string }
  l2capChannelUnpublished: { psm: number; error?: string }
  l2capChannelOpened: { channelId: string; psm: number; deviceId?: string }
  l2capChannelOpenFailed: { deviceId?: string; error: string }
  l2capChannelClosed: { channelId: string; psm?: number; deviceId?: string }
  l2capDataReceived: {
    channelId: string
    psm?: number
    deviceId?: string
    value: string
  }
  peripheralReadRequest: CharacteristicValue & { centralId: string }
  peripheralWriteRequest: CharacteristicValue & { centralId: string }
  peripheralSubscribed: {
    centralId: string
    serviceUUID: string
    characteristicUUID: string
  }
  peripheralUnsubscribed: {
    centralId: string
    serviceUUID: string
    characteristicUUID: string
  }
  rssiUpdated: { deviceId: string; rssi: number }
  backgroundSessionStarted: {
    platform: string
    serviceUUIDs?: string[]
    localName?: string | null
  }
  backgroundSessionStopped: { platform: string }
  backgroundSessionRestored: {
    platform: string
    role?: 'central' | 'peripheral'
    isScanning?: boolean
    isAdvertising?: boolean
    serviceUUIDs?: string[]
    deviceIds?: string[]
  }
  backgroundSessionStartFailed: { platform: string; error: string }
  multipeerStarted: {
    platform: string
    serviceType: string
    peerId: string
    displayName: string
  }
  multipeerStopped: { platform: string }
  multipeerStartFailed: { platform: string; error: string }
  multipeerPeerFound: MultipeerPeer
  multipeerPeerLost: { peerId: string }
  multipeerPeerStateChanged: MultipeerPeer
  multipeerMessageReceived: {
    peerId: string
    displayName: string
    value: string
  }
}

export type AndroidBluetoothPermission = 'scan' | 'connect' | 'advertise'

export type BluetoothEventName = keyof BluetoothEventMap

const MunimBluetooth =
  NitroModules.createHybridObject<MunimBluetoothSpec>('MunimBluetooth')

// Android emits through DeviceEventEmitter from the Nitro module itself.
// iOS exposes a dedicated RCTEventEmitter module.
const nativeEventModule =
  Platform.OS === 'ios' ? NativeModules.MunimBluetoothEventEmitter : null

let eventEmitter: Pick<NativeEventEmitter, 'addListener'> | null = null

if (Platform.OS === 'android') {
  eventEmitter = DeviceEventEmitter
} else if (nativeEventModule) {
  try {
    eventEmitter = new NativeEventEmitter(nativeEventModule)
  } catch (error) {
    console.error(
      '[munim-bluetooth] Failed to initialize event emitter:',
      error
    )
  }
}

// ========== Peripheral Features ==========

/**
 * Start advertising as a Bluetooth peripheral with platform-aware advertising data.
 *
 * @param options - An object with serviceUUIDs (string[]) and advertising data types.
 *                  iOS only advertises local name and service UUIDs.
 */
export function startAdvertising(options: {
  serviceUUIDs: string[]
  localName?: string
  manufacturerData?: string
  advertisingData?: AdvertisingDataTypes
}): void {
  return MunimBluetooth.startAdvertising(options)
}

/**
 * Update advertising data while advertising is active.
 *
 * @param advertisingData - The new advertising data to use.
 */
export function updateAdvertisingData(
  advertisingData: AdvertisingDataTypes
): void {
  return MunimBluetooth.updateAdvertisingData(advertisingData)
}

/**
 * Get current advertising data.
 *
 * @returns Promise resolving to current advertising data.
 */
export function getAdvertisingData(): Promise<AdvertisingDataTypes> {
  return MunimBluetooth.getAdvertisingData()
}

/**
 * Stop BLE advertising.
 */
export function stopAdvertising(): void {
  return MunimBluetooth.stopAdvertising()
}

/**
 * Set GATT services and characteristics for the Bluetooth peripheral.
 *
 * @param services - An array of service objects, each with a uuid and an array of characteristics.
 */
export function setServices(services: GATTService[]): void {
  return MunimBluetooth.setServices(services)
}

/**
 * Update a local peripheral characteristic value and optionally notify/indicate
 * subscribed centrals.
 */
export function updateCharacteristicValue(
  serviceUUID: string,
  characteristicUUID: string,
  value: string,
  notify?: boolean
): Promise<void> {
  return MunimBluetooth.updateCharacteristicValue(
    serviceUUID,
    characteristicUUID,
    value,
    notify
  )
}

// ========== Central/Manager Features ==========

/**
 * Check if Bluetooth is enabled on the device.
 *
 * @returns Promise resolving to true if Bluetooth is enabled, false otherwise.
 */
export function isBluetoothEnabled(): Promise<boolean> {
  return MunimBluetooth.isBluetoothEnabled()
}

/**
 * Request selected Bluetooth permissions (Android) or check authorization status (iOS).
 *
 * @param permissions - Android capabilities to request. Defaults to central-mode permissions.
 * @returns Promise resolving to true if permissions are granted, false otherwise.
 */
export function requestBluetoothPermission(
  permissions: AndroidBluetoothPermission[] = ['scan', 'connect']
): Promise<boolean> {
  return MunimBluetooth.requestBluetoothPermission(permissions)
}

/**
 * Return the Bluetooth feature set supported by the current platform/device.
 */
export function getCapabilities(): Promise<BluetoothCapabilities> {
  return MunimBluetooth.getCapabilities()
}

/**
 * Start scanning for BLE devices.
 *
 * @param options - Optional scan configuration including service UUIDs to filter by.
 */
export function startScan(options?: ScanOptions): void {
  return MunimBluetooth.startScan(options)
}

/**
 * Stop scanning for BLE devices.
 */
export function stopScan(): void {
  return MunimBluetooth.stopScan()
}

/**
 * Connect to a BLE device.
 *
 * @param deviceId - The unique identifier of the device to connect to.
 * @returns Promise resolving when connection is established or rejected.
 */
export function connect(deviceId: string): Promise<void> {
  return MunimBluetooth.connect(deviceId)
}

/**
 * Disconnect from a BLE device.
 *
 * @param deviceId - The unique identifier of the device to disconnect from.
 */
export function disconnect(deviceId: string): void {
  return MunimBluetooth.disconnect(deviceId)
}

/**
 * Discover GATT services for a connected device.
 *
 * @param deviceId - The unique identifier of the connected device.
 * @returns Promise resolving to array of discovered services.
 */
export function discoverServices(deviceId: string): Promise<GATTService[]> {
  return MunimBluetooth.discoverServices(deviceId)
}

/**
 * Read a characteristic value from a connected device.
 *
 * @param deviceId - The unique identifier of the connected device.
 * @param serviceUUID - The UUID of the service containing the characteristic.
 * @param characteristicUUID - The UUID of the characteristic to read.
 * @returns Promise resolving to the characteristic value.
 */
export function readCharacteristic(
  deviceId: string,
  serviceUUID: string,
  characteristicUUID: string
): Promise<CharacteristicValue> {
  return MunimBluetooth.readCharacteristic(
    deviceId,
    serviceUUID,
    characteristicUUID
  )
}

/**
 * Read a descriptor value from a connected device.
 */
export function readDescriptor(
  deviceId: string,
  serviceUUID: string,
  characteristicUUID: string,
  descriptorUUID: string
): Promise<DescriptorValue> {
  return MunimBluetooth.readDescriptor(
    deviceId,
    serviceUUID,
    characteristicUUID,
    descriptorUUID
  )
}

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
export function writeCharacteristic(
  deviceId: string,
  serviceUUID: string,
  characteristicUUID: string,
  value: string,
  writeType?: 'write' | 'writeWithoutResponse'
): Promise<void> {
  return MunimBluetooth.writeCharacteristic(
    deviceId,
    serviceUUID,
    characteristicUUID,
    value,
    writeType
  )
}

/**
 * Write a descriptor value to a connected device.
 */
export function writeDescriptor(
  deviceId: string,
  serviceUUID: string,
  characteristicUUID: string,
  descriptorUUID: string,
  value: string
): Promise<void> {
  return MunimBluetooth.writeDescriptor(
    deviceId,
    serviceUUID,
    characteristicUUID,
    descriptorUUID,
    value
  )
}

/**
 * Subscribe to notifications/indications from a characteristic.
 *
 * @param deviceId - The unique identifier of the connected device.
 * @param serviceUUID - The UUID of the service containing the characteristic.
 * @param characteristicUUID - The UUID of the characteristic to subscribe to.
 */
export function subscribeToCharacteristic(
  deviceId: string,
  serviceUUID: string,
  characteristicUUID: string
): void {
  return MunimBluetooth.subscribeToCharacteristic(
    deviceId,
    serviceUUID,
    characteristicUUID
  )
}

/**
 * Unsubscribe from notifications/indications from a characteristic.
 *
 * @param deviceId - The unique identifier of the connected device.
 * @param serviceUUID - The UUID of the service containing the characteristic.
 * @param characteristicUUID - The UUID of the characteristic to unsubscribe from.
 */
export function unsubscribeFromCharacteristic(
  deviceId: string,
  serviceUUID: string,
  characteristicUUID: string
): void {
  return MunimBluetooth.unsubscribeFromCharacteristic(
    deviceId,
    serviceUUID,
    characteristicUUID
  )
}

/**
 * Get list of currently connected devices.
 *
 * @returns Promise resolving to array of connected device IDs.
 */
export function getConnectedDevices(): Promise<string[]> {
  return MunimBluetooth.getConnectedDevices()
}

/**
 * Read RSSI (signal strength) for a connected device.
 *
 * @param deviceId - The unique identifier of the connected device.
 * @returns Promise resolving to RSSI value in dBm.
 */
export function readRSSI(deviceId: string): Promise<number> {
  return MunimBluetooth.readRSSI(deviceId)
}

/**
 * Request an ATT MTU. Android supports this directly; iOS rejects with unsupported.
 */
export function requestMTU(deviceId: string, mtu: number): Promise<number> {
  return MunimBluetooth.requestMTU(deviceId, mtu)
}

/**
 * Set preferred BLE PHY. Android 8+ supports this when hardware allows it.
 */
export function setPreferredPhy(
  deviceId: string,
  txPhy: BluetoothPhy,
  rxPhy: BluetoothPhy,
  phyOption?: BluetoothPhyOption
): Promise<void> {
  return MunimBluetooth.setPreferredPhy(deviceId, txPhy, rxPhy, phyOption)
}

/**
 * Read current BLE PHY. Android 8+ supports this when hardware allows it.
 */
export function readPhy(deviceId: string): Promise<PhyStatus> {
  return MunimBluetooth.readPhy(deviceId)
}

/**
 * Return current platform bond state for a device.
 */
export function getBondState(deviceId: string): Promise<BondState> {
  return MunimBluetooth.getBondState(deviceId)
}

/**
 * Start platform pairing/bonding for a device.
 */
export function createBond(deviceId: string): Promise<BondState> {
  return MunimBluetooth.createBond(deviceId)
}

/**
 * Remove a platform bond where supported.
 */
export function removeBond(deviceId: string): Promise<BondState> {
  return MunimBluetooth.removeBond(deviceId)
}

/**
 * Start BLE extended advertising where supported.
 */
export function startExtendedAdvertising(
  options: ExtendedAdvertisingOptions
): Promise<string> {
  return MunimBluetooth.startExtendedAdvertising(options)
}

/**
 * Stop a BLE extended advertising set.
 */
export function stopExtendedAdvertising(advertisingId: string): void {
  return MunimBluetooth.stopExtendedAdvertising(advertisingId)
}

/**
 * Publish a local BLE L2CAP channel where supported.
 */
export function publishL2CAPChannel(
  encryptionRequired?: boolean
): Promise<L2CAPChannel> {
  return MunimBluetooth.publishL2CAPChannel(encryptionRequired)
}

/**
 * Stop a local BLE L2CAP channel.
 */
export function unpublishL2CAPChannel(psm: number): void {
  return MunimBluetooth.unpublishL2CAPChannel(psm)
}

/**
 * Open an outbound BLE L2CAP channel.
 */
export function openL2CAPChannel(
  deviceId: string,
  psm: number
): Promise<L2CAPChannel> {
  return MunimBluetooth.openL2CAPChannel(deviceId, psm)
}

/**
 * Close an L2CAP channel.
 */
export function closeL2CAPChannel(channelId: string): void {
  return MunimBluetooth.closeL2CAPChannel(channelId)
}

/**
 * Send hex data over an open L2CAP channel.
 */
export function sendL2CAPData(
  channelId: string,
  value: string
): Promise<void> {
  return MunimBluetooth.sendL2CAPData(channelId, value)
}

/**
 * Start Classic Bluetooth discovery where supported.
 */
export function startClassicScan(): void {
  return MunimBluetooth.startClassicScan()
}

/**
 * Stop Classic Bluetooth discovery.
 */
export function stopClassicScan(): void {
  return MunimBluetooth.stopClassicScan()
}

/**
 * Connect to a Classic Bluetooth RFCOMM service where supported.
 */
export function connectClassic(
  deviceId: string,
  serviceUUID?: string
): Promise<void> {
  return MunimBluetooth.connectClassic(deviceId, serviceUUID)
}

/**
 * Listen for incoming Classic Bluetooth RFCOMM connections on Android.
 */
export function startClassicServer(
  serviceUUID?: string,
  serviceName?: string
): Promise<void> {
  return MunimBluetooth.startClassicServer(serviceUUID, serviceName)
}

/**
 * Stop a Classic Bluetooth RFCOMM listener on Android.
 */
export function stopClassicServer(serviceUUID?: string): void {
  return MunimBluetooth.stopClassicServer(serviceUUID)
}

/**
 * Disconnect a Classic Bluetooth device.
 */
export function disconnectClassic(deviceId: string): void {
  return MunimBluetooth.disconnectClassic(deviceId)
}

/**
 * Write hex data to a Classic Bluetooth RFCOMM connection.
 */
export function writeClassic(
  deviceId: string,
  value: string
): Promise<void> {
  return MunimBluetooth.writeClassic(deviceId, value)
}

/**
 * Start a best-effort background BLE session.
 *
 * Android uses a foreground service and restores scan/advertising/configured
 * GATT services after normal service process recreation. iOS relies on the host
 * app's Bluetooth background modes and CoreBluetooth state restoration, with
 * terminated-state relaunch still limited by Apple's current relaunch rules.
 * User force-quit/force-stop is controlled by the OS and cannot be bypassed.
 */
export function startBackgroundSession(
  options: BackgroundSessionOptions
): void {
  return MunimBluetooth.startBackgroundSession(options)
}

/**
 * Stop the active background BLE session.
 */
export function stopBackgroundSession(): void {
  return MunimBluetooth.stopBackgroundSession()
}

/**
 * Start Apple Multipeer Connectivity transport. This is iOS/iPadOS/macOS/tvOS
 * only; Android cannot join Apple Multipeer sessions.
 */
export function startMultipeerSession(
  options: MultipeerSessionOptions
): void {
  return MunimBluetooth.startMultipeerSession(options)
}

/**
 * Stop the active Apple Multipeer Connectivity session.
 */
export function stopMultipeerSession(): void {
  return MunimBluetooth.stopMultipeerSession()
}

/**
 * Invite a discovered Multipeer peer by runtime peer id.
 */
export function inviteMultipeerPeer(peerId: string): void {
  return MunimBluetooth.inviteMultipeerPeer(peerId)
}

/**
 * Return discovered/connected Apple Multipeer peers.
 */
export function getMultipeerPeers(): Promise<MultipeerPeer[]> {
  return MunimBluetooth.getMultipeerPeers()
}

/**
 * Send hex data over Apple Multipeer Connectivity. Omit peerIds to broadcast
 * to all connected peers.
 */
export function sendMultipeerMessage(
  value: string,
  peerIds?: string[],
  reliable?: boolean
): Promise<void> {
  return MunimBluetooth.sendMultipeerMessage(value, peerIds, reliable)
}

// ========== Event Management ==========

/**
 * Add a device found event listener (for scanning).
 *
 * @param callback - Function to call when a device is found
 * @returns A function to remove the listener
 */
export function addDeviceFoundListener(
  callback: (device: BLEDevice) => void
): () => void {
  if (!eventEmitter) {
    console.warn(
      '[munim-bluetooth] Cannot add listener - event emitter not available'
    )
    return () => {}
  }

  const subscription = eventEmitter.addListener('deviceFound', callback)
  return () => subscription.remove()
}

/**
 * Add an event listener.
 *
 * @param eventName - The name of the event to listen for.
 * @param callback - The callback to invoke when the event occurs.
 * @returns A function to remove the listener
 */
export function addEventListener<EventName extends BluetoothEventName>(
  eventName: EventName,
  callback: (data: BluetoothEventMap[EventName]) => void
): () => void {
  if (!eventEmitter) {
    console.warn(
      '[munim-bluetooth] Cannot add listener - event emitter not available'
    )
    return () => {}
  }

  const subscription = eventEmitter.addListener(eventName, callback)
  return () => subscription.remove()
}

/**
 * Add an event listener (legacy method).
 *
 * @param eventName - The name of the event to listen for.
 */
export function addListener(eventName: string): void {
  return MunimBluetooth.addListener(eventName)
}

/**
 * Remove event listeners.
 *
 * @param count - Number of listeners to remove.
 */
export function removeListeners(count: number): void {
  return MunimBluetooth.removeListeners(count)
}

// ========== Type Exports ==========

export type {
  AdvertisingDataTypes,
  BLEDevice,
  BackgroundSessionOptions,
  MultipeerSessionOptions,
  MultipeerPeer,
  MultipeerDiscoveryInfoEntry,
  MultipeerEncryptionPreference,
  MultipeerPeerState,
  ScanOptions,
  GATTService,
  GATTDescriptor,
  CharacteristicValue,
  DescriptorValue,
  BluetoothCapabilities,
  BluetoothPhy,
  BluetoothPhyOption,
  BondState,
  PhyStatus,
  ExtendedAdvertisingOptions,
  L2CAPChannel,
}

// Default export for convenience
export default {
  // Peripheral
  startAdvertising,
  stopAdvertising,
  updateAdvertisingData,
  getAdvertisingData,
  setServices,
  updateCharacteristicValue,
  // Central
  isBluetoothEnabled,
  requestBluetoothPermission,
  getCapabilities,
  startScan,
  stopScan,
  connect,
  disconnect,
  discoverServices,
  readCharacteristic,
  readDescriptor,
  writeCharacteristic,
  writeDescriptor,
  subscribeToCharacteristic,
  unsubscribeFromCharacteristic,
  getConnectedDevices,
  readRSSI,
  requestMTU,
  setPreferredPhy,
  readPhy,
  getBondState,
  createBond,
  removeBond,
  startExtendedAdvertising,
  stopExtendedAdvertising,
  publishL2CAPChannel,
  unpublishL2CAPChannel,
  openL2CAPChannel,
  closeL2CAPChannel,
  sendL2CAPData,
  startClassicScan,
  stopClassicScan,
  connectClassic,
  startClassicServer,
  stopClassicServer,
  disconnectClassic,
  writeClassic,
  startBackgroundSession,
  stopBackgroundSession,
  startMultipeerSession,
  stopMultipeerSession,
  inviteMultipeerPeer,
  getMultipeerPeers,
  sendMultipeerMessage,
  // Events
  addDeviceFoundListener,
  addEventListener,
  addListener,
  removeListeners,
}

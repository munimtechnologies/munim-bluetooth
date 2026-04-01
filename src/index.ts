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
  ScanOptions,
  GATTService,
  CharacteristicValue,
} from './specs/munim-bluetooth.nitro'

const MunimBluetooth =
  NitroModules.createHybridObject<MunimBluetoothSpec>('MunimBluetooth')

// Android emits through DeviceEventEmitter from the Nitro module itself.
// iOS exposes a dedicated RCTEventEmitter module.
const nativeEventModule =
  Platform.OS === 'ios' ? NativeModules.MunimBluetoothEventEmitter : null

console.log(
  '[munim-bluetooth] Checking for event emitter...',
  Platform.OS === 'android'
    ? 'USING_DEVICE_EVENT_EMITTER'
    : nativeEventModule
      ? 'FOUND'
      : 'NOT FOUND'
)
console.log(
  '[munim-bluetooth] Available NativeModules:',
  Object.keys(NativeModules).filter(
    (key) => key.includes('Bluetooth') || key.includes('Munim')
  )
)

let eventEmitter: Pick<NativeEventEmitter, 'addListener'> | null = null

if (Platform.OS === 'android') {
  eventEmitter = DeviceEventEmitter
  console.log('[munim-bluetooth] Using DeviceEventEmitter on Android')
} else if (nativeEventModule) {
  try {
    eventEmitter = new NativeEventEmitter(nativeEventModule)
    console.log('[munim-bluetooth] Event emitter initialized successfully')
  } catch (error) {
    console.error(
      '[munim-bluetooth] Failed to initialize event emitter:',
      error
    )
  }
} else {
  console.warn(
    '[munim-bluetooth] Event emitter module not found in NativeModules - device discovery events will not work'
  )
  console.warn(
    '[munim-bluetooth] This usually means the native module was not linked properly or needs a rebuild'
  )
}

// ========== Peripheral Features ==========

/**
 * Start advertising as a Bluetooth peripheral with supported advertising data.
 *
 * @param options - An object with serviceUUIDs (string[]) and supported advertising data types.
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
 * Request Bluetooth permissions (Android) or check authorization status (iOS).
 *
 * @returns Promise resolving to true if permissions are granted, false otherwise.
 */
export function requestBluetoothPermission(): Promise<boolean> {
  return MunimBluetooth.requestBluetoothPermission()
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
 * Start a best-effort background BLE session.
 *
 * Android uses a foreground service. iOS relies on the host app's Bluetooth
 * background modes and state restoration.
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
export function addEventListener(
  eventName: string,
  callback: (data: any) => void
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
  ScanOptions,
  GATTService,
  CharacteristicValue,
}

// Default export for convenience
export default {
  // Peripheral
  startAdvertising,
  stopAdvertising,
  updateAdvertisingData,
  getAdvertisingData,
  setServices,
  // Central
  isBluetoothEnabled,
  requestBluetoothPermission,
  startScan,
  stopScan,
  connect,
  disconnect,
  discoverServices,
  readCharacteristic,
  writeCharacteristic,
  subscribeToCharacteristic,
  unsubscribeFromCharacteristic,
  getConnectedDevices,
  readRSSI,
  startBackgroundSession,
  stopBackgroundSession,
  // Events
  addDeviceFoundListener,
  addEventListener,
  addListener,
  removeListeners,
}

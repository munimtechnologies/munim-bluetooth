import { NitroModules } from 'react-native-nitro-modules'
import type {
  MunimBluetooth as MunimBluetoothSpec,
  AdvertisingDataTypes,
  BLEDevice,
  ScanOptions,
  GATTService,
  CharacteristicValue,
} from './specs/munim-bluetooth.nitro'

const MunimBluetooth =
  NitroModules.createHybridObject<MunimBluetoothSpec>('MunimBluetooth')

// ========== Peripheral Features ==========

/**
 * Start advertising as a Bluetooth peripheral with supported advertising data.
 *
 * @param options - An object with serviceUUIDs (string[]) and supported advertising data types.
 */
export function startAdvertising(options: {
  serviceUUIDs: string[];
  localName?: string;
  manufacturerData?: string;
  advertisingData?: AdvertisingDataTypes;
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

// ========== Event Management ==========

/**
 * Add an event listener.
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
  // Events
  addListener,
  removeListeners,
}

import { type HybridObject } from 'react-native-nitro-modules'

// BLE Advertising Data Types - Only Platform-Supported Types
export interface AdvertisingDataTypes {
  // 0x01 - Flags (partial support)
  flags?: number

  // 0x02-0x07 - Service UUIDs (fully supported)
  incompleteServiceUUIDs16?: string[]
  completeServiceUUIDs16?: string[]
  incompleteServiceUUIDs32?: string[]
  completeServiceUUIDs32?: string[]
  incompleteServiceUUIDs128?: string[]
  completeServiceUUIDs128?: string[]

  // 0x08-0x09 - Local Name (fully supported)
  shortenedLocalName?: string
  completeLocalName?: string

  // 0x0A - Tx Power Level (fully supported)
  txPowerLevel?: number

  // 0x14-0x15 - Service Solicitation (fully supported)
  serviceSolicitationUUIDs16?: string[]
  serviceSolicitationUUIDs128?: string[]

  // 0x16, 0x20, 0x21 - Service Data (fully supported)
  serviceData16?: Array<{
    uuid: string
    data: string
  }>
  serviceData32?: Array<{
    uuid: string
    data: string
  }>
  serviceData128?: Array<{
    uuid: string
    data: string
  }>

  // 0x19 - Appearance (partial support)
  appearance?: number

  // 0x1F - Service Solicitation (32-bit) (fully supported)
  serviceSolicitationUUIDs32?: string[]

  // 0xFF - Manufacturer Specific Data (fully supported)
  manufacturerData?: string
}

// BLE Device information
export interface BLEDevice {
  id: string
  name?: string
  rssi?: number
  advertisingData?: AdvertisingDataTypes
  serviceUUIDs?: string[]
  isConnectable?: boolean
}

// Scan options
export interface ScanOptions {
  serviceUUIDs?: string[]
  allowDuplicates?: boolean
  scanMode?: 'lowPower' | 'balanced' | 'lowLatency'
}

// GATT Service
export interface GATTService {
  uuid: string
  characteristics: Array<{
    uuid: string
    properties: string[]
    value?: string
  }>
}

// Characteristic value
export interface CharacteristicValue {
  value: string
  serviceUUID: string
  characteristicUUID: string
}

export interface MunimBluetooth
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {
  // ========== Peripheral Features ==========

  /**
   * Start advertising as a Bluetooth peripheral with supported advertising data.
   *
   * @param options - An object with serviceUUIDs (string[]) and supported advertising data types.
   *                  This must be a plain JS object (no Maps/Sets/functions).
   */
  startAdvertising(options: {
    serviceUUIDs: string[]
    localName?: string
    manufacturerData?: string
    advertisingData?: AdvertisingDataTypes
  }): void

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

  // ========== Central/Manager Features ==========

  /**
   * Check if Bluetooth is enabled on the device.
   *
   * @returns Promise resolving to true if Bluetooth is enabled, false otherwise.
   */
  isBluetoothEnabled(): Promise<boolean>

  /**
   * Request Bluetooth permissions (Android) or check authorization status (iOS).
   *
   * @returns Promise resolving to true if permissions are granted, false otherwise.
   */
  requestBluetoothPermission(): Promise<boolean>

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
    writeType?: 'write' | 'writeWithoutResponse'
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

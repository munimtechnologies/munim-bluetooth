# munim-bluetooth

A comprehensive React Native library for all your Bluetooth Low Energy (BLE) needs, supporting both peripheral and central roles. Built with React Native's Nitro modules architecture for high performance.

[![Version](https://img.shields.io/npm/v/munim-bluetooth.svg)](https://www.npmjs.com/package/munim-bluetooth)
[![Downloads](https://img.shields.io/npm/dm/munim-bluetooth.svg)](https://www.npmjs.com/package/munim-bluetooth)
[![License](https://img.shields.io/npm/l/munim-bluetooth.svg)](https://github.com/munimtechnologies/munim-bluetooth/LICENSE)

## Features

### Peripheral Mode (BLE Advertising & GATT Server)

- 🔵 **BLE Peripheral Mode**: Transform your React Native app into a BLE peripheral device
- 📡 **Service Advertising**: Advertise custom GATT services with multiple characteristics
- 🔄 **Real-time Communication**: Support for read, write, and notify operations
- ✅ **Platform-Supported BLE Advertising**: Support for core BLE advertising data types
- 🔧 **Dynamic Updates**: Update advertising data while advertising is active

### Central Mode (BLE Scanning & GATT Client)

- 🔍 **Device Scanning**: Scan for BLE devices with filtering options
- 🔗 **Device Connection**: Connect and disconnect from BLE devices
- 📊 **GATT Operations**: Discover services, read/write characteristics
- 🔔 **Notifications**: Subscribe to characteristic notifications/indications
- 📶 **RSSI Monitoring**: Read signal strength for connected devices

### Additional Features

- 📱 **Cross-platform**: Works on both iOS and Android
- 🎯 **TypeScript Support**: Full TypeScript definitions included
- ⚡ **High Performance**: Built with React Native's Nitro modules architecture
- 🔐 **Permission Handling**: Built-in permission request helpers

## Requirements

- React Native v0.76.0 or higher
- Node 18.0.0 or higher

> [!IMPORTANT]  
> To Support `Nitro Modules` you need to install React Native version v0.78.0 or higher.

## Installation

```bash
npm install munim-bluetooth react-native-nitro-modules
```

### iOS Setup

For iOS, the library is automatically linked. However, you need to add the following to your `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth for BLE communication</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to create a peripheral device</string>
```

**For Expo projects**, add these permissions to your `app.json`:

```json
{
  "expo": {
    "ios": {
      "infoPlist": {
        "NSBluetoothAlwaysUsageDescription": "This app uses Bluetooth for BLE communication",
        "NSBluetoothPeripheralUsageDescription": "This app uses Bluetooth to create a peripheral device"
      }
    }
  }
}
```

### Android Setup

The required permissions are automatically included in the library's `AndroidManifest.xml`. However, for Android 12+ (API 31+), you may need to add the following to your app's `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

For Android 12+, you also need to specify that these permissions are not used for device discovery:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
```

## Quick Start

### Peripheral Mode Example

```typescript
import { startAdvertising, stopAdvertising, setServices } from 'munim-bluetooth'

// Start advertising
startAdvertising({
  serviceUUIDs: ['180D', '180F'],
  localName: 'My Device',
  advertisingData: {
    flags: 0x06,
    completeLocalName: 'My Smart Device',
    txPowerLevel: -12,
    manufacturerData: '0102030405',
  },
})

// Set up GATT services
setServices([
  {
    uuid: '180D',
    characteristics: [
      {
        uuid: '2A37',
        properties: ['read', 'notify'],
        value: 'Hello World',
      },
    ],
  },
])

// Stop advertising
stopAdvertising()
```

### Central Mode Example

```typescript
import {
  startScan,
  stopScan,
  connect,
  discoverServices,
  readCharacteristic,
  subscribeToCharacteristic,
} from 'munim-bluetooth'

// Start scanning
startScan({
  serviceUUIDs: ['180D'],
  allowDuplicates: false,
  scanMode: 'balanced',
})

// Listen for discovered devices
// (Event handling would be implemented here)

// Connect to a device
await connect('device-id-here')

// Discover services
const services = await discoverServices('device-id-here')

// Read a characteristic
const value = await readCharacteristic('device-id-here', '180D', '2A37')

// Subscribe to notifications
subscribeToCharacteristic('device-id-here', '180D', '2A37')
```

## API Reference

### Peripheral Functions

#### `startAdvertising(options)`

Starts BLE advertising with the specified options.

**Parameters:**

- `options` (object):
  - `serviceUUIDs` (string[]): Array of service UUIDs to advertise
  - `localName?` (string): Device name (legacy support)
  - `manufacturerData?` (string): Manufacturer data in hex format (legacy support)
  - `advertisingData?` (AdvertisingDataTypes): Platform-supported advertising data

#### `updateAdvertisingData(advertisingData)`

Updates the advertising data while advertising is active.

**Parameters:**

- `advertisingData` (AdvertisingDataTypes): New advertising data

#### `getAdvertisingData()`

Returns a Promise that resolves to the current advertising data.

**Returns:** Promise<AdvertisingDataTypes>

#### `stopAdvertising()`

Stops BLE advertising.

#### `setServices(services)`

Sets GATT services and characteristics.

**Parameters:**

- `services` (GATTService[]): Array of service objects

### Central Functions

#### `isBluetoothEnabled()`

Checks if Bluetooth is enabled on the device.

**Returns:** Promise<boolean>

#### `requestBluetoothPermission()`

Requests Bluetooth permissions (Android) or checks authorization status (iOS).

**Returns:** Promise<boolean>

#### `startScan(options?)`

Starts scanning for BLE devices.

**Parameters:**

- `options?` (ScanOptions):
  - `serviceUUIDs?` (string[]): Filter by service UUIDs
  - `allowDuplicates?` (boolean): Allow duplicate scan results
  - `scanMode?` ('lowPower' | 'balanced' | 'lowLatency'): Scan mode

#### `stopScan()`

Stops scanning for BLE devices.

#### `connect(deviceId)`

Connects to a BLE device.

**Parameters:**

- `deviceId` (string): The unique identifier of the device

**Returns:** Promise<void>

#### `disconnect(deviceId)`

Disconnects from a BLE device.

**Parameters:**

- `deviceId` (string): The unique identifier of the device

#### `discoverServices(deviceId)`

Discovers GATT services for a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device

**Returns:** Promise<GATTService[]>

#### `readCharacteristic(deviceId, serviceUUID, characteristicUUID)`

Reads a characteristic value from a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic

**Returns:** Promise<CharacteristicValue>

#### `writeCharacteristic(deviceId, serviceUUID, characteristicUUID, value, writeType?)`

Writes a value to a characteristic on a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic
- `value` (string): The value to write (hex string)
- `writeType?` ('write' | 'writeWithoutResponse'): Write type

**Returns:** Promise<void>

#### `subscribeToCharacteristic(deviceId, serviceUUID, characteristicUUID)`

Subscribes to notifications/indications from a characteristic.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic

#### `unsubscribeFromCharacteristic(deviceId, serviceUUID, characteristicUUID)`

Unsubscribes from notifications/indications from a characteristic.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device
- `serviceUUID` (string): The UUID of the service
- `characteristicUUID` (string): The UUID of the characteristic

#### `getConnectedDevices()`

Gets list of currently connected devices.

**Returns:** Promise<string[]>

#### `readRSSI(deviceId)`

Reads RSSI (signal strength) for a connected device.

**Parameters:**

- `deviceId` (string): The unique identifier of the connected device

**Returns:** Promise<number>

### Event Management

#### `addListener(eventName)`

Adds an event listener.

**Parameters:**

- `eventName` (string): The name of the event to listen for

**Event Types:**

- `deviceFound`: Emitted when a BLE device is discovered during scanning
- `deviceConnected`: Emitted when a device is successfully connected
- `deviceDisconnected`: Emitted when a device is disconnected
- `servicesDiscovered`: Emitted when services are discovered for a device
- `characteristicsDiscovered`: Emitted when characteristics are discovered
- `characteristicValueChanged`: Emitted when a characteristic value changes
- `writeSuccess`: Emitted when a write operation succeeds
- `writeError`: Emitted when a write operation fails
- `rssiUpdated`: Emitted when RSSI is updated

#### `removeListeners(count)`

Removes event listeners.

**Parameters:**

- `count` (number): Number of listeners to remove

## Types

### `AdvertisingDataTypes`

Platform-supported interface for BLE advertising data types:

```typescript
interface AdvertisingDataTypes {
  flags?: number
  incompleteServiceUUIDs16?: string[]
  completeServiceUUIDs16?: string[]
  incompleteServiceUUIDs32?: string[]
  completeServiceUUIDs32?: string[]
  incompleteServiceUUIDs128?: string[]
  completeServiceUUIDs128?: string[]
  shortenedLocalName?: string
  completeLocalName?: string
  txPowerLevel?: number
  serviceSolicitationUUIDs16?: string[]
  serviceSolicitationUUIDs128?: string[]
  serviceSolicitationUUIDs32?: string[]
  serviceData16?: Array<{ uuid: string; data: string }>
  serviceData32?: Array<{ uuid: string; data: string }>
  serviceData128?: Array<{ uuid: string; data: string }>
  appearance?: number
  manufacturerData?: string
}
```

### `GATTService`

```typescript
interface GATTService {
  uuid: string
  characteristics: Array<{
    uuid: string
    properties: string[]
    value?: string
  }>
}
```

### `ScanOptions`

```typescript
interface ScanOptions {
  serviceUUIDs?: string[]
  allowDuplicates?: boolean
  scanMode?: 'lowPower' | 'balanced' | 'lowLatency'
}
```

## Usage Examples

### Health Device (Peripheral)

```typescript
import { startAdvertising, setServices } from 'munim-bluetooth'

// Health device advertising
startAdvertising({
  serviceUUIDs: ['180D', '180F'], // Heart Rate, Battery Service
  advertisingData: {
    flags: 0x06,
    completeLocalName: 'Health Monitor',
    appearance: 0x03c0, // Generic Watch
    txPowerLevel: -8,
    manufacturerData: '0102030405',
    serviceData16: [
      { uuid: '180D', data: '6400' }, // Heart rate: 100 bpm
      { uuid: '180F', data: '64' }, // Battery: 100%
    ],
  },
})

// Set up GATT services
setServices([
  {
    uuid: '180D', // Heart Rate Service
    characteristics: [
      {
        uuid: '2A37', // Heart Rate Measurement
        properties: ['read', 'notify'],
        value: '6400', // 100 bpm
      },
    ],
  },
])
```

### Device Scanner (Central)

```typescript
import {
  startScan,
  stopScan,
  connect,
  discoverServices,
  readCharacteristic,
  subscribeToCharacteristic,
  addListener,
} from 'munim-bluetooth'

// Start scanning
startScan({
  serviceUUIDs: ['180D'], // Filter by Heart Rate service
  allowDuplicates: false,
  scanMode: 'balanced',
})

// Listen for discovered devices
addListener('deviceFound')
// Handle deviceFound events to get device information

// Connect to a device
const deviceId = '...' // From deviceFound event
await connect(deviceId)

// Discover services
const services = await discoverServices(deviceId)

// Read a characteristic
const heartRate = await readCharacteristic(deviceId, '180D', '2A37')

// Subscribe to notifications
subscribeToCharacteristic(deviceId, '180D', '2A37')

// Listen for value changes
addListener('characteristicValueChanged')
// Handle characteristicValueChanged events
```

## Troubleshooting

### Common Issues

1. **Permission Denied**: Ensure you have the necessary Bluetooth permissions in your app
2. **Advertising Not Starting**: Check that Bluetooth is enabled on the device
3. **Services Not Visible**: Verify that your service UUIDs are properly formatted
4. **Scanning Not Working**: On Android 6.0+, ensure location permissions are granted
5. **Connection Fails**: Verify the device is in range and advertising

### Platform-Specific Notes

**iOS:**

- All features work natively through Core Bluetooth
- Permission prompts appear automatically when needed

**Android:**

- Android 12+ requires additional permission flags
- Location permissions are required for BLE scanning
- Some features may behave differently on different Android versions

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## Credits

Bootstrapped with [create-nitro-module](https://github.com/patrickkabwe/create-nitro-module).

## License

MIT

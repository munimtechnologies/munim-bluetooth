package com.munimbluetooth

import com.margelo.nitro.munimbluetooth.HybridMunimBluetoothSpec
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import java.util.*

class HybridMunimBluetooth : HybridMunimBluetoothSpec() {
    private val TAG = "HybridMunimBluetooth"
    
    // Peripheral Manager
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var gattServerReady = false
    private var advertiseJob: Job? = null
    private var currentAdvertisingData: Map<String, Any>? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    // Central Manager
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()
    private val connectedDevices = mutableMapOf<String, BluetoothGatt>()
    private val deviceCharacteristics = mutableMapOf<String, MutableList<BluetoothGattCharacteristic>>()
    
    init {
        // Initialize Bluetooth managers - this would need ReactApplicationContext in real implementation
        // For now, we'll initialize them when needed
    }
    
    private fun getBluetoothManager(): BluetoothManager? {
        // In a real implementation, this would get the context from ReactApplicationContext
        // For now, return null - actual implementation would need context injection
        return null
    }
    
    private fun ensureBluetoothManager() {
        if (bluetoothManager == null) {
            bluetoothManager = getBluetoothManager()
            bluetoothAdapter = bluetoothManager?.adapter
        }
    }
    
    // MARK: - Peripheral Features
    
    override fun startAdvertising(options: Map<String, Any>) {
        ensureBluetoothManager()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or not available")
            return
        }
        
        val serviceUUIDs = options["serviceUUIDs"] as? List<String>
        if (serviceUUIDs == null || serviceUUIDs.isEmpty()) {
            Log.e(TAG, "No service UUIDs provided for advertising")
            return
        }
        
        // Ensure GATT server is set up before advertising
        if (!gattServerReady) {
            setServicesFromOptions(serviceUUIDs)
        }
        
        // Cancel any previous advertising job
        advertiseJob?.cancel()
        advertiseJob = CoroutineScope(Dispatchers.Main).launch {
            delay(300) // Wait for GATT server to be ready
            advertiser = adapter.bluetoothLeAdvertiser
            
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()
            
            val dataBuilder = AdvertiseData.Builder()
            
            // Process comprehensive advertising data
            val advertisingDataMap = options["advertisingData"] as? Map<String, Any>
            if (advertisingDataMap != null) {
                processAdvertisingData(advertisingDataMap, dataBuilder)
            }
            
            // Legacy support - add service UUIDs
            for (uuid in serviceUUIDs) {
                dataBuilder.addServiceUuid(ParcelUuid.fromString(uuid))
            }
            
            // Legacy support - local name
            val localName = options["localName"] as? String
            if (localName != null) {
                dataBuilder.setIncludeDeviceName(true)
            }
            
            // Legacy support - manufacturer data
            val manufacturerData = options["manufacturerData"] as? String
            if (manufacturerData != null) {
                val data = hexStringToByteArray(manufacturerData)
                if (data != null) {
                    dataBuilder.addManufacturerData(0x0000, data) // Default manufacturer code
                }
            }
            
            currentAdvertisingData = mapOf(
                "advertisingData" to (advertisingDataMap ?: emptyMap<String, Any>()),
                "localName" to (localName ?: ""),
                "manufacturerData" to (manufacturerData ?: "")
            )
            
            advertiser?.startAdvertising(settings, dataBuilder.build(), object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.i(TAG, "Advertising started successfully")
                }
                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "Advertising failed: $errorCode")
                }
            })
        }
    }
    
    override fun updateAdvertisingData(advertisingData: Map<String, Any>) {
        ensureBluetoothManager()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or not available")
            return
        }
        
        advertiser?.stopAdvertising(object : AdvertiseCallback() {})
        
        advertiseJob?.cancel()
        advertiseJob = CoroutineScope(Dispatchers.Main).launch {
            delay(100) // Brief delay before restarting
            advertiser = adapter.bluetoothLeAdvertiser
            
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()
            
            val dataBuilder = AdvertiseData.Builder()
            processAdvertisingData(advertisingData, dataBuilder)
            
            currentAdvertisingData = mapOf("advertisingData" to advertisingData)
            
            advertiser?.startAdvertising(settings, dataBuilder.build(), object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.i(TAG, "Advertising updated successfully")
                }
                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "Advertising update failed: $errorCode")
                }
            })
        }
    }
    
    override fun getAdvertisingData(): Map<String, Any> {
        return currentAdvertisingData ?: emptyMap()
    }
    
    override fun stopAdvertising() {
        advertiser?.stopAdvertising(object : AdvertiseCallback() {})
        advertiser = null
        advertiseJob?.cancel()
        currentAdvertisingData = null
    }
    
    override fun setServices(services: List<Map<String, Any>>) {
        ensureBluetoothManager()
        gattServerReady = false
        
        val manager = bluetoothManager ?: return
        gattServer = manager.openGattServer(null, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                // Handle connection state changes
            }
            
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.value)
            }
            
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
            }
        })
        
        gattServer?.clearServices()
        
        for (serviceMap in services) {
            val serviceUuid = serviceMap["uuid"] as? String ?: continue
            val service = BluetoothGattService(
                UUID.fromString(serviceUuid),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            
            val characteristics = serviceMap["characteristics"] as? List<Map<String, Any>>
            if (characteristics != null) {
                for (charMap in characteristics) {
                    val charUuid = charMap["uuid"] as? String ?: continue
                    val propertiesArray = charMap["properties"] as? List<String>
                    
                    var properties = 0
                    if (propertiesArray != null) {
                        for (prop in propertiesArray) {
                            when (prop) {
                                "read" -> properties = properties or BluetoothGattCharacteristic.PROPERTY_READ
                                "write" -> properties = properties or BluetoothGattCharacteristic.PROPERTY_WRITE
                                "notify" -> properties = properties or BluetoothGattCharacteristic.PROPERTY_NOTIFY
                                "indicate" -> properties = properties or BluetoothGattCharacteristic.PROPERTY_INDICATE
                            }
                        }
                    }
                    
                    val permissions = BluetoothGattCharacteristic.PERMISSION_READ or
                                     BluetoothGattCharacteristic.PERMISSION_WRITE
                    
                    val characteristic = BluetoothGattCharacteristic(
                        UUID.fromString(charUuid),
                        properties,
                        permissions
                    )
                    
                    val value = charMap["value"] as? String
                    if (value != null) {
                        characteristic.value = value.toByteArray()
                    }
                    
                    service.addCharacteristic(characteristic)
                }
            }
            
            gattServer?.addService(service)
        }
        
        gattServerReady = true
    }
    
    // MARK: - Central Features
    
    override fun isBluetoothEnabled(): Boolean {
        ensureBluetoothManager()
        return bluetoothAdapter?.isEnabled == true
    }
    
    override fun requestBluetoothPermission(): Boolean {
        // On Android, permissions are requested at runtime
        // This would need Activity context in real implementation
        return true
    }
    
    override fun startScan(options: Map<String, Any>?) {
        ensureBluetoothManager()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or not available")
            return
        }
        
        if (isScanning) return
        
        isScanning = true
        discoveredDevices.clear()
        
        val scanner = adapter.bluetoothLeScanner
        bluetoothLeScanner = scanner
        
        val serviceUUIDs = options?.get("serviceUUIDs") as? List<String>
        val scanFilters = if (serviceUUIDs != null && serviceUUIDs.isNotEmpty()) {
            serviceUUIDs.map { uuid ->
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(uuid))
                    .build()
            }
        } else {
            emptyList()
        }
        
        val scanMode = when (options?.get("scanMode") as? String) {
            "lowPower" -> ScanSettings.SCAN_MODE_LOW_POWER
            "balanced" -> ScanSettings.SCAN_MODE_BALANCED
            "lowLatency" -> ScanSettings.SCAN_MODE_LOW_LATENCY
            else -> ScanSettings.SCAN_MODE_BALANCED
        }
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceId = device.address
                discoveredDevices[deviceId] = device
                
                // Emit deviceFound event
                // In real implementation, this would use event emitter
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) {
                    onScanResult(ScanCallback.SCAN_RESULT_TYPE_BATCH, result)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                isScanning = false
            }
        }
        
        scanner.startScan(scanFilters, scanSettings, scanCallback)
    }
    
    override fun stopScan() {
        if (!isScanning) return
        
        bluetoothLeScanner?.stopScan(scanCallback)
        bluetoothLeScanner = null
        scanCallback = null
        isScanning = false
    }
    
    override fun connect(deviceId: String) {
        val device = discoveredDevices[deviceId]
        if (device == null) {
            Log.e(TAG, "Device not found: $deviceId")
            return
        }
        
        val gatt = device.connectGatt(null, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevices[deviceId] = gatt
                    gatt.discoverServices()
                    // Emit deviceConnected event
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedDevices.remove(deviceId)
                    deviceCharacteristics.remove(deviceId)
                    // Emit deviceDisconnected event
                }
            }
            
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val characteristics = mutableListOf<BluetoothGattCharacteristic>()
                    for (service in gatt.services) {
                        characteristics.addAll(service.characteristics)
                    }
                    deviceCharacteristics[deviceId] = characteristics
                    // Emit servicesDiscovered event
                }
            }
            
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val hexValue = characteristic.value?.joinToString("") { "%02x".format(it) } ?: ""
                    // Emit characteristicValueChanged event
                }
            }
            
            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Emit writeSuccess event
                } else {
                    // Emit writeError event
                }
            }
            
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val hexValue = characteristic.value?.joinToString("") { "%02x".format(it) } ?: ""
                // Emit characteristicValueChanged event
            }
            
            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Emit rssiUpdated event
                }
            }
        })
    }
    
    override fun disconnect(deviceId: String) {
        val gatt = connectedDevices[deviceId]
        gatt?.disconnect()
        gatt?.close()
        connectedDevices.remove(deviceId)
        deviceCharacteristics.remove(deviceId)
    }
    
    override fun discoverServices(deviceId: String): List<Map<String, Any>> {
        val gatt = connectedDevices[deviceId]
        if (gatt == null) {
            Log.e(TAG, "Device not connected: $deviceId")
            return emptyList()
        }
        
        gatt.discoverServices()
        
        // Services will be discovered via callback
        // Return empty list for now
        return emptyList()
    }
    
    override fun readCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ): Map<String, Any> {
        val gatt = connectedDevices[deviceId]
        if (gatt == null) {
            Log.e(TAG, "Device not connected: $deviceId")
            return emptyMap()
        }
        
        val characteristics = deviceCharacteristics[deviceId] ?: return emptyMap()
        val characteristic = characteristics.firstOrNull {
            it.service?.uuid.toString() == serviceUUID && it.uuid.toString() == characteristicUUID
        }
        
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found")
            return emptyMap()
        }
        
        gatt.readCharacteristic(characteristic)
        
        return mapOf(
            "value" to "",
            "serviceUUID" to serviceUUID,
            "characteristicUUID" to characteristicUUID
        )
    }
    
    override fun writeCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String,
        value: String,
        writeType: String?
    ) {
        val gatt = connectedDevices[deviceId]
        if (gatt == null) {
            Log.e(TAG, "Device not connected: $deviceId")
            return
        }
        
        val characteristics = deviceCharacteristics[deviceId] ?: return
        val characteristic = characteristics.firstOrNull {
            it.service?.uuid.toString() == serviceUUID && it.uuid.toString() == characteristicUUID
        }
        
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found")
            return
        }
        
        val data = hexStringToByteArray(value) ?: return
        characteristic.value = data
        
        val writeTypeValue = if (writeType == "writeWithoutResponse") {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        
        characteristic.writeType = writeTypeValue
        gatt.writeCharacteristic(characteristic)
    }
    
    override fun subscribeToCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ) {
        val gatt = connectedDevices[deviceId]
        if (gatt == null) {
            Log.e(TAG, "Device not connected: $deviceId")
            return
        }
        
        val characteristics = deviceCharacteristics[deviceId] ?: return
        val characteristic = characteristics.firstOrNull {
            it.service?.uuid.toString() == serviceUUID && it.uuid.toString() == characteristicUUID
        }
        
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found")
            return
        }
        
        gatt.setCharacteristicNotification(characteristic, true)
        
        // Enable notification descriptor
        val descriptor = characteristic.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )
        descriptor?.let {
            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(it)
        }
    }
    
    override fun unsubscribeFromCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ) {
        val gatt = connectedDevices[deviceId]
        if (gatt == null) {
            Log.e(TAG, "Device not connected: $deviceId")
            return
        }
        
        val characteristics = deviceCharacteristics[deviceId] ?: return
        val characteristic = characteristics.firstOrNull {
            it.service?.uuid.toString() == serviceUUID && it.uuid.toString() == characteristicUUID
        }
        
        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found")
            return
        }
        
        gatt.setCharacteristicNotification(characteristic, false)
    }
    
    override fun getConnectedDevices(): List<String> {
        return connectedDevices.keys.toList()
    }
    
    override fun readRSSI(deviceId: String): Double {
        val gatt = connectedDevices[deviceId]
        if (gatt == null) {
            Log.e(TAG, "Device not connected: $deviceId")
            return 0.0
        }
        
        gatt.readRemoteRssi()
        // RSSI will come via callback
        return 0.0
    }
    
    // MARK: - Event Management
    
    override fun addListener(eventName: String) {
        // Event listeners are handled by the event emitter
        // This is a no-op as Nitro modules handle events differently
    }
    
    override fun removeListeners(count: Double) {
        // Event listeners are handled by the event emitter
        // This is a no-op as Nitro modules handle events differently
    }
    
    // MARK: - Helper Methods
    
    private fun processAdvertisingData(
        dataMap: Map<String, Any>,
        dataBuilder: AdvertiseData.Builder
    ) {
        // Service UUIDs
        addServiceUUIDs(dataMap["incompleteServiceUUIDs16"] as? List<String>, dataBuilder)
        addServiceUUIDs(dataMap["completeServiceUUIDs16"] as? List<String>, dataBuilder)
        addServiceUUIDs(dataMap["incompleteServiceUUIDs32"] as? List<String>, dataBuilder)
        addServiceUUIDs(dataMap["completeServiceUUIDs32"] as? List<String>, dataBuilder)
        addServiceUUIDs(dataMap["incompleteServiceUUIDs128"] as? List<String>, dataBuilder)
        addServiceUUIDs(dataMap["completeServiceUUIDs128"] as? List<String>, dataBuilder)
        
        // Local Name
        if (dataMap.containsKey("shortenedLocalName") || dataMap.containsKey("completeLocalName")) {
            dataBuilder.setIncludeDeviceName(true)
        }
        
        // Tx Power Level
        if (dataMap.containsKey("txPowerLevel")) {
            dataBuilder.setIncludeTxPowerLevel(true)
        }
        
        // Service Solicitation
        addServiceUUIDs(dataMap["serviceSolicitationUUIDs16"] as? List<String>, dataBuilder)
        addServiceUUIDs(dataMap["serviceSolicitationUUIDs128"] as? List<String>, dataBuilder)
        addServiceUUIDs(dataMap["serviceSolicitationUUIDs32"] as? List<String>, dataBuilder)
        
        // Service Data
        addServiceData(dataMap["serviceData16"] as? List<Map<String, Any>>, dataBuilder)
        addServiceData(dataMap["serviceData32"] as? List<Map<String, Any>>, dataBuilder)
        addServiceData(dataMap["serviceData128"] as? List<Map<String, Any>>, dataBuilder)
        
        // Appearance
        if (dataMap.containsKey("appearance")) {
            val appearance = (dataMap["appearance"] as? Number)?.toInt() ?: 0
            val appearanceData = byteArrayOf(
                (appearance and 0xFF).toByte(),
                ((appearance shr 8) and 0xFF).toByte()
            )
            dataBuilder.addServiceData(
                ParcelUuid.fromString("00001800-0000-1000-8000-00805F9B34FB"),
                appearanceData
            )
        }
        
        // Manufacturer Data
        val manufacturerData = dataMap["manufacturerData"] as? String
        if (manufacturerData != null) {
            val data = hexStringToByteArray(manufacturerData)
            if (data != null) {
                dataBuilder.addManufacturerData(0x0000, data)
            }
        }
    }
    
    private fun addServiceUUIDs(uuids: List<String>?, dataBuilder: AdvertiseData.Builder) {
        if (uuids != null) {
            for (uuid in uuids) {
                dataBuilder.addServiceUuid(ParcelUuid.fromString(uuid))
            }
        }
    }
    
    private fun addServiceData(
        serviceDataArray: List<Map<String, Any>>?,
        dataBuilder: AdvertiseData.Builder
    ) {
        if (serviceDataArray != null) {
            for (serviceData in serviceDataArray) {
                val uuid = serviceData["uuid"] as? String
                val data = serviceData["data"] as? String
                if (uuid != null && data != null) {
                    val dataBytes = hexStringToByteArray(data)
                    if (dataBytes != null) {
                        dataBuilder.addServiceData(ParcelUuid.fromString(uuid), dataBytes)
                    }
                }
            }
        }
    }
    
    private fun hexStringToByteArray(hexString: String?): ByteArray? {
        if (hexString == null) return null
        
        val cleanHex = hexString.replace(" ", "")
        if (cleanHex.length % 2 != 0) return null
        
        val bytes = ByteArray(cleanHex.length / 2)
        for (i in bytes.indices) {
            val index = i * 2
            bytes[i] = cleanHex.substring(index, index + 2).toInt(16).toByte()
        }
        return bytes
    }
    
    private fun setServicesFromOptions(serviceUUIDs: List<String>) {
        ensureBluetoothManager()
        gattServerReady = false
        
        val manager = bluetoothManager ?: return
        gattServer = manager.openGattServer(null, object : BluetoothGattServerCallback() {})
        gattServer?.clearServices()
        
        for (uuid in serviceUUIDs) {
            val service = BluetoothGattService(
                UUID.fromString(uuid),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            gattServer?.addService(service)
        }
        
        gattServerReady = true
    }
}

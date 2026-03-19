package com.munimbluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import com.margelo.nitro.munimbluetooth.AdvertisingDataTypes
import com.margelo.nitro.munimbluetooth.AdvertisingOptions
import com.margelo.nitro.munimbluetooth.CharacteristicValue
import com.margelo.nitro.munimbluetooth.GATTCharacteristic
import com.margelo.nitro.munimbluetooth.GATTService
import com.margelo.nitro.munimbluetooth.HybridMunimBluetoothSpec
import com.margelo.nitro.munimbluetooth.ScanMode
import com.margelo.nitro.munimbluetooth.ScanOptions
import com.margelo.nitro.munimbluetooth.ServiceDataEntry
import com.margelo.nitro.munimbluetooth.WriteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class HybridMunimBluetooth : HybridMunimBluetoothSpec() {
    private val bluetoothScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var gattServer: BluetoothGattServer? = null
    private var gattServerReady = false
    private var advertiseJob: Job? = null
    private var currentAdvertisingData: AdvertisingDataTypes? = null
    private var currentServiceUUIDs: Array<String> = emptyArray()
    private var currentLocalName: String? = null
    private var currentManufacturerData: String? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()
    private val connectedDevices = mutableMapOf<String, BluetoothGatt>()
    private val pendingConnections = mutableMapOf<String, Promise<Unit>>()
    private val pendingServiceDiscoveries = mutableMapOf<String, Promise<Array<GATTService>>>()
    private val pendingReads = mutableMapOf<String, Promise<CharacteristicValue>>()
    private val pendingWrites = mutableMapOf<String, Promise<Unit>>()
    private val pendingRssiReads = mutableMapOf<String, Promise<Double>>()
    private val lastCharacteristicValues = mutableMapOf<String, CharacteristicValue>()
    private val lastRssiValues = mutableMapOf<String, Double>()
    private val eventEmitter = NitroEventEmitter(TAG)

    private fun getBluetoothManager(): BluetoothManager? {
        val context = NitroModules.applicationContext ?: return null
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private fun ensureBluetoothManager() {
        if (bluetoothManager == null) {
            bluetoothManager = getBluetoothManager()
            bluetoothAdapter = bluetoothManager?.adapter
        }
    }

    override fun startAdvertising(options: AdvertisingOptions) {
        ensureBluetoothManager()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or not available")
            return
        }
        if (options.serviceUUIDs.isEmpty()) {
            Log.e(TAG, "No service UUIDs provided for advertising")
            return
        }

        currentServiceUUIDs = options.serviceUUIDs
        currentLocalName = options.localName
        currentManufacturerData = options.manufacturerData
        currentAdvertisingData = normalizeAdvertisingData(
            options.advertisingData,
            options.localName,
            options.manufacturerData
        )

        if (!gattServerReady) {
            setServicesFromOptions(options.serviceUUIDs)
        }
        restartAdvertising(delayMs = 300L)
    }

    override fun updateAdvertisingData(advertisingData: AdvertisingDataTypes) {
        currentAdvertisingData = normalizeAdvertisingData(
            advertisingData,
            currentLocalName,
            currentManufacturerData
        )
        if (currentServiceUUIDs.isNotEmpty()) {
            restartAdvertising(delayMs = 100L)
        }
    }

    override fun getAdvertisingData(): Promise<AdvertisingDataTypes> {
        return Promise.resolved(currentAdvertisingData ?: emptyAdvertisingData())
    }

    override fun stopAdvertising() {
        advertiseJob?.cancel()
        advertiseCallback?.let { callback ->
            advertiser?.stopAdvertising(callback)
        }
        advertiseCallback = null
        advertiser = null
        currentAdvertisingData = null
        currentServiceUUIDs = emptyArray()
        currentLocalName = null
        currentManufacturerData = null
    }

    override fun setServices(services: Array<GATTService>) {
        ensureBluetoothManager()
        gattServerReady = false

        val manager = bluetoothManager ?: return
        val context = NitroModules.applicationContext ?: return

        gattServer?.close()
        gattServer = manager.openGattServer(context, buildGattServerCallback())
        gattServer?.clearServices()

        for (serviceData in services) {
            val service = BluetoothGattService(
                UUID.fromString(serviceData.uuid),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            for (characteristicData in serviceData.characteristics) {
                val characteristic = BluetoothGattCharacteristic(
                    UUID.fromString(characteristicData.uuid),
                    propertiesFromArray(characteristicData.properties),
                    BluetoothGattCharacteristic.PERMISSION_READ or
                        BluetoothGattCharacteristic.PERMISSION_WRITE
                )
                characteristicData.value?.let { value ->
                    characteristic.value = hexStringToByteArray(value) ?: value.toByteArray()
                }
                service.addCharacteristic(characteristic)
            }

            gattServer?.addService(service)
        }

        gattServerReady = true
    }

    override fun isBluetoothEnabled(): Promise<Boolean> {
        ensureBluetoothManager()
        return Promise.resolved(bluetoothAdapter?.isEnabled == true)
    }

    override fun requestBluetoothPermission(): Promise<Boolean> {
        return Promise.resolved(true)
    }

    override fun startScan(options: ScanOptions?) {
        ensureBluetoothManager()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or not available")
            return
        }
        if (isScanning) return

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "Bluetooth LE scanner is not available")
            return
        }

        isScanning = true
        discoveredDevices.clear()
        bluetoothLeScanner = scanner

        val scanFilters = options?.serviceUUIDs
            ?.takeIf { it.isNotEmpty() }
            ?.map { uuid ->
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(uuid))
                    .build()
            }
            ?: emptyList()

        val scanMode = when (options?.scanMode) {
            ScanMode.LOWPOWER -> ScanSettings.SCAN_MODE_LOW_POWER
            ScanMode.LOWLATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
            else -> ScanSettings.SCAN_MODE_BALANCED
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                discoveredDevices[device.address] = device
                eventEmitter.emit("deviceFound", buildScanPayload(result))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    val device = result.device
                    discoveredDevices[device.address] = device
                    eventEmitter.emit("deviceFound", buildScanPayload(result))
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
        scanCallback?.let { callback ->
            bluetoothLeScanner?.stopScan(callback)
        }
        bluetoothLeScanner = null
        scanCallback = null
        isScanning = false
    }

    override fun connect(deviceId: String): Promise<Unit> {
        ensureBluetoothManager()
        connectedDevices[deviceId]?.let { existingGatt ->
            if (existingGatt.services != null) {
                return Promise.resolved(Unit)
            }
        }

        val context = NitroModules.applicationContext
            ?: return Promise.rejected(IllegalStateException("React context unavailable"))
        val adapter = bluetoothAdapter
            ?: return Promise.rejected(IllegalStateException("Bluetooth adapter unavailable"))

        val device = discoveredDevices[deviceId] ?: run {
            try {
                adapter.getRemoteDevice(deviceId)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: return Promise.rejected(IllegalArgumentException("Device not found: $deviceId"))

        val promise = Promise<Unit>()
        pendingConnections[deviceId] = promise

        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, createGattCallback(deviceId), BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, createGattCallback(deviceId))
        }
        connectedDevices[deviceId] = gatt
        return promise
    }

    override fun disconnect(deviceId: String) {
        pendingConnections.remove(deviceId)
        pendingServiceDiscoveries.remove(deviceId)
        pendingRssiReads.remove(deviceId)

        val gatt = connectedDevices.remove(deviceId)
        gatt?.disconnect()
        gatt?.close()

        rejectPendingOperationsForDevice(deviceId, IllegalStateException("Disconnected from $deviceId"))
        eventEmitter.emit("deviceDisconnected", mapOf("deviceId" to deviceId))
    }

    override fun discoverServices(deviceId: String): Promise<Array<GATTService>> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        if (gatt.services.isNotEmpty()) {
            return Promise.resolved(buildGattServices(gatt))
        }

        val promise = Promise<Array<GATTService>>()
        pendingServiceDiscoveries[deviceId] = promise
        if (!gatt.discoverServices()) {
            pendingServiceDiscoveries.remove(deviceId)
            return Promise.rejected(IllegalStateException("Failed to start service discovery for $deviceId"))
        }
        return promise
    }

    override fun readCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ): Promise<CharacteristicValue> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))
        val characteristic = findCharacteristic(gatt, serviceUUID, characteristicUUID)
            ?: return Promise.rejected(
                IllegalArgumentException("Characteristic not found: $serviceUUID/$characteristicUUID")
            )

        val promise = Promise<CharacteristicValue>()
        val key = characteristicKey(deviceId, serviceUUID, characteristicUUID)
        pendingReads[key] = promise

        if (!gatt.readCharacteristic(characteristic)) {
            pendingReads.remove(key)
            return Promise.rejected(IllegalStateException("Failed to start characteristic read"))
        }
        return promise
    }

    override fun writeCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String,
        value: String,
        writeType: WriteType?
    ): Promise<Unit> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))
        val characteristic = findCharacteristic(gatt, serviceUUID, characteristicUUID)
            ?: return Promise.rejected(
                IllegalArgumentException("Characteristic not found: $serviceUUID/$characteristicUUID")
            )
        val data = hexStringToByteArray(value)
            ?: return Promise.rejected(IllegalArgumentException("Invalid hex string for characteristic write"))

        characteristic.value = data
        characteristic.writeType = when (writeType) {
            WriteType.WRITEWITHOUTRESPONSE -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        val promise = Promise<Unit>()
        val key = characteristicKey(deviceId, serviceUUID, characteristicUUID)
        pendingWrites[key] = promise

        if (!gatt.writeCharacteristic(characteristic)) {
            pendingWrites.remove(key)
            return Promise.rejected(IllegalStateException("Failed to start characteristic write"))
        }
        return promise
    }

    override fun subscribeToCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ) {
        val gatt = connectedDevices[deviceId] ?: return
        val characteristic = findCharacteristic(gatt, serviceUUID, characteristicUUID) ?: return
        gatt.setCharacteristicNotification(characteristic, true)

        characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)?.let { descriptor ->
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    override fun unsubscribeFromCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ) {
        val gatt = connectedDevices[deviceId] ?: return
        val characteristic = findCharacteristic(gatt, serviceUUID, characteristicUUID) ?: return
        gatt.setCharacteristicNotification(characteristic, false)

        characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)?.let { descriptor ->
            descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    override fun getConnectedDevices(): Promise<Array<String>> {
        return Promise.resolved(connectedDevices.keys.toTypedArray())
    }

    override fun readRSSI(deviceId: String): Promise<Double> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        lastRssiValues[deviceId]?.let { cachedRssi ->
            return Promise.resolved(cachedRssi)
        }

        val promise = Promise<Double>()
        pendingRssiReads[deviceId] = promise
        if (!gatt.readRemoteRssi()) {
            pendingRssiReads.remove(deviceId)
            return Promise.rejected(IllegalStateException("Failed to start RSSI read"))
        }
        return promise
    }

    override fun addListener(eventName: String) {
        // Nitro uses JS-side listener registration. No native bookkeeping required here.
    }

    override fun removeListeners(count: Double) {
        // Nitro uses JS-side listener registration. No native bookkeeping required here.
    }

    private fun restartAdvertising(delayMs: Long) {
        ensureBluetoothManager()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled or not available")
            return
        }

        advertiseJob?.cancel()
        advertiseCallback?.let { callback ->
            advertiser?.stopAdvertising(callback)
        }

        advertiseJob = bluetoothScope.launch {
            if (delayMs > 0) {
                delay(delayMs)
            }

            advertiser = adapter.bluetoothLeAdvertiser
            val activeAdvertiser = advertiser
            if (activeAdvertiser == null) {
                Log.e(TAG, "Bluetooth LE advertiser is not available")
                return@launch
            }

            val dataBuilder = AdvertiseData.Builder()
            currentAdvertisingData?.let { processAdvertisingData(it, dataBuilder) }
            currentServiceUUIDs.forEach { uuid ->
                dataBuilder.addServiceUuid(ParcelUuid.fromString(uuid))
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.i(TAG, "Advertising started successfully")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "Advertising failed: $errorCode")
                }
            }

            activeAdvertiser.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
        }
    }

    private fun buildGattServerCallback(): BluetoothGattServerCallback {
        return object : BluetoothGattServerCallback() {
            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    characteristic.value
                )
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
                characteristic.value = value ?: byteArrayOf()
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
            }
        }
    }

    private fun createGattCallback(deviceId: String): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                    pendingConnections.remove(deviceId)?.reject(
                        IllegalStateException("Failed to connect to $deviceId (status=$status)")
                    )
                    connectedDevices.remove(deviceId)?.close()
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedDevices[deviceId] = gatt
                        pendingConnections.remove(deviceId)?.resolve(Unit)
                        eventEmitter.emit("deviceConnected", mapOf("deviceId" to deviceId))
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        pendingConnections.remove(deviceId)?.reject(
                            IllegalStateException("Disconnected from $deviceId")
                        )
                        connectedDevices.remove(deviceId)?.close()
                        rejectPendingOperationsForDevice(
                            deviceId,
                            IllegalStateException("Disconnected from $deviceId")
                        )
                        eventEmitter.emit("deviceDisconnected", mapOf("deviceId" to deviceId))
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val services = buildGattServices(gatt)
                    pendingServiceDiscoveries.remove(deviceId)?.resolve(services)
                    eventEmitter.emit(
                        "servicesDiscovered",
                        mapOf("deviceId" to deviceId, "services" to services.map { service ->
                            mapOf(
                                "uuid" to service.uuid,
                                "characteristics" to service.characteristics.map { characteristic ->
                                    mapOf(
                                        "uuid" to characteristic.uuid,
                                        "properties" to characteristic.properties.toList(),
                                        "value" to characteristic.value
                                    )
                                }
                            )
                        })
                    )
                } else {
                    pendingServiceDiscoveries.remove(deviceId)?.reject(
                        IllegalStateException("Failed to discover services for $deviceId (status=$status)")
                    )
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                val key = characteristicKey(
                    deviceId,
                    characteristic.service.uuid.toString(),
                    characteristic.uuid.toString()
                )
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val value = buildCharacteristicValue(characteristic)
                    lastCharacteristicValues[key] = value
                    pendingReads.remove(key)?.resolve(value)
                    eventEmitter.emit(
                        "characteristicValueChanged",
                        mapOf(
                            "deviceId" to deviceId,
                            "serviceUUID" to value.serviceUUID,
                            "characteristicUUID" to value.characteristicUUID,
                            "value" to value.value
                        )
                    )
                } else {
                    pendingReads.remove(key)?.reject(
                        IllegalStateException("Failed to read characteristic $key (status=$status)")
                    )
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                val key = characteristicKey(
                    deviceId,
                    characteristic.service.uuid.toString(),
                    characteristic.uuid.toString()
                )
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingWrites.remove(key)?.resolve(Unit)
                } else {
                    pendingWrites.remove(key)?.reject(
                        IllegalStateException("Failed to write characteristic $key (status=$status)")
                    )
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val value = buildCharacteristicValue(characteristic)
                val key = characteristicKey(deviceId, value.serviceUUID, value.characteristicUUID)
                lastCharacteristicValues[key] = value
                eventEmitter.emit(
                    "characteristicValueChanged",
                    mapOf(
                        "deviceId" to deviceId,
                        "serviceUUID" to value.serviceUUID,
                        "characteristicUUID" to value.characteristicUUID,
                        "value" to value.value
                    )
                )
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val rssiValue = rssi.toDouble()
                    lastRssiValues[deviceId] = rssiValue
                    pendingRssiReads.remove(deviceId)?.resolve(rssiValue)
                    eventEmitter.emit(
                        "rssiUpdated",
                        mapOf("deviceId" to deviceId, "rssi" to rssiValue)
                    )
                } else {
                    pendingRssiReads.remove(deviceId)?.reject(
                        IllegalStateException("Failed to read RSSI for $deviceId (status=$status)")
                    )
                }
            }
        }
    }

    private fun rejectPendingOperationsForDevice(deviceId: String, error: Throwable) {
        pendingReads.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingReads.remove(key)?.reject(error) }
        pendingWrites.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingWrites.remove(key)?.reject(error) }
        pendingServiceDiscoveries.remove(deviceId)?.reject(error)
        pendingRssiReads.remove(deviceId)?.reject(error)
    }

    private fun buildGattServices(gatt: BluetoothGatt): Array<GATTService> {
        return gatt.services.map { service ->
            GATTService(
                uuid = service.uuid.toString(),
                characteristics = service.characteristics.map { characteristic ->
                    GATTCharacteristic(
                        uuid = characteristic.uuid.toString(),
                        properties = propertiesToArray(characteristic.properties),
                        value = characteristic.value?.toHexString()
                    )
                }.toTypedArray()
            )
        }.toTypedArray()
    }

    private fun buildCharacteristicValue(characteristic: BluetoothGattCharacteristic): CharacteristicValue {
        return CharacteristicValue(
            value = characteristic.value?.toHexString() ?: "",
            serviceUUID = characteristic.service.uuid.toString(),
            characteristicUUID = characteristic.uuid.toString()
        )
    }

    private fun findCharacteristic(
        gatt: BluetoothGatt,
        serviceUUID: String,
        characteristicUUID: String
    ): BluetoothGattCharacteristic? {
        val service = gatt.services.firstOrNull { it.uuid.toString().equals(serviceUUID, ignoreCase = true) }
            ?: return null
        return service.characteristics.firstOrNull {
            it.uuid.toString().equals(characteristicUUID, ignoreCase = true)
        }
    }

    private fun characteristicKey(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ): String {
        return "$deviceId|${serviceUUID.lowercase()}|${characteristicUUID.lowercase()}"
    }

    private fun buildScanPayload(result: ScanResult): Map<String, Any?> {
        val record = result.scanRecord
        val manufacturerData = extractManufacturerData(record)
        val serviceUUIDs = record?.serviceUuids?.map { it.uuid.toString() }
        val serviceData = extractServiceData(record)
        val txPower = record?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
        val advertisingData = mutableMapOf<String, Any?>()

        record?.deviceName?.let { advertisingData["completeLocalName"] = it }
        txPower?.let { advertisingData["txPowerLevel"] = it }
        manufacturerData?.let { advertisingData["manufacturerData"] = it }
        serviceUUIDs?.let { advertisingData["serviceUUIDs"] = it }
        serviceData?.takeIf { it.isNotEmpty() }?.let { entries ->
            advertisingData["serviceData"] = entries.map { mapOf("uuid" to it.uuid, "data" to it.data) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            advertisingData["isConnectable"] = result.isConnectable
        }
        advertisingData["rssi"] = result.rssi

        return mapOf(
            "id" to result.device.address,
            "name" to result.device.name,
            "localName" to record?.deviceName,
            "manufacturerData" to manufacturerData,
            "serviceUUIDs" to serviceUUIDs,
            "serviceData" to serviceData?.map { mapOf("uuid" to it.uuid, "data" to it.data) },
            "rssi" to result.rssi,
            "txPowerLevel" to txPower,
            "isConnectable" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else null,
            "advertisingData" to advertisingData
        )
    }

    private fun extractManufacturerData(record: ScanRecord?): String? {
        val data = record?.manufacturerSpecificData ?: return null
        if (data.size() == 0) return null
        return data.valueAt(0)?.toHexString()
    }

    private fun extractServiceData(record: ScanRecord?): List<ServiceDataEntry>? {
        val data = record?.serviceData ?: return null
        return data.entries.mapNotNull { entry ->
            val value = entry.value ?: return@mapNotNull null
            ServiceDataEntry(entry.key.uuid.toString(), value.toHexString())
        }.takeIf { it.isNotEmpty() }
    }

    private fun processAdvertisingData(
        data: AdvertisingDataTypes,
        dataBuilder: AdvertiseData.Builder
    ) {
        addServiceUUIDs(data.incompleteServiceUUIDs16, dataBuilder)
        addServiceUUIDs(data.completeServiceUUIDs16, dataBuilder)
        addServiceUUIDs(data.incompleteServiceUUIDs32, dataBuilder)
        addServiceUUIDs(data.completeServiceUUIDs32, dataBuilder)
        addServiceUUIDs(data.incompleteServiceUUIDs128, dataBuilder)
        addServiceUUIDs(data.completeServiceUUIDs128, dataBuilder)

        if (data.shortenedLocalName != null || data.completeLocalName != null) {
            dataBuilder.setIncludeDeviceName(true)
        }
        if (data.txPowerLevel != null) {
            dataBuilder.setIncludeTxPowerLevel(true)
        }

        addServiceUUIDs(data.serviceSolicitationUUIDs16, dataBuilder)
        addServiceUUIDs(data.serviceSolicitationUUIDs32, dataBuilder)
        addServiceUUIDs(data.serviceSolicitationUUIDs128, dataBuilder)
        addServiceData(data.serviceData16, dataBuilder)
        addServiceData(data.serviceData32, dataBuilder)
        addServiceData(data.serviceData128, dataBuilder)

        data.appearance?.toInt()?.let { appearance ->
            val appearanceData = byteArrayOf(
                (appearance and 0xFF).toByte(),
                ((appearance shr 8) and 0xFF).toByte()
            )
            dataBuilder.addServiceData(
                ParcelUuid.fromString("00001800-0000-1000-8000-00805F9B34FB"),
                appearanceData
            )
        }

        data.manufacturerData?.let { manufacturerData ->
            hexStringToByteArray(manufacturerData)?.let { bytes ->
                dataBuilder.addManufacturerData(0x0000, bytes)
            }
        }
    }

    private fun normalizeAdvertisingData(
        advertisingData: AdvertisingDataTypes?,
        localName: String?,
        manufacturerData: String?
    ): AdvertisingDataTypes {
        val base = advertisingData ?: emptyAdvertisingData()
        return base.copy(
            completeLocalName = base.completeLocalName ?: localName,
            manufacturerData = base.manufacturerData ?: manufacturerData
        )
    }

    private fun emptyAdvertisingData(): AdvertisingDataTypes {
        return AdvertisingDataTypes(
            flags = null,
            incompleteServiceUUIDs16 = null,
            completeServiceUUIDs16 = null,
            incompleteServiceUUIDs32 = null,
            completeServiceUUIDs32 = null,
            incompleteServiceUUIDs128 = null,
            completeServiceUUIDs128 = null,
            shortenedLocalName = null,
            completeLocalName = null,
            txPowerLevel = null,
            serviceSolicitationUUIDs16 = null,
            serviceSolicitationUUIDs128 = null,
            serviceData16 = null,
            serviceData32 = null,
            serviceData128 = null,
            appearance = null,
            serviceSolicitationUUIDs32 = null,
            manufacturerData = null
        )
    }

    private fun propertiesFromArray(properties: Array<String>): Int {
        var result = 0
        properties.forEach { property ->
            when (property) {
                "read" -> result = result or BluetoothGattCharacteristic.PROPERTY_READ
                "write" -> result = result or BluetoothGattCharacteristic.PROPERTY_WRITE
                "notify" -> result = result or BluetoothGattCharacteristic.PROPERTY_NOTIFY
                "indicate" -> result = result or BluetoothGattCharacteristic.PROPERTY_INDICATE
                "writeWithoutResponse" -> {
                    result = result or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                }
            }
        }
        return result
    }

    private fun propertiesToArray(properties: Int): Array<String> {
        val result = mutableListOf<String>()
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) result += "read"
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) result += "write"
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) result += "notify"
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) result += "indicate"
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            result += "writeWithoutResponse"
        }
        return result.toTypedArray()
    }

    private fun addServiceUUIDs(uuids: Array<String>?, dataBuilder: AdvertiseData.Builder) {
        uuids?.forEach { uuid ->
            dataBuilder.addServiceUuid(ParcelUuid.fromString(uuid))
        }
    }

    private fun addServiceData(
        serviceDataEntries: Array<ServiceDataEntry>?,
        dataBuilder: AdvertiseData.Builder
    ) {
        serviceDataEntries?.forEach { entry ->
            hexStringToByteArray(entry.data)?.let { dataBytes ->
                dataBuilder.addServiceData(ParcelUuid.fromString(entry.uuid), dataBytes)
            }
        }
    }

    private fun hexStringToByteArray(hexString: String?): ByteArray? {
        if (hexString == null) return null

        val cleanHex = hexString.replace(" ", "")
        if (cleanHex.length % 2 != 0) return null

        return try {
            ByteArray(cleanHex.length / 2).also { bytes ->
                bytes.indices.forEach { index ->
                    val offset = index * 2
                    bytes[index] = cleanHex.substring(offset, offset + 2).toInt(16).toByte()
                }
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun setServicesFromOptions(serviceUUIDs: Array<String>) {
        ensureBluetoothManager()
        gattServerReady = false

        val manager = bluetoothManager ?: return
        val context = NitroModules.applicationContext ?: return

        gattServer?.close()
        gattServer = manager.openGattServer(context, object : BluetoothGattServerCallback() {})
        gattServer?.clearServices()

        serviceUUIDs.forEach { uuid ->
            val service = BluetoothGattService(
                UUID.fromString(uuid),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            gattServer?.addService(service)
        }

        gattServerReady = true
    }

    companion object {
        private const val TAG = "HybridMunimBluetooth"
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

private class NitroEventEmitter(private val tag: String) {
    fun emit(eventName: String, payload: Map<String, Any?>) {
        val context = NitroModules.applicationContext
        if (context == null) {
            Log.w(tag, "Unable to emit $eventName: React context unavailable")
            return
        }
        
        val writable = Arguments.createMap()
        payload.forEach { (key, value) ->
            writeValue(writable, key, value)
        }
        
        context
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, writable)
    }
    
    private fun writeValue(map: WritableMap, key: String, value: Any?) {
        when (value) {
            null -> map.putNull(key)
            is String -> map.putString(key, value)
            is Boolean -> map.putBoolean(key, value)
            is Int -> map.putInt(key, value)
            is Double -> map.putDouble(key, value)
            is Float -> map.putDouble(key, value.toDouble())
            is Long -> map.putDouble(key, value.toDouble())
            is Map<*, *> -> map.putMap(key, convertMap(value))
            is List<*> -> map.putArray(key, convertArray(value))
            else -> map.putString(key, value.toString())
        }
    }
    
    private fun convertMap(map: Map<*, *>): WritableMap {
        val writable = Arguments.createMap()
        map.forEach { (key, value) ->
            if (key is String) {
                writeValue(writable, key, value)
            }
        }
        return writable
    }
    
    private fun convertArray(list: List<*>): WritableArray {
        val writable = Arguments.createArray()
        list.forEach { value ->
            when (value) {
                null -> writable.pushNull()
                is String -> writable.pushString(value)
                is Boolean -> writable.pushBoolean(value)
                is Int -> writable.pushInt(value)
                is Double -> writable.pushDouble(value)
                is Float -> writable.pushDouble(value.toDouble())
                is Long -> writable.pushDouble(value.toDouble())
                is Map<*, *> -> writable.pushMap(convertMap(value))
                is List<*> -> writable.pushArray(convertArray(value))
                else -> writable.pushString(value.toString())
            }
        }
        return writable
    }
}

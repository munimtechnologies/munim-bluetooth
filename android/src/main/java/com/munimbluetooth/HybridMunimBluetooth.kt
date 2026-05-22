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
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.Keep
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import com.margelo.nitro.munimbluetooth.AdvertisingDataTypes
import com.margelo.nitro.munimbluetooth.AdvertisingOptions
import com.margelo.nitro.munimbluetooth.BackgroundSessionOptions
import com.margelo.nitro.munimbluetooth.BluetoothCapabilities
import com.margelo.nitro.munimbluetooth.BluetoothPhy
import com.margelo.nitro.munimbluetooth.BluetoothPhyOption
import com.margelo.nitro.munimbluetooth.BondState
import com.margelo.nitro.munimbluetooth.CharacteristicValue
import com.margelo.nitro.munimbluetooth.DescriptorValue
import com.margelo.nitro.munimbluetooth.ExtendedAdvertisingOptions
import com.margelo.nitro.munimbluetooth.GATTCharacteristic
import com.margelo.nitro.munimbluetooth.GATTDescriptor
import com.margelo.nitro.munimbluetooth.GATTService
import com.margelo.nitro.munimbluetooth.HybridMunimBluetoothSpec
import com.margelo.nitro.munimbluetooth.L2CAPChannel
import com.margelo.nitro.munimbluetooth.MultipeerPeer
import com.margelo.nitro.munimbluetooth.MultipeerSessionOptions
import com.margelo.nitro.munimbluetooth.PhyStatus
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

@Keep
@DoNotStrip
class HybridMunimBluetooth : HybridMunimBluetoothSpec() {
    private val bluetoothScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private val extendedAdvertisingSets = mutableMapOf<String, AdvertisingSet>()
    private val extendedAdvertisingCallbacks = mutableMapOf<String, AdvertisingSetCallback>()
    private var gattServer: BluetoothGattServer? = null
    private var gattServerReady = false
    private var advertiseJob: Job? = null
    private var currentAdvertisingData: AdvertisingDataTypes? = null
    private var currentServiceUUIDs: Array<String> = emptyArray()
    private var currentLocalName: String? = null
    private var currentManufacturerData: String? = null
    private var previousAdapterName: String? = null
    private var configuredServices: Array<GATTService> = emptyArray()
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
    private val pendingDescriptorReads = mutableMapOf<String, Promise<DescriptorValue>>()
    private val pendingDescriptorWrites = mutableMapOf<String, Promise<Unit>>()
    private val pendingMtuRequests = mutableMapOf<String, Promise<Double>>()
    private val pendingPhyReads = mutableMapOf<String, Promise<PhyStatus>>()
    private val pendingRssiReads = mutableMapOf<String, Promise<Double>>()
    private val pendingConnectionTimeouts = mutableMapOf<String, Job>()
    private val pendingConnectionAttempts = mutableMapOf<String, Int>()
    private val pendingOperationTimeouts = mutableMapOf<String, Job>()
    private val pendingConnectionGatts = mutableMapOf<String, BluetoothGatt>()
    private val lastCharacteristicValues = mutableMapOf<String, CharacteristicValue>()
    private val lastRssiValues = mutableMapOf<String, Double>()
    private val subscribedDevices = mutableMapOf<UUID, MutableSet<BluetoothDevice>>()
    private var classicScanReceiver: BroadcastReceiver? = null
    private val classicDevices = mutableMapOf<String, BluetoothDevice>()
    private val classicSockets = mutableMapOf<String, BluetoothSocket>()
    private val classicReadJobs = mutableMapOf<String, Job>()
    private val classicServerSockets = mutableMapOf<String, BluetoothServerSocket>()
    private val classicServerJobs = mutableMapOf<String, Job>()
    private val l2capServerSockets = mutableMapOf<Int, BluetoothServerSocket>()
    private val l2capAcceptJobs = mutableMapOf<Int, Job>()
    private val l2capSockets = mutableMapOf<String, BluetoothSocket>()
    private val l2capReadJobs = mutableMapOf<String, Job>()
    private val eventEmitter = NitroEventEmitter(TAG)
    private var nextPermissionRequestCode = BLUETOOTH_PERMISSION_REQUEST_CODE

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

    private fun hasRequiredBluetoothPermissions(): Boolean {
        val context = NitroModules.applicationContext ?: return false
        return BluetoothPermissionUtils.hasRequiredPermissions(context)
    }

    private fun ensureBluetoothPermissions(operationName: String): Boolean {
        val context = NitroModules.applicationContext
        if (context == null) {
            Log.w(TAG, "Unable to $operationName: React context unavailable")
            return false
        }

        val missingPermissions = BluetoothPermissionUtils.missingPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            Log.w(
                TAG,
                "Unable to $operationName: missing Bluetooth permissions (${missingPermissions.joinToString()})"
            )
            return false
        }

        return true
    }

    override fun startAdvertising(options: AdvertisingOptions) {
        if (!ensureBluetoothPermissions("start advertising")) {
            return
        }

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

        if (!currentLocalName.isNullOrBlank() && previousAdapterName == null) {
            previousAdapterName = try {
                adapter.name
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to read Bluetooth adapter name", error)
                null
            }
        }
        if (!currentLocalName.isNullOrBlank()) {
            try {
                adapter.name = currentLocalName
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to apply custom localName to Bluetooth adapter", error)
            }
        }

        if (!gattServerReady) {
            if (configuredServices.isNotEmpty()) {
                setServices(configuredServices)
            } else {
                setServicesFromOptions(options.serviceUUIDs)
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activeAdvertiser = advertiser ?: bluetoothAdapter?.bluetoothLeAdvertiser
            extendedAdvertisingCallbacks.values.forEach { callback ->
                activeAdvertiser?.stopAdvertisingSet(callback)
            }
        }
        extendedAdvertisingCallbacks.clear()
        extendedAdvertisingSets.clear()
        advertiseCallback = null
        advertiser = null
        gattServer?.clearServices()
        gattServer?.close()
        gattServer = null
        gattServerReady = false
        subscribedDevices.clear()
        currentAdvertisingData = null
        currentServiceUUIDs = emptyArray()
        currentLocalName = null
        currentManufacturerData = null
        restoreAdapterName()
    }

    override fun setServices(services: Array<GATTService>) {
        if (!ensureBluetoothPermissions("set GATT services")) {
            return
        }

        configuredServices = services
        ensureBluetoothManager()
        gattServerReady = false

        val manager = bluetoothManager ?: return
        val context = NitroModules.applicationContext ?: return

        gattServer?.close()
        gattServer = manager.openGattServer(context, buildGattServerCallback())
        gattServer?.clearServices()

        val nativeServices = linkedMapOf<String, BluetoothGattService>()

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
                    setCharacteristicValue(characteristic, hexStringToByteArray(value) ?: value.toByteArray())
                }
                characteristicData.descriptors?.forEach { descriptorData ->
                    val descriptor = BluetoothGattDescriptor(
                        UUID.fromString(descriptorData.uuid),
                        descriptorPermissionsFromArray(descriptorData.permissions)
                    )
                    descriptorData.value?.let { value ->
                        setDescriptorValue(descriptor, hexStringToByteArray(value) ?: value.toByteArray())
                    }
                    characteristic.addDescriptor(descriptor)
                }

                val hasClientConfigDescriptor = characteristic.descriptors.any {
                    it.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID
                }
                if (supportsNotifyOrIndicate(characteristic) && !hasClientConfigDescriptor) {
                    characteristic.addDescriptor(
                        BluetoothGattDescriptor(
                            CLIENT_CHARACTERISTIC_CONFIG_UUID,
                            BluetoothGattDescriptor.PERMISSION_READ or
                                BluetoothGattDescriptor.PERMISSION_WRITE
                        )
                    )
                }
                service.addCharacteristic(characteristic)
            }

            nativeServices[serviceData.uuid.lowercase()] = service
        }

        for (serviceData in services) {
            val service = nativeServices[serviceData.uuid.lowercase()] ?: continue
            serviceData.includedServices?.forEach { includedServiceUuid ->
                nativeServices[includedServiceUuid.lowercase()]?.let { includedService ->
                    service.addService(includedService)
                }
            }
        }

        nativeServices.values.forEach { service ->
            gattServer?.addService(service)
        }

        gattServerReady = true
    }

    override fun updateCharacteristicValue(
        serviceUUID: String,
        characteristicUUID: String,
        value: String,
        notify: Boolean?
    ): Promise<Unit> {
        val payload = hexStringToByteArray(value)
            ?: return Promise.rejected(IllegalArgumentException("Value must be a hex string"))
        val characteristic = findLocalCharacteristic(serviceUUID, characteristicUUID)
            ?: return Promise.rejected(IllegalArgumentException("Local characteristic $characteristicUUID was not found"))

        setCharacteristicValue(characteristic, payload)
        if (notify == true) {
            notifySubscribedDevices(characteristic)
        }
        return Promise.resolved(Unit)
    }

    override fun isBluetoothEnabled(): Promise<Boolean> {
        if (!hasRequiredBluetoothPermissions()) {
            return Promise.resolved(false)
        }

        ensureBluetoothManager()
        return Promise.resolved(bluetoothAdapter?.isEnabled == true)
    }

    override fun requestBluetoothPermission(): Promise<Boolean> {
        val context = NitroModules.applicationContext ?: run {
            Log.w(TAG, "Unable to request Bluetooth permissions: React context unavailable")
            return Promise.resolved(false)
        }

        val missingPermissions = BluetoothPermissionUtils.missingPermissions(context)
        if (missingPermissions.isEmpty()) {
            return Promise.resolved(true)
        }

        val activity = context.currentActivity as? PermissionAwareActivity
        if (activity == null) {
            Log.w(TAG, "Unable to request Bluetooth permissions: current activity unavailable")
            return Promise.resolved(false)
        }

        val requestCode = nextPermissionRequestCode++
        val promise = Promise<Boolean>()

        try {
            activity.requestPermissions(
                missingPermissions,
                requestCode,
                PermissionListener { callbackRequestCode, _, grantResults ->
                    if (callbackRequestCode != requestCode) {
                        return@PermissionListener false
                    }

                    val isGranted =
                        grantResults.isNotEmpty() &&
                            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                    promise.resolve(isGranted)
                    true
                }
            )
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Unable to request Bluetooth permissions", error)
            promise.resolve(false)
        }

        return promise
    }

    override fun getCapabilities(): Promise<BluetoothCapabilities> {
        ensureBluetoothManager()
        val adapter = bluetoothAdapter
        return Promise.resolved(
            BluetoothCapabilities(
                platform = "android",
                supportsBleCentral = true,
                supportsBlePeripheral = adapter?.bluetoothLeAdvertiser != null,
                supportsDescriptors = true,
                supportsIncludedServices = true,
                supportsMtu = true,
                supportsPhy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                supportsBonding = true,
                supportsExtendedAdvertising = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    adapter?.isLeExtendedAdvertisingSupported == true,
                supportsL2cap = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                supportsClassicBluetooth = adapter != null,
                supportsBackgroundBle = true,
                supportsMultipeerConnectivity = false
            )
        )
    }

    override fun startScan(options: ScanOptions?) {
        if (!ensureBluetoothPermissions("start scanning")) {
            return
        }

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
                emitDeviceFound(buildScanPayload(result))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    val device = result.device
                    discoveredDevices[device.address] = device
                    emitDeviceFound(buildScanPayload(result))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                isScanning = false
                eventEmitter.emit(
                    "scanFailed",
                    mapOf(
                        "errorCode" to errorCode,
                        "message" to scanFailureMessage(errorCode)
                    )
                )
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
        if (!ensureBluetoothPermissions("connect to BLE device")) {
            return Promise.rejected(IllegalStateException("Bluetooth permissions not granted"))
        }

        ensureBluetoothManager()
        connectedDevices[deviceId]?.let { existingGatt ->
            if (existingGatt.services != null) {
                return Promise.resolved(Unit)
            }
        }

        val adapter = bluetoothAdapter
            ?: return Promise.rejected(IllegalStateException("Bluetooth adapter unavailable"))
        NitroModules.applicationContext
            ?: return Promise.rejected(IllegalStateException("React context unavailable"))

        val device = discoveredDevices[deviceId] ?: run {
            try {
                adapter.getRemoteDevice(deviceId)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: return Promise.rejected(IllegalArgumentException("Device not found: $deviceId"))

        val promise = Promise<Unit>()
        pendingConnections[deviceId] = promise
        pendingConnectionAttempts[deviceId] = 0
        scheduleConnectionTimeout(deviceId)
        startGattConnection(deviceId, device)
        return promise
    }

    override fun disconnect(deviceId: String) {
        pendingConnectionTimeouts.remove(deviceId)?.cancel()
        pendingConnectionAttempts.remove(deviceId)
        pendingConnectionGatts.remove(deviceId)?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        pendingConnections.remove(deviceId)?.reject(
            IllegalStateException("Disconnected from $deviceId")
        )

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
        schedulePendingOperationTimeout("services|$deviceId", "Service discovery for $deviceId") {
            pendingServiceDiscoveries.remove(deviceId)
        }
        if (!gatt.discoverServices()) {
            cancelPendingOperationTimeout("services|$deviceId")
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
        schedulePendingOperationTimeout("read|$key", "Characteristic read $key") {
            pendingReads.remove(key)
        }

        if (!gatt.readCharacteristic(characteristic)) {
            cancelPendingOperationTimeout("read|$key")
            pendingReads.remove(key)
            return Promise.rejected(IllegalStateException("Failed to start characteristic read"))
        }
        return promise
    }

    override fun readDescriptor(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String,
        descriptorUUID: String
    ): Promise<DescriptorValue> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))
        val descriptor = findDescriptor(gatt, serviceUUID, characteristicUUID, descriptorUUID)
            ?: return Promise.rejected(IllegalStateException("Descriptor not found: $descriptorUUID"))

        val promise = Promise<DescriptorValue>()
        val key = descriptorKey(deviceId, serviceUUID, characteristicUUID, descriptorUUID)
        pendingDescriptorReads[key] = promise
        schedulePendingOperationTimeout("descriptorRead|$key", "Descriptor read $key") {
            pendingDescriptorReads.remove(key)
        }
        if (!gatt.readDescriptor(descriptor)) {
            cancelPendingOperationTimeout("descriptorRead|$key")
            pendingDescriptorReads.remove(key)
            return Promise.rejected(IllegalStateException("Failed to start descriptor read"))
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

        val resolvedWriteType = when (writeType) {
            WriteType.WRITEWITHOUTRESPONSE -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }

        val promise = Promise<Unit>()
        val key = characteristicKey(deviceId, serviceUUID, characteristicUUID)
        if (resolvedWriteType != BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            pendingWrites[key] = promise
            schedulePendingOperationTimeout("write|$key", "Characteristic write $key") {
                pendingWrites.remove(key)
            }
        }

        if (!writeGattCharacteristic(gatt, characteristic, data, resolvedWriteType)) {
            cancelPendingOperationTimeout("write|$key")
            pendingWrites.remove(key)
            return Promise.rejected(IllegalStateException("Failed to start characteristic write"))
        }
        if (resolvedWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            promise.resolve(Unit)
        }
        return promise
    }

    override fun writeDescriptor(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String,
        descriptorUUID: String,
        value: String
    ): Promise<Unit> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))
        val descriptor = findDescriptor(gatt, serviceUUID, characteristicUUID, descriptorUUID)
            ?: return Promise.rejected(IllegalStateException("Descriptor not found: $descriptorUUID"))
        val data = hexStringToByteArray(value)
            ?: return Promise.rejected(IllegalArgumentException("Invalid hex string for descriptor write"))

        val promise = Promise<Unit>()
        val key = descriptorKey(deviceId, serviceUUID, characteristicUUID, descriptorUUID)
        pendingDescriptorWrites[key] = promise
        schedulePendingOperationTimeout("descriptorWrite|$key", "Descriptor write $key") {
            pendingDescriptorWrites.remove(key)
        }
        if (!writeGattDescriptor(gatt, descriptor, data)) {
            cancelPendingOperationTimeout("descriptorWrite|$key")
            pendingDescriptorWrites.remove(key)
            return Promise.rejected(IllegalStateException("Failed to start descriptor write"))
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
            val subscriptionValue = when {
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> return
            }
            writeGattDescriptor(gatt, descriptor, subscriptionValue)
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
            writeGattDescriptor(gatt, descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
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
        schedulePendingOperationTimeout("rssi|$deviceId", "RSSI read for $deviceId") {
            pendingRssiReads.remove(deviceId)
        }
        if (!gatt.readRemoteRssi()) {
            cancelPendingOperationTimeout("rssi|$deviceId")
            pendingRssiReads.remove(deviceId)
            return Promise.rejected(IllegalStateException("Failed to start RSSI read"))
        }
        return promise
    }

    override fun requestMTU(deviceId: String, mtu: Double): Promise<Double> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        val requestedMtu = mtu.toInt().coerceIn(23, 517)
        val promise = Promise<Double>()
        pendingMtuRequests[deviceId] = promise
        schedulePendingOperationTimeout("mtu|$deviceId", "MTU request for $deviceId") {
            pendingMtuRequests.remove(deviceId)
        }
        if (!gatt.requestMtu(requestedMtu)) {
            cancelPendingOperationTimeout("mtu|$deviceId")
            pendingMtuRequests.remove(deviceId)
            return Promise.rejected(IllegalStateException("Failed to start MTU request"))
        }
        return promise
    }

    override fun setPreferredPhy(
        deviceId: String,
        txPhy: BluetoothPhy,
        rxPhy: BluetoothPhy,
        phyOption: BluetoothPhyOption?
    ): Promise<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return unsupportedPromise("BLE PHY selection requires Android 8.0 or newer")
        }

        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        gatt.setPreferredPhy(
            phyToMask(txPhy),
            phyToMask(rxPhy),
            phyOptionToConstant(phyOption ?: BluetoothPhyOption.NONE)
        )
        return Promise.resolved(Unit)
    }

    override fun readPhy(deviceId: String): Promise<PhyStatus> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return unsupportedPromise("BLE PHY reads require Android 8.0 or newer")
        }

        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        val promise = Promise<PhyStatus>()
        pendingPhyReads[deviceId] = promise
        schedulePendingOperationTimeout("phy|$deviceId", "PHY read for $deviceId") {
            pendingPhyReads.remove(deviceId)
        }
        gatt.readPhy()
        return promise
    }

    override fun getBondState(deviceId: String): Promise<BondState> {
        val device = resolveBluetoothDevice(deviceId)
            ?: return Promise.rejected(IllegalArgumentException("Device not found: $deviceId"))
        return Promise.resolved(bondStateFor(device))
    }

    override fun createBond(deviceId: String): Promise<BondState> {
        val device = resolveBluetoothDevice(deviceId)
            ?: return Promise.rejected(IllegalArgumentException("Device not found: $deviceId"))

        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            return Promise.resolved(BondState.BONDED)
        }

        return if (device.createBond()) {
            Promise.resolved(bondStateFor(device))
        } else {
            Promise.rejected(IllegalStateException("Failed to start bond creation for $deviceId"))
        }
    }

    override fun removeBond(deviceId: String): Promise<BondState> {
        val device = resolveBluetoothDevice(deviceId)
            ?: return Promise.rejected(IllegalArgumentException("Device not found: $deviceId"))

        return try {
            val method = device.javaClass.getMethod("removeBond")
            val removed = method.invoke(device) as? Boolean ?: false
            if (removed) {
                Promise.resolved(bondStateFor(device))
            } else {
                Promise.rejected(IllegalStateException("Failed to remove bond for $deviceId"))
            }
        } catch (error: ReflectiveOperationException) {
            Promise.rejected(UnsupportedOperationException("Removing bonds is unavailable on this Android build", error))
        }
    }

    override fun startExtendedAdvertising(options: ExtendedAdvertisingOptions): Promise<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return unsupportedPromise("BLE extended advertising requires Android 8.0 or newer")
        }
        if (!ensureBluetoothPermissions("start extended advertising")) {
            return Promise.rejected(IllegalStateException("Bluetooth permissions not granted"))
        }

        ensureBluetoothManager()
        val adapter = bluetoothAdapter
            ?: return Promise.rejected(IllegalStateException("Bluetooth adapter unavailable"))
        if (!adapter.isLeExtendedAdvertisingSupported) {
            return unsupportedPromise("BLE extended advertising is not supported by this device")
        }
        val advertiser = adapter.bluetoothLeAdvertiser
            ?: return Promise.rejected(IllegalStateException("Bluetooth LE advertiser is unavailable"))

        val id = UUID.randomUUID().toString()
        val promise = Promise<String>()
        val dataBuilder = AdvertiseData.Builder()
        options.serviceUUIDs?.forEach { uuid ->
            dataBuilder.addServiceUuid(ParcelUuid.fromString(uuid))
        }
        normalizeAdvertisingData(
            options.advertisingData,
            options.localName,
            options.manufacturerData
        ).let { data ->
            processAdvertisingData(data, dataBuilder, includeServiceUuids = true)
        }

        val scanResponseBuilder = AdvertiseData.Builder()
        options.localName?.let { scanResponseBuilder.setIncludeDeviceName(true) }

        val parameters = AdvertisingSetParameters.Builder()
            .setLegacyMode(options.legacyMode ?: false)
            .setConnectable(options.connectable ?: true)
            .setScannable(options.scannable ?: false)
            .setAnonymous(options.anonymous ?: false)
            .setIncludeTxPower(options.includeTxPower ?: false)
            .setPrimaryPhy(phyToAdvertisingPhy(options.primaryPhy ?: BluetoothPhy.LE1M))
            .setSecondaryPhy(phyToAdvertisingPhy(options.secondaryPhy ?: BluetoothPhy.LE1M))
            .setInterval(options.interval?.toInt() ?: AdvertisingSetParameters.INTERVAL_MEDIUM)
            .setTxPowerLevel(options.txPowerLevel?.toInt() ?: AdvertisingSetParameters.TX_POWER_HIGH)
            .build()

        val callback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS && advertisingSet != null) {
                    extendedAdvertisingSets[id] = advertisingSet
                    promise.resolve(id)
                    eventEmitter.emit("advertisingStarted", mapOf("advertisingId" to id))
                } else {
                    extendedAdvertisingCallbacks.remove(id)
                    promise.reject(IllegalStateException("Extended advertising failed (status=$status)"))
                    eventEmitter.emit(
                        "advertisingStartFailed",
                        mapOf(
                            "advertisingId" to id,
                            "errorCode" to status,
                            "message" to advertiseFailureMessage(status)
                        )
                    )
                }
            }
        }

        extendedAdvertisingCallbacks[id] = callback
        advertiser.startAdvertisingSet(
            parameters,
            dataBuilder.build(),
            scanResponseBuilder.build(),
            null,
            null,
            callback
        )
        return promise
    }

    override fun stopExtendedAdvertising(advertisingId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        ensureBluetoothManager()
        extendedAdvertisingSets.remove(advertisingId)
        val callback = extendedAdvertisingCallbacks.remove(advertisingId) ?: return
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertisingSet(callback)
    }

    override fun publishL2CAPChannel(encryptionRequired: Boolean?): Promise<L2CAPChannel> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return unsupportedPromise("BLE L2CAP channel streams require Android 10 or newer")
        }
        if (!hasRequiredBluetoothPermissions()) {
            return Promise.rejected(SecurityException("Missing Bluetooth permissions"))
        }

        ensureBluetoothManager()
        val adapter = bluetoothAdapter
            ?: return Promise.rejected(IllegalStateException("Bluetooth adapter is unavailable"))
        val promise = Promise<L2CAPChannel>()

        bluetoothScope.launch(Dispatchers.IO) {
            try {
                val serverSocket = if (encryptionRequired == true) {
                    adapter.listenUsingL2capChannel()
                } else {
                    adapter.listenUsingInsecureL2capChannel()
                }
                val psm = serverSocket.psm
                l2capServerSockets[psm] = serverSocket
                l2capAcceptJobs[psm]?.cancel()
                l2capAcceptJobs[psm] = bluetoothScope.launch(Dispatchers.IO) {
                    acceptL2CAPConnections(psm, serverSocket)
                }

                val channel = L2CAPChannel("server:$psm", psm.toDouble(), null)
                eventEmitter.emit(
                    "l2capChannelPublished",
                    mapOf("channelId" to channel.id, "psm" to channel.psm)
                )
                promise.resolve(channel)
            } catch (error: SecurityException) {
                promise.reject(error)
            } catch (error: IOException) {
                promise.reject(error)
            }
        }

        return promise
    }

    override fun unpublishL2CAPChannel(psm: Double) {
        val psmValue = psm.toInt()
        l2capAcceptJobs.remove(psmValue)?.cancel()
        try {
            l2capServerSockets.remove(psmValue)?.close()
        } catch (error: IOException) {
            Log.w(TAG, "Unable to close L2CAP server socket for PSM $psmValue", error)
        }
    }

    override fun openL2CAPChannel(deviceId: String, psm: Double): Promise<L2CAPChannel> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return unsupportedPromise("BLE L2CAP channel streams require Android 10 or newer")
        }
        if (!hasRequiredBluetoothPermissions()) {
            return Promise.rejected(SecurityException("Missing Bluetooth permissions"))
        }

        val device = resolveBluetoothDevice(deviceId)
            ?: return Promise.rejected(IllegalArgumentException("Device $deviceId was not found"))
        val promise = Promise<L2CAPChannel>()

        bluetoothScope.launch(Dispatchers.IO) {
            try {
                val socket = device.createL2capChannel(psm.toInt())
                socket.connect()
                val channel = registerL2CAPSocket(socket, psm.toInt(), device.address)
                promise.resolve(channel)
            } catch (error: SecurityException) {
                promise.reject(error)
            } catch (error: IOException) {
                promise.reject(error)
            }
        }

        return promise
    }

    override fun closeL2CAPChannel(channelId: String) {
        closeL2CAPChannelInternal(channelId, true)
    }

    override fun sendL2CAPData(channelId: String, value: String): Promise<Unit> {
        val socket = l2capSockets[channelId]
            ?: return Promise.rejected(IllegalArgumentException("L2CAP channel $channelId is not open"))
        val payload = hexStringToByteArray(value)
            ?: return Promise.rejected(IllegalArgumentException("Value must be a hex string"))
        val promise = Promise<Unit>()

        bluetoothScope.launch(Dispatchers.IO) {
            try {
                socket.outputStream.write(payload)
                socket.outputStream.flush()
                promise.resolve(Unit)
            } catch (error: IOException) {
                promise.reject(error)
                closeL2CAPChannelInternal(channelId, true)
            }
        }

        return promise
    }

    override fun startClassicScan() {
        if (!ensureBluetoothPermissions("start Classic Bluetooth discovery")) {
            return
        }

        ensureBluetoothManager()
        val adapter = bluetoothAdapter
        val context = NitroModules.applicationContext ?: return
        if (adapter == null || !adapter.isEnabled) {
            eventEmitter.emit(
                "classicScanFailed",
                mapOf("message" to "Bluetooth is not enabled or unavailable")
            )
            return
        }

        if (classicScanReceiver == null) {
            classicScanReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = getBluetoothDeviceExtra(intent) ?: return
                            classicDevices[device.address] = device
                            eventEmitter.emit(
                                "classicDeviceFound",
                                mapOf(
                                    "id" to device.address,
                                    "name" to device.name,
                                    "bondState" to bondStateFor(device).name.lowercase()
                                )
                            )
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            eventEmitter.emit("classicScanFinished", emptyMap())
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(classicScanReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(classicScanReceiver, filter)
            }
        }

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            if (!adapter.startDiscovery()) {
                eventEmitter.emit(
                    "classicScanFailed",
                    mapOf("message" to "Classic Bluetooth discovery failed to start")
                )
            }
        } catch (error: SecurityException) {
            eventEmitter.emit("classicScanFailed", mapOf("message" to (error.message ?: "Missing Bluetooth permissions")))
        }
    }

    override fun stopClassicScan() {
        ensureBluetoothManager()
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to cancel Classic Bluetooth discovery", error)
        }

        val context = NitroModules.applicationContext ?: return
        classicScanReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Classic Bluetooth discovery receiver was not registered", error)
            }
        }
        classicScanReceiver = null
    }

    override fun connectClassic(deviceId: String, serviceUUID: String?): Promise<Unit> {
        if (!hasRequiredBluetoothPermissions()) {
            return Promise.rejected(SecurityException("Missing Bluetooth permissions"))
        }
        val device = resolveClassicDevice(deviceId)
            ?: return Promise.rejected(IllegalArgumentException("Classic Bluetooth device $deviceId was not found"))
        val uuid = try {
            UUID.fromString(serviceUUID ?: SERIAL_PORT_PROFILE_UUID.toString())
        } catch (error: IllegalArgumentException) {
            return Promise.rejected(error)
        }
        val promise = Promise<Unit>()

        bluetoothScope.launch(Dispatchers.IO) {
            try {
                bluetoothAdapter?.cancelDiscovery()
                closeClassicSocket(deviceId, false)
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                classicSockets[deviceId] = socket
                startClassicReadLoop(deviceId, socket)
                eventEmitter.emit("classicConnected", mapOf("deviceId" to deviceId))
                promise.resolve(Unit)
            } catch (error: SecurityException) {
                promise.reject(error)
            } catch (error: IOException) {
                closeClassicSocket(deviceId, false)
                promise.reject(error)
            }
        }

        return promise
    }

    override fun startClassicServer(serviceUUID: String?, serviceName: String?): Promise<Unit> {
        if (!hasRequiredBluetoothPermissions()) {
            return Promise.rejected(SecurityException("Missing Bluetooth permissions"))
        }

        ensureBluetoothManager()
        val adapter = bluetoothAdapter
            ?: return Promise.rejected(IllegalStateException("Bluetooth adapter unavailable"))
        val uuid = try {
            UUID.fromString(serviceUUID ?: SERIAL_PORT_PROFILE_UUID.toString())
        } catch (error: IllegalArgumentException) {
            return Promise.rejected(error)
        }
        val key = uuid.toString().lowercase()
        val promise = Promise<Unit>()

        bluetoothScope.launch(Dispatchers.IO) {
            try {
                closeClassicServerInternal(key, false)
                val serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                    serviceName ?: DEFAULT_CLASSIC_SERVICE_NAME,
                    uuid
                )
                classicServerSockets[key] = serverSocket
                classicServerJobs[key] = bluetoothScope.launch(Dispatchers.IO) {
                    acceptClassicConnections(key, serverSocket)
                }
                eventEmitter.emit(
                    "classicServerStarted",
                    mapOf("serviceUUID" to uuid.toString(), "serviceName" to (serviceName ?: DEFAULT_CLASSIC_SERVICE_NAME))
                )
                promise.resolve(Unit)
            } catch (error: SecurityException) {
                promise.reject(error)
            } catch (error: IOException) {
                promise.reject(error)
            }
        }

        return promise
    }

    override fun stopClassicServer(serviceUUID: String?) {
        val uuid = try {
            UUID.fromString(serviceUUID ?: SERIAL_PORT_PROFILE_UUID.toString())
        } catch (_: IllegalArgumentException) {
            return
        }
        closeClassicServerInternal(uuid.toString().lowercase(), true)
    }

    override fun disconnectClassic(deviceId: String) {
        closeClassicSocket(deviceId, true)
    }

    override fun writeClassic(deviceId: String, value: String): Promise<Unit> {
        val socket = classicSockets[deviceId]
            ?: return Promise.rejected(IllegalArgumentException("Classic Bluetooth device $deviceId is not connected"))
        val payload = hexStringToByteArray(value)
            ?: return Promise.rejected(IllegalArgumentException("Value must be a hex string"))
        val promise = Promise<Unit>()

        bluetoothScope.launch(Dispatchers.IO) {
            try {
                socket.outputStream.write(payload)
                socket.outputStream.flush()
                promise.resolve(Unit)
            } catch (error: IOException) {
                promise.reject(error)
                closeClassicSocket(deviceId, true)
            }
        }

        return promise
    }

    override fun startBackgroundSession(options: BackgroundSessionOptions) {
        val context = NitroModules.applicationContext ?: run {
            Log.w(TAG, "Unable to start background BLE session: application context unavailable")
            return
        }

        if (!ensureBluetoothPermissions("start background BLE session")) {
            return
        }

        val intent = Intent(context, MunimBluetoothBackgroundService::class.java).apply {
            action = MunimBluetoothBackgroundService.ACTION_START
            putExtra(
                MunimBluetoothBackgroundService.EXTRA_SERVICE_UUIDS,
                options.serviceUUIDs
            )
            putExtra(
                MunimBluetoothBackgroundService.EXTRA_LOCAL_NAME,
                options.localName
            )
            putExtra(
                MunimBluetoothBackgroundService.EXTRA_ALLOW_DUPLICATES,
                options.allowDuplicates ?: false
            )
            putExtra(
                MunimBluetoothBackgroundService.EXTRA_SCAN_MODE,
                options.scanMode?.name ?: ScanMode.LOWPOWER.name
            )
            serializeConfiguredServices()?.let { servicesJson ->
                putExtra(
                    MunimBluetoothBackgroundService.EXTRA_GATT_SERVICES_JSON,
                    servicesJson
                )
            }
            putExtra(
                MunimBluetoothBackgroundService.EXTRA_NOTIFICATION_CHANNEL_ID,
                options.androidNotificationChannelId
                    ?: MunimBluetoothBackgroundService.DEFAULT_CHANNEL_ID
            )
            putExtra(
                MunimBluetoothBackgroundService.EXTRA_NOTIFICATION_CHANNEL_NAME,
                options.androidNotificationChannelName
                    ?: MunimBluetoothBackgroundService.DEFAULT_CHANNEL_NAME
            )
            putExtra(
                MunimBluetoothBackgroundService.EXTRA_NOTIFICATION_TITLE,
                options.androidNotificationTitle
                    ?: MunimBluetoothBackgroundService.DEFAULT_NOTIFICATION_TITLE
            )
            putExtra(
                MunimBluetoothBackgroundService.EXTRA_NOTIFICATION_TEXT,
                options.androidNotificationText
                    ?: MunimBluetoothBackgroundService.DEFAULT_NOTIFICATION_TEXT
            )
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            eventEmitter.emit(
                "backgroundSessionStarted",
                mapOf(
                    "platform" to "android",
                    "serviceUUIDs" to options.serviceUUIDs.toList(),
                    "localName" to options.localName
                )
            )
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to start background BLE session", error)
            eventEmitter.emit(
                "backgroundSessionStartFailed",
                mapOf(
                    "platform" to "android",
                    "error" to (error.message ?: "Unable to start background BLE session")
                )
            )
        }
    }

    override fun stopBackgroundSession() {
        val context = NitroModules.applicationContext ?: return
        val intent = Intent(context, MunimBluetoothBackgroundService::class.java).apply {
            action = MunimBluetoothBackgroundService.ACTION_STOP
        }
        context.startService(intent)
        eventEmitter.emit("backgroundSessionStopped", mapOf("platform" to "android"))
    }

    override fun startMultipeerSession(options: MultipeerSessionOptions) {
        eventEmitter.emit(
            "multipeerStartFailed",
            mapOf(
                "platform" to "android",
                "error" to MULTIPEER_UNSUPPORTED_MESSAGE
            )
        )
        throw UnsupportedOperationException(MULTIPEER_UNSUPPORTED_MESSAGE)
    }

    override fun stopMultipeerSession() {
        eventEmitter.emit("multipeerStopped", mapOf("platform" to "android"))
    }

    override fun inviteMultipeerPeer(peerId: String) {
        throw UnsupportedOperationException(MULTIPEER_UNSUPPORTED_MESSAGE)
    }

    override fun getMultipeerPeers(): Promise<Array<MultipeerPeer>> {
        return Promise.resolved(emptyArray<MultipeerPeer>())
    }

    override fun sendMultipeerMessage(
        value: String,
        peerIds: Array<String>?,
        reliable: Boolean?
    ): Promise<Unit> {
        return unsupportedPromise(MULTIPEER_UNSUPPORTED_MESSAGE)
    }

    override fun addListener(eventName: String) {
        // Nitro uses JS-side listener registration. No native bookkeeping required here.
    }

    override fun removeListeners(count: Double) {
        // Nitro uses JS-side listener registration. No native bookkeeping required here.
    }

    private fun restartAdvertising(delayMs: Long) {
        if (!ensureBluetoothPermissions("restart advertising")) {
            return
        }

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
            currentServiceUUIDs.forEach { uuid ->
                dataBuilder.addServiceUuid(ParcelUuid.fromString(uuid))
            }

            val scanResponseBuilder = AdvertiseData.Builder()
            currentAdvertisingData?.let {
                processAdvertisingData(
                    data = it,
                    dataBuilder = scanResponseBuilder,
                    includeServiceUuids = false
                )
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
                    eventEmitter.emit("advertisingStarted", emptyMap())
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "Advertising failed: $errorCode")
                    eventEmitter.emit(
                        "advertisingStartFailed",
                        mapOf(
                            "errorCode" to errorCode,
                            "message" to advertiseFailureMessage(errorCode)
                        )
                    )
                }
            }

            activeAdvertiser.startAdvertising(
                settings,
                dataBuilder.build(),
                scanResponseBuilder.build(),
                advertiseCallback
            )
        }
    }

    private fun serializeConfiguredServices(): String? {
        if (configuredServices.isEmpty()) {
            return null
        }

        val services = JSONArray()
        configuredServices.forEach { service ->
            val serviceJson = JSONObject()
                .put("uuid", service.uuid)
                .put("characteristics", JSONArray().also { characteristics ->
                    service.characteristics.forEach { characteristic ->
                        val characteristicJson = JSONObject()
                            .put("uuid", characteristic.uuid)
                            .put("properties", stringArrayJson(characteristic.properties))
                        characteristic.value?.let { characteristicJson.put("value", it) }
                        characteristic.descriptors?.let { descriptors ->
                            characteristicJson.put(
                                "descriptors",
                                JSONArray().also { descriptorArray ->
                                    descriptors.forEach { descriptor ->
                                        val descriptorJson = JSONObject()
                                            .put("uuid", descriptor.uuid)
                                        descriptor.value?.let { descriptorJson.put("value", it) }
                                        descriptor.permissions?.let { permissions ->
                                            descriptorJson.put(
                                                "permissions",
                                                stringArrayJson(permissions)
                                            )
                                        }
                                        descriptorArray.put(descriptorJson)
                                    }
                                }
                            )
                        }
                        characteristics.put(characteristicJson)
                    }
                })
            service.includedServices?.let {
                serviceJson.put("includedServices", stringArrayJson(it))
            }
            services.put(serviceJson)
        }

        return services.toString()
    }

    private fun stringArrayJson(values: Array<String>): JSONArray {
        return JSONArray().also { array ->
            values.forEach(array::put)
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
                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_READ_NOT_PERMITTED,
                        offset,
                        null
                    )
                    return
                }

                val value = getCharacteristicValue(characteristic) ?: byteArrayOf()
                if (offset > value.size) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_INVALID_OFFSET,
                        offset,
                        null
                    )
                    return
                }

                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value.copyOfRange(offset, value.size)
                )
                eventEmitter.emit(
                    "peripheralReadRequest",
                    mapOf(
                        "centralId" to device.address,
                        "serviceUUID" to characteristic.service.uuid.toString(),
                        "characteristicUUID" to characteristic.uuid.toString(),
                        "value" to value.toHexString()
                    )
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
                val canWrite = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                if (!canWrite) {
                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
                            offset,
                            null
                        )
                    }
                    return
                }

                val incomingValue = value ?: byteArrayOf()
                val currentValue = getCharacteristicValue(characteristic) ?: byteArrayOf()
                if (offset > currentValue.size) {
                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_INVALID_OFFSET,
                            offset,
                            null
                        )
                    }
                    return
                }

                val nextValue = if (offset == 0) {
                    incomingValue
                } else {
                    val replaceEnd = minOf(offset + incomingValue.size, currentValue.size)
                    currentValue.copyOfRange(0, offset) +
                        incomingValue +
                        currentValue.copyOfRange(replaceEnd, currentValue.size)
                }
                setCharacteristicValue(characteristic, nextValue)
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
                notifySubscribedDevices(characteristic)
                eventEmitter.emit(
                    "peripheralWriteRequest",
                    mapOf(
                        "centralId" to device.address,
                        "serviceUUID" to characteristic.service.uuid.toString(),
                        "characteristicUUID" to characteristic.uuid.toString(),
                        "value" to nextValue.toHexString()
                    )
                )
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor
            ) {
                if (descriptor.uuid != CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                    if ((descriptor.permissions and BluetoothGattDescriptor.PERMISSION_READ) == 0) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                        return
                    }

                    val value = getDescriptorValue(descriptor) ?: byteArrayOf()
                    if (offset > value.size) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                        return
                    }

                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value.copyOfRange(offset, value.size)
                    )
                    return
                }

                val characteristic = descriptor.characteristic
                val subscribers = subscribedDevices[characteristic.uuid]
                val value = if (subscribers?.contains(device) == true) {
                    if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    }
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }

                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (descriptor.uuid != CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                    if ((descriptor.permissions and BluetoothGattDescriptor.PERMISSION_WRITE) == 0) {
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                        }
                        return
                    }

                    val incomingValue = value ?: byteArrayOf()
                    val currentValue = getDescriptorValue(descriptor) ?: byteArrayOf()
                    if (offset > currentValue.size) {
                        if (responseNeeded) {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                        }
                        return
                    }

                    val nextValue = if (offset == 0) {
                        incomingValue
                    } else {
                        val replaceEnd = minOf(offset + incomingValue.size, currentValue.size)
                        currentValue.copyOfRange(0, offset) +
                            incomingValue +
                            currentValue.copyOfRange(replaceEnd, currentValue.size)
                    }
                    setDescriptorValue(descriptor, nextValue)
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                    }
                    return
                }

                val characteristic = descriptor.characteristic
                val requestedValue = value ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                val enabled = requestedValue.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                    requestedValue.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)

                if (enabled) {
                    subscribedDevices.getOrPut(characteristic.uuid) { mutableSetOf() }.add(device)
                    setDescriptorValue(descriptor, requestedValue)
                    eventEmitter.emit(
                        "peripheralSubscribed",
                        mapOf(
                            "centralId" to device.address,
                            "serviceUUID" to characteristic.service.uuid.toString(),
                            "characteristicUUID" to characteristic.uuid.toString()
                        )
                    )
                } else {
                    subscribedDevices[characteristic.uuid]?.remove(device)
                    setDescriptorValue(descriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                    eventEmitter.emit(
                        "peripheralUnsubscribed",
                        mapOf(
                            "centralId" to device.address,
                            "serviceUUID" to characteristic.service.uuid.toString(),
                            "characteristicUUID" to characteristic.uuid.toString()
                        )
                    )
                }

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
                    if (retryPendingConnection(deviceId, gatt, status)) {
                        return
                    }
                    pendingConnectionTimeouts.remove(deviceId)?.cancel()
                    pendingConnectionAttempts.remove(deviceId)
                    pendingConnections.remove(deviceId)?.reject(
                        IllegalStateException("Failed to connect to $deviceId (status=$status)")
                    )
                    (pendingConnectionGatts.remove(deviceId) ?: connectedDevices.remove(deviceId))?.close()
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        pendingConnectionTimeouts.remove(deviceId)?.cancel()
                        pendingConnectionAttempts.remove(deviceId)
                        pendingConnectionGatts.remove(deviceId)
                        connectedDevices[deviceId] = gatt
                        pendingConnections.remove(deviceId)?.resolve(Unit)
                        eventEmitter.emit("deviceConnected", mapOf("deviceId" to deviceId))
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        pendingConnectionTimeouts.remove(deviceId)?.cancel()
                        pendingConnectionAttempts.remove(deviceId)
                        pendingConnections.remove(deviceId)?.reject(
                            IllegalStateException("Disconnected from $deviceId")
                        )
                        (pendingConnectionGatts.remove(deviceId) ?: connectedDevices.remove(deviceId))?.close()
                        rejectPendingOperationsForDevice(
                            deviceId,
                            IllegalStateException("Disconnected from $deviceId")
                        )
                        eventEmitter.emit("deviceDisconnected", mapOf("deviceId" to deviceId))
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                cancelPendingOperationTimeout("services|$deviceId")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val services = buildGattServices(gatt)
                    pendingServiceDiscoveries.remove(deviceId)?.resolve(services)
                    eventEmitter.emit(
                        "servicesDiscovered",
                        mapOf("deviceId" to deviceId, "services" to services.map { servicePayload(it) })
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
                handleCharacteristicRead(deviceId, characteristic, getCharacteristicValue(characteristic), status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                handleCharacteristicRead(deviceId, characteristic, value, status)
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
                cancelPendingOperationTimeout("write|$key")
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
                handleCharacteristicChanged(deviceId, characteristic, getCharacteristicValue(characteristic))
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleCharacteristicChanged(deviceId, characteristic, value)
            }

            override fun onDescriptorRead(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                handleDescriptorRead(deviceId, descriptor, getDescriptorValue(descriptor), status)
            }

            override fun onDescriptorRead(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
                value: ByteArray
            ) {
                handleDescriptorRead(deviceId, descriptor, value, status)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                val characteristic = descriptor.characteristic
                val key = descriptorKey(
                    deviceId,
                    characteristic.service.uuid.toString(),
                    characteristic.uuid.toString(),
                    descriptor.uuid.toString()
                )
                cancelPendingOperationTimeout("descriptorWrite|$key")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingDescriptorWrites.remove(key)?.resolve(Unit)
                } else {
                    pendingDescriptorWrites.remove(key)?.reject(
                        IllegalStateException("Failed to write descriptor $key (status=$status)")
                    )
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                cancelPendingOperationTimeout("mtu|$deviceId")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingMtuRequests.remove(deviceId)?.resolve(mtu.toDouble())
                } else {
                    pendingMtuRequests.remove(deviceId)?.reject(
                        IllegalStateException("Failed to request MTU for $deviceId (status=$status)")
                    )
                }
            }

            override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

                cancelPendingOperationTimeout("phy|$deviceId")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    pendingPhyReads.remove(deviceId)?.resolve(
                        PhyStatus(
                            txPhy = constantToPhy(txPhy),
                            rxPhy = constantToPhy(rxPhy)
                        )
                    )
                } else {
                    pendingPhyReads.remove(deviceId)?.reject(
                        IllegalStateException("Failed to read PHY for $deviceId (status=$status)")
                    )
                }
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                cancelPendingOperationTimeout("rssi|$deviceId")
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

    private fun handleCharacteristicRead(
        deviceId: String,
        characteristic: BluetoothGattCharacteristic,
        valueBytes: ByteArray?,
        status: Int
    ) {
        val key = characteristicKey(
            deviceId,
            characteristic.service.uuid.toString(),
            characteristic.uuid.toString()
        )
        cancelPendingOperationTimeout("read|$key")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val value = buildCharacteristicValue(characteristic, valueBytes)
            lastCharacteristicValues[key] = value
            pendingReads.remove(key)?.resolve(value)
            emitCharacteristicValueChanged(deviceId, value)
        } else {
            pendingReads.remove(key)?.reject(
                IllegalStateException("Failed to read characteristic $key (status=$status)")
            )
        }
    }

    private fun handleCharacteristicChanged(
        deviceId: String,
        characteristic: BluetoothGattCharacteristic,
        valueBytes: ByteArray?
    ) {
        val value = buildCharacteristicValue(characteristic, valueBytes)
        val key = characteristicKey(deviceId, value.serviceUUID, value.characteristicUUID)
        lastCharacteristicValues[key] = value
        emitCharacteristicValueChanged(deviceId, value)
    }

    private fun handleDescriptorRead(
        deviceId: String,
        descriptor: BluetoothGattDescriptor,
        valueBytes: ByteArray?,
        status: Int
    ) {
        val characteristic = descriptor.characteristic
        val key = descriptorKey(
            deviceId,
            characteristic.service.uuid.toString(),
            characteristic.uuid.toString(),
            descriptor.uuid.toString()
        )
        cancelPendingOperationTimeout("descriptorRead|$key")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            pendingDescriptorReads.remove(key)?.resolve(buildDescriptorValue(descriptor, valueBytes))
        } else {
            pendingDescriptorReads.remove(key)?.reject(
                IllegalStateException("Failed to read descriptor $key (status=$status)")
            )
        }
    }

    private fun emitCharacteristicValueChanged(deviceId: String, value: CharacteristicValue) {
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

    private fun rejectPendingOperationsForDevice(deviceId: String, error: Throwable) {
        pendingConnectionTimeouts.remove(deviceId)?.cancel()
        pendingConnectionAttempts.remove(deviceId)
        cancelPendingOperationTimeoutsForDevice(deviceId)
        pendingReads.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingReads.remove(key)?.reject(error) }
        pendingWrites.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingWrites.remove(key)?.reject(error) }
        pendingDescriptorReads.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingDescriptorReads.remove(key)?.reject(error) }
        pendingDescriptorWrites.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingDescriptorWrites.remove(key)?.reject(error) }
        pendingServiceDiscoveries.remove(deviceId)?.reject(error)
        pendingRssiReads.remove(deviceId)?.reject(error)
        pendingMtuRequests.remove(deviceId)?.reject(error)
        pendingPhyReads.remove(deviceId)?.reject(error)
    }

    private fun scheduleConnectionTimeout(deviceId: String) {
        pendingConnectionTimeouts.remove(deviceId)?.cancel()
        pendingConnectionTimeouts[deviceId] = bluetoothScope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            pendingConnectionTimeouts.remove(deviceId)
            pendingConnectionAttempts.remove(deviceId)
            val promise = pendingConnections.remove(deviceId) ?: return@launch
            (pendingConnectionGatts.remove(deviceId) ?: connectedDevices.remove(deviceId))?.let { gatt ->
                gatt.disconnect()
                gatt.close()
            }
            promise.reject(IllegalStateException("Connection timed out for $deviceId"))
        }
    }

    private fun startGattConnection(deviceId: String, device: BluetoothDevice) {
        val context = NitroModules.applicationContext
        if (context == null) {
            pendingConnectionTimeouts.remove(deviceId)?.cancel()
            pendingConnectionAttempts.remove(deviceId)
            pendingConnections.remove(deviceId)?.reject(IllegalStateException("React context unavailable"))
            return
        }

        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, createGattCallback(deviceId), BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, createGattCallback(deviceId))
        }
        pendingConnectionGatts[deviceId] = gatt
    }

    private fun retryPendingConnection(deviceId: String, failedGatt: BluetoothGatt, status: Int): Boolean {
        val promise = pendingConnections[deviceId] ?: return false
        val attempt = pendingConnectionAttempts[deviceId] ?: 0
        if (attempt >= MAX_CONNECTION_RETRIES) {
            return false
        }

        pendingConnectionAttempts[deviceId] = attempt + 1
        pendingConnectionGatts.remove(deviceId)
        failedGatt.close()

        Log.w(
            TAG,
            "BLE connection attempt ${attempt + 1} for $deviceId failed with status=$status; retrying"
        )

        bluetoothScope.launch {
            delay(CONNECTION_RETRY_DELAY_MS * (attempt + 1))
            if (pendingConnections[deviceId] === promise) {
                startGattConnection(deviceId, failedGatt.device)
            }
        }
        return true
    }

    private fun <T> schedulePendingOperationTimeout(
        timeoutKey: String,
        description: String,
        removePending: () -> Promise<T>?
    ) {
        pendingOperationTimeouts.remove(timeoutKey)?.cancel()
        pendingOperationTimeouts[timeoutKey] = bluetoothScope.launch {
            delay(OPERATION_TIMEOUT_MS)
            pendingOperationTimeouts.remove(timeoutKey)
            removePending()?.reject(IllegalStateException("$description timed out"))
        }
    }

    private fun cancelPendingOperationTimeout(timeoutKey: String) {
        pendingOperationTimeouts.remove(timeoutKey)?.cancel()
    }

    private fun cancelPendingOperationTimeoutsForDevice(deviceId: String) {
        pendingOperationTimeouts.keys
            .filter { key ->
                key == "services|$deviceId" ||
                    key == "rssi|$deviceId" ||
                    key == "mtu|$deviceId" ||
                    key == "phy|$deviceId" ||
                    key.contains("|$deviceId|")
            }
            .forEach { key -> pendingOperationTimeouts.remove(key)?.cancel() }
    }

    private fun notifySubscribedDevices(characteristic: BluetoothGattCharacteristic) {
        if (!supportsNotifyOrIndicate(characteristic)) return

        val subscribers = subscribedDevices[characteristic.uuid]?.toList().orEmpty()
        if (subscribers.isEmpty()) return

        subscribers.forEach { device ->
            val confirm = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattServer?.notifyCharacteristicChanged(
                    device,
                    characteristic,
                    confirm,
                    getCharacteristicValue(characteristic) ?: byteArrayOf()
                )
            } else {
                @Suppress("DEPRECATION")
                gattServer?.notifyCharacteristicChanged(
                    device,
                    characteristic,
                    confirm
                )
            }
        }
    }

    private fun supportsNotifyOrIndicate(characteristic: BluetoothGattCharacteristic): Boolean {
        return (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
            (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
    }

    private fun buildGattServices(gatt: BluetoothGatt): Array<GATTService> {
        return gatt.services.map { service ->
            GATTService(
                uuid = service.uuid.toString(),
                characteristics = service.characteristics.map { characteristic ->
                    GATTCharacteristic(
                        uuid = characteristic.uuid.toString(),
                        properties = propertiesToArray(characteristic.properties),
                        value = getCharacteristicValue(characteristic)?.toHexString(),
                        descriptors = characteristic.descriptors.map { descriptor ->
                            GATTDescriptor(
                                uuid = descriptor.uuid.toString(),
                                value = getDescriptorValue(descriptor)?.toHexString(),
                                permissions = permissionsToArray(descriptor.permissions)
                            )
                        }.toTypedArray()
                    )
                }.toTypedArray(),
                includedServices = service.includedServices.map { it.uuid.toString() }.toTypedArray()
            )
        }.toTypedArray()
    }

    private fun servicePayload(service: GATTService): Map<String, Any?> {
        return mapOf(
            "uuid" to service.uuid,
            "characteristics" to service.characteristics.map { characteristic ->
                mapOf(
                    "uuid" to characteristic.uuid,
                    "properties" to characteristic.properties.toList(),
                    "value" to characteristic.value,
                    "descriptors" to characteristic.descriptors?.map { descriptor ->
                        mapOf(
                            "uuid" to descriptor.uuid,
                            "value" to descriptor.value,
                            "permissions" to descriptor.permissions?.toList()
                        )
                    }
                )
            },
            "includedServices" to service.includedServices?.toList()
        )
    }

    private fun buildCharacteristicValue(
        characteristic: BluetoothGattCharacteristic,
        valueBytes: ByteArray? = getCharacteristicValue(characteristic)
    ): CharacteristicValue {
        return CharacteristicValue(
            value = valueBytes?.toHexString() ?: "",
            serviceUUID = characteristic.service.uuid.toString(),
            characteristicUUID = characteristic.uuid.toString()
        )
    }

    private fun buildDescriptorValue(
        descriptor: BluetoothGattDescriptor,
        valueBytes: ByteArray? = getDescriptorValue(descriptor)
    ): DescriptorValue {
        val characteristic = descriptor.characteristic
        return DescriptorValue(
            value = valueBytes?.toHexString() ?: "",
            serviceUUID = characteristic.service.uuid.toString(),
            characteristicUUID = characteristic.uuid.toString(),
            descriptorUUID = descriptor.uuid.toString()
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

    private fun findDescriptor(
        gatt: BluetoothGatt,
        serviceUUID: String,
        characteristicUUID: String,
        descriptorUUID: String
    ): BluetoothGattDescriptor? {
        val characteristic = findCharacteristic(gatt, serviceUUID, characteristicUUID) ?: return null
        return characteristic.descriptors.firstOrNull {
            it.uuid.toString().equals(descriptorUUID, ignoreCase = true)
        }
    }

    @Suppress("DEPRECATION")
    private fun getCharacteristicValue(characteristic: BluetoothGattCharacteristic): ByteArray? {
        return characteristic.value
    }

    @Suppress("DEPRECATION")
    private fun setCharacteristicValue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        characteristic.value = value
    }

    @Suppress("DEPRECATION")
    private fun getDescriptorValue(descriptor: BluetoothGattDescriptor): ByteArray? {
        return descriptor.value
    }

    @Suppress("DEPRECATION")
    private fun setDescriptorValue(descriptor: BluetoothGattDescriptor, value: ByteArray) {
        descriptor.value = value
    }

    @Suppress("DEPRECATION")
    private fun writeGattCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.value = value
            characteristic.writeType = writeType
            gatt.writeCharacteristic(characteristic)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeGattDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun characteristicKey(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ): String {
        return "$deviceId|${serviceUUID.lowercase()}|${characteristicUUID.lowercase()}"
    }

    private fun descriptorKey(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String,
        descriptorUUID: String
    ): String {
        return "${characteristicKey(deviceId, serviceUUID, characteristicUUID)}|${descriptorUUID.lowercase()}"
    }

    private fun resolveBluetoothDevice(deviceId: String): BluetoothDevice? {
        ensureBluetoothManager()
        return discoveredDevices[deviceId]
            ?: connectedDevices[deviceId]?.device
            ?: try {
                bluetoothAdapter?.getRemoteDevice(deviceId)
            } catch (_: IllegalArgumentException) {
                null
            }
    }

    private fun findLocalCharacteristic(
        serviceUUID: String,
        characteristicUUID: String
    ): BluetoothGattCharacteristic? {
        return try {
            val service = gattServer?.getService(UUID.fromString(serviceUUID))
            service?.getCharacteristic(UUID.fromString(characteristicUUID))
        } catch (_: IllegalArgumentException) {
            null
        }
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
        serviceUUIDs?.let { addServiceUuidBuckets(it, advertisingData) }
        serviceData?.takeIf { it.isNotEmpty() }?.let { entries ->
            addServiceDataBuckets(entries, advertisingData)
        }

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

    private fun addServiceUuidBuckets(
        uuidStrings: List<String>,
        target: MutableMap<String, Any?>
    ) {
        addUuidBuckets(
            uuidStrings,
            key16 = "completeServiceUUIDs16",
            key32 = "completeServiceUUIDs32",
            key128 = "completeServiceUUIDs128",
            target = target
        )
    }

    private fun addUuidBuckets(
        uuidStrings: List<String>,
        key16: String,
        key32: String,
        key128: String,
        target: MutableMap<String, Any?>
    ) {
        val uuid16 = uuidStrings.filter { uuidBitWidth(it) == 16 }
        val uuid32 = uuidStrings.filter { uuidBitWidth(it) == 32 }
        val uuid128 = uuidStrings.filter { uuidBitWidth(it) == 128 }

        if (uuid16.isNotEmpty()) target[key16] = uuid16
        if (uuid32.isNotEmpty()) target[key32] = uuid32
        if (uuid128.isNotEmpty()) target[key128] = uuid128
    }

    private fun addServiceDataBuckets(
        entries: List<ServiceDataEntry>,
        target: MutableMap<String, Any?>
    ) {
        val serviceData16 = entries.filter { uuidBitWidth(it.uuid) == 16 }
            .map { mapOf("uuid" to it.uuid, "data" to it.data) }
        val serviceData32 = entries.filter { uuidBitWidth(it.uuid) == 32 }
            .map { mapOf("uuid" to it.uuid, "data" to it.data) }
        val serviceData128 = entries.filter { uuidBitWidth(it.uuid) == 128 }
            .map { mapOf("uuid" to it.uuid, "data" to it.data) }

        if (serviceData16.isNotEmpty()) target["serviceData16"] = serviceData16
        if (serviceData32.isNotEmpty()) target["serviceData32"] = serviceData32
        if (serviceData128.isNotEmpty()) target["serviceData128"] = serviceData128
    }

    private fun uuidBitWidth(uuidString: String): Int {
        return when (uuidString.replace("-", "").length) {
            4 -> 16
            8 -> 32
            else -> 128
        }
    }

    private fun emitDeviceFound(payload: Map<String, Any?>) {
        eventEmitter.emit("deviceFound", payload)
        eventEmitter.emit("onDeviceFound", payload)
        eventEmitter.emit("scanResult", payload)
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
        dataBuilder: AdvertiseData.Builder,
        includeServiceUuids: Boolean = true
    ) {
        if (includeServiceUuids) {
            addServiceUUIDs(data.incompleteServiceUUIDs16, dataBuilder)
            addServiceUUIDs(data.completeServiceUUIDs16, dataBuilder)
            addServiceUUIDs(data.incompleteServiceUUIDs32, dataBuilder)
            addServiceUUIDs(data.completeServiceUUIDs32, dataBuilder)
            addServiceUUIDs(data.incompleteServiceUUIDs128, dataBuilder)
            addServiceUUIDs(data.completeServiceUUIDs128, dataBuilder)
        }

        if (data.shortenedLocalName != null || data.completeLocalName != null) {
            dataBuilder.setIncludeDeviceName(true)
        }
        if (data.txPowerLevel != null) {
            dataBuilder.setIncludeTxPowerLevel(true)
        }

        if (includeServiceUuids) {
            addServiceUUIDs(data.serviceSolicitationUUIDs16, dataBuilder)
            addServiceUUIDs(data.serviceSolicitationUUIDs32, dataBuilder)
            addServiceUUIDs(data.serviceSolicitationUUIDs128, dataBuilder)
        }
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

    private fun descriptorPermissionsFromArray(permissions: Array<String>?): Int {
        if (permissions.isNullOrEmpty()) {
            return BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        }

        var result = 0
        permissions.forEach { permission ->
            when (permission) {
                "read" -> result = result or BluetoothGattDescriptor.PERMISSION_READ
                "write" -> result = result or BluetoothGattDescriptor.PERMISSION_WRITE
                "readEncrypted" -> result = result or BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                "writeEncrypted" -> result = result or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
                "readEncryptedMitm" -> {
                    result = result or BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM
                }
                "writeEncryptedMitm" -> {
                    result = result or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM
                }
            }
        }
        return result
    }

    private fun permissionsToArray(permissions: Int): Array<String> {
        val result = mutableListOf<String>()
        if (permissions and BluetoothGattDescriptor.PERMISSION_READ != 0) result += "read"
        if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE != 0) result += "write"
        if (permissions and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED != 0) result += "readEncrypted"
        if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED != 0) result += "writeEncrypted"
        if (permissions and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM != 0) {
            result += "readEncryptedMitm"
        }
        if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM != 0) {
            result += "writeEncryptedMitm"
        }
        return result.toTypedArray()
    }

    private fun bondStateFor(device: BluetoothDevice): BondState {
        return when (device.bondState) {
            BluetoothDevice.BOND_BONDING -> BondState.BONDING
            BluetoothDevice.BOND_BONDED -> BondState.BONDED
            else -> BondState.NONE
        }
    }

    private fun phyToMask(phy: BluetoothPhy): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0
        return when (phy) {
            BluetoothPhy.LE2M -> BluetoothDevice.PHY_LE_2M_MASK
            BluetoothPhy.LECODED -> BluetoothDevice.PHY_LE_CODED_MASK
            BluetoothPhy.LE1M -> BluetoothDevice.PHY_LE_1M_MASK
        }
    }

    private fun phyToAdvertisingPhy(phy: BluetoothPhy): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 1
        return when (phy) {
            BluetoothPhy.LE2M -> BluetoothDevice.PHY_LE_2M
            BluetoothPhy.LECODED -> BluetoothDevice.PHY_LE_CODED
            BluetoothPhy.LE1M -> BluetoothDevice.PHY_LE_1M
        }
    }

    private fun constantToPhy(phy: Int): BluetoothPhy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return when (phy) {
                BluetoothDevice.PHY_LE_2M -> BluetoothPhy.LE2M
                BluetoothDevice.PHY_LE_CODED -> BluetoothPhy.LECODED
                else -> BluetoothPhy.LE1M
            }
        }
        return BluetoothPhy.LE1M
    }

    private fun phyOptionToConstant(option: BluetoothPhyOption): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0
        return when (option) {
            BluetoothPhyOption.S2 -> BluetoothDevice.PHY_OPTION_S2
            BluetoothPhyOption.S8 -> BluetoothDevice.PHY_OPTION_S8
            BluetoothPhyOption.NONE -> BluetoothDevice.PHY_OPTION_NO_PREFERRED
        }
    }

    private fun acceptL2CAPConnections(psm: Int, serverSocket: BluetoothServerSocket) {
        while (l2capServerSockets[psm] === serverSocket) {
            try {
                val socket = serverSocket.accept()
                val deviceId = try {
                    socket.remoteDevice?.address
                } catch (_: SecurityException) {
                    null
                }
                registerL2CAPSocket(socket, psm, deviceId)
            } catch (error: IOException) {
                if (l2capServerSockets[psm] === serverSocket) {
                    Log.w(TAG, "L2CAP accept failed for PSM $psm", error)
                }
                break
            }
        }
    }

    private fun registerL2CAPSocket(socket: BluetoothSocket, psm: Int, deviceId: String?): L2CAPChannel {
        val channelId = UUID.randomUUID().toString()
        l2capSockets[channelId] = socket
        val channel = L2CAPChannel(channelId, psm.toDouble(), deviceId)
        eventEmitter.emit(
            "l2capChannelOpened",
            mapOf("channelId" to channelId, "psm" to channel.psm, "deviceId" to deviceId)
        )
        startL2CAPReadLoop(channelId, socket, channel)
        return channel
    }

    private fun startL2CAPReadLoop(channelId: String, socket: BluetoothSocket, channel: L2CAPChannel) {
        l2capReadJobs.remove(channelId)?.cancel()
        l2capReadJobs[channelId] = bluetoothScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(DEFAULT_STREAM_BUFFER_SIZE)
            try {
                while (true) {
                    val count = socket.inputStream.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        eventEmitter.emit(
                            "l2capDataReceived",
                            mapOf(
                                "channelId" to channelId,
                                "psm" to channel.psm,
                                "deviceId" to channel.deviceId,
                                "value" to buffer.copyOf(count).toHexString()
                            )
                        )
                    }
                }
            } catch (error: IOException) {
                if (l2capSockets[channelId] === socket) {
                    Log.w(TAG, "L2CAP channel $channelId closed", error)
                }
            } finally {
                if (l2capSockets[channelId] === socket) {
                    closeL2CAPChannelInternal(channelId, true)
                }
            }
        }
    }

    private fun closeL2CAPChannelInternal(channelId: String, emitEvent: Boolean) {
        l2capReadJobs.remove(channelId)?.cancel()
        val socket = l2capSockets.remove(channelId)
        try {
            socket?.close()
        } catch (error: IOException) {
            Log.w(TAG, "Unable to close L2CAP channel $channelId", error)
        }
        if (emitEvent) {
            eventEmitter.emit("l2capChannelClosed", mapOf("channelId" to channelId))
        }
    }

    private fun startClassicReadLoop(deviceId: String, socket: BluetoothSocket) {
        classicReadJobs.remove(deviceId)?.cancel()
        classicReadJobs[deviceId] = bluetoothScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(DEFAULT_STREAM_BUFFER_SIZE)
            try {
                while (true) {
                    val count = socket.inputStream.read(buffer)
                    if (count < 0) break
                    if (count > 0) {
                        eventEmitter.emit(
                            "classicDataReceived",
                            mapOf("deviceId" to deviceId, "value" to buffer.copyOf(count).toHexString())
                        )
                    }
                }
            } catch (error: IOException) {
                if (classicSockets[deviceId] === socket) {
                    Log.w(TAG, "Classic Bluetooth socket for $deviceId closed", error)
                }
            } finally {
                if (classicSockets[deviceId] === socket) {
                    closeClassicSocket(deviceId, true)
                }
            }
        }
    }

    private fun acceptClassicConnections(key: String, serverSocket: BluetoothServerSocket) {
        while (classicServerSockets[key] === serverSocket) {
            try {
                val socket = serverSocket.accept()
                val device = socket.remoteDevice
                val deviceId = device.address
                classicDevices[deviceId] = device
                closeClassicSocket(deviceId, false)
                classicSockets[deviceId] = socket
                startClassicReadLoop(deviceId, socket)
                eventEmitter.emit("classicConnectionReceived", mapOf("deviceId" to deviceId))
                eventEmitter.emit("classicConnected", mapOf("deviceId" to deviceId))
            } catch (error: IOException) {
                if (classicServerSockets[key] === serverSocket) {
                    Log.w(TAG, "Classic Bluetooth RFCOMM accept failed", error)
                }
                break
            } catch (error: SecurityException) {
                Log.w(TAG, "Classic Bluetooth RFCOMM accept failed due to permissions", error)
                break
            }
        }
    }

    private fun closeClassicSocket(deviceId: String, emitEvent: Boolean) {
        classicReadJobs.remove(deviceId)?.cancel()
        try {
            classicSockets.remove(deviceId)?.close()
        } catch (error: IOException) {
            Log.w(TAG, "Unable to close Classic Bluetooth socket for $deviceId", error)
        }
        if (emitEvent) {
            eventEmitter.emit("classicDisconnected", mapOf("deviceId" to deviceId))
        }
    }

    private fun closeClassicServerInternal(key: String, emitEvent: Boolean) {
        classicServerJobs.remove(key)?.cancel()
        try {
            classicServerSockets.remove(key)?.close()
        } catch (error: IOException) {
            Log.w(TAG, "Unable to close Classic Bluetooth server socket for $key", error)
        }
        if (emitEvent) {
            eventEmitter.emit("classicServerStopped", mapOf("serviceUUID" to key))
        }
    }

    private fun resolveClassicDevice(deviceId: String): BluetoothDevice? {
        ensureBluetoothManager()
        classicDevices[deviceId]?.let { return it }
        discoveredDevices[deviceId]?.let { return it }

        val adapter = bluetoothAdapter ?: return null
        try {
            adapter.bondedDevices?.firstOrNull { it.address == deviceId }?.let { return it }
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to inspect bonded Classic Bluetooth devices", error)
        }

        return try {
            adapter.getRemoteDevice(deviceId)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun getBluetoothDeviceExtra(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun <T> unsupportedPromise(message: String): Promise<T> {
        return Promise.rejected(UnsupportedOperationException(message))
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

    private fun scanFailureMessage(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal scan error"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scan feature unsupported"
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of hardware scan resources"
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scanning too frequently"
            else -> "Scan failed"
        }
    }

    private fun advertiseFailureMessage(errorCode: Int): String {
        return when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Advertising data too large"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising already started"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal advertising error"
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE advertising feature unsupported"
            else -> "Advertising failed"
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

    private fun restoreAdapterName() {
        val adapter = bluetoothAdapter ?: return
        val originalName = previousAdapterName ?: return
        try {
            adapter.name = originalName
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to restore Bluetooth adapter name", error)
        }
        previousAdapterName = null
    }

    companion object {
        private const val TAG = "HybridMunimBluetooth"
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 9137
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val CONNECTION_RETRY_DELAY_MS = 350L
        private const val MAX_CONNECTION_RETRIES = 2
        private const val OPERATION_TIMEOUT_MS = 15_000L
        private const val DEFAULT_STREAM_BUFFER_SIZE = 4096
        private const val DEFAULT_CLASSIC_SERVICE_NAME = "MunimBluetooth"
        private const val MULTIPEER_UNSUPPORTED_MESSAGE =
            "Apple Multipeer Connectivity is only available on Apple platforms"
        private val SERIAL_PORT_PROFILE_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
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

        UiThreadUtil.runOnUiThread {
            val writable = Arguments.createMap()
            payload.forEach { (key, value) ->
                writeValue(writable, key, value)
            }

            context
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, writable)
        }
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

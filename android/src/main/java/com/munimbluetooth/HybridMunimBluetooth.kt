package com.munimbluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
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
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.annotation.Keep
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
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
import com.margelo.nitro.munimbluetooth.BluetoothDeviceType
import com.margelo.nitro.munimbluetooth.BondState
import com.margelo.nitro.munimbluetooth.BondedDevice
import com.margelo.nitro.munimbluetooth.CharacteristicValue
import com.margelo.nitro.munimbluetooth.ConnectOptions
import com.margelo.nitro.munimbluetooth.ConnectionPriority
import com.margelo.nitro.munimbluetooth.DescriptorValue
import com.margelo.nitro.munimbluetooth.ExtendedAdvertisingOptions
import com.margelo.nitro.munimbluetooth.GATTCharacteristic
import com.margelo.nitro.munimbluetooth.GATTCharacteristicPermission
import com.margelo.nitro.munimbluetooth.GATTDescriptor
import com.margelo.nitro.munimbluetooth.GATTQueueDiagnostic
import com.margelo.nitro.munimbluetooth.GATTService
import com.margelo.nitro.munimbluetooth.HybridMunimBluetoothSpec
import com.margelo.nitro.munimbluetooth.L2CAPChannel
import com.margelo.nitro.munimbluetooth.ManufacturerDataEntry
import com.margelo.nitro.munimbluetooth.MultipeerPeer
import com.margelo.nitro.munimbluetooth.MultipeerSessionOptions
import com.margelo.nitro.munimbluetooth.PeripheralRequestMode
import com.margelo.nitro.munimbluetooth.PeripheralRequestOptions
import com.margelo.nitro.munimbluetooth.PeripheralRequestStatus
import com.margelo.nitro.munimbluetooth.PhyStatus
import com.margelo.nitro.munimbluetooth.ScanCallbackType
import com.margelo.nitro.munimbluetooth.ScanMatchMode
import com.margelo.nitro.munimbluetooth.ScanMode
import com.margelo.nitro.munimbluetooth.ScanPhy
import com.margelo.nitro.munimbluetooth.ScanOptions
import com.margelo.nitro.munimbluetooth.ServiceDataEntry
import com.margelo.nitro.munimbluetooth.WriteLengthType
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
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

@Keep
@DoNotStrip
class HybridMunimBluetooth : HybridMunimBluetoothSpec() {
    private data class QueuedGattOperation(
        val kind: String,
        val target: String,
        val start: () -> Boolean,
        val reject: (Throwable) -> Unit,
        var startedAtMs: Long = 0
    )

    private data class PreparedWriteFragment(
        val characteristic: BluetoothGattCharacteristic,
        val offset: Int,
        val value: ByteArray,
        val requestId: String
    )

    private sealed class PendingPeripheralRequest(open val timeout: Job) {
        data class Read(
            val device: BluetoothDevice,
            val nativeRequestId: Int,
            val offset: Int,
            val characteristic: BluetoothGattCharacteristic,
            override val timeout: Job
        ) : PendingPeripheralRequest(timeout)

        data class Write(
            val device: BluetoothDevice,
            val nativeRequestId: Int,
            val offset: Int,
            val characteristic: BluetoothGattCharacteristic,
            val value: ByteArray,
            val preparedWrite: Boolean,
            val responseNeeded: Boolean,
            override val timeout: Job
        ) : PendingPeripheralRequest(timeout)

        data class Execute(
            val device: BluetoothDevice,
            val nativeRequestId: Int,
            val fragments: List<PreparedWriteFragment>,
            override val timeout: Job
        ) : PendingPeripheralRequest(timeout)
    }

    private val bluetoothScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private val extendedAdvertisingSets = ConcurrentHashMap<String, AdvertisingSet>()
    private val extendedAdvertisingCallbacks = ConcurrentHashMap<String, AdvertisingSetCallback>()
    private var gattServer: BluetoothGattServer? = null
    private var gattServerReady = false
    private var advertiseJob: Job? = null
    private var currentAdvertisingData: AdvertisingDataTypes? = null
    private var currentServiceUUIDs: Array<String> = emptyArray()
    private var currentLocalName: String? = null
    private var currentManufacturerData: String? = null
    private var currentManufacturerCompanyId: Double? = null
    private var currentManufacturerDataEntries: Array<ManufacturerDataEntry>? = null
    private var previousAdapterName: String? = null
    private var configuredServices: Array<GATTService> = emptyArray()
    private var peripheralRequestMode = PeripheralRequestMode.AUTOMATIC
    private var peripheralRequestTimeoutMs = DEFAULT_PERIPHERAL_REQUEST_TIMEOUT_MS
    private val pendingPeripheralRequests = ConcurrentHashMap<String, PendingPeripheralRequest>()
    private val preparedWrites = ConcurrentHashMap<String, MutableList<PreparedWriteFragment>>()
    private val pendingServicePublications = ArrayDeque<BluetoothGattService>()
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private var scanAllowDuplicates = false
    private var scanRssiThreshold: Double? = null
    private var scanNamePrefix: String? = null
    // Android silently ignores the 6th scan start within 30 s per app;
    // track our own starts so the app hears about it.
    private val recentScanStarts = ArrayDeque<Long>()
    private val discoveredDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val connectedDevices = ConcurrentHashMap<String, BluetoothGatt>()
    private val pendingConnections = ConcurrentHashMap<String, Promise<Unit>>()
    private val pendingServiceDiscoveries = ConcurrentHashMap<String, Promise<Array<GATTService>>>()
    private val pendingReads = ConcurrentHashMap<String, Promise<CharacteristicValue>>()
    private val pendingWrites = ConcurrentHashMap<String, Promise<Unit>>()
    private val pendingDescriptorReads = ConcurrentHashMap<String, Promise<DescriptorValue>>()
    private val pendingDescriptorWrites = ConcurrentHashMap<String, Promise<Unit>>()
    private val pendingMtuRequests = ConcurrentHashMap<String, Promise<Double>>()
    private val negotiatedMtus = ConcurrentHashMap<String, Int>()
    // Devices whose remote GATT database changed (or whose cache was
    // refreshed); gatt.services is stale for them until rediscovered.
    private val devicesNeedingServiceRediscovery = ConcurrentHashMap.newKeySet<String>()
    private val pendingPhyReads = ConcurrentHashMap<String, Promise<PhyStatus>>()
    private val pendingPhyWrites = ConcurrentHashMap<String, Promise<Unit>>()
    private val pendingRssiReads = ConcurrentHashMap<String, Promise<Double>>()
    private val pendingConnectionTimeouts = ConcurrentHashMap<String, Job>()
    private val pendingConnectionAttempts = ConcurrentHashMap<String, Int>()
    private val pendingConnectionAutoConnect = ConcurrentHashMap<String, Boolean>()
    private val pendingOperationTimeouts = ConcurrentHashMap<String, Job>()
    private val gattOperationQueues = ConcurrentHashMap<String, ArrayDeque<QueuedGattOperation>>()
    private val activeGattOperations = ConcurrentHashMap<String, QueuedGattOperation>()
    private val gattOperationTimeouts = ConcurrentHashMap<String, Job>()
    private val pendingConnectionGatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val lastCharacteristicValues = ConcurrentHashMap<String, CharacteristicValue>()
    private val lastRssiValues = ConcurrentHashMap<String, Double>()
    private val subscribedDevices = ConcurrentHashMap<UUID, MutableSet<BluetoothDevice>>()
    private var bondStateReceiver: BroadcastReceiver? = null
    private var adapterStateReceiver: BroadcastReceiver? = null
    private var pendingEnableRequest: Promise<Boolean>? = null
    private val pendingBondPromises = ConcurrentHashMap<String, Promise<BondState>>()
    private val pendingBondTimeouts = ConcurrentHashMap<String, Job>()
    private var classicScanReceiver: BroadcastReceiver? = null
    private val classicDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val classicSockets = ConcurrentHashMap<String, BluetoothSocket>()
    private val classicReadJobs = ConcurrentHashMap<String, Job>()
    private val classicServerSockets = ConcurrentHashMap<String, BluetoothServerSocket>()
    private val classicServerJobs = ConcurrentHashMap<String, Job>()
    private val l2capServerSockets = ConcurrentHashMap<Int, BluetoothServerSocket>()
    private val l2capAcceptJobs = ConcurrentHashMap<Int, Job>()
    private val l2capSockets = ConcurrentHashMap<String, BluetoothSocket>()
    private val l2capReadJobs = ConcurrentHashMap<String, Job>()
    private val l2capAdmissionLock = Any()
    private var inboundL2CAPChannelCount = 0
    private val inboundL2CAPCountsByPeer = mutableMapOf<String, Int>()
    private val inboundL2CAPPeersByChannel = mutableMapOf<String, String>()
    private val eventEmitter = NitroEventEmitter(TAG)
    private var nextPermissionRequestCode = BLUETOOTH_PERMISSION_REQUEST_CODE

    init {
        ensureAdapterStateReceiver()
    }

    private fun getBluetoothManager(): BluetoothManager? {
        val context = NitroModules.applicationContext ?: return null
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private fun ensureBluetoothManager() {
        if (bluetoothManager == null) {
            bluetoothManager = getBluetoothManager()
            bluetoothAdapter = bluetoothManager?.adapter
        }
        ensureAdapterStateReceiver()
    }

    /** Emits adapterStateChanged for BluetoothAdapter.ACTION_STATE_CHANGED. */
    @Synchronized
    private fun ensureAdapterStateReceiver() {
        if (adapterStateReceiver != null) return
        val context = NitroModules.applicationContext ?: return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                eventEmitter.emit(
                    "adapterStateChanged",
                    mapOf(
                        "state" to adapterStateName(state),
                        "authorization" to if (hasRequiredBluetoothPermissions(BluetoothPermission.CONNECT)) {
                            "allowedAlways"
                        } else {
                            "unknown"
                        }
                    )
                )
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        // Exported for the same reason as the bond receiver: the broadcast
        // comes from the privileged Bluetooth app, which NOT_EXPORTED
        // receivers do not hear.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        adapterStateReceiver = receiver
    }

    private fun adapterStateName(state: Int): String {
        return when (state) {
            BluetoothAdapter.STATE_ON -> "poweredOn"
            BluetoothAdapter.STATE_OFF -> "poweredOff"
            BluetoothAdapter.STATE_TURNING_ON -> "turningOn"
            BluetoothAdapter.STATE_TURNING_OFF -> "turningOff"
            else -> "unknown"
        }
    }

    private fun hasRequiredBluetoothPermissions(
        vararg permissions: BluetoothPermission
    ): Boolean {
        val context = NitroModules.applicationContext ?: return false
        return BluetoothPermissionUtils.hasRequiredPermissions(context, *permissions)
    }

    private fun ensureBluetoothPermissions(
        operationName: String,
        vararg permissions: BluetoothPermission
    ): Boolean {
        val context = NitroModules.applicationContext
        if (context == null) {
            Log.w(TAG, "Unable to $operationName: React context unavailable")
            return false
        }

        val missingPermissions = BluetoothPermissionUtils.missingPermissions(context, *permissions)
        if (missingPermissions.isNotEmpty()) {
            Log.w(
                TAG,
                "Unable to $operationName: missing Bluetooth permissions (${missingPermissions.joinToString()})"
            )
            return false
        }

        return true
    }

    private fun permissionsForRequest(
        permissions: Array<String>?
    ): Array<BluetoothPermission> {
        val requestedCapabilities = permissions ?: arrayOf("scan", "connect")
        return requestedCapabilities.flatMap { capability ->
            when (capability.trim().lowercase()) {
                "scan" -> listOf(BluetoothPermission.SCAN, BluetoothPermission.CONNECT)
                "connect" -> listOf(BluetoothPermission.CONNECT)
                "advertise" -> listOf(BluetoothPermission.ADVERTISE, BluetoothPermission.CONNECT)
                else -> throw IllegalArgumentException(
                    "Unsupported Bluetooth permission capability: $capability"
                )
            }
        }.distinct().toTypedArray()
    }

    override fun startAdvertising(options: AdvertisingOptions) {
        if (!ensureBluetoothPermissions(
                "start advertising",
                BluetoothPermission.ADVERTISE,
                BluetoothPermission.CONNECT
            )
        ) {
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
        currentManufacturerCompanyId = options.manufacturerCompanyId
        currentManufacturerDataEntries = options.manufacturerDataEntries
        currentAdvertisingData = normalizeAdvertisingData(
            options.advertisingData,
            options.localName,
            options.manufacturerData,
            options.manufacturerCompanyId,
            options.manufacturerDataEntries
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
                // Keep the request mode and timeout the services were set up with.
                setServices(
                    configuredServices,
                    PeripheralRequestOptions(
                        peripheralRequestMode,
                        peripheralRequestTimeoutMs.toDouble()
                    )
                )
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
            currentManufacturerData,
            currentManufacturerCompanyId,
            currentManufacturerDataEntries
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
        currentAdvertisingData = null
        currentServiceUUIDs = emptyArray()
        currentLocalName = null
        currentManufacturerData = null
        currentManufacturerCompanyId = null
        currentManufacturerDataEntries = null
        restoreAdapterName()
    }

    override fun setServices(
        services: Array<GATTService>,
        requestOptions: PeripheralRequestOptions
    ) {
        if (!ensureBluetoothPermissions("set GATT services", BluetoothPermission.CONNECT)) {
            return
        }

        ensureBluetoothManager()
        gattServerReady = false
        peripheralRequestMode = requestOptions?.mode ?: PeripheralRequestMode.AUTOMATIC
        peripheralRequestTimeoutMs = (requestOptions?.timeoutMs?.toLong()
            ?: DEFAULT_PERIPHERAL_REQUEST_TIMEOUT_MS).coerceIn(100L, 30_000L)
        rejectAllPeripheralRequests(IllegalStateException("GATT services were replaced"))
        preparedWrites.clear()

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
                    characteristicPermissionsFromArray(
                        characteristicData.permissions,
                        characteristicData.properties,
                        characteristicData.uuid
                    )
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

        configuredServices = services
        pendingServicePublications.clear()
        pendingServicePublications.addAll(nativeServices.values)
        publishNextGattService()
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

    override fun respondToPeripheralReadRequest(
        requestId: String,
        value: String,
        useStoredValue: Boolean,
        status: PeripheralRequestStatus
    ): Promise<Unit> {
        val value: String? = if (useStoredValue) null else value
        val request = pendingPeripheralRequests.remove(requestId) as? PendingPeripheralRequest.Read
            ?: return Promise.rejected(IllegalArgumentException("Peripheral read request is unknown or expired"))
        request.timeout.cancel()
        val result = peripheralStatusToGatt(status ?: PeripheralRequestStatus.SUCCESS)
        val payload = if (result == BluetoothGatt.GATT_SUCCESS) {
            val fullValue = if (value != null) {
                hexStringToByteArray(value)
                    ?: return Promise.rejected(IllegalArgumentException("Value must be an even-length hex string"))
            } else {
                getCharacteristicValue(request.characteristic) ?: byteArrayOf()
            }
            if (request.offset > fullValue.size) {
                gattServer?.sendResponse(
                    request.device,
                    request.nativeRequestId,
                    BluetoothGatt.GATT_INVALID_OFFSET,
                    request.offset,
                    null
                )
                return Promise.resolved(Unit)
            }
            fullValue.copyOfRange(request.offset, fullValue.size)
        } else {
            null
        }
        gattServer?.sendResponse(
            request.device,
            request.nativeRequestId,
            result,
            request.offset,
            payload
        )
        return Promise.resolved(Unit)
    }

    override fun respondToPeripheralWriteRequest(
        requestId: String,
        accept: Boolean,
        status: PeripheralRequestStatus
    ): Promise<Unit> {
        val request = pendingPeripheralRequests.remove(requestId) as? PendingPeripheralRequest.Write
            ?: return Promise.rejected(IllegalArgumentException("Peripheral write request is unknown or expired"))
        request.timeout.cancel()
        val result = if (accept) {
            peripheralStatusToGatt(status ?: PeripheralRequestStatus.SUCCESS)
        } else {
            peripheralStatusToGatt(status ?: PeripheralRequestStatus.WRITENOTPERMITTED)
        }
        if (result == BluetoothGatt.GATT_SUCCESS) {
            if (request.preparedWrite) {
                preparedWrites.getOrPut(request.device.address) { java.util.Collections.synchronizedList(mutableListOf()) }.add(
                    PreparedWriteFragment(
                        request.characteristic,
                        request.offset,
                        request.value,
                        requestId
                    )
                )
            } else {
                applyPeripheralWrite(request.characteristic, request.offset, request.value)
            }
        }
        if (request.responseNeeded) {
            gattServer?.sendResponse(
                request.device,
                request.nativeRequestId,
                result,
                request.offset,
                if (result == BluetoothGatt.GATT_SUCCESS) request.value else null
            )
        }
        return Promise.resolved(Unit)
    }

    override fun respondToPeripheralExecuteWriteRequest(
        requestId: String,
        accept: Boolean
    ): Promise<Unit> {
        val request = pendingPeripheralRequests.remove(requestId) as? PendingPeripheralRequest.Execute
            ?: return Promise.rejected(IllegalArgumentException("Peripheral execute-write request is unknown or expired"))
        request.timeout.cancel()
        if (accept) {
            request.fragments.forEach { fragment ->
                applyPeripheralWrite(fragment.characteristic, fragment.offset, fragment.value)
            }
        }
        gattServer?.sendResponse(
            request.device,
            request.nativeRequestId,
            BluetoothGatt.GATT_SUCCESS,
            0,
            null
        )
        return Promise.resolved(Unit)
    }

    override fun isBluetoothEnabled(): Promise<Boolean> {
        if (!hasRequiredBluetoothPermissions(BluetoothPermission.CONNECT)) {
            return Promise.resolved(false)
        }

        ensureBluetoothManager()
        return Promise.resolved(bluetoothAdapter?.isEnabled == true)
    }

    override fun requestEnable(): Promise<Boolean> {
        ensureBluetoothManager()
        val adapter = bluetoothAdapter
            ?: return unsupportedPromise("Bluetooth is not available on this device")
        val isEnabled = try {
            adapter.isEnabled
        } catch (error: SecurityException) {
            false
        }
        if (isEnabled) {
            return Promise.resolved(true)
        }
        if (!hasRequiredBluetoothPermissions(BluetoothPermission.CONNECT)) {
            return Promise.rejected(
                SecurityException("BLUETOOTH_CONNECT is required to request enabling Bluetooth; call requestBluetoothPermission(['connect']) first")
            )
        }
        val context = NitroModules.applicationContext
            ?: return Promise.rejected(IllegalStateException("React context unavailable"))
        val activity = context.currentActivity
            ?: return Promise.rejected(IllegalStateException("requestEnable() needs a foreground Activity"))

        synchronized(this) {
            if (pendingEnableRequest != null) {
                return Promise.rejected(IllegalStateException("An enable request is already showing"))
            }
            val promise = Promise<Boolean>()
            pendingEnableRequest = promise
            val listener = object : BaseActivityEventListener() {
                override fun onActivityResult(
                    activity: Activity,
                    requestCode: Int,
                    resultCode: Int,
                    data: Intent?
                ) {
                    if (requestCode != REQUEST_ENABLE_BLUETOOTH_CODE) return
                    context.removeActivityEventListener(this)
                    val pending = synchronized(this@HybridMunimBluetooth) {
                        pendingEnableRequest.also { pendingEnableRequest = null }
                    }
                    pending?.resolve(resultCode == Activity.RESULT_OK)
                }
            }
            context.addActivityEventListener(listener)
            UiThreadUtil.runOnUiThread {
                try {
                    activity.startActivityForResult(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                        REQUEST_ENABLE_BLUETOOTH_CODE
                    )
                } catch (error: RuntimeException) {
                    // ActivityNotFoundException / SecurityException.
                    context.removeActivityEventListener(listener)
                    val pending = synchronized(this@HybridMunimBluetooth) {
                        pendingEnableRequest.also { pendingEnableRequest = null }
                    }
                    pending?.reject(error)
                }
            }
            return promise
        }
    }

    override fun requestBluetoothPermission(permissions: Array<String>?): Promise<Boolean> {
        val context = NitroModules.applicationContext ?: run {
            Log.w(TAG, "Unable to request Bluetooth permissions: React context unavailable")
            return Promise.resolved(false)
        }

        val requestedPermissions = try {
            permissionsForRequest(permissions)
        } catch (error: IllegalArgumentException) {
            return Promise.rejected(error)
        }
        val missingPermissions = BluetoothPermissionUtils.missingPermissions(
            context,
            *requestedPermissions
        )
        if (missingPermissions.isEmpty()) {
            return Promise.resolved(true)
        }

        val requestCode = nextPermissionRequestCode++
        val promise = Promise<Boolean>()
        requestPermissionsWhenActivityReady(missingPermissions, requestCode, promise, attempt = 0)
        return promise
    }

    /**
     * The React context has no current Activity for a short window after
     * launch, which is exactly when apps tend to ask for permissions. Retry on
     * the main thread for a little while before giving up.
     */
    private fun requestPermissionsWhenActivityReady(
        missingPermissions: Array<String>,
        requestCode: Int,
        promise: Promise<Boolean>,
        attempt: Int
    ) {
        val context = NitroModules.applicationContext
        val activity = context?.currentActivity as? PermissionAwareActivity
        if (activity == null) {
            if (attempt >= PERMISSION_ACTIVITY_RETRIES) {
                Log.w(TAG, "Unable to request Bluetooth permissions: current activity unavailable")
                promise.resolve(false)
                return
            }
            bluetoothScope.launch {
                delay(PERMISSION_ACTIVITY_RETRY_DELAY_MS)
                requestPermissionsWhenActivityReady(missingPermissions, requestCode, promise, attempt + 1)
            }
            return
        }

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

    override fun startScan(options: ScanOptions) {
        if (!ensureBluetoothPermissions(
                "start scanning",
                BluetoothPermission.SCAN,
                BluetoothPermission.CONNECT
            )
        ) {
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

        val scanFilters = buildScanFilters(options)
        val scanSettings = buildScanSettings(options)

        val retryAfterMs = scanThrottleRetryAfterMs()
        if (retryAfterMs > 0) {
            Log.w(TAG, "Not starting scan: Android allows 5 scan starts per 30 s (retry in ${retryAfterMs} ms)")
            eventEmitter.emit(
                "scanFailed",
                mapOf(
                    "errorCode" to ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY,
                    "message" to "Scanning too frequently: Android allows 5 scan starts per 30 seconds; the platform would silently ignore this one",
                    "retryAfterMs" to retryAfterMs
                )
            )
            return
        }

        isScanning = true
        scanAllowDuplicates = options?.allowDuplicates ?: false
        scanRssiThreshold = options?.rssiThreshold
        scanNamePrefix = options?.namePrefix?.takeIf { it.isNotEmpty() }
        discoveredDevices.clear()
        bluetoothLeScanner = scanner

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
                    discoveredDevices.remove(result.device.address)
                    eventEmitter.emit(
                        "deviceLost",
                        mapOf("id" to result.device.address, "rssi" to result.rssi)
                    )
                    return
                }
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    handleScanResult(result)
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

        recordScanStart()
        scanner.startScan(scanFilters, scanSettings, scanCallback)
    }

    private fun scanThrottleRetryAfterMs(): Long {
        val now = SystemClock.elapsedRealtime()
        synchronized(recentScanStarts) {
            while (recentScanStarts.isNotEmpty() && now - recentScanStarts.first() >= SCAN_THROTTLE_WINDOW_MS) {
                recentScanStarts.removeFirst()
            }
            if (recentScanStarts.size < SCAN_THROTTLE_MAX_STARTS) return 0
            return (SCAN_THROTTLE_WINDOW_MS - (now - recentScanStarts.first())).coerceAtLeast(1)
        }
    }

    private fun recordScanStart() {
        synchronized(recentScanStarts) {
            recentScanStarts.addLast(SystemClock.elapsedRealtime())
        }
    }

    /**
     * Android ORs the filters in the list and ANDs the criteria inside one
     * filter, so each service UUID gets its own filter carrying the shared
     * name/address/manufacturer criteria.
     */
    private fun buildScanFilters(options: ScanOptions?): List<ScanFilter> {
        options ?: return emptyList()
        try {
            val manufacturerId = options.manufacturerId?.let { id ->
                require(id.isFinite() && id >= 0 && id <= 0xFFFF && id % 1.0 == 0.0) {
                    "manufacturerId must be an integer between 0 and 65535"
                }
                id.toInt()
            }
            val manufacturerData = options.manufacturerData?.takeIf { it.isNotEmpty() }?.let {
                hexStringToByteArray(it) ?: throw IllegalArgumentException("manufacturerData must be an even-length hex string")
            }
            val manufacturerMask = options.manufacturerDataMask?.takeIf { it.isNotEmpty() }?.let {
                hexStringToByteArray(it) ?: throw IllegalArgumentException("manufacturerDataMask must be an even-length hex string")
            }
            require(manufacturerId != null || (manufacturerData == null && manufacturerMask == null)) {
                "manufacturerData/manufacturerDataMask require manufacturerId"
            }
            require(manufacturerMask == null || manufacturerMask.size == manufacturerData?.size) {
                "manufacturerDataMask must be the same length as manufacturerData"
            }
            val deviceName = options.deviceName?.takeIf { it.isNotEmpty() }
            val deviceAddress = options.deviceAddress?.takeIf { it.isNotEmpty() }?.uppercase()
            val serviceUUIDs = options.serviceUUIDs?.takeIf { it.isNotEmpty() }?.toList()

            val hasSharedCriteria = manufacturerId != null || deviceName != null || deviceAddress != null
            if (serviceUUIDs == null && !hasSharedCriteria) return emptyList()

            fun filter(serviceUUID: String?): ScanFilter {
                val builder = ScanFilter.Builder()
                serviceUUID?.let { builder.setServiceUuid(ParcelUuid.fromString(it)) }
                deviceName?.let { builder.setDeviceName(it) }
                deviceAddress?.let { builder.setDeviceAddress(it) }
                if (manufacturerId != null) {
                    // An empty data array matches any payload for the id.
                    val data = manufacturerData ?: byteArrayOf()
                    if (manufacturerMask != null) {
                        builder.setManufacturerData(manufacturerId, data, manufacturerMask)
                    } else {
                        builder.setManufacturerData(manufacturerId, data)
                    }
                }
                return builder.build()
            }

            return serviceUUIDs?.map { filter(it) } ?: listOf(filter(null))
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid scan filter: ${error.message}", error)
        }
    }

    private fun buildScanSettings(options: ScanOptions?): ScanSettings {
        val builder = ScanSettings.Builder()
            .setScanMode(
                when (options?.scanMode) {
                    ScanMode.LOWPOWER -> ScanSettings.SCAN_MODE_LOW_POWER
                    ScanMode.LOWLATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
                    else -> ScanSettings.SCAN_MODE_BALANCED
                }
            )
        options?.reportDelayMs?.let { delayMs ->
            require(delayMs >= 0) { "reportDelayMs must be >= 0" }
            builder.setReportDelay(delayMs.toLong())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            options?.callbackType?.let { callbackType ->
                builder.setCallbackType(
                    when (callbackType) {
                        ScanCallbackType.ALLMATCHES -> ScanSettings.CALLBACK_TYPE_ALL_MATCHES
                        ScanCallbackType.FIRSTMATCH -> ScanSettings.CALLBACK_TYPE_FIRST_MATCH
                        ScanCallbackType.MATCHLOST -> ScanSettings.CALLBACK_TYPE_MATCH_LOST
                    }
                )
            }
            options?.matchMode?.let { matchMode ->
                builder.setMatchMode(
                    when (matchMode) {
                        ScanMatchMode.AGGRESSIVE -> ScanSettings.MATCH_MODE_AGGRESSIVE
                        ScanMatchMode.STICKY -> ScanSettings.MATCH_MODE_STICKY
                    }
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            options?.legacy?.let { builder.setLegacy(it) }
            options?.phy?.let { phy ->
                builder.setPhy(
                    when (phy) {
                        ScanPhy.LE1M -> BluetoothDevice.PHY_LE_1M
                        ScanPhy.LECODED -> BluetoothDevice.PHY_LE_CODED
                        ScanPhy.ALLSUPPORTED -> ScanSettings.PHY_LE_ALL_SUPPORTED
                    }
                )
            }
        }
        return builder.build()
    }

    override fun stopScan() {
        if (!isScanning) return
        scanCallback?.let { callback ->
            bluetoothLeScanner?.stopScan(callback)
        }
        bluetoothLeScanner = null
        scanCallback = null
        isScanning = false
        scanAllowDuplicates = false
        scanRssiThreshold = null
        scanNamePrefix = null
    }

    private fun handleScanResult(result: ScanResult) {
        if (!passesScanFilters(result)) return
        val device = result.device
        val wasKnown = discoveredDevices.containsKey(device.address)
        discoveredDevices[device.address] = device
        if (scanAllowDuplicates || !wasKnown) {
            emitDeviceFound(buildScanPayload(result))
        }
    }

    private fun passesScanFilters(result: ScanResult): Boolean {
        scanRssiThreshold?.let { threshold ->
            if (result.rssi < threshold) return false
        }
        scanNamePrefix?.let { prefix ->
            val name = result.scanRecord?.deviceName
                ?: try {
                    result.device.name
                } catch (_: SecurityException) {
                    null
                }
            if (name?.startsWith(prefix) != true) return false
        }
        return true
    }

    override fun connect(deviceId: String, options: ConnectOptions): Promise<Unit> {
        if (!ensureBluetoothPermissions("connect to BLE device", BluetoothPermission.CONNECT)) {
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

        val autoConnect = options?.autoConnect ?: false
        // A background (autoConnect) connection waits for the device
        // indefinitely by design, so it only times out when asked to.
        val timeoutMs = options?.timeoutMs?.toLong()
            ?: if (autoConnect) 0L else CONNECTION_TIMEOUT_MS

        val promise = Promise<Unit>()
        pendingConnections[deviceId] = promise
        pendingConnectionAttempts[deviceId] = 0
        pendingConnectionAutoConnect[deviceId] = autoConnect
        if (timeoutMs > 0) {
            scheduleConnectionTimeout(deviceId, timeoutMs)
        } else {
            pendingConnectionTimeouts.remove(deviceId)?.cancel()
        }
        startGattConnection(deviceId, device)
        return promise
    }

    override fun disconnect(deviceId: String) {
        pendingConnectionTimeouts.remove(deviceId)?.cancel()
        pendingConnectionAttempts.remove(deviceId)
        pendingConnectionAutoConnect.remove(deviceId)
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

        if (gatt.services.isNotEmpty() && !devicesNeedingServiceRediscovery.contains(deviceId)) {
            return Promise.resolved(buildGattServices(gatt))
        }

        val promise = Promise<Array<GATTService>>()
        enqueueGattOperation(
            deviceId,
            "discoverServices",
            deviceId,
            start = {
                pendingServiceDiscoveries[deviceId] = promise
                gatt.discoverServices()
            },
            reject = { error ->
                pendingServiceDiscoveries.remove(deviceId)
                promise.reject(error)
            }
        )
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
        enqueueGattOperation(
            deviceId,
            "readCharacteristic",
            key,
            start = {
                pendingReads[key] = promise
                gatt.readCharacteristic(characteristic)
            },
            reject = { error ->
                pendingReads.remove(key)
                promise.reject(error)
            }
        )
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
        enqueueGattOperation(
            deviceId,
            "readDescriptor",
            key,
            start = {
                pendingDescriptorReads[key] = promise
                gatt.readDescriptor(descriptor)
            },
            reject = { error ->
                pendingDescriptorReads.remove(key)
                promise.reject(error)
            }
        )
        return promise
    }

    override fun writeCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String,
        value: String,
        writeType: WriteType
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
        if (resolvedWriteType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            enqueueGattOperation(
                deviceId,
                "writeWithoutResponse",
                key,
                start = {
                    // The operation stays active until onCharacteristicWrite
                    // (Android reports no-response writes there once the
                    // stack has taken the packet), which is the flow-control
                    // signal: starting the next write earlier returns BUSY.
                    val operation = activeGattOperations[deviceId]
                    startCharacteristicWriteWithRetry(
                        deviceId, "writeWithoutResponse", key, gatt, characteristic, data, resolvedWriteType, 0
                    ) {
                        promise.resolve(Unit)
                        scheduleWriteWithoutResponseFallback(deviceId, operation)
                    }
                },
                reject = promise::reject
            )
        } else {
            enqueueGattOperation(
                deviceId,
                "writeCharacteristic",
                key,
                start = {
                    pendingWrites[key] = promise
                    startCharacteristicWriteWithRetry(
                        deviceId, "writeCharacteristic", key, gatt, characteristic, data, resolvedWriteType, 0
                    ) {}
                },
                reject = { error ->
                    pendingWrites.remove(key)
                    promise.reject(error)
                }
            )
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
        enqueueGattOperation(
            deviceId,
            "writeDescriptor",
            key,
            start = {
                pendingDescriptorWrites[key] = promise
                writeGattDescriptor(gatt, descriptor, data)
            },
            reject = { error ->
                pendingDescriptorWrites.remove(key)
                promise.reject(error)
            }
        )
        return promise
    }

    override fun subscribeToCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ): Promise<Unit> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))
        val characteristic = findCharacteristic(gatt, serviceUUID, characteristicUUID)
            ?: return Promise.rejected(IllegalArgumentException("Characteristic not found"))
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            ?: return Promise.rejected(IllegalArgumentException("Client configuration descriptor not found"))
        val subscriptionValue = when {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> return Promise.rejected(IllegalArgumentException("Characteristic does not support notify or indicate"))
        }
        val promise = Promise<Unit>()
        val key = descriptorKey(deviceId, serviceUUID, characteristicUUID, descriptor.uuid.toString())
        enqueueGattOperation(
            deviceId,
            "subscribe",
            key,
            start = {
                pendingDescriptorWrites[key] = promise
                if (!gatt.setCharacteristicNotification(characteristic, true)) {
                    false
                } else {
                    writeGattDescriptor(gatt, descriptor, subscriptionValue)
                }
            },
            reject = { error ->
                pendingDescriptorWrites.remove(key)
                gatt.setCharacteristicNotification(characteristic, false)
                promise.reject(error)
            }
        )
        return promise
    }

    override fun unsubscribeFromCharacteristic(
        deviceId: String,
        serviceUUID: String,
        characteristicUUID: String
    ): Promise<Unit> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))
        val characteristic = findCharacteristic(gatt, serviceUUID, characteristicUUID)
            ?: return Promise.rejected(IllegalArgumentException("Characteristic not found"))
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            ?: return Promise.rejected(IllegalArgumentException("Client configuration descriptor not found"))
        val promise = Promise<Unit>()
        val key = descriptorKey(deviceId, serviceUUID, characteristicUUID, descriptor.uuid.toString())
        enqueueGattOperation(
            deviceId,
            "unsubscribe",
            key,
            start = {
                pendingDescriptorWrites[key] = promise
                writeGattDescriptor(
                    gatt,
                    descriptor,
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                )
            },
            reject = { error ->
                pendingDescriptorWrites.remove(key)
                promise.reject(error)
            }
        )
        return promise
    }

    override fun refreshGattCache(deviceId: String): Promise<Boolean> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        val promise = Promise<Boolean>()
        enqueueGattOperation(
            deviceId,
            "refreshGattCache",
            deviceId,
            start = {
                val operation = activeGattOperations[deviceId]
                val refreshed = try {
                    // Hidden API (greylisted). It clears the cached attribute
                    // database; services must be rediscovered afterwards.
                    gatt.javaClass.getMethod("refresh").invoke(gatt) as? Boolean ?: false
                } catch (error: ReflectiveOperationException) {
                    Log.w(TAG, "BluetoothGatt.refresh() is unavailable", error)
                    false
                } catch (error: SecurityException) {
                    Log.w(TAG, "BluetoothGatt.refresh() was denied", error)
                    false
                }
                if (refreshed) {
                    lastCharacteristicValues.keys.removeIf { it.startsWith("$deviceId|") }
                    devicesNeedingServiceRediscovery.add(deviceId)
                }
                promise.resolve(refreshed)
                // refresh() is asynchronous inside the stack; hold the queue
                // briefly so the next operation does not race it.
                if (operation != null) {
                    bluetoothScope.launch {
                        delay(GATT_REFRESH_SETTLE_MS)
                        completeSpecificGattOperation(deviceId, operation)
                    }
                }
                true
            },
            reject = promise::reject
        )
        return promise
    }

    private fun handleServicesChanged(deviceId: String) {
        lastCharacteristicValues.keys.removeIf { it.startsWith("$deviceId|") }
        devicesNeedingServiceRediscovery.add(deviceId)
        Log.i(TAG, "Remote GATT services changed for $deviceId")
        eventEmitter.emit("servicesChanged", mapOf("deviceId" to deviceId))
    }

    // The per-device ArrayDeques are only safe under the same monitor as
    // enqueue/startNext/complete (all @Synchronized on this instance); an
    // unsynchronized size() read could observe a deque mid-mutation.
    @Synchronized
    override fun getGattQueueDiagnostics(): Promise<Array<GATTQueueDiagnostic>> {
        val now = System.currentTimeMillis()
        val deviceIds = (gattOperationQueues.keys + activeGattOperations.keys).toSortedSet()
        return Promise.resolved(deviceIds.map { deviceId ->
            val active = activeGattOperations[deviceId]
            GATTQueueDiagnostic(
                deviceId = deviceId,
                activeOperation = active?.kind,
                activeTarget = active?.target,
                queuedOperations = (gattOperationQueues[deviceId]?.size ?: 0).toDouble(),
                activeDurationMs = active?.startedAtMs?.let { (now - it).coerceAtLeast(0).toDouble() }
            )
        }.toTypedArray())
    }

    override fun getConnectedDevices(): Promise<Array<String>> {
        return Promise.resolved(connectedDevices.keys.toTypedArray())
    }

    override fun readRSSI(deviceId: String): Promise<Double> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        val promise = Promise<Double>()
        enqueueGattOperation(
            deviceId,
            "readRSSI",
            deviceId,
            start = {
                pendingRssiReads[deviceId] = promise
                gatt.readRemoteRssi()
            },
            reject = { error ->
                pendingRssiReads.remove(deviceId)
                promise.reject(error)
            }
        )
        return promise
    }

    override fun requestMTU(deviceId: String, mtu: Double): Promise<Double> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        val requestedMtu = mtu.toInt().coerceIn(23, 517)
        val promise = Promise<Double>()
        enqueueGattOperation(
            deviceId,
            "requestMTU",
            deviceId,
            start = {
                pendingMtuRequests[deviceId] = promise
                gatt.requestMtu(requestedMtu)
            },
            reject = { error ->
                pendingMtuRequests.remove(deviceId)
                promise.reject(error)
            }
        )
        return promise
    }

    override fun getMaximumWriteLength(deviceId: String, type: WriteLengthType): Promise<Double> {
        if (!connectedDevices.containsKey(deviceId)) {
            return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))
        }
        return Promise.resolved(maximumWriteLength(deviceId, type).toDouble())
    }

    private fun maximumWriteLength(deviceId: String, type: WriteLengthType): Int {
        return when (type) {
            // A no-response write is a single ATT packet: MTU minus the
            // 3-byte opcode/handle header. Android only reports the MTU via
            // onMtuChanged, so before any exchange this is the 23-byte
            // default; call requestMTU() first for an accurate value.
            WriteLengthType.WITHOUTRESPONSE -> (negotiatedMtus[deviceId] ?: DEFAULT_ATT_MTU) - 3
            // With-response writes above MTU-3 become a long (prepared) write,
            // bounded by the 512-byte maximum attribute value length.
            WriteLengthType.WITHRESPONSE -> MAX_ATTRIBUTE_VALUE_LENGTH
        }
    }

    override fun requestConnectionPriority(
        deviceId: String,
        priority: ConnectionPriority
    ): Promise<Boolean> {
        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))
        val nativePriority = when (priority) {
            ConnectionPriority.HIGH -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
            ConnectionPriority.LOWPOWER -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
            ConnectionPriority.BALANCED -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
        }
        return try {
            Promise.resolved(gatt.requestConnectionPriority(nativePriority))
        } catch (error: SecurityException) {
            Promise.rejected(error)
        }
    }

    override fun setPreferredPhy(
        deviceId: String,
        txPhy: BluetoothPhy,
        rxPhy: BluetoothPhy,
        phyOption: BluetoothPhyOption
    ): Promise<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return unsupportedPromise("BLE PHY selection requires Android 8.0 or newer")
        }

        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        val promise = Promise<Unit>()
        enqueueGattOperation(
            deviceId,
            "setPreferredPhy",
            deviceId,
            start = {
                pendingPhyWrites[deviceId] = promise
                gatt.setPreferredPhy(
                    phyToMask(txPhy),
                    phyToMask(rxPhy),
                    phyOptionToConstant(phyOption ?: BluetoothPhyOption.NONE)
                )
                true
            },
            reject = { error ->
                pendingPhyWrites.remove(deviceId)
                promise.reject(error)
            }
        )
        return promise
    }

    override fun readPhy(deviceId: String): Promise<PhyStatus> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return unsupportedPromise("BLE PHY reads require Android 8.0 or newer")
        }

        val gatt = connectedDevices[deviceId]
            ?: return Promise.rejected(IllegalStateException("Device not connected: $deviceId"))

        val promise = Promise<PhyStatus>()
        enqueueGattOperation(
            deviceId,
            "readPhy",
            deviceId,
            start = {
                pendingPhyReads[deviceId] = promise
                gatt.readPhy()
                true
            },
            reject = { error ->
                pendingPhyReads.remove(deviceId)
                promise.reject(error)
            }
        )
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
        pendingBondPromises[deviceId]?.let {
            return Promise.rejected(IllegalStateException("Bonding is already in progress for $deviceId"))
        }

        ensureBondStateReceiver()

        val promise = Promise<BondState>()
        pendingBondPromises[deviceId] = promise
        pendingBondTimeouts[deviceId] = bluetoothScope.launch {
            delay(BOND_TIMEOUT_MS)
            pendingBondTimeouts.remove(deviceId)
            pendingBondPromises.remove(deviceId)?.reject(
                IllegalStateException("Bonding timed out for $deviceId")
            )
        }

        val started = try {
            device.createBond()
        } catch (error: SecurityException) {
            pendingBondTimeouts.remove(deviceId)?.cancel()
            pendingBondPromises.remove(deviceId)
            return Promise.rejected(error)
        }
        if (!started) {
            pendingBondTimeouts.remove(deviceId)?.cancel()
            pendingBondPromises.remove(deviceId)
            return Promise.rejected(IllegalStateException("Failed to start bond creation for $deviceId"))
        }
        return promise
    }

    private fun ensureBondStateReceiver() {
        if (bondStateReceiver != null) return
        val context = NitroModules.applicationContext ?: return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val device = getBluetoothDeviceExtra(intent) ?: return
                val deviceId = device.address
                val bondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )
                val previousBondState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )

                eventEmitter.emit(
                    "bondStateChanged",
                    mapOf(
                        "deviceId" to deviceId,
                        "bondState" to nativeBondStateToState(bondState).name.lowercase(),
                        "previousBondState" to nativeBondStateToState(previousBondState).name.lowercase()
                    )
                )

                when (bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        pendingBondTimeouts.remove(deviceId)?.cancel()
                        pendingBondPromises.remove(deviceId)?.resolve(BondState.BONDED)
                    }

                    BluetoothDevice.BOND_NONE -> {
                        pendingBondTimeouts.remove(deviceId)?.cancel()
                        pendingBondPromises.remove(deviceId)?.reject(
                            IllegalStateException("Bonding failed for $deviceId")
                        )
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        // Must stay RECEIVER_EXPORTED: Bluetooth broadcasts are sent by the
        // privileged Bluetooth app, not the system UID, and Android documents
        // that RECEIVER_NOT_EXPORTED receivers do not get broadcasts from
        // "highly privileged apps, such as Bluetooth". The action is protected,
        // so other apps cannot spoof it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        bondStateReceiver = receiver
    }

    private fun nativeBondStateToState(bondState: Int): BondState {
        return when (bondState) {
            BluetoothDevice.BOND_BONDING -> BondState.BONDING
            BluetoothDevice.BOND_BONDED -> BondState.BONDED
            else -> BondState.NONE
        }
    }

    override fun getBondedDevices(): Promise<Array<BondedDevice>> {
        if (!hasRequiredBluetoothPermissions(BluetoothPermission.CONNECT)) {
            return Promise.rejected(SecurityException("Missing Bluetooth permissions"))
        }
        ensureBluetoothManager()
        val adapter = bluetoothAdapter
            ?: return Promise.rejected(IllegalStateException("Bluetooth adapter unavailable"))
        return try {
            val devices = adapter.bondedDevices.orEmpty().map { device ->
                BondedDevice(
                    id = device.address,
                    name = device.name,
                    type = when (device.type) {
                        BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceType.CLASSIC
                        BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceType.LE
                        BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceType.DUAL
                        else -> BluetoothDeviceType.UNKNOWN
                    }
                )
            }.sortedBy { it.id }
            Promise.resolved(devices.toTypedArray())
        } catch (error: SecurityException) {
            Promise.rejected(error)
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
        if (!ensureBluetoothPermissions(
                "start extended advertising",
                BluetoothPermission.ADVERTISE,
                BluetoothPermission.CONNECT
            )
        ) {
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
            options.manufacturerData,
            options.manufacturerCompanyId,
            options.manufacturerDataEntries
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
        if (!hasRequiredBluetoothPermissions(BluetoothPermission.CONNECT)) {
            return Promise.rejected(SecurityException("Missing Bluetooth permissions"))
        }

        ensureBluetoothManager()
        val adapter = bluetoothAdapter
            ?: return Promise.rejected(IllegalStateException("Bluetooth adapter is unavailable"))
        val promise = Promise<L2CAPChannel>()

        bluetoothScope.launch(Dispatchers.IO) {
            try {
                val serverSocket = if (encryptionRequired != false) {
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

    override fun openL2CAPChannel(deviceId: String, psm: Double, encryptionRequired: Boolean?): Promise<L2CAPChannel> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return unsupportedPromise("BLE L2CAP channel streams require Android 10 or newer")
        }
        if (!hasRequiredBluetoothPermissions(BluetoothPermission.CONNECT)) {
            return Promise.rejected(SecurityException("Missing Bluetooth permissions"))
        }

        val device = resolveBluetoothDevice(deviceId)
            ?: return Promise.rejected(IllegalArgumentException("Device $deviceId was not found"))
        val promise = Promise<L2CAPChannel>()

        bluetoothScope.launch(Dispatchers.IO) {
            try {
                val socket = if (encryptionRequired != false) {
                    device.createL2capChannel(psm.toInt())
                } else {
                    device.createInsecureL2capChannel(psm.toInt())
                }
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
        if (!ensureBluetoothPermissions(
                "start Classic Bluetooth discovery",
                BluetoothPermission.SCAN,
                BluetoothPermission.CONNECT
            )
        ) {
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
                            eventEmitter.emit("classicDeviceFound", classicDevicePayload(device, intent))
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
        if (!hasRequiredBluetoothPermissions(BluetoothPermission.CONNECT)) {
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
        if (!hasRequiredBluetoothPermissions(BluetoothPermission.CONNECT)) {
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

        if (!ensureBluetoothPermissions(
                "start background BLE session",
                BluetoothPermission.SCAN,
                BluetoothPermission.CONNECT,
                BluetoothPermission.ADVERTISE
            )
        ) {
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

    override fun acceptMultipeerInvitation(invitationId: String) {
        throw UnsupportedOperationException(MULTIPEER_UNSUPPORTED_MESSAGE)
    }

    override fun rejectMultipeerInvitation(invitationId: String) {
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
        if (!ensureBluetoothPermissions(
                "restart advertising",
                BluetoothPermission.ADVERTISE,
                BluetoothPermission.CONNECT
            )
        ) {
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
                        characteristic.permissions?.let { permissions ->
                            characteristicJson.put(
                                "permissions",
                                stringArrayJson(permissions.map(::characteristicPermissionToString).toTypedArray())
                            )
                        }
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
            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Failed to publish GATT service ${service.uuid} (status=$status)")
                    pendingServicePublications.clear()
                    gattServerReady = false
                    return
                }
                publishNextGattService()
            }

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    preparedWrites.remove(device.address)
                    rejectPeripheralRequestsForDevice(device.address)
                }
            }

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
                val opaqueRequestId = UUID.randomUUID().toString()
                if (peripheralRequestMode == PeripheralRequestMode.AUTOMATIC) {
                    if (offset > value.size) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_INVALID_OFFSET,
                            offset,
                            null
                        )
                    } else {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            offset,
                            value.copyOfRange(offset, value.size)
                        )
                    }
                } else {
                    val timeout = schedulePeripheralRequestTimeout(opaqueRequestId) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            GATT_UNLIKELY_ERROR,
                            offset,
                            null
                        )
                    }
                    pendingPeripheralRequests[opaqueRequestId] = PendingPeripheralRequest.Read(
                        device,
                        requestId,
                        offset,
                        characteristic,
                        timeout
                    )
                }
                eventEmitter.emit(
                    "peripheralReadRequest",
                    mapOf(
                        "requestId" to opaqueRequestId,
                        "centralId" to device.address,
                        "serviceUUID" to characteristic.service.uuid.toString(),
                        "characteristicUUID" to characteristic.uuid.toString(),
                        "value" to value.toHexString(),
                        "offset" to offset,
                        "responseRequired" to (peripheralRequestMode == PeripheralRequestMode.MANUAL)
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
                val opaqueRequestId = UUID.randomUUID().toString()
                if (peripheralRequestMode == PeripheralRequestMode.AUTOMATIC) {
                    if (preparedWrite) {
                        preparedWrites.getOrPut(device.address) { java.util.Collections.synchronizedList(mutableListOf()) }.add(
                            PreparedWriteFragment(
                                characteristic,
                                offset,
                                incomingValue,
                                opaqueRequestId
                            )
                        )
                    } else {
                        applyPeripheralWrite(characteristic, offset, incomingValue)
                    }
                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            offset,
                            incomingValue
                        )
                    }
                } else {
                    val timeout = schedulePeripheralRequestTimeout(opaqueRequestId) {
                        if (responseNeeded) {
                            gattServer?.sendResponse(
                                device,
                                requestId,
                                GATT_UNLIKELY_ERROR,
                                offset,
                                null
                            )
                        }
                    }
                    pendingPeripheralRequests[opaqueRequestId] = PendingPeripheralRequest.Write(
                        device,
                        requestId,
                        offset,
                        characteristic,
                        incomingValue,
                        preparedWrite,
                        responseNeeded,
                        timeout
                    )
                }
                eventEmitter.emit(
                    "peripheralWriteRequest",
                    mapOf(
                        "requestId" to opaqueRequestId,
                        "centralId" to device.address,
                        "serviceUUID" to characteristic.service.uuid.toString(),
                        "characteristicUUID" to characteristic.uuid.toString(),
                        "value" to incomingValue.toHexString(),
                        "offset" to offset,
                        "preparedWrite" to preparedWrite,
                        "responseRequired" to (peripheralRequestMode == PeripheralRequestMode.MANUAL)
                    )
                )
            }

            override fun onExecuteWrite(
                device: BluetoothDevice,
                requestId: Int,
                execute: Boolean
            ) {
                val fragments = preparedWrites.remove(device.address)?.toList().orEmpty()
                val opaqueRequestId = UUID.randomUUID().toString()
                if (!execute || peripheralRequestMode == PeripheralRequestMode.AUTOMATIC) {
                    if (execute) {
                        fragments.forEach { fragment ->
                            applyPeripheralWrite(
                                fragment.characteristic,
                                fragment.offset,
                                fragment.value
                            )
                        }
                    }
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null
                    )
                } else {
                    val timeout = schedulePeripheralRequestTimeout(opaqueRequestId) {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            GATT_UNLIKELY_ERROR,
                            0,
                            null
                        )
                    }
                    pendingPeripheralRequests[opaqueRequestId] = PendingPeripheralRequest.Execute(
                        device,
                        requestId,
                        fragments,
                        timeout
                    )
                }
                eventEmitter.emit(
                    "peripheralExecuteWriteRequest",
                    mapOf(
                        "requestId" to opaqueRequestId,
                        "centralId" to device.address,
                        "execute" to execute,
                        "preparedRequestIds" to fragments.map { it.requestId }
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
                    subscribedDevices.getOrPut(characteristic.uuid) { ConcurrentHashMap.newKeySet() }.add(device)
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

    private fun publishNextGattService() {
        val service = pendingServicePublications.pollFirst()
        if (service == null) {
            gattServerReady = true
            return
        }
        val server = gattServer
        if (server == null || !server.addService(service)) {
            Log.e(TAG, "Failed to publish GATT service ${service.uuid}")
            pendingServicePublications.clear()
            gattServerReady = false
        }
    }

    private fun schedulePeripheralRequestTimeout(
        requestId: String,
        onTimeout: () -> Unit
    ): Job {
        return bluetoothScope.launch {
            delay(peripheralRequestTimeoutMs)
            if (pendingPeripheralRequests.remove(requestId) != null) {
                onTimeout()
            }
        }
    }

    private fun rejectAllPeripheralRequests(error: Throwable) {
        if (pendingPeripheralRequests.isEmpty()) return
        Log.w(TAG, "Rejecting ${pendingPeripheralRequests.size} pending peripheral request(s)", error)
        val requests = pendingPeripheralRequests.values.toList()
        pendingPeripheralRequests.clear()
        requests.forEach { request ->
            request.timeout.cancel()
            respondUnlikelyError(request)
        }
    }

    private fun rejectPeripheralRequestsForDevice(deviceAddress: String) {
        val requestIds = pendingPeripheralRequests.filterValues { request ->
            when (request) {
                is PendingPeripheralRequest.Read -> request.device.address == deviceAddress
                is PendingPeripheralRequest.Write -> request.device.address == deviceAddress
                is PendingPeripheralRequest.Execute -> request.device.address == deviceAddress
            }
        }.keys.toList()
        requestIds.forEach { requestId ->
            pendingPeripheralRequests.remove(requestId)?.timeout?.cancel()
        }
    }

    private fun respondUnlikelyError(request: PendingPeripheralRequest) {
        when (request) {
            is PendingPeripheralRequest.Read -> gattServer?.sendResponse(
                request.device,
                request.nativeRequestId,
                GATT_UNLIKELY_ERROR,
                request.offset,
                null
            )

            is PendingPeripheralRequest.Write -> if (request.responseNeeded) {
                gattServer?.sendResponse(
                    request.device,
                    request.nativeRequestId,
                    GATT_UNLIKELY_ERROR,
                    request.offset,
                    null
                )
            }

            is PendingPeripheralRequest.Execute -> gattServer?.sendResponse(
                request.device,
                request.nativeRequestId,
                GATT_UNLIKELY_ERROR,
                0,
                null
            )
        }
    }

    private fun peripheralStatusToGatt(status: PeripheralRequestStatus): Int {
        return when (status) {
            PeripheralRequestStatus.SUCCESS -> BluetoothGatt.GATT_SUCCESS
            PeripheralRequestStatus.INVALIDOFFSET -> BluetoothGatt.GATT_INVALID_OFFSET
            PeripheralRequestStatus.READNOTPERMITTED -> BluetoothGatt.GATT_READ_NOT_PERMITTED
            PeripheralRequestStatus.WRITENOTPERMITTED -> BluetoothGatt.GATT_WRITE_NOT_PERMITTED
            PeripheralRequestStatus.REQUESTNOTSUPPORTED -> BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
            PeripheralRequestStatus.UNLIKELYERROR -> GATT_UNLIKELY_ERROR
        }
    }

    private fun applyPeripheralWrite(
        characteristic: BluetoothGattCharacteristic,
        offset: Int,
        value: ByteArray
    ) {
        val currentValue = getCharacteristicValue(characteristic) ?: byteArrayOf()
        val nextValue = when {
            offset == 0 -> value
            offset >= currentValue.size -> currentValue + value
            else -> {
                val replaceEnd = minOf(offset + value.size, currentValue.size)
                currentValue.copyOfRange(0, offset) +
                    value +
                    currentValue.copyOfRange(replaceEnd, currentValue.size)
            }
        }
        setCharacteristicValue(characteristic, nextValue)
        if (supportsNotifyOrIndicate(characteristic)) {
            notifySubscribedDevices(characteristic)
        }
    }

    private fun createGattCallback(deviceId: String): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED) {
                    // An established link that drops (supervision timeout, remote
                    // disconnect, status 133) also lands here with a non-success
                    // status. Only a link that never came up is a failed connect.
                    val wasConnected = connectedDevices.containsKey(deviceId)
                    if (!wasConnected && retryPendingConnection(deviceId, gatt, status)) {
                        return
                    }
                    pendingConnectionTimeouts.remove(deviceId)?.cancel()
                    pendingConnectionAttempts.remove(deviceId)
                    pendingConnections.remove(deviceId)?.reject(
                        IllegalStateException("Failed to connect to $deviceId (status=$status)")
                    )
                    (pendingConnectionGatts.remove(deviceId) ?: connectedDevices.remove(deviceId))?.close()
                    val reason = if (wasConnected) "remoteOrLinkLoss" else "connectionFailed"
                    if (wasConnected) {
                        rejectPendingOperationsForDevice(
                            deviceId,
                            IllegalStateException("Disconnected from $deviceId (status=$status)")
                        )
                        eventEmitter.emit(
                            "deviceDisconnected",
                            mapOf("deviceId" to deviceId, "status" to status, "reason" to reason)
                        )
                    }
                    eventEmitter.emit(
                        "connectionStateChanged",
                        mapOf(
                            "deviceId" to deviceId,
                            "state" to "disconnected",
                            "status" to status,
                            "reason" to reason
                        )
                    )
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        pendingConnectionTimeouts.remove(deviceId)?.cancel()
                        pendingConnectionAttempts.remove(deviceId)
                        pendingConnectionGatts.remove(deviceId)
                        connectedDevices[deviceId] = gatt
                        pendingConnections.remove(deviceId)?.resolve(Unit)
                        eventEmitter.emit("deviceConnected", mapOf("deviceId" to deviceId, "status" to status))
                        eventEmitter.emit(
                            "connectionStateChanged",
                            mapOf("deviceId" to deviceId, "state" to "connected", "status" to status)
                        )
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
                        eventEmitter.emit(
                            "deviceDisconnected",
                            mapOf("deviceId" to deviceId, "status" to status, "reason" to "remoteOrLinkLoss")
                        )
                        eventEmitter.emit(
                            "connectionStateChanged",
                            mapOf(
                                "deviceId" to deviceId,
                                "state" to "disconnected",
                                "status" to status,
                                "reason" to "remoteOrLinkLoss"
                            )
                        )
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                completeGattOperation(
                    deviceId,
                    setOf("discoverServices"),
                    deviceId,
                    status
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        devicesNeedingServiceRediscovery.remove(deviceId)
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
                completeGattOperation(
                    deviceId,
                    setOf("writeCharacteristic", "writeWithoutResponse"),
                    key,
                    status
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        pendingWrites.remove(key)?.resolve(Unit)
                    } else {
                        pendingWrites.remove(key)?.reject(
                            IllegalStateException("Failed to write characteristic $key (status=$status)")
                        )
                    }
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
                completeGattOperation(
                    deviceId,
                    setOf("writeDescriptor", "subscribe", "unsubscribe"),
                    key,
                    status
                ) { operation ->
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (operation.kind == "unsubscribe") {
                            gatt.setCharacteristicNotification(characteristic, false)
                        }
                        pendingDescriptorWrites.remove(key)?.resolve(Unit)
                    } else {
                        if (operation.kind == "subscribe") {
                            gatt.setCharacteristicNotification(characteristic, false)
                        }
                        pendingDescriptorWrites.remove(key)?.reject(
                            IllegalStateException("Failed to write descriptor $key (status=$status)")
                        )
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                // Also fires for exchanges the stack or the peer initiated.
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    negotiatedMtus[deviceId] = mtu
                }
                completeGattOperation(deviceId, setOf("requestMTU"), deviceId, status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        pendingMtuRequests.remove(deviceId)?.resolve(mtu.toDouble())
                    } else {
                        pendingMtuRequests.remove(deviceId)?.reject(
                            IllegalStateException("Failed to request MTU for $deviceId (status=$status)")
                        )
                    }
                    eventEmitter.emit(
                        "mtuChanged",
                        mapOf("deviceId" to deviceId, "mtu" to mtu, "status" to status)
                    )
                }
            }

            override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

                completeGattOperation(deviceId, setOf("readPhy"), deviceId, status) {
                    val phyStatus = PhyStatus(
                        txPhy = constantToPhy(txPhy),
                        rxPhy = constantToPhy(rxPhy)
                    )
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        pendingPhyReads.remove(deviceId)?.resolve(phyStatus)
                    } else {
                        pendingPhyReads.remove(deviceId)?.reject(
                            IllegalStateException("Failed to read PHY for $deviceId (status=$status)")
                        )
                    }
                    eventEmitter.emit(
                        "phyChanged",
                        mapOf(
                            "deviceId" to deviceId,
                            "txPhy" to phyStatus.txPhy.name.lowercase(),
                            "rxPhy" to phyStatus.rxPhy.name.lowercase(),
                            "status" to status
                        )
                    )
                }
            }

            override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
                completeGattOperation(deviceId, setOf("setPreferredPhy"), deviceId, status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        pendingPhyWrites.remove(deviceId)?.resolve(Unit)
                    } else {
                        pendingPhyWrites.remove(deviceId)?.reject(
                            IllegalStateException("Failed to set PHY for $deviceId (status=$status)")
                        )
                    }
                    eventEmitter.emit(
                        "phyChanged",
                        mapOf(
                            "deviceId" to deviceId,
                            "txPhy" to constantToPhy(txPhy).name.lowercase(),
                            "rxPhy" to constantToPhy(rxPhy).name.lowercase(),
                            "status" to status
                        )
                    )
                }
            }

            // API 31+: the remote sent a Service Changed indication. Older
            // releases handle it inside the stack without telling the app.
            override fun onServiceChanged(gatt: BluetoothGatt) {
                handleServicesChanged(deviceId)
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                completeGattOperation(deviceId, setOf("readRSSI"), deviceId, status) {
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
        completeGattOperation(deviceId, setOf("readCharacteristic"), key, status) {
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
        completeGattOperation(deviceId, setOf("readDescriptor"), key, status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingDescriptorReads.remove(key)?.resolve(buildDescriptorValue(descriptor, valueBytes))
            } else {
                pendingDescriptorReads.remove(key)?.reject(
                    IllegalStateException("Failed to read descriptor $key (status=$status)")
                )
            }
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
        rejectGattOperationsForDevice(deviceId, error)
        pendingReads.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingReads.remove(key) }
        pendingWrites.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingWrites.remove(key) }
        pendingDescriptorReads.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingDescriptorReads.remove(key) }
        pendingDescriptorWrites.keys
            .filter { it.startsWith("$deviceId|") }
            .forEach { key -> pendingDescriptorWrites.remove(key) }
        pendingServiceDiscoveries.remove(deviceId)
        pendingRssiReads.remove(deviceId)
        pendingMtuRequests.remove(deviceId)
        negotiatedMtus.remove(deviceId)
        devicesNeedingServiceRediscovery.remove(deviceId)
        pendingPhyReads.remove(deviceId)
        pendingPhyWrites.remove(deviceId)
    }

    private fun scheduleConnectionTimeout(deviceId: String, timeoutMs: Long) {
        pendingConnectionTimeouts.remove(deviceId)?.cancel()
        pendingConnectionTimeouts[deviceId] = bluetoothScope.launch {
            delay(timeoutMs)
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

        val autoConnect = pendingConnectionAutoConnect[deviceId] ?: false
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, autoConnect, createGattCallback(deviceId), BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, autoConnect, createGattCallback(deviceId))
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

    // Every function that touches gattOperationQueues/activeGattOperations is
    // @Synchronized on this instance, so JS-thread enqueues and binder-thread
    // GATT callbacks serialize on one monitor.
    @Synchronized
    private fun enqueueGattOperation(
        deviceId: String,
        kind: String,
        target: String,
        start: () -> Boolean,
        reject: (Throwable) -> Unit
    ) {
        val operation = QueuedGattOperation(kind, target, start, reject)
        gattOperationQueues.getOrPut(deviceId) { ArrayDeque() }.addLast(operation)
        startNextGattOperation(deviceId)
    }

    @Synchronized
    private fun startNextGattOperation(deviceId: String) {
        if (activeGattOperations.containsKey(deviceId)) return
        val queue = gattOperationQueues[deviceId] ?: return
        val operation = queue.pollFirst() ?: run {
            gattOperationQueues.remove(deviceId)
            return
        }
        if (!connectedDevices.containsKey(deviceId)) {
            operation.reject(IllegalStateException("Device not connected: $deviceId"))
            startNextGattOperation(deviceId)
            return
        }

        operation.startedAtMs = System.currentTimeMillis()
        activeGattOperations[deviceId] = operation
        val started: Boolean
        try {
            started = operation.start()
        } catch (error: Throwable) {
            activeGattOperations.remove(deviceId)
            operation.reject(error)
            emitGattOperationResult(deviceId, operation, null, error.message)
            startNextGattOperation(deviceId)
            return
        }
        if (!started) {
            activeGattOperations.remove(deviceId)
            operation.reject(
                IllegalStateException("Failed to start ${operation.kind} for ${operation.target}")
            )
            startNextGattOperation(deviceId)
            return
        }

        gattOperationTimeouts.remove(deviceId)?.cancel()
        gattOperationTimeouts[deviceId] = bluetoothScope.launch {
            delay(OPERATION_TIMEOUT_MS)
            timeoutGattOperation(deviceId, operation)
        }
    }

    @Synchronized
    private fun timeoutGattOperation(deviceId: String, expected: QueuedGattOperation) {
        if (activeGattOperations[deviceId] !== expected) return
        gattOperationTimeouts.remove(deviceId)
        activeGattOperations.remove(deviceId)
        val error = IllegalStateException(
            "${expected.kind} timed out for ${expected.target}"
        )
        expected.reject(error)
        emitGattOperationResult(deviceId, expected, null, error.message)
        startNextGattOperation(deviceId)
    }

    @Synchronized
    private fun completeGattOperation(
        deviceId: String,
        kinds: Set<String>,
        target: String,
        status: Int?,
        completion: (QueuedGattOperation) -> Unit
    ): Boolean {
        val operation = activeGattOperations[deviceId] ?: return false
        if (operation.kind !in kinds || operation.target != target) return false
        gattOperationTimeouts.remove(deviceId)?.cancel()
        completion(operation)
        activeGattOperations.remove(deviceId)
        emitGattOperationResult(deviceId, operation, status, null)
        startNextGattOperation(deviceId)
        return true
    }

    /**
     * Some stacks never report onCharacteristicWrite for no-response writes.
     * Advance the queue anyway after a short grace period, but only if the
     * same operation instance is still the active one.
     */
    private fun scheduleWriteWithoutResponseFallback(
        deviceId: String,
        operation: QueuedGattOperation?
    ) {
        operation ?: return
        bluetoothScope.launch {
            delay(WRITE_WITHOUT_RESPONSE_FALLBACK_MS)
            completeSpecificGattOperation(deviceId, operation)
        }
    }

    @Synchronized
    private fun completeSpecificGattOperation(deviceId: String, expected: QueuedGattOperation) {
        if (activeGattOperations[deviceId] !== expected) return
        gattOperationTimeouts.remove(deviceId)?.cancel()
        activeGattOperations.remove(deviceId)
        emitGattOperationResult(deviceId, expected, BluetoothGatt.GATT_SUCCESS, null)
        startNextGattOperation(deviceId)
    }

    @Synchronized
    private fun failActiveGattOperation(deviceId: String, expected: QueuedGattOperation, error: Throwable) {
        if (activeGattOperations[deviceId] !== expected) return
        gattOperationTimeouts.remove(deviceId)?.cancel()
        activeGattOperations.remove(deviceId)
        expected.reject(error)
        emitGattOperationResult(deviceId, expected, null, error.message)
        startNextGattOperation(deviceId)
    }

    /**
     * Starts a characteristic write, retrying with a short backoff while the
     * stack reports it is busy (ERROR_GATT_WRITE_REQUEST_BUSY on Android 13+,
     * a plain `false` before that). Must be called from an operation's
     * `start` block, i.e. while that operation is active.
     */
    private fun startCharacteristicWriteWithRetry(
        deviceId: String,
        kind: String,
        key: String,
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        writeType: Int,
        attempt: Int,
        onStarted: () -> Unit
    ): Boolean {
        val operation = activeGattOperations[deviceId]
        return when (writeGattCharacteristicStatus(gatt, characteristic, data, writeType)) {
            WRITE_STARTED -> {
                onStarted()
                true
            }
            WRITE_BUSY -> {
                if (attempt >= MAX_WRITE_BUSY_RETRIES || operation == null) {
                    false
                } else {
                    bluetoothScope.launch {
                        delay((WRITE_BUSY_RETRY_BASE_DELAY_MS * (attempt + 1)).coerceAtMost(WRITE_BUSY_RETRY_MAX_DELAY_MS))
                        synchronized(this@HybridMunimBluetooth) {
                            if (activeGattOperations[deviceId] !== operation) return@launch
                            val restarted = try {
                                startCharacteristicWriteWithRetry(
                                    deviceId, kind, key, gatt, characteristic, data, writeType, attempt + 1, onStarted
                                )
                            } catch (error: SecurityException) {
                                failActiveGattOperation(deviceId, operation, error)
                                return@launch
                            }
                            if (!restarted) {
                                failActiveGattOperation(
                                    deviceId,
                                    operation,
                                    IllegalStateException("$kind for $key failed: GATT stayed busy after ${attempt + 1} retries")
                                )
                            }
                        }
                    }
                    true
                }
            }
            else -> false
        }
    }

    private fun emitGattOperationResult(
        deviceId: String,
        operation: QueuedGattOperation,
        status: Int?,
        error: String?
    ) {
        eventEmitter.emit(
            "gattOperationCompleted",
            mapOf(
                "deviceId" to deviceId,
                "operation" to operation.kind,
                "target" to operation.target,
                "durationMs" to (System.currentTimeMillis() - operation.startedAtMs).coerceAtLeast(0),
                "status" to status,
                "error" to error
            )
        )
    }

    @Synchronized
    private fun rejectGattOperationsForDevice(deviceId: String, error: Throwable) {
        gattOperationTimeouts.remove(deviceId)?.cancel()
        activeGattOperations.remove(deviceId)?.reject(error)
        gattOperationQueues.remove(deviceId)?.forEach { it.reject(error) }
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
                        permissions = null,
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
    private fun writeGattCharacteristicStatus(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int
    ): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (gatt.writeCharacteristic(characteristic, value, writeType)) {
                BluetoothStatusCodes.SUCCESS -> WRITE_STARTED
                BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> WRITE_BUSY
                else -> WRITE_FAILED
            }
        } else {
            characteristic.value = value
            characteristic.writeType = writeType
            // Before Android 13 a busy stack is indistinguishable from other
            // failures; treat false as busy and let the bounded retry decide.
            if (gatt.writeCharacteristic(characteristic)) WRITE_STARTED else WRITE_BUSY
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

        val manufacturerEntries = data.manufacturerDataEntries
        if (!manufacturerEntries.isNullOrEmpty()) {
            manufacturerEntries.forEach { entry ->
                hexStringToByteArray(entry.data)?.let { bytes ->
                    dataBuilder.addManufacturerData(entry.companyId.toInt(), bytes)
                }
            }
        } else {
            data.manufacturerData?.let { manufacturerData ->
                hexStringToByteArray(manufacturerData)?.let { bytes ->
                    dataBuilder.addManufacturerData(data.manufacturerCompanyId?.toInt() ?: 0x0000, bytes)
                }
            }
        }
    }

    private fun normalizeAdvertisingData(
        advertisingData: AdvertisingDataTypes?,
        localName: String?,
        manufacturerData: String?,
        manufacturerCompanyId: Double? = null,
        manufacturerDataEntries: Array<ManufacturerDataEntry>? = null
    ): AdvertisingDataTypes {
        val base = advertisingData ?: emptyAdvertisingData()
        return base.copy(
            completeLocalName = base.completeLocalName ?: localName,
            manufacturerData = base.manufacturerData ?: manufacturerData,
            manufacturerCompanyId = base.manufacturerCompanyId ?: manufacturerCompanyId,
            manufacturerDataEntries = base.manufacturerDataEntries ?: manufacturerDataEntries
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
            manufacturerData = null,
            manufacturerCompanyId = null,
            manufacturerDataEntries = null
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
                else -> throw IllegalArgumentException(
                    "Unsupported GATT characteristic property '$property'"
                )
            }
        }
        return result
    }

    private fun characteristicPermissionsFromArray(
        permissions: Array<GATTCharacteristicPermission>?,
        properties: Array<String>,
        characteristicUuid: String
    ): Int {
        val propertySet = properties.toSet()
        val hasReadProperty = "read" in propertySet
        val hasWriteProperty = "write" in propertySet || "writeWithoutResponse" in propertySet

        if (permissions == null) {
            var defaults = 0
            if (hasReadProperty) {
                defaults = defaults or BluetoothGattCharacteristic.PERMISSION_READ
            }
            if (hasWriteProperty) {
                defaults = defaults or BluetoothGattCharacteristic.PERMISSION_WRITE
            }
            return defaults
        }

        val readPermissions = permissions.filter {
            it == GATTCharacteristicPermission.READ ||
                it == GATTCharacteristicPermission.READENCRYPTED ||
                it == GATTCharacteristicPermission.READENCRYPTEDMITM
        }
        val writePermissions = permissions.filter {
            it == GATTCharacteristicPermission.WRITE ||
                it == GATTCharacteristicPermission.WRITEENCRYPTED ||
                it == GATTCharacteristicPermission.WRITEENCRYPTEDMITM
        }
        require(readPermissions.size == if (hasReadProperty) 1 else 0) {
            "Characteristic $characteristicUuid must specify exactly one read permission when and only when it has the read property"
        }
        require(writePermissions.size == if (hasWriteProperty) 1 else 0) {
            "Characteristic $characteristicUuid must specify exactly one write permission when and only when it has a write property"
        }

        return permissions.fold(0) { result, permission ->
            result or when (permission) {
                GATTCharacteristicPermission.READ ->
                    BluetoothGattCharacteristic.PERMISSION_READ
                GATTCharacteristicPermission.WRITE ->
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                GATTCharacteristicPermission.READENCRYPTED ->
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
                GATTCharacteristicPermission.WRITEENCRYPTED ->
                    BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
                GATTCharacteristicPermission.READENCRYPTEDMITM ->
                    BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM
                GATTCharacteristicPermission.WRITEENCRYPTEDMITM ->
                    BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM
            }
        }
    }

    private fun characteristicPermissionToString(
        permission: GATTCharacteristicPermission
    ): String {
        return when (permission) {
            GATTCharacteristicPermission.READ -> "read"
            GATTCharacteristicPermission.WRITE -> "write"
            GATTCharacteristicPermission.READENCRYPTED -> "readEncrypted"
            GATTCharacteristicPermission.WRITEENCRYPTED -> "writeEncrypted"
            GATTCharacteristicPermission.READENCRYPTEDMITM -> "readEncryptedMitm"
            GATTCharacteristicPermission.WRITEENCRYPTEDMITM -> "writeEncryptedMitm"
        }
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
                else -> throw IllegalArgumentException(
                    "Unsupported GATT descriptor permission '$permission'"
                )
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
                val peerKey = deviceId ?: "unknown"
                if (!reserveInboundL2CAPChannel(peerKey)) {
                    try {
                        socket.close()
                    } catch (closeError: IOException) {
                        Log.w(TAG, "Unable to close rejected inbound L2CAP channel", closeError)
                    }
                    continue
                }

                try {
                    registerL2CAPSocket(socket, psm, deviceId, inboundPeerKey = peerKey)
                } catch (error: RuntimeException) {
                    releaseInboundL2CAPReservation(peerKey)
                    try {
                        socket.close()
                    } catch (_: IOException) {
                        // The registration failure is the primary error.
                    }
                    throw error
                }
            } catch (error: IOException) {
                if (l2capServerSockets[psm] === serverSocket) {
                    Log.w(TAG, "L2CAP accept failed for PSM $psm", error)
                }
                break
            }
        }
    }

    private fun registerL2CAPSocket(
        socket: BluetoothSocket,
        psm: Int,
        deviceId: String?,
        inboundPeerKey: String? = null
    ): L2CAPChannel {
        val channelId = UUID.randomUUID().toString()
        l2capSockets[channelId] = socket
        if (inboundPeerKey != null) {
            synchronized(l2capAdmissionLock) {
                inboundL2CAPPeersByChannel[channelId] = inboundPeerKey
            }
        }
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
        synchronized(l2capAdmissionLock) {
            inboundL2CAPPeersByChannel.remove(channelId)?.let(::releaseInboundL2CAPReservationLocked)
        }
        try {
            socket?.close()
        } catch (error: IOException) {
            Log.w(TAG, "Unable to close L2CAP channel $channelId", error)
        }
        if (emitEvent) {
            eventEmitter.emit("l2capChannelClosed", mapOf("channelId" to channelId))
        }
    }

    private fun reserveInboundL2CAPChannel(peerKey: String): Boolean {
        synchronized(l2capAdmissionLock) {
            val peerCount = inboundL2CAPCountsByPeer[peerKey] ?: 0
            if (inboundL2CAPChannelCount >= MAX_INBOUND_L2CAP_CHANNELS ||
                peerCount >= MAX_INBOUND_L2CAP_CHANNELS_PER_PEER
            ) {
                return false
            }
            inboundL2CAPChannelCount += 1
            inboundL2CAPCountsByPeer[peerKey] = peerCount + 1
            return true
        }
    }

    private fun releaseInboundL2CAPReservation(peerKey: String) {
        synchronized(l2capAdmissionLock) {
            releaseInboundL2CAPReservationLocked(peerKey)
        }
    }

    private fun releaseInboundL2CAPReservationLocked(peerKey: String) {
        inboundL2CAPChannelCount = (inboundL2CAPChannelCount - 1).coerceAtLeast(0)
        val remaining = (inboundL2CAPCountsByPeer[peerKey] ?: 1) - 1
        if (remaining <= 0) {
            inboundL2CAPCountsByPeer.remove(peerKey)
        } else {
            inboundL2CAPCountsByPeer[peerKey] = remaining
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

    private fun classicDevicePayload(
        device: BluetoothDevice,
        intent: Intent
    ): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>(
            "id" to device.address,
            "name" to device.name,
            "bondState" to bondStateFor(device).name.lowercase()
        )

        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
        if (rssi != Short.MIN_VALUE) {
            payload["rssi"] = rssi.toInt()
        }

        getBluetoothClassExtra(intent)?.let { bluetoothClass ->
            payload["bluetoothClass"] = mapOf(
                "deviceClass" to bluetoothClass.deviceClass,
                "majorDeviceClass" to bluetoothClass.majorDeviceClass,
                "serviceClasses" to bluetoothServiceClasses(bluetoothClass)
            )
        }

        try {
            device.uuids
                ?.map { it.uuid.toString() }
                ?.takeIf { it.isNotEmpty() }
                ?.let { payload["serviceUUIDs"] = it }
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to read cached Classic Bluetooth service UUIDs", error)
        }

        return payload
    }

    private fun bluetoothServiceClasses(bluetoothClass: BluetoothClass): List<Int> {
        val serviceClasses = CLASSIC_SERVICE_CLASSES
            .filter { bluetoothClass.hasService(it) }
            .toMutableList()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            bluetoothClass.hasService(BluetoothClass.Service.LE_AUDIO)
        ) {
            serviceClasses += BluetoothClass.Service.LE_AUDIO
        }

        return serviceClasses
    }

    private fun getBluetoothClassExtra(intent: Intent): BluetoothClass? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
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
        // ATT "Unlikely Error" (0x0E); not exposed as a constant by the Android SDK
        private const val GATT_UNLIKELY_ERROR = 0x0E
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 9137
        private const val REQUEST_ENABLE_BLUETOOTH_CODE = 0x4D42
        private const val PERMISSION_ACTIVITY_RETRIES = 15
        private const val PERMISSION_ACTIVITY_RETRY_DELAY_MS = 200L
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val CONNECTION_RETRY_DELAY_MS = 350L
        private const val MAX_CONNECTION_RETRIES = 2
        private const val OPERATION_TIMEOUT_MS = 15_000L
        private const val SCAN_THROTTLE_WINDOW_MS = 30_000L
        private const val SCAN_THROTTLE_MAX_STARTS = 5
        private const val DEFAULT_ATT_MTU = 23
        private const val GATT_REFRESH_SETTLE_MS = 300L
        private const val MAX_ATTRIBUTE_VALUE_LENGTH = 512
        private const val WRITE_STARTED = 0
        private const val WRITE_BUSY = 1
        private const val WRITE_FAILED = 2
        private const val MAX_WRITE_BUSY_RETRIES = 20
        private const val WRITE_BUSY_RETRY_BASE_DELAY_MS = 5L
        private const val WRITE_BUSY_RETRY_MAX_DELAY_MS = 50L
        private const val WRITE_WITHOUT_RESPONSE_FALLBACK_MS = 500L
        private const val BOND_TIMEOUT_MS = 30_000L
        private const val DEFAULT_PERIPHERAL_REQUEST_TIMEOUT_MS = 10_000L
        private const val DEFAULT_STREAM_BUFFER_SIZE = 4096
        private const val MAX_INBOUND_L2CAP_CHANNELS = 16
        private const val MAX_INBOUND_L2CAP_CHANNELS_PER_PEER = 4
        private const val DEFAULT_CLASSIC_SERVICE_NAME = "MunimBluetooth"
        private const val MULTIPEER_UNSUPPORTED_MESSAGE =
            "Apple Multipeer Connectivity is only available on Apple platforms"
        private val SERIAL_PORT_PROFILE_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val CLASSIC_SERVICE_CLASSES = listOf(
            BluetoothClass.Service.LIMITED_DISCOVERABILITY,
            BluetoothClass.Service.POSITIONING,
            BluetoothClass.Service.NETWORKING,
            BluetoothClass.Service.RENDER,
            BluetoothClass.Service.CAPTURE,
            BluetoothClass.Service.OBJECT_TRANSFER,
            BluetoothClass.Service.AUDIO,
            BluetoothClass.Service.TELEPHONY,
            BluetoothClass.Service.INFORMATION
        )
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
            if (!context.hasActiveReactInstance()) {
                return@runOnUiThread
            }
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

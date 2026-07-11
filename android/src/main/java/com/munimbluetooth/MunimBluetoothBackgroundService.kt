package com.munimbluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import com.margelo.nitro.munimbluetooth.ScanMode
import org.json.JSONArray
import java.util.Locale
import java.util.UUID

class MunimBluetoothBackgroundService : Service() {
    private data class SessionConfig(
        val serviceUUIDs: Array<String>,
        val localName: String?,
        val allowDuplicates: Boolean,
        val scanMode: ScanMode,
        val gattServicesJson: String?,
        val restoreGattOnStart: Boolean,
        val notificationChannelId: String,
        val notificationChannelName: String,
        val notificationTitle: String,
        val notificationText: String
    )

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gattServer: BluetoothGattServer? = null
    private var previousAdapterName: String? = null

    private val discoveredDeviceIds = linkedSetOf<String>()
    private val characteristicValues = mutableMapOf<String, ByteArray>()
    private val descriptorValues = mutableMapOf<String, ByteArray>()
    private val subscribedDevices = mutableMapOf<UUID, MutableSet<BluetoothDevice>>()
    private var notificationChannelId = DEFAULT_CHANNEL_ID
    private var notificationChannelName = DEFAULT_CHANNEL_NAME
    private var notificationTitle = DEFAULT_NOTIFICATION_TITLE
    private var notificationText = DEFAULT_NOTIFICATION_TEXT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            clearPersistedSession()
            stopSelf()
            return START_NOT_STICKY
        }

        val config = when (intent?.action) {
            ACTION_START -> sessionConfigFromIntent(intent).also(::persistSession)
            else -> readPersistedSession()
        }

        if (config == null) {
            Log.w(TAG, "No persisted background BLE session to restart")
            return START_NOT_STICKY
        }

        notificationChannelId = config.notificationChannelId
        notificationChannelName = config.notificationChannelName
        notificationTitle = config.notificationTitle
        notificationText = config.notificationText

        startForeground(
            NOTIFICATION_ID,
            buildNotification(neighborCount = discoveredDeviceIds.size)
        )

        startBleSession(config)
        return START_STICKY
    }

    override fun onDestroy() {
        stopBleSession()
        super.onDestroy()
    }

    private fun startBleSession(config: SessionConfig) {
        stopBleSession()

        if (!BluetoothPermissionUtils.hasRequiredPermissions(
                applicationContext,
                BluetoothPermission.SCAN,
                BluetoothPermission.CONNECT,
                BluetoothPermission.ADVERTISE
            )
        ) {
            Log.w(TAG, "Unable to start background BLE session: missing runtime permissions")
            stopSelf()
            return
        }

        bluetoothManager =
            applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        val adapter = bluetoothAdapter
        val isEnabled = try {
            adapter?.isEnabled == true
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to inspect Bluetooth adapter state for background session", error)
            false
        }

        if (adapter == null || !isEnabled) {
            Log.w(TAG, "Unable to start background BLE session: Bluetooth unavailable")
            stopSelf()
            return
        }

        if (!config.localName.isNullOrBlank() && previousAdapterName == null) {
            previousAdapterName = try {
                adapter.name
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to read adapter name for background advertising", error)
                null
            }

            try {
                adapter.name = config.localName
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to set adapter name for background advertising", error)
            }
        }

        if (config.restoreGattOnStart) {
            startGattServer(config.gattServicesJson)
        }
        startAdvertising(adapter, config.serviceUUIDs, !config.localName.isNullOrBlank())
        startScan(adapter, config.serviceUUIDs, config.allowDuplicates, config.scanMode)
    }

    private fun stopBleSession() {
        scanCallback?.let { callback ->
            try {
                scanner?.stopScan(callback)
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to stop background scan cleanly", error)
            }
        }
        scanCallback = null
        scanner = null

        gattServer?.close()
        gattServer = null
        characteristicValues.clear()
        descriptorValues.clear()
        subscribedDevices.clear()

        advertiseCallback?.let { callback ->
            try {
                advertiser?.stopAdvertising(callback)
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to stop background advertising cleanly", error)
            }
        }
        advertiseCallback = null
        advertiser = null

        val adapter = bluetoothAdapter
        val previousName = previousAdapterName
        if (adapter != null && previousName != null) {
            try {
                adapter.name = previousName
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to restore adapter name", error)
            }
        }
        previousAdapterName = null
        discoveredDeviceIds.clear()
    }

    private fun startAdvertising(
        adapter: BluetoothAdapter,
        serviceUUIDs: Array<String>,
        includeDeviceName: Boolean
    ) {
        val activeAdvertiser = adapter.bluetoothLeAdvertiser ?: run {
            Log.w(TAG, "Bluetooth advertiser unavailable for background session")
            return
        }
        advertiser = activeAdvertiser

        val data = AdvertiseData.Builder()

        serviceUUIDs.forEach { uuid ->
            runCatching { ParcelUuid.fromString(uuid) }.getOrNull()?.let(data::addServiceUuid)
        }

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(includeDeviceName)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "Background advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Background advertising failed: $errorCode")
            }
        }

        try {
            activeAdvertiser.startAdvertising(
                settings,
                data.build(),
                scanResponse.build(),
                advertiseCallback
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to start background advertising", error)
        }
    }

    private fun startGattServer(gattServicesJson: String?) {
        if (gattServicesJson.isNullOrBlank()) {
            return
        }

        val manager = bluetoothManager ?: return
        gattServer = try {
            manager.openGattServer(applicationContext, buildGattServerCallback())
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to open background GATT server", error)
            null
        }

        val server = gattServer ?: return
        runCatching {
            val services = JSONArray(gattServicesJson)
            val nativeServices = linkedMapOf<String, BluetoothGattService>()
            for (index in 0 until services.length()) {
                val serviceJson = services.getJSONObject(index)
                val service = BluetoothGattService(
                    UUID.fromString(serviceJson.getString("uuid")),
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
                )
                val characteristics = serviceJson.optJSONArray("characteristics") ?: JSONArray()
                for (characteristicIndex in 0 until characteristics.length()) {
                    val characteristicJson = characteristics.getJSONObject(characteristicIndex)
                    val characteristic = BluetoothGattCharacteristic(
                        UUID.fromString(characteristicJson.getString("uuid")),
                        propertiesFromJson(characteristicJson.optJSONArray("properties")),
                        BluetoothGattCharacteristic.PERMISSION_READ or
                            BluetoothGattCharacteristic.PERMISSION_WRITE
                    )
                    val characteristicInitialValue = optionalString(characteristicJson, "value")
                        ?.let { value ->
                            hexStringToByteArray(value) ?: value.toByteArray()
                        }
                    val descriptorInitialValues =
                        mutableListOf<Pair<BluetoothGattDescriptor, ByteArray>>()
                    characteristicInitialValue?.let { value ->
                        @Suppress("DEPRECATION")
                        characteristic.value = value
                    }

                    val descriptors = characteristicJson.optJSONArray("descriptors") ?: JSONArray()
                    for (descriptorIndex in 0 until descriptors.length()) {
                        val descriptorJson = descriptors.getJSONObject(descriptorIndex)
                        val descriptor = BluetoothGattDescriptor(
                            UUID.fromString(descriptorJson.getString("uuid")),
                            descriptorPermissionsFromJson(descriptorJson.optJSONArray("permissions"))
                        )
                        optionalString(descriptorJson, "value")?.let { value ->
                            val bytes = hexStringToByteArray(value) ?: value.toByteArray()
                            @Suppress("DEPRECATION")
                            descriptor.value = bytes
                            descriptorInitialValues.add(descriptor to bytes)
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
                    characteristicInitialValue?.let { value ->
                        setCharacteristicValue(characteristic, value)
                    }
                    descriptorInitialValues.forEach { (descriptor, value) ->
                        setDescriptorValue(descriptor, value)
                    }
                }
                nativeServices[serviceJson.getString("uuid").lowercase(Locale.US)] = service
            }

            for (index in 0 until services.length()) {
                val serviceJson = services.getJSONObject(index)
                val service = nativeServices[
                    serviceJson.getString("uuid").lowercase(Locale.US)
                ] ?: continue
                val includedServices = serviceJson.optJSONArray("includedServices") ?: continue
                for (includedIndex in 0 until includedServices.length()) {
                    nativeServices[
                        includedServices.optString(includedIndex).lowercase(Locale.US)
                    ]?.let(service::addService)
                }
            }

            nativeServices.values.forEach { service ->
                server.addService(service)
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to restore background GATT services", error)
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
                val value = characteristicValues[characteristicKey(characteristic)] ?: ByteArray(0)
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
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                val canWrite = characteristic.properties and
                    (BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
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

                val key = characteristicKey(characteristic)
                val previous = characteristicValues[key] ?: ByteArray(0)
                if (offset > previous.size) {
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

                val updated = if (offset == 0) {
                    value
                } else {
                    val merged = previous.copyOf(maxOf(previous.size, offset + value.size))
                    System.arraycopy(value, 0, merged, offset, value.size)
                    merged
                }
                setCharacteristicValue(characteristic, updated)

                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        updated
                    )
                }

                notifySubscribers(characteristic, updated)
            }

            override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor
            ) {
                val value = descriptorValues[descriptorKey(descriptor)] ?: ByteArray(0)
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
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                setDescriptorValue(descriptor, value)

                if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                    val characteristic = descriptor.characteristic
                    val subscribers = subscribedDevices.getOrPut(characteristic.uuid) {
                        mutableSetOf()
                    }
                    val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                        value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    if (enabled) {
                        subscribers.add(device)
                    } else {
                        subscribers.remove(device)
                    }
                }

                if (responseNeeded) {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value
                    )
                }
            }
        }
    }

    private fun startScan(
        adapter: BluetoothAdapter,
        serviceUUIDs: Array<String>,
        allowDuplicates: Boolean,
        scanMode: ScanMode
    ) {
        val activeScanner = adapter.bluetoothLeScanner ?: run {
            Log.w(TAG, "Bluetooth scanner unavailable for background session")
            return
        }
        scanner = activeScanner

        val filters = serviceUUIDs.mapNotNull { uuid ->
            runCatching {
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(uuid))
                    .build()
            }.getOrNull()
        }

        val androidScanMode = when (scanMode) {
            ScanMode.LOWLATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
            ScanMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
            ScanMode.LOWPOWER -> ScanSettings.SCAN_MODE_LOW_POWER
        }

        val settingsBuilder = ScanSettings.Builder().setScanMode(androidScanMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_STICKY)
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result, allowDuplicates)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it, allowDuplicates) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Background scan failed: $errorCode")
            }
        }

        try {
            activeScanner.startScan(filters, settingsBuilder.build(), scanCallback)
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to start background scan", error)
        }
    }

    private fun handleScanResult(result: ScanResult, allowDuplicates: Boolean) {
        val deviceId = result.device.address ?: return
        if (!allowDuplicates && !discoveredDeviceIds.add(deviceId)) {
            return
        }
        if (allowDuplicates) {
            discoveredDeviceIds.add(deviceId)
        }

        updateForegroundNotification()
    }

    private fun updateForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(
            NOTIFICATION_ID,
            buildNotification(neighborCount = discoveredDeviceIds.size)
        )
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(neighborCount: Int): Notification {
        ensureNotificationChannel()

        val text = if (neighborCount <= 0) {
            notificationText
        } else {
            String.format(
                Locale.US,
                "%s (%d nearby)",
                notificationText,
                neighborCount
            )
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, notificationChannelId)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(notificationTitle)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehaviorCompat()
            .build()
    }

    private fun Notification.Builder.setForegroundServiceBehaviorCompat(): Notification.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return this
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val channel = NotificationChannel(
            notificationChannelId,
            notificationChannelName,
            NotificationManager.IMPORTANCE_LOW
        )
        manager?.createNotificationChannel(channel)
    }

    private fun parseScanMode(rawValue: String): ScanMode {
        return runCatching { ScanMode.valueOf(rawValue) }.getOrElse { ScanMode.LOWPOWER }
    }

    private fun sessionConfigFromIntent(intent: Intent): SessionConfig {
        return SessionConfig(
            serviceUUIDs = intent.getStringArrayExtra(EXTRA_SERVICE_UUIDS) ?: emptyArray(),
            localName = intent.getStringExtra(EXTRA_LOCAL_NAME),
            allowDuplicates = intent.getBooleanExtra(EXTRA_ALLOW_DUPLICATES, false),
            scanMode = parseScanMode(
                intent.getStringExtra(EXTRA_SCAN_MODE) ?: ScanMode.LOWPOWER.name
            ),
            gattServicesJson = intent.getStringExtra(EXTRA_GATT_SERVICES_JSON),
            restoreGattOnStart = false,
            notificationChannelId = intent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_ID)
                ?: DEFAULT_CHANNEL_ID,
            notificationChannelName = intent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_NAME)
                ?: DEFAULT_CHANNEL_NAME,
            notificationTitle = intent.getStringExtra(EXTRA_NOTIFICATION_TITLE)
                ?: DEFAULT_NOTIFICATION_TITLE,
            notificationText = intent.getStringExtra(EXTRA_NOTIFICATION_TEXT)
                ?: DEFAULT_NOTIFICATION_TEXT
        )
    }

    private fun persistSession(config: SessionConfig) {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SERVICE_UUIDS, config.serviceUUIDs.joinToString("\n"))
            .putString(PREF_LOCAL_NAME, config.localName)
            .putBoolean(PREF_ALLOW_DUPLICATES, config.allowDuplicates)
            .putString(PREF_SCAN_MODE, config.scanMode.name)
            .putString(PREF_GATT_SERVICES_JSON, config.gattServicesJson)
            .putString(PREF_NOTIFICATION_CHANNEL_ID, config.notificationChannelId)
            .putString(PREF_NOTIFICATION_CHANNEL_NAME, config.notificationChannelName)
            .putString(PREF_NOTIFICATION_TITLE, config.notificationTitle)
            .putString(PREF_NOTIFICATION_TEXT, config.notificationText)
            .apply()
    }

    private fun readPersistedSession(): SessionConfig? {
        val preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!preferences.contains(PREF_SERVICE_UUIDS)) {
            return null
        }

        return SessionConfig(
            serviceUUIDs = preferences.getString(PREF_SERVICE_UUIDS, "")
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                ?.toTypedArray()
                ?: emptyArray(),
            localName = preferences.getString(PREF_LOCAL_NAME, null),
            allowDuplicates = preferences.getBoolean(PREF_ALLOW_DUPLICATES, false),
            scanMode = parseScanMode(
                preferences.getString(PREF_SCAN_MODE, ScanMode.LOWPOWER.name)
                    ?: ScanMode.LOWPOWER.name
            ),
            gattServicesJson = preferences.getString(PREF_GATT_SERVICES_JSON, null),
            restoreGattOnStart = true,
            notificationChannelId = preferences.getString(
                PREF_NOTIFICATION_CHANNEL_ID,
                DEFAULT_CHANNEL_ID
            ) ?: DEFAULT_CHANNEL_ID,
            notificationChannelName = preferences.getString(
                PREF_NOTIFICATION_CHANNEL_NAME,
                DEFAULT_CHANNEL_NAME
            ) ?: DEFAULT_CHANNEL_NAME,
            notificationTitle = preferences.getString(
                PREF_NOTIFICATION_TITLE,
                DEFAULT_NOTIFICATION_TITLE
            ) ?: DEFAULT_NOTIFICATION_TITLE,
            notificationText = preferences.getString(
                PREF_NOTIFICATION_TEXT,
                DEFAULT_NOTIFICATION_TEXT
            ) ?: DEFAULT_NOTIFICATION_TEXT
        )
    }

    private fun clearPersistedSession() {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun propertiesFromJson(properties: JSONArray?): Int {
        var result = 0
        forEachString(properties) { property ->
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

    private fun descriptorPermissionsFromJson(permissions: JSONArray?): Int {
        if (permissions == null || permissions.length() == 0) {
            return BluetoothGattDescriptor.PERMISSION_READ or
                BluetoothGattDescriptor.PERMISSION_WRITE
        }

        var result = 0
        forEachString(permissions) { permission ->
            when (permission) {
                "read" -> result = result or BluetoothGattDescriptor.PERMISSION_READ
                "write" -> result = result or BluetoothGattDescriptor.PERMISSION_WRITE
                "readEncrypted" -> result = result or
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                "writeEncrypted" -> result = result or
                    BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED
                "readEncryptedMitm" -> result = result or
                    BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM
                "writeEncryptedMitm" -> result = result or
                    BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM
            }
        }
        return result
    }

    private fun forEachString(array: JSONArray?, block: (String) -> Unit) {
        if (array == null) return
        for (index in 0 until array.length()) {
            block(array.optString(index))
        }
    }

    private fun optionalString(json: org.json.JSONObject, key: String): String? {
        return if (json.has(key) && !json.isNull(key)) json.getString(key) else null
    }

    private fun supportsNotifyOrIndicate(characteristic: BluetoothGattCharacteristic): Boolean {
        return characteristic.properties and
            (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
    }

    private fun setCharacteristicValue(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        characteristicValues[characteristicKey(characteristic)] = value
        @Suppress("DEPRECATION")
        characteristic.value = value
    }

    private fun setDescriptorValue(descriptor: BluetoothGattDescriptor, value: ByteArray) {
        descriptorValues[descriptorKey(descriptor)] = value
        @Suppress("DEPRECATION")
        descriptor.value = value
    }

    private fun notifySubscribers(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val subscribers = subscribedDevices[characteristic.uuid]?.toList().orEmpty()
        subscribers.forEach { device ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gattServer?.notifyCharacteristicChanged(
                        device,
                        characteristic,
                        characteristic.properties and
                            BluetoothGattCharacteristic.PROPERTY_INDICATE != 0,
                        value
                    )
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = value
                    @Suppress("DEPRECATION")
                    gattServer?.notifyCharacteristicChanged(
                        device,
                        characteristic,
                        characteristic.properties and
                            BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    )
                }
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to notify background GATT subscriber", error)
            }
        }
    }

    private fun characteristicKey(characteristic: BluetoothGattCharacteristic): String {
        return "${characteristic.service?.uuid}|${characteristic.uuid}".lowercase(Locale.US)
    }

    private fun descriptorKey(descriptor: BluetoothGattDescriptor): String {
        val characteristic = descriptor.characteristic
            ?: return "unknown|${descriptor.uuid}".lowercase(Locale.US)
        return "${characteristicKey(characteristic)}|${descriptor.uuid}"
            .lowercase(Locale.US)
    }

    private fun hexStringToByteArray(hex: String): ByteArray? {
        val cleanHex = hex.removePrefix("0x")
        if (cleanHex.isEmpty() || cleanHex.length % 2 != 0) {
            return null
        }

        return try {
            ByteArray(cleanHex.length / 2) { index ->
                cleanHex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    companion object {
        const val ACTION_START = "com.munimbluetooth.action.START_BACKGROUND_SESSION"
        const val ACTION_STOP = "com.munimbluetooth.action.STOP_BACKGROUND_SESSION"

        const val EXTRA_SERVICE_UUIDS = "serviceUUIDs"
        const val EXTRA_LOCAL_NAME = "localName"
        const val EXTRA_ALLOW_DUPLICATES = "allowDuplicates"
        const val EXTRA_SCAN_MODE = "scanMode"
        const val EXTRA_GATT_SERVICES_JSON = "gattServicesJson"
        const val EXTRA_NOTIFICATION_CHANNEL_ID = "notificationChannelId"
        const val EXTRA_NOTIFICATION_CHANNEL_NAME = "notificationChannelName"
        const val EXTRA_NOTIFICATION_TITLE = "notificationTitle"
        const val EXTRA_NOTIFICATION_TEXT = "notificationText"

        const val DEFAULT_CHANNEL_ID = "munim-bluetooth-background"
        const val DEFAULT_CHANNEL_NAME = "Bluetooth background session"
        const val DEFAULT_NOTIFICATION_TITLE = "Bluetooth nearby mode"
        const val DEFAULT_NOTIFICATION_TEXT = "Scanning for nearby Bluetooth devices"

        private const val NOTIFICATION_ID = 48231
        private const val TAG = "MunimBluetoothBgSvc"
        private const val PREFERENCES_NAME = "munim-bluetooth-background"
        private const val PREF_SERVICE_UUIDS = "serviceUUIDs"
        private const val PREF_LOCAL_NAME = "localName"
        private const val PREF_ALLOW_DUPLICATES = "allowDuplicates"
        private const val PREF_SCAN_MODE = "scanMode"
        private const val PREF_GATT_SERVICES_JSON = "gattServicesJson"
        private const val PREF_NOTIFICATION_CHANNEL_ID = "notificationChannelId"
        private const val PREF_NOTIFICATION_CHANNEL_NAME = "notificationChannelName"
        private const val PREF_NOTIFICATION_TITLE = "notificationTitle"
        private const val PREF_NOTIFICATION_TEXT = "notificationText"
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

package com.munimbluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
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
import java.util.Locale

class MunimBluetoothBackgroundService : Service() {
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var previousAdapterName: String? = null

    private val discoveredDeviceIds = linkedSetOf<String>()
    private var notificationChannelId = DEFAULT_CHANNEL_ID
    private var notificationChannelName = DEFAULT_CHANNEL_NAME
    private var notificationTitle = DEFAULT_NOTIFICATION_TITLE
    private var notificationText = DEFAULT_NOTIFICATION_TEXT

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                notificationChannelId =
                    intent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_ID)
                        ?: DEFAULT_CHANNEL_ID
                notificationChannelName =
                    intent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_NAME)
                        ?: DEFAULT_CHANNEL_NAME
                notificationTitle =
                    intent.getStringExtra(EXTRA_NOTIFICATION_TITLE)
                        ?: DEFAULT_NOTIFICATION_TITLE
                notificationText =
                    intent.getStringExtra(EXTRA_NOTIFICATION_TEXT)
                        ?: DEFAULT_NOTIFICATION_TEXT

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(neighborCount = discoveredDeviceIds.size)
                )

                val serviceUUIDs =
                    intent.getStringArrayExtra(EXTRA_SERVICE_UUIDS) ?: emptyArray()
                val localName = intent.getStringExtra(EXTRA_LOCAL_NAME)
                val allowDuplicates =
                    intent.getBooleanExtra(EXTRA_ALLOW_DUPLICATES, false)
                val scanMode = parseScanMode(
                    intent.getStringExtra(EXTRA_SCAN_MODE) ?: ScanMode.LOWPOWER.name
                )

                startBleSession(
                    serviceUUIDs = serviceUUIDs,
                    localName = localName,
                    allowDuplicates = allowDuplicates,
                    scanMode = scanMode
                )
                return START_STICKY
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopBleSession()
        super.onDestroy()
    }

    private fun startBleSession(
        serviceUUIDs: Array<String>,
        localName: String?,
        allowDuplicates: Boolean,
        scanMode: ScanMode
    ) {
        if (!BluetoothPermissionUtils.hasRequiredPermissions(applicationContext)) {
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

        if (!localName.isNullOrBlank() && previousAdapterName == null) {
            previousAdapterName = try {
                adapter.name
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to read adapter name for background advertising", error)
                null
            }

            try {
                adapter.name = localName
            } catch (error: SecurityException) {
                Log.w(TAG, "Unable to set adapter name for background advertising", error)
            }
        }

        startAdvertising(adapter, serviceUUIDs, !localName.isNullOrBlank())
        startScan(adapter, serviceUUIDs, allowDuplicates, scanMode)
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
            .setIncludeDeviceName(includeDeviceName)

        serviceUUIDs.forEach { uuid ->
            runCatching { ParcelUuid.fromString(uuid) }.getOrNull()?.let(data::addServiceUuid)
        }

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
            activeAdvertiser.startAdvertising(settings, data.build(), advertiseCallback)
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to start background advertising", error)
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

    companion object {
        const val ACTION_START = "com.munimbluetooth.action.START_BACKGROUND_SESSION"
        const val ACTION_STOP = "com.munimbluetooth.action.STOP_BACKGROUND_SESSION"

        const val EXTRA_SERVICE_UUIDS = "serviceUUIDs"
        const val EXTRA_LOCAL_NAME = "localName"
        const val EXTRA_ALLOW_DUPLICATES = "allowDuplicates"
        const val EXTRA_SCAN_MODE = "scanMode"
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
    }
}

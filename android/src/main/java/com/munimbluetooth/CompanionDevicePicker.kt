package com.munimbluetooth

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.ReactApplicationContext
import com.margelo.nitro.core.Promise
import com.margelo.nitro.munimbluetooth.AssociatedDevice
import com.margelo.nitro.munimbluetooth.DevicePickerOptions
import java.util.concurrent.Executor
import java.util.regex.Pattern

/**
 * showDevicePicker() on Android: CompanionDeviceManager association. The
 * system shows its own device chooser, so the app needs neither location nor
 * BLUETOOTH_SCAN to find the device, and the association survives restarts.
 */
internal class CompanionDevicePicker(private val tag: String) {
    private val lock = Any()
    private var pending: Promise<AssociatedDevice>? = null

    fun isSupported(context: Context?): Boolean {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
    }

    fun show(context: ReactApplicationContext?, options: DevicePickerOptions): Promise<AssociatedDevice> {
        if (context == null) {
            return Promise.rejected(IllegalStateException("React context unavailable"))
        }
        if (!isSupported(context)) {
            return Promise.rejected(
                UnsupportedOperationException("This device has no companion device setup (CompanionDeviceManager)")
            )
        }
        val activity = context.currentActivity
            ?: return Promise.rejected(IllegalStateException("showDevicePicker() needs a foreground Activity"))
        val manager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
            ?: return Promise.rejected(UnsupportedOperationException("CompanionDeviceManager is unavailable"))

        val request = try {
            buildRequest(options)
        } catch (error: IllegalArgumentException) {
            return Promise.rejected(error)
        }

        val promise = Promise<AssociatedDevice>()
        synchronized(lock) {
            if (pending != null) {
                return Promise.rejected(IllegalStateException("A device picker is already showing"))
            }
            pending = promise
        }

        val listener = object : BaseActivityEventListener() {
            override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
                if (requestCode != REQUEST_CODE) return
                context.removeActivityEventListener(this)
                if (resultCode != Activity.RESULT_OK || data == null) {
                    settle { it.reject(IllegalStateException("Device picker was cancelled")) }
                    return
                }
                val device = associatedDeviceFromResult(data)
                if (device == null) {
                    settle { it.reject(IllegalStateException("The picker returned no device")) }
                } else {
                    settle { it.resolve(device) }
                }
            }
        }
        context.addActivityEventListener(listener)

        fun launch(sender: IntentSender) {
            try {
                activity.startIntentSenderForResult(sender, REQUEST_CODE, null, 0, 0, 0)
            } catch (error: IntentSender.SendIntentException) {
                Log.w(tag, "Unable to show the companion device chooser", error)
                context.removeActivityEventListener(listener)
                settle { it.reject(error) }
            }
        }

        val callback = object : CompanionDeviceManager.Callback() {
            // API 33+.
            override fun onAssociationPending(intentSender: IntentSender) {
                launch(intentSender)
            }

            // API 26-32: the same chooser intent under its old name.
            @Deprecated("Replaced by onAssociationPending on API 33")
            override fun onDeviceFound(intentSender: IntentSender) {
                launch(intentSender)
            }

            override fun onFailure(error: CharSequence?) {
                context.removeActivityEventListener(listener)
                settle { it.reject(IllegalStateException("Device association failed: $error")) }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.associate(request, MAIN_EXECUTOR, callback)
            } else {
                @Suppress("DEPRECATION")
                manager.associate(request, callback, Handler(Looper.getMainLooper()))
            }
        } catch (error: RuntimeException) {
            context.removeActivityEventListener(listener)
            settle { it.reject(error) }
        }
        return promise
    }

    fun associations(context: Context?): Promise<Array<AssociatedDevice>> {
        val manager = manager(context)
            ?: return Promise.resolved(emptyArray())
        return try {
            val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.myAssociations.map { info -> associatedDeviceFromInfo(info) }
            } else {
                @Suppress("DEPRECATION")
                manager.associations.map { address ->
                    AssociatedDevice(id = address, name = null, associationId = address)
                }
            }
            Promise.resolved(devices.toTypedArray())
        } catch (error: RuntimeException) {
            Promise.rejected(error)
        }
    }

    fun remove(context: Context?, associationId: String): Promise<Unit> {
        val manager = manager(context)
            ?: return Promise.rejected(UnsupportedOperationException("CompanionDeviceManager is unavailable"))
        return try {
            val numericId = associationId.toIntOrNull()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && numericId != null) {
                manager.disassociate(numericId)
            } else {
                @Suppress("DEPRECATION")
                manager.disassociate(associationId)
            }
            Promise.resolved(Unit)
        } catch (error: RuntimeException) {
            Promise.rejected(error)
        }
    }

    private fun manager(context: Context?): CompanionDeviceManager? {
        if (!isSupported(context)) return null
        return context?.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
    }

    private fun settle(action: (Promise<AssociatedDevice>) -> Unit) {
        val promise = synchronized(lock) { pending.also { pending = null } } ?: return
        action(promise)
    }

    private fun buildRequest(options: DevicePickerOptions): AssociationRequest {
        require(options.filters.isNotEmpty()) { "showDevicePicker needs at least one filter" }
        val builder = AssociationRequest.Builder()
            .setSingleDevice(options.singleDevice ?: false)
        options.filters.forEach { filter ->
            val scanFilter = ScanFilter.Builder()
            filter.serviceUUID?.takeIf { it.isNotEmpty() }?.let {
                scanFilter.setServiceUuid(parseBleParcelUuid(it))
            }
            filter.manufacturerId?.let { id ->
                require(id.isFinite() && id >= 0 && id <= 0xFFFF && id % 1.0 == 0.0) {
                    "manufacturerId must be an integer between 0 and 65535"
                }
                val data = filter.manufacturerData?.takeIf { it.isNotEmpty() }?.let {
                    Hex.decode(it) ?: throw IllegalArgumentException("manufacturerData must be a hex string")
                } ?: byteArrayOf()
                val mask = filter.manufacturerDataMask?.takeIf { it.isNotEmpty() }?.let {
                    Hex.decode(it) ?: throw IllegalArgumentException("manufacturerDataMask must be a hex string")
                }
                if (mask != null) {
                    require(mask.size == data.size) {
                        "manufacturerDataMask must be the same length as manufacturerData"
                    }
                    scanFilter.setManufacturerData(id.toInt(), data, mask)
                } else {
                    scanFilter.setManufacturerData(id.toInt(), data)
                }
            }
            val leFilter = BluetoothLeDeviceFilter.Builder().setScanFilter(scanFilter.build())
            filter.namePrefix?.takeIf { it.isNotEmpty() }?.let { prefix ->
                leFilter.setNamePattern(Pattern.compile("^" + Pattern.quote(prefix) + ".*"))
            }
            builder.addDeviceFilter(leFilter.build())
        }
        return builder.build()
    }

    private fun associatedDeviceFromResult(data: Intent): AssociatedDevice? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
                ?.let { return associatedDeviceFromInfo(it) }
        }
        @Suppress("DEPRECATION")
        val extra: Any? = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        val device = when (extra) {
            is ScanResult -> extra.device
            is BluetoothDevice -> extra
            else -> null
        } ?: return null
        val name = try {
            device.name
        } catch (_: SecurityException) {
            null
        }
        return AssociatedDevice(id = device.address, name = name, associationId = device.address)
    }

    private fun associatedDeviceFromInfo(info: AssociationInfo): AssociatedDevice {
        val address = info.deviceMacAddress?.toString()?.uppercase() ?: ""
        // API 34+: the scan result the user picked, which carries the name
        // the device advertised.
        val advertisedName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.associatedDevice?.bleDevice?.scanRecord?.deviceName
        } else {
            null
        }
        return AssociatedDevice(
            id = address,
            name = info.displayName?.toString() ?: advertisedName,
            associationId = info.id.toString()
        )
    }

    companion object {
        private const val REQUEST_CODE = 0x4D43
        private val MAIN_EXECUTOR = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
    }
}

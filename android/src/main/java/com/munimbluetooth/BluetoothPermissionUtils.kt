package com.munimbluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal enum class BluetoothPermission {
    SCAN,
    CONNECT,
    ADVERTISE
}

internal object BluetoothPermissionUtils {
    fun requiredPermissions(vararg permissions: BluetoothPermission): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> permissions.map { permission ->
                when (permission) {
                    BluetoothPermission.SCAN -> Manifest.permission.BLUETOOTH_SCAN
                    BluetoothPermission.CONNECT -> Manifest.permission.BLUETOOTH_CONNECT
                    BluetoothPermission.ADVERTISE -> Manifest.permission.BLUETOOTH_ADVERTISE
                }
            }.distinct().toTypedArray()

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                permissions.contains(BluetoothPermission.SCAN) -> arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )

            else -> emptyArray()
        }
    }

    fun missingPermissions(
        context: Context,
        vararg permissions: BluetoothPermission
    ): Array<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return emptyArray()
        }

        return requiredPermissions(*permissions)
            .filter { permission ->
                context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    fun hasRequiredPermissions(
        context: Context,
        vararg permissions: BluetoothPermission
    ): Boolean {
        return missingPermissions(context, *permissions).isEmpty()
    }
}

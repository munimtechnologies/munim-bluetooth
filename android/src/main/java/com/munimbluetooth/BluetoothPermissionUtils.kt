package com.munimbluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal object BluetoothPermissionUtils {
    fun requiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            else -> emptyArray()
        }
    }

    fun missingPermissions(context: Context): Array<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return emptyArray()
        }

        return requiredPermissions()
            .filter { permission ->
                context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
            }
            .toTypedArray()
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        return missingPermissions(context).isEmpty()
    }
}

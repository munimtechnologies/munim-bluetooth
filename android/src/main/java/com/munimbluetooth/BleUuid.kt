package com.munimbluetooth

import android.os.ParcelUuid
import java.util.UUID

private const val BLUETOOTH_BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

/**
 * Parses a Bluetooth UUID passed from JS. Besides full 128-bit UUIDs this
 * accepts the 16- and 32-bit short forms ("180D", "0x180D", "0000180D") that
 * iOS accepts and the README uses, expanding them onto the Bluetooth base UUID.
 * `UUID.fromString` alone rejects short forms.
 *
 * @throws IllegalArgumentException when [value] is not a Bluetooth UUID.
 */
internal fun parseBleUuid(value: String): UUID {
    val hex = if (value.startsWith("0x", ignoreCase = true)) value.substring(2) else value
    val isShortForm = (hex.length == 4 || hex.length == 8) &&
        hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (isShortForm) {
        return UUID.fromString(hex.padStart(8, '0') + BLUETOOTH_BASE_UUID_SUFFIX)
    }
    return UUID.fromString(value)
}

internal fun parseBleParcelUuid(value: String): ParcelUuid = ParcelUuid(parseBleUuid(value))

/** True when [uuid] is the Bluetooth UUID that [value] names, in any accepted form. */
internal fun isSameBleUuid(uuid: UUID, value: String): Boolean =
    try {
        uuid == parseBleUuid(value)
    } catch (_: IllegalArgumentException) {
        false
    }

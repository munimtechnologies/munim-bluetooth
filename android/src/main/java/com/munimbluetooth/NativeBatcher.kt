package com.munimbluetooth

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

/**
 * Collects high-rate items (scan results, notifications) produced on
 * Bluetooth binder threads and hands them to one consumer in batches.
 *
 * - `intervalMs == 0`: every item is delivered on its own, right away.
 * - `intervalMs > 0`: the first item of a batch arms a timer; everything
 *   that arrives before it fires is delivered together. A batch that grows
 *   to `maxBatchSize` is flushed early.
 * - With a `keyOf`, a batch keeps one item per key (the latest), in the
 *   order keys were first seen.
 *
 * Delivery happens on a dedicated background looper, never on the binder
 * thread that produced the item and never on the main thread.
 */
internal class NativeBatcher<T>(
    private val intervalMs: Long,
    private val maxBatchSize: Int,
    private val keyOf: ((T) -> String)?,
    private val deliver: (List<T>) -> Unit
) {
    private val lock = Any()
    private var keyed = LinkedHashMap<String, T>()
    private var items = ArrayList<T>()
    private var flushScheduled = false
    @Volatile private var closed = false

    private val flushRunnable = Runnable { flush() }

    fun add(item: T) {
        if (closed) return
        var flushNow = false
        synchronized(lock) {
            val key = keyOf?.invoke(item)
            if (key != null) {
                // Re-inserting keeps the original position in a LinkedHashMap.
                keyed[key] = item
            } else {
                items.add(item)
            }
            val size = keyed.size + items.size
            if (intervalMs <= 0 || size >= maxBatchSize) {
                flushNow = true
            } else if (!flushScheduled) {
                flushScheduled = true
                handler.postDelayed(flushRunnable, intervalMs)
            }
        }
        if (flushNow) {
            handler.post(flushRunnable)
        }
    }

    fun flush() {
        val batch: List<T>
        synchronized(lock) {
            flushScheduled = false
            handler.removeCallbacks(flushRunnable)
            if (keyed.isEmpty() && items.isEmpty()) return
            batch = if (keyed.isEmpty()) {
                items
            } else {
                ArrayList<T>(keyed.size + items.size).also {
                    it.addAll(keyed.values)
                    it.addAll(items)
                }
            }
            keyed = LinkedHashMap()
            items = ArrayList()
        }
        if (closed) return
        try {
            deliver(batch)
        } catch (error: Throwable) {
            android.util.Log.w("MunimBluetooth", "Batched listener failed", error)
        }
    }

    /** Drops anything not yet delivered; later add() calls are ignored. */
    fun close() {
        closed = true
        synchronized(lock) {
            handler.removeCallbacks(flushRunnable)
            flushScheduled = false
            keyed = LinkedHashMap()
            items = ArrayList()
        }
    }

    companion object {
        private val handler: Handler by lazy {
            val thread = HandlerThread("MunimBluetoothEvents").apply { start() }
            Handler(thread.looper ?: Looper.getMainLooper())
        }
    }
}

/**
 * Small pieces of plumbing behind the batched listener APIs, kept free of
 * react-native imports so they can be unit tested in Node.
 */

export type BatchCallback<T> = (items: T[]) => void

interface Subscriber<T> {
  callback: BatchCallback<T>
  batchIntervalMs: number
}

/**
 * Fans one native batched listener out to any number of JS subscribers.
 * The native side is attached with the smallest interval any subscriber
 * asked for, re-attached when that changes, and detached when the last
 * subscriber leaves.
 */
export class BatchedListenerHub<T> {
  private readonly subscribers = new Map<number, Subscriber<T>>()
  private nextId = 1
  private attachedIntervalMs: number | null = null

  constructor(
    private readonly attach: (
      deliver: BatchCallback<T>,
      batchIntervalMs: number
    ) => void,
    private readonly detach: () => void,
    private readonly onError: (error: unknown) => void = (error) =>
      console.error('[munim-bluetooth] listener threw:', error)
  ) {}

  get size(): number {
    return this.subscribers.size
  }

  add(callback: BatchCallback<T>, batchIntervalMs: number): () => void {
    const interval = normalizeInterval(batchIntervalMs)
    const id = this.nextId++
    this.subscribers.set(id, { callback, batchIntervalMs: interval })
    this.sync()
    let removed = false
    return () => {
      if (removed) return
      removed = true
      this.subscribers.delete(id)
      this.sync()
    }
  }

  private readonly deliver: BatchCallback<T> = (items) => {
    // Snapshot so a subscriber removing itself does not skip another one.
    for (const subscriber of Array.from(this.subscribers.values())) {
      try {
        subscriber.callback(items)
      } catch (error) {
        this.onError(error)
      }
    }
  }

  private sync(): void {
    if (this.subscribers.size === 0) {
      if (this.attachedIntervalMs !== null) {
        this.attachedIntervalMs = null
        this.detach()
      }
      return
    }
    let interval = Infinity
    for (const subscriber of this.subscribers.values()) {
      interval = Math.min(interval, subscriber.batchIntervalMs)
    }
    if (interval !== this.attachedIntervalMs) {
      this.attachedIntervalMs = interval
      this.attach(this.deliver, interval)
    }
  }
}

function normalizeInterval(value: number): number {
  if (!Number.isFinite(value) || value <= 0) return 0
  return Math.min(Math.floor(value), 10_000)
}

/**
 * Reference counts JS listeners for the high-rate legacy events and reports
 * changes to native code.
 */
export class EventListenerCounter {
  private readonly counts = new Map<string, number>()

  constructor(
    private readonly tracked: ReadonlySet<string>,
    private readonly report: (eventName: string, count: number) => void
  ) {}

  increment(eventName: string): void {
    this.change(eventName, 1)
  }

  decrement(eventName: string): void {
    this.change(eventName, -1)
  }

  count(eventName: string): number {
    return this.counts.get(eventName) ?? 0
  }

  private change(eventName: string, delta: number): void {
    if (!this.tracked.has(eventName)) return
    const next = Math.max(0, (this.counts.get(eventName) ?? 0) + delta)
    this.counts.set(eventName, next)
    try {
      this.report(eventName, next)
    } catch {
      // Older native builds without the method: legacy events keep flowing.
    }
  }
}

/**
 * Runs `count` async steps in order, keeping at most `window` in flight.
 * Steps start in index order; the first rejection stops new steps and is
 * rethrown once the in-flight ones settle.
 */
export async function runWindowed(
  count: number,
  window: number,
  step: (index: number) => Promise<void>
): Promise<void> {
  const limit = Math.max(1, Math.floor(window))
  const inFlight = new Set<Promise<void>>()
  const state: { failure?: { error: unknown } } = {}
  for (let index = 0; index < count && !state.failure; index++) {
    const promise: Promise<void> = step(index).then(
      () => {
        inFlight.delete(promise)
      },
      (error: unknown) => {
        inFlight.delete(promise)
        state.failure ??= { error }
      }
    )
    inFlight.add(promise)
    if (inFlight.size >= limit) {
      await Promise.race(inFlight)
    }
  }
  await Promise.all(inFlight)
  if (state.failure) throw state.failure.error
}

/** Split `total` bytes into [start, end) ranges of at most `chunkSize`. */
export function chunkRanges(
  total: number,
  chunkSize: number
): Array<[number, number]> {
  if (!Number.isInteger(chunkSize) || chunkSize <= 0) {
    throw new RangeError('chunkSize must be a positive integer')
  }
  const ranges: Array<[number, number]> = []
  for (let start = 0; start < total; start += chunkSize) {
    ranges.push([start, Math.min(start + chunkSize, total)])
  }
  return ranges
}

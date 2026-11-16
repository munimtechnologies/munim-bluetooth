import {
  BatchedListenerHub,
  EventListenerCounter,
  chunkRanges,
  runWindowed,
} from '../src/fanout'

describe('BatchedListenerHub', () => {
  function makeHub() {
    const attach = jest.fn<void, [(items: number[]) => void, number]>()
    const detach = jest.fn()
    const onError = jest.fn()
    const hub = new BatchedListenerHub<number>(attach, detach, onError)
    const deliver = (items: number[]) => attach.mock.calls.at(-1)![0](items)
    return { hub, attach, detach, onError, deliver }
  }

  it('attaches once, fans out, and detaches with the last subscriber', () => {
    const { hub, attach, detach, deliver } = makeHub()
    const a = jest.fn()
    const b = jest.fn()
    const removeA = hub.add(a, 50)
    const removeB = hub.add(b, 50)
    expect(attach).toHaveBeenCalledTimes(1)
    expect(attach.mock.calls[0]![1]).toBe(50)

    deliver([1, 2])
    expect(a).toHaveBeenCalledWith([1, 2])
    expect(b).toHaveBeenCalledWith([1, 2])

    removeA()
    removeA() // idempotent
    expect(detach).not.toHaveBeenCalled()
    removeB()
    expect(detach).toHaveBeenCalledTimes(1)
    expect(hub.size).toBe(0)
  })

  it('uses the smallest requested interval and re-attaches when it changes', () => {
    const { hub, attach } = makeHub()
    const removeSlow = hub.add(jest.fn(), 200)
    const removeFast = hub.add(jest.fn(), 16)
    expect(attach.mock.calls.map((call) => call[1])).toEqual([200, 16])
    removeFast()
    expect(attach.mock.calls.map((call) => call[1])).toEqual([200, 16, 200])
    removeSlow()
  })

  it('normalizes bad intervals to immediate delivery', () => {
    const { hub, attach } = makeHub()
    hub.add(jest.fn(), Number.NaN)
    expect(attach.mock.calls[0]![1]).toBe(0)
  })

  it('isolates a throwing subscriber', () => {
    const { hub, onError, deliver } = makeHub()
    const after = jest.fn()
    hub.add(() => {
      throw new Error('boom')
    }, 0)
    hub.add(after, 0)
    deliver([7])
    expect(onError).toHaveBeenCalledTimes(1)
    expect(after).toHaveBeenCalledWith([7])
  })
})

describe('EventListenerCounter', () => {
  it('reports counts only for tracked events and never below zero', () => {
    const report = jest.fn()
    const counter = new EventListenerCounter(new Set(['deviceFound']), report)
    counter.increment('deviceFound')
    counter.increment('deviceFound')
    counter.increment('mtuChanged')
    counter.decrement('deviceFound')
    counter.decrement('deviceFound')
    counter.decrement('deviceFound')
    expect(report.mock.calls).toEqual([
      ['deviceFound', 1],
      ['deviceFound', 2],
      ['deviceFound', 1],
      ['deviceFound', 0],
      ['deviceFound', 0],
    ])
    expect(counter.count('deviceFound')).toBe(0)
  })

  it('survives a native module without the method', () => {
    const counter = new EventListenerCounter(new Set(['x']), () => {
      throw new Error('not a function')
    })
    expect(() => counter.increment('x')).not.toThrow()
  })
})

describe('chunkRanges', () => {
  it('splits into full chunks plus a remainder', () => {
    expect(chunkRanges(10, 4)).toEqual([
      [0, 4],
      [4, 8],
      [8, 10],
    ])
    expect(chunkRanges(0, 4)).toEqual([])
  })

  it('rejects invalid chunk sizes', () => {
    expect(() => chunkRanges(10, 0)).toThrow(RangeError)
    expect(() => chunkRanges(10, 1.5)).toThrow(RangeError)
  })
})

describe('runWindowed', () => {
  it('starts steps in order and never exceeds the window', async () => {
    let inFlight = 0
    let maxInFlight = 0
    const started: number[] = []
    await runWindowed(20, 3, async (index) => {
      started.push(index)
      inFlight++
      maxInFlight = Math.max(maxInFlight, inFlight)
      await new Promise((resolve) => setTimeout(resolve, (index % 4) + 1))
      inFlight--
    })
    expect(started).toEqual(Array.from({ length: 20 }, (_, i) => i))
    expect(maxInFlight).toBe(3)
  })

  it('stops starting steps after a failure and rethrows it', async () => {
    const started: number[] = []
    await expect(
      runWindowed(10, 1, async (index) => {
        started.push(index)
        if (index === 2) throw new Error('write failed')
      })
    ).rejects.toThrow('write failed')
    expect(started).toEqual([0, 1, 2])
  })
})

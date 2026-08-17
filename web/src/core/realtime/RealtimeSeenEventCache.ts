interface RealtimeSeenEventCacheOptions {
  ttlMs: number
  maxEntries: number
}

export class RealtimeSeenEventCache {
  private readonly seenEvents = new Map<string, number>()
  private readonly options: RealtimeSeenEventCacheOptions

  constructor(options: RealtimeSeenEventCacheOptions) {
    this.options = options
    if (options.ttlMs <= 0) {
      throw new Error('Realtime event cache TTL must be greater than zero.')
    }

    if (!Number.isInteger(options.maxEntries) || options.maxEntries <= 0) {
      throw new Error('Realtime event cache capacity must be a positive integer.')
    }
  }

  checkAndRemember(eventKey: string, now = Date.now()): boolean {
    this.removeExpiredEntries(now)

    const seenAt = this.seenEvents.get(eventKey)

    if (seenAt !== undefined) {
      return true
    }

    this.removeOldestEntriesAtCapacity()
    this.seenEvents.set(eventKey, now)
    return false
  }

  clear() {
    this.seenEvents.clear()
  }

  private removeExpiredEntries(now: number) {
    this.seenEvents.forEach((seenAt, eventKey) => {
      if (now - seenAt > this.options.ttlMs) {
        this.seenEvents.delete(eventKey)
      }
    })
  }

  private removeOldestEntriesAtCapacity() {
    while (this.seenEvents.size >= this.options.maxEntries) {
      const oldestEventKey = this.seenEvents.keys().next().value

      if (oldestEventKey === undefined) {
        return
      }

      this.seenEvents.delete(oldestEventKey)
    }
  }
}

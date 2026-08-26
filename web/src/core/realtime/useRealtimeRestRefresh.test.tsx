import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  REALTIME_REST_REFRESH_DEBOUNCE_MS,
  useRealtimeRestRefresh,
} from './useRealtimeRestRefresh'
import {
  subscribeToRealtimeMessages,
  subscribeToRealtimeRecovery,
} from './realtimeRuntime'
import type { RealtimeMessageHandler } from './realtimeTypes'

vi.mock('./realtimeRuntime', () => ({
  subscribeToRealtimeMessages: vi.fn(() => vi.fn()),
  subscribeToRealtimeRecovery: vi.fn(() => vi.fn()),
}))

const mockedSubscribeToRealtimeMessages = vi.mocked(subscribeToRealtimeMessages)
const mockedSubscribeToRealtimeRecovery = vi.mocked(subscribeToRealtimeRecovery)

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  vi.useRealTimers()
})

describe('useRealtimeRestRefresh', () => {
  it('coalesces realtime message storms into a single refresh', () => {
    vi.useFakeTimers()

    let messageListener: RealtimeMessageHandler | undefined

    mockedSubscribeToRealtimeMessages.mockImplementation((listener) => {
      messageListener = listener
      return vi.fn()
    })

    const refresh = vi.fn()
    const message = { body: '', headers: {} }

    renderHook(() => useRealtimeRestRefresh(refresh))

    act(() => {
      messageListener?.(message)
      messageListener?.(message)
      messageListener?.(message)
      messageListener?.(message)
      messageListener?.(message)
      messageListener?.(message)
      messageListener?.(message)
      messageListener?.(message)
      messageListener?.(message)
      messageListener?.(message)
    })

    expect(refresh).not.toHaveBeenCalled()

    act(() => {
      vi.advanceTimersByTime(REALTIME_REST_REFRESH_DEBOUNCE_MS)
    })

    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('refreshes after realtime recovery', () => {
    vi.useFakeTimers()

    let recoveryListener: (() => void) | undefined

    mockedSubscribeToRealtimeRecovery.mockImplementation((listener) => {
      recoveryListener = listener
      return vi.fn()
    })

    const refresh = vi.fn()

    renderHook(() => useRealtimeRestRefresh(refresh))

    act(() => {
      recoveryListener?.()
      vi.advanceTimersByTime(REALTIME_REST_REFRESH_DEBOUNCE_MS)
    })

    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('does not subscribe to messages when message refresh is disabled', () => {
    const refresh = vi.fn()

    renderHook(() =>
      useRealtimeRestRefresh(refresh, {
        onMessages: false,
      }),
    )

    expect(mockedSubscribeToRealtimeMessages).not.toHaveBeenCalled()
    expect(mockedSubscribeToRealtimeRecovery).toHaveBeenCalledTimes(1)
  })

  it('does not subscribe to recovery when recovery refresh is disabled', () => {
    const refresh = vi.fn()

    renderHook(() =>
      useRealtimeRestRefresh(refresh, {
        onRecovery: false,
      }),
    )

    expect(mockedSubscribeToRealtimeMessages).toHaveBeenCalledTimes(1)
    expect(mockedSubscribeToRealtimeRecovery).not.toHaveBeenCalled()
  })

  it('does not refresh after unmount', () => {
    vi.useFakeTimers()

    let messageListener: RealtimeMessageHandler | undefined

    mockedSubscribeToRealtimeMessages.mockImplementation((listener) => {
      messageListener = listener
      return vi.fn()
    })

    const refresh = vi.fn()
    const { unmount } = renderHook(() => useRealtimeRestRefresh(refresh))

    act(() => {
      messageListener?.({ body: '', headers: {} })
    })

    unmount()

    act(() => {
      vi.advanceTimersByTime(REALTIME_REST_REFRESH_DEBOUNCE_MS)
    })

    expect(refresh).not.toHaveBeenCalled()
  })
})

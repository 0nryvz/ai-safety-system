import { act, cleanup, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { RealtimeViolationRecord } from '../../core/realtime/realtimeViolationReducer'
import { playAlertSound } from './alertSound'
import { useAlertSound } from './useAlertSound'

vi.mock('./alertSound', () => ({
  playAlertSound: vi.fn(),
}))

const mockedPlayAlertSound = vi.mocked(playAlertSound)

const violation: RealtimeViolationRecord = {
  violationId: 'violation-1',
  type: 'MISSING_GLOVES',
  cameraName: 'Üretim Kamerası',
  departmentName: 'Üretim',
  startedAt: '2026-08-18T18:00:00Z',
  confidence: 0.94,
  lifecycleStatus: 'ACTIVE',
  recordingStatus: 'REQUESTED',
  clipReady: false,
  coverImageReady: false,
  lastEventAt: '2026-08-18T18:00:00Z',
  dismissed: false,
  errorCode: null,
}

afterEach(() => {
  cleanup()
  mockedPlayAlertSound.mockReset()
})

describe('useAlertSound', () => {
  it('plays sound for a new violation after user interaction', () => {
    const { rerender } = renderHook(({ violations }) => useAlertSound(violations, false), {
      initialProps: {
        violations: [] as RealtimeViolationRecord[],
      },
    })

    act(() => {
      window.dispatchEvent(new Event('pointerdown'))
    })

    rerender({
      violations: [violation],
    })

    expect(mockedPlayAlertSound).toHaveBeenCalledOnce()
  })

  it('does not replay sound for an update to the same violation', () => {
    const { rerender } = renderHook(({ violations }) => useAlertSound(violations, false), {
      initialProps: {
        violations: [] as RealtimeViolationRecord[],
      },
    })

    act(() => {
      window.dispatchEvent(new Event('keydown'))
    })

    rerender({
      violations: [violation],
    })

    rerender({
      violations: [
        {
          ...violation,
          recordingStatus: 'READY',
          clipReady: true,
          lastEventAt: '2026-08-18T18:01:00Z',
        },
      ],
    })

    expect(mockedPlayAlertSound).toHaveBeenCalledOnce()
  })

  it('does not play sound while muted', () => {
    const { rerender } = renderHook(({ violations }) => useAlertSound(violations, true), {
      initialProps: {
        violations: [] as RealtimeViolationRecord[],
      },
    })

    act(() => {
      window.dispatchEvent(new Event('pointerdown'))
    })

    rerender({
      violations: [violation],
    })

    expect(mockedPlayAlertSound).not.toHaveBeenCalled()
  })
})

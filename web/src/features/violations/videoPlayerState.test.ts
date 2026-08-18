import { describe, expect, it } from 'vitest'
import { getVideoPlayerViewState } from './videoPlayerState'

describe('getVideoPlayerViewState', () => {
  it.each(['REQUESTED', 'RECORDING', 'PROCESSING'] as const)(
    'maps %s to a preparation state',
    (recordingStatus) => {
      expect(getVideoPlayerViewState(recordingStatus).kind).toBe('preparing')
    },
  )

  it('allows the player only when the recording is ready', () => {
    expect(getVideoPlayerViewState('READY')).toEqual({
      kind: 'ready',
    })
  })

  it('maps a recording error to an error state', () => {
    expect(getVideoPlayerViewState('ERROR')).toMatchObject({
      kind: 'error',
      title: 'Video hazırlanamadı',
    })
  })

  it('handles an unknown backend value safely', () => {
    expect(getVideoPlayerViewState('UNKNOWN')).toMatchObject({
      kind: 'error',
      title: 'Video durumu bilinmiyor',
    })
  })
})

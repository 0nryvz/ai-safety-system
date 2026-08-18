import { describe, expect, it } from 'vitest'
import { ApiError } from '../../core/api/apiError'
import { getVideoPlayerErrorMessage } from './videoPlayerError'

describe('getVideoPlayerErrorMessage', () => {
  it('shows a non-retryable authorization message for 403', () => {
    expect(getVideoPlayerErrorMessage(new ApiError('Forbidden', 403))).toMatchObject({
      title: 'Videoya erişim izniniz yok',
      canRetry: false,
    })
  })

  it('allows a controlled retry when the video is not ready', () => {
    expect(getVideoPlayerErrorMessage(new ApiError('Not ready', 409))).toMatchObject({
      title: 'Video henüz hazır değil',
      canRetry: true,
    })
  })

  it('shows a separate message when the recording is missing', () => {
    expect(getVideoPlayerErrorMessage(new ApiError('Not found', 404))).toMatchObject({
      title: 'Video bulunamadı',
      canRetry: true,
    })
  })

  it('handles unexpected errors safely', () => {
    expect(getVideoPlayerErrorMessage(new Error('Unexpected'))).toMatchObject({
      title: 'Video yüklenemedi',
      canRetry: true,
    })
  })
})

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../core/api/apiError'
import * as violationService from '../../services/violationService'
import ViolationVideoPlayer from './ViolationVideoPlayer'

const violationId = '11111111-1111-1111-1111-111111111111'

const clipUrlResponse = {
  url: 'https://media.example.test/authorized-clip',
  expiresAt: '2099-08-18T10:05:00Z',
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('ViolationVideoPlayer', () => {
  it('does not request a URL while the video is processing', () => {
    const getClipUrlSpy = vi.spyOn(violationService, 'getViolationClipUrl')

    render(<ViolationVideoPlayer violationId={violationId} recordingStatus="PROCESSING" />)

    expect(screen.getByText('Video işleniyor')).toBeInTheDocument()
    expect(getClipUrlSpy).not.toHaveBeenCalled()
  })

  it('renders an HTML5 video when the recording is ready', async () => {
    vi.spyOn(violationService, 'getViolationClipUrl').mockResolvedValue(clipUrlResponse)

    render(<ViolationVideoPlayer violationId={violationId} recordingStatus="READY" />)

    const video = await screen.findByLabelText('İhlal videosu oynatıcı')

    expect(video).toHaveAttribute('controls')
    expect(video).toHaveAttribute('src', clipUrlResponse.url)
  })

  it('shows a non-retryable authorization message for 403', async () => {
    vi.spyOn(violationService, 'getViolationClipUrl').mockRejectedValue(
      new ApiError('Forbidden', 403),
    )

    render(<ViolationVideoPlayer violationId={violationId} recordingStatus="READY" />)

    expect(await screen.findByText('Videoya erişim izniniz yok')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tekrar dene' })).not.toBeInTheDocument()
  })

  it('offers a controlled retry when the video is not ready', async () => {
    vi.spyOn(violationService, 'getViolationClipUrl').mockRejectedValue(
      new ApiError('Not ready', 409),
    )

    render(<ViolationVideoPlayer violationId={violationId} recordingStatus="READY" />)

    expect(await screen.findByText('Video henüz hazır değil')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tekrar dene' })).toBeInTheDocument()
  })

  it('shows the recording error without requesting a URL', async () => {
    const getClipUrlSpy = vi.spyOn(violationService, 'getViolationClipUrl')

    render(<ViolationVideoPlayer violationId={violationId} recordingStatus="ERROR" />)

    await waitFor(() => {
      expect(screen.getByText('Video hazırlanamadı')).toBeInTheDocument()
    })

    expect(getClipUrlSpy).not.toHaveBeenCalled()
  })

  it('refreshes the URL only once after consecutive playback errors', async () => {
    const getClipUrlSpy = vi
      .spyOn(violationService, 'getViolationClipUrl')
      .mockResolvedValueOnce({
        url: 'https://media.example.test/first-clip',
        expiresAt: '2099-08-18T10:05:00Z',
      })
      .mockResolvedValueOnce({
        url: 'https://media.example.test/refreshed-clip',
        expiresAt: '2099-08-18T10:10:00Z',
      })

    render(<ViolationVideoPlayer violationId={violationId} recordingStatus="READY" />)

    const firstVideo = await screen.findByLabelText('İhlal videosu oynatıcı')

    fireEvent.error(firstVideo)

    await waitFor(() => {
      expect(getClipUrlSpy).toHaveBeenCalledTimes(2)
    })

    const refreshedVideo = await screen.findByLabelText('İhlal videosu oynatıcı')

    fireEvent.error(refreshedVideo)

    expect(await screen.findByText('Video oynatılamıyor')).toBeInTheDocument()
    expect(getClipUrlSpy).toHaveBeenCalledTimes(2)
  })
})

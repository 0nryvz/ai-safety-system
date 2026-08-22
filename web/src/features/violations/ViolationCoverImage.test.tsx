import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as violationService from '../../services/violationService'
import ViolationCoverImage from './ViolationCoverImage'

const violationId = '11111111-1111-1111-1111-111111111111'

const coverUrlResponse = {
  url: 'https://media.example.test/authorized-cover',
  expiresAt: '2099-08-18T10:05:00Z',
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('ViolationCoverImage', () => {
  it('does not request a URL while the cover is not ready', () => {
    const getCoverUrlSpy = vi.spyOn(violationService, 'getViolationCoverUrl')

    render(<ViolationCoverImage violationId={violationId} coverImageReady={false} />)

    expect(screen.getByText('Kapak hazırlanıyor')).toBeInTheDocument()
    expect(getCoverUrlSpy).not.toHaveBeenCalled()
  })

  it('renders the cover image when the cover is ready', async () => {
    vi.spyOn(violationService, 'getViolationCoverUrl').mockResolvedValue(coverUrlResponse)

    render(<ViolationCoverImage violationId={violationId} coverImageReady />)

    const image = await screen.findByAltText('İhlal kapak görseli')

    expect(image).toHaveAttribute('src', coverUrlResponse.url)
  })

  it('offers a retry when the cover URL request fails', async () => {
    vi.spyOn(violationService, 'getViolationCoverUrl').mockRejectedValue(
      new Error('Request failed'),
    )

    render(<ViolationCoverImage violationId={violationId} coverImageReady />)

    expect(await screen.findByText('Kapak görseli yüklenemedi')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tekrar dene' })).toBeInTheDocument()
  })

  it('retries when the image itself cannot be loaded', async () => {
    const getCoverUrlSpy = vi
      .spyOn(violationService, 'getViolationCoverUrl')
      .mockResolvedValueOnce({
        url: 'https://media.example.test/first-cover',
        expiresAt: '2099-08-18T10:05:00Z',
      })
      .mockResolvedValueOnce({
        url: 'https://media.example.test/refreshed-cover',
        expiresAt: '2099-08-18T10:10:00Z',
      })

    render(<ViolationCoverImage violationId={violationId} coverImageReady />)

    const image = await screen.findByAltText('İhlal kapak görseli')

    fireEvent.error(image)

    expect(await screen.findByText('Kapak görseli gösterilemiyor')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Tekrar dene' }))

    await waitFor(() => {
      expect(getCoverUrlSpy).toHaveBeenCalledTimes(2)
    })
  })
})

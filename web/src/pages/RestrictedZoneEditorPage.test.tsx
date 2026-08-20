import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import * as cameraService from '../services/cameraService'
import RestrictedZoneEditorPage from './RestrictedZoneEditorPage'
import axios from 'axios'
import { ApiError } from '../core/api/apiError'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function mockRestrictedZoneNotFound() {
  vi.spyOn(cameraService, 'getRestrictedZone').mockRejectedValue(
    new axios.AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
      data: {
        status: 404,
        error: 'Not Found',
        message: 'Aktif yasaklı alan bulunamadı.',
        path: '/api/v1/cameras/11111111-1111-1111-1111-111111111111/restricted-zone',
      },
      status: 404,
      statusText: 'Not Found',
      headers: {},
      config: {
        headers: new axios.AxiosHeaders(),
      },
    }),
  )
}

function mockReferenceImageNotFound() {
  vi.spyOn(cameraService, 'getReferenceImageUrl').mockRejectedValue(
    new axios.AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
      data: {
        status: 404,
        error: 'Not Found',
        message: 'Kamera referans görüntüsü bulunamadı.',
        path: '/api/v1/cameras/11111111-1111-1111-1111-111111111111/reference-image-url',
      },
      status: 404,
      statusText: 'Not Found',
      headers: {},
      config: {
        headers: new axios.AxiosHeaders(),
      },
    }),
  )
}

function renderPage() {
  return render(
    <MemoryRouter
      initialEntries={['/admin/cameras/11111111-1111-1111-1111-111111111111/restricted-zone']}
    >
      <Routes>
        <Route
          path="/admin/cameras/:cameraId/restricted-zone"
          element={<RestrictedZoneEditorPage />}
        />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  mockRestrictedZoneNotFound()
  mockReferenceImageNotFound()
})

describe('RestrictedZoneEditorPage', () => {
  it('renders the loading state', () => {
    vi.spyOn(cameraService, 'getCamera').mockReturnValue(new Promise(() => {}))

    renderPage()

    expect(
      screen.getByRole('status', {
        name: 'Kamera bilgileri yükleniyor',
      }),
    ).toBeInTheDocument()
  })

  it('renders camera information', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Kamera 1')).toBeInTheDocument()
    })

    expect(screen.getByText(/CAM-001/)).toBeInTheDocument()
    expect(screen.getByText('Departman: Kaynak')).toBeInTheDocument()

    expect(
      screen.getByRole('application', {
        name: 'Yasaklı alan çizim alanı',
      }),
    ).toBeInTheDocument()
  })

  it('treats ApiError 404 restricted zone response as an empty state', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    vi.mocked(cameraService.getRestrictedZone).mockRejectedValue(
      new ApiError('Aktif yasaklı alan bulunamadı.', 404),
    )

    renderPage()

    await waitFor(() => {
      expect(
        screen.getByRole('application', {
          name: 'Yasaklı alan çizim alanı',
        }),
      ).toBeInTheDocument()
    })

    expect(screen.queryByText('Yasaklı alan yüklenemedi')).not.toBeInTheDocument()
  })

  it('treats ApiError 404 reference image response as an empty state', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    vi.mocked(cameraService.getReferenceImageUrl).mockRejectedValue(
      new ApiError('Kamera referans görüntüsü bulunamadı.', 404),
    )

    renderPage()

    await waitFor(() => {
      expect(
        screen.getByRole('application', {
          name: 'Yasaklı alan çizim alanı',
        }),
      ).toBeInTheDocument()
    })

    expect(screen.queryByText('Kamera referans görüntüsü yüklenemedi')).not.toBeInTheDocument()
  })

  it('renders the secured reference image URL', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    vi.mocked(cameraService.getReferenceImageUrl).mockResolvedValue({
      url: 'https://storage.example/reference-image.png',
      expiresAt: '2026-08-20T16:00:00Z',
    })

    renderPage()

    const image = await screen.findByLabelText('Kamera referans görüntüsü')

    expect(image).toHaveAttribute('src', 'https://storage.example/reference-image.png')
  })

  it('renders an error state', async () => {
    vi.spyOn(cameraService, 'getCamera').mockRejectedValue(new Error('camera load failed'))

    renderPage()

    await waitFor(() => {
      expect(
        screen.getByRole('heading', {
          name: 'Kamera bilgileri yüklenemedi',
        }),
      ).toBeInTheDocument()
    })
  })

  it('undoes the last polygon point', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    const editor = await screen.findByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1000,
      height: 500,
      top: 0,
      left: 0,
      right: 1000,
      bottom: 500,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.click(editor, {
      clientX: 250,
      clientY: 200,
    })

    expect(
      screen.getByRole('button', {
        name: 'Son noktayı geri al',
      }),
    ).not.toBeDisabled()

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Son noktayı geri al',
      }),
    )

    expect(
      screen.getByRole('button', {
        name: 'Son noktayı geri al',
      }),
    ).toBeDisabled()
  })

  it('clears polygon points', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    const editor = await screen.findByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1000,
      height: 500,
      top: 0,
      left: 0,
      right: 1000,
      bottom: 500,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.click(editor, {
      clientX: 250,
      clientY: 200,
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Temizle',
      }),
    )

    expect(
      screen.getByRole('button', {
        name: 'Temizle',
      }),
    ).toBeDisabled()
  })

  it('requires a restricted zone name before saving', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    await screen.findByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kaydet',
      }),
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Yasaklı alan adı boş olamaz.')
  })

  it('requires at least three polygon points before saving', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    await screen.findByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    fireEvent.change(screen.getByLabelText('Yasaklı alan adı'), {
      target: { value: 'Kaynak hattı' },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kaydet',
      }),
    )

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Yasaklı alan için en az 3 nokta seçmelisiniz.',
    )
  })

  it('accepts a valid local polygon without validation errors', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    const editor = await screen.findByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1000,
      height: 500,
      top: 0,
      left: 0,
      right: 1000,
      bottom: 500,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.change(screen.getByLabelText('Yasaklı alan adı'), {
      target: { value: 'Kaynak hattı' },
    })

    fireEvent.click(editor, { clientX: 100, clientY: 100 })
    fireEvent.click(editor, { clientX: 900, clientY: 100 })
    fireEvent.click(editor, { clientX: 900, clientY: 400 })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kaydet',
      }),
    )

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('loads an existing restricted zone', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    vi.mocked(cameraService.getRestrictedZone).mockResolvedValue({
      name: 'Kaynak hattı',
      polygon: [
        { x: 0.1, y: 0.1 },
        { x: 0.9, y: 0.1 },
        { x: 0.9, y: 0.9 },
      ],
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByDisplayValue('Kaynak hattı')).toBeInTheDocument()
    })

    expect(screen.getAllByTestId(/restricted-zone-point-/)).toHaveLength(3)
  })

  it('treats a missing restricted zone as an empty editor', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByLabelText('Yasaklı alan adı')).toBeInTheDocument()
    })

    expect(screen.getByLabelText('Yasaklı alan adı')).toHaveValue('')
    expect(
      screen.queryByRole('heading', { name: 'Yasaklı alan yüklenemedi' }),
    ).not.toBeInTheDocument()
  })

  it('saves a valid restricted zone', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    mockRestrictedZoneNotFound()

    const updateSpy = vi.spyOn(cameraService, 'updateRestrictedZone').mockResolvedValue(undefined)

    renderPage()

    const editor = await screen.findByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1000,
      height: 500,
      top: 0,
      left: 0,
      right: 1000,
      bottom: 500,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.change(screen.getByLabelText('Yasaklı alan adı'), {
      target: { value: 'Kaynak hattı' },
    })

    fireEvent.click(editor, { clientX: 100, clientY: 100 })
    fireEvent.click(editor, { clientX: 900, clientY: 100 })
    fireEvent.click(editor, { clientX: 900, clientY: 400 })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kaydet',
      }),
    )

    await waitFor(() => {
      expect(updateSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', {
        name: 'Kaynak hattı',
        polygon: [
          { x: 0.1, y: 0.2 },
          { x: 0.9, y: 0.2 },
          { x: 0.9, y: 0.8 },
        ],
      })
    })

    expect(await screen.findByText('Yasaklı alan kaydedildi.')).toBeInTheDocument()
  })

  it('shows a backend error when saving fails', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    mockRestrictedZoneNotFound()

    vi.spyOn(cameraService, 'updateRestrictedZone').mockRejectedValue(
      new axios.AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
        data: {
          status: 403,
          error: 'Forbidden',
          message: 'Bu kameranın yasaklı alanını güncelleme yetkiniz yok!',
          path: '/api/v1/cameras/11111111-1111-1111-1111-111111111111/restricted-zone',
        },
        status: 403,
        statusText: 'Forbidden',
        headers: {},
        config: {
          headers: new axios.AxiosHeaders(),
        },
      }),
    )

    renderPage()

    const editor = await screen.findByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1000,
      height: 500,
      top: 0,
      left: 0,
      right: 1000,
      bottom: 500,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.change(screen.getByLabelText('Yasaklı alan adı'), {
      target: { value: 'Kaynak hattı' },
    })

    fireEvent.click(editor, { clientX: 100, clientY: 100 })
    fireEvent.click(editor, { clientX: 900, clientY: 100 })
    fireEvent.click(editor, { clientX: 900, clientY: 400 })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kaydet',
      }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Bu kameranın yasaklı alanını güncelleme yetkiniz yok!',
    )
  })

  it('shows a validation error for a self-intersecting polygon', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    mockRestrictedZoneNotFound()

    renderPage()

    const editor = await screen.findByRole('application', {
      name: 'Yasaklı alan çizim alanı',
    })

    vi.spyOn(editor, 'getBoundingClientRect').mockReturnValue({
      width: 1000,
      height: 500,
      top: 0,
      left: 0,
      right: 1000,
      bottom: 500,
      x: 0,
      y: 0,
      toJSON: () => {},
    })

    fireEvent.change(screen.getByLabelText('Yasaklı alan adı'), {
      target: {
        value: 'Kaynak hattı',
      },
    })

    // Bow-tie / kendi üzerinden kesişen polygon
    fireEvent.click(editor, {
      clientX: 100,
      clientY: 100,
    })

    fireEvent.click(editor, {
      clientX: 900,
      clientY: 400,
    })

    fireEvent.click(editor, {
      clientX: 100,
      clientY: 400,
    })

    fireEvent.click(editor, {
      clientX: 900,
      clientY: 100,
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Kaydet',
      }),
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Polygon kendi üzerinden kesişemez.')
  })

  it('uploads a reference image and refreshes the secured image URL', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    const uploadSpy = vi.spyOn(cameraService, 'uploadReferenceImage').mockResolvedValue()

    renderPage()

    const fileInput = await screen.findByLabelText('Yeni kamera referans görüntüsü')

    await waitFor(() => {
      expect(cameraService.getReferenceImageUrl).toHaveBeenCalled()
    })

    vi.mocked(cameraService.getReferenceImageUrl).mockResolvedValue({
      url: 'https://storage.example/updated-reference-image.png',
      expiresAt: '2026-08-20T17:00:00Z',
    })

    const file = new File(['reference-image'], 'reference.png', {
      type: 'image/png',
    })

    fireEvent.change(fileInput, {
      target: {
        files: [file],
      },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Referans görüntüsünü yükle',
      }),
    )

    await waitFor(() => {
      expect(uploadSpy).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', file)
    })

    expect(await screen.findByText('Referans görüntüsü yüklendi.')).toBeInTheDocument()

    expect(fileInput).toHaveValue('')

    expect(await screen.findByLabelText('Kamera referans görüntüsü')).toHaveAttribute(
      'src',
      'https://storage.example/updated-reference-image.png',
    )
  })

  it('rejects an unsupported reference image type before upload', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    const uploadSpy = vi.spyOn(cameraService, 'uploadReferenceImage').mockResolvedValue()

    renderPage()

    const fileInput = await screen.findByLabelText('Yeni kamera referans görüntüsü')
    const file = new File(['reference-image'], 'reference.gif', {
      type: 'image/gif',
    })

    fireEvent.change(fileInput, {
      target: {
        files: [file],
      },
    })

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Yalnızca JPEG, PNG veya WebP görselleri yüklenebilir.',
    )

    expect(
      screen.getByRole('button', {
        name: 'Referans görüntüsünü yükle',
      }),
    ).toBeDisabled()

    expect(uploadSpy).not.toHaveBeenCalled()
  })

  it('rejects a reference image larger than 5 MiB before upload', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    const uploadSpy = vi.spyOn(cameraService, 'uploadReferenceImage').mockResolvedValue()

    renderPage()

    const fileInput = await screen.findByLabelText('Yeni kamera referans görüntüsü')
    const file = new File(['reference-image'], 'reference.png', {
      type: 'image/png',
    })

    Object.defineProperty(file, 'size', {
      value: 5 * 1024 * 1024 + 1,
    })

    fireEvent.change(fileInput, {
      target: {
        files: [file],
      },
    })

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Referans görüntüsü en fazla 5 MiB olabilir.',
    )

    expect(
      screen.getByRole('button', {
        name: 'Referans görüntüsünü yükle',
      }),
    ).toBeDisabled()

    expect(uploadSpy).not.toHaveBeenCalled()
  })

  it('shows a controlled error when reference image upload fails', async () => {
    vi.spyOn(cameraService, 'getCamera').mockResolvedValue({
      id: '11111111-1111-1111-1111-111111111111',
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: '22222222-2222-2222-2222-222222222222',
      departmentName: 'Kaynak',
      active: true,
      connectionStatus: 'ONLINE',
      lastSeenAt: null,
      activeSessionId: null,
    })

    vi.spyOn(cameraService, 'uploadReferenceImage').mockRejectedValue(new Error('upload failed'))

    renderPage()

    const fileInput = await screen.findByLabelText('Yeni kamera referans görüntüsü')
    const file = new File(['reference-image'], 'reference.png', {
      type: 'image/png',
    })

    fireEvent.change(fileInput, {
      target: {
        files: [file],
      },
    })

    fireEvent.click(
      screen.getByRole('button', {
        name: 'Referans görüntüsünü yükle',
      }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('İstek işlenirken bir hata oluştu.')

    expect(screen.queryByText('Referans görüntüsü yüklendi.')).not.toBeInTheDocument()
  })
})

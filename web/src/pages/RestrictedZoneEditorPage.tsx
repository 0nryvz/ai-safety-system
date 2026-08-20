import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import AppShell from '../app/AppShell'
import { ROUTE_PATHS } from '../app/routeConfig'
import {
  getCamera,
  getRestrictedZone,
  updateRestrictedZone,
  getReferenceImageUrl,
  uploadReferenceImage,
  type CameraResponse,
  type RestrictedZonePoint,
} from '../services/cameraService'
import Button from '../shared/ui/Button/Button'
import ErrorState from '../shared/ui/ErrorState/ErrorState'
import Skeleton from '../shared/ui/Skeleton/Skeleton'
import RestrictedZoneEditor from '../features/restricted-zone/RestrictedZoneEditor'
import Input from '../shared/ui/Input/Input'
import {
  hasMinimumPolygonPoints,
  hasSelfIntersection,
} from '../features/restricted-zone/polygonUtils'
import { mapApiError } from '../core/api/apiErrorMapper'
import { ApiError } from '../core/api/apiError'
import './RestrictedZoneEditorPage.css'

function RestrictedZoneEditorPage() {
  const { cameraId } = useParams<{ cameraId: string }>()
  const navigate = useNavigate()
  const referenceImageInputRef = useRef<HTMLInputElement>(null)

  const [camera, setCamera] = useState<CameraResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [points, setPoints] = useState<RestrictedZonePoint[]>([])
  const [zoneName, setZoneName] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [isZoneLoading, setIsZoneLoading] = useState(true)
  const [zoneLoadError, setZoneLoadError] = useState<string | null>(null)
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveSuccess, setSaveSuccess] = useState(false)
  const [referenceImageUrl, setReferenceImageUrl] = useState<string>()
  const [referenceImageError, setReferenceImageError] = useState<string | null>(null)
  const [selectedReferenceImage, setSelectedReferenceImage] = useState<File | null>(null)
  const [isReferenceImageUploading, setIsReferenceImageUploading] = useState(false)
  const [referenceImageUploadError, setReferenceImageUploadError] = useState<string | null>(null)
  const [referenceImageUploadSuccess, setReferenceImageUploadSuccess] = useState(false)

  function handleUndoPoint() {
    setPoints((currentPoints) => currentPoints.slice(0, -1))
  }

  function handleClearPoints() {
    setPoints([])
  }

  function handleReferenceImageChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0] ?? null

    setReferenceImageUploadError(null)
    setReferenceImageUploadSuccess(false)

    if (!file) {
      setSelectedReferenceImage(null)
      return
    }

    const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
    const maximumFileSize = 5 * 1024 * 1024

    if (!allowedTypes.includes(file.type)) {
      setSelectedReferenceImage(null)
      setReferenceImageUploadError('Yalnızca JPEG, PNG veya WebP görselleri yüklenebilir.')
      event.target.value = ''
      return
    }

    if (file.size > maximumFileSize) {
      setSelectedReferenceImage(null)
      setReferenceImageUploadError('Referans görüntüsü en fazla 5 MiB olabilir.')
      event.target.value = ''
      return
    }

    setSelectedReferenceImage(file)
  }

  async function handleReferenceImageUpload() {
    if (!cameraId || !selectedReferenceImage || isReferenceImageUploading) {
      return
    }

    setIsReferenceImageUploading(true)
    setReferenceImageUploadError(null)
    setReferenceImageUploadSuccess(false)

    try {
      await uploadReferenceImage(cameraId, selectedReferenceImage)

      const response = await getReferenceImageUrl(cameraId)

      setReferenceImageUrl(response.url)
      setReferenceImageError(null)
      setSelectedReferenceImage(null)

      if (referenceImageInputRef.current) {
        referenceImageInputRef.current.value = ''
      }

      setReferenceImageUploadSuccess(true)
    } catch (error) {
      const apiError = mapApiError(error)

      setReferenceImageUploadError(
        apiError.message || 'Referans görüntüsü yüklenemedi. Lütfen tekrar deneyin.',
      )
    } finally {
      setIsReferenceImageUploading(false)
    }
  }

  async function handleSaveZone() {
    if (!cameraId || isSaving) {
      return
    }

    setSaveError(null)
    setSaveSuccess(false)

    if (!zoneName.trim()) {
      setValidationError('Yasaklı alan adı boş olamaz.')
      return
    }

    if (!hasMinimumPolygonPoints(points)) {
      setValidationError('Yasaklı alan için en az 3 nokta seçmelisiniz.')
      return
    }

    if (hasSelfIntersection(points)) {
      setValidationError('Polygon kendi üzerinden kesişemez.')
      return
    }

    setValidationError(null)
    setIsSaving(true)

    try {
      await updateRestrictedZone(cameraId, {
        name: zoneName.trim(),
        polygon: points,
      })

      setSaveSuccess(true)
    } catch (error) {
      const apiError = mapApiError(error)

      setSaveError(apiError.message || 'Yasaklı alan kaydedilemedi. Lütfen tekrar deneyin.')
    } finally {
      setIsSaving(false)
    }
  }

  useEffect(() => {
    if (!cameraId) {
      return
    }

    const currentCameraId = cameraId
    let isCancelled = false

    async function loadCamera() {
      try {
        const response = await getCamera(currentCameraId)

        if (!isCancelled) {
          setCamera(response)
        }
      } catch (error) {
        if (!isCancelled) {
          setError(error instanceof Error ? error : new Error('Camera load failed'))
        }
      } finally {
        if (!isCancelled) {
          setIsLoading(false)
        }
      }
    }

    async function loadRestrictedZone() {
      try {
        const restrictedZone = await getRestrictedZone(currentCameraId)

        if (!isCancelled) {
          setZoneName(restrictedZone.name)
          setPoints(restrictedZone.polygon)
        }
      } catch (error) {
        if (isCancelled) {
          return
        }

        const apiError = error instanceof ApiError ? error : mapApiError(error)

        if (apiError.status === 404) {
          setZoneName('')
          setPoints([])
          return
        }

        setZoneLoadError(apiError.message || 'Yasaklı alan bilgileri yüklenemedi.')
      } finally {
        if (!isCancelled) {
          setIsZoneLoading(false)
        }
      }
    }

    async function loadReferenceImage() {
      try {
        const response = await getReferenceImageUrl(currentCameraId)

        if (!isCancelled) {
          setReferenceImageUrl(response.url)
          setReferenceImageError(null)
        }
      } catch (error) {
        if (isCancelled) {
          return
        }

        const apiError = error instanceof ApiError ? error : mapApiError(error)

        if (apiError.status === 404) {
          setReferenceImageUrl(undefined)
          setReferenceImageError(null)
          return
        }

        setReferenceImageUrl(undefined)
        setReferenceImageError(apiError.message || 'Kamera referans görüntüsü yüklenemedi.')
      }
    }

    void loadCamera()
    void loadRestrictedZone()
    void loadReferenceImage()

    return () => {
      isCancelled = true
    }
  }, [cameraId])

  if (!cameraId) {
    return (
      <AppShell>
        <ErrorState
          title="Kamera bulunamadı"
          description="Yasaklı alanı düzenlemek için geçerli bir kamera seçilmedi."
        />
      </AppShell>
    )
  }

  return (
    <AppShell>
      <section className="restricted-zone-page">
        <header className="restricted-zone-page__header">
          <div>
            <h2>Yasaklı Alan Düzenleyici</h2>

            {camera && (
              <p>
                {camera.name} · {camera.code}
              </p>
            )}
          </div>

          <Button
            type="button"
            variant="secondary"
            onClick={() => navigate(ROUTE_PATHS.adminCameras)}
          >
            Kamera listesine dön
          </Button>
        </header>

        {isLoading ? (
          <div role="status" aria-label="Kamera bilgileri yükleniyor">
            <Skeleton height="44px" />
            <Skeleton height="44px" />
          </div>
        ) : error ? (
          <ErrorState
            title="Kamera bilgileri yüklenemedi"
            description="Seçilen kamera bilgileri alınırken bir hata oluştu."
          />
        ) : camera ? (
          <section className="restricted-zone-page__content">
            <h3>{camera.name}</h3>
            <p>Departman: {camera.departmentName ?? '-'}</p>

            {isZoneLoading ? (
              <div role="status" aria-label="Yasaklı alan yükleniyor">
                <Skeleton height="44px" />
                <Skeleton height="240px" />
              </div>
            ) : zoneLoadError ? (
              <ErrorState title="Yasaklı alan yüklenemedi" description={zoneLoadError} />
            ) : (
              <>
                <Input
                  label="Yasaklı alan adı"
                  value={zoneName}
                  onChange={(event) => setZoneName(event.target.value)}
                />

                <div className="restricted-zone-page__actions">
                  <Button
                    type="button"
                    variant="secondary"
                    disabled={points.length === 0}
                    onClick={handleUndoPoint}
                  >
                    Son noktayı geri al
                  </Button>

                  <Button
                    type="button"
                    variant="secondary"
                    disabled={points.length === 0}
                    onClick={handleClearPoints}
                  >
                    Temizle
                  </Button>

                  <Button type="button" disabled={isSaving} onClick={() => void handleSaveZone()}>
                    {isSaving ? 'Kaydediliyor...' : 'Kaydet'}
                  </Button>
                </div>

                {referenceImageError && <p role="alert">{referenceImageError}</p>}

                <div className="restricted-zone-page__reference-upload">
                  <label htmlFor="reference-image-file">Yeni kamera referans görüntüsü</label>

                  <input
                    ref={referenceImageInputRef}
                    id="reference-image-file"
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    disabled={isReferenceImageUploading}
                    onChange={handleReferenceImageChange}
                  />

                  <Button
                    type="button"
                    disabled={!selectedReferenceImage || isReferenceImageUploading}
                    onClick={() => void handleReferenceImageUpload()}
                  >
                    {isReferenceImageUploading ? 'Yükleniyor...' : 'Referans görüntüsünü yükle'}
                  </Button>
                </div>

                {referenceImageUploadError && <p role="alert">{referenceImageUploadError}</p>}

                {referenceImageUploadSuccess && <p role="status">Referans görüntüsü yüklendi.</p>}

                <div className="restricted-zone-page__editor">
                  <RestrictedZoneEditor
                    points={points}
                    onChange={setPoints}
                    imageUrl={referenceImageUrl}
                  />
                </div>

                {validationError && <p role="alert">{validationError}</p>}

                {saveError && <p role="alert">{saveError}</p>}

                {saveSuccess && <p role="status">Yasaklı alan kaydedildi.</p>}
              </>
            )}
          </section>
        ) : null}
      </section>
    </AppShell>
  )
}

export default RestrictedZoneEditorPage

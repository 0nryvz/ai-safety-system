import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import AppShell from '../app/AppShell'
import { ROUTE_PATHS } from '../app/routeConfig'
import {
  getCamera,
  getRestrictedZone,
  updateRestrictedZone,
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

function RestrictedZoneEditorPage() {
  const { cameraId } = useParams<{ cameraId: string }>()
  const navigate = useNavigate()

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

  function handleUndoPoint() {
    setPoints((currentPoints) => currentPoints.slice(0, -1))
  }

  function handleClearPoints() {
    setPoints([])
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

        const apiError = mapApiError(error)

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

    void loadCamera()
    void loadRestrictedZone()

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
      <section>
        <header>
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
          <section>
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

                <div>
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

                <RestrictedZoneEditor points={points} onChange={setPoints} />

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

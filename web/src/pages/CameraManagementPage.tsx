import AppShell from '../app/AppShell'
import { useCameraManagement } from '../features/admin/useCameraManagement'
import { createCamera, updateCamera, type CameraResponse } from '../services/cameraService'
import Button from '../shared/ui/Button/Button'
import DataTable, { type DataTableColumn } from '../shared/ui/DataTable/DataTable'
import EmptyState from '../shared/ui/EmptyState/EmptyState'
import ErrorState from '../shared/ui/ErrorState/ErrorState'
import Skeleton from '../shared/ui/Skeleton/Skeleton'
import StatusBadge from '../shared/ui/StatusBadge/StatusBadge'
import './CameraManagementPage.css'
import { useState } from 'react'
import CameraForm from '../features/admin/CameraForm'
import { useAdminDepartmentOptions } from '../features/admin/useAdminDepartmentOptions'
import {
  toCreateCameraRequest,
  toUpdateCameraRequest,
  type CameraFormErrors,
  type CameraFormValues,
} from '../features/admin/cameraFormUtils'
import ConfirmDialog from '../shared/ui/ConfirmDialog/ConfirmDialog'
import { mapApiError } from '../core/api/apiErrorMapper'
import { useNavigate } from 'react-router-dom'
import { ROUTE_PATHS } from '../app/routeConfig'

function getConnectionPresentation(status: string) {
  switch (status) {
    case 'ONLINE':
      return {
        label: 'Çevrimiçi',
        variant: 'success' as const,
      }

    case 'WEAK':
      return {
        label: 'Zayıf bağlantı',
        variant: 'warning' as const,
      }

    case 'OFFLINE':
      return {
        label: 'Çevrimdışı',
        variant: 'critical' as const,
      }

    default:
      return {
        label: 'Bilinmiyor',
        variant: 'neutral' as const,
      }
  }
}

function CameraManagementPage() {
  const navigate = useNavigate()
  const { data, isLoading, error, retry } = useCameraManagement()

  const {
    departments,
    isLoading: areDepartmentsLoading,
    error: departmentsError,
  } = useAdminDepartmentOptions()

  const [isCreateFormOpen, setIsCreateFormOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [editingCamera, setEditingCamera] = useState<CameraResponse | null>(null)
  const [statusCamera, setStatusCamera] = useState<CameraResponse | null>(null)
  const [serverErrors, setServerErrors] = useState<CameraFormErrors>({})

  const columns: DataTableColumn<CameraResponse>[] = [
    {
      key: 'name',
      header: 'Kamera',
      render: (camera) => (
        <div>
          <strong>{camera.name}</strong>
          <div>{camera.code}</div>
        </div>
      ),
    },
    {
      key: 'department',
      header: 'Departman',
      render: (camera) => camera.departmentName ?? '-',
    },
    {
      key: 'connectionStatus',
      header: 'Bağlantı',
      render: (camera) => {
        const presentation = getConnectionPresentation(camera.connectionStatus)

        return <StatusBadge variant={presentation.variant}>{presentation.label}</StatusBadge>
      },
    },
    {
      key: 'active',
      header: 'Durum',
      render: (camera) => (
        <StatusBadge variant={camera.active ? 'success' : 'neutral'}>
          {camera.active ? 'Aktif' : 'Pasif'}
        </StatusBadge>
      ),
    },

    {
      key: 'actions',
      header: 'İşlemler',
      render: (camera) => (
        <div className="camera-management__actions">
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              navigate(ROUTE_PATHS.restrictedZoneEditor.replace(':cameraId', camera.id))
            }}
          >
            Yasaklı Alan
          </Button>
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              setSubmitError(null)
              setIsCreateFormOpen(false)
              setEditingCamera(camera)
            }}
          >
            Düzenle
          </Button>

          <Button
            type="button"
            variant={camera.active ? 'danger' : 'secondary'}
            onClick={() => {
              setSubmitError(null)
              setStatusCamera(camera)
            }}
          >
            {camera.active ? 'Pasife al' : 'Aktifleştir'}
          </Button>
        </div>
      ),
    },
  ]

  async function handleCreateCamera(values: CameraFormValues) {
    if (isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)
    setServerErrors({})

    try {
      await createCamera(toCreateCameraRequest(values))

      setIsCreateFormOpen(false)
      retry()
    } catch (error) {
      const apiError = mapApiError(error)
      const fieldErrors = apiError.response?.fieldErrors ?? {}

      setServerErrors({
        name: fieldErrors.name,
        code: fieldErrors.code,
        departmentId: fieldErrors.departmentId,
      })

      setSubmitError(
        apiError.message || 'Kamera oluşturulamadı. Bilgileri kontrol edip tekrar deneyin.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleUpdateCamera(values: CameraFormValues) {
    if (!editingCamera || isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)
    setServerErrors({})

    try {
      await updateCamera(editingCamera.id, toUpdateCameraRequest(values))

      setEditingCamera(null)
      retry()
    } catch (error) {
      const apiError = mapApiError(error)
      const fieldErrors = apiError.response?.fieldErrors ?? {}

      setServerErrors({
        name: fieldErrors.name,
        code: fieldErrors.code,
        departmentId: fieldErrors.departmentId,
      })

      setSubmitError(
        apiError.message || 'Kamera güncellenemedi. Bilgileri kontrol edip tekrar deneyin.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleToggleCameraStatus() {
    if (!statusCamera || isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)

    try {
      await updateCamera(statusCamera.id, {
        active: !statusCamera.active,
      })

      setStatusCamera(null)
      retry()
    } catch {
      setSubmitError(
        statusCamera.active
          ? 'Kamera pasife alınamadı. Lütfen tekrar deneyin.'
          : 'Kamera aktifleştirilemedi. Lütfen tekrar deneyin.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AppShell>
      <section className="camera-management">
        <header className="camera-management__header">
          <div>
            <h2>Kamera Yönetimi</h2>
            <p>Sisteme bağlı kameraları görüntüleyin ve yönetin.</p>
          </div>

          <Button
            type="button"
            onClick={() => {
              setSubmitError(null)
              setIsCreateFormOpen(true)
            }}
          >
            Yeni Kamera
          </Button>
        </header>

        {isCreateFormOpen && (
          <section className="camera-management__form-section">
            <h3>Yeni Kamera</h3>

            {departmentsError ? (
              <ErrorState
                title="Departmanlar yüklenemedi"
                description="Kamera eklemek için departman seçenekleri alınamadı."
              />
            ) : (
              <CameraForm
                key="new-camera"
                departments={departments}
                isSubmitting={isSubmitting || areDepartmentsLoading}
                serverErrors={serverErrors}
                onSubmit={(values) => void handleCreateCamera(values)}
                onCancel={() => {
                  setSubmitError(null)
                  setIsCreateFormOpen(false)
                }}
              />
            )}

            {submitError && (
              <p className="camera-management__submit-error" role="alert">
                {submitError}
              </p>
            )}
          </section>
        )}

        {editingCamera && (
          <section className="camera-management__form-section">
            <h3>Kamerayı Düzenle</h3>

            {departmentsError ? (
              <ErrorState
                title="Departmanlar yüklenemedi"
                description="Kamera düzenlemek için departman seçenekleri alınamadı."
              />
            ) : (
              <CameraForm
                key={editingCamera.id}
                camera={editingCamera}
                departments={departments}
                isSubmitting={isSubmitting || areDepartmentsLoading}
                serverErrors={serverErrors}
                onSubmit={(values) => void handleUpdateCamera(values)}
                onCancel={() => {
                  setSubmitError(null)
                  setEditingCamera(null)
                }}
              />
            )}
          </section>
        )}

        <ConfirmDialog
          open={Boolean(statusCamera)}
          title={statusCamera?.active ? 'Kamerayı pasife al' : 'Kamerayı aktifleştir'}
          description={
            statusCamera?.active
              ? 'Bu kamera pasife alınacak. Devam etmek istiyor musunuz?'
              : 'Bu kamera yeniden aktifleştirilecek. Devam etmek istiyor musunuz?'
          }
          confirmLabel={statusCamera?.active ? 'Pasife al' : 'Aktifleştir'}
          cancelLabel="Vazgeç"
          confirmVariant={statusCamera?.active ? 'danger' : 'primary'}
          onConfirm={() => void handleToggleCameraStatus()}
          onCancel={() => setStatusCamera(null)}
        />

        {isLoading ? (
          <div
            className="camera-management__loading"
            role="status"
            aria-label="Kameralar yükleniyor"
          >
            <Skeleton height="44px" />
            <Skeleton height="44px" />
            <Skeleton height="44px" />
          </div>
        ) : error ? (
          <ErrorState
            title="Kameralar yüklenemedi"
            description="Kamera listesi alınırken bir hata oluştu."
            action={
              <Button type="button" onClick={retry}>
                Tekrar dene
              </Button>
            }
          />
        ) : data.length === 0 ? (
          <EmptyState
            title="Kamera bulunamadı"
            description="Henüz sisteme eklenmiş bir kamera yok."
          />
        ) : (
          <DataTable columns={columns} data={data} getRowKey={(camera) => camera.id} />
        )}
      </section>
    </AppShell>
  )
}

export default CameraManagementPage

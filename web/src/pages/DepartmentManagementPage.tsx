import AppShell from '../app/AppShell'
import { useDepartmentManagement } from '../features/admin/useDepartmentManagement'
import type { DepartmentResponse } from '../services/departmentService'
import Button from '../shared/ui/Button/Button'
import DataTable, { type DataTableColumn } from '../shared/ui/DataTable/DataTable'
import EmptyState from '../shared/ui/EmptyState/EmptyState'
import ErrorState from '../shared/ui/ErrorState/ErrorState'
import Skeleton from '../shared/ui/Skeleton/Skeleton'
import StatusBadge from '../shared/ui/StatusBadge/StatusBadge'
import './DepartmentManagementPage.css'
import { useState } from 'react'
import DepartmentForm from '../features/admin/DepartmentForm'
import {
  toCreateDepartmentRequest,
  toUpdateDepartmentRequest,
  type DepartmentFormErrors,
  type DepartmentFormValues,
} from '../features/admin/departmentFormUtils'
import { createDepartment, updateDepartment } from '../services/departmentService'
import ConfirmDialog from '../shared/ui/ConfirmDialog/ConfirmDialog'
import { mapApiError } from '../core/api/apiErrorMapper'

function DepartmentManagementPage() {
  const { data, isLoading, error, retry } = useDepartmentManagement()
  const [isCreateFormOpen, setIsCreateFormOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const [editingDepartment, setEditingDepartment] = useState<DepartmentResponse | null>(null)

  const [statusDepartment, setStatusDepartment] = useState<DepartmentResponse | null>(null)

  const [serverErrors, setServerErrors] = useState<DepartmentFormErrors>({})

  async function handleCreateDepartment(values: DepartmentFormValues) {
    if (isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)
    setServerErrors({})

    try {
      await createDepartment(toCreateDepartmentRequest(values))

      setIsCreateFormOpen(false)
      retry()
    } catch (error) {
      const apiError = mapApiError(error)
      const fieldErrors = apiError.response?.fieldErrors ?? {}

      setServerErrors({
        code: fieldErrors.code,
        name: fieldErrors.name,
        description: fieldErrors.description,
      })

      setSubmitError(
        apiError.message || 'Departman oluşturulamadı. Bilgileri kontrol edip tekrar deneyin.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleUpdateDepartment(values: DepartmentFormValues) {
    if (!editingDepartment || isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)
    setServerErrors({})

    try {
      await updateDepartment(editingDepartment.id, toUpdateDepartmentRequest(values))

      setEditingDepartment(null)
      retry()
    } catch (error) {
      const apiError = mapApiError(error)
      const fieldErrors = apiError.response?.fieldErrors ?? {}

      setServerErrors({
        name: fieldErrors.name,
        description: fieldErrors.description,
      })

      setSubmitError(
        apiError.message || 'Departman güncellenemedi. Bilgileri kontrol edip tekrar deneyin.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleToggleDepartmentStatus() {
    if (!statusDepartment || isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)

    try {
      await updateDepartment(statusDepartment.id, {
        active: !statusDepartment.active,
      })

      setStatusDepartment(null)
      retry()
    } catch {
      setSubmitError(
        statusDepartment.active
          ? 'Departman pasife alınamadı. Lütfen tekrar deneyin.'
          : 'Departman aktifleştirilemedi. Lütfen tekrar deneyin.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  const columns: DataTableColumn<DepartmentResponse>[] = [
    {
      key: 'name',
      header: 'Departman',
      render: (department) => (
        <div>
          <strong>{department.name}</strong>
          <div>{department.code}</div>
        </div>
      ),
    },
    {
      key: 'description',
      header: 'Açıklama',
      render: (department) => department.description || '-',
    },
    {
      key: 'active',
      header: 'Durum',
      render: (department) => (
        <StatusBadge variant={department.active ? 'success' : 'neutral'}>
          {department.active ? 'Aktif' : 'Pasif'}
        </StatusBadge>
      ),
    },
    {
      key: 'actions',
      header: 'İşlemler',
      render: (department) => (
        <div className="department-management__actions">
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              setSubmitError(null)
              setIsCreateFormOpen(false)
              setEditingDepartment(department)
            }}
          >
            Düzenle
          </Button>

          <Button
            type="button"
            variant={department.active ? 'danger' : 'secondary'}
            onClick={() => {
              setSubmitError(null)
              setStatusDepartment(department)
            }}
          >
            {department.active ? 'Pasife al' : 'Aktifleştir'}
          </Button>
        </div>
      ),
    },
  ]

  return (
    <AppShell>
      <section className="department-management">
        <header className="department-management__header">
          <div>
            <h2>Departman Yönetimi</h2>
            <p>Sistemdeki departmanları görüntüleyin ve yönetin.</p>
          </div>
          <Button
            type="button"
            onClick={() => {
              setSubmitError(null)
              setServerErrors({})
              setEditingDepartment(null)
              setIsCreateFormOpen(true)
            }}
          >
            Yeni Departman
          </Button>
        </header>

        {isCreateFormOpen && (
          <section className="department-management__form-section">
            <h3>Yeni Departman</h3>

            <DepartmentForm
              key="new-department"
              isSubmitting={isSubmitting}
              serverErrors={serverErrors}
              onSubmit={(values) => void handleCreateDepartment(values)}
              onCancel={() => {
                setSubmitError(null)
                setServerErrors({})
                setIsCreateFormOpen(false)
              }}
            />

            {submitError && (
              <p className="department-management__submit-error" role="alert">
                {submitError}
              </p>
            )}
          </section>
        )}

        {editingDepartment && (
          <section className="department-management__form-section">
            <h3>Departmanı Düzenle</h3>

            <DepartmentForm
              key={editingDepartment.id}
              department={editingDepartment}
              isSubmitting={isSubmitting}
              serverErrors={serverErrors}
              onSubmit={(values) => void handleUpdateDepartment(values)}
              onCancel={() => {
                setSubmitError(null)
                setServerErrors({})
                setEditingDepartment(null)
              }}
            />

            {submitError && (
              <p className="department-management__submit-error" role="alert">
                {submitError}
              </p>
            )}
          </section>
        )}

        <ConfirmDialog
          open={Boolean(statusDepartment)}
          title={statusDepartment?.active ? 'Departmanı pasife al' : 'Departmanı aktifleştir'}
          description={
            statusDepartment?.active
              ? 'Bu departman pasife alınacak. Devam etmek istiyor musunuz?'
              : 'Bu departman yeniden aktifleştirilecek. Devam etmek istiyor musunuz?'
          }
          confirmLabel={statusDepartment?.active ? 'Pasife al' : 'Aktifleştir'}
          cancelLabel="Vazgeç"
          confirmVariant={statusDepartment?.active ? 'danger' : 'primary'}
          onConfirm={() => void handleToggleDepartmentStatus()}
          onCancel={() => setStatusDepartment(null)}
        />

        {isLoading ? (
          <div
            className="department-management__loading"
            role="status"
            aria-label="Departmanlar yükleniyor"
          >
            <Skeleton height="44px" />
            <Skeleton height="44px" />
            <Skeleton height="44px" />
          </div>
        ) : error ? (
          <ErrorState
            title="Departmanlar yüklenemedi"
            description="Departman listesi alınırken bir hata oluştu."
            action={
              <Button type="button" onClick={retry}>
                Tekrar dene
              </Button>
            }
          />
        ) : data.length === 0 ? (
          <EmptyState
            title="Departman bulunamadı"
            description="Henüz sisteme eklenmiş bir departman yok."
          />
        ) : (
          <DataTable columns={columns} data={data} getRowKey={(department) => department.id} />
        )}
      </section>
    </AppShell>
  )
}

export default DepartmentManagementPage

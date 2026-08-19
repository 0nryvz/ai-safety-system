import AppShell from '../app/AppShell'
import { useUserManagement } from '../features/admin/useUserManagement'
import Button from '../shared/ui/Button/Button'
import DataTable, { type DataTableColumn } from '../shared/ui/DataTable/DataTable'
import EmptyState from '../shared/ui/EmptyState/EmptyState'
import ErrorState from '../shared/ui/ErrorState/ErrorState'
import Skeleton from '../shared/ui/Skeleton/Skeleton'
import StatusBadge from '../shared/ui/StatusBadge/StatusBadge'
import './UserManagementPage.css'
import { useState } from 'react'
import UserForm from '../features/admin/UserForm'
import { useAdminDepartmentOptions } from '../features/admin/useAdminDepartmentOptions'
import {
  toCreateUserRequest,
  toUpdateUserRequest,
  type UserFormErrors,
  type UserFormValues,
} from '../features/admin/userFormUtils'
import { createUser, deactivateUser, updateUser, type UserResponse } from '../services/userService'
import ConfirmDialog from '../shared/ui/ConfirmDialog/ConfirmDialog'
import { mapApiError } from '../core/api/apiErrorMapper'

function getRoleLabel(role: string): string {
  switch (role) {
    case 'ADMIN':
      return 'Admin'
    case 'OHS_SPECIALIST':
      return 'İSG Uzmanı'
    case 'SHIFT_SUPERVISOR':
      return 'Vardiya Sorumlusu'
    default:
      return role
  }
}

function UserManagementPage() {
  const { data, isLoading, error, retry } = useUserManagement()

  const {
    departments,
    isLoading: areDepartmentsLoading,
    error: departmentsError,
  } = useAdminDepartmentOptions()

  const [isCreateFormOpen, setIsCreateFormOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [editingUser, setEditingUser] = useState<UserResponse | null>(null)
  const [statusTarget, setStatusTarget] = useState<UserResponse | null>(null)
  const [serverErrors, setServerErrors] = useState<UserFormErrors>({})

  const columns: DataTableColumn<UserResponse>[] = [
    {
      key: 'user',
      header: 'Kullanıcı',
      render: (user) => (
        <div>
          <strong>{user.fullName}</strong>
          <div>{user.email}</div>
        </div>
      ),
    },
    {
      key: 'roles',
      header: 'Roller',
      render: (user) => (user.roles.length > 0 ? user.roles.map(getRoleLabel).join(', ') : '-'),
    },
    {
      key: 'department',
      header: 'Departman',
      render: (user) => user.departmentName ?? '-',
    },
    {
      key: 'active',
      header: 'Durum',
      render: (user) => (
        <StatusBadge variant={user.active ? 'success' : 'neutral'}>
          {user.active ? 'Aktif' : 'Pasif'}
        </StatusBadge>
      ),
    },
    {
      key: 'actions',
      header: 'İşlemler',
      render: (user) => (
        <div className="user-management__actions">
          <Button
            type="button"
            variant="secondary"
            onClick={() => {
              setSubmitError(null)
              setIsCreateFormOpen(false)
              setEditingUser(user)
            }}
          >
            Düzenle
          </Button>

          <Button
            type="button"
            variant={user.active ? 'danger' : 'secondary'}
            onClick={() => {
              setSubmitError(null)
              setStatusTarget(user)
            }}
          >
            {user.active ? 'Pasife al' : 'Aktifleştir'}
          </Button>
        </div>
      ),
    },
  ]

  async function handleCreateUser(values: UserFormValues) {
    if (isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)
    setServerErrors({})

    try {
      await createUser(toCreateUserRequest(values))

      setIsCreateFormOpen(false)
      retry()
    } catch (error) {
      const apiError = mapApiError(error)
      const fieldErrors = apiError.response?.fieldErrors ?? {}

      setServerErrors({
        fullName: fieldErrors.fullName,
        email: fieldErrors.email,
        password: fieldErrors.password,
        departmentIds: fieldErrors.departmentIds,
        roleNames: fieldErrors.roleNames,
      })

      setSubmitError(
        apiError.message || 'Kullanıcı oluşturulamadı. Bilgileri kontrol edip tekrar deneyin.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleUpdateUser(values: UserFormValues) {
    if (!editingUser || isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)
    setServerErrors({})

    try {
      await updateUser(editingUser.id, toUpdateUserRequest(values, editingUser.active))

      setEditingUser(null)
      retry()
    } catch (error) {
      const apiError = mapApiError(error)
      const fieldErrors = apiError.response?.fieldErrors ?? {}

      setServerErrors({
        fullName: fieldErrors.fullName,
        departmentIds: fieldErrors.departmentIds,
        roleNames: fieldErrors.roleNames,
      })

      setSubmitError(
        apiError.message || 'Kullanıcı güncellenemedi. Bilgileri kontrol edip tekrar deneyin.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleToggleUserStatus() {
    if (!statusTarget || isSubmitting) {
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)

    try {
      if (statusTarget.active) {
        await deactivateUser(statusTarget.id)
      } else {
        await updateUser(statusTarget.id, {
          active: true,
        })
      }

      setStatusTarget(null)
      retry()
    } catch {
      setSubmitError(
        statusTarget.active
          ? 'Kullanıcı pasife alınamadı. Lütfen tekrar deneyin.'
          : 'Kullanıcı aktifleştirilemedi. Lütfen tekrar deneyin.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AppShell>
      <section className="user-management">
        <header className="user-management__header">
          <div>
            <h2>Kullanıcı Yönetimi</h2>
            <p>Sistem kullanıcılarını görüntüleyin ve yönetin.</p>
          </div>

          <Button
            type="button"
            onClick={() => {
              setSubmitError(null)
              setEditingUser(null)
              setIsCreateFormOpen(true)
            }}
          >
            Yeni Kullanıcı
          </Button>
        </header>

        {isCreateFormOpen && (
          <section className="user-management__form-section">
            <h3>Yeni Kullanıcı</h3>

            {departmentsError ? (
              <ErrorState
                title="Departmanlar yüklenemedi"
                description="Kullanıcı oluşturmak için departman seçenekleri alınamadı."
              />
            ) : (
              <UserForm
                departments={departments}
                isSubmitting={isSubmitting || areDepartmentsLoading}
                serverErrors={serverErrors}
                onSubmit={(values) => void handleCreateUser(values)}
                onCancel={() => {
                  setSubmitError(null)
                  setIsCreateFormOpen(false)
                }}
              />
            )}

            {submitError && (
              <p className="user-management__submit-error" role="alert">
                {submitError}
              </p>
            )}
          </section>
        )}

        {editingUser && (
          <section className="user-management__form-section">
            <h3>Kullanıcıyı Düzenle</h3>

            {departmentsError ? (
              <ErrorState
                title="Departmanlar yüklenemedi"
                description="Kullanıcı düzenlemek için departman seçenekleri alınamadı."
              />
            ) : (
              <UserForm
                key={editingUser.id}
                user={editingUser}
                departments={departments}
                isSubmitting={isSubmitting || areDepartmentsLoading}
                serverErrors={serverErrors}
                onSubmit={(values) => void handleUpdateUser(values)}
                onCancel={() => {
                  setSubmitError(null)
                  setEditingUser(null)
                }}
              />
            )}
          </section>
        )}

        <ConfirmDialog
          open={Boolean(statusTarget)}
          title={statusTarget?.active ? 'Kullanıcıyı pasife al' : 'Kullanıcıyı aktifleştir'}
          description={
            statusTarget
              ? statusTarget.active
                ? `${statusTarget.fullName} kullanıcısı pasife alınacak. Devam etmek istiyor musunuz?`
                : `${statusTarget.fullName} kullanıcısı yeniden aktifleştirilecek. Devam etmek istiyor musunuz?`
              : ''
          }
          confirmLabel={statusTarget?.active ? 'Pasife al' : 'Aktifleştir'}
          cancelLabel="Vazgeç"
          confirmVariant={statusTarget?.active ? 'danger' : 'primary'}
          onConfirm={() => void handleToggleUserStatus()}
          onCancel={() => setStatusTarget(null)}
        />

        {isLoading ? (
          <div
            className="user-management__loading"
            role="status"
            aria-label="Kullanıcılar yükleniyor"
          >
            <Skeleton height="44px" />
            <Skeleton height="44px" />
            <Skeleton height="44px" />
          </div>
        ) : error ? (
          <ErrorState
            title="Kullanıcılar yüklenemedi"
            description="Kullanıcı listesi alınırken bir hata oluştu."
            action={
              <Button type="button" onClick={retry}>
                Tekrar dene
              </Button>
            }
          />
        ) : data.length === 0 ? (
          <EmptyState
            title="Kullanıcı bulunamadı"
            description="Henüz sisteme eklenmiş bir kullanıcı yok."
          />
        ) : (
          <DataTable columns={columns} data={data} getRowKey={(user) => user.id} />
        )}
      </section>
    </AppShell>
  )
}

export default UserManagementPage

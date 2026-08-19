import { useState } from 'react'
import type { DepartmentResponse, UserResponse } from '../../services/userService'
import Button from '../../shared/ui/Button/Button'
import Input from '../../shared/ui/Input/Input'
import {
  hasUserFormErrors,
  validateUserForm,
  type UserFormErrors,
  type UserFormValues,
} from './userFormUtils'
import './UserForm.css'

interface UserFormProps {
  user?: UserResponse
  departments: DepartmentResponse[]
  isSubmitting: boolean
  serverErrors?: UserFormErrors
  onSubmit: (values: UserFormValues) => void
  onCancel: () => void
}

const roleOptions = [
  {
    value: 'ADMIN',
    label: 'Admin',
  },
  {
    value: 'OHS_SPECIALIST',
    label: 'İSG Uzmanı',
  },
  {
    value: 'SHIFT_SUPERVISOR',
    label: 'Vardiya Sorumlusu',
  },
] as const

function UserForm({
  user,
  departments,
  isSubmitting,
  serverErrors,
  onSubmit,
  onCancel,
}: UserFormProps) {
  const isEditing = Boolean(user)

  const [values, setValues] = useState<UserFormValues>(() => ({
    fullName: user?.fullName ?? '',
    email: user?.email ?? '',
    password: '',
    departmentIds: user?.departmentIds ?? [],
    roleNames:
      user?.roles.filter(
        (role): role is UserFormValues['roleNames'][number] =>
          role === 'ADMIN' || role === 'OHS_SPECIALIST' || role === 'SHIFT_SUPERVISOR',
      ) ?? [],
  }))

  const [errors, setErrors] = useState<UserFormErrors>({})

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const nextErrors = validateUserForm(values, {
      isEditing,
    })

    setErrors(nextErrors)

    if (hasUserFormErrors(nextErrors)) {
      return
    }

    onSubmit(values)
  }

  function handleDepartmentChange(departmentId: string, checked: boolean) {
    setValues((current) => ({
      ...current,
      departmentIds: checked
        ? [...current.departmentIds, departmentId]
        : current.departmentIds.filter((id) => id !== departmentId),
    }))
  }

  function handleRoleChange(role: UserFormValues['roleNames'][number], checked: boolean) {
    setValues((current) => ({
      ...current,
      roleNames: checked
        ? [...current.roleNames, role]
        : current.roleNames.filter((currentRole) => currentRole !== role),
    }))
  }

  return (
    <form className="user-form" onSubmit={handleSubmit}>
      <Input
        label="Ad soyad"
        value={values.fullName}
        error={errors.fullName ?? serverErrors?.fullName}
        disabled={isSubmitting}
        onChange={(event) => {
          setValues((current) => ({
            ...current,
            fullName: event.target.value,
          }))
        }}
      />

      {!isEditing && (
        <>
          <Input
            label="E-posta"
            type="email"
            value={values.email}
            error={errors.email ?? serverErrors?.email}
            disabled={isSubmitting}
            onChange={(event) => {
              setValues((current) => ({
                ...current,
                email: event.target.value,
              }))
            }}
          />

          <Input
            label="Parola"
            type="password"
            value={values.password}
            error={errors.password ?? serverErrors?.password}
            disabled={isSubmitting}
            onChange={(event) => {
              setValues((current) => ({
                ...current,
                password: event.target.value,
              }))
            }}
          />
        </>
      )}

      <fieldset className="user-form__group" disabled={isSubmitting}>
        <legend>Roller</legend>

        <div className="user-form__options">
          {roleOptions.map((role) => (
            <label key={role.value} className="user-form__checkbox">
              <input
                type="checkbox"
                checked={values.roleNames.includes(role.value)}
                onChange={(event) => handleRoleChange(role.value, event.target.checked)}
              />

              <span>{role.label}</span>
            </label>
          ))}
        </div>

        {(errors.roleNames ?? serverErrors?.roleNames) && (
          <p className="user-form__error" role="alert">
            {errors.roleNames ?? serverErrors?.roleNames}
          </p>
        )}
      </fieldset>

      <fieldset className="user-form__group" disabled={isSubmitting}>
        <legend>Departmanlar</legend>

        <div className="user-form__options">
          {departments.map((department) => (
            <label key={department.id} className="user-form__checkbox">
              <input
                type="checkbox"
                checked={values.departmentIds.includes(department.id)}
                onChange={(event) => handleDepartmentChange(department.id, event.target.checked)}
              />

              <span>{department.name}</span>
            </label>
          ))}
        </div>

        {(errors.departmentIds ?? serverErrors?.departmentIds) && (
          <p className="user-form__error" role="alert">
            {errors.departmentIds ?? serverErrors?.departmentIds}
          </p>
        )}
      </fieldset>

      <div className="user-form__actions">
        <Button type="button" variant="secondary" disabled={isSubmitting} onClick={onCancel}>
          Vazgeç
        </Button>

        <Button type="submit" disabled={isSubmitting}>
          {isEditing ? 'Değişiklikleri kaydet' : 'Kullanıcı oluştur'}
        </Button>
      </div>
    </form>
  )
}

export default UserForm

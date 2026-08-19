import type { CreateUserRequest, UpdateUserRequest } from '../../services/userService'

export interface UserFormValues {
  fullName: string
  email: string
  password: string
  departmentIds: string[]
  roleNames: CreateUserRequest['roleNames']
}

export interface UserFormErrors {
  fullName?: string
  email?: string
  password?: string
  departmentIds?: string
  roleNames?: string
}

interface ValidateUserFormOptions {
  isEditing: boolean
}

export function validateUserForm(
  values: UserFormValues,
  options: ValidateUserFormOptions,
): UserFormErrors {
  const errors: UserFormErrors = {}

  if (!values.fullName.trim()) {
    errors.fullName = 'Ad soyad zorunludur.'
  }

  if (!options.isEditing) {
    if (!values.email.trim()) {
      errors.email = 'E-posta zorunludur.'
    } else if (!values.email.includes('@')) {
      errors.email = 'Geçerli bir e-posta adresi girin.'
    }

    if (!values.password) {
      errors.password = 'Parola zorunludur.'
    } else if (values.password.length < 6) {
      errors.password = 'Parola en az 6 karakter olmalıdır.'
    }
  }

  if (values.departmentIds.length === 0) {
    errors.departmentIds = 'En az bir departman seçin.'
  }

  if (values.roleNames.length === 0) {
    errors.roleNames = 'En az bir rol seçin.'
  }

  return errors
}

export function toCreateUserRequest(values: UserFormValues): CreateUserRequest {
  return {
    fullName: values.fullName.trim(),
    email: values.email.trim(),
    password: values.password,
    departmentIds: values.departmentIds,
    roleNames: values.roleNames,
  }
}

export function toUpdateUserRequest(values: UserFormValues, active: boolean): UpdateUserRequest {
  return {
    fullName: values.fullName.trim(),
    departmentIds: values.departmentIds,
    roleNames: values.roleNames,
    active,
  }
}

export function hasUserFormErrors(errors: UserFormErrors): boolean {
  return Object.keys(errors).length > 0
}

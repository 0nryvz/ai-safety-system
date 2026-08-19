import { describe, expect, it } from 'vitest'
import {
  hasUserFormErrors,
  toCreateUserRequest,
  toUpdateUserRequest,
  validateUserForm,
  type UserFormValues,
} from './userFormUtils'

const validValues: UserFormValues = {
  fullName: 'Test User',
  email: 'user@example.com',
  password: '123456',
  departmentIds: ['22222222-2222-2222-2222-222222222222'],
  roleNames: ['OHS_SPECIALIST'],
}

describe('userFormUtils', () => {
  it('validates required create fields', () => {
    const errors = validateUserForm(
      {
        fullName: '',
        email: '',
        password: '',
        departmentIds: [],
        roleNames: [],
      },
      {
        isEditing: false,
      },
    )

    expect(errors.fullName).toBeDefined()
    expect(errors.email).toBeDefined()
    expect(errors.password).toBeDefined()
    expect(errors.departmentIds).toBeDefined()
    expect(errors.roleNames).toBeDefined()
    expect(hasUserFormErrors(errors)).toBe(true)
  })

  it('requires a valid email and minimum six character password', () => {
    const errors = validateUserForm(
      {
        ...validValues,
        email: 'invalid-email',
        password: '12345',
      },
      {
        isEditing: false,
      },
    )

    expect(errors.email).toBe('Geçerli bir e-posta adresi girin.')
    expect(errors.password).toBe('Parola en az 6 karakter olmalıdır.')
  })

  it('does not require email or password when editing', () => {
    const errors = validateUserForm(
      {
        ...validValues,
        email: '',
        password: '',
      },
      {
        isEditing: true,
      },
    )

    expect(errors.email).toBeUndefined()
    expect(errors.password).toBeUndefined()
    expect(hasUserFormErrors(errors)).toBe(false)
  })

  it('maps form values to create request', () => {
    expect(
      toCreateUserRequest({
        ...validValues,
        fullName: '  Test User  ',
        email: '  user@example.com  ',
      }),
    ).toEqual({
      fullName: 'Test User',
      email: 'user@example.com',
      password: '123456',
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
      roleNames: ['OHS_SPECIALIST'],
    })
  })

  it('maps form values to update request without email and password', () => {
    expect(toUpdateUserRequest(validValues, true)).toEqual({
      fullName: 'Test User',
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
      roleNames: ['OHS_SPECIALIST'],
      active: true,
    })
  })
})

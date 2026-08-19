import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../core/api/apiClient'
import {
  getCurrentUser,
  getMyDepartments,
  mapUserResponseToAuthUser,
  type DepartmentResponse,
  type UserResponse,
} from './userService'

const userResponse: UserResponse = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'user@example.com',
  fullName: 'Test User',
  active: true,
  departmentId: '22222222-2222-2222-2222-222222222222',
  departmentName: 'Üretim',
  roles: ['OHS_SPECIALIST'],
  departmentIds: ['22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333'],
  createdAt: '2026-08-15T00:00:00Z',
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('userService', () => {
  it('loads the current user from the confirmed endpoint', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: userResponse,
    })

    const result = await getCurrentUser()

    expect(getSpy).toHaveBeenCalledWith('/users/me')
    expect(result).toEqual(userResponse)
  })

  it('maps the backend user response to the auth user contract', () => {
    const result = mapUserResponseToAuthUser(userResponse)

    expect(result).toEqual({
      id: userResponse.id,
      email: userResponse.email,
      fullName: userResponse.fullName,
      active: true,
      roles: ['OHS_SPECIALIST'],
      departmentIds: userResponse.departmentIds,
    })
  })

  it('ignores unknown backend roles', () => {
    const result = mapUserResponseToAuthUser({
      ...userResponse,
      roles: ['ADMIN', 'UNKNOWN_ROLE'],
    })

    expect(result.roles).toEqual(['ADMIN'])
  })
  it('loads the current user departments', async () => {
    const departments: DepartmentResponse[] = [
      {
        id: '22222222-2222-2222-2222-222222222222',
        name: 'Üretim',
      },
      {
        id: '33333333-3333-3333-3333-333333333333',
        name: 'Kaynak',
      },
    ]

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: departments,
    })

    const result = await getMyDepartments()

    expect(getSpy).toHaveBeenCalledWith('/users/me/departments')
    expect(result).toEqual(departments)
  })
})

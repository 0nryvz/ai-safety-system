import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../core/api/apiClient'
import {
  createUser,
  deactivateUser,
  getCurrentUser,
  getMyDepartments,
  getUser,
  getUsers,
  mapUserResponseToAuthUser,
  updateUser,
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

  it('loads all users', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: [userResponse],
    })

    const result = await getUsers()

    expect(getSpy).toHaveBeenCalledWith('/users')
    expect(result).toEqual([userResponse])
  })

  it('loads a user by id', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: userResponse,
    })

    const result = await getUser(userResponse.id)

    expect(getSpy).toHaveBeenCalledWith(`/users/${userResponse.id}`)
    expect(result).toEqual(userResponse)
  })

  it('creates a user', async () => {
    const request = {
      email: 'new@example.com',
      password: '123456',
      fullName: 'New User',
      departmentIds: ['22222222-2222-2222-2222-222222222222'],
      roleNames: ['OHS_SPECIALIST'] as const,
    }

    const createdUser: UserResponse = {
      ...userResponse,
      id: '44444444-4444-4444-4444-444444444444',
      email: request.email,
      fullName: request.fullName,
      roles: [...request.roleNames],
      departmentIds: request.departmentIds,
    }

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: createdUser,
    })

    const result = await createUser({
      ...request,
      roleNames: [...request.roleNames],
    })

    expect(postSpy).toHaveBeenCalledWith('/users', {
      ...request,
      roleNames: [...request.roleNames],
    })

    expect(result).toEqual(createdUser)
  })

  it('updates a user', async () => {
    const request = {
      fullName: 'Updated User',
      departmentIds: ['33333333-3333-3333-3333-333333333333'],
      roleNames: ['SHIFT_SUPERVISOR'] as const,
      active: true,
    }

    const updatedUser: UserResponse = {
      ...userResponse,
      fullName: request.fullName,
      departmentIds: request.departmentIds,
      roles: [...request.roleNames],
    }

    const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValue({
      data: updatedUser,
    })

    const result = await updateUser(userResponse.id, {
      ...request,
      roleNames: [...request.roleNames],
    })

    expect(patchSpy).toHaveBeenCalledWith(`/users/${userResponse.id}`, {
      ...request,
      roleNames: [...request.roleNames],
    })

    expect(result).toEqual(updatedUser)
  })

  it('deactivates a user', async () => {
    const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValue({
      data: undefined,
    })

    await deactivateUser(userResponse.id)

    expect(deleteSpy).toHaveBeenCalledWith(`/users/${userResponse.id}`)
  })
})

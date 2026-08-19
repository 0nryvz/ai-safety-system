import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as userService from '../../services/userService'
import { useUserManagement } from './useUserManagement'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useUserManagement', () => {
  it('loads users', async () => {
    vi.spyOn(userService, 'getUsers').mockResolvedValue([
      {
        id: '11111111-1111-1111-1111-111111111111',
        email: 'user@example.com',
        fullName: 'Test User',
        active: true,
        departmentId: '22222222-2222-2222-2222-222222222222',
        departmentName: 'Üretim',
        roles: ['OHS_SPECIALIST'],
        departmentIds: ['22222222-2222-2222-2222-222222222222'],
        createdAt: '2026-08-15T00:00:00Z',
      },
    ])

    const { result } = renderHook(() => useUserManagement())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.error).toBeNull()
    expect(result.current.data).toHaveLength(1)
    expect(result.current.data[0].email).toBe('user@example.com')
  })

  it('stores an error when loading users fails', async () => {
    vi.spyOn(userService, 'getUsers').mockRejectedValue(new Error('request failed'))

    const { result } = renderHook(() => useUserManagement())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.data).toEqual([])
    expect(result.current.error).toBeInstanceOf(Error)
  })

  it('retries loading users', async () => {
    const getUsersSpy = vi
      .spyOn(userService, 'getUsers')
      .mockRejectedValueOnce(new Error('request failed'))
      .mockResolvedValueOnce([])

    const { result } = renderHook(() => useUserManagement())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.error).toBeInstanceOf(Error)

    act(() => {
      result.current.retry()
    })

    await waitFor(() => {
      expect(getUsersSpy).toHaveBeenCalledTimes(2)
    })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
      expect(result.current.error).toBeNull()
    })
  })
})

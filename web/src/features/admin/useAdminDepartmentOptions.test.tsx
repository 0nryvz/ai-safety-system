import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as userService from '../../services/userService'
import { useAdminDepartmentOptions } from './useAdminDepartmentOptions'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useAdminDepartmentOptions', () => {
  it('loads departments', async () => {
    vi.spyOn(userService, 'getMyDepartments').mockResolvedValue([
      {
        id: '22222222-2222-2222-2222-222222222222',
        name: 'Üretim',
      },
      {
        id: '33333333-3333-3333-3333-333333333333',
        name: 'Kaynak',
      },
    ])

    const { result } = renderHook(() => useAdminDepartmentOptions())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.error).toBeNull()
    expect(result.current.departments).toHaveLength(2)
  })

  it('stores an error when departments cannot be loaded', async () => {
    vi.spyOn(userService, 'getMyDepartments').mockRejectedValue(new Error('request failed'))

    const { result } = renderHook(() => useAdminDepartmentOptions())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.departments).toEqual([])
    expect(result.current.error).toBeInstanceOf(Error)
  })
})

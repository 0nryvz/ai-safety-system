import { cleanup, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as userService from '../../services/userService'
import type { DepartmentResponse } from '../../services/userService'
import { useDepartmentOptions } from './useDepartmentOptions'

const departments: DepartmentResponse[] = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Kaynak',
  },
]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('useDepartmentOptions', () => {
  it('loads department options', async () => {
    vi.spyOn(userService, 'getMyDepartments').mockResolvedValue(departments)

    const { result } = renderHook(() => useDepartmentOptions())

    expect(result.current.isLoading).toBe(true)

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.departments).toEqual(departments)
    expect(result.current.error).toBeNull()
  })

  it('exposes department loading errors', async () => {
    const error = new Error('Department request failed')

    vi.spyOn(userService, 'getMyDepartments').mockRejectedValue(error)

    const { result } = renderHook(() => useDepartmentOptions())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.departments).toEqual([])
    expect(result.current.error).toBe(error)
  })
})

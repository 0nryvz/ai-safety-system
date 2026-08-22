import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as departmentService from '../../services/departmentService'
import { useDepartmentManagement } from './useDepartmentManagement'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useDepartmentManagement', () => {
  it('loads departments', async () => {
    vi.spyOn(departmentService, 'getDepartments').mockResolvedValue([
      {
        id: '11111111-1111-1111-1111-111111111111',
        code: 'URETIM',
        name: 'Üretim',
        description: 'Üretim departmanı',
        active: true,
      },
    ])

    const { result } = renderHook(() => useDepartmentManagement())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.error).toBeNull()
    expect(result.current.data).toHaveLength(1)
    expect(result.current.data[0].code).toBe('URETIM')
    expect(result.current.data[0].name).toBe('Üretim')
  })

  it('stores an error when loading departments fails', async () => {
    vi.spyOn(departmentService, 'getDepartments').mockRejectedValue(
      new Error('request failed'),
    )

    const { result } = renderHook(() => useDepartmentManagement())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.data).toEqual([])
    expect(result.current.error).toBeInstanceOf(Error)
  })

  it('retries loading departments', async () => {
    const getDepartmentsSpy = vi
      .spyOn(departmentService, 'getDepartments')
      .mockRejectedValueOnce(new Error('request failed'))
      .mockResolvedValueOnce([])

    const { result } = renderHook(() => useDepartmentManagement())

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.error).toBeInstanceOf(Error)

    act(() => {
      result.current.retry()
    })

    await waitFor(() => {
      expect(getDepartmentsSpy).toHaveBeenCalledTimes(2)
    })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
      expect(result.current.error).toBeNull()
    })
  })
})
import { useCallback, useEffect, useState } from 'react'
import {
  getDepartments,
  type DepartmentResponse,
} from '../../services/departmentService'

interface DepartmentManagementState {
  data: DepartmentResponse[]
  isLoading: boolean
  error: unknown
}

const initialState: DepartmentManagementState = {
  data: [],
  isLoading: true,
  error: null,
}

export function useDepartmentManagement() {
  const [state, setState] = useState(initialState)
  const [retryVersion, setRetryVersion] = useState(0)

  const retry = useCallback(() => {
    setRetryVersion((current) => current + 1)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function loadDepartments() {
      setState((current) => ({
        ...current,
        isLoading: true,
        error: null,
      }))

      try {
        const data = await getDepartments()

        if (cancelled) {
          return
        }

        setState({
          data,
          isLoading: false,
          error: null,
        })
      } catch (error) {
        if (cancelled) {
          return
        }

        setState({
          data: [],
          isLoading: false,
          error,
        })
      }
    }

    void loadDepartments()

    return () => {
      cancelled = true
    }
  }, [retryVersion])

  return {
    ...state,
    retry,
  }
}
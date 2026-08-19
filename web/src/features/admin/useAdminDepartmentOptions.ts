import { useEffect, useState } from 'react'
import { getMyDepartments, type DepartmentResponse } from '../../services/userService'

interface AdminDepartmentOptionsState {
  departments: DepartmentResponse[]
  isLoading: boolean
  error: unknown
}

const initialState: AdminDepartmentOptionsState = {
  departments: [],
  isLoading: true,
  error: null,
}

export function useAdminDepartmentOptions() {
  const [state, setState] = useState<AdminDepartmentOptionsState>(initialState)

  useEffect(() => {
    let cancelled = false

    async function loadDepartments() {
      try {
        const departments = await getMyDepartments()

        if (cancelled) {
          return
        }

        setState({
          departments,
          isLoading: false,
          error: null,
        })
      } catch (error) {
        if (cancelled) {
          return
        }

        setState({
          departments: [],
          isLoading: false,
          error,
        })
      }
    }

    void loadDepartments()

    return () => {
      cancelled = true
    }
  }, [])

  return state
}

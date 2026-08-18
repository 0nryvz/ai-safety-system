import { useEffect, useState } from 'react'
import { getMyDepartments, type DepartmentResponse } from '../../services/userService'

interface DepartmentOptionsState {
  departments: DepartmentResponse[]
  isLoading: boolean
  error: unknown
}

const initialState: DepartmentOptionsState = {
  departments: [],
  isLoading: true,
  error: null,
}

export function useDepartmentOptions() {
  const [state, setState] = useState<DepartmentOptionsState>(initialState)

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

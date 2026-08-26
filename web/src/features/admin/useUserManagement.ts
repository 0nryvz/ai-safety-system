import { useCallback, useEffect, useState } from 'react'
import { getUsers, type UserResponse } from '../../services/userService'

interface UserManagementState {
  data: UserResponse[]
  isLoading: boolean
  error: unknown
}

const initialState: UserManagementState = {
  data: [],
  isLoading: true,
  error: null,
}

export function useUserManagement() {
  const [state, setState] = useState(initialState)
  const [retryVersion, setRetryVersion] = useState(0)

  const retry = useCallback(() => {
    setRetryVersion((current) => current + 1)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function loadUsers() {
      setState((current) => ({
        ...current,
        isLoading: true,
        error: null,
      }))

      try {
        const data = await getUsers()

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

    void loadUsers()

    return () => {
      cancelled = true
    }
  }, [retryVersion])

  return {
    ...state,
    retry,
  }
}

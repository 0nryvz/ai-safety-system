import { useCallback, useEffect, useState } from 'react'
import { getCameras, type CameraResponse } from '../../services/cameraService'

interface CameraManagementState {
  data: CameraResponse[]
  isLoading: boolean
  error: unknown
}

const initialState: CameraManagementState = {
  data: [],
  isLoading: true,
  error: null,
}

export function useCameraManagement() {
  const [state, setState] = useState(initialState)
  const [retryVersion, setRetryVersion] = useState(0)

  const retry = useCallback(() => {
    setRetryVersion((current) => current + 1)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function loadCameras() {
      setState((current) => ({
        ...current,
        isLoading: true,
        error: null,
      }))

      try {
        const data = await getCameras()

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

    void loadCameras()

    return () => {
      cancelled = true
    }
  }, [retryVersion])

  return {
    ...state,
    retry,
  }
}

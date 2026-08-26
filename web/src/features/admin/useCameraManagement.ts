import { useCallback, useEffect, useRef, useState } from 'react'
import { useRealtimeRestRefresh } from '../../core/realtime/useRealtimeRestRefresh'
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

  const isMountedRef = useRef(true)

  useEffect(() => {
    isMountedRef.current = true

    return () => {
      isMountedRef.current = false
    }
  }, [])

  useRealtimeRestRefresh(
    () => {
      void getCameras()
        .then((data) => {
          if (!isMountedRef.current) {
            return
          }

          setState({
            data,
            isLoading: false,
            error: null,
          })
        })
        .catch(() => {
          // Recovery refresh failure keeps the last known camera list visible.
        })
    },
    {
      onMessages: false,
    },
  )

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

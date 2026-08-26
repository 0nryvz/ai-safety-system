import { useEffect, useState } from 'react'
import { getCameras, type CameraResponse } from '../../services/cameraService'

interface CameraOptionsState {
  cameras: CameraResponse[]
  isLoading: boolean
  error: unknown
}

const initialState: CameraOptionsState = {
  cameras: [],
  isLoading: true,
  error: null,
}

export function useCameraOptions() {
  const [state, setState] = useState<CameraOptionsState>(initialState)

  useEffect(() => {
    let cancelled = false

    async function loadCameras() {
      try {
        const cameras = await getCameras()

        if (cancelled) {
          return
        }

        setState({
          cameras,
          isLoading: false,
          error: null,
        })
      } catch (error) {
        if (cancelled) {
          return
        }

        setState({
          cameras: [],
          isLoading: false,
          error,
        })
      }
    }

    void loadCameras()

    return () => {
      cancelled = true
    }
  }, [])

  return state
}

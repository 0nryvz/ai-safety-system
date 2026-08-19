import type { CreateCameraRequest, UpdateCameraRequest } from '../../services/cameraService'

export interface CameraFormValues {
  name: string
  code: string
  departmentId: string
}

export interface CameraFormErrors {
  name?: string
  code?: string
  departmentId?: string
}

export function validateCameraForm(values: CameraFormValues): CameraFormErrors {
  const errors: CameraFormErrors = {}

  if (!values.name.trim()) {
    errors.name = 'Kamera adı zorunludur.'
  }

  if (!values.code.trim()) {
    errors.code = 'Kamera kodu zorunludur.'
  }

  if (!values.departmentId) {
    errors.departmentId = 'Departman seçimi zorunludur.'
  }

  return errors
}

export function toCreateCameraRequest(values: CameraFormValues): CreateCameraRequest {
  return {
    name: values.name.trim(),
    code: values.code.trim(),
    departmentId: values.departmentId,
  }
}

export function toUpdateCameraRequest(values: CameraFormValues): UpdateCameraRequest {
  return {
    name: values.name.trim(),
    code: values.code.trim(),
    departmentId: values.departmentId,
  }
}

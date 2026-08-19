import { useState } from 'react'
import type { CameraResponse } from '../../services/cameraService'
import Button from '../../shared/ui/Button/Button'
import Input from '../../shared/ui/Input/Input'
import Select from '../../shared/ui/Select/Select'
import { type CameraFormErrors, type CameraFormValues, validateCameraForm } from './cameraFormUtils'
import './CameraForm.css'

interface CameraFormProps {
  camera?: CameraResponse | null
  departments: {
    id: string
    name: string
  }[]
  isSubmitting?: boolean
  serverErrors?: CameraFormErrors
  onSubmit: (values: CameraFormValues) => void
  onCancel: () => void
}

const emptyValues: CameraFormValues = {
  name: '',
  code: '',
  departmentId: '',
}

function CameraForm({
  camera,
  departments,
  isSubmitting = false,
  serverErrors,
  onSubmit,
  onCancel,
}: CameraFormProps) {
  const [values, setValues] = useState<CameraFormValues>(() =>
    camera
      ? {
          name: camera.name,
          code: camera.code,
          departmentId: camera.departmentId,
        }
      : emptyValues,
  )
  const [errors, setErrors] = useState<CameraFormErrors>({})

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const nextErrors = validateCameraForm(values)

    setErrors(nextErrors)

    if (Object.keys(nextErrors).length > 0) {
      return
    }

    onSubmit(values)
  }

  return (
    <form className="camera-form" onSubmit={handleSubmit} noValidate>
      <Input
        label="Kamera adı"
        name="name"
        value={values.name}
        disabled={isSubmitting}
        error={errors.name ?? serverErrors?.name}
        onChange={(event) =>
          setValues((current) => ({
            ...current,
            name: event.target.value,
          }))
        }
      />

      <Input
        label="Kamera kodu"
        name="code"
        value={values.code}
        disabled={isSubmitting}
        error={errors.code ?? serverErrors?.code}
        onChange={(event) =>
          setValues((current) => ({
            ...current,
            code: event.target.value,
          }))
        }
      />

      <Select
        label="Departman"
        name="departmentId"
        value={values.departmentId}
        disabled={isSubmitting}
        error={errors.departmentId ?? serverErrors?.departmentId}
        onChange={(event) =>
          setValues((current) => ({
            ...current,
            departmentId: event.target.value,
          }))
        }
      >
        <option value="">Departman seçin</option>

        {departments.map((department) => (
          <option key={department.id} value={department.id}>
            {department.name}
          </option>
        ))}
      </Select>

      <div className="camera-form__actions">
        <Button type="button" variant="secondary" disabled={isSubmitting} onClick={onCancel}>
          Vazgeç
        </Button>

        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Kaydediliyor...' : camera ? 'Değişiklikleri kaydet' : 'Kamera ekle'}
        </Button>
      </div>
    </form>
  )
}

export default CameraForm

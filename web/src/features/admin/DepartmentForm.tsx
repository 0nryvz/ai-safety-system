import { useState } from 'react'
import type { DepartmentResponse } from '../../services/departmentService'
import Button from '../../shared/ui/Button/Button'
import Input from '../../shared/ui/Input/Input'
import {
  getDepartmentFormInitialValues,
  type DepartmentFormErrors,
  type DepartmentFormValues,
  validateDepartmentForm,
} from './departmentFormUtils'
import './DepartmentForm.css'

interface DepartmentFormProps {
  department?: DepartmentResponse | null
  isSubmitting?: boolean
  serverErrors?: DepartmentFormErrors
  onSubmit: (values: DepartmentFormValues) => void
  onCancel: () => void
}

function DepartmentForm({
  department,
  isSubmitting = false,
  serverErrors,
  onSubmit,
  onCancel,
}: DepartmentFormProps) {
  const [values, setValues] = useState<DepartmentFormValues>(() =>
    getDepartmentFormInitialValues(department ?? undefined),
  )
  const [errors, setErrors] = useState<DepartmentFormErrors>({})

  const isEditing = Boolean(department)

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const nextErrors = validateDepartmentForm(values, isEditing)

    setErrors(nextErrors)

    if (Object.keys(nextErrors).length > 0) {
      return
    }

    onSubmit(values)
  }

  return (
    <form className="department-form" onSubmit={handleSubmit} noValidate>
      <Input
        label="Departman kodu"
        name="code"
        value={values.code}
        disabled={isSubmitting || isEditing}
        error={errors.code ?? serverErrors?.code}
        onChange={(event) =>
          setValues((current) => ({
            ...current,
            code: event.target.value,
          }))
        }
      />

      <Input
        label="Departman adı"
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
        label="Açıklama"
        name="description"
        value={values.description}
        disabled={isSubmitting}
        error={errors.description ?? serverErrors?.description}
        onChange={(event) =>
          setValues((current) => ({
            ...current,
            description: event.target.value,
          }))
        }
      />

      <div className="department-form__actions">
        <Button
          type="button"
          variant="secondary"
          disabled={isSubmitting}
          onClick={onCancel}
        >
          Vazgeç
        </Button>

        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting
            ? 'Kaydediliyor...'
            : isEditing
              ? 'Değişiklikleri kaydet'
              : 'Departman ekle'}
        </Button>
      </div>
    </form>
  )
}

export default DepartmentForm
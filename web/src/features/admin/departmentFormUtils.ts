import type {
    CreateDepartmentRequest,
    DepartmentResponse,
    UpdateDepartmentRequest,
  } from '../../services/departmentService'
  
  export interface DepartmentFormValues {
    code: string
    name: string
    description: string
  }
  
  export interface DepartmentFormErrors {
    code?: string
    name?: string
    description?: string
  }
  
  export function getDepartmentFormInitialValues(
    department?: DepartmentResponse,
  ): DepartmentFormValues {
    return {
      code: department?.code ?? '',
      name: department?.name ?? '',
      description: department?.description ?? '',
    }
  }
  
  export function validateDepartmentForm(
    values: DepartmentFormValues,
    isEditing: boolean,
  ): DepartmentFormErrors {
    const errors: DepartmentFormErrors = {}
  
    const code = values.code.trim()
    const name = values.name.trim()
    const description = values.description.trim()
  
    if (!isEditing && !code) {
      errors.code = 'Departman kodu zorunludur.'
    } else if (!isEditing && code.length > 40) {
      errors.code = 'Departman kodu en fazla 40 karakter olabilir.'
    }
  
    if (!name) {
      errors.name = 'Departman adı zorunludur.'
    } else if (name.length > 120) {
      errors.name = 'Departman adı en fazla 120 karakter olabilir.'
    }
  
    if (description.length > 500) {
      errors.description = 'Açıklama en fazla 500 karakter olabilir.'
    }
  
    return errors
  }
  
  export function toCreateDepartmentRequest(
    values: DepartmentFormValues,
  ): CreateDepartmentRequest {
    return {
      code: values.code.trim(),
      name: values.name.trim(),
      description: values.description.trim() || undefined,
    }
  }
  
  export function toUpdateDepartmentRequest(
    values: DepartmentFormValues,
  ): UpdateDepartmentRequest {
    return {
      name: values.name.trim(),
      description: values.description.trim(),
    }
  }
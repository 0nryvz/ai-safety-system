import { describe, expect, it } from 'vitest'
import {
  getDepartmentFormInitialValues,
  toCreateDepartmentRequest,
  toUpdateDepartmentRequest,
  validateDepartmentForm,
} from './departmentFormUtils'

describe('departmentFormUtils', () => {
  it('returns empty initial values for create mode', () => {
    expect(getDepartmentFormInitialValues()).toEqual({
      code: '',
      name: '',
      description: '',
    })
  })

  it('returns department values for edit mode', () => {
    expect(
      getDepartmentFormInitialValues({
        id: '11111111-1111-1111-1111-111111111111',
        code: 'URETIM',
        name: 'Üretim',
        description: 'Üretim departmanı',
        active: true,
      }),
    ).toEqual({
      code: 'URETIM',
      name: 'Üretim',
      description: 'Üretim departmanı',
    })
  })

  it('requires code and name when creating', () => {
    expect(
      validateDepartmentForm(
        {
          code: '   ',
          name: '',
          description: '',
        },
        false,
      ),
    ).toEqual({
      code: 'Departman kodu zorunludur.',
      name: 'Departman adı zorunludur.',
    })
  })

  it('validates backend length limits', () => {
    expect(
      validateDepartmentForm(
        {
          code: 'A'.repeat(41),
          name: 'B'.repeat(121),
          description: 'C'.repeat(501),
        },
        false,
      ),
    ).toEqual({
      code: 'Departman kodu en fazla 40 karakter olabilir.',
      name: 'Departman adı en fazla 120 karakter olabilir.',
      description: 'Açıklama en fazla 500 karakter olabilir.',
    })
  })

  it('does not validate code in edit mode', () => {
    expect(
      validateDepartmentForm(
        {
          code: '',
          name: 'Üretim',
          description: '',
        },
        true,
      ),
    ).toEqual({})
  })

  it('builds a trimmed create request', () => {
    expect(
      toCreateDepartmentRequest({
        code: ' uretim ',
        name: ' Üretim ',
        description: ' Üretim departmanı ',
      }),
    ).toEqual({
      code: 'uretim',
      name: 'Üretim',
      description: 'Üretim departmanı',
    })
  })

  it('omits an empty description from create request', () => {
    expect(
      toCreateDepartmentRequest({
        code: 'URETIM',
        name: 'Üretim',
        description: '   ',
      }),
    ).toEqual({
      code: 'URETIM',
      name: 'Üretim',
      description: undefined,
    })
  })

  it('builds update request without department code', () => {
    expect(
      toUpdateDepartmentRequest({
        code: 'DEGISTIRILMEMELI',
        name: ' Yeni Üretim ',
        description: ' Yeni açıklama ',
      }),
    ).toEqual({
      name: 'Yeni Üretim',
      description: 'Yeni açıklama',
    })
  })
})
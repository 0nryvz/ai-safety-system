import { describe, expect, it } from 'vitest'
import { toCreateCameraRequest, toUpdateCameraRequest, validateCameraForm } from './cameraFormUtils'

describe('cameraForm', () => {
  it('requires camera name, code and department', () => {
    expect(
      validateCameraForm({
        name: '',
        code: '',
        departmentId: '',
      }),
    ).toEqual({
      name: 'Kamera adı zorunludur.',
      code: 'Kamera kodu zorunludur.',
      departmentId: 'Departman seçimi zorunludur.',
    })
  })

  it('accepts a valid camera form', () => {
    expect(
      validateCameraForm({
        name: 'Kamera 1',
        code: 'CAM-001',
        departmentId: '11111111-1111-1111-1111-111111111111',
      }),
    ).toEqual({})
  })

  it('maps form values to create and update requests', () => {
    const values = {
      name: ' Kamera 1 ',
      code: ' CAM-001 ',
      departmentId: '11111111-1111-1111-1111-111111111111',
    }

    expect(toCreateCameraRequest(values)).toEqual({
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: values.departmentId,
    })

    expect(toUpdateCameraRequest(values)).toEqual({
      name: 'Kamera 1',
      code: 'CAM-001',
      departmentId: values.departmentId,
    })
  })
})
